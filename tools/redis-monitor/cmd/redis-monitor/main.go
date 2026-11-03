package main

import (
	"context"
	"io"
	"os"
	"os/signal"
	"syscall"

	"github.com/ThaiXuanHien/FastToWin/tools/redis-monitor/internal/monitor"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	code := execute(ctx, os.Getenv, os.Stderr)
	stop()
	os.Exit(code)
}

func execute(ctx context.Context, getenv func(string) string, out io.Writer) int {
	cfg, err := monitor.LoadConfig(getenv)
	if err != nil {
		_, _ = io.WriteString(out, "configuration_failed\n")
		return 1
	}
	reader, err := monitor.NewReader(cfg)
	if err != nil {
		_, _ = io.WriteString(out, "configuration_failed\n")
		return 1
	}
	if err := monitor.Run(ctx, cfg, reader, out); err != nil {
		_, _ = io.WriteString(out, "service_failed\n")
		return 1
	}
	return 0
}
