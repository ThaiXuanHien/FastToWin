package monitor

import (
	"strings"
	"testing"
)

func TestAOFDetailRequiredOnlyWhenEnabled(t *testing.T) {
	for key, entry := range selectedFields {
		if entry.core {
			continue
		}
		t.Run(key, func(t *testing.T) {
			lines := strings.SplitAfter(aofInfo, "\n")
			var missing string
			for _, line := range lines {
				if !strings.HasPrefix(line, key+":") {
					missing += line
				}
			}
			_, err := parseInfo([]byte(missing))
			requireReason(t, err, "missing")
			sample, err := parseInfo([]byte(strings.Replace(missing, "aof_enabled:1", "aof_enabled:0", 1)))
			if err != nil {
				t.Fatal("absent disabled AOF detail rejected")
			}
			if _, ok := sample.Values[entry.suffix]; ok {
				t.Fatal("fabricated absent metric")
			}
		})
	}
}

func TestStatusOKAndNumericBounds(t *testing.T) {
	sample, err := parseInfo([]byte(strings.ReplaceAll(aofInfo, ":err\r\n", ":ok\r\n")))
	if err != nil || sample.Values["aof_last_write_ok"] != 1 || sample.Values["aof_last_rewrite_ok"] != 1 {
		t.Fatal("OK status mapping failed")
	}
	for _, bad := range []string{"-1", "18446744073709551616", "NaN", "1.5", " 1", "1 ", "+1"} {
		_, err := parseInfo([]byte(coreInfo + "aof_pending_bio_fsync:" + bad + "\r\n"))
		requireReason(t, err, "invalid")
	}
}
