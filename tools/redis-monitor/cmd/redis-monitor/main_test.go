package main

import (
	"bytes"
	"context"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

func TestInvalidConfigurationExitsWithSafeMessage(t *testing.T) {
	if os.Getenv("REDIS_MONITOR_TEST_HELPER") == "1" {
		main()
		return
	}
	executable, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	cmd := exec.Command(executable, "-test.run=^TestInvalidConfigurationExitsWithSafeMessage$")
	for _, value := range os.Environ() {
		if !strings.HasPrefix(value, "FASTTOWIN_REDIS_MONITOR_") && !strings.HasPrefix(value, "REDIS_MONITOR_TEST_HELPER=") {
			cmd.Env = append(cmd.Env, value)
		}
	}
	cmd.Env = append(cmd.Env, "REDIS_MONITOR_TEST_HELPER=1", "FASTTOWIN_REDIS_MONITOR_ADDRESS=secret-marker", "FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE=secret-marker")
	out, err := cmd.CombinedOutput()
	if err == nil || string(out) != "configuration_failed\n" {
		t.Fatalf("exit=%v, output=%q", err, out)
	}
}

func TestStartupSecretFailureIsSafe(t *testing.T) {
	var out bytes.Buffer
	env := map[string]string{"FASTTOWIN_REDIS_MONITOR_ADDRESS": "127.0.0.1:19120", "FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE": filepath.Join(t.TempDir(), "secret-marker")}
	if code := execute(context.Background(), func(k string) string { return env[k] }, &out); code != 1 || out.String() != "configuration_failed\n" {
		t.Fatal(code, out.String())
	}
}

func TestCanceledServiceExitsCleanly(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	listen := ln.Addr().String()
	_ = ln.Close()
	secret := filepath.Join(t.TempDir(), "synthetic-secret")
	if err := os.WriteFile(secret, []byte(strings.Repeat("a", 96)), 0600); err != nil {
		t.Fatal(err)
	}
	env := map[string]string{"FASTTOWIN_REDIS_MONITOR_ADDRESS": listen, "FASTTOWIN_REDIS_MONITOR_LISTEN": listen, "FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE": secret}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	var out bytes.Buffer
	if code := execute(ctx, func(k string) string { return env[k] }, &out); code != 0 || out.String() != "started\nstopped\n" {
		t.Fatal(code, out.String())
	}
}
