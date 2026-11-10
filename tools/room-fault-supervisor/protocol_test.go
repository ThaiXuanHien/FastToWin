package supervisor

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"testing"
	"time"
)

// Loosening constructor validation must allow an unsafe fixture to start.
func TestRejectInvalidConfiguration(t *testing.T) {
	now := time.Unix(1800000000, 0)
	valid := Config{FixtureID: "fixture-1", Role: "a", Token: testToken, Deadline: now.Add(time.Hour), Clock: func() time.Time { return now }}
	for _, change := range []func(*Config){
		func(c *Config) { c.FixtureID = "" }, func(c *Config) { c.FixtureID = strings.Repeat("x", 129) }, func(c *Config) { c.FixtureID = "bad fixture" },
		func(c *Config) { c.Role = "backend" }, func(c *Config) { c.Token = "short" }, func(c *Config) { c.Token = strings.ToUpper(testToken) },
		func(c *Config) { c.Deadline = now }, func(c *Config) { c.Deadline = now.Add(2*time.Hour + time.Millisecond) },
	} {
		c := valid
		change(&c)
		if _, err := NewService(c, &recordingDriver{}); err == nil {
			t.Fatalf("accepted invalid config: role=%s fixture=%s", c.Role, c.FixtureID)
		}
	}
	if _, err := NewService(valid, nil); err == nil {
		t.Fatal("accepted nil driver")
	}
}

func TestRequestBindingsRejectBeforeApply(t *testing.T) {
	for _, tc := range []struct {
		name   string
		mutate func(*Request)
		code   int
	}{
		{"zero-generation", func(r *Request) { r.Generation = 0 }, 409},
		{"wrong-generation", func(r *Request) { r.Generation = 2 }, 409},
		{"expired-request", func(r *Request) { r.DeadlineUnixMillis = 1800000000000 }, 408},
		{"outside-fixture", func(r *Request) { r.DeadlineUnixMillis = 1800003600001 }, 400},
		{"zero-lease", func(r *Request) { r.LeaseMillis = 0 }, 400},
		{"negative-lease", func(r *Request) { r.LeaseMillis = -1 }, 400},
		{"too-long-lease", func(r *Request) { r.LeaseMillis = 60001 }, 400},
		{"unknown-op", func(r *Request) { r.Operation = "exec" }, 400},
		{"empty-id", func(r *Request) { r.RequestID = "" }, 400},
		{"long-id", func(r *Request) { r.RequestID = strings.Repeat("x", 129) }, 400},
		{"unsafe-id", func(r *Request) { r.RequestID = "secret\nvalue" }, 400},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, d, _ := testService(t, "a")
			r := validRequest()
			tc.mutate(&r)
			w := send(t, s, testToken, r)
			if w.Code != tc.code || d.calls != 0 {
				t.Fatalf("code=%d calls=%d body=%s", w.Code, d.calls, w.Body)
			}
		})
	}
}

func TestRoleAllowlist(t *testing.T) {
	for _, role := range []string{"a", "b", "pg", "redis", "proxy"} {
		for _, op := range []string{"pause", "resume", "kill", "start", "block-a", "block-b", "unblock-a", "unblock-b"} {
			allowed := (role == "a" || role == "b") && (op == "pause" || op == "resume" || op == "kill" || op == "start") || (role == "pg" || role == "redis") && (op == "pause" || op == "resume") || role == "proxy" && strings.Contains(op, "block-")
			if allowed {
				continue
			}
			t.Run(role+"-"+op, func(t *testing.T) {
				s, d, _ := testService(t, role)
				r := validRequest()
				r.Operation = op
				w := send(t, s, testToken, r)
				if w.Code != 400 || d.calls != 0 {
					t.Fatalf("allowlist failed: %d %s", w.Code, w.Body)
				}
			})
		}
	}
}

func TestFaultRecoveryAndGeneration(t *testing.T) {
	for _, tc := range []struct {
		role, fault, recover string
		next                 uint64
	}{
		{"a", "pause", "resume", 1}, {"b", "kill", "start", 2}, {"pg", "pause", "resume", 1}, {"redis", "pause", "resume", 1},
		{"proxy", "block-a", "unblock-a", 1}, {"proxy", "block-b", "unblock-b", 1},
	} {
		t.Run(tc.role+"-"+tc.fault, func(t *testing.T) {
			s, d, _ := testService(t, tc.role)
			r := validRequest()
			r.Operation = tc.fault
			w := send(t, s, testToken, r)
			if w.Code != 200 || !response(t, w).Verified {
				t.Fatalf("inject: %d %s", w.Code, w.Body)
			}
			r.RequestID = "recover"
			r.Operation = tc.recover
			r.LeaseMillis = 0
			w = send(t, s, testToken, r)
			got := response(t, w)
			if w.Code != 200 || got.State != "running" || got.Generation != tc.next || got.Failed || d.calls != 2 {
				t.Fatalf("recover: %d %+v calls=%d", w.Code, got, d.calls)
			}
			r.RequestID = "status"
			r.Operation = "status"
			r.Generation = 0
			w = send(t, s, testToken, r)
			if w.Code != 200 || response(t, w).Generation != tc.next || d.calls != 2 {
				t.Fatalf("status: %d %s", w.Code, w.Body)
			}
		})
	}
}

func TestReplayCanonicalAndConflictingFields(t *testing.T) {
	s, d, _ := testService(t, "a")
	original := validRequest()
	first := send(t, s, testToken, original)
	if first.Code != 200 {
		t.Fatal(first.Body)
	}
	// Semantic field equality survives JSON order and whitespace differences.
	body := `{ "leaseMillis":1000,"operation":"pause","generation":1,"fixtureId":"fixture-1","deadlineUnixMillis":1800003600000,"requestId":"request-1" }`
	w := sendBody(s, testToken, body)
	if w.Code != 200 || w.Body.String() != first.Body.String() || d.calls != 1 {
		t.Fatalf("replay changed: %d %s calls=%d", w.Code, w.Body, d.calls)
	}
	for _, change := range []func(*Request){func(r *Request) { r.Operation = "kill" }, func(r *Request) { r.Generation = 2 }, func(r *Request) { r.LeaseMillis = 500 }, func(r *Request) { r.DeadlineUnixMillis-- }} {
		r := original
		change(&r)
		w = send(t, s, testToken, r)
		if w.Code != 409 || response(t, w).ErrorCode != "request_conflict" || d.calls != 1 {
			t.Fatalf("conflict: %d %s calls=%d", w.Code, w.Body, d.calls)
		}
	}
}

func TestOnlyOneActiveFaultAndMatchingRecovery(t *testing.T) {
	for _, tc := range []struct {
		role, first, second string
		lease               int64
	}{
		{"a", "pause", "kill", 1000}, {"a", "kill", "pause", 1000}, {"proxy", "block-a", "block-b", 1000}, {"proxy", "block-a", "unblock-b", 0},
	} {
		t.Run(tc.first+"-"+tc.second, func(t *testing.T) {
			s, d, _ := testService(t, tc.role)
			r := validRequest()
			r.Operation = tc.first
			if send(t, s, testToken, r).Code != 200 {
				t.Fatal("first fault failed")
			}
			r.RequestID = "second"
			r.Operation = tc.second
			r.LeaseMillis = tc.lease
			w := send(t, s, testToken, r)
			if w.Code != 409 || d.calls != 1 {
				t.Fatalf("second fault accepted: %d %s calls=%d", w.Code, w.Body, d.calls)
			}
		})
	}
}

func TestNoopRecoveryAndInjectionFail(t *testing.T) {
	for _, op := range []string{"resume", "start"} {
		t.Run(op, func(t *testing.T) {
			s, d, _ := testService(t, "a")
			r := validRequest()
			r.Operation = op
			r.LeaseMillis = 0
			if w := send(t, s, testToken, r); w.Code != 409 || d.calls != 0 {
				t.Fatalf("no-op: %d %s", w.Code, w.Body)
			}
		})
	}
	s, d, _ := testService(t, "a")
	d.status.State = "paused"
	if w := send(t, s, testToken, validRequest()); w.Code != 409 || d.calls != 0 {
		t.Fatalf("no-op injection: %d %s", w.Code, w.Body)
	}
}

func TestHistoryExhaustionFailsClosedWithoutEviction(t *testing.T) {
	s, d, _ := testService(t, "a")
	r := validRequest()
	r.Operation = "status"
	r.LeaseMillis = 0
	r.Generation = 0
	for i := 0; i < 256; i++ {
		r.RequestID = fmt.Sprintf("status-%d", i)
		if w := send(t, s, testToken, r); w.Code != 200 {
			t.Fatalf("slot %d: %d %s", i, w.Code, w.Body)
		}
	}
	before := d.statusCalls
	r.RequestID = "overflow"
	r.Operation = "pause"
	r.Generation = 1
	r.LeaseMillis = 1000
	w := send(t, s, testToken, r)
	if w.Code != 503 || response(t, w).ErrorCode != "request_limit" || d.calls != 0 || d.statusCalls != before {
		t.Fatalf("exhaustion: %d %s", w.Code, w.Body)
	}
	r.RequestID = "status-0"
	r.Operation = "status"
	r.Generation = 0
	r.LeaseMillis = 0
	if w = send(t, s, testToken, r); w.Code != 200 || d.statusCalls != before {
		t.Fatalf("old identity evicted: %d %s", w.Code, w.Body)
	}
}

func TestVerificationAndDriverErrorsRestoreAndFailSafely(t *testing.T) {
	for _, tc := range []struct {
		name   string
		change func(*recordingDriver)
		code   string
	}{
		{"unverified", func(d *recordingDriver) { d.unverified = true }, "verification_failed"},
		{"no-change", func(d *recordingDriver) { d.noChange = true }, "verification_failed"},
		{"wrong-generation", func(d *recordingDriver) { d.wrongGeneration = true }, "verification_failed"},
		{"apply-error", func(d *recordingDriver) { d.applyError = errors.New("raw-secret-driver-exception") }, "driver_error"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			s, d, _ := testService(t, "a")
			tc.change(d)
			w := send(t, s, testToken, validRequest())
			got := response(t, w)
			if w.Code != 503 || !got.Failed || got.ErrorCode != tc.code || strings.Contains(w.Body.String(), "raw-secret") || d.restores != 1 {
				t.Fatalf("unsafe failure: %d %+v restores=%d", w.Code, got, d.restores)
			}
			r := validRequest()
			r.RequestID = "after"
			r.Operation = "kill"
			if w = send(t, s, testToken, r); w.Code != 503 || !response(t, w).Failed || d.calls != 1 {
				t.Fatalf("failure not latched: %d %s", w.Code, w.Body)
			}
		})
	}
}

func TestStatusFailureNeverMutatesOrLeaks(t *testing.T) {
	s, d, _ := testService(t, "a")
	d.statusError = errors.New("raw-secret-status-exception")
	w := send(t, s, testToken, validRequest())
	if w.Code != 503 || d.calls != 0 || strings.Contains(w.Body.String(), "raw-secret") || response(t, w).ErrorCode != "driver_error" {
		t.Fatalf("status error: %d %s", w.Code, w.Body)
	}
}

func TestLeaseExpiryRestoresAndPermanentlyFailsIncludingReplay(t *testing.T) {
	s, d, now := testService(t, "a")
	original := validRequest()
	if w := send(t, s, testToken, original); w.Code != 200 {
		t.Fatal(w.Body)
	}
	*now = now.Add(time.Second)
	if err := s.Tick(context.Background()); err == nil || err.Error() != "lease_expired" {
		t.Fatalf("tick=%v", err)
	}
	if d.restores != 1 || d.status.State != "running" {
		t.Fatal("lease did not restore")
	}
	for _, r := range []Request{original, {FixtureID: "fixture-1", RequestID: "status-after", Operation: "status", DeadlineUnixMillis: 1800003600000}} {
		w := send(t, s, testToken, r)
		got := response(t, w)
		if w.Code != 503 || !got.Failed || got.ErrorCode != "lease_expired" || got.State != "running" {
			t.Fatalf("expired case succeeded: %d %+v", w.Code, got)
		}
	}
	_ = s.Tick(context.Background())
	if d.restores != 1 {
		t.Fatal("restored expired fault twice")
	}
}

func TestRequestAfterLeaseExpiryRestoresBeforeApplying(t *testing.T) {
	s, d, now := testService(t, "a")
	if send(t, s, testToken, validRequest()).Code != 200 {
		t.Fatal("injection failed")
	}
	*now = now.Add(2 * time.Second)
	r := validRequest()
	r.RequestID = "later"
	r.Operation = "kill"
	w := send(t, s, testToken, r)
	if w.Code != 503 || d.calls != 1 || d.restores != 1 || !response(t, w).Failed {
		t.Fatalf("lease bypass: %d %s", w.Code, w.Body)
	}
}

func TestFixtureDeadlineBoundsLeaseAndRestores(t *testing.T) {
	s, d, now := testService(t, "a")
	*now = now.Add(time.Hour - 500*time.Millisecond)
	r := validRequest()
	r.LeaseMillis = 60000
	if w := send(t, s, testToken, r); w.Code != 200 {
		t.Fatalf("bounded lease: %d %s", w.Code, w.Body)
	}
	*now = now.Add(500 * time.Millisecond)
	if err := s.Tick(context.Background()); err == nil || err.Error() != "fixture_expired" || d.restores != 1 {
		t.Fatalf("deadline: err=%v restores=%d", err, d.restores)
	}
}

func TestCanceledAndExpiredRequestsNeverApply(t *testing.T) {
	s, d, _ := testService(t, "a")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	r := validRequest()
	b := sendCanceled(t, s, ctx, r)
	if b != http.StatusRequestTimeout || d.calls != 0 {
		t.Fatalf("canceled request reached driver: code=%d calls=%d", b, d.calls)
	}
}

func TestApplyContextUsesRequestDeadlineAndCancellation(t *testing.T) {
	s, d, now := testService(t, "a")
	r := validRequest()
	r.DeadlineUnixMillis = now.Add(30 * time.Millisecond).UnixMilli()
	d.onApply = func(ctx context.Context) {
		deadline, ok := ctx.Deadline()
		if !ok || time.Until(deadline) > 100*time.Millisecond {
			t.Error("request deadline not propagated")
			return
		}
		select {
		case <-ctx.Done():
		case <-time.After(200 * time.Millisecond):
			t.Error("driver context did not cancel")
		}
	}
	w := send(t, s, testToken, r)
	if w.Code != 503 || !response(t, w).Failed || d.restores != 1 {
		t.Fatalf("canceled apply not restored: %d %s", w.Code, w.Body)
	}
}

func TestCloseRestoresClosesAndRejectsFurtherMutation(t *testing.T) {
	s, d, _ := testService(t, "a")
	if send(t, s, testToken, validRequest()).Code != 200 {
		t.Fatal("inject failed")
	}
	for _, hook := range []*func(context.Context){&d.onRestore, &d.onClose} {
		*hook = func(ctx context.Context) {
			deadline, ok := ctx.Deadline()
			if !ok || time.Until(deadline) > 5*time.Second+time.Millisecond {
				t.Error("cleanup context unbounded")
			}
			if ctx.Err() != nil {
				t.Error("cleanup inherits cancellation")
			}
		}
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := s.Close(ctx); err != nil {
		t.Fatal(err)
	}
	if d.restores != 1 || d.closes != 1 {
		t.Fatalf("close cleanup restores=%d closes=%d", d.restores, d.closes)
	}
	if err := s.Close(context.Background()); err != nil || d.closes != 1 {
		t.Fatal("close not idempotent")
	}
	r := validRequest()
	r.RequestID = "after-close"
	if w := send(t, s, testToken, r); w.Code != 503 || d.calls != 1 {
		t.Fatalf("closed service mutated: %d %s", w.Code, w.Body)
	}
}

func TestTickAndRequestsSerializeDriverCalls(t *testing.T) {
	s, d, now := testService(t, "a")
	entered := make(chan struct{})
	release := make(chan struct{})
	done := make(chan struct{})
	d.onApply = func(context.Context) { close(entered); <-release }
	go func() { defer close(done); send(t, s, testToken, validRequest()) }()
	<-entered
	var wg sync.WaitGroup
	wg.Add(1)
	go func() { defer wg.Done(); _ = s.Tick(context.Background()) }()
	close(release)
	<-done
	wg.Wait()
	*now = now.Add(time.Second)
	if err := s.Tick(context.Background()); err == nil || d.restores != 1 {
		t.Fatal("serialized tick lost active fault")
	}
}

func TestRestoreFailureRetriesWithoutClearingPermanentFailure(t *testing.T) {
	s, d, now := testService(t, "a")
	if send(t, s, testToken, validRequest()).Code != 200 {
		t.Fatal("inject failed")
	}
	*now = now.Add(time.Second)
	d.restoreError = errors.New("raw-secret-restore-exception")
	if err := s.Tick(context.Background()); err == nil || strings.Contains(err.Error(), "secret") {
		t.Fatalf("unsafe restore error: %v", err)
	}
	d.restoreError = nil
	_ = s.Tick(context.Background())
	if d.restores != 2 || d.status.State != "running" {
		t.Fatal("failed restoration not retried")
	}
	r := validRequest()
	r.RequestID = "status"
	r.Operation = "status"
	r.LeaseMillis = 0
	r.Generation = 0
	w := send(t, s, testToken, r)
	if !response(t, w).Failed || w.Code != 503 {
		t.Fatal("restoration erased failed status")
	}
}

// A driver may acknowledge a canceled Status call with nil error; the service
// must still reject its result instead of beginning a new process mutation.
func TestStatusCannotConsumeDeadlineThenStartApply(t *testing.T) {
	s, d, now := testService(t, "a")
	r := validRequest()
	r.DeadlineUnixMillis = now.Add(20 * time.Millisecond).UnixMilli()
	d.onStatus = func(ctx context.Context) {
		if d.statusCalls == 1 {
			<-ctx.Done()
		}
	}
	w := send(t, s, testToken, r)
	if w.Code != 408 || d.calls != 0 {
		t.Fatalf("Apply began after request deadline: code=%d calls=%d body=%s", w.Code, d.calls, w.Body)
	}
}

func TestStatusCannotConsumeActiveLeaseThenStartRecovery(t *testing.T) {
	s, d, now := testService(t, "a")
	r := validRequest()
	if send(t, s, testToken, r).Code != 200 {
		t.Fatal("inject failed")
	}
	d.onStatus = func(context.Context) {
		if d.statusCalls == 3 {
			*now = now.Add(time.Second)
		}
	}
	r.RequestID = "late-recovery"
	r.Operation = "resume"
	r.LeaseMillis = 0
	w := send(t, s, testToken, r)
	if w.Code != 503 || response(t, w).ErrorCode != "lease_expired" || d.calls != 1 || d.restores != 1 {
		t.Fatalf("late recovery: code=%d calls=%d restores=%d body=%s", w.Code, d.calls, d.restores, w.Body)
	}
}

func TestReturnedStatusMustMatchFreshObservation(t *testing.T) {
	s, d, _ := testService(t, "a")
	d.onStatus = func(context.Context) {
		if d.statusCalls == 2 {
			d.status.State = "running"
		}
	}
	w := send(t, s, testToken, validRequest())
	if w.Code != 503 || !response(t, w).Failed || response(t, w).ErrorCode != "verification_failed" || d.restores != 1 {
		t.Fatalf("false acknowledgement accepted: %d %s", w.Code, w.Body)
	}
}

func TestFailedRestoreCannotReportStaleVerifiedRunningState(t *testing.T) {
	s, d, _ := testService(t, "a")
	d.applyError = errors.New("unsafe-partial-injection")
	d.restoreError = errors.New("unsafe-restore")
	w := send(t, s, testToken, validRequest())
	if w.Code != 503 || response(t, w).Verified || !response(t, w).Failed {
		t.Fatalf("unverified cleanup reported verified: %d %s", w.Code, w.Body)
	}
}

func TestCloseAfterLeaseExpiryCannotEraseFailedCase(t *testing.T) {
	s, d, now := testService(t, "a")
	if send(t, s, testToken, validRequest()).Code != 200 {
		t.Fatal("inject failed")
	}
	*now = now.Add(time.Second)
	if err := s.Close(context.Background()); err == nil || err.Error() != "lease_expired" {
		t.Fatalf("Close erased lease failure: %v", err)
	}
	if d.restores != 1 || d.closes != 1 {
		t.Fatal("expired close did not clean up once")
	}
	r := validRequest()
	r.RequestID = "after-expired-close"
	if w := send(t, s, testToken, r); w.Code != 503 || !response(t, w).Failed {
		t.Fatalf("closed expiry not sticky: %d %s", w.Code, w.Body)
	}
}
