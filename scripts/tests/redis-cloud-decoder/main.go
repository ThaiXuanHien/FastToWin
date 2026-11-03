// Fixture-only matching Prometheus decoder. Emit timestamps, never values,
// arbitrary labels, raw WAL records, credentials or compressed payloads.
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"os"
	"sort"

	"github.com/golang/snappy"
	"github.com/prometheus/prometheus/model/labels"
	"github.com/prometheus/prometheus/prompb"
	"github.com/prometheus/prometheus/tsdb/record"
	"github.com/prometheus/prometheus/tsdb/wlog"
)

const metric = "fasttowin_redis_monitor_memory_used_bytes"

func allowed(get func(string) string, length int) bool {
	return length == 3 && get("__name__") == metric && get("job") == "fasttowin-redis-monitor" && get("instance") == "redis-primary"
}

func remote(body []byte) ([]int64, error) {
	size, err := snappy.DecodedLen(body)
	if err != nil || size > 4*1024*1024 {
		return nil, fmt.Errorf("invalid payload")
	}
	plain, err := snappy.Decode(nil, body)
	if err != nil {
		return nil, err
	}
	var request prompb.WriteRequest
	if err = request.Unmarshal(plain); err != nil {
		return nil, err
	}
	times := []int64{}
	for _, s := range request.Timeseries {
		m := map[string]string{}
		for _, l := range s.Labels {
			m[l.Name] = l.Value
		}
		if allowed(func(k string) string { return m[k] }, len(s.Labels)) {
			for _, sample := range s.Samples {
				times = append(times, sample.Timestamp)
			}
		}
	}
	return times, nil
}

func wal(dir string) ([]int64, error) {
	reader, err := wlog.NewSegmentsReader(dir)
	if err != nil {
		return nil, err
	}
	defer reader.Close()
	r := wlog.NewReader(reader)
	dec := record.NewDecoder(labels.NewSymbolTable(), slog.New(slog.NewTextHandler(io.Discard, nil)))
	refs := map[uint64]bool{}
	times := []int64{}
	for r.Next() {
		raw := r.Record()
		switch dec.Type(raw) {
		case record.Series:
			series, e := dec.Series(raw, nil)
			if e != nil {
				return nil, e
			}
			for _, s := range series {
				refs[uint64(s.Ref)] = allowed(s.Labels.Get, s.Labels.Len())
			}
		case record.Samples, record.SamplesV2:
			samples, e := dec.Samples(raw, nil)
			if e != nil {
				return nil, e
			}
			for _, s := range samples {
				if refs[uint64(s.Ref)] {
					times = append(times, s.T)
				}
			}
		}
	}
	return times, r.Err()
}

func main() {
	var times []int64
	var err error
	if len(os.Args) == 3 && os.Args[1] == "wal" {
		times, err = wal(os.Args[2])
	} else if len(os.Args) == 2 && os.Args[1] == "remote" {
		var body []byte
		body, err = io.ReadAll(io.LimitReader(os.Stdin, 1048577))
		if err == nil && len(body) <= 1048576 {
			times, err = remote(body)
		} else {
			err = fmt.Errorf("invalid input")
		}
	} else {
		err = fmt.Errorf("invalid mode")
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "diagnostic_decode_failed")
		os.Exit(1)
	}
	sort.Slice(times, func(i, j int) bool { return times[i] < times[j] })
	json.NewEncoder(os.Stdout).Encode(struct {
		Metric string  `json:"metric"`
		Times  []int64 `json:"memoryTimes"`
	}{metric, times})
}
