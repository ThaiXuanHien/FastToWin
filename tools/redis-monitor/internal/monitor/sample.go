package monitor

import (
	"bytes"
	"strconv"
)

// Sample contains only the approved Redis-domain metric suffixes.
type Sample struct{ Values map[string]uint64 }

// Failure deliberately excludes underlying errors and their potentially sensitive text.
type Failure struct{ Reason string }

func (Failure) Error() string { return "redis monitor failure" }

type field struct {
	suffix string
	kind   byte
	core   bool
}

var selectedFields = map[string]field{
	"used_memory":               {"memory_used_bytes", 'n', true},
	"maxmemory":                 {"memory_max_bytes", 'n', true},
	"evicted_keys":              {"evicted_keys_total", 'n', true},
	"aof_enabled":               {"aof_enabled", 'b', true},
	"aof_current_size":          {"aof_current_size_bytes", 'n', false},
	"aof_base_size":             {"aof_base_size_bytes", 'n', false},
	"aof_last_write_status":     {"aof_last_write_ok", 's', false},
	"aof_last_bgrewrite_status": {"aof_last_rewrite_ok", 's', false},
	"aof_rewrite_in_progress":   {"aof_rewrite_in_progress", 'b', false},
	"aof_pending_bio_fsync":     {"aof_pending_fsync", 'n', false},
	"aof_delayed_fsync":         {"aof_delayed_fsync_total", 'n', false},
}

const maxInfoLine = 16 * 1024

func decimal(s string) bool {
	if len(s) == 0 {
		return false
	}
	for i := range s {
		if s[i] < '0' || s[i] > '9' {
			return false
		}
	}
	return true
}

func parseInfo(data []byte) (Sample, error) {
	values := make(map[string]uint64, len(selectedFields))
	for len(data) > 0 {
		end := bytes.IndexByte(data, '\n')
		if end < 0 {
			return Sample{}, Failure{"protocol"}
		}
		if end > maxInfoLine {
			return Sample{}, Failure{"oversized"}
		}
		line := data[:end]
		data = data[end+1:]
		if len(line) == 0 || line[len(line)-1] != '\r' {
			return Sample{}, Failure{"protocol"}
		}
		line = line[:len(line)-1]
		if len(line) == 0 || line[0] == '#' {
			continue
		}
		key, raw, hasColon := bytes.Cut(line, []byte{':'})
		entry, selected := selectedFields[string(key)]
		if !selected {
			continue
		}
		if !hasColon {
			return Sample{}, Failure{"invalid"}
		}
		if _, exists := values[entry.suffix]; exists {
			return Sample{}, Failure{"invalid"}
		}
		var value uint64
		if entry.kind == 's' {
			switch string(raw) {
			case "ok":
				value = 1
			case "err":
				value = 0
			default:
				return Sample{}, Failure{"invalid"}
			}
		} else {
			str := string(raw)
			if !decimal(str) {
				return Sample{}, Failure{"invalid"}
			}
			n, err := strconv.ParseUint(str, 10, 64)
			if err != nil || entry.kind == 'b' && n > 1 {
				return Sample{}, Failure{"invalid"}
			}
			value = n
		}
		values[entry.suffix] = value
	}
	for _, entry := range selectedFields {
		if entry.core || values["aof_enabled"] == 1 {
			if _, exists := values[entry.suffix]; !exists {
				return Sample{}, Failure{"missing"}
			}
		}
	}
	return Sample{Values: values}, nil
}
