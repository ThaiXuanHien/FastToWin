package supervisor

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"syscall"
	"time"
)

var errProxy = errors.New("unverified fixed proxy")

type proxyConfig struct {
	Name     string            `json:"name"`
	Listen   string            `json:"listen"`
	Upstream string            `json:"upstream"`
	Enabled  bool              `json:"enabled"`
	Toxics   []json.RawMessage `json:"toxics"`
	Logger   struct{}          `json:"Logger"`
}

// Only trusted startup constructs this driver. Neither its control operations
// nor its exported constructor accept targets, commands or configuration.
// Service serializes all access, including child cleanup and restoration.
type proxyDriver struct {
	child           Driver
	client          *http.Client
	api             string
	targets         [2]string
	state           string
	closing, closed bool
}

func proxyHTTPClient() *http.Client {
	return &http.Client{Timeout: time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return errProxy }, Transport: &http.Transport{
		Proxy: nil, DialContext: (&net.Dialer{Timeout: time.Second}).DialContext,
		DisableKeepAlives: true, MaxResponseHeaderBytes: 4096, ResponseHeaderTimeout: time.Second,
	}}
}

// apiCall never follows a redirect, inherits no proxy environment and reads at
// most 16 KiB. All call sites use fixed paths and enabled-only update bodies.
func (d *proxyDriver) apiCall(ctx context.Context, method, path string, body []byte, out any) error {
	req, err := http.NewRequestWithContext(ctx, method, d.api+path, bytes.NewReader(body))
	if err != nil {
		return errProxy
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	response, err := d.client.Do(req)
	if err != nil {
		return errProxy
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return errProxy
	}
	b, err := io.ReadAll(io.LimitReader(response.Body, 16385))
	if err != nil || len(b) > 16384 {
		return errProxy
	}
	dec := json.NewDecoder(bytes.NewReader(b))
	dec.DisallowUnknownFields()
	if dec.Decode(out) != nil || dec.Decode(new(any)) != io.EOF {
		return errProxy
	}
	return nil
}
func (d *proxyDriver) configuration(ctx context.Context) ([2]bool, error) {
	var entries map[string]proxyConfig
	var enabled [2]bool
	if err := d.apiCall(ctx, "GET", "/proxies", nil, &entries); err != nil || len(entries) != 2 {
		return enabled, errProxy
	}
	for i, name := range []string{"pg-a", "pg-b"} {
		entry, ok := entries[name]
		listen := "[::]:15432"
		if i == 1 {
			listen = "[::]:25432"
		}
		if !ok || entry.Name != name || entry.Listen != listen || entry.Upstream != "postgres:5432" || len(entry.Toxics) != 0 {
			return enabled, errProxy
		}
		enabled[i] = entry.Enabled
	}
	return enabled, nil
}
func (d *proxyDriver) setEnabled(ctx context.Context, index int, enabled bool) error {
	name := "pg-a"
	if index == 1 {
		name = "pg-b"
	}
	body := []byte(`{"enabled":false}`)
	if enabled {
		body = []byte(`{"enabled":true}`)
	}
	var result proxyConfig
	return d.apiCall(ctx, "POST", "/proxies/"+name, body, &result)
}
func proxyDeadline(ctx context.Context) time.Time {
	end := time.Now().Add(500 * time.Millisecond)
	if deadline, ok := ctx.Deadline(); ok && deadline.Before(end) {
		end = deadline
	}
	return end
}

// SSLRequest causes PostgreSQL itself to reply N or S without credentials or a
// database mutation. A TCP handshake alone would not prove upstream traffic.
func postgresProbe(ctx context.Context, c net.Conn) error {
	if ctx.Err() != nil {
		return ctx.Err()
	}
	stop := context.AfterFunc(ctx, func() { c.Close() })
	defer stop()
	c.SetDeadline(proxyDeadline(ctx))
	if _, err := c.Write([]byte{0, 0, 0, 8, 4, 210, 22, 47}); err != nil {
		return errProxy
	}
	var b [1]byte
	if _, err := io.ReadFull(c, b[:]); err != nil || (b[0] != 'N' && b[0] != 'S') {
		return errProxy
	}
	return ctx.Err()
}
func (d *proxyDriver) connection(ctx context.Context, index int) (net.Conn, error) {
	c, err := (&net.Dialer{Timeout: 500 * time.Millisecond}).DialContext(ctx, "tcp", d.targets[index])
	if err != nil {
		return nil, err
	}
	if err = postgresProbe(ctx, c); err != nil {
		c.Close()
		return nil, err
	}
	return c, nil
}
func (d *proxyDriver) verify(ctx context.Context) (Status, error) {
	blank := Status{Generation: 1, State: d.state}
	if d.closing || ctx.Err() != nil {
		return blank, errProxy
	}
	if d.child != nil {
		st, err := d.child.Status(ctx)
		if err != nil || !st.Verified || st.State != "running" || st.Generation != 1 {
			return blank, errProxy
		}
	}
	enabled, err := d.configuration(ctx)
	if err != nil {
		return blank, err
	}
	for i := range enabled {
		want := !(d.state == "blocked-a" && i == 0 || d.state == "blocked-b" && i == 1)
		if enabled[i] != want {
			return blank, errProxy
		}
		if want {
			c, err := d.connection(ctx, i)
			if err != nil {
				return blank, errProxy
			}
			c.Close()
		} else {
			c, err := (&net.Dialer{Timeout: 500 * time.Millisecond}).DialContext(ctx, "tcp", d.targets[i])
			if err == nil {
				c.Close()
				return blank, errProxy
			}
			// A timeout, route failure or canceled caller cannot prove disabled listener.
			if !errors.Is(err, syscall.ECONNREFUSED) || ctx.Err() != nil {
				return blank, errProxy
			}
		}
	}
	blank.Verified = true
	return blank, nil
}
func (d *proxyDriver) Status(ctx context.Context) (Status, error) { return d.verify(ctx) }
func (d *proxyDriver) Apply(ctx context.Context, op string, generation uint64) (Status, error) {
	if d.closing || generation != 1 {
		return Status{}, errProxy
	}
	index := 0
	enabled := false
	switch op {
	case "block-a", "block-b":
		if d.state != "running" {
			return Status{}, errProxy
		}
		if op == "block-b" {
			index = 1
		}
	case "unblock-a":
		if d.state != "blocked-a" {
			return Status{}, errProxy
		}
		enabled = true
	case "unblock-b":
		if d.state != "blocked-b" {
			return Status{}, errProxy
		}
		index = 1
		enabled = true
	default:
		return Status{}, errProxy
	}
	if _, err := d.verify(ctx); err != nil {
		return Status{}, err
	}
	var existing net.Conn
	if !enabled {
		var err error
		existing, err = d.connection(ctx, index)
		if err != nil {
			return Status{}, err
		}
		defer existing.Close()
	}
	// Mark the potentially partial transition before the request: Restore must
	// still recover if HTTP acknowledgement is lost or verification fails.
	if enabled {
		d.state = "running"
	} else if index == 0 {
		d.state = "blocked-a"
	} else {
		d.state = "blocked-b"
	}
	if err := d.setEnabled(ctx, index, enabled); err != nil {
		return Status{}, err
	}
	if existing != nil {
		stop := context.AfterFunc(ctx, func() { existing.Close() })
		defer stop()
		existing.SetReadDeadline(proxyDeadline(ctx))
		var b [1]byte
		_, err := existing.Read(b[:])
		if err != io.EOF && !errors.Is(err, syscall.ECONNRESET) {
			return Status{}, errProxy
		}
	}
	return d.verify(ctx)
}
func (d *proxyDriver) Restore(ctx context.Context) error {
	if d.closing {
		return errProxy
	}
	if _, err := d.configuration(ctx); err != nil {
		return err
	}
	for i := 0; i < 2; i++ {
		if err := d.setEnabled(ctx, i, true); err != nil {
			return err
		}
	}
	d.state = "running"
	_, err := d.verify(ctx)
	return err
}
func (d *proxyDriver) Close(ctx context.Context) error {
	d.closing = true
	if d.closed {
		return nil
	}
	if d.child != nil {
		if err := d.child.Close(ctx); err != nil {
			return err
		}
	}
	d.client.CloseIdleConnections()
	d.closed = true
	return nil
}
