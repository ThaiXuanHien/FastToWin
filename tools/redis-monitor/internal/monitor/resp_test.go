package monitor

import (
	"bufio"
	"fmt"
	"strings"
	"testing"
)

func TestBoundedRESPBulkReplies(t *testing.T) {
	for _, tc := range []struct{ name, reply, reason string }{
		{"empty", "$0\r\n\r\n", ""},
		{"maximum", fmt.Sprintf("$%d\r\n%s\r\n", maxInfoBytes, strings.Repeat("x", maxInfoBytes)), ""},
		{"too-large", "$1048577\r\n", "oversized"},
		{"truncated", "$3\r\nx", "protocol"},
		{"null", "$-1\r\n", "protocol"},
		{"array", "*1\r\n", "protocol"},
		{"integer", ":5\r\n", "protocol"},
		{"length-overflow", "$18446744073709551616\r\n", "protocol"},
		{"header-overflow", "$" + strings.Repeat("1", 257) + "\r\n", "oversized"},
		{"bad-header-ending", "$3\nxxx\r\n", "protocol"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			data, err := infoReply(bufio.NewReader(strings.NewReader(tc.reply)))
			if tc.reason != "" {
				requireReason(t, classify(t.Context(), err, "protocol"), tc.reason)
				return
			}
			if err != nil {
				t.Fatal("valid bulk rejected")
			}
			if tc.name == "maximum" && len(data) != maxInfoBytes {
				t.Fatal("maximum bulk length mismatch")
			}
		})
	}
}
