package main

import (
	"github.com/golang/snappy"
	"github.com/prometheus/prometheus/model/labels"
	"github.com/prometheus/prometheus/prompb"
	"github.com/prometheus/prometheus/tsdb/chunks"
	"github.com/prometheus/prometheus/tsdb/record"
	"github.com/prometheus/prometheus/tsdb/wlog"
	"github.com/prometheus/prometheus/util/compression"
	"os"
	"path/filepath"
	"testing"
)

func TestWALIncludesCheckpointAndActiveRecords(t *testing.T) {
	dir := t.TempDir()
	checkpoint := filepath.Join(dir, "checkpoint.00000002")
	if e := os.Mkdir(checkpoint, 0700); e != nil {
		t.Fatal(e)
	}
	w, e := wlog.New(nil, nil, checkpoint, compression.None)
	if e != nil {
		t.Fatal(e)
	}
	var enc record.Encoder
	if e = w.Log(enc.Series([]record.RefSeries{{Ref: 1, Labels: labels.FromStrings("__name__", metric, "job", "fasttowin-redis-monitor", "instance", "redis-primary")}}, nil)); e != nil {
		t.Fatal(e)
	}
	if e = w.Log(enc.Samples([]record.RefSample{{Ref: 1, T: 1000}}, nil)); e != nil {
		t.Fatal(e)
	}
	w.Close()
	active, e := wlog.New(nil, nil, dir, compression.None)
	if e != nil {
		t.Fatal(e)
	}
	active.Log(enc.Samples([]record.RefSample{{Ref: 1, T: 5000}}, nil))
	active.Close()
	times, e := wal(dir)
	if e != nil || len(times) != 2 || times[0] != 1000 || times[1] != 5000 {
		t.Fatalf("checkpoint/active evidence missing: %v %v", times, e)
	}
}

func TestRemoteSanitizes(t *testing.T) {
	good := []prompb.Label{{Name: "__name__", Value: metric}, {Name: "job", Value: "fasttowin-redis-monitor"}, {Name: "instance", Value: "redis-primary"}}
	request := prompb.WriteRequest{Timeseries: []prompb.TimeSeries{{Labels: good, Samples: []prompb.Sample{{Timestamp: 12345, Value: 987}}}, {Labels: append(append([]prompb.Label{}, good...), prompb.Label{Name: "private", Value: "never-output"}), Samples: []prompb.Sample{{Timestamp: 99999}}}}}
	raw, e := request.Marshal()
	if e != nil {
		t.Fatal(e)
	}
	times, e := remote(snappy.Encode(nil, raw))
	if e != nil || len(times) != 1 || times[0] != 12345 {
		t.Fatalf("unexpected sanitized timestamps: %v %v", times, e)
	}
	if _, e = remote([]byte("invalid")); e == nil {
		t.Fatal("invalid compressed body accepted")
	}
}

func TestMatchingWALTimestampDecoder(t *testing.T) {
	dir := t.TempDir()
	w, e := wlog.New(nil, nil, dir, compression.None)
	if e != nil {
		t.Fatal(e)
	}
	var enc record.Encoder
	series := []record.RefSeries{{Ref: chunks.HeadSeriesRef(1), Labels: labels.FromStrings("__name__", metric, "job", "fasttowin-redis-monitor", "instance", "redis-primary")}, {Ref: chunks.HeadSeriesRef(2), Labels: labels.FromStrings("__name__", "unapproved")}}
	if e = w.Log(enc.Series(series, nil)); e != nil {
		t.Fatal(e)
	}
	if e = w.Log(enc.Samples([]record.RefSample{{Ref: 1, T: 12345, V: 789}, {Ref: 1, T: 22345, V: 789}, {Ref: 2, T: 99999, V: 999}}, nil)); e != nil {
		t.Fatal(e)
	}
	if e = w.Close(); e != nil {
		t.Fatal(e)
	}
	times, e := wal(dir)
	if e != nil || len(times) != 2 || times[0] != 12345 || times[1] != 22345 {
		t.Fatalf("unexpected WAL timestamps: %v %v", times, e)
	}
}
