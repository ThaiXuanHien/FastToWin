package monitor

import (
	"errors"
	"strings"
	"sync"
	"testing"
	"time"
)

type fakeClock struct {
	mu   sync.Mutex
	wall time.Time
	mono time.Duration
}

func newFakeClock() *fakeClock { return &fakeClock{wall: time.Unix(1800000000, 0)} }
func (c *fakeClock) Clock() Clock {
	return Clock{WallNow: func() time.Time { c.mu.Lock(); defer c.mu.Unlock(); return c.wall }, MonotonicNow: func() time.Duration { c.mu.Lock(); defer c.mu.Unlock(); return c.mono }}
}
func (c *fakeClock) AdvanceMono(d time.Duration) { c.mu.Lock(); defer c.mu.Unlock(); c.mono += d }
func (c *fakeClock) MoveWall(d time.Duration) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.wall = c.wall.Add(d)
}
func validSample() Sample {
	return Sample{Values: map[string]uint64{"memory_used_bytes": 123, "memory_max_bytes": 0, "evicted_keys_total": 7, "aof_enabled": 0}}
}

func TestSnapshotAgeUsesMonotonicClock(t *testing.T) {
	c := newFakeClock()
	s := NewStore(c.Clock())
	s.Record(validSample(), nil, time.Millisecond)
	c.AdvanceMono(45 * time.Second)
	c.MoveWall(-24 * time.Hour)
	if !s.Ready() {
		t.Fatal("45-second boundary is still fresh")
	}
	c.AdvanceMono(time.Nanosecond)
	if s.Ready() {
		t.Fatal("stale snapshot accepted")
	}
	body := string(s.Metrics())
	if !strings.Contains(body, "fasttowin_redis_monitor_up 0\n") {
		t.Fatal(body)
	}
	if strings.Contains(body, "fasttowin_redis_monitor_memory_used_bytes ") {
		t.Fatal("stale domain sample")
	}
}

func TestSnapshotFailureRecoveryAndOwnership(t *testing.T) {
	c := newFakeClock()
	s := NewStore(c.Clock())
	if s.Ready() || !strings.Contains(string(s.Metrics()), "last_success_timestamp_seconds 0\n") {
		t.Fatal("never succeeded")
	}
	sample := validSample()
	sample.Values["secret-marker"] = 19
	s.Record(sample, nil, time.Millisecond)
	sample.Values["memory_used_bytes"] = 999
	body := s.Metrics()
	if !s.Ready() || !strings.Contains(string(body), "memory_used_bytes 123\n") || strings.Contains(string(body), "secret-marker") {
		t.Fatal(string(body))
	}
	body[0] = '!'
	if s.Metrics()[0] != '#' {
		t.Fatal("metrics result shares mutable storage")
	}
	s.Record(Sample{}, errors.New("secret-marker"), 2*time.Millisecond)
	if s.Ready() || strings.Contains(string(s.Metrics()), "fasttowin_redis_monitor_memory_used_bytes ") {
		t.Fatal("failed attempt retained domain sample")
	}
	if strings.Contains(string(s.Metrics()), "secret-marker") || !strings.Contains(string(s.Metrics()), "last_success_timestamp_seconds 1800000000\n") {
		t.Fatal("unsafe failure diagnostics")
	}
	sample = validSample()
	sample.Values["evicted_keys_total"] = 1
	s.Record(sample, nil, 0)
	if !s.Ready() || !strings.Contains(string(s.Metrics()), "evicted_keys_total 1\n") {
		t.Fatal("source counter reset/recovery")
	}
}

func TestMetricsContractAndReasons(t *testing.T) {
	s := NewStore(newFakeClock().Clock())
	for _, reason := range []string{"connect", "timeout", "auth", "protocol", "oversized", "missing", "invalid"} {
		s.Record(Sample{}, Failure{reason}, time.Second)
	}
	s.Record(Sample{}, Failure{"secret-marker"}, -time.Second)
	body := string(s.Metrics())
	for _, reason := range []string{"connect", "timeout", "auth", "protocol", "oversized", "missing", "invalid"} {
		value := "1"
		if reason == "invalid" {
			value = "2"
		}
		if !strings.Contains(body, "collection_errors_total{reason=\""+reason+"\"} "+value+"\n") {
			t.Fatal(body)
		}
	}
	if strings.Contains(body, "secret-marker") || strings.Contains(body, "NaN") || strings.Contains(body, "Inf") || !strings.Contains(body, "collection_duration_seconds 0\n") {
		t.Fatal("unsafe numeric output")
	}
	sample, err := parseInfo([]byte(aofInfo))
	if err != nil {
		t.Fatal(err)
	}
	s.Record(sample, nil, 1500*time.Millisecond)
	body = string(s.Metrics())
	if strings.Count(body, "# HELP ") != 15 || strings.Count(body, "# TYPE ") != 15 {
		t.Fatal("metadata count", body)
	}
	for _, suffix := range []string{"evicted_keys_total", "aof_delayed_fsync_total", "collection_errors_total"} {
		if !strings.Contains(body, "# TYPE fasttowin_redis_monitor_"+suffix+" counter\n") {
			t.Fatal("counter type", suffix)
		}
	}
	if !strings.Contains(body, "aof_last_write_ok 0\n") || !strings.Contains(body, "collection_duration_seconds 1.5\n") {
		t.Fatal("collected fault/duration lost", body)
	}
	if body != string(s.Metrics()) {
		t.Fatal("nondeterministic exposition")
	}
}
