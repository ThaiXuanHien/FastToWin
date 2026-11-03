# Redis Monitor Grafana Cloud Local Preparation Implementation Plan

Owner local-test extension,2026-11-03: one explicit `FullReset` reuses the bounded held64MiB FullRecovery fixture and shared cleanup, skips accepted phases and permits only fixed monitoring-component WAL removal/recreation after saved prior evidence, restored capacity, observed stopped137/noOOM and exact run-volume/holder/path checks. A bounded nonroot/network-none helper may write only that held volume. Record deliberate loss of unsent monitoring samples, then require empty UID/GID65532 WAL and two distinct fresh original post-restart receiver samples, same config/pins/caps and retained gap. Relevant focused guard/routing/evidence tests and one actual run; no second attempt or fallback. Preserve earlier automatic/preserved-WAL failures. Source/evidence scoped review remains required; no production reset script, Redis AOF/game/backend changes, Cloud/CI/push or task-complete grant.

Owner revision, 2026-11-03: accept historical gaps across sender restart; require fresh original post-restart timestamps and unavailable current values for missing/stale samples. No durable restart-backfill promise, no retimestamping or fabricated history. Existing failed receipts remain unchanged failures of the former contract. Reuse retained focused restart/freshness and desktop/narrow stale UI evidence; no repeat of accepted phases. Controller clarification: test accelerated expiry on complete disk WAL plus checkpoint timestamp snapshots, requiring old-before/absent-after and a recent retained control; RAM-queued samples have no new wire TTL. Keep production5m/5m/2h, queue, pin, renderer/core/caps unchanged. Finish only remaining storage phases in one bounded opt-in mode, continuing independent safe checks after a failed gate and retaining nonzero failure/cleanup semantics.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a locally accepted, reviewable collector/Alloy cloud-shipping package without sending data to a real Cloud account or modifying production.

**Architecture:** Two separately credentialed nonroot services reuse the read-only collector and add a single Alloy scrape/relabel/remote-write pipeline. A cloud dashboard/rule variant adds end-to-end timestamp freshness. An isolated local HTTPS/auth receiver backed by Prometheus proves actual remote-write payloads, failures, WAL behavior and resource use with synthetic data.

**Tech Stack:** Existing Go collector unchanged, Grafana Alloy official pinned image, shell launch adapters, Node built-ins for preparation/tests, actual Prometheus/promtool, existing Grafana runtime, Docker local only.

**Spec:** `docs/superpowers/specs/2026-11-03-redis-monitor-grafana-cloud-design.md` (Owner approved local preparation on 3 November 2026).

## Global Constraints

- No account, token, real Grafana Cloud shipping, production secret access, Railway/SSH/resource change, push/CI/merge, external alert destination or scheduler is authorized. Public anonymous official documentation/manifests/image pulls and uniquely owned local fixtures are allowed. No user Docker credential configuration or remote builders.
- Reuse the collector's fixed `ftw_monitor` role, fixed commands, three-second attempt bound, 15-second polling, 45-second monotonic snapshot expiry and bounded HTTP/log/shutdown behavior. Core collector behavior does not change to support shipping.
- Alloy scrapes that single private endpoint every 15 seconds with a five-second scrape timeout. One sender, one static target, no clustering or autodiscovery. No backend, host, Docker, database, logs, traces or profile collection.
- Ship only an exact metric-name allowlist: the 15 existing `fasttowin_redis_monitor_` metric families plus the scrape-generated `up`. No prefix-only forwarding of arbitrary new metrics.
- Permitted labels are `__name__`, fixed `job="fasttowin-redis-monitor"`, fixed `instance="redis-primary"`, and `reason` only on the error counter, limited to its seven defined values. Disable exemplars and Alloy usage reporting; do not enable support bundles/profiling or publish the Alloy UI.
- Collector has only the monitor secret; sender has only a stack-scoped synthetic metrics-write token. No password/token arguments, dumps, shell tracing, insecure TLS, redirects/fallback credentials or arbitrary destination endpoints. Synthetic values/files are owner-only and removed by the owning fixture.
- Keep separate opt-in configuration and cloud dashboard/rules. Existing default Compose, local collector fixture, game modules and dashboards remain unchanged.
- End-to-end current values/domain alerts require own-up1, scrape-up1 and sample age in inclusive 0–60 seconds using query time. Missing, old or future samples are unavailable; retain original WAL sample timestamps, historical gaps and counter resets.
- Proposed collector cap0.1 CPU/64 MiB; sender0.1 CPU/256 MiB, dedicated1-GB WAL, cleanup5m/minimum5m/maximum2h retention. A retention setting is not a disk-size guarantee. Measure before claiming fit; do not silently increase limits.
- Planning estimate USD1–3/month is conditional, separate from current Redis. Review projections above USD3 and require a new cost decision before USD5/resources increase. No automated budget cutoff or resource purchase.
- UI changes follow `.agents/skills/ui-ux-pro-max/SKILL.md`, retain accessible units/text states, and get desktop/narrow actual render checks. Synthetic local UI is not Grafana Cloud acceptance.
- Use only exact owned local labels/IDs and verified workspace paths for cleanup, never prune/reset/Docker repair or shared resource deletion. Keep failed gates explicit. No test/CI polling beyond owned bounded local execution.

## File map and interfaces

Cloud runtime files: `deploy/monitoring/redis-monitor-cloud/` — Alloy config template, pure preparation module, two private secret launchers, pinned Dockerfiles/provenance and separate collector/sender Railway config examples. No deployment command runs automatically.

Local test files: `scripts/tests/redis-cloud-*.test.mjs` — direct behavior/config contracts; `redis-cloud-local.ps1` and `redis-cloud-fixture.mjs` — bounded uniquely owned local orchestration; `redis-cloud-receiver.mjs` — local-only HTTPS/basic-auth forwarding into a real Prometheus remote-write receiver, no public destination.

Cloud visual/rule files: `deploy/monitoring/redis-monitor-cloud/redis-alerts.yml`, `redis-alerts.test.yml`; `deploy/monitoring/grafana/dashboards/fasttowin-redis-cloud.json`. Operator handoff: `docs/redis-cloud-monitoring.md`.

Task1 exports a dependency-free preparation module `prepare.mjs`:

```js
export const metricNames; // frozen exact names including scrape up
export function validateDestination({url, expectedHost, tenantId}); // returns normalized non-secret values or fixed safe error
export function renderAlloy({url, expectedHost, tenantId, collectorAddress}); // returns config; token path fixed /run/secrets/grafana-write
```

Production destination validation: HTTPS only, exact expected host matching a Grafana metrics host under `grafana.net`, port443/default only, path `/api/prom/push`, no userinfo/query/fragment/control characters or redirects. Collector target must be one private host:port, not user-supplied URL; runtime examples use the explicit private listener `[::]:19121`. Tenant is a nonempty decimal identifier. No secret value enters the renderer. All credentials remain file-based runtime values.

Local fixture produces an endpoint-only/trust-only variant by replacing the validated Cloud endpoint with `https://receiver:9443/api/v1/write` and adding the synthetic receiver CA. The metric selection, labels, scrape cadence and queue pipeline are byte-identical to production output; contract-test this boundary. A fixture endpoint is never accepted by the Railway production launcher.

### Task 1: Private sender configuration and nonroot launch packaging

**Files:** Create the cloud runtime directory files described above, `scripts/tests/redis-cloud-config.test.mjs` and `scripts/tests/redis-cloud-launch.test.mjs`. Select focused names inside the runtime directory once and keep preparation/secret launch/Docker configuration separate. Reuse existing frozen collector binary as input; do not change collector Go source or add an arbitrary command surface.

**Consumes:** Approved metric contract and existing collector binary/defaults. **Produces:** `metricNames`, `validateDestination`, `renderAlloy`, fixed two-service launch interfaces, pinned image/config provenance; Task3 mounts those exact files/binaries in local tests.

- [ ] **1 — RED configuration/egress contracts.** Test renderer with a synthetic endpoint `https://prometheus-fixture.grafana.net/api/prom/push`, matching expected host, tenant `123456`, collector `redis-monitor.railway.internal:19121`. Assert unsafe destination/tenant/target inputs reject without echoing a marker; no runtime secret is returned. Include complete expected16 metric names, reason-only error series and dropped other labels, constant job/instance, no metadata/discovery/host collection, no insecure TLS/redirect/reporting. Add malformed URL, wrong host, alternate port, escaped path, URI target and missing values. Run before implementation:

```powershell
node --test scripts/tests/redis-cloud-config.test.mjs
```

- [ ] **2 — Implement the exact allowlist pipeline and bounded queue.** `prometheus.scrape` forwards through `prometheus.relabel` to a single `prometheus.remote_write`. Drop other names; validate error reasons; remove reason from other families; retain only permitted labels. Disable metadata/exemplars, redirects and usage reporting. Set queue min/max shards1, capacity1000 and max samples/send250; verified support of those values in the pinned Alloy is required. Use a10-second remote timeout and retry backoff between1s and30s; WAL5m/5m/2h as specified. Fail closed on config errors. No fallback to example endpoints/tokens.

- [ ] **3 — RED/GREEN private launch contracts.** Use shell launchers with test-owned fake binaries to assert: missing/malformed monitor or write token/config fails safely; credentials are written to regular owner-only files under a process-owned private directory, removed from the child environment and never printed; only collector gets monitor and only sender gets write token. Exec the correct binary with fixed listeners and no secret arguments. Preserve exits and ensure setup failure cleans its own partial file. Restrict runtime paths/output; no shell eval. Use synthetic files only:

```powershell
node --test scripts/tests/redis-cloud-launch.test.mjs
```

- [ ] **4 — Pin actual runtime inputs and validate Alloy.** Resolve anonymous official manifest digests before executing images. Prefer project Alloy1.18.0 if still appropriate and verify its available flags/config; otherwise report the concrete compatibility reason before changing version. Collector adapter needs a nonroot shell/file bridge and CA roots while retaining its core binary; pin official runtime/CA input and prove CA presence, do not install tools globally. Validate actual generated config with the pinned `alloy validate` command and check private IPv6 binding/config. Record no-shell/insecure behavior as failure, not a reason to broaden roles. Measure actual runtime later in Task3.

- [ ] **5 — Focused verification, self-review, commit/report.** Run the two focused suites together once after changes, actual Alloy validation, shell syntax and diff checks. Write task1 report with RED/GREEN, exact versions/digests/commands and known cross-task gates, then commit locally. Do not run Cloud, Railway, CI, push or the full old collector/UI suites.

### Task 2: Cloud timestamp-aware rules and dashboard

**Files:** Create cloud `redis-alerts.yml`, `redis-alerts.test.yml`, separate dashboard `fasttowin-redis-cloud.json`, `scripts/tests/redis-cloud-dashboard.test.mjs`. Optional pure helper next to runtime configs may generate/check repeated query text, but the shipped dashboard is self-contained JSON with a datasource input, not a hardcoded Cloud stack.

**Consumes:** Task1 fixed job/instance/metric names. **Produces:** standalone eight-rule YAML and cloud dashboard UID `fasttowin-redis-cloud`, all current-domain queries guarded against remote-delivery staleness. These are Task3's local receiver/Grafana acceptance inputs.

- [ ] **1 — RED actual PromQL contracts.** Test sender death while old scrape/own-up stay1, scrape0, own-up0/missing, age exactly60s/stale after60s/future timestamp, WAL replay with old timestamp, delayed recovery, AOF-off/err, eviction reset/recovery, memory positive/unlimited and holds. Current freshness expression composes exact `(time() - timestamp(up{job="fasttowin-redis-monitor",instance="redis-primary"}) >= 0)` and `<=60`; no last-success freshness. Use actual `promtool test rules` with controlled sample timestamps/time, not source-text matching. Include query-expression expected results for all current panels; source rules must demonstrate RED sensitivity by an artifact-only ungated copy.

- [ ] **2 — Implement eight cloud rules.** Missing target means absent/down/stale/future scrape; collection failure handles a reachable current scrape without successful own-up; domain conditions require all three gates. Holds remain1m except memory5m. Preserve source error meaning/counter-reset safety. Reuse wording/units where appropriate without modifying local rules.

- [ ] **3 — Implement dashboard with explicit unknown state.** Use datasource input `${DS_PROMETHEUS}` resolved at import, instance fixed/redacted `redis-primary`, instant stat queries, explicit Unavailable labels and historic gaps. Every current panel uses the gate; historic metric queries must not relabel/re-timestamp WAL data as current. Retain desktop/narrow readable statuses, units/legends and accessible titles. Only synthetic local render will occur in Task3.

- [ ] **4 — Validate and commit/report.** Run actual `promtool check rules` and `test rules`, and the dashboard/config behavior test once after focused GREEN. Capture names/count/commands and no real Cloud claim in task2 report; commit the separate variants. Free-plan managed-rule support remains a future account-specific gate, not a reason to remove local tested rules.

### Task 3: Actual local remote-write acceptance, resources and operator handoff

**Files:** Create `scripts/tests/redis-cloud-local.ps1`, `redis-cloud-fixture.mjs`, `redis-cloud-receiver.mjs`, focused `redis-cloud-fixture.test.mjs` (and receiver test if separate behavior needs its own seam), `deploy/monitoring/redis-monitor-cloud/compose.local.yaml`, local receiver config files and `docs/redis-cloud-monitoring.md`. Keep test helpers focused; a separate local probe helper is allowed if needed for internal-only networking. Add no production auto-deploy script.

**Consumes:** Task1 exact launch/render/pinned images, Task2 queries/rules/dashboard, existing official Redis bootstrap, actual Prometheus/promtool receiver and native Grafana tooling. **Produces:** repeatable bounded local acceptance and sanitized production approval draft, no real account/secret/link.

- [ ] **1 — RED fixture ownership and TLS/auth receiver.** Test unique workspace-owned IDs/labels, local daemon/empty-auth Docker config with existing plugin discovery, no production paths/endpoints/secrets, no backend/PG/socket/global host mounts. Receiver has a synthetic CA/server certificate, exact Basic credentials and fixed `/api/v1/write`, fails auth401 and redirects only in deliberate test cases, and forwards authorized compressed requests unchanged into a real Prometheus remote-write receiver. Never log credentials/raw headers/payload. Save sanitized counts/hash/status only. TLS cert/key/auth are fixture-only; test untrusted/hostname-mismatch/redirect/wrong-token paths without disabling verification.

- [ ] **2 — Run actual private pipeline under caps.** Use uniquely labelled local Redis+collector+Alloy+TLS proxy+Prometheus receiver, internal-only network. Keep collector Redis role monitor-only; separate fixture writer/probes use synthetic app credentials only. If host ports are suppressed on internal Docker29 networking, use an owned in-network probe, not a public/egress network. Assert actual received names/labelsets/22-series expected healthy payload, malicious/extra labels and reason sanitation before remote write, one15s cadence, private IPv4/IPv6 behavior. Receiver reception is the success evidence; sender health alone is not.

- [ ] **3 — Failure, restart and storage acceptance (revised2026-11-03).** Stop only owned sender/proxy/collector phases to demonstrate unavailable/gaps, no extra Redis command amplification, token401 and TLS/redirect rejection. Sender restart may leave history gaps; prove fresh original post-restart samples and preserve timestamp/freshness semantics. Durable restart backfill is not promised. Use a clearly fixture-only accelerated disk-WAL/checkpoint retention variant with old-before/absent-after and recent-present controls; contract-test production5m/5m/2h remains unchanged and do not call an accelerated run a two-hour wall-clock test. RAM-queued older delivery is observed separately, without a new wire TTL. Exercise a constrained owned filesystem full condition, record bounded failure/recovery/gaps without quota/Redis operations; do not infer physical durability. Inspect caps/noOOM and actual sender+collector memory/CPU, WAL bytes, sample/cardinality/egress estimate. If budget/fit not established, report it; do not silently resize. Receiver failure cases must end within a fixed owning deadline and still reach exact cleanup.

- [ ] **4 — Actual dashboard render.** Use a new localhost-only Grafana fixture/tab and local received/synthetic data. Verify healthy→unavailable after sample-age gate despite old lookback data→recovery; inspect desktop/narrow screenshots with current panels unknown while history remains. Existing portable tools can be reused, but screenshots must match the new cloud dashboard. Keep actual Cloud receipt/access/Free quota gates explicitly unverified. No interaction with user's production tabs/accounts.

- [ ] **5 — Handoff, final checks and commit/report.** Record actual commands/digests/results, all initial failures, isolated cleanup and known gaps in task3 report. Document runnable commands, credential separation, TLS/WAL/freshness behavior, real measurements vs USD1–3 assumptions and budget review thresholds. Prepare a production-approval DRAFT identifying later actions/data/destinations without reading actual production values or requesting access prematurely. Run only relevant new suites once, actual config/rule checks and changed-file formatting/syntax, commit docs/source locally. Controller performs scoped task and broad final review before handoff; no CI/push/deployment implied.

## Execution and preflight

The Owner approved the concrete spec and local preparation, so execute sequentially in this already isolated branch/worktree with subagent-driven development; do not ask another execution-choice question. Task reviews are source read-only and use implementer reports rather than rerunning suites. Preserve incomplete gates honestly if local runtime is unavailable. Record the producer/consumer interface consistency table, spec coverage and any rulings in this plan's own ignored SDD ledger before dispatch. Keep the previous monitor plan's ledger untouched.
