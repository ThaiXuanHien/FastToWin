package monitor

import (
	"bufio"
	"io"
	"strconv"
)

const maxReplyHeader = 256
const maxInfoBytes = 1024 * 1024

func replyHeader(r *bufio.Reader) ([]byte, error) {
	line := make([]byte, 0, 32)
	for {
		b, err := r.ReadByte()
		if err != nil {
			return nil, err
		}
		if len(line) >= maxReplyHeader {
			return nil, Failure{"oversized"}
		}
		line = append(line, b)
		if b == '\n' {
			if len(line) < 3 || line[len(line)-2] != '\r' {
				return nil, Failure{"protocol"}
			}
			return line[:len(line)-2], nil
		}
	}
}

func simpleReply(r *bufio.Reader, expected, rejection string) error {
	line, err := replyHeader(r)
	if err != nil {
		return err
	}
	defer clear(line)
	if line[0] == '-' {
		return Failure{rejection}
	}
	if line[0] != '+' {
		return Failure{"protocol"}
	}
	if string(line[1:]) != expected {
		return Failure{rejection}
	}
	return nil
}

func infoReply(r *bufio.Reader) ([]byte, error) {
	line, err := replyHeader(r)
	if err != nil {
		return nil, err
	}
	defer clear(line)
	if line[0] != '$' || !decimal(string(line[1:])) {
		return nil, Failure{"protocol"}
	}
	n, err := strconv.ParseUint(string(line[1:]), 10, 64)
	if err != nil {
		return nil, Failure{"protocol"}
	}
	if n > maxInfoBytes {
		return nil, Failure{"oversized"}
	}
	data := make([]byte, int(n)+2)
	if _, err = io.ReadFull(r, data); err != nil {
		clear(data)
		return nil, err
	}
	if data[n] != '\r' || data[n+1] != '\n' {
		clear(data)
		return nil, Failure{"protocol"}
	}
	return data[:n], nil
}

// These are the only command frames available to the collector.
func writeAuth(w io.Writer, secret []byte) error {
	if _, err := io.WriteString(w, "*3\r\n$4\r\nAUTH\r\n$11\r\nftw_monitor\r\n$96\r\n"); err != nil {
		return err
	}
	if _, err := w.Write(secret); err != nil {
		return err
	}
	_, err := io.WriteString(w, "\r\n")
	return err
}

const pingFrame = "*1\r\n$4\r\nPING\r\n"
const infoFrame = "*4\r\n$4\r\nINFO\r\n$6\r\nmemory\r\n$11\r\npersistence\r\n$5\r\nstats\r\n"
