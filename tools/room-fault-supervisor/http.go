package supervisor

import (
	"bytes"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"unicode/utf8"
)

const maxBodyBytes = 4096

func (s *Service) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	writeError := func(code int, errorCode string) {
		writeResponse(w, Response{Role: s.config.Role, ErrorCode: errorCode}, code)
	}
	if r.URL.Path != "/control" {
		writeError(http.StatusNotFound, "not_found")
		return
	}
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		writeError(http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	provided := sha256.Sum256([]byte(r.Header.Get("Authorization")))
	authenticated := subtle.ConstantTimeCompare(provided[:], s.authorization[:]) == 1
	if !authenticated || len(r.Header.Values("Authorization")) != 1 {
		writeError(http.StatusUnauthorized, "unauthorized")
		return
	}
	if r.URL.RawQuery != "" {
		writeError(http.StatusBadRequest, "invalid_request")
		return
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, maxBodyBytes+1))
	if len(body) > maxBodyBytes {
		writeError(http.StatusRequestEntityTooLarge, "body_too_large")
		return
	}
	if err != nil {
		writeError(http.StatusBadRequest, "invalid_request")
		return
	}
	request, err := parseRequest(body)
	if err != nil {
		writeError(http.StatusBadRequest, "invalid_request")
		return
	}
	response, code := s.control(r.Context(), request)
	writeResponse(w, response, code)
}

// Decode keys explicitly: encoding/json struct decoding alone accepts differently
// cased keys and duplicate fields, which this fixed control contract forbids.
func parseRequest(body []byte) (Request, error) {
	var request Request
	invalid := errors.New("invalid_request")
	if !utf8.Valid(body) {
		return request, invalid
	}
	decoder := json.NewDecoder(bytes.NewReader(body))
	opening, err := decoder.Token()
	if err != nil || opening != json.Delim('{') {
		return request, invalid
	}
	seen := make(map[string]bool, 6)
	for decoder.More() {
		key, err := decoder.Token()
		if err != nil {
			return request, invalid
		}
		name, ok := key.(string)
		if !ok || seen[name] {
			return request, invalid
		}
		seen[name] = true
		var value json.RawMessage
		if err := decoder.Decode(&value); err != nil || bytes.Equal(bytes.TrimSpace(value), []byte("null")) {
			return request, invalid
		}
		var target any
		switch name {
		case "fixtureId":
			target = &request.FixtureID
		case "requestId":
			target = &request.RequestID
		case "generation":
			target = &request.Generation
		case "operation":
			target = &request.Operation
		case "deadlineUnixMillis":
			target = &request.DeadlineUnixMillis
		case "leaseMillis":
			target = &request.LeaseMillis
		default:
			return request, invalid
		}
		if err := json.Unmarshal(value, target); err != nil {
			return request, invalid
		}
	}
	closing, err := decoder.Token()
	if err != nil || closing != json.Delim('}') || len(seen) != 6 {
		return request, invalid
	}
	if _, err := decoder.Token(); err != io.EOF {
		return request, invalid
	}
	return request, nil
}

func writeResponse(w http.ResponseWriter, response Response, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(response)
}
