// Package eventlog isolates collector lifecycle work from log backpressure.
package eventlog

import (
	"io"
	"sync"
	"time"
)

// Writer has one output worker and at most 16 queued fixed-size events.
// Writes drop unknown events, events submitted after Close, and overflow.
// Close allows 50 ms to flush, then abandons queued events. An arbitrary
// io.Writer cannot be interrupted: its one worker can remain in Write until
// the caller's writer returns or the executable exits. It never owns/closes out.
type Writer struct {
	mu        sync.Mutex
	closed    bool
	queue     chan string
	abort     chan struct{}
	done      chan struct{}
	closeOnce sync.Once
}

func New(out io.Writer) *Writer {
	if out == nil {
		out = io.Discard
	}
	w := &Writer{queue: make(chan string, 16), abort: make(chan struct{}), done: make(chan struct{})}
	go func() {
		defer close(w.done)
		for {
			select {
			case <-w.abort:
				return
			default:
			}
			select {
			case <-w.abort:
				return
			case event, ok := <-w.queue:
				if !ok {
					return
				}
				_, _ = io.WriteString(out, event)
			}
		}
	}()
	return w
}

// Acquire shares an executable's logger; otherwise the service owns one worker.
func Acquire(out io.Writer) (*Writer, func()) {
	if w, ok := out.(*Writer); ok {
		return w, func() {}
	}
	w := New(out)
	return w, w.Close
}

func safe(event string) bool {
	switch event {
	case "started\n", "stopped\n", "configuration_failed\n", "service_failed\n", "http_failed connect\n", "collection_succeeded\n",
		"collection_failed connect\n", "collection_failed timeout\n", "collection_failed auth\n", "collection_failed protocol\n", "collection_failed oversized\n", "collection_failed missing\n", "collection_failed invalid\n":
		return true
	}
	return false
}

func (w *Writer) Write(p []byte) (int, error) {
	if len(p) > 64 {
		return len(p), nil
	}
	event := string(p)
	if !safe(event) {
		return len(p), nil
	}
	w.mu.Lock()
	defer w.mu.Unlock()
	if !w.closed {
		select {
		case w.queue <- event:
		default:
		}
	}
	return len(p), nil
}

func (w *Writer) Close() {
	w.closeOnce.Do(func() {
		w.mu.Lock()
		w.closed = true
		close(w.queue)
		w.mu.Unlock()
		timer := time.NewTimer(50 * time.Millisecond)
		defer timer.Stop()
		select {
		case <-w.done:
		case <-timer.C:
			close(w.abort)
		}
	})
}
