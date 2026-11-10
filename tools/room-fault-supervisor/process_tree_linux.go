package supervisor

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"syscall"
)

type procStat struct {
	parent int
	start  uint64
	state  byte
}

func statAt(path string) (procStat, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return procStat{}, err
	}
	// comm is parenthesized and can contain spaces, newlines, or ')'.
	i := strings.LastIndexByte(string(b), ')')
	if i < 0 {
		return procStat{}, errTree
	}
	f := strings.Fields(string(b[i+1:]))
	if len(f) < 20 || len(f[0]) != 1 {
		return procStat{}, errTree
	}
	pp, err := strconv.Atoi(f[1])
	if err != nil {
		return procStat{}, err
	}
	start, err := strconv.ParseUint(f[19], 10, 64)
	return procStat{pp, start, f[0][0]}, err
}
func statPID(pid int) (procStat, error) { return statAt(fmt.Sprintf("/proc/%d/stat", pid)) }
func gone(err error) bool {
	return os.IsNotExist(err) || errors.Is(err, os.ErrProcessDone) || errors.Is(err, syscall.ESRCH)
}
func dead(s byte) bool { return s == 'Z' || s == 'X' }

type identity struct {
	process *os.Process
	start   uint64
}

func checkedHandle(p *os.Process) error {
	// In this pinned Go runtime WithHandle succeeds only for a pidfd-backed
	// Process. Signal on that same object always calls pidfd_send_signal.
	return p.WithHandle(func(uintptr) {})
}
func bind(pid int, parent *identity) (*identity, error) {
	p, err := os.FindProcess(pid)
	if err != nil {
		return nil, err
	}
	ok := false
	defer func() {
		if !ok {
			p.Release()
		}
	}()
	if err = checkedHandle(p); err != nil {
		if _, statErr := statPID(pid); gone(statErr) {
			return nil, os.ErrProcessDone
		}
		return nil, err
	}
	s, err := statPID(pid)
	if err != nil {
		return nil, err
	}
	if dead(s.state) {
		return nil, os.ErrProcessDone
	}
	if s.parent != parent.process.Pid {
		return nil, errTree
	}
	if err = p.Signal(syscall.Signal(0)); err != nil {
		return nil, err
	}
	if err = parent.process.Signal(syscall.Signal(0)); err != nil {
		return nil, err
	}
	ok = true
	return &identity{p, s.start}, nil
}
func (id *identity) stat() (procStat, error) {
	s, err := statPID(id.process.Pid)
	if err != nil {
		return procStat{}, err
	}
	if s.start != id.start {
		return procStat{}, os.ErrProcessDone
	}
	if err = id.process.Signal(syscall.Signal(0)); err != nil {
		return procStat{}, err
	}
	return s, nil
}

// inspect walks the children of every thread, since Linux records a child under
// the particular thread that forked it. The private subreaper is the only
// ancestry anchor. Revalidation on both pidfds brackets every new binding.
type observation struct{ allStopped, anyStopped, changed bool }

func (d *ProcessDriver) inspect(ctx context.Context) (observation, error) {
	o := observation{allStopped: true}
	todo := []*identity{d.guard}
	for pid, id := range d.owned {
		s, err := id.stat()
		if gone(err) || err == nil && dead(s.state) {
			delete(d.owned, pid)
			if id != d.root {
				id.process.Release()
			}
			o.changed = true
			continue
		}
		if err != nil {
			return o, err
		}
		todo = append(todo, id)
	}
	seen := map[*identity]bool{}
	for len(todo) > 0 {
		if err := ctx.Err(); err != nil {
			return o, err
		}
		id := todo[0]
		todo = todo[1:]
		if seen[id] {
			continue
		}
		seen[id] = true
		base := fmt.Sprintf("/proc/%d/task", id.process.Pid)
		threads, err := os.ReadDir(base)
		if gone(err) {
			o.changed = true
			continue
		}
		if err != nil {
			return o, err
		}
		for _, thread := range threads {
			if err := ctx.Err(); err != nil {
				return o, err
			}
			path := base + "/" + thread.Name()
			s, err := statAt(path + "/stat")
			if gone(err) {
				o.changed = true
				continue
			}
			if err != nil {
				return o, err
			}
			if !dead(s.state) {
				o.allStopped = o.allStopped && s.state == 'T'
				o.anyStopped = o.anyStopped || s.state == 'T' || s.state == 't'
			}
			b, err := os.ReadFile(path + "/children")
			if gone(err) {
				o.changed = true
				continue
			}
			if err != nil {
				return o, err
			}
			for _, n := range strings.Fields(string(b)) {
				pid, err := strconv.Atoi(n)
				if err != nil || pid <= 0 {
					return o, errTree
				}
				if d.owned[pid] != nil {
					continue
				}
				c, err := bind(pid, id)
				if gone(err) {
					continue
				}
				if errors.Is(err, errTree) {
					o.changed = true
					continue
				} // worker exited/reparented during discovery
				if err != nil {
					return o, err
				}
				d.owned[pid] = c
				todo = append(todo, c)
				o.changed = true
			}
		}
		// Numeric /proc reads can race exit/reuse. Never accept that snapshot.
		if _, err = id.stat(); gone(err) {
			o.changed = true
		} else if err != nil {
			return o, err
		}
	}
	return o, nil
}
func (d *ProcessDriver) signal(id *identity, sig syscall.Signal) error {
	if id == nil {
		return nil
	}
	_, err := id.stat()
	if err == nil {
		err = id.process.Signal(sig)
	}
	if gone(err) {
		return nil
	}
	return err
}
