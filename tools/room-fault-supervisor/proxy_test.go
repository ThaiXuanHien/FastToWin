package supervisor

import (
	"context"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

// This fixture forwards real protocol bytes; the API can deliberately lie
// about disable so a successful configuration response cannot verify a fault.
type proxyFixture struct {
	mu                        sync.Mutex
	listeners                 [2]net.Listener
	conns                     [2][]net.Conn
	entries                   map[string]proxyConfig
	api                       *httptest.Server
	noop, redirect, oversized bool
	updates                   int
}

func newProxyFixture(t *testing.T) (*proxyDriver, *proxyFixture) {
	t.Helper()
	f := &proxyFixture{entries: map[string]proxyConfig{
		"pg-a": {Name: "pg-a", Listen: "[::]:15432", Upstream: "room-pilot-123456781234-pg.railway.internal:5432", Enabled: true},
		"pg-b": {Name: "pg-b", Listen: "[::]:25432", Upstream: "room-pilot-123456781234-pg.railway.internal:5432", Enabled: true},
	}}
	var targets [2]string
	for i := range targets {
		l, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		targets[i] = l.Addr().String()
		f.listeners[i] = l
		go f.serve(i, l)
	}
	f.api = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		f.mu.Lock()
		defer f.mu.Unlock()
		if f.redirect {
			http.Redirect(w, r, "http://127.0.0.1:1/untrusted", http.StatusTemporaryRedirect)
			return
		}
		if f.oversized {
			io.WriteString(w, strings.Repeat(" ", 20000))
			return
		}
		if r.Method == "GET" && r.URL.Path == "/proxies" {
			json.NewEncoder(w).Encode(f.entries)
			return
		}
		if r.Method != "POST" {
			http.Error(w, "method", 405)
			return
		}
		name := strings.TrimPrefix(r.URL.Path, "/proxies/")
		entry, ok := f.entries[name]
		if !ok {
			http.Error(w, "no creation", 404)
			return
		}
		var body struct {
			Enabled bool `json:"enabled"`
		}
		dec := json.NewDecoder(r.Body)
		dec.DisallowUnknownFields()
		if dec.Decode(&body) != nil {
			http.Error(w, "body", 400)
			return
		}
		f.updates++
		entry.Enabled = body.Enabled
		f.entries[name] = entry
		if !body.Enabled && !f.noop {
			index := 0
			if name == "pg-b" {
				index = 1
			}
			f.listeners[index].Close()
			for _, c := range f.conns[index] {
				c.Close()
			}
		}
		if body.Enabled {
			index := 0
			if name == "pg-b" {
				index = 1
			}
			l, err := net.Listen("tcp", targets[index])
			if err == nil {
				f.listeners[index] = l
				go f.serve(index, l)
			}
		}
		json.NewEncoder(w).Encode(entry)
	}))
	t.Cleanup(func() {
		f.api.Close()
		f.mu.Lock()
		defer f.mu.Unlock()
		for _, l := range f.listeners {
			l.Close()
		}
		for _, cs := range f.conns {
			for _, c := range cs {
				c.Close()
			}
		}
	})
	return &proxyDriver{client: proxyHTTPClient(), api: f.api.URL, targets: targets, upstream: "room-pilot-123456781234-pg.railway.internal:5432", state: "running"}, f
}
func (f *proxyFixture) serve(i int, l net.Listener) {
	for {
		c, err := l.Accept()
		if err != nil {
			return
		}
		f.mu.Lock()
		f.conns[i] = append(f.conns[i], c)
		f.mu.Unlock()
		go func() {
			defer c.Close()
			var b [8]byte
			for {
				if _, err := io.ReadFull(c, b[:]); err != nil {
					return
				}
				if b != [8]byte{0, 0, 0, 8, 4, 210, 22, 47} {
					return
				}
				if _, err := c.Write([]byte("N")); err != nil {
					return
				}
			}
		}()
	}
}
func TestProxyBlocksExistingAndFreshOnlySelectedListener(t *testing.T) {
	d, _ := newProxyFixture(t)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	existing, err := net.Dial("tcp", d.targets[0])
	if err != nil {
		t.Fatal(err)
	}
	defer existing.Close()
	if err = postgresProbe(ctx, existing); err != nil {
		t.Fatal(err)
	}
	st, err := d.Apply(ctx, "block-a", 1)
	if err != nil || st != (Status{Generation: 1, State: "blocked-a", Verified: true}) {
		t.Fatalf("block A: %+v %v", st, err)
	}
	existing.SetReadDeadline(time.Now().Add(time.Second))
	var b [1]byte
	if _, err = existing.Read(b[:]); err != io.EOF {
		t.Fatalf("existing A survived: %v", err)
	}
	c, err := net.DialTimeout("tcp", d.targets[0], time.Second)
	if err == nil {
		c.Close()
		t.Fatal("fresh A connected")
	}
	bconn, err := net.Dial("tcp", d.targets[1])
	if err != nil {
		t.Fatal(err)
	}
	defer bconn.Close()
	if err = postgresProbe(ctx, bconn); err != nil {
		t.Fatalf("B failed during A block: %v", err)
	}
	if err = d.Restore(ctx); err != nil {
		t.Fatal(err)
	}
	st, err = d.Status(ctx)
	if err != nil || st.State != "running" || !st.Verified {
		t.Fatalf("restore: %+v %v", st, err)
	}
	st, err = d.Apply(ctx, "block-b", 1)
	if err != nil || st.State != "blocked-b" {
		t.Fatalf("block B: %+v %v", st, err)
	}
	if _, err = d.Apply(ctx, "unblock-a", 1); err == nil {
		t.Fatal("wrong recovery accepted")
	}
	if _, err = d.Apply(ctx, "unblock-b", 1); err != nil {
		t.Fatal(err)
	}
}
func TestProxyRejectsNoopSuccess(t *testing.T) {
	d, f := newProxyFixture(t)
	f.noop = true
	if st, err := d.Apply(context.Background(), "block-a", 1); err == nil || st.Verified {
		t.Fatalf("no-op disable verified: %+v %v", st, err)
	}
}
func TestProxyRejectsMutationAndUnboundedAPI(t *testing.T) {
	for _, kind := range []string{"upstream", "listen", "extra", "toxic", "redirect", "oversized"} {
		t.Run(kind, func(t *testing.T) {
			d, f := newProxyFixture(t)
			switch kind {
			case "upstream":
				e := f.entries["pg-a"]
				e.Upstream = "untrusted:5432"
				f.entries["pg-a"] = e
			case "listen":
				e := f.entries["pg-b"]
				e.Listen = "0.0.0.0:5432"
				f.entries["pg-b"] = e
			case "extra":
				f.entries["extra"] = proxyConfig{Name: "extra"}
			case "toxic":
				e := f.entries["pg-a"]
				e.Toxics = []json.RawMessage{json.RawMessage(`{"name":"bad"}`)}
				f.entries["pg-a"] = e
			case "redirect":
				f.redirect = true
			case "oversized":
				f.oversized = true
			}
			if st, err := d.Status(context.Background()); err == nil || st.Verified {
				t.Fatalf("trusted %s: %+v %v", kind, st, err)
			}
			if _, err := d.Apply(context.Background(), "block-a", 1); err == nil {
				t.Fatal("mutated despite invalid configuration")
			}
			f.mu.Lock()
			defer f.mu.Unlock()
			if f.updates != 0 {
				t.Fatal("issued mutation after invalid preflight")
			}
		})
	}
}
func TestProxyLeaseRestoresWithoutRunnerAndStaysFailed(t *testing.T) {
	d, _ := newProxyFixture(t)
	now := time.Now()
	s, err := NewService(Config{FixtureID: "proxy-test", Role: "proxy", Token: strings.Repeat("a", 64), Deadline: now.Add(time.Hour), Clock: func() time.Time { return now }}, d)
	if err != nil {
		t.Fatal(err)
	}
	r := Request{FixtureID: "proxy-test", RequestID: "fault", Generation: 1, Operation: "block-a", LeaseMillis: 1000, DeadlineUnixMillis: now.Add(time.Minute).UnixMilli()}
	response, code := s.control(context.Background(), r)
	if code != 200 {
		t.Fatalf("fault: %+v", response)
	}
	now = now.Add(1100 * time.Millisecond)
	if err = s.Tick(context.Background()); err == nil {
		t.Fatal("expiry did not fail")
	}
	st, err := d.Status(context.Background())
	if err != nil || st.State != "running" || !st.Verified {
		t.Fatalf("expiry failed restore: %+v %v", st, err)
	}
	r.RequestID = "status"
	r.Operation = "status"
	r.LeaseMillis = 0
	r.Generation = 0
	response, code = s.control(context.Background(), r)
	if code != 503 || !response.Failed || response.ErrorCode != "lease_expired" || response.State != "running" {
		t.Fatalf("expiry not sticky: %+v", response)
	}
	if err = s.Close(context.Background()); err == nil || err.Error() != "lease_expired" {
		t.Fatalf("close: %v", err)
	}
	if _, err = d.Apply(context.Background(), "block-a", 1); err == nil {
		t.Fatal("mutation after close")
	}
	if err = d.Restore(context.Background()); err == nil {
		t.Fatal("restore after close")
	}
}

func TestProxyProbeCancellationInterruptsProtocolRead(t *testing.T) {
	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()
	dispatched := make(chan struct{})
	go func() { var b [8]byte; io.ReadFull(server, b[:]); close(dispatched) }()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan error, 1)
	go func() { done <- postgresProbe(ctx, client) }()
	<-dispatched
	cancel()
	select {
	case err := <-done:
		if err == nil {
			t.Fatal("canceled probe verified traffic")
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("canceled probe retained blocked protocol read")
	}
}

func TestProxyCloseRetriesChildCleanupWithoutResurrection(t *testing.T) {
	d, _ := newProxyFixture(t)
	child, _ := processFixture(t, "pg")
	d.child = child
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := d.Close(ctx); err == nil {
		t.Fatal("canceled child cleanup verified")
	}
	if err := d.Restore(context.Background()); err == nil {
		t.Fatal("restore during teardown")
	}
	if _, err := d.Apply(context.Background(), "block-a", 1); err == nil {
		t.Fatal("apply during teardown")
	}
	if err := d.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
	if _, err := child.Status(context.Background()); err == nil {
		t.Fatal("child survived close")
	}
	if err := d.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
}

func TestProxyPinnedToxiproxyReadbackShape(t *testing.T) {
	d, _ := newProxyFixture(t)
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, `{"pg-a":{"name":"pg-a","listen":"[::]:15432","upstream":"room-pilot-123456781234-pg.railway.internal:5432","enabled":true,"Logger":{},"toxics":[]},"pg-b":{"name":"pg-b","listen":"[::]:25432","upstream":"room-pilot-123456781234-pg.railway.internal:5432","enabled":true,"Logger":{},"toxics":[]}}`)
	}))
	defer api.Close()
	d.api = api.URL
	st, err := d.Status(context.Background())
	if err != nil || !st.Verified || st.State != "running" {
		t.Fatalf("pinned API healthy paths rejected: %+v %v", st, err)
	}
}

// A Docker-only alias must never hide a broken private DNS deployment.
func TestProxyMarkedFixtureDNSReadback(t *testing.T) {
	d, f := newProxyFixture(t)
	for name, entry := range f.entries {
		entry.Upstream = "room-pilot-123456781234-pg.railway.internal:5432"
		f.entries[name] = entry
	}
	st, err := d.Status(context.Background())
	if err != nil || !st.Verified {
		t.Fatalf("marked PostgreSQL DNS rejected: %+v %v", st, err)
	}
}

func TestProxyFixtureBindsExactConfigAndRejectsAliases(t *testing.T) {
	upstream, err := proxyUpstream("12345678-1234-5678-9012-345678901234")
	if err != nil || upstream != "room-pilot-123456781234-pg.railway.internal:5432" {
		t.Fatal(upstream, err)
	}
	for _, bad := range []string{"", "proxy-test", "12345678123456789012345678901234", "12345678-1234-5678-9012-345678901234\n", "ABCDEF78-1234-5678-9012-345678901234", "http://postgres:5432"} {
		if _, err := proxyUpstream(bad); err == nil {
			t.Fatalf("accepted invalid fixture %q", bad)
		}
	}
	file := filepath.Join(t.TempDir(), "proxies.json")
	if err := writeProxyConfig(file, upstream); err != nil {
		t.Fatal(err)
	}
	b, _ := os.ReadFile(file)
	var entries []proxyConfig
	if err := json.Unmarshal(b, &entries); err != nil {
		t.Fatal(err)
	}
	if len(entries) != 2 || entries[0].Name != "pg-a" || entries[0].Listen != "[::]:15432" || entries[1].Name != "pg-b" || entries[1].Listen != "[::]:25432" {
		t.Fatal(entries)
	}
	for _, e := range entries {
		if e.Upstream != upstream || !e.Enabled || len(e.Toxics) != 0 {
			t.Fatal(e)
		}
	}
	info, _ := os.Stat(file)
	if info.Mode().Perm() != 0400 {
		t.Fatal(info.Mode())
	}
	if err := writeProxyConfig(file, "evil:5432"); err == nil {
		t.Fatal("rewrote startup configuration")
	}
	for _, bad := range []string{"postgres:5432", "room-pilot-abcdefabcdef-pg.railway.internal:5432", "room-pilot-123456781234-pg.railway.internal:5433"} {
		d, f := newProxyFixture(t)
		e := f.entries["pg-a"]
		e.Upstream = bad
		f.entries["pg-a"] = e
		if st, err := d.Status(context.Background()); err == nil || st.Verified {
			t.Fatalf("accepted %q", bad)
		}
	}
}
