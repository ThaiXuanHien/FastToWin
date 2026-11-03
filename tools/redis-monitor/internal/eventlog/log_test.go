package eventlog

import (
	"bytes"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type stalledWriter struct {
	entered             chan struct{}
	release             chan struct{}
	once                sync.Once
	active, peak, calls atomic.Int32
}

func (w *stalledWriter) Write(p []byte) (int, error) {
	w.calls.Add(1)
	active := w.active.Add(1)
	defer w.active.Add(-1)
	for peak := w.peak.Load(); active > peak; peak = w.peak.Load() {
		if w.peak.CompareAndSwap(peak, active) {
			break
		}
	}
	w.once.Do(func() { close(w.entered) })
	<-w.release
	return len(p), nil
}

func TestConcurrentSubmissionAndCloseDoNotWaitForWriter(t *testing.T) {
	out := &stalledWriter{entered: make(chan struct{}), release: make(chan struct{})}
	log := New(out)
	defer func() {
		close(out.release)
		select {
		case <-log.done:
			if out.calls.Load() != 1 {
				t.Error("abandoned queue was drained after stop", out.calls.Load())
			}
		case <-time.After(time.Second):
			t.Error("worker did not exit after writer release")
		}
	}()
	_, _ = log.Write([]byte("started\n"))
	<-out.entered
	for i := 0; i < 1000; i++ {
		_, _ = log.Write([]byte("collection_succeeded\n"))
	}
	finished := make(chan struct{})
	go func() {
		var wg sync.WaitGroup
		for i := 0; i < 32; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				for j := 0; j < 1000; j++ {
					_, _ = log.Write([]byte("collection_failed timeout\n"))
				}
			}()
		}
		log.Close()
		wg.Wait()
		_, _ = log.Write([]byte("stopped\n"))
		close(finished)
	}()
	select {
	case <-finished:
	case <-time.After(time.Second):
		t.Fatal("submission/close blocked on writer")
	}
	if out.peak.Load() != 1 {
		t.Fatal("more than one output worker", out.peak.Load())
	}
}

func TestAcquireDoesNotCloseBorrowedLogger(t *testing.T) {
	var out bytes.Buffer
	owner := New(&out)
	borrowed, closeBorrowed := Acquire(owner)
	closeBorrowed()
	_, _ = borrowed.Write([]byte("stopped\n"))
	owner.Close()
	if out.String() != "stopped\n" {
		t.Fatal("borrower closed executable logger", out.String())
	}
}

func TestSafeEventsFlushAndUnknownDataIsDropped(t *testing.T) {
	var out bytes.Buffer
	log := New(&out)
	for _, event := range []string{"started\n", "collection_failed auth\n", "stopped\n", "secret-marker\n", strings.Repeat("s", 100000)} {
		_, _ = log.Write([]byte(event))
	}
	log.Close()
	if out.String() != "started\ncollection_failed auth\nstopped\n" {
		t.Fatal("unsafe or missing events", out.String())
	}
}
