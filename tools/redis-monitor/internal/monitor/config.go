package monitor

import (
	"io"
	"net"
	"os"
	"strconv"
	"strings"
	"unicode"
)

// Config selects one Redis endpoint and a private metrics listener.
type Config struct {
	Address, PasswordFile, Listen string
	TLS                           bool
}

// LoadConfig reads only the four collector-specific environment variables.
func LoadConfig(getenv func(string) string) (Config, error) {
	cfg := Config{Address: getenv("FASTTOWIN_REDIS_MONITOR_ADDRESS"), PasswordFile: getenv("FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE"), Listen: getenv("FASTTOWIN_REDIS_MONITOR_LISTEN")}
	tlsValue := getenv("FASTTOWIN_REDIS_MONITOR_TLS")
	if cfg.Address == "" || cfg.PasswordFile == "" {
		return Config{}, Failure{"missing"}
	}
	if cfg.Listen == "" {
		cfg.Listen = "127.0.0.1:19121"
	}
	switch tlsValue {
	case "", "false":
	case "true":
		cfg.TLS = true
	default:
		return Config{}, Failure{"invalid"}
	}
	if !validAddress(cfg.Address) || !validAddress(cfg.Listen) {
		return Config{}, Failure{"invalid"}
	}
	return cfg, nil
}

func validAddress(address string) bool {
	if strings.ContainsAny(address, "/@?#\\") || strings.IndexFunc(address, func(r rune) bool { return unicode.IsSpace(r) || unicode.IsControl(r) }) >= 0 {
		return false
	}
	host, port, err := net.SplitHostPort(address)
	if err != nil || host == "" || !decimal(port) {
		return false
	}
	// Colons are permitted only in literal IPv6 addresses, never URI prefixes.
	if strings.ContainsAny(host, "[]") || (strings.Contains(host, ":") && net.ParseIP(host) == nil) {
		return false
	}
	n, err := strconv.ParseUint(port, 10, 16)
	return err == nil && n > 0
}

func readSecret(path string) ([]byte, error) {
	if path == "" {
		return nil, Failure{"missing"}
	}
	info, err := os.Lstat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, Failure{"missing"}
		}
		return nil, Failure{"invalid"}
	}
	if !info.Mode().IsRegular() || info.Size() > 128 {
		return nil, Failure{"invalid"}
	}
	f, err := os.Open(path)
	if err != nil {
		return nil, Failure{"invalid"}
	}
	defer f.Close()
	info, err = f.Stat()
	if err != nil || !info.Mode().IsRegular() || info.Size() > 128 {
		return nil, Failure{"invalid"}
	}
	data, err := io.ReadAll(io.LimitReader(f, 128))
	if err != nil {
		clear(data)
		return nil, Failure{"invalid"}
	}
	length := len(data)
	if length == 98 && string(data[96:]) == "\r\n" {
		length = 96
	} else if length == 97 && data[96] == '\n' {
		length = 96
	}
	valid := length == 96
	if valid {
		for _, b := range data[:96] {
			if !(b >= '0' && b <= '9' || b >= 'a' && b <= 'f') {
				valid = false
				break
			}
		}
	}
	if !valid {
		clear(data)
		return nil, Failure{"invalid"}
	}
	secret := append([]byte(nil), data[:96]...)
	clear(data)
	return secret, nil
}
