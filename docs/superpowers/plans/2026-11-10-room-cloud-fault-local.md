# Room Cloud Fault Harness Local Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Implement and locally verify the approved test-only process supervisor and fault adapter while preserving the accepted application JAR and full acceptance gates.

**Architecture:** A small Linux Go supervisor provides authenticated, fixture/generation-bound fixed operations and bounded fault leases. Linux process and loopback Toxiproxy drivers implement the same contract. Kotlin acceptance helpers keep the Docker adapter and add the supervisor adapter; guarded local staging-profile launches exercise the new adapter without Railway credentials.

**Tech Stack:** Go 1.27.3 Linux compiler image `golang@sha256:e482b31b5d30c31f9e495f4b8cbdf41429b2d74955dfbcd9c0a904483d738390`, pinned Linux runtime images, PowerShell 7, existing Kotlin/Ktor acceptance classpath and local Docker.

**Spec:** [Approved proposal](../../room-ownership-cloud-fault-proposal.md). The user explicitly approved local implementation/test after reviewing commit `23e7939`.

## Global Constraints

- No cloud resources, provider mutations, SSH keys, CI dispatch, issue messages, merge or push.
- Work in the existing linked `codex/room-ownership-fix` worktree.
- Server JAR remains SHA-256 `8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818`; changes belong to test tools, test sources and fixture images only.
- Backend pool 20; twenty rooms, forty distinct accounts, 600 seconds, 60,000 ACKs, three owner outages, recovery at most 25 seconds, all eight fault cases.
- Supervisors/children use the same unprivileged role account; no host PID/network privilege or extra capabilities.
- PostgreSQL pause proves all relevant descendants stopped, including existing SQL sessions across `setsid()` boundaries; no store kill/redeploy equivalence claim.
- Fault lease at most 60 seconds, automatic restoration with permanent failed-case status; overall fixture deadline at most two hours.
- No-op/unverified injections fail; recovery durations include control/network acknowledgement.
- Exact role/fixture/generation/request binding, bounded input/history, fixed operation set, private control endpoint and immutable targets.
- Preserve old Docker semantics and strict one-backend Railway baseline behavior; cloud process-pause store cases have explicit distinct names.
- Independently verify every owned fixture's container/network/volume/image cleanup. Keep sensitive values out of provider-compatible receipts/logs.

## File Map and Shared Contract

`tools/room-fault-supervisor/` is a separate Go module. Its package `supervisor` contains `protocol.go`, `http.go`, `process_linux.go`, `proxy.go`, tests beside each, and `cmd/supervisor/main.go`. `deploy/room-ownership/supervised/` holds frozen-toolchain builds, unprivileged backend/store/proxy image recipes and control configuration. `scripts/room-ownership-supervised-local.ps1` owns local fixtures and evidence. Kotlin changes stay under `server/src/test/.../ownership/`.

The control HTTP request uses exactly `fixtureId`, `requestId`, `generation`, `operation`, `deadlineUnixMillis`, `leaseMillis`. The endpoint is `POST /control` with `Authorization: Bearer <64-lowercase-hex per-run secret>`. Bodies are limited to 4 KiB; unknown fields and extra JSON values fail. Roles are `a`, `b`, `pg`, `redis`, `proxy`.

Shared Go types and entry points:

```go
type Request struct {
    FixtureID string `json:"fixtureId"`
    RequestID string `json:"requestId"`
    Generation uint64 `json:"generation"`
    Operation string `json:"operation"`
    DeadlineUnixMillis int64 `json:"deadlineUnixMillis"`
    LeaseMillis int64 `json:"leaseMillis"`
}
type Status struct {
    Generation uint64 `json:"generation"`
    State string `json:"state"` // running, paused, stopped, blocked-a, blocked-b
    Verified bool `json:"verified"`
}
type Driver interface {
    Status(context.Context) (Status, error)
    Apply(context.Context, string, uint64) (Status, error)
    Restore(context.Context) error
    Close(context.Context) error
}
// Config binds FixtureID, Role, Token, Deadline and the clock for deterministic tests.
func NewService(Config, Driver) (*Service, error)
func (*Service) ServeHTTP(http.ResponseWriter, *http.Request)
func (*Service) Tick(context.Context) error
func (*Service) Close(context.Context) error
```

Responses contain fixed fields `requestId`, `role`, `generation`, `state`, `verified`, `failed`, `errorCode`. `status` alone may request generation zero; all mutations must match the driver's current generation. Request replay uses an exact canonical fingerprint; conflicts fail without another driver call. Maximum retained request identities is 256, with exhaustion failing closed instead of evicting old replay protection. Backend operations: status/pause/resume/kill/start. Store operations: status/pause/resume. Proxy operations: status/block-a/block-b/unblock-a/unblock-b. Only one active fault is permitted per supervisor. A case whose fault lease expires remains failed even after restoration.

### Task 1: Authenticated bounded control protocol

**Files:** create Go module, `protocol.go`, `http.go`, `protocol_test.go`, `http_test.go`, `scripts/tests/RoomFaultSupervisor.Tests.ps1`.

**Consumes:** shared Driver interface above, fake drivers in tests. **Produces:** executable HTTP/state contract for real drivers and Kotlin clients.

- [ ] Add the Go module and a failing HTTP test using `httptest.NewRecorder()` and a recording fake driver. Assert a bad token and wrong fixture never call `Apply`.
- [ ] Run the pinned compiler container with the module mounted at `/src`, `GOTOOLCHAIN=local`, `--network none`, and `go test ./...`; confirm the test is red for missing validation.
- [ ] Implement exact-field parsing, constant-time authentication, role allowlists, fixture/deadline/generation binding and serialized operations. Keep errors fixed and free of raw driver exception text.
- [ ] Add behavioral tests for duplicate/conflicting requests, wrong generation, two active faults, request-table exhaustion, failed verification, HTTP size/extra-fields, lease expiry restoration and permanent failed status.
- [ ] Run the complete protocol tests and independent task review; commit the tested contract.

Concrete regression signal:

```go
before := driver.calls
reply := send(t, handler, "wrong-token", validRequest)
if reply.Code != http.StatusUnauthorized || driver.calls != before {
    t.Fatal("unauthenticated request reached process control")
}
```

### Task 2: Linux process identity control and real store feasibility

**Files:** create `process_linux.go`, `process_linux_test.go`, `cmd/supervisor/main.go`, `deploy/room-ownership/supervised/{supervisor,backend,postgres,redis}.Dockerfile`, local capability runner scripts.

**Consumes:** Task 1 Driver/Service. **Produces:** verified process Driver and supervised fixture images, including actual unprivileged UID/GID evidence.

- [ ] Add failing Linux tests with a subprocess tree whose grandchild calls `setsid`, keeps a live output stream and can fork during discovery. Pause must freeze all descendants; resume must restore the same live generation.
- [ ] Add a pinned `golang.org/x/sys` dependency only if required for pidfd calls; retain `go.sum` and prove offline builds from a prefilled workspace cache. Never fall back to unsafe recycled numeric PID signalling.
- [ ] Implement pidfd identities plus `/proc` start-time/ancestry checks, root-first stop and stable full-tree verification, orphan reaping, cancellation restoration and exact generation binding. No unrelated container process can become a target.
- [ ] Add kill/start for backend roles only. Test a replayed kill cannot affect a later generation. Test parent exit, fault-lease expiry and overall deadline without a surviving managed process.
- [ ] Build image variants under the original unprivileged role accounts. Preserve the backend distribution bytes and record all image/compiler digests. No runtime root or capability escalation.
- [ ] Against pinned PostgreSQL 18, create a marker row and an already-open SQL connection. Begin a write during verified pause and require no completion; after resume require the same data and correct single write. Test new connections, worker exit/fork races and cancellation midway through pause. Redis key/connection checks prove equivalent pause preservation.
- [ ] Run Linux unit/race tests, real image capability tests, zero-resource cleanup checks and independent review. Commit only after store feasibility is proven.

Behavioral capability assertion:

```go
select {
case <-queryCompleted:
    t.Fatal("established PostgreSQL session executed during verified pause")
case <-time.After(250 * time.Millisecond):
}
```

### Task 3: Leased immutable proxy control

**Files:** create `proxy.go`, `proxy_test.go`, `deploy/room-ownership/supervised/proxy.Dockerfile` and fixed dual-listener template.

**Consumes:** Task 1 Service and Task 2 supervisor entrypoint. **Produces:** proxy Driver using loopback-only Toxiproxy API and exact backend-A/B listener names.

- [ ] Write failing local listener tests: blocking A must stop A's existing/fresh traffic while B remains usable; no-op HTTP success cannot count as verification.
- [ ] Implement only block-a/block-b/unblock-a/unblock-b against immutable loopback targets and expected upstreams; reject redirects, proxy creation and upstream changes.
- [ ] Verify bounded fault lease automatically restores the listener and marks expiry failed if the runner disappears. No public configuration API is exposed.
- [ ] Run unit/race and digest-pinned Toxiproxy integration tests, teardown checks and independent review; commit.

### Task 4: Kotlin control adapter and guarded staging profiles

**Files:** create `RoomFaultControl.kt`, `SupervisorRoomFaultControl.kt`, their tests, `RailwayRoomRehearsal.kt` and its receipt/settings tests; modify `RoomOwnershipAcceptanceMain.kt`, `RoomOwnershipFaultAcceptance.kt`, test-only staging settings and receipt readers where the new profile requires it.

**Consumes:** Tasks 1–3 HTTP contract. **Produces:** a test-only `RoomFaultControl` adapter seam; existing Docker behavior remains default.

- [ ] Write failing contract tests proving the existing Docker case routing and supervisor operation mapping; unknown target, generation, expired/failed control response or unverified fault fails acceptance.
- [ ] Extract lifecycle operations behind the adapter and route current fault helpers through it. The supervisor adapter obtains authenticated status, binds mutation generation/request IDs and checks proof; never pass management credentials or evaluate arbitrary commands.
- [ ] Add a separate exact-project/fixture private-DNS `two-backend`/`fault` profile; retain strict single-backend baseline checks. Forty accounts, pool 20 on both backends and fixed A/B/PG/Redis/proxy addresses are mandatory.
- [ ] Reuse all existing selection, epoch, replay, quota and economy assertions. Export the distinct cloud store-pause case names and injection mechanism while preserving the exact eight required cases.
- [ ] Add checksum-bounded load/failure/latency/fault export tests. Sensitive IDs, tokens, raw SQL/errors and incomplete/mixed chunks fail the reader. Single-backend, short, missing-fault or wrong-pool receipts cannot pass the two-backend gate.
- [ ] Run all activity/ownership tests and existing/new receipt-reader tests; verify the production server JAR hash is unchanged; review and commit.

### Task 5: End-to-end local verification and frozen review package

**Files:** create `compose.supervised.yaml`, `scripts/room-ownership-supervised-local.ps1`, fixture isolation tests; update the approved proposal status, handoff and reproducibility docs.

**Consumes:** Tasks 1–4 verified images and test entrypoint. **Produces:** exact manifests and accepted local supervised fault/full-load evidence; no cloud launch.

- [ ] Add failing rendered-Compose tests for six private services, original unprivileged users, loopback/private-only controls, exact proxy paths and unchanged application JAR/pool/resource settings.
- [ ] Implement host fixture ownership, strict local Docker endpoint, secret redaction, deadline/lease setup, image manifest checks and cleanup in `finally`. Record only bounded safe evidence.
- [ ] Run capability checks before game traffic. Reject an unavailable capability instead of skipping or renaming a failed case into success.
- [ ] Run all eight supervised fault cases. Independently verify receipt byte/hash equality, actual injection proof, all recovery samples and zero owned resources.
- [ ] Run full supervised two-backend load: exactly 60,000 ACKs over the 600-second offer and three real owner pauses. Independently verify runtime pool 20 on both nodes, all economy assertions, recovery <=25 seconds, complete exports and cleanup.
- [ ] Freeze all supervisor/wrapper/store/proxy/runner manifests, measure overhead and review the combined change. Record local acceptance and remaining cloud gates. Prepare a separate concrete cloud scope for later approval; do not create cloud resources.

## Execution Tracking

- User authorization: local implementation and tests only, received 10 November 2026.
- Execute with fresh task agents and focused reviews under the existing isolated worktree; sequentially respect interface dependencies.
- After each independently verified task, report its result and the next step to the user. Do not ask again to continue routine approved local work.
- A failed Linux capability is an engineering failure to diagnose and fix within the approved design. Do not silently weaken the proof, broaden permissions, substitute store redeploy, or claim a cloud pass.
