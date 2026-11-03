package monitor

import (
	"bytes"
	"context"
	"errors"
	"io"
	"strings"
	"testing"
	"time"
)

type readerFunc func(context.Context) (Sample, error)

func (f readerFunc) Read(ctx context.Context) (Sample, error) { return f(ctx) }

func TestPollImmediateSkipsElapsedTicksAndCancels(t *testing.T) {
	c := newFakeClock()
	s := NewStore(c.Clock())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	calls := 0
	waits := 0
	reader := readerFunc(func(ctx context.Context) (Sample, error) {
		calls++
		if _, ok := ctx.Deadline(); !ok {
			t.Error("attempt has no absolute deadline")
		}
		if calls == 1 {
			c.AdvanceMono(35 * time.Second)
		} else {
			cancel()
		}
		return validSample(), nil
	})
	wait := func(ctx context.Context, d time.Duration) bool {
		waits++
		if calls != 1 || d != 10*time.Second {
			t.Fatalf("catch-up or wrong schedule: %d %v", calls, d)
		}
		c.AdvanceMono(d)
		return true
	}
	poll(ctx, reader, s, c.Clock(), wait, &eventLog{out: io.Discard})
	if calls != 2 || waits != 1 {
		t.Fatal(calls, waits)
	}
	if !strings.Contains(string(s.Metrics()), "collection_duration_seconds 35\n") {
		t.Fatal("duration not monotonic")
	}
}

func TestPollFailureLogsOnlyFixedReasonAndDoesNotOverlap(t *testing.T) {
	c := newFakeClock()
	s := NewStore(c.Clock())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	var logs bytes.Buffer
	active := false
	calls := 0
	reader := readerFunc(func(ctx context.Context) (Sample, error) {
		if active {
			t.Fatal("overlapping read")
		}
		active = true
		defer func() { active = false }()
		calls++
		return Sample{}, errors.New("secret-marker-target-request")
	})
	wait := func(ctx context.Context, d time.Duration) bool { cancel(); return false }
	poll(ctx, reader, s, c.Clock(), wait, &eventLog{out: &logs})
	if calls != 1 || logs.String() != "collection_failed invalid\n" {
		t.Fatal(calls, logs.String())
	}
	if s.Ready() {
		t.Fatal("failed collection ready")
	}
}

func TestPollCancellationReachesActiveReader(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	entered := make(chan struct{})
	done := make(chan struct{})
	c := realClock()
	go func() {
		defer close(done)
		poll(ctx, readerFunc(func(ctx context.Context) (Sample, error) { close(entered); <-ctx.Done(); return Sample{}, ctx.Err() }), NewStore(c), c, waitPoll, &eventLog{out: io.Discard})
	}()
	<-entered
	cancel()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("active reader not canceled")
	}
}

func TestPollExpiredAttemptCannotPublishSuccess(t *testing.T) {
	c := realClock()
	s := NewStore(c)
	var logs bytes.Buffer
	poll(context.Background(), readerFunc(func(ctx context.Context) (Sample, error) { <-ctx.Done(); return validSample(), nil }), s, c,
		func(context.Context, time.Duration) bool { return false }, &eventLog{out: &logs})
	if s.Ready() || logs.String() != "collection_failed timeout\n" {
		t.Fatal("expired attempt published success", logs.String())
	}
}

func TestPollAllFailureLogsAreBounded(t *testing.T) {
	for _, reason := range []string{"connect", "timeout", "auth", "protocol", "oversized", "missing", "invalid"} {
		c := newFakeClock()
		var out bytes.Buffer
		poll(context.Background(), readerFunc(func(context.Context) (Sample, error) {
			return Sample{}, errors.Join(Failure{reason}, errors.New("secret-marker-target-request"))
		}), NewStore(c.Clock()), c.Clock(),
			func(context.Context, time.Duration) bool { return false }, &eventLog{out: &out})
		if out.String() != "collection_failed "+reason+"\n" {
			t.Fatal("unsafe log", out.String())
		}
	}
}
