// Room fault supervisor is a private, test-only container PID 1.
package main

import (
	"context"
	"errors"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	supervisor "github.com/ThaiXuanHien/FastToWin/tools/room-fault-supervisor"
)

func main() {
	if len(os.Args) > 1 && os.Args[1] == "--reap" {
		os.Exit(supervisor.RunReaper(os.Args[2:]))
	}
	os.Exit(run())
}

func run() int {
	if os.Getpid() != 1 || os.Geteuid() == 0 {
		return 1
	}
	role := os.Getenv("ROOM_FAULT_ROLE")
	var argv []string
	switch role {
	case "a", "b":
		argv = []string{"/opt/fasttowin/bin/server"}
	case "pg":
		argv = []string{"/usr/local/bin/docker-entrypoint.sh", "postgres"}
	case "redis":
		argv = []string{"/usr/local/bin/room-fixture-redis"}
	case "proxy":
		// Fixed child command and immutable endpoints live inside the driver.
	default:
		return 1
	}
	millis, err := strconv.ParseInt(os.Getenv("ROOM_FAULT_DEADLINE_MS"), 10, 64)
	if err != nil {
		return 1
	}
	deadline := time.UnixMilli(millis)
	life, cancel := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	defer cancel()
	ctx, stop := context.WithDeadline(life, deadline)
	defer stop()
	var d supervisor.Driver
	if role == "proxy" {
		d, err = supervisor.NewProxyDriver(ctx, os.Getenv("ROOM_FAULT_FIXTURE"))
	} else {
		d, err = supervisor.NewProcessDriver(ctx, role, argv)
	}
	if err != nil {
		return 1
	}
	s, err := supervisor.NewService(supervisor.Config{Role: role, FixtureID: os.Getenv("ROOM_FAULT_FIXTURE"), Token: os.Getenv("ROOM_FAULT_TOKEN"), Deadline: deadline}, d)
	if err != nil {
		clean, end := context.WithTimeout(context.Background(), 5*time.Second)
		defer end()
		d.Close(clean)
		return 1
	}
	server := &http.Server{Addr: ":8181", Handler: s, ReadHeaderTimeout: 2 * time.Second, ReadTimeout: 5 * time.Second, WriteTimeout: 65 * time.Second, IdleTimeout: 10 * time.Second, MaxHeaderBytes: 4096, BaseContext: func(net.Listener) context.Context { return ctx }}
	exited := make(chan error, 1)
	go func() { exited <- server.ListenAndServe() }()
	go func() {
		ticker := time.NewTicker(50 * time.Millisecond)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				s.Tick(ctx)
			}
		}
	}()
	result := 0
	select {
	case <-ctx.Done():
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			result = 1
		}
	case <-exited:
		result = 1
	}
	server.Close()
	for i := 0; i < 3; i++ {
		err = s.Close(context.Background())
		if err == nil {
			return result
		}
		if err.Error() != "close_failed" && err.Error() != "control_timeout" {
			return 1
		}
	}
	// PID 1 exit is the final kernel-enforced boundary for the private namespace.
	return 1
}
