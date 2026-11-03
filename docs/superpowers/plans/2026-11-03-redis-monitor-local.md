# Redis Monitor Local Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver and locally qualify a separate read-only Redis AOF/eviction collector and opt-in monitoring configuration.

**Architecture:** A Go standard-library collector polls the fixed `ftw_monitor` AUTH/PING/INFO exchange and publishes immutable bounded-age snapshots. An independent local Redis/Prometheus/Alertmanager/Grafana fixture proves the unchanged ACL, metrics, alerts and dashboard. Existing backend/default monitoring and production resources stay unchanged.

**Tech Stack:** Go 1.27.3 portable official toolchain, standard library only; pinned Redis bootstrap already in this repository; existing monitoring versions from `compose.production.yaml`.

**Spec:** [Owner-approved concrete design](../specs/2026-11-03-redis-monitor-local-design.md), approved in chat before this plan.

## Global Constraints

- Work only on `codex/redis-monitor-local` in the existing isolated worktree. No production secrets, SSH/Railway operations, app/DB changes, push, merge, CI, issue publication or scheduler.
- Username `ftw_monitor`; only AUTH, PING, `INFO memory persistence stats`; one target, one connection/attempt, three-second absolute deadline including DNS/TLS/I/O, no retry; poll every 15 seconds without overlap/catch-up. No broadening Redis ACL.
- Password only from a bounded regular file, 96 lowercase hexadecimal characters with at most one line ending; maximum read 128 bytes. No raw config/error/INFO/auth/frame/request logging or endpoint/identifier labels.
- Preserve every metric/type/conditional field and error reason in the spec. Publish atomically; omit Redis-domain values on latest failure or age greater than 45 seconds, using monotonic age. Never turn absent data into zero. Redis counters remain source counters.
- HTTP defaults to loopback port 19121; container binding is explicit and private. At most 16 active connections/handlers, 8-KiB headers, two-second header/read/write timeout, ten-second idle timeout, no body. Shutdown within five seconds; no automatic repair.
- Separate opt-in files/config/rules; original Compose and backend monitoring do not acquire required new secrets/targets. Local fixture runs no game backend or PostgreSQL. Existing Alertmanager remains dashboard-only.
- Tests use synthetic credentials and uniquely owned local resources. Delete/move only validated owned workspace paths and matching Docker labels/IDs; no prune/reset/WSL shutdown. Mock/skip is not real Redis, promtool or rendered dashboard acceptance. Preserve incomplete evidence if local runtime fails.
- Collector cap 0.1 CPU/64 MiB in local fixture. Pin official toolchain/image checksums/digests before execution; no global installations, unverified binaries or `latest` acceptance artifacts.
- UI work follows `.agents/skills/ui-ux-pro-max/SKILL.md`, existing Grafana conventions and desktop/narrow screenshot checks; missing/stale samples explicitly unavailable.

## Task 1: Bounded read-only collection library

**Files:** Create `tools/redis-monitor/go.mod`, `internal/monitor/config.go`, `resp.go`, `collect.go`, `sample.go`, and matching `_test.go` files. Paths after `go.mod` are relative to `tools/redis-monitor/`.

**Interfaces:**

```go
// package monitor; module github.com/ThaiXuanHien/FastToWin/tools/redis-monitor
type Config struct { Address, PasswordFile, Listen string; TLS bool }
func LoadConfig(getenv func(string) string) (Config, error)
type Sample struct { Values map[string]uint64 } // approved metric suffixes only
type Reader interface { Read(context.Context) (Sample, error) }
func NewReader(Config) (Reader, error) // reads/validates secret once, private storage
type Failure struct { Reason string } // only seven approved values; fixed Error text
```

Consumes: approved spec, portable Go toolchain supplied by controller. Produces: validated Config and Reader for Task 2. No arbitrary command API; transport helpers remain private.

- [ ] **Step 1 — RED configuration and protocol contracts.** Create module and test files first. Observe failures because entry points are absent. Use isolated temporary secret files, table cases for address/port/TLS/file validation, and a fake loopback RESP server recording exact frames. A representative contract:

```go
func TestAOFDisabledOmitsConditionalMetrics(t *testing.T) {
    peer := startInfoPeer(t, "used_memory:1024\r\nmaxmemory:67108864\r\nevicted_keys:0\r\naof_enabled:0\r\n")
    reader, err := NewReader(peer.Config(t))
    if err != nil { t.Fatal(err) }
    got, err := reader.Read(context.Background())
    if err != nil { t.Fatal(err) }
    if got.Values["aof_enabled"] != 0 { t.Fatal(got.Values) }
    if _, exists := got.Values["aof_current_size_bytes"]; exists { t.Fatal("fabricated AOF size") }
    peer.RequireCommands(t, "AUTH", "PING", "INFO memory persistence stats")
}
```

Implement `startInfoPeer`, its Config/RequireCommands helpers in test code: synthetic secret, close listener in cleanup, verify exact RESP arguments privately, never print the AUTH bytes on failure. Additional RED cases cover fragmenting/error/truncated/oversized replies, unknown/duplicate/missing/negative/overflow/NaN fields and AOF write/rewrite `err` as valid fault gauges.

- [ ] **Step 2 — Implement configuration and fixed transport.** `LoadConfig` uses only the four named spec variables and defaults. Validate host/port with `net.SplitHostPort`, reject whitespace/control/URI credentials/path/query and ports outside 1–65535; support bracketed IPv6. `NewReader` validates a regular secret file before bounded reading. The reader uses RESP2 arrays of bulk strings; reads only bounded simple/bulk replies, with a single attempt deadline and cancellation closure. INFO parser maps only spec fields to suffixes; retain no unknown strings/raw response after parsing. Errors are `Failure` with fixed reason only. TLS must verify certificates and cannot downgrade.

- [ ] **Step 3 — GREEN transport/validation.** Run focused tests while iterating, then `go test ./...` and `go vet ./...` once for this task. Add loopback tests that demonstrate actual absolute timeout under trickled bytes and cancellation, bad/untrusted TLS, successful verified TLS using an injected test-only dial/TLS boundary, no command after auth failure, and marker-free errors. Private injection helpers must not create production bypass/config options. Record actual RED/GREEN output; do not assert every later case was preimplementation RED if it was not.

- [ ] **Step 4 — Commit and report.** Commit only module/client files. Report interfaces, exact test commands/output, toolchain identity, any incomplete real-network/TLS evidence and file list. Controller reviews the task diff for spec and quality before Task 2.

## Task 2: Poller, snapshots, metrics HTTP and executable

**Files:** Create `tools/redis-monitor/internal/monitor/store.go`, `poll.go`, `metrics.go`, `http.go`, matching tests, and `tools/redis-monitor/cmd/redis-monitor/main.go` with executable-level tests where appropriate. Modify Task 1 types only for a demonstrated integration requirement.

**Interfaces:** Consumes `Config`, `Reader`, `Sample`, `Failure`. Produces the native collector executable and stable metrics/HTTP interface for Task 3.

```go
type Clock struct {
    WallNow func() time.Time
    MonotonicNow func() time.Duration
}
type Store struct { /* private synchronized state */ }
func NewStore(Clock) *Store
func (s *Store) Record(Sample, error, time.Duration)
func (s *Store) Metrics() []byte // owned immutable result, one consistent view
func (s *Store) Ready() bool
func Run(context.Context, Config, Reader, io.Writer) error
```

No public clock/config endpoint. Real `MonotonicNow` uses `time.Since(processStart)`; tests inject independently advancing wall and monotonic clocks. `Run` owns the poller/listener, graceful stop and bounded connection accounting. Logs accept stable event/reason codes, never raw returned errors.

- [ ] **Step 1 — RED store/error/staleness tests.** Exercise through Record/Metrics/Ready, using valid Sample helpers from Task 1. Exact boundary contract:

```go
func TestSnapshotAgeUsesMonotonicClock(t *testing.T) {
    clock := newFakeClock()
    store := NewStore(clock.Clock())
    store.Record(validSample(), nil, time.Millisecond)
    clock.AdvanceMono(45*time.Second)
    clock.MoveWall(-24*time.Hour)
    if !store.Ready() { t.Fatal("45-second boundary is still fresh") }
    clock.AdvanceMono(time.Nanosecond)
    if store.Ready() { t.Fatal("stale snapshot accepted") }
    body := string(store.Metrics())
    if !strings.Contains(body, "fasttowin_redis_monitor_up 0") { t.Fatal(body) }
    if strings.Contains(body, "fasttowin_redis_monitor_memory_used_bytes ") { t.Fatal("stale domain sample") }
}
```

Also test immediate omission after a failed attempt, never-succeeded diagnostics, subsequent recovery, map mutation isolation, source counter reset, exact finite HELP/TYPE output and all seven error reasons. Failure body must not contain a distinct injected secret marker.

- [ ] **Step 2 — Implement synchronized snapshots/exposition.** Copy selected values at ownership boundaries. Keep last-success timestamp only for diagnostics; latest failure invalidates current domain values. Metrics emit deterministic suffixes/types with bounded labels, integer formatting and no raw/string data. Domain error states remain present while collection is successful. Readiness and own-up derive from the same state/age decision.

- [ ] **Step 3 — RED/GREEN poller and HTTP behavior.** Implement `Run` with immediate single poll, then nonoverlapping monotonic cadence; skip elapsed ticks, do not let a request trigger collection. Fake Reader/clock tests exercise slow collection/no overlap/cancellation. Real loopback HTTP tests cover GET endpoints, 404/405, request bodies, read/header/idle deadlines, concurrent connections at cap, snapshot consistency and bounded shutdown. Verify stdout/stderr never contains target/secret/raw error or request data. Supply deterministic timing seams instead of waiting real 45 seconds per unit test.

- [ ] **Step 4 — Build/executable acceptance and review.** Run full module tests and vet once after focused checks. Build Windows executable and `CGO_ENABLED=0 GOOS=linux GOARCH=amd64` binary using the verified portable toolchain. Race tests run with a verified available supported C toolchain/container; if unavailable, record the gap and do not represent plain tests as race verification. Check invalid configuration exits nonzero with a fixed safe message, and OS termination produces a clean stop. Commit code and report exact behavior/evidence for task review.

## Task 3: Local packaging, rules, dashboard and actual acceptance

**Files:** Create `deploy/monitoring/redis-monitor/Dockerfile`, `compose.local.yaml`, `prometheus.local.yml`, `prometheus.integration.yml`, `redis-alerts.yml`, `redis-alerts.test.yml`, local Grafana provisioning files as needed; create `deploy/monitoring/grafana/dashboards/fasttowin-redis.json`, `scripts/tests/redis-monitor-local.ps1`, `docs/redis-monitoring.md`. Add local config/package tests beside the script. Existing default Compose, Prometheus alerts and dashboards remain unchanged; the existing dashboard provisioning directory can discover the new Redis dashboard.

**Interfaces:** Consumes collector binary/HTTP/metrics from Task 2, existing Redis Dockerfile/bootstrap and existing monitoring versions. Produces a repeatable local runner, pinned container contexts, tested opt-in rules/dashboard and sanitized local acceptance/handoff.

- [ ] **Step 1 — Resolve and pin local inputs.** Inspect existing official local images or resolve official manifests for Redis, static non-root runtime with CA roots, Prometheus/promtool, Alertmanager and Grafana. Pin digests in the checked-in opt-in files before building/running. Record provenance/checksums. Build static Linux collector locally, copy only the binary into its explicit image context; preserve certificate verification in the image. Do not send the repository or secrets to a cloud builder.

- [ ] **Step 2 — RED/GREEN packaging isolation.** Tests validate expanded `docker compose ... config --format json`: no backend/PG, loopback-only published endpoints, no app/backup/DB credential mount or Docker socket, non-root collector/read-only/caps-drop/no-new-privileges, cap0.1CPU/64MiB, exact local secret inputs and no production host. The runner creates fresh 96-hex synthetic role secrets in an owner-only workspace directory and only uniquely labelled resources. Never broaden the production bootstrap ACL. Cleanup verifies resolved paths stay within the owned artifact directory and Docker IDs/labels match before deleting.

- [ ] **Step 3 — RED/GREEN alerts with actual promtool.** Keep Redis rules in their opt-in file. Test no collector series, scrape-down, missing own-up, own-up0, stale data, AOF disabled/absent detail fields, write/rewrite faults, eviction increase/reset/recovery, high memory hold and unlimited memory. Example rule subject to matching/unit checks:

```yaml
- alert: FastToWinRedisUnexpectedEviction
  expr: (increase(fasttowin_redis_monitor_evicted_keys_total{job="fasttowin-redis-monitor"}[5m]) > 0) and on(job,instance) (fasttowin_redis_monitor_up{job="fasttowin-redis-monitor"} == 1)
  for: 1m
  labels: {severity: critical}
  annotations: {summary: "Redis đã loại bỏ key hạn mức", description: "Kiểm tra chính sách bộ nhớ; không tự reset hoặc đổi namespace."}
```

Run actual `promtool check config`, `check rules`, `check metrics`, and `test rules`. The local fixture scrapes one collector only; the integration example retains original backend/Prometheus jobs and adds that one collector job/rule file, without activating absent-target alarms in the default stack. Review selectors against actual emitted names.

- [ ] **Step 4 — Actual Redis and bounded resource acceptance.** Run isolated Redis with the existing digest/bootstrap and fresh secrets. Collector gets only monitor credentials. A separate synthetic fixture probe verifies successful fixed AUTH/PING/INFO, denied GET/HSET/SCAN/CONFIG/FLUSHDB, and actual AOF numeric/status metrics. A fixture writer using its own synthetic app identity changes only UUID-named fixture data, then observe size behavior; do not assume every write immediately changes physical-file size. Stop/restart only the owned Redis to verify down/recovery and counter-reset behavior. Observe collector under its resource cap. Record physical failure simulations not performed. Preserve/redact tool errors and remove exact owned resources/secret files after acceptance.

- [ ] **Step 5 — Dashboard render and docs.** Follow UI skill/default Grafana styling: readable units/legends, status labels beyond colors, unavailable text for missing/stale data and gaps rather than synthetic zeros. Render local dashboard with actual/synthetic data, inspect desktop and narrow screenshots, and validate recovery/missing-data views. Document Windows/toolchain, runner commands, credentials, limits, alerts/dashboard-only delivery, cleanup and remaining production decisions. If a runtime gate cannot run, retain a reproducible runner and an explicit incomplete result; continue independent local work rather than using production as a substitute.

- [ ] **Step 6 — Commit, review and handoff.** Report RED/GREEN/rule/runtime/UI gates separately with commands, versions and artifact paths. Controller reviews Task 3 and the complete new collector range once. Resolve load-bearing findings; retain honest incomplete environmental gates. Commit the final acceptance record/docs locally. No CI/push or production action is implied; follow the user's CI dispatch-only preference if later requested.

## Execution selection

The Owner approved local implementation and tests with the concrete spec. Continue in this chat using subagent-driven development, sequential implementers and scoped task reviews; no additional execution-choice question is needed. Record the plan ledger, shared-interface scan and per-task base commits before dispatch. Existing workspace isolation is verified; source baseline is `13cf42e`.
