// Package supervisor implements the bounded control protocol for local test fixtures.
package supervisor

import (
	"context"
	"crypto/sha256"
	"errors"
	"math"
	"net/http"
	"sync/atomic"
	"time"
)

const (
	maxRequests        = 256
	maxLease           = 60 * time.Second
	maxFixtureDuration = 2 * time.Hour
	cleanupTimeout     = 5 * time.Second
)

type Request struct {
	FixtureID          string `json:"fixtureId"`
	RequestID          string `json:"requestId"`
	Generation         uint64 `json:"generation"`
	Operation          string `json:"operation"`
	DeadlineUnixMillis int64  `json:"deadlineUnixMillis"`
	LeaseMillis        int64  `json:"leaseMillis"`
}
type Status struct {
	Generation uint64 `json:"generation"`
	State      string `json:"state"`
	Verified   bool   `json:"verified"`
}

// Driver implementations must honor context cancellation, verify real effects,
// and keep their immutable process/proxy targets private to the fixture.
// Close must be idempotent and retryable after errors or cancellation: a nil
// result means all owned resources were cleaned up, while an error permits the
// service to call Close again to finish partially completed cleanup.
type Driver interface {
	Status(context.Context) (Status, error)
	Apply(context.Context, string, uint64) (Status, error)
	Restore(context.Context) error
	Close(context.Context) error
}
type Config struct {
	FixtureID string
	Role      string
	Token     string
	Deadline  time.Time
	Clock     func() time.Time
}
type Response struct {
	RequestID  string `json:"requestId"`
	Role       string `json:"role"`
	Generation uint64 `json:"generation"`
	State      string `json:"state"`
	Verified   bool   `json:"verified"`
	Failed     bool   `json:"failed"`
	ErrorCode  string `json:"errorCode"`
}
type cachedRequest struct {
	request  Request // Exact typed fields form the canonical fingerprint; JSON order is irrelevant.
	response Response
	code     int
}

// Service serializes all Driver access, including expiry restoration and Close.
// The owner must call Tick regularly even when no control requests arrive.
type Service struct {
	config        Config
	driver        Driver
	authorization [32]byte
	gate          chan struct{}
	requests      map[string]cachedRequest
	last          Status
	active        string
	leaseDeadline time.Time
	needsRestore  bool
	failure       string      // Latched permanently; successful restoration never clears this.
	failed        atomic.Bool // Publishes the failure flag to requests rejected before gate acquisition.
	lifecycle     context.Context
	shutdown      context.CancelFunc
	driverClosed  bool
	closed        bool
	closeError    error
}

func NewService(c Config, d Driver) (*Service, error) {
	if c.Clock == nil {
		c.Clock = time.Now
	}
	now := c.Clock()
	if d == nil || !validIdentity(c.FixtureID) || !validRole(c.Role) || !validToken(c.Token) ||
		!c.Deadline.After(now) || c.Deadline.Sub(now) > maxFixtureDuration {
		return nil, errors.New("invalid_config")
	}
	s := &Service{config: c, driver: d, authorization: sha256.Sum256([]byte("Bearer " + c.Token)), gate: make(chan struct{}, 1), requests: make(map[string]cachedRequest)}
	s.lifecycle, s.shutdown = context.WithCancel(context.Background())
	s.gate <- struct{}{}
	return s, nil
}

func validIdentity(value string) bool {
	if len(value) == 0 || len(value) > 128 {
		return false
	}
	for i := 0; i < len(value); i++ {
		b := value[i]
		if (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') {
			continue
		}
		if i > 0 && (b == '-' || b == '_' || b == '.') {
			continue
		}
		return false
	}
	return true
}

func validToken(token string) bool {
	if len(token) != 64 {
		return false
	}
	for i := range token {
		if !(token[i] >= '0' && token[i] <= '9' || token[i] >= 'a' && token[i] <= 'f') {
			return false
		}
	}
	return true
}

func validRole(role string) bool {
	switch role {
	case "a", "b", "pg", "redis", "proxy":
		return true
	}
	return false
}

func allowed(role, operation string) bool {
	if operation == "status" {
		return true
	}
	switch role {
	case "a", "b":
		return operation == "pause" || operation == "resume" || operation == "kill" || operation == "start"
	case "pg", "redis":
		return operation == "pause" || operation == "resume"
	case "proxy":
		return operation == "block-a" || operation == "block-b" || operation == "unblock-a" || operation == "unblock-b"
	}
	return false
}

func fault(operation string) bool {
	return operation == "pause" || operation == "kill" || operation == "block-a" || operation == "block-b"
}

func expectedState(operation string) string {
	switch operation {
	case "pause":
		return "paused"
	case "kill":
		return "stopped"
	case "block-a":
		return "blocked-a"
	case "block-b":
		return "blocked-b"
	default:
		return "running"
	}
}

func recoveryFor(operation string) string {
	switch operation {
	case "pause":
		return "resume"
	case "kill":
		return "start"
	case "block-a":
		return "unblock-a"
	case "block-b":
		return "unblock-b"
	}
	return ""
}

func (s *Service) validStatus(status Status) bool {
	if !status.Verified || status.Generation == 0 {
		return false
	}
	switch status.State {
	case "running":
		return true
	case "paused":
		return s.config.Role != "proxy"
	case "stopped":
		return s.config.Role == "a" || s.config.Role == "b"
	case "blocked-a", "blocked-b":
		return s.config.Role == "proxy"
	}
	return false
}

func (s *Service) acquire(ctx context.Context) bool {
	if ctx.Err() != nil {
		return false
	}
	select {
	case <-ctx.Done():
		return false
	case <-s.gate:
		if ctx.Err() != nil {
			s.release()
			return false
		}
		return true
	}
}
func (s *Service) release() { s.gate <- struct{}{} }

func (s *Service) reply(r Request, errorCode string) Response {
	return Response{RequestID: r.RequestID, Role: s.config.Role, Generation: s.last.Generation, State: s.last.State, Verified: s.last.Verified, Failed: s.failure != "", ErrorCode: errorCode}
}

func (s *Service) fail(code string) {
	if s.failure == "" {
		s.failure = code
		s.failed.Store(true)
	}
}

// operationContext bounds cooperative drivers by request, fixture and active lease.
func (s *Service) operationContext(parent context.Context, deadline time.Time) (context.Context, context.CancelFunc) {
	if s.config.Deadline.Before(deadline) {
		deadline = s.config.Deadline
	}
	if s.needsRestore && !s.leaseDeadline.IsZero() && s.leaseDeadline.Before(deadline) {
		deadline = s.leaseDeadline
	}
	return context.WithTimeout(parent, deadline.Sub(s.config.Clock()))
}

func (s *Service) control(parent context.Context, r Request) (Response, int) {
	blank := Response{Role: s.config.Role}
	if validIdentity(r.RequestID) {
		blank.RequestID = r.RequestID
	}
	if s.lifecycle.Err() != nil {
		blank.Failed = s.failed.Load()
		blank.ErrorCode = "closed"
		return blank, http.StatusServiceUnavailable
	}
	if r.FixtureID != s.config.FixtureID {
		blank.ErrorCode = "fixture_mismatch"
		return blank, http.StatusForbidden
	}
	if !validIdentity(r.RequestID) || !allowed(s.config.Role, r.Operation) || r.DeadlineUnixMillis > s.config.Deadline.UnixMilli() ||
		(fault(r.Operation) && (r.LeaseMillis <= 0 || r.LeaseMillis > maxLease.Milliseconds())) || (!fault(r.Operation) && r.LeaseMillis != 0) {
		blank.ErrorCode = "invalid_request"
		return blank, http.StatusBadRequest
	}
	deadline := time.UnixMilli(r.DeadlineUnixMillis)
	if !deadline.After(s.config.Clock()) || parent.Err() != nil {
		blank.ErrorCode = "request_expired"
		return blank, http.StatusRequestTimeout
	}
	waitCtx, cancel := context.WithTimeout(parent, deadline.Sub(s.config.Clock()))
	defer cancel()
	stopShutdown := context.AfterFunc(s.lifecycle, cancel)
	defer stopShutdown()
	if s.lifecycle.Err() != nil {
		cancel()
	}
	if !s.acquire(waitCtx) {
		if s.lifecycle.Err() != nil {
			blank.Failed = s.failed.Load()
			blank.ErrorCode = "closed"
			return blank, http.StatusServiceUnavailable
		}
		blank.ErrorCode = "request_expired"
		return blank, http.StatusRequestTimeout
	}
	defer s.release()
	if s.lifecycle.Err() != nil {
		return s.reply(r, "closed"), http.StatusServiceUnavailable
	}
	s.expireLocked()
	if prior, exists := s.requests[r.RequestID]; exists {
		if prior.request != r {
			return s.reply(r, "request_conflict"), http.StatusConflict
		}
		if s.failure != "" {
			return s.reply(r, s.failure), http.StatusServiceUnavailable
		}
		return prior.response, prior.code
	}
	if s.failure != "" {
		return s.reply(r, s.failure), http.StatusServiceUnavailable
	}
	if len(s.requests) >= maxRequests {
		return s.reply(r, "request_limit"), http.StatusServiceUnavailable
	}
	result, code := s.executeLocked(waitCtx, r, deadline)
	s.requests[r.RequestID] = cachedRequest{request: r, response: result, code: code}
	return result, code
}

func (s *Service) executeLocked(parent context.Context, r Request, deadline time.Time) (Response, int) {
	ctx, cancel := s.operationContext(parent, deadline)
	defer cancel()
	if ctx.Err() != nil {
		return s.reply(r, "request_expired"), http.StatusRequestTimeout
	}
	before, err := s.driver.Status(ctx)
	s.expireLocked()
	if s.failure != "" {
		return s.reply(r, s.failure), http.StatusServiceUnavailable
	}
	if ctx.Err() != nil || !deadline.After(s.config.Clock()) {
		return s.reply(r, "request_expired"), http.StatusRequestTimeout
	}
	if err != nil {
		s.last.Verified = false
		return s.reply(r, "driver_error"), http.StatusServiceUnavailable
	}
	if !s.validStatus(before) {
		s.last.Verified = false
		return s.reply(r, "verification_failed"), http.StatusServiceUnavailable
	}
	s.last = before
	if (r.Operation != "status" || r.Generation != 0) && (r.Generation == 0 || r.Generation != before.Generation) {
		return s.reply(r, "generation_mismatch"), http.StatusConflict
	}
	if r.Operation == "status" {
		return s.reply(r, ""), http.StatusOK
	}
	if fault(r.Operation) {
		if s.active != "" || before.State != "running" {
			return s.reply(r, "fault_conflict"), http.StatusConflict
		}
		s.active = r.Operation
		s.leaseDeadline = s.config.Clock().Add(time.Duration(r.LeaseMillis) * time.Millisecond)
		if s.config.Deadline.Before(s.leaseDeadline) {
			s.leaseDeadline = s.config.Deadline
		}
		s.needsRestore = true // Apply may partially mutate before returning an error.
	} else if s.active == "" || recoveryFor(s.active) != r.Operation || before.State != expectedState(s.active) {
		return s.reply(r, "fault_conflict"), http.StatusConflict
	}
	wantGeneration := before.Generation
	if r.Operation == "start" {
		if wantGeneration == math.MaxUint64 {
			return s.reply(r, "generation_mismatch"), http.StatusConflict
		}
		wantGeneration++
	}
	// A fault's lease starts before Apply, so control acknowledgement consumes lease time.
	applyCtx, applyCancel := s.operationContext(ctx, deadline)
	defer applyCancel()
	s.last.Verified = false // The prior observation cannot verify a partially applied mutation.
	after, err := s.driver.Apply(applyCtx, r.Operation, r.Generation)
	if err != nil || applyCtx.Err() != nil {
		s.fail("driver_error")
		s.restoreLocked()
		return s.reply(r, s.failure), http.StatusServiceUnavailable
	}
	if !s.validStatus(after) || after.State != expectedState(r.Operation) || after.Generation != wantGeneration {
		s.fail("verification_failed")
		s.restoreLocked()
		return s.reply(r, s.failure), http.StatusServiceUnavailable
	}
	observed, err := s.driver.Status(applyCtx)
	s.observeExpiryLocked()
	if s.failure == "" && !deadline.After(s.config.Clock()) {
		s.fail("request_expired")
	}
	if s.failure != "" {
		s.restoreLocked()
		return s.reply(r, s.failure), http.StatusServiceUnavailable
	}
	if err != nil || !s.validStatus(observed) || observed != after || applyCtx.Err() != nil {
		s.fail("verification_failed")
		s.restoreLocked()
		return s.reply(r, s.failure), http.StatusServiceUnavailable
	}
	s.last = observed
	// Expiry wins even if a driver acknowledges just as the deterministic clock advances.
	s.expireLocked()
	if s.failure != "" {
		return s.reply(r, s.failure), http.StatusServiceUnavailable
	}
	if !fault(r.Operation) {
		s.active = ""
		s.needsRestore = false
		s.leaseDeadline = time.Time{}
	}
	return s.reply(r, ""), http.StatusOK
}

func (s *Service) restoreLocked() {
	if !s.needsRestore {
		return
	}
	s.last.Verified = false
	// Independent of the expired/canceled request: restoration must still run.
	ctx, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
	defer cancel()
	err := s.driver.Restore(ctx)
	s.observeExpiryLocked()
	if err != nil || ctx.Err() != nil {
		s.fail("restore_failed")
		return
	}
	status, err := s.driver.Status(ctx)
	// Keep the fault/lease live through restoration acknowledgement and verification.
	s.observeExpiryLocked()
	if err != nil || !s.validStatus(status) || status.State != "running" || ctx.Err() != nil {
		s.fail("restore_failed")
		return
	}
	s.last = status
	s.needsRestore = false
	s.active = ""
	s.leaseDeadline = time.Time{}
}

func (s *Service) observeExpiryLocked() {
	now := s.config.Clock()
	if !now.Before(s.config.Deadline) {
		s.fail("fixture_expired")
	} else if s.needsRestore && !s.leaseDeadline.IsZero() && !now.Before(s.leaseDeadline) {
		s.fail("lease_expired")
	}
}

func (s *Service) expireLocked() {
	s.observeExpiryLocked()
	if s.failure != "" {
		s.restoreLocked()
	}
}

func (s *Service) Tick(parent context.Context) error {
	ctx, cancel := context.WithTimeout(parent, cleanupTimeout)
	defer cancel()
	if !s.acquire(ctx) {
		return errors.New("control_timeout")
	}
	defer s.release()
	if s.closed {
		return s.closeError
	}
	s.expireLocked()
	if s.failure != "" {
		return errors.New(s.failure)
	}
	return nil
}

func (s *Service) Close(context.Context) error {
	// Cancel controls and reject new work before waiting behind any current driver call.
	// CancelFunc and Context.Err are safe for concurrent Close/control callers.
	s.shutdown()
	// A canceled shutdown caller must not skip restoration. Every cleanup call is bounded.
	ctx, cancel := context.WithTimeout(context.Background(), cleanupTimeout)
	defer cancel()
	if !s.acquire(ctx) {
		return errors.New("control_timeout")
	}
	defer s.release()
	if s.closed {
		return s.closeError
	}
	s.closeError = nil
	s.expireLocked()
	if s.failure == "" {
		s.restoreLocked()
	}
	if !s.driverClosed {
		closeCtx, closeCancel := context.WithTimeout(context.Background(), cleanupTimeout)
		err := s.driver.Close(closeCtx)
		if err != nil || closeCtx.Err() != nil {
			s.closeError = errors.New("close_failed")
		} else {
			s.driverClosed = true
		}
		closeCancel()
	}
	s.closed = s.driverClosed && !s.needsRestore
	if s.closeError != nil {
		return s.closeError
	}
	if s.needsRestore {
		s.closeError = errors.New("restore_failed")
	} else if s.failure != "" {
		s.closeError = errors.New(s.failure)
	}
	return s.closeError
}
