package supervisor

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"testing"
	"time"
)

func TestMain(m *testing.M) {
	if len(os.Args) > 1 && os.Args[1] == "--reap" {
		os.Exit(RunReaper(os.Args[2:]))
	}
	os.Exit(m.Run())
}

func eventually(t *testing.T, cond func() bool) {
	t.Helper()
	until := time.Now().Add(3 * time.Second)
	for !cond() {
		if time.Now().After(until) {
			t.Fatal("timed out waiting for process observation")
		}
		time.Sleep(2 * time.Millisecond)
	}
}

func fileSize(path string) int64 {
	st, err := os.Stat(path)
	if err != nil {
		return 0
	}
	return st.Size()
}

func processFixture(t *testing.T, role string) (*ProcessDriver, string) {
	t.Helper()
	dir := t.TempDir()
	// The intermediate parent exits before discovery. The grandchild enters a new
	// session, writes continuously and repeatedly forks short-lived workers.
	shell := `(setsid sh -c 'echo $$ > "$1/pid"; while :; do echo x >> "$1/stream"; sleep .01; done' worker "$1" </dev/null &) ; exec sleep 120`
	d, err := NewProcessDriver(t.Context(), role, []string{"/bin/sh", "-c", shell, "root", dir})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
		defer cancel()
		if err := d.Close(ctx); err != nil {
			t.Error(err)
		}
	})
	eventually(t, func() bool { return fileSize(filepath.Join(dir, "stream")) > 0 })
	return d, dir
}

func TestProcessPauseEscapedOrphanAndResumeSameStream(t *testing.T) {
	d, dir := processFixture(t, "pg")
	unrelated := exec.Command("sleep", "120")
	if err := unrelated.Start(); err != nil {
		t.Fatal(err)
	}
	defer func() { unrelated.Process.Kill(); unrelated.Wait() }()
	pidBytes, _ := os.ReadFile(filepath.Join(dir, "pid"))
	pid, _ := strconv.Atoi(strings.TrimSpace(string(pidBytes)))
	if pid <= 0 {
		t.Fatal("missing escaped worker")
	}
	p, err := d.Apply(t.Context(), "pause", 1)
	if err != nil || p != (Status{1, "paused", true}) {
		t.Fatalf("pause=%v err=%v", p, err)
	}
	status, err := os.ReadFile(fmt.Sprintf("/proc/%d/status", pid))
	if err != nil || !strings.Contains(string(status), "State:\tT") {
		t.Fatalf("escaped worker still running: %v", err)
	}
	status, err = os.ReadFile(fmt.Sprintf("/proc/%d/status", unrelated.Process.Pid))
	if err != nil || strings.Contains(string(status), "State:\tT") {
		t.Fatal("unrelated process was stopped")
	}
	stream := filepath.Join(dir, "stream")
	frozen := fileSize(stream)
	time.Sleep(70 * time.Millisecond)
	if fileSize(stream) != frozen {
		t.Fatal("output continued during verified pause")
	}
	if _, err = d.Apply(t.Context(), "kill", 1); err == nil {
		t.Fatal("store accepted kill")
	}
	p, err = d.Apply(t.Context(), "resume", 1)
	if err != nil || p != (Status{1, "running", true}) {
		t.Fatalf("resume=%v err=%v", p, err)
	}
	eventually(t, func() bool { return fileSize(stream) > frozen })
	if err = d.Close(t.Context()); err != nil {
		t.Fatal(err)
	}
	if _, err = os.Stat(fmt.Sprintf("/proc/%d", pid)); !os.IsNotExist(err) {
		t.Fatal("escaped worker survived close")
	}
	if err = unrelated.Process.Signal(syscall.Signal(0)); err != nil {
		t.Fatal("unrelated process lost")
	}
}

func TestProcessBackendKillGenerationAndRetryClose(t *testing.T) {
	d, _ := processFixture(t, "a")
	for i, op := range []string{"kill", "start"} {
		s, err := d.Apply(t.Context(), op, 1)
		if err != nil || !s.Verified || s.Generation != uint64(i+1) {
			t.Fatalf("%s=%v err=%v", op, s, err)
		}
	}
	if _, err := d.Apply(t.Context(), "kill", 1); err == nil {
		t.Fatal("stale kill accepted")
	}
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	if err := d.Close(ctx); err == nil {
		t.Fatal("canceled cleanup reported success")
	}
	s, err := d.Status(t.Context())
	if err != nil || s != (Status{2, "running", true}) {
		t.Fatalf("new generation lost: %v %v", s, err)
	}
	if err := d.Close(t.Context()); err != nil {
		t.Fatal(err)
	}
}

// Perform a test action only after the kernel confirms that the service root
// has stopped. This yields a deterministic boundary inside real tree discovery.
type whenRootStopped struct {
	context.Context
	pid  int
	once func()
}

func (c *whenRootStopped) Err() error {
	if c.once != nil {
		s, err := statPID(c.pid)
		if err == nil && s.state == 'T' {
			act := c.once
			c.once = nil
			act()
		}
	}
	return c.Context.Err()
}
func TestProcessCancelPartialPauseRestoresAllTargets(t *testing.T) {
	d, dir := processFixture(t, "redis")
	canceled, cancel := context.WithCancel(t.Context())
	defer cancel()
	ctx := &whenRootStopped{canceled, d.root.process.Pid, cancel}
	if _, err := d.Apply(ctx, "pause", 1); err == nil || canceled.Err() == nil {
		t.Fatal("pause ignored mid-discovery cancellation")
	}
	s, err := d.Status(t.Context())
	if err != nil || s != (Status{1, "running", true}) {
		t.Fatalf("partial pause survived cancellation: %v %v", s, err)
	}
	stream := filepath.Join(dir, "stream")
	before := fileSize(stream)
	eventually(t, func() bool { return fileSize(stream) > before })
}
func TestProcessNewOrphanForksDuringDiscovery(t *testing.T) {
	dir := t.TempDir()
	script := `(echo ready > "$1/ready"; while [ ! -f "$1/go" ];do sleep .001;done; setsid sh -c 'echo $$ > "$1/pid"; exec sleep 60' fork "$1" &) & exec sleep 120`
	d, err := NewProcessDriver(t.Context(), "pg", []string{"sh", "-c", script, "root", dir})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if err := d.Close(context.Background()); err != nil {
			t.Error(err)
		}
	})
	eventually(t, func() bool { return fileSize(filepath.Join(dir, "ready")) > 0 })
	ctx := &whenRootStopped{t.Context(), d.root.process.Pid, func() {
		if err := os.WriteFile(filepath.Join(dir, "go"), nil, 0600); err != nil {
			t.Fatal(err)
		}
		eventually(t, func() bool { return fileSize(filepath.Join(dir, "pid")) > 0 })
	}}
	if _, err = d.Apply(ctx, "pause", 1); err != nil {
		t.Fatal(err)
	}
	b, err := os.ReadFile(filepath.Join(dir, "pid"))
	if err != nil {
		t.Fatal(err)
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(b)))
	if err != nil {
		t.Fatal(err)
	}
	s, err := statPID(pid)
	if err != nil || s.state != 'T' {
		t.Fatalf("concurrent fork escaped: %v %v", s, err)
	}
}
func TestProcessRejectsMismatchedProcStartTime(t *testing.T) {
	d, _ := processFixture(t, "a")
	stale := *d.root
	stale.start++
	if err := d.signal(&stale, syscall.SIGSTOP); err != nil {
		t.Fatal(err)
	}
	if s, err := d.Status(t.Context()); err != nil || s.State != "running" {
		t.Fatalf("stale identity signalled: %v %v", s, err)
	}
}

func TestProcessCloseRetryNeverResurrectsKilledBackend(t *testing.T) {
	d, _ := processFixture(t, "b")
	if _, err := d.Apply(t.Context(), "kill", 1); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	if err := d.Close(ctx); err == nil {
		t.Fatal("expected interrupted Close")
	}
	if err := d.Restore(t.Context()); err == nil || d.gen != 1 {
		t.Fatal("Restore restarted backend during teardown")
	}
	if _, err := d.Apply(t.Context(), "start", 1); err == nil {
		t.Fatal("Apply restarted backend during teardown")
	}
	if err := d.Close(t.Context()); err != nil {
		t.Fatal(err)
	}
}

func TestProcessAbruptBrokerLossCannotVerifyOwnedTree(t *testing.T) {
	d, err := NewProcessDriver(t.Context(), "pg", []string{"sleep", "60"})
	if err != nil {
		t.Fatal(err)
	}
	root := d.root.process
	defer root.Kill() // separate saved pidfd if a broken Close drops its registry
	if err = d.guard.process.Kill(); err != nil {
		t.Fatal(err)
	}
	eventually(t, func() bool { s, e := d.guard.stat(); return e == nil && dead(s.state) })
	if _, err = d.Status(t.Context()); err == nil {
		t.Error("running status trusted a dead ancestry anchor")
	}
	if err = d.Close(t.Context()); err == nil {
		t.Error("abrupt broker loss was reported as verified cleanup")
	}
	s, err := statPID(root.Pid)
	if err == nil && !dead(s.state) {
		t.Fatal("known workload left running after broker loss")
	}
}

func TestProcessCloseRetriesAfterStoppingPartOfTree(t *testing.T) {
	dir := t.TempDir()
	script := `trap '' TERM; setsid sh -c 'echo $$ > "$1/pid"; exec sleep 60' child "$1" & while :;do sleep .02;done`
	d, err := NewProcessDriver(t.Context(), "pg", []string{"sh", "-c", script, "root", dir})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
		defer cancel()
		if err := d.Close(ctx); err != nil {
			t.Error(err)
		}
	})
	eventually(t, func() bool { return fileSize(filepath.Join(dir, "pid")) > 0 })
	canceled, cancel := context.WithCancel(t.Context())
	defer cancel()
	ctx := &whenRootStopped{canceled, d.root.process.Pid, cancel}
	if err = d.Close(ctx); err == nil || canceled.Err() == nil {
		t.Fatal("cleanup did not stop at partial-tree cancellation")
	}
	if d.root == nil || d.guard == nil || len(d.owned) == 0 {
		t.Fatal("canceled cleanup discarded identities")
	}
	if err = d.Restore(t.Context()); err == nil {
		t.Fatal("teardown allowed restoration")
	}
	if err = d.Close(t.Context()); err != nil {
		t.Fatal(err)
	}
	b, _ := os.ReadFile(filepath.Join(dir, "pid"))
	pid, _ := strconv.Atoi(strings.TrimSpace(string(b)))
	if _, err = os.Stat(fmt.Sprintf("/proc/%d", pid)); !os.IsNotExist(err) {
		t.Fatal("escaped worker survived retried Close")
	}
}

func TestProcessInterruptedKillRestoresNewBackendGeneration(t *testing.T) {
	d, _ := processFixture(t, "a")
	canceled, cancel := context.WithCancel(t.Context())
	defer cancel()
	ctx := &whenRootStopped{canceled, d.root.process.Pid, cancel}
	if _, err := d.Apply(ctx, "kill", 1); err == nil || canceled.Err() == nil {
		t.Fatal("kill did not honor cancellation")
	}
	if err := d.Restore(t.Context()); err != nil {
		t.Fatal(err)
	}
	if s, err := d.Status(t.Context()); err != nil || s != (Status{2, "running", true}) {
		t.Fatalf("partial kill lost restoration: %v %v", s, err)
	}
}

func TestProcessWorkerExitDuringPauseDiscovery(t *testing.T) {
	d, dir := processFixture(t, "pg")
	b, _ := os.ReadFile(filepath.Join(dir, "pid"))
	pid, _ := strconv.Atoi(strings.TrimSpace(string(b)))
	worker, err := os.FindProcess(pid)
	if err != nil {
		t.Fatal(err)
	}
	defer worker.Release()
	if err = checkedHandle(worker); err != nil {
		t.Fatal(err)
	}
	ctx := &whenRootStopped{t.Context(), d.root.process.Pid, func() {
		if err := worker.Kill(); err != nil {
			t.Fatal(err)
		}
	}}
	if _, err = d.Apply(ctx, "pause", 1); err != nil {
		t.Fatal(err)
	}
	if _, err = d.Apply(t.Context(), "resume", 1); err != nil {
		t.Fatal(err)
	}
}
