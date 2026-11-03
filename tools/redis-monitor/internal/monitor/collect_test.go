package monitor

import (
	"bufio"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"os"
	"reflect"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

const coreInfo = "used_memory:1024\r\nmaxmemory:67108864\r\nevicted_keys:0\r\naof_enabled:0\r\n"
const aofInfo = "used_memory:1024\r\nmaxmemory:0\r\nevicted_keys:18446744073709551615\r\naof_enabled:1\r\naof_current_size:12\r\naof_base_size:8\r\naof_last_write_status:err\r\naof_last_bgrewrite_status:err\r\naof_rewrite_in_progress:0\r\naof_pending_bio_fsync:1\r\naof_delayed_fsync:2\r\n"

type infoPeer struct {
	listener net.Listener
	replies  []string
	mu       sync.Mutex
	commands [][]string
	done     chan struct{}
	bad      bool
	fragment bool
}

func startPeer(t *testing.T, listener net.Listener, replies []string, fragment bool) *infoPeer {
	t.Helper()
	p := &infoPeer{listener: listener, replies: replies, done: make(chan struct{}), fragment: fragment}
	go func() {
		defer close(p.done)
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
		r := bufio.NewReader(conn)
		for _, reply := range replies {
			args, err := readTestCommand(r)
			if err != nil {
				return
			}
			p.mu.Lock()
			p.commands = append(p.commands, args)
			p.mu.Unlock()
			if p.fragment {
				for _, b := range []byte(reply) {
					if _, err = conn.Write([]byte{b}); err != nil {
						return
					}
				}
			} else {
				if _, err = io.WriteString(conn, reply); err != nil {
					return
				}
			}
		}
		b := make([]byte, 1)
		n, _ := conn.Read(b)
		if n > 0 {
			p.mu.Lock()
			p.bad = true
			p.mu.Unlock()
		}
	}()
	t.Cleanup(func() {
		_ = listener.Close()
		select {
		case <-p.done:
		case <-time.After(6 * time.Second):
			t.Error("peer failed to finish")
		}
	})
	return p
}
func listenLocal(t *testing.T) net.Listener {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	if err = l.(*net.TCPListener).SetDeadline(time.Now().Add(6 * time.Second)); err != nil {
		t.Fatal("set peer accept deadline")
	}
	return l
}
func startInfoPeer(t *testing.T, info string) *infoPeer {
	return startPeer(t, listenLocal(t), []string{"+OK\r\n", "+PONG\r\n", fmt.Sprintf("$%d\r\n%s\r\n", len(info), info)}, true)
}
func (p *infoPeer) Config(t *testing.T) Config {
	return Config{Address: p.listener.Addr().String(), PasswordFile: secretFile(t, testSecret)}
}
func (p *infoPeer) RequireCommands(t *testing.T, names ...string) {
	t.Helper()
	select {
	case <-p.done:
	case <-time.After(6 * time.Second):
		t.Fatal("peer completion timeout")
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.bad || len(p.commands) != len(names) {
		t.Fatal("unexpected command count")
	}
	for i, name := range names {
		want := strings.Split(name, " ")
		if name == "AUTH" {
			want = []string{"AUTH", "ftw_monitor", testSecret}
		}
		if !reflect.DeepEqual(p.commands[i], want) {
			t.Fatal("fixed RESP arguments mismatch (redacted)")
		}
	}
}
func readTestCommand(r *bufio.Reader) ([]string, error) {
	line, err := r.ReadString('\n')
	if err != nil {
		return nil, err
	}
	if len(line) < 4 || line[0] != '*' {
		return nil, fmt.Errorf("bad array")
	}
	n, err := strconv.Atoi(strings.TrimSuffix(line[1:], "\r\n"))
	if err != nil || n < 1 || n > 4 {
		return nil, fmt.Errorf("bad count")
	}
	args := make([]string, n)
	for i := range args {
		line, err = r.ReadString('\n')
		if err != nil || len(line) < 4 || line[0] != '$' {
			return nil, fmt.Errorf("bad bulk")
		}
		size, err := strconv.Atoi(strings.TrimSuffix(line[1:], "\r\n"))
		if err != nil || size < 0 || size > 128 {
			return nil, fmt.Errorf("bad length")
		}
		b := make([]byte, size+2)
		if _, err = io.ReadFull(r, b); err != nil {
			return nil, err
		}
		if string(b[size:]) != "\r\n" {
			return nil, fmt.Errorf("bad trailer")
		}
		args[i] = string(b[:size])
	}
	return args, nil
}

func TestAOFDisabledOmitsConditionalMetrics(t *testing.T) {
	p := startInfoPeer(t, coreInfo)
	r, err := NewReader(p.Config(t))
	if err != nil {
		t.Fatal(err)
	}
	got, err := r.Read(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	want := map[string]uint64{"memory_used_bytes": 1024, "memory_max_bytes": 67108864, "evicted_keys_total": 0, "aof_enabled": 0}
	if !reflect.DeepEqual(got.Values, want) {
		t.Fatal("metric map mismatch")
	}
	p.RequireCommands(t, "AUTH", "PING", "INFO memory persistence stats")
}
func TestAOFErrorsAreValidFaultGauges(t *testing.T) {
	p := startInfoPeer(t, aofInfo)
	r, _ := NewReader(p.Config(t))
	got, err := r.Read(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	want := map[string]uint64{"memory_used_bytes": 1024, "memory_max_bytes": 0, "evicted_keys_total": ^uint64(0), "aof_enabled": 1, "aof_current_size_bytes": 12, "aof_base_size_bytes": 8, "aof_last_write_ok": 0, "aof_last_rewrite_ok": 0, "aof_rewrite_in_progress": 0, "aof_pending_fsync": 1, "aof_delayed_fsync_total": 2}
	if !reflect.DeepEqual(got.Values, want) {
		t.Fatal("metric map mismatch")
	}
	p.RequireCommands(t, "AUTH", "PING", "INFO memory persistence stats")
}
func TestInfoValidation(t *testing.T) {
	for _, tc := range []struct{ name, info, reason string }{
		{"unknown", coreInfo + "unknown:SECRET_MARKER\r\n", ""}, {"comments", "# Memory\r\n\r\n" + coreInfo, ""},
		{"duplicate", coreInfo + "used_memory:2\r\n", "invalid"}, {"missing", strings.ReplaceAll(coreInfo, "used_memory:1024\r\n", ""), "missing"},
		{"negative", strings.ReplaceAll(coreInfo, "1024", "-1"), "invalid"}, {"overflow", strings.ReplaceAll(coreInfo, "1024", "18446744073709551616"), "invalid"},
		{"nan", strings.ReplaceAll(coreInfo, "1024", "NaN"), "invalid"}, {"plus", strings.ReplaceAll(coreInfo, "1024", "+1"), "invalid"},
		{"empty", strings.ReplaceAll(coreInfo, "1024", ""), "invalid"}, {"float", strings.ReplaceAll(coreInfo, "1024", "1.0"), "invalid"},
		{"flag", strings.ReplaceAll(coreInfo, "aof_enabled:0", "aof_enabled:2"), "invalid"},
		{"aof-required", strings.ReplaceAll(coreInfo, "aof_enabled:0", "aof_enabled:1"), "missing"},
		{"status", strings.ReplaceAll(aofInfo, "aof_last_write_status:err", "aof_last_write_status:SECRET_MARKER"), "invalid"},
		{"progress", strings.ReplaceAll(aofInfo, "aof_rewrite_in_progress:0", "aof_rewrite_in_progress:2"), "invalid"},
		{"conditional-present", coreInfo + "aof_current_size:6\r\n", ""}, {"malformed-selected", coreInfo + "aof_current_size\r\n", "invalid"},
		{"long-line", coreInfo + "unknown:" + strings.Repeat("x", 16385) + "\r\n", "oversized"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			p := startInfoPeer(t, tc.info)
			r, _ := NewReader(p.Config(t))
			got, err := r.Read(context.Background())
			if tc.reason != "" {
				requireReason(t, err, tc.reason)
				if got.Values != nil {
					t.Fatal("published partial sample")
				}
			} else if err != nil {
				t.Fatal(err)
			}
			p.RequireCommands(t, "AUTH", "PING", "INFO memory persistence stats")
		})
	}
}

func TestProtocolRejection(t *testing.T) {
	for _, tc := range []struct {
		name     string
		replies  []string
		reason   string
		commands []string
	}{
		{"auth-error", []string{"-SECRET_MARKER " + testSecret + "\r\n"}, "auth", []string{"AUTH"}},
		{"auth-wrong", []string{"+NO\r\n"}, "auth", []string{"AUTH"}},
		{"ping-error", []string{"+OK\r\n", "-SECRET_MARKER\r\n"}, "protocol", []string{"AUTH", "PING"}},
		{"ping-wrong", []string{"+OK\r\n", "+OK\r\n"}, "protocol", []string{"AUTH", "PING"}},
		{"info-type", []string{"+OK\r\n", "+PONG\r\n", "+SECRET_MARKER\r\n"}, "protocol", []string{"AUTH", "PING", "INFO memory persistence stats"}},
		{"negative-length", []string{"+OK\r\n", "+PONG\r\n", "$-1\r\n"}, "protocol", []string{"AUTH", "PING", "INFO memory persistence stats"}},
		{"bad-length", []string{"+OK\r\n", "+PONG\r\n", "$+3\r\n"}, "protocol", []string{"AUTH", "PING", "INFO memory persistence stats"}},
		{"oversized", []string{"+OK\r\n", "+PONG\r\n", "$1048577\r\n"}, "oversized", []string{"AUTH", "PING", "INFO memory persistence stats"}},
		{"long-header", []string{"+" + strings.Repeat("x", 257) + "\r\n"}, "oversized", []string{"AUTH"}},
		{"wrong-trailer", []string{"+OK\r\n", "+PONG\r\n", "$1\r\nx!!"}, "protocol", []string{"AUTH", "PING", "INFO memory persistence stats"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			p := startPeer(t, listenLocal(t), tc.replies, false)
			r, _ := NewReader(p.Config(t))
			_, err := r.Read(context.Background())
			requireReason(t, err, tc.reason)
			p.RequireCommands(t, tc.commands...)
		})
	}
}

func TestTruncatedReply(t *testing.T) {
	l := listenLocal(t)
	defer l.Close()
	go func() {
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		_, _ = readTestCommand(bufio.NewReader(c))
		_, _ = io.WriteString(c, "+O")
	}()
	r, _ := NewReader(Config{Address: l.Addr().String(), PasswordFile: secretFile(t, testSecret)})
	_, err := r.Read(context.Background())
	requireReason(t, err, "protocol")
}
func TestSecretReadOnce(t *testing.T) {
	p := startInfoPeer(t, coreInfo)
	cfg := p.Config(t)
	r, err := NewReader(cfg)
	if err != nil {
		t.Fatal(err)
	}
	if err = os.WriteFile(cfg.PasswordFile, []byte("SECRET_MARKER"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err = r.Read(context.Background()); err != nil {
		t.Fatal(err)
	}
	p.RequireCommands(t, "AUTH", "PING", "INFO memory persistence stats")
}

func TestAbsoluteDeadlineAndCancellation(t *testing.T) {
	for _, cancel := range []bool{false, true} {
		t.Run(fmt.Sprint(cancel), func(t *testing.T) {
			l := listenLocal(t)
			defer l.Close()
			done := make(chan struct{})
			go func() {
				defer close(done)
				c, err := l.Accept()
				if err != nil {
					return
				}
				defer c.Close()
				_, _ = readTestCommand(bufio.NewReader(c))
				for {
					if _, err = io.WriteString(c, "+"); err != nil {
						return
					}
					time.Sleep(200 * time.Millisecond)
				}
			}()
			r, _ := NewReader(Config{Address: l.Addr().String(), PasswordFile: secretFile(t, testSecret)})
			ctx, stop := context.WithCancel(context.Background())
			defer stop()
			if cancel {
				time.AfterFunc(100*time.Millisecond, stop)
			}
			start := time.Now()
			_, err := r.Read(ctx)
			elapsed := time.Since(start)
			requireReason(t, err, "timeout")
			if cancel {
				if elapsed > time.Second {
					t.Fatal("cancellation did not close connection")
				}
			} else if elapsed < 2800*time.Millisecond || elapsed > 4*time.Second {
				t.Fatal("absolute three-second deadline not enforced")
			}
			select {
			case <-done:
			case <-time.After(time.Second):
				t.Fatal("peer connection leaked")
			}
		})
	}
}

func testCertificate(t *testing.T) (tls.Certificate, *x509.CertPool) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	template := &x509.Certificate{SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "localhost"}, NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour), DNSNames: []string{"localhost"}, IPAddresses: []net.IP{net.ParseIP("127.0.0.1")}, KeyUsage: x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}, IsCA: true, BasicConstraintsValid: true}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatal(err)
	}
	roots := x509.NewCertPool()
	roots.AddCert(cert)
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key}, roots
}
func TestTLSVerification(t *testing.T) {
	cert, roots := testCertificate(t)
	for _, trusted := range []bool{false, true} {
		t.Run(fmt.Sprint(trusted), func(t *testing.T) {
			l := tls.NewListener(listenLocal(t), &tls.Config{Certificates: []tls.Certificate{cert}, MinVersion: tls.VersionTLS12})
			p := startPeer(t, l, []string{"+OK\r\n", "+PONG\r\n", fmt.Sprintf("$%d\r\n%s\r\n", len(coreInfo), coreInfo)}, false)
			cfg := p.Config(t)
			cfg.TLS = true
			r, err := NewReader(cfg)
			if err != nil {
				t.Fatal(err)
			}
			if trusted {
				r.(*redisReader).tlsConfig.RootCAs = roots
			}
			_, err = r.Read(context.Background())
			if trusted {
				if err != nil {
					t.Fatal(err)
				}
				p.RequireCommands(t, "AUTH", "PING", "INFO memory persistence stats")
			} else {
				requireReason(t, err, "connect")
				select {
				case <-p.done:
				case <-time.After(6 * time.Second):
					t.Fatal("TLS peer completion timeout")
				}
				p.mu.Lock()
				defer p.mu.Unlock()
				if len(p.commands) != 0 {
					t.Fatal("command sent over untrusted TLS")
				}
			}
		})
	}
}
func TestTLSHandshakeDeadline(t *testing.T) {
	l := listenLocal(t)
	defer l.Close()
	done := make(chan struct{})
	go func() {
		defer close(done)
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		_, _ = io.Copy(io.Discard, c)
	}()
	r, _ := NewReader(Config{Address: l.Addr().String(), TLS: true, PasswordFile: secretFile(t, testSecret)})
	ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
	defer cancel()
	start := time.Now()
	_, err := r.Read(ctx)
	requireReason(t, err, "timeout")
	if time.Since(start) > time.Second {
		t.Fatal("TLS cancellation ignored")
	}
	<-done
}

func TestDialFailureDoesNotRetryOrLeak(t *testing.T) {
	r, err := NewReader(Config{Address: "localhost:6379", PasswordFile: secretFile(t, testSecret)})
	if err != nil {
		t.Fatal(err)
	}
	attempts := 0
	r.(*redisReader).dial = func(ctx context.Context, network, address string) (net.Conn, error) {
		attempts++
		deadline, ok := ctx.Deadline()
		if !ok || time.Until(deadline) > 3*time.Second || time.Until(deadline) < 2*time.Second {
			t.Error("dial lacks absolute attempt deadline")
		}
		return nil, errors.New("SECRET_MARKER " + testSecret)
	}
	_, err = r.Read(context.Background())
	requireReason(t, err, "connect")
	if attempts != 1 {
		t.Fatal("dial retried")
	}
}

func TestDialTimeConsumesAbsoluteBudget(t *testing.T) {
	l := listenLocal(t)
	defer l.Close()
	done := make(chan struct{})
	go func() {
		defer close(done)
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		_, _ = io.Copy(io.Discard, c)
	}()
	r, err := NewReader(Config{Address: l.Addr().String(), PasswordFile: secretFile(t, testSecret)})
	if err != nil {
		t.Fatal(err)
	}
	client := r.(*redisReader)
	dial := client.dial
	client.dial = func(ctx context.Context, network, address string) (net.Conn, error) {
		// A private dial boundary models DNS/connect latency without external DNS.
		timer := time.NewTimer(time.Second)
		defer timer.Stop()
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-timer.C:
			return dial(ctx, network, address)
		}
	}
	start := time.Now()
	_, err = r.Read(context.Background())
	elapsed := time.Since(start)
	requireReason(t, err, "timeout")
	if elapsed < 2800*time.Millisecond || elapsed > 3600*time.Millisecond {
		t.Fatal("dial time was not part of absolute three-second budget")
	}
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("deadline leaked connection")
	}
}

func TestTLSRejectsPlaintextPeer(t *testing.T) {
	l := listenLocal(t)
	defer l.Close()
	done := make(chan struct{})
	go func() {
		defer close(done)
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		_ = c.SetDeadline(time.Now().Add(time.Second))
		_, _ = io.WriteString(c, "+SECRET_MARKER\r\n")
		_, _ = io.Copy(io.Discard, c)
	}()
	r, err := NewReader(Config{Address: l.Addr().String(), TLS: true, PasswordFile: secretFile(t, testSecret)})
	if err != nil {
		t.Fatal(err)
	}
	_, err = r.Read(context.Background())
	requireReason(t, err, "connect")
	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("bad TLS connection leaked")
	}
}
