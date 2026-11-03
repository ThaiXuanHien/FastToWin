package monitor

import (
	"context"
	"errors"
	"io"
	"log"
	"net"
	"net/http"
	"sync"
	"time"

	"github.com/ThaiXuanHien/FastToWin/tools/redis-monitor/internal/eventlog"
)

const maxHTTPConnections = 16

func metricsHandler(store *Store) http.Handler {
	handlers := make(chan struct{}, maxHTTPConnections)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		select {
		case handlers <- struct{}{}:
			defer func() { <-handlers }()
		default:
			fixedResponse(w, 503, "busy\n")
			return
		}
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		if r.ContentLength != 0 || len(r.TransferEncoding) != 0 {
			r.Close = true
			w.Header().Set("Connection", "close")
			fixedResponse(w, 400, "request_body_not_allowed\n")
			return
		}
		switch r.URL.Path {
		case "/metrics", "/health", "/ready":
		default:
			fixedResponse(w, 404, "not_found\n")
			return
		}
		if r.Method != http.MethodGet {
			w.Header().Set("Allow", "GET")
			fixedResponse(w, 405, "method_not_allowed\n")
			return
		}
		switch r.URL.Path {
		case "/metrics":
			w.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
			_, _ = w.Write(store.Metrics())
		case "/health":
			fixedResponse(w, 200, "ok\n")
		case "/ready":
			if store.Ready() {
				fixedResponse(w, 200, "ready\n")
			} else {
				fixedResponse(w, 503, "not_ready\n")
			}
		}
	})
}

func fixedResponse(w http.ResponseWriter, status int, body string) {
	w.WriteHeader(status)
	_, _ = io.WriteString(w, body)
}

type cappedListener struct {
	net.Listener
	slots chan struct{}
}

func (l *cappedListener) Accept() (net.Conn, error) {
	for {
		conn, err := l.Listener.Accept()
		if err != nil {
			return nil, err
		}
		select {
		case l.slots <- struct{}{}:
			return &countedConn{Conn: conn, release: func() { <-l.slots }}, nil
		default:
			_ = conn.Close()
		}
	}
}

type countedConn struct {
	net.Conn
	once    sync.Once
	release func()
}

func (c *countedConn) Close() error { err := c.Conn.Close(); c.once.Do(c.release); return err }

// Run owns the one poller and the bounded HTTP listener until cancellation.
func Run(ctx context.Context, cfg Config, reader Reader, out io.Writer) error {
	if !validAddress(cfg.Listen) {
		return Failure{"invalid"}
	}
	ln, err := net.Listen("tcp", cfg.Listen)
	if err != nil {
		return Failure{"connect"}
	}
	clock := realClock()
	return runService(ctx, reader, NewStore(clock), ln, clock, out)
}

func newHTTPServer(store *Store) *http.Server {
	return &http.Server{
		// net/http adds 4096 bytes of bufio slop to MaxHeaderBytes. Subtract
		// that allowance so the actual request-header read is bounded to 8 KiB.
		Handler: metricsHandler(store), MaxHeaderBytes: 8*1024 - 4096,
		ReadHeaderTimeout: 2 * time.Second, ReadTimeout: 2 * time.Second,
		WriteTimeout: 2 * time.Second, IdleTimeout: 10 * time.Second,
		ErrorLog: log.New(io.Discard, "", 0),
	}
}

func runService(parent context.Context, reader Reader, store *Store, ln net.Listener, clock Clock, out io.Writer) error {
	ctx, cancel := context.WithCancel(parent)
	defer cancel()
	defer ln.Close()
	output, closeLog := eventlog.Acquire(out)
	defer closeLog()
	events := &eventLog{out: output}
	server := newHTTPServer(store)
	listener := &cappedListener{Listener: ln, slots: make(chan struct{}, maxHTTPConnections)}
	pollDone := make(chan struct{})
	go func() { defer close(pollDone); poll(ctx, reader, store, clock, waitPoll, events) }()
	serveDone := make(chan error, 1)
	go func() { serveDone <- server.Serve(listener) }()
	events.write("started")
	var result error
	select {
	case <-parent.Done():
	case err := <-serveDone:
		if !errors.Is(err, http.ErrServerClosed) {
			result = Failure{"connect"}
			events.write("http_failed connect")
		}
	}
	cancel()
	// Reserve the bounded logger flush within the five-second stop budget.
	shutdown, stop := context.WithTimeout(context.Background(), 4900*time.Millisecond)
	defer stop()
	if err := server.Shutdown(shutdown); err != nil {
		_ = server.Close()
	}
	select {
	case <-pollDone:
	case <-shutdown.Done():
		result = Failure{"timeout"}
	}
	events.write("stopped")
	return result
}
