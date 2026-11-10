package supervisor

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

const testToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

type recordingDriver struct {
	status          Status
	calls           int
	statusCalls     int
	restores        int
	closes          int
	applyError      error
	statusError     error
	restoreError    error
	closeError      error
	unverified      bool
	noChange        bool
	wrongGeneration bool
	onApply         func(context.Context)
	onStatus        func(context.Context)
	onRestore       func(context.Context)
	onClose         func(context.Context)
}

func (d *recordingDriver) Status(ctx context.Context) (Status, error) {
	d.statusCalls++
	if d.onStatus != nil {
		d.onStatus(ctx)
	}
	return d.status, d.statusError
}
func (d *recordingDriver) Apply(ctx context.Context, op string, generation uint64) (Status, error) {
	d.calls++
	if d.onApply != nil {
		d.onApply(ctx)
	}
	if d.applyError != nil {
		return Status{}, d.applyError
	}
	if !d.noChange {
		next := map[string]string{"pause": "paused", "resume": "running", "kill": "stopped", "start": "running", "block-a": "blocked-a", "block-b": "blocked-b", "unblock-a": "running", "unblock-b": "running"}[op]
		if op == "start" {
			generation++
		}
		if d.wrongGeneration {
			generation++
		}
		d.status = Status{Generation: generation, State: next, Verified: !d.unverified}
	}
	return d.status, ctx.Err()
}
func (d *recordingDriver) Restore(ctx context.Context) error {
	d.restores++
	if d.onRestore != nil {
		d.onRestore(ctx)
	}
	if d.restoreError != nil {
		return d.restoreError
	}
	if d.status.State == "stopped" {
		d.status.Generation++
	}
	d.status.State = "running"
	d.status.Verified = true
	return ctx.Err()
}
func (d *recordingDriver) Close(ctx context.Context) error {
	d.closes++
	if d.onClose != nil {
		d.onClose(ctx)
	}
	return d.closeError
}

func testService(t *testing.T, role string) (*Service, *recordingDriver, *time.Time) {
	t.Helper()
	now := time.Unix(1800000000, 0)
	d := &recordingDriver{status: Status{Generation: 1, State: "running", Verified: true}}
	s, err := NewService(Config{FixtureID: "fixture-1", Role: role, Token: testToken, Deadline: now.Add(time.Hour), Clock: func() time.Time { return now }}, d)
	if err != nil {
		t.Fatal(err)
	}
	return s, d, &now
}

func validRequest() Request {
	return Request{FixtureID: "fixture-1", RequestID: "request-1", Generation: 1, Operation: "pause", DeadlineUnixMillis: 1800003600000, LeaseMillis: 1000}
}

func send(t *testing.T, h http.Handler, token string, request Request) *httptest.ResponseRecorder {
	t.Helper()
	body, err := json.Marshal(request)
	if err != nil {
		t.Fatal(err)
	}
	return sendBody(h, token, string(body))
}

func sendBody(h http.Handler, token, body string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(http.MethodPost, "/control", strings.NewReader(body))
	r.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}

func sendCanceled(t *testing.T, h http.Handler, ctx context.Context, request Request) int {
	t.Helper()
	body, err := json.Marshal(request)
	if err != nil {
		t.Fatal(err)
	}
	r := httptest.NewRequest(http.MethodPost, "/control", strings.NewReader(string(body))).WithContext(ctx)
	r.Header.Set("Authorization", "Bearer "+testToken)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w.Code
}

// Removing authentication or fixture binding must let this test catch a process-control call.
func TestUnauthorizedAndWrongFixtureNeverApply(t *testing.T) {
	for _, tc := range []struct {
		name, token, fixture string
		code                 int
	}{
		{"bad-token", "wrong-token", "fixture-1", http.StatusUnauthorized},
		{"wrong-fixture", testToken, "other-fixture", http.StatusForbidden},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, d, _ := testService(t, "a")
			r := validRequest()
			r.FixtureID = tc.fixture
			before := d.calls
			reply := send(t, s, tc.token, r)
			if reply.Code != tc.code || d.calls != before {
				t.Fatalf("rejected request reached process control: code=%d calls=%d before=%d", reply.Code, d.calls, before)
			}
		})
	}
}

func response(t *testing.T, w *httptest.ResponseRecorder) Response {
	t.Helper()
	var r Response
	if err := json.Unmarshal(w.Body.Bytes(), &r); err != nil {
		t.Fatal(err)
	}
	return r
}

// Removing the exact six-field parser must fail before the driver's first call.
func TestMalformedBodiesNeverReachDriver(t *testing.T) {
	valid, _ := json.Marshal(validRequest())
	base := string(valid)
	for _, tc := range []struct {
		name, body string
		code       int
	}{
		{"unknown", strings.TrimSuffix(base, "}") + `,"extra":1}`, 400},
		{"wrong-case", strings.Replace(base, "fixtureId", "FixtureId", 1), 400},
		{"duplicate", strings.TrimSuffix(base, "}") + `,"operation":"pause"}`, 400},
		{"missing", strings.Replace(base, `"leaseMillis":1000`, `"ignored":1000`, 1), 400},
		{"null", strings.Replace(base, `"generation":1`, `"generation":null`, 1), 400},
		{"fraction", strings.Replace(base, `"generation":1`, `"generation":1.5`, 1), 400},
		{"negative-generation", strings.Replace(base, `"generation":1`, `"generation":-1`, 1), 400},
		{"extra-value", base + ` {}`, 400},
		{"array", `[]`, 400},
		{"empty", ``, 400},
		{"malformed", `{`, 400},
		{"too-large", strings.Repeat(" ", 4096) + base, 413},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, d, _ := testService(t, "a")
			w := sendBody(s, testToken, tc.body)
			if w.Code != tc.code || d.calls != 0 || d.statusCalls != 0 {
				t.Fatalf("code=%d calls=%d statusCalls=%d body=%s", w.Code, d.calls, d.statusCalls, w.Body)
			}
			if response(t, w).ErrorCode == "" {
				t.Fatal("missing fixed error code")
			}
		})
	}
}

func TestWrongRouteMethodAndAuthorizationNeverReachDriver(t *testing.T) {
	for _, tc := range []struct {
		method, path, auth string
		code               int
	}{
		{"GET", "/control", "Bearer " + testToken, 405},
		{"POST", "/other", "Bearer " + testToken, 404},
		{"POST", "/control?operation=kill", "Bearer " + testToken, 400},
		{"POST", "/control", "", 401},
		{"POST", "/control", testToken, 401},
		{"POST", "/control", "bearer " + testToken, 401},
		{"POST", "/control", "Bearer " + strings.ToUpper(testToken), 401},
	} {
		t.Run(fmt.Sprintf("%s-%s-%d", tc.method, tc.path, tc.code), func(t *testing.T) {
			s, d, _ := testService(t, "a")
			b, _ := json.Marshal(validRequest())
			r := httptest.NewRequest(tc.method, tc.path, strings.NewReader(string(b)))
			r.Header.Set("Authorization", tc.auth)
			w := httptest.NewRecorder()
			s.ServeHTTP(w, r)
			if w.Code != tc.code || d.calls != 0 || d.statusCalls != 0 {
				t.Fatalf("code=%d calls=%d status=%d", w.Code, d.calls, d.statusCalls)
			}
		})
	}
	s, d, _ := testService(t, "a")
	r := httptest.NewRequest("POST", "/control", strings.NewReader(`{}`))
	r.Header.Add("Authorization", "Bearer "+testToken)
	r.Header.Add("Authorization", "Bearer "+testToken)
	w := httptest.NewRecorder()
	s.ServeHTTP(w, r)
	if w.Code != 401 || d.calls != 0 {
		t.Fatal("multiple authorization headers accepted")
	}
}

func TestResponseContainsOnlyFixedFields(t *testing.T) {
	s, _, _ := testService(t, "a")
	w := send(t, s, testToken, validRequest())
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(w.Body.Bytes(), &fields); err != nil {
		t.Fatal(err)
	}
	if len(fields) != 7 {
		t.Fatalf("wrong response fields: %v", fields)
	}
	for _, key := range []string{"requestId", "role", "generation", "state", "verified", "failed", "errorCode"} {
		if _, ok := fields[key]; !ok {
			t.Fatalf("missing %s", key)
		}
	}
	if w.Header().Get("Content-Type") != "application/json" || w.Header().Get("Cache-Control") != "no-store" {
		t.Fatal("unsafe response headers")
	}
}

func TestConcurrentDuplicateRequestsApplyOnce(t *testing.T) {
	s, d, _ := testService(t, "a")
	var wg sync.WaitGroup
	for range 32 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			w := send(t, s, testToken, validRequest())
			if w.Code != 200 {
				t.Errorf("code=%d body=%s", w.Code, w.Body)
			}
		}()
	}
	wg.Wait()
	if d.calls != 1 {
		t.Fatalf("duplicate calls=%d", d.calls)
	}
}

func TestExactlyFourKiBBodyIsAccepted(t *testing.T) {
	s, d, _ := testService(t, "a")
	body, err := json.Marshal(validRequest())
	if err != nil {
		t.Fatal(err)
	}
	w := sendBody(s, testToken, string(body)+strings.Repeat(" ", 4096-len(body)))
	if w.Code != 200 || d.calls != 1 {
		t.Fatalf("4 KiB boundary rejected: code=%d calls=%d body=%s", w.Code, d.calls, w.Body)
	}
}
