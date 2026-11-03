package monitor

import (
	"bytes"
	"strconv"
)

const metricPrefix = "fasttowin_redis_monitor_"

type metric struct{ suffix, kind, help string }

var domainMetrics = [...]metric{
	{"memory_used_bytes", "gauge", "Redis used memory in bytes."},
	{"memory_max_bytes", "gauge", "Redis memory limit in bytes; zero means unlimited."},
	{"evicted_keys_total", "counter", "Redis source counter of evicted keys."},
	{"aof_enabled", "gauge", "Whether Redis AOF is enabled."},
	{"aof_current_size_bytes", "gauge", "Current Redis AOF size in bytes."},
	{"aof_base_size_bytes", "gauge", "Base Redis AOF size in bytes."},
	{"aof_last_write_ok", "gauge", "Whether the last Redis AOF write succeeded."},
	{"aof_last_rewrite_ok", "gauge", "Whether the last Redis AOF rewrite succeeded."},
	{"aof_rewrite_in_progress", "gauge", "Whether a Redis AOF rewrite is in progress."},
	{"aof_pending_fsync", "gauge", "Pending Redis AOF background fsync operations."},
	{"aof_delayed_fsync_total", "counter", "Redis source counter of delayed AOF fsync operations."},
}

func metadata(b *bytes.Buffer, m metric) {
	b.WriteString("# HELP " + metricPrefix + m.suffix + " " + m.help + "\n# TYPE " + metricPrefix + m.suffix + " " + m.kind + "\n")
}

func number(b *bytes.Buffer, suffix, value string) {
	b.WriteString(metricPrefix + suffix + " " + value + "\n")
}

// Metrics returns independent bytes containing one consistent snapshot.
func (s *Store) Metrics() []byte {
	s.mu.Lock()
	defer s.mu.Unlock()
	var b bytes.Buffer
	metadata(&b, metric{"up", "gauge", "Whether the latest complete collection succeeded within 45 seconds."})
	fresh := s.fresh()
	up := "0"
	if fresh {
		up = "1"
	}
	number(&b, "up", up)
	metadata(&b, metric{"collection_duration_seconds", "gauge", "Duration of the latest completed collection in seconds."})
	number(&b, "collection_duration_seconds", strconv.FormatFloat(s.duration.Seconds(), 'f', -1, 64))
	metadata(&b, metric{"last_success_timestamp_seconds", "gauge", "Wall timestamp of the last successful collection; zero means never."})
	number(&b, "last_success_timestamp_seconds", strconv.FormatInt(s.lastSuccess, 10))
	metadata(&b, metric{"collection_errors_total", "counter", "Failed collections by fixed reason."})
	for i, reason := range reasons {
		number(&b, "collection_errors_total{reason=\""+reason+"\"}", strconv.FormatUint(s.errors[i], 10))
	}
	for _, m := range domainMetrics {
		if value, exists := s.values[m.suffix]; fresh && exists {
			metadata(&b, m)
			number(&b, m.suffix, strconv.FormatUint(value, 10))
		}
	}
	return b.Bytes()
}
