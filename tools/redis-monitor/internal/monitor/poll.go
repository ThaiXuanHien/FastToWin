package monitor

import (
	"context"
	"io"
	"time"
)

const pollInterval = 15 * time.Second

type eventLog struct {
	// runService supplies the bounded eventlog.Writer; tests may capture inline.
	out io.Writer
}

func (l *eventLog) write(event string) {
	_, _ = io.WriteString(l.out, event+"\n")
}

func waitPoll(ctx context.Context, duration time.Duration) bool {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return ctx.Err() == nil
	}
}

// poll is deliberately synchronous: neither ticks nor HTTP can create workers.
func poll(ctx context.Context, reader Reader, store *Store, clock Clock, wait func(context.Context, time.Duration) bool, log *eventLog) {
	next := clock.MonotonicNow() + pollInterval
	for ctx.Err() == nil {
		start := clock.MonotonicNow()
		attempt, cancel := context.WithTimeout(ctx, 3*time.Second)
		sample, err := reader.Read(attempt)
		if attempt.Err() != nil {
			err = Failure{"timeout"}
		}
		cancel()
		if ctx.Err() != nil {
			return
		}
		store.Record(sample, err, clock.MonotonicNow()-start)
		if err != nil {
			log.write("collection_failed " + failureReason(err))
		} else {
			log.write("collection_succeeded")
		}
		now := clock.MonotonicNow()
		if now >= next {
			next += ((now-next)/pollInterval + 1) * pollInterval
		}
		if !wait(ctx, next-now) {
			return
		}
		next += pollInterval
	}
}
