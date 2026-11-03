package main

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func rejects(f func()) (rejected bool) {
	defer func() { rejected = recover() != nil }()
	f()
	return false
}

func TestHTTPProbeBoundsBodyWithoutLoggingResponse(t *testing.T) {
	for _, tc := range []struct {
		name       string
		size       int
		wantReject bool
	}{{"bounded", 131072, false}, {"oversized", 131073, true}} {
		t.Run(tc.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte(strings.Repeat("x", tc.size))) }))
			defer server.Close()
			transport := &http.Transport{DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
				return (&net.Dialer{}).DialContext(ctx, network, server.Listener.Addr().String())
			}}
			previous := client
			client = &http.Client{Transport: transport, Timeout: 2 * time.Second}
			defer func() { client = previous; transport.CloseIdleConnections() }()
			if got := rejects(func() { status, body := get("/metrics"); require(status == 200 && len(body) == tc.size) }); got != tc.wantReject {
				t.Fatalf("rejected=%t, want %t", got, tc.wantReject)
			}
		})
	}
}

func TestUnavailableRequiresExplicitOwnUpAndAbsentDomain(t *testing.T) {
	for _, tc := range []struct {
		name       string
		metrics    map[string]uint64
		wantReject bool
	}{
		{"explicit down", map[string]uint64{"up": 0}, false},
		{"missing own up cannot prove down", map[string]uint64{}, true},
		{"healthy", map[string]uint64{"up": 1}, true},
		{"retained aof", map[string]uint64{"up": 0, "aof_enabled": 1}, true},
		{"retained memory", map[string]uint64{"up": 0, "memory_used_bytes": 1024}, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := rejects(func() { requireUnavailable(tc.metrics) }); got != tc.wantReject {
				t.Fatalf("rejected=%t, want %t", got, tc.wantReject)
			}
		})
	}
}
