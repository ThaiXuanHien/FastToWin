package monitor

import (
	"errors"
	"sync"
	"time"
)

// Clock keeps wall timestamps separate from elapsed monotonic time.
type Clock struct {
	WallNow      func() time.Time
	MonotonicNow func() time.Duration
}

func realClock() Clock {
	start := time.Now()
	return Clock{WallNow: time.Now, MonotonicNow: func() time.Duration { return time.Since(start) }}
}

// Store owns each validated sample and publishes one synchronized view.
type Store struct {
	mu          sync.Mutex
	clock       Clock
	values      map[string]uint64
	success     bool
	successAt   time.Duration
	lastSuccess int64
	duration    time.Duration
	errors      [7]uint64
}

func NewStore(clock Clock) *Store { return &Store{clock: clock} }

var reasons = [...]string{"connect", "timeout", "auth", "protocol", "oversized", "missing", "invalid"}

func failureReason(err error) string {
	var failure Failure
	if errors.As(err, &failure) {
		for _, reason := range reasons {
			if failure.Reason == reason {
				return reason
			}
		}
	}
	return "invalid"
}

func (s *Store) Record(sample Sample, err error, duration time.Duration) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if duration < 0 {
		duration = 0
	}
	s.duration = duration
	s.success = err == nil
	s.values = nil
	if err != nil {
		reason := failureReason(err)
		for i, allowed := range reasons {
			if allowed == reason && s.errors[i] < ^uint64(0) {
				s.errors[i]++
			}
		}
		return
	}
	s.values = make(map[string]uint64, len(domainMetrics))
	for _, metric := range domainMetrics {
		if value, exists := sample.Values[metric.suffix]; exists {
			s.values[metric.suffix] = value
		}
	}
	s.successAt = s.clock.MonotonicNow()
	s.lastSuccess = s.clock.WallNow().Unix()
	if s.lastSuccess < 0 {
		s.lastSuccess = 0
	}
}

func (s *Store) fresh() bool {
	return s.success && s.clock.MonotonicNow()-s.successAt <= 45*time.Second
}

func (s *Store) Ready() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.fresh()
}
