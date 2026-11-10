package supervisor

import (
	"context"
	"errors"
	"os"
	"os/exec"
	"syscall"
	"time"
)

var errTree = errors.New("unverified process tree")

// ProcessDriver owns one private subreaper and workload generation. Its methods
// are serialized by Service. The caller must dispatch --reap to RunReaper and
// run as PID 1 in a private namespace for abrupt-parent-death containment.
type ProcessDriver struct {
	role            string
	argv            []string
	gen             uint64
	state           string
	guard, root     *identity
	child           *exec.Cmd
	owned           map[int]*identity
	closing, closed bool
	brokerLost      bool
}

// NewProcessDriver starts an immutable workload command. Only trusted startup
// configuration supplies argv; control requests can never select a command.
func NewProcessDriver(ctx context.Context, role string, argv []string) (*ProcessDriver, error) {
	if !allowed(role, "pause") || len(argv) == 0 {
		return nil, errTree
	}
	return newOwnedProcess(ctx, role, argv)
}

// Private fixed-workload entrypoint also serves the proxy lifecycle. Public
// NewProcessDriver retains its process-role allowlist.
func newOwnedProcess(ctx context.Context, role string, argv []string) (*ProcessDriver, error) {
	self, err := os.FindProcess(os.Getpid())
	if err != nil {
		return nil, err
	}
	err = checkedHandle(self)
	self.Release()
	if err != nil {
		return nil, err
	}
	d := &ProcessDriver{role: role, argv: append([]string(nil), argv...)}
	if err = d.launch(ctx); err != nil {
		clean, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
		defer cancel()
		return nil, errors.Join(err, d.Close(clean))
	}
	return d, nil
}
func waitPoll(ctx context.Context) error {
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-time.After(time.Millisecond):
		return nil
	}
}
func (d *ProcessDriver) freeze(ctx context.Context, requireRoot bool) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if requireRoot {
		if err := d.liveRoot(); err != nil {
			return err
		}
	}
	// Stop the postmaster/JVM first, then freeze the subreaper so adopted
	// orphans and zombies cannot disappear through reaping during verification.
	if err := d.signal(d.root, syscall.SIGSTOP); err != nil {
		return err
	}
	if err := d.signal(d.guard, syscall.SIGSTOP); err != nil {
		return err
	}
	stable := 0
	for {
		o, err := d.inspect(ctx)
		if err != nil {
			return err
		}
		for _, id := range d.owned {
			if err := ctx.Err(); err != nil {
				return err
			}
			if err = d.signal(id, syscall.SIGSTOP); err != nil {
				return err
			}
		}
		if o.allStopped && !o.changed {
			stable++
		} else {
			stable = 0
		}
		if stable >= 2 {
			if requireRoot {
				return d.liveRoot()
			}
			return nil
		}
		if err = waitPoll(ctx); err != nil {
			return err
		}
	}
}
func (d *ProcessDriver) liveRoot() error {
	if d.brokerLost || d.root == nil || d.guard == nil {
		return errTree
	}
	anchor, err := d.guard.stat()
	if err != nil || dead(anchor.state) {
		return errTree
	}
	s, err := d.root.stat()
	if err != nil {
		return err
	}
	if dead(s.state) {
		return errTree
	}
	return nil
}
func (d *ProcessDriver) resume(ctx context.Context) error {
	for _, id := range d.owned {
		if err := ctx.Err(); err != nil {
			return err
		}
		if id != d.root {
			if err := d.signal(id, syscall.SIGCONT); err != nil {
				return err
			}
		}
	}
	if err := d.signal(d.guard, syscall.SIGCONT); err != nil {
		return err
	}
	if err := d.signal(d.root, syscall.SIGCONT); err != nil {
		return err
	}
	for {
		if err := d.liveRoot(); err != nil {
			return err
		}
		o, err := d.inspect(ctx)
		if err != nil {
			return err
		}
		if !o.anyStopped {
			d.state = "running"
			return nil
		}
		if err = waitPoll(ctx); err != nil {
			return err
		}
	}
}
func (d *ProcessDriver) Status(ctx context.Context) (Status, error) {
	if err := ctx.Err(); err != nil {
		return Status{}, err
	}
	if d.closed {
		return Status{}, errTree
	}
	if d.state == "stopped" && d.child == nil {
		return Status{d.gen, "stopped", true}, nil
	}
	if err := d.liveRoot(); err != nil {
		return Status{}, err
	}
	o, err := d.inspect(ctx)
	if err != nil {
		return Status{}, err
	}
	if d.state == "paused" && (!o.allStopped || o.changed) || d.state == "running" && o.anyStopped {
		return Status{}, errTree
	}
	if d.state != "running" && d.state != "paused" {
		return Status{}, errTree
	}
	if err = d.liveRoot(); err != nil {
		return Status{}, err
	}
	return Status{d.gen, d.state, true}, nil
}
func (d *ProcessDriver) Apply(ctx context.Context, op string, gen uint64) (Status, error) {
	if d.closing || gen != d.gen || !allowed(d.role, op) {
		return Status{}, errTree
	}
	var err error
	switch {
	case op == "pause" && d.state == "running":
		err = d.freeze(ctx, true)
		if err == nil {
			d.state = "paused"
		} else {
			clean, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
			defer cancel()
			err = errors.Join(err, d.resume(clean))
		}
	case op == "resume" && d.state == "paused":
		err = d.resume(ctx)
	case op == "kill" && d.state == "running":
		d.state = "killing"
		err = d.terminate(ctx, false)
	case op == "start" && d.state == "stopped":
		err = d.launch(ctx)
	default:
		return Status{}, errTree
	}
	if err != nil {
		return Status{}, err
	}
	return d.Status(ctx)
}
func (d *ProcessDriver) Restore(ctx context.Context) error {
	if d.closing {
		return errTree
	}
	if d.state == "killing" || d.state == "starting" {
		if err := d.terminate(ctx, false); err != nil {
			return err
		}
	}
	if d.state == "stopped" && (d.role == "a" || d.role == "b") {
		return d.launch(ctx)
	}
	return d.resume(ctx)
}
func (d *ProcessDriver) reaped() (bool, error) {
	if d.brokerLost {
		return false, errTree
	}
	if d.child == nil {
		return true, nil
	}
	s, err := statPID(d.child.Process.Pid)
	if err != nil {
		return false, err
	}
	if !dead(s.state) {
		return false, nil
	}
	// Only this owner waits for the guard; it cannot be PID-recycled before Wait.
	exit, err := d.child.Process.Wait()
	if err != nil {
		return false, err
	}
	if !exit.Success() {
		// Only a normal broker exit proves wait4(ECHILD). After an unexpected
		// loss, retain every pidfd and fail closed until PID 1 exits.
		d.brokerLost = true
		return false, errTree
	}
	for _, id := range d.owned {
		if id != d.root {
			id.process.Release()
		}
	}
	if d.root != nil {
		d.root.process.Release()
	}
	d.child = nil
	d.guard = nil
	d.root = nil
	d.owned = nil
	d.state = "stopped"
	return true, nil
}
func (d *ProcessDriver) terminate(ctx context.Context, graceful bool) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	done, err := d.reaped()
	if d.brokerLost {
		for _, id := range d.owned {
			if err := d.signal(id, syscall.SIGKILL); err != nil {
				return err
			}
		}
		for _, id := range d.owned {
			for {
				s, err := id.stat()
				if gone(err) || err == nil && dead(s.state) {
					break
				}
				if err != nil {
					return err
				}
				if err = waitPoll(ctx); err != nil {
					return err
				}
			}
		}
		return errTree
	}
	if done || err != nil {
		return err
	}
	if d.guard == nil {
		return errTree
	}
	if graceful {
		if err = d.signal(d.guard, syscall.SIGCONT); err != nil {
			return err
		}
		for _, id := range d.owned {
			if err = d.signal(id, syscall.SIGCONT); err != nil {
				return err
			}
		}
		if err = d.signal(d.root, syscall.SIGTERM); err != nil {
			return err
		}
		grace := time.Now().Add(100 * time.Millisecond)
		for time.Now().Before(grace) {
			if done, err = d.reaped(); done || err != nil {
				return err
			}
			if err = waitPoll(ctx); err != nil {
				return err
			}
		}
	}
	if err = d.freeze(ctx, false); err != nil {
		return err
	}
	for _, id := range d.owned {
		if err = d.signal(id, syscall.SIGKILL); err != nil {
			return err
		}
	}
	if err = d.signal(d.guard, syscall.SIGCONT); err != nil {
		return err
	}
	for {
		if done, err = d.reaped(); done || err != nil {
			return err
		}
		if err = waitPoll(ctx); err != nil {
			return err
		}
	}
}
func (d *ProcessDriver) Close(ctx context.Context) error {
	if d.closed {
		return nil
	}
	d.closing = true
	if err := d.terminate(ctx, true); err != nil {
		return err
	}
	d.closed = true
	return nil
}
