// Acceptance-only fixed synthetic probes, run inside the owned internal network.
// This is not part of the collector module or its production image.
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

func require(ok bool) {
	if !ok {
		panic("fixture_assertion_failed")
	}
}
func command(role, password string, args ...string) string {
	deadline := time.Now().Add(3 * time.Second)
	c, err := net.DialTimeout("tcp", "redis:6379", 3*time.Second)
	require(err == nil)
	defer c.Close()
	require(c.SetDeadline(deadline) == nil)
	r := bufio.NewReaderSize(c, 8192)
	send := func(parts []string) {
		var b strings.Builder
		fmt.Fprintf(&b, "*%d\r\n", len(parts))
		for _, p := range parts {
			fmt.Fprintf(&b, "$%d\r\n%s\r\n", len(p), p)
		}
		_, err := io.WriteString(c, b.String())
		require(err == nil)
	}
	read := func() string {
		frame, err := r.ReadSlice('\n')
		line := string(frame)
		require(err == nil && len(line) <= 8192 && strings.HasSuffix(line, "\r\n"))
		if strings.HasPrefix(line, "$") {
			n, err := strconv.Atoi(strings.TrimSuffix(line[1:], "\r\n"))
			require(err == nil && n >= 0 && n <= 131072)
			body := make([]byte, n+2)
			_, err = io.ReadFull(r, body)
			require(err == nil && string(body[n:]) == "\r\n")
			return string(body[:n])
		}
		return line
	}
	send([]string{"AUTH", role, password})
	require(read() == "+OK\r\n")
	send(args)
	return read()
}

var client = &http.Client{Timeout: 2 * time.Second}

func get(route string) (int, string) {
	r, err := client.Get("http://collector:19121" + route)
	if err != nil {
		return 0, ""
	}
	defer r.Body.Close()
	b, err := io.ReadAll(io.LimitReader(r.Body, 131073))
	require(err == nil && len(b) <= 131072)
	return r.StatusCode, string(b)
}
func wait(test func() bool) {
	deadline := time.Now().Add(45 * time.Second)
	for time.Now().Before(deadline) {
		if test() {
			return
		}
		time.Sleep(250 * time.Millisecond)
	}
	panic("fixture_deadline")
}
func metrics() map[string]uint64 {
	status, body := get("/metrics")
	require(status == 200)
	m := map[string]uint64{}
	for _, line := range strings.Split(body, "\n") {
		f := strings.Fields(line)
		if len(f) != 2 || !strings.HasPrefix(f[0], "fasttowin_redis_monitor_") {
			continue
		}
		n, err := strconv.ParseUint(f[1], 10, 64)
		if err == nil {
			m[strings.TrimPrefix(f[0], "fasttowin_redis_monitor_")] = n
		}
	}
	return m
}
func requireUnavailable(m map[string]uint64) {
	up, present := m["up"]
	require(present && up == 0)
	_, present = m["aof_enabled"]
	require(!present)
	_, present = m["memory_used_bytes"]
	require(!present)
}
func main() {
	watchdog := time.AfterFunc(100*time.Second, func() { fmt.Fprintln(os.Stderr, "runtime_probe_deadline"); os.Exit(1) })
	defer watchdog.Stop()
	// Only fixed safe failure codes reach orchestration; never panic with frames.
	defer func() {
		if recover() != nil {
			fmt.Fprintln(os.Stderr, "runtime_probe_failed")
			os.Exit(1)
		}
	}()
	require(len(os.Args) == 3)
	phase, namespace := os.Args[1], os.Args[2]
	require(strings.HasPrefix(namespace, "fixture_") && len(namespace) == 40)
	result := map[string]any{"phase": phase}
	if phase == "down" {
		wait(func() bool { s, _ := get("/ready"); return s == 503 })
		m := metrics()
		requireUnavailable(m)
		result["unavailable"] = true
	} else {
		require(phase == "healthy" || phase == "recovery")
		wait(func() bool { s, _ := get("/ready"); return s == 200 })
		before := metrics()
		require(before["up"] == 1 && before["aof_enabled"] == 1 && before["aof_last_write_ok"] == 1)
		for _, key := range []string{"aof_current_size_bytes", "aof_base_size_bytes", "aof_last_rewrite_ok", "aof_rewrite_in_progress", "aof_pending_fsync", "aof_delayed_fsync_total", "evicted_keys_total"} {
			_, ok := before[key]
			require(ok)
		}
		if phase == "healthy" {
			monitor, err := os.ReadFile("/run/secrets/monitor")
			require(err == nil && len(monitor) == 96)
			app, err := os.ReadFile("/run/secrets/app")
			require(err == nil && len(app) == 96)
			require(command("ftw_monitor", string(monitor), "PING") == "+PONG\r\n")
			require(strings.Contains(command("ftw_monitor", string(monitor), "INFO", "memory", "persistence", "stats"), "aof_enabled:1"))
			for _, args := range [][]string{{"GET", "fixture-denied"}, {"HSET", "fixture-denied", "field", "value"}, {"SCAN", "0"}, {"CONFIG", "GET", "maxmemory"}, {"FLUSHDB"}} {
				require(strings.HasPrefix(command("ftw_monitor", string(monitor), args...), "-NOPERM"))
			}
			result["aclAllow"] = []string{"AUTH", "PING", "INFO memory persistence stats"}
			result["aclDeny"] = []string{"GET", "HSET", "SCAN", "CONFIG", "FLUSHDB"}
			key := "ftw:" + namespace + ":rl:v1:fixture-" + namespace[8:]
			for i := 0; i < 100; i++ {
				require(command("ftw_app", string(app), "HSET", key, strconv.Itoa(i), strings.Repeat("f", 2048)) == ":1\r\n")
			}
			wait(func() bool { return metrics()["aof_current_size_bytes"] > before["aof_current_size_bytes"] })
			result["writes"] = 100
			result["aofBeforeBytes"] = before["aof_current_size_bytes"]
		}
		result["metrics"] = metrics()
	}
	require(json.NewEncoder(os.Stdout).Encode(result) == nil)
}
