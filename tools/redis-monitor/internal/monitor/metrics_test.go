package monitor

import (
	"strings"
	"testing"
	"time"
)

func TestMetricsExactExposition(t *testing.T) {
	s := NewStore(newFakeClock().Clock())
	sample, err := parseInfo([]byte(aofInfo))
	if err != nil {
		t.Fatal(err)
	}
	s.Record(sample, nil, 1500*time.Millisecond)
	const expected = `# HELP fasttowin_redis_monitor_up Whether the latest complete collection succeeded within 45 seconds.
# TYPE fasttowin_redis_monitor_up gauge
fasttowin_redis_monitor_up 1
# HELP fasttowin_redis_monitor_collection_duration_seconds Duration of the latest completed collection in seconds.
# TYPE fasttowin_redis_monitor_collection_duration_seconds gauge
fasttowin_redis_monitor_collection_duration_seconds 1.5
# HELP fasttowin_redis_monitor_last_success_timestamp_seconds Wall timestamp of the last successful collection; zero means never.
# TYPE fasttowin_redis_monitor_last_success_timestamp_seconds gauge
fasttowin_redis_monitor_last_success_timestamp_seconds 1800000000
# HELP fasttowin_redis_monitor_collection_errors_total Failed collections by fixed reason.
# TYPE fasttowin_redis_monitor_collection_errors_total counter
fasttowin_redis_monitor_collection_errors_total{reason="connect"} 0
fasttowin_redis_monitor_collection_errors_total{reason="timeout"} 0
fasttowin_redis_monitor_collection_errors_total{reason="auth"} 0
fasttowin_redis_monitor_collection_errors_total{reason="protocol"} 0
fasttowin_redis_monitor_collection_errors_total{reason="oversized"} 0
fasttowin_redis_monitor_collection_errors_total{reason="missing"} 0
fasttowin_redis_monitor_collection_errors_total{reason="invalid"} 0
# HELP fasttowin_redis_monitor_memory_used_bytes Redis used memory in bytes.
# TYPE fasttowin_redis_monitor_memory_used_bytes gauge
fasttowin_redis_monitor_memory_used_bytes 1024
# HELP fasttowin_redis_monitor_memory_max_bytes Redis memory limit in bytes; zero means unlimited.
# TYPE fasttowin_redis_monitor_memory_max_bytes gauge
fasttowin_redis_monitor_memory_max_bytes 0
# HELP fasttowin_redis_monitor_evicted_keys_total Redis source counter of evicted keys.
# TYPE fasttowin_redis_monitor_evicted_keys_total counter
fasttowin_redis_monitor_evicted_keys_total 18446744073709551615
# HELP fasttowin_redis_monitor_aof_enabled Whether Redis AOF is enabled.
# TYPE fasttowin_redis_monitor_aof_enabled gauge
fasttowin_redis_monitor_aof_enabled 1
# HELP fasttowin_redis_monitor_aof_current_size_bytes Current Redis AOF size in bytes.
# TYPE fasttowin_redis_monitor_aof_current_size_bytes gauge
fasttowin_redis_monitor_aof_current_size_bytes 12
# HELP fasttowin_redis_monitor_aof_base_size_bytes Base Redis AOF size in bytes.
# TYPE fasttowin_redis_monitor_aof_base_size_bytes gauge
fasttowin_redis_monitor_aof_base_size_bytes 8
# HELP fasttowin_redis_monitor_aof_last_write_ok Whether the last Redis AOF write succeeded.
# TYPE fasttowin_redis_monitor_aof_last_write_ok gauge
fasttowin_redis_monitor_aof_last_write_ok 0
# HELP fasttowin_redis_monitor_aof_last_rewrite_ok Whether the last Redis AOF rewrite succeeded.
# TYPE fasttowin_redis_monitor_aof_last_rewrite_ok gauge
fasttowin_redis_monitor_aof_last_rewrite_ok 0
# HELP fasttowin_redis_monitor_aof_rewrite_in_progress Whether a Redis AOF rewrite is in progress.
# TYPE fasttowin_redis_monitor_aof_rewrite_in_progress gauge
fasttowin_redis_monitor_aof_rewrite_in_progress 0
# HELP fasttowin_redis_monitor_aof_pending_fsync Pending Redis AOF background fsync operations.
# TYPE fasttowin_redis_monitor_aof_pending_fsync gauge
fasttowin_redis_monitor_aof_pending_fsync 1
# HELP fasttowin_redis_monitor_aof_delayed_fsync_total Redis source counter of delayed AOF fsync operations.
# TYPE fasttowin_redis_monitor_aof_delayed_fsync_total counter
fasttowin_redis_monitor_aof_delayed_fsync_total 2
`
	if actual := string(s.Metrics()); actual != expected {
		t.Fatal("exposition contract changed:\n" + actual)
	}
	// Optional absent values contribute neither a sample nor misleading metadata.
	s.Record(validSample(), nil, 0)
	if strings.Contains(string(s.Metrics()), "fasttowin_redis_monitor_aof_pending_fsync") {
		t.Fatal("invented disabled-AOF field")
	}
}
