package monitor

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestHTTPEndpointsAndBodies(t *testing.T) {
	s := NewStore(newFakeClock().Clock())
	h := metricsHandler(s)
	for _, tc := range []struct {
		method, path, body string
		status             int
	}{
		{"GET", "/metrics", "", 200}, {"GET", "/health", "", 200}, {"GET", "/ready", "", 503},
		{"GET", "/secret-marker", "", 404}, {"POST", "/metrics", "", 405}, {"GET", "/metrics", "secret-marker", 400},
	} {
		r := httptest.NewRequest(tc.method, tc.path, strings.NewReader(tc.body))
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		if w.Code != tc.status || strings.Contains(w.Body.String(), "secret-marker") {
			t.Fatal(tc, w.Code, w.Body.String())
		}
		if tc.path == "/metrics" && tc.status == 200 && w.Header().Get("Content-Type") != "text/plain; version=0.0.4; charset=utf-8" {
			t.Fatal("wrong exposition type")
		}
	}
	s.Record(validSample(), nil, 0)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, httptest.NewRequest("GET", "/ready", nil))
	if w.Code != 200 {
		t.Fatal("fresh readiness")
	}
	r := httptest.NewRequest("GET", "/metrics", nil)
	r.ContentLength = -1
	r.TransferEncoding = []string{"chunked"}
	w = httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Code != 400 || w.Header().Get("Connection") != "close" {
		t.Fatal("chunked body accepted")
	}
}

func startService(t *testing.T, reader Reader) (string, *Store, context.CancelFunc, <-chan error) {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal("local test listen failed", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	c := realClock()
	s := NewStore(c)
	done := make(chan error, 1)
	go func() { done <- runService(ctx, reader, s, ln, c, io.Discard) }()
	return ln.Addr().String(), s, cancel, done
}

func dialHTTP(t *testing.T, address string) net.Conn {
	t.Helper()
	c, err := net.DialTimeout("tcp", address, time.Second)
	if err != nil {
		t.Fatal("local test dial failed", err)
	}
	t.Cleanup(func() { _ = c.Close() })
	return c
}

func TestHTTPRealLoopbackDeadlinesCapAndShutdown(t *testing.T) {
	entered := make(chan struct{})
	address, _, cancel, done := startService(t, readerFunc(func(ctx context.Context) (Sample, error) { close(entered); <-ctx.Done(); return Sample{}, ctx.Err() }))
	<-entered
	var held []net.Conn
	for i := 0; i < 16; i++ {
		c := dialHTTP(t, address)
		_, _ = io.WriteString(c, "GET /metrics HTTP/1.1\r\nHost: local\r\nX-Hold: ")
		held = append(held, c)
	}
	extra := dialHTTP(t, address)
	_ = extra.SetReadDeadline(time.Now().Add(time.Second))
	if n, err := extra.Read(make([]byte, 1)); err == nil || n != 0 {
		t.Fatal("excess connection was not closed")
	}
	_ = held[0].SetReadDeadline(time.Now().Add(3 * time.Second))
	if _, err := held[0].Read(make([]byte, 4096)); err == nil {
		t.Fatal("header timeout did not close connection")
	}
	for _, c := range held {
		_ = c.Close()
	}
	start := time.Now()
	cancel()
	select {
	case err := <-done:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("shutdown exceeded five seconds")
	}
	if time.Since(start) > 5*time.Second {
		t.Fatal("slow shutdown")
	}
}

func TestHTTPConcurrentSnapshotsDoNotCollect(t *testing.T) {
	var calls atomic.Int32
	address, store, cancel, done := startService(t, readerFunc(func(context.Context) (Sample, error) { calls.Add(1); return validSample(), nil }))
	client := &http.Client{Timeout: 2 * time.Second, Transport: &http.Transport{DisableKeepAlives: true}}
	var wg sync.WaitGroup
	for i := 0; i < 8; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 10; j++ {
				sample := validSample()
				sample.Values["memory_used_bytes"] = uint64(j)
				sample.Values["evicted_keys_total"] = uint64(j)
				store.Record(sample, nil, 0)
				response, err := client.Get("http://" + address + "/metrics")
				if err != nil {
					t.Error("local metrics request failed", err)
					return
				}
				body, err := io.ReadAll(response.Body)
				_ = response.Body.Close()
				if err != nil {
					t.Error(err)
					return
				}
				var memory, evicted string
				for _, line := range strings.Split(string(body), "\n") {
					if strings.HasPrefix(line, metricPrefix+"memory_used_bytes ") {
						memory = strings.TrimPrefix(line, metricPrefix+"memory_used_bytes ")
					}
					if strings.HasPrefix(line, metricPrefix+"evicted_keys_total ") {
						evicted = strings.TrimPrefix(line, metricPrefix+"evicted_keys_total ")
					}
				}
				if memory != evicted && !(memory == "123" && evicted == "7") {
					t.Error("torn snapshot", memory, evicted)
				}
			}
		}()
	}
	wg.Wait()
	if calls.Load() != 1 {
		t.Fatal("HTTP amplified collections", calls.Load())
	}
	cancel()
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}

func TestHTTPHeaderLimitAndNoBodyRead(t *testing.T) {
	address, _, cancel, done := startService(t, readerFunc(func(context.Context) (Sample, error) { return validSample(), nil }))
	for _, request := range []string{
		"GET /metrics HTTP/1.1\r\nHost: local\r\nX-Large: " + strings.Repeat("s", 9*1024) + "\r\n\r\n",
		"GET /metrics HTTP/1.1\r\nHost: local\r\nContent-Length: 99999\r\n\r\n",
	} {
		c := dialHTTP(t, address)
		_ = c.SetDeadline(time.Now().Add(time.Second))
		_, _ = io.WriteString(c, request)
		r, err := http.ReadResponse(bufio.NewReader(c), nil)
		if err != nil {
			t.Fatal(err)
		}
		if r.StatusCode != 431 && r.StatusCode != 400 {
			t.Fatal("oversized header/body accepted", r.StatusCode)
		}
		_ = r.Body.Close()
		_ = c.Close()
	}
	cancel()
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}

func TestRunErrorsAndLogsSanitized(t *testing.T) {
	var out bytes.Buffer
	err := Run(context.Background(), Config{Listen: "secret-marker"}, readerFunc(func(context.Context) (Sample, error) { t.Fatal("invalid listener polled"); return Sample{}, nil }), &out)
	if err == nil || strings.Contains(fmt.Sprint(err)+out.String(), "secret-marker") {
		t.Fatal("unsafe listener failure")
	}
}

func TestHTTPTimeoutConfiguration(t *testing.T) {
	server := newHTTPServer(NewStore(newFakeClock().Clock()))
	if server.ReadHeaderTimeout != 2*time.Second || server.ReadTimeout != 2*time.Second || server.WriteTimeout != 2*time.Second || server.IdleTimeout != 10*time.Second {
		t.Fatal("unbounded HTTP timeout configuration")
	}
	// Go adds a 4-KiB allowance to this configured value when reading headers.
	if server.MaxHeaderBytes+4096 != 8192 {
		t.Fatal("header read bound")
	}
}

func TestHTTPRealKeepAliveIdleDeadline(t *testing.T) {
	address, _, cancel, done := startService(t, readerFunc(func(context.Context) (Sample, error) { return validSample(), nil }))
	c := dialHTTP(t, address)
	_, _ = io.WriteString(c, "GET /health HTTP/1.1\r\nHost: local\r\n\r\n")
	reader := bufio.NewReader(c)
	response, err := http.ReadResponse(reader, nil)
	if err != nil {
		t.Fatal(err)
	}
	_, _ = io.Copy(io.Discard, response.Body)
	_ = response.Body.Close()
	start := time.Now()
	_ = c.SetReadDeadline(start.Add(12 * time.Second))
	_, err = reader.ReadByte()
	if err == nil || time.Since(start) < 9*time.Second || time.Since(start) > 11*time.Second {
		t.Fatal("idle deadline not enforced", time.Since(start), err)
	}
	cancel()
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}

func TestHTTPMalformedRequestsDoNotEchoData(t *testing.T) {
	address, _, cancel, done := startService(t, readerFunc(func(context.Context) (Sample, error) { return validSample(), nil }))
	for _, request := range []string{
		"GET /secret-marker HTTP/9.9\r\nHost: local\r\n\r\n",
		"GET /%secret-marker HTTP/1.1\r\nHost: local\r\n\r\n",
		"GET /metrics HTTP/1.1\r\nHost: local\r\nsecret-marker: bad\x00value\r\n\r\n",
	} {
		c := dialHTTP(t, address)
		_ = c.SetDeadline(time.Now().Add(time.Second))
		_, _ = io.WriteString(c, request)
		body, _ := io.ReadAll(c)
		if bytes.Contains(body, []byte("secret-marker")) {
			t.Fatal("request data echoed")
		}
		_ = c.Close()
	}
	cancel()
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}

type blockingWriter struct {
	*httptest.ResponseRecorder
	entered chan<- struct{}
	release <-chan struct{}
}

func (w blockingWriter) Write(data []byte) (int, error) {
	w.entered <- struct{}{}
	<-w.release
	return w.ResponseRecorder.Write(data)
}
func (w blockingWriter) WriteString(data string) (int, error) { return w.Write([]byte(data)) }

func TestHTTPHandlerCap(t *testing.T) {
	h := metricsHandler(NewStore(newFakeClock().Clock()))
	entered := make(chan struct{}, 16)
	release := make(chan struct{})
	var wg sync.WaitGroup
	for i := 0; i < 16; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			h.ServeHTTP(blockingWriter{httptest.NewRecorder(), entered, release}, httptest.NewRequest("GET", "/health", nil))
		}()
	}
	for i := 0; i < 16; i++ {
		<-entered
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, httptest.NewRequest("GET", "/health", nil))
	close(release)
	wg.Wait()
	if w.Code != 503 || w.Body.String() != "busy\n" {
		t.Fatal("handler cap not enforced", w.Code)
	}
}

type singleConnListener struct {
	conn     net.Conn
	accepted bool
	done     chan struct{}
	once     sync.Once
}

func (l *singleConnListener) Accept() (net.Conn, error) {
	if !l.accepted {
		l.accepted = true
		return l.conn, nil
	}
	<-l.done
	return nil, net.ErrClosed
}
func (l *singleConnListener) Close() error   { l.once.Do(func() { close(l.done) }); return nil }
func (l *singleConnListener) Addr() net.Addr { return l.conn.LocalAddr() }

type closeObservedConn struct {
	net.Conn
	closed chan struct{}
	once   sync.Once
}

func (c *closeObservedConn) Close() error {
	err := c.Conn.Close()
	c.once.Do(func() { close(c.closed) })
	return err
}

func TestHTTPStalledWriteDeadline(t *testing.T) {
	serverConn, clientConn := net.Pipe()
	defer clientConn.Close()
	observed := &closeObservedConn{Conn: serverConn, closed: make(chan struct{})}
	ln := &singleConnListener{conn: observed, done: make(chan struct{})}
	server := newHTTPServer(NewStore(newFakeClock().Clock()))
	done := make(chan error, 1)
	go func() { done <- server.Serve(ln) }()
	defer func() { _ = server.Close(); <-done }()
	start := time.Now()
	_, err := io.WriteString(clientConn, "GET /metrics HTTP/1.1\r\nHost: local\r\n\r\n")
	if err != nil {
		t.Fatal(err)
	}
	// net.Pipe has no write buffer, so a client that never reads deterministically
	// stalls the real HTTP server's first response write.
	select {
	case <-observed.closed:
	case <-time.After(3 * time.Second):
		t.Fatal("stalled write was not closed")
	}
	if time.Since(start) < 1800*time.Millisecond || time.Since(start) > 3*time.Second {
		t.Fatal("wrong write deadline", time.Since(start))
	}
}
