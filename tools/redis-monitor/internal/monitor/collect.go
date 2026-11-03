package monitor

import (
	"bufio"
	"context"
	"crypto/tls"
	"errors"
	"io"
	"net"
	"time"
)

// Reader performs one complete, bounded collection attempt without retries.
type Reader interface {
	Read(context.Context) (Sample, error)
}

type redisReader struct {
	address   string
	secret    []byte
	tlsConfig *tls.Config
	dial      func(context.Context, string, string) (net.Conn, error)
}

// NewReader validates configuration and reads the bounded regular secret file once.
func NewReader(cfg Config) (Reader, error) {
	if !validAddress(cfg.Address) {
		return nil, Failure{"invalid"}
	}
	secret, err := readSecret(cfg.PasswordFile)
	if err != nil {
		return nil, err
	}
	reader := &redisReader{address: cfg.Address, secret: secret, dial: (&net.Dialer{}).DialContext}
	if cfg.TLS {
		host, _, _ := net.SplitHostPort(cfg.Address)
		reader.tlsConfig = &tls.Config{ServerName: host, MinVersion: tls.VersionTLS12}
	}
	return reader, nil
}

func (r *redisReader) Read(parent context.Context) (Sample, error) {
	ctx, cancel := context.WithTimeout(parent, 3*time.Second)
	defer cancel()
	conn, err := r.dial(ctx, "tcp", r.address)
	if err != nil {
		return Sample{}, classify(ctx, err, "connect")
	}
	defer conn.Close()
	deadline, _ := ctx.Deadline()
	if err = conn.SetDeadline(deadline); err != nil {
		return Sample{}, classify(ctx, err, "connect")
	}
	// Capture the original socket before wrapping it in TLS. Cancellation must
	// close that socket without racing the local transport variable assignment.
	rawConn := conn
	stop := context.AfterFunc(ctx, func() { _ = rawConn.Close() })
	defer stop()
	if r.tlsConfig != nil {
		secure := tls.Client(conn, r.tlsConfig)
		if err = secure.HandshakeContext(ctx); err != nil {
			return Sample{}, classify(ctx, err, "connect")
		}
		conn = secure
	}
	if err = writeAuth(conn, r.secret); err != nil {
		return Sample{}, classify(ctx, err, "protocol")
	}
	replies := bufio.NewReader(conn)
	if err = simpleReply(replies, "OK", "auth"); err != nil {
		return Sample{}, classify(ctx, err, "protocol")
	}
	if _, err = io.WriteString(conn, pingFrame); err != nil {
		return Sample{}, classify(ctx, err, "protocol")
	}
	if err = simpleReply(replies, "PONG", "protocol"); err != nil {
		return Sample{}, classify(ctx, err, "protocol")
	}
	if _, err = io.WriteString(conn, infoFrame); err != nil {
		return Sample{}, classify(ctx, err, "protocol")
	}
	data, err := infoReply(replies)
	if err != nil {
		return Sample{}, classify(ctx, err, "protocol")
	}
	defer clear(data)
	sample, err := parseInfo(data)
	if err != nil {
		return Sample{}, classify(ctx, err, "protocol")
	}
	if ctx.Err() != nil {
		return Sample{}, Failure{"timeout"}
	}
	return sample, nil
}

func classify(ctx context.Context, err error, fallback string) error {
	var network net.Error
	if ctx.Err() != nil || errors.As(err, &network) && network.Timeout() {
		return Failure{"timeout"}
	}
	var failure Failure
	if errors.As(err, &failure) {
		return failure
	}
	return Failure{fallback}
}
