package monitor

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const testSecret = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"

func secretFile(t *testing.T, value string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "synthetic-secret")
	if err := os.WriteFile(path, []byte(value), 0600); err != nil {
		t.Fatal("write synthetic secret failed")
	}
	return path
}

func requireReason(t *testing.T, err error, reason string) {
	t.Helper()
	var failure Failure
	if !errors.As(err, &failure) {
		t.Fatalf("expected reason %s, got sanitized failure classification mismatch", reason)
	}
	if failure.Reason != reason {
		switch failure.Reason {
		case "connect", "timeout", "auth", "protocol", "oversized", "missing", "invalid":
			t.Fatalf("expected reason %s, got %s", reason, failure.Reason)
		default:
			t.Fatal("unapproved failure reason")
		}
	}
	if strings.Contains(err.Error(), testSecret) || strings.Contains(err.Error(), "SECRET_MARKER") {
		t.Fatal("unsafe error text")
	}
}

func TestLoadConfig(t *testing.T) {
	base := map[string]string{"FASTTOWIN_REDIS_MONITOR_ADDRESS": "localhost:6379", "FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE": "synthetic-file"}
	cases := []struct {
		name, key, value string
		valid            bool
	}{
		{"default", "", "", true}, {"ipv6", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "[::1]:6379", true},
		{"tls", "FASTTOWIN_REDIS_MONITOR_TLS", "true", true}, {"container", "FASTTOWIN_REDIS_MONITOR_LISTEN", "0.0.0.0:19121", true},
		{"missing-address", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "", false}, {"missing-file", "FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE", "", false},
		{"uri", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "redis://user:SECRET_MARKER@localhost:6379", false},
		{"path", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host:6379/path", false}, {"query", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host:6379?x", false},
		{"fragment", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host:6379#x", false}, {"credentials", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "user@host:6379", false},
		{"space", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host :6379", false}, {"control", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host\n:6379", false},
		{"no-port", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host", false}, {"no-host", "FASTTOWIN_REDIS_MONITOR_ADDRESS", ":6379", false},
		{"zero", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host:0", false}, {"overflow", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host:65536", false},
		{"negative", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host:-1", false}, {"plus", "FASTTOWIN_REDIS_MONITOR_ADDRESS", "host:+1", false},
		{"bad-tls", "FASTTOWIN_REDIS_MONITOR_TLS", "TRUE", false}, {"bad-listen", "FASTTOWIN_REDIS_MONITOR_LISTEN", "SECRET_MARKER", false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			values := map[string]string{}
			for k, v := range base {
				values[k] = v
			}
			if tc.key != "" {
				values[tc.key] = tc.value
			}
			cfg, err := LoadConfig(func(k string) string {
				if _, ok := base[k]; !ok && k != "FASTTOWIN_REDIS_MONITOR_TLS" && k != "FASTTOWIN_REDIS_MONITOR_LISTEN" {
					t.Fatal("read unapproved environment variable")
				}
				return values[k]
			})
			if !tc.valid {
				if err == nil {
					t.Fatal("expected rejection")
				}
				if strings.Contains(err.Error(), "SECRET_MARKER") {
					t.Fatal("unsafe config error")
				}
				return
			}
			if err != nil {
				t.Fatal("valid config rejected")
			}
			if cfg.Listen == "" || cfg.Address != values["FASTTOWIN_REDIS_MONITOR_ADDRESS"] || cfg.PasswordFile != "synthetic-file" || cfg.TLS != (tc.name == "tls") {
				t.Fatal("configuration mismatch")
			}
			if tc.name == "default" && cfg.Listen != "127.0.0.1:19121" {
				t.Fatal("wrong listen default")
			}
		})
	}
}

func TestSecretValidation(t *testing.T) {
	for _, tc := range []struct {
		name, value string
		valid       bool
	}{
		{"exact", testSecret, true}, {"lf", testSecret + "\n", true}, {"crlf", testSecret + "\r\n", true},
		{"empty", "", false}, {"short", testSecret[:95], false}, {"long", testSecret + "a", false},
		{"uppercase", strings.ToUpper(testSecret), false}, {"marker", "SECRET_MARKER", false}, {"multiple-lines", testSecret + "\n\n", false},
		{"cr", testSecret + "\r", false}, {"oversized", strings.Repeat("a", 129), false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, err := NewReader(Config{Address: "localhost:6379", PasswordFile: secretFile(t, tc.value)})
			if (err == nil) != tc.valid {
				t.Fatal("secret validation mismatch")
			}
			if err != nil {
				requireReason(t, err, "invalid")
			}
		})
	}
	for _, path := range []string{"", filepath.Join(t.TempDir(), "missing")} {
		_, err := NewReader(Config{Address: "localhost:6379", PasswordFile: path})
		requireReason(t, err, "missing")
	}
	_, err := NewReader(Config{Address: "localhost:6379", PasswordFile: t.TempDir()})
	requireReason(t, err, "invalid")
	_, err = NewReader(Config{Address: "redis://SECRET_MARKER", PasswordFile: secretFile(t, testSecret)})
	requireReason(t, err, "invalid")
}
