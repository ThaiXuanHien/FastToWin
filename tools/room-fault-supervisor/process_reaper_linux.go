package supervisor

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"strings"
	"syscall"
	"time"
)

// RunReaper is the private --reap entrypoint. Exactly one workload is ever
// launched by this process. Linux reparents its daemonized grandchildren here
// (including setsid workers); unrelated namespace children go to PID 1 instead.
func RunReaper(argv []string) int {
	// Exit zero is the cleanup acknowledgement: no workload was launched, or
	// the last owned descendant was reaped. Startup itself requires the pipe ACK.
	if len(argv) == 0 {
		return 0
	}
	_, _, errno := syscall.Syscall6(syscall.SYS_PRCTL, 36, 1, 0, 0, 0, 0) // PR_SET_CHILD_SUBREAPER
	if errno != 0 {
		return 0
	}
	ready := os.NewFile(3, "ready")
	ack := os.NewFile(4, "ack")
	syscall.CloseOnExec(3)
	syscall.CloseOnExec(4)
	path, err := exec.LookPath(argv[0])
	if err != nil {
		return 0
	}
	cmd := exec.Command(path, argv[1:]...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Env = []string{}
	for _, v := range os.Environ() {
		if !strings.HasPrefix(v, "ROOM_FAULT_") {
			cmd.Env = append(cmd.Env, v)
		}
	}
	if err = cmd.Start(); err != nil {
		return 0
	}
	// Keep the direct child's PID reserved (including if already a zombie)
	// until the controller has opened and checked its own pidfd.
	fmt.Fprintln(ready, cmd.Process.Pid)
	ready.Close()
	var buf [1]byte
	ack.Read(buf[:])
	ack.Close()
	cmd.Process.Release()
	for {
		var status syscall.WaitStatus
		_, waitErr := syscall.Wait4(-1, &status, 0, nil)
		if waitErr == syscall.EINTR {
			continue
		}
		if waitErr == syscall.ECHILD {
			return 0
		}
		if waitErr != nil {
			return 1
		}
	}
}

func (d *ProcessDriver) launch(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	reader, writer, err := os.Pipe()
	if err != nil {
		return err
	}
	defer reader.Close()
	defer writer.Close()
	ack, release, err := os.Pipe()
	if err != nil {
		return err
	}
	defer ack.Close()
	defer release.Close()
	command := exec.Command("/proc/self/exe", append([]string{"--reap"}, d.argv...)...)
	command.ExtraFiles = []*os.File{writer, ack}
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	if err = command.Start(); err != nil {
		return err
	}
	writer.Close()
	ack.Close()
	d.child = command
	d.state = "starting"
	d.owned = make(map[int]*identity)
	if err = checkedHandle(command.Process); err != nil {
		return err
	}
	s, err := statPID(command.Process.Pid)
	if err != nil {
		return err
	}
	d.guard = &identity{command.Process, s.start}
	stop := context.AfterFunc(ctx, func() { reader.Close() })
	defer stop()
	reader.SetReadDeadline(time.Now().Add(cleanupTimeout))
	var pid int
	if _, err = fmt.Fscanln(reader, &pid); err != nil {
		return err
	}
	if d.root, err = bind(pid, d.guard); err != nil {
		return err
	}
	d.owned[pid] = d.root
	d.gen++
	d.state = "running"
	return ctx.Err()
}
