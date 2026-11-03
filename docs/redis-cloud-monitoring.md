# Redis Cloud monitoring: local preparation and operator handoff

This is an opt-in package for the approved [design](superpowers/specs/2026-11-03-redis-monitor-grafana-cloud-design.md). No Grafana account, real credential, Railway resource or production transfer has been authorized or performed. Local acceptance results and outstanding gates are recorded in the implementation report; a failed gate blocks production acceptance.

**Deployment is blocked.** On 2026-11-03 two bounded local runs failed to demonstrate WAL restart backfill: after a 46-second receiver outage and restart of the same sender/container volume, HTTP204 resumed but no outage-window sample timestamps were received within the 60-second verification deadline. HTTP204 recovery is not replay acceptance. Accelerated retention expiry and constrained-filesystem failure/recovery remain unverified because the second owning runner was interrupted before recording those results. No third pipeline run was performed, and production settings or Alloy pins were not changed to bypass these gates.

The retained local receipts are under `.artifacts/redis-cloud-local-20261103/`: `run-98fd18bb06d4469f919dda9604aa58ba` records the first replay failure; `run-97210ab8261940c7b08959d9bd703eb6` records the second failure, transport, freshness and resource observations; `run-cf712a772fd14d87bc353def8c3444bb` holds the independent synthetic dashboard screenshots and stale-query proof. These ignored artifacts are local evidence, not portable Cloud acceptance. Exact fixture cleanup is recorded in each run's `cleanup.json`.

Actual local Prometheus receipt established 22 series across 16 allowed names, fixed labels/seven error reasons, rejection of injected labels/names/reasons, private IPv4/IPv6 HTTP200, and roughly 15-second cadence. Actual Alloy rejected hostname mismatch, untrusted CA, token401 and redirect307 without fallback. Sender interruption left a historical lookback series while the current query became unavailable after65seconds, then recovered; collector interruption produced scrape-up0 and recovery. The separate dashboard rendered healthy, stale at about125seconds with all eight current panels Unavailable and history retained, then recovered at desktop1440×1000 and narrow390×844. This does not establish remote Cloud receipt.

The collector polls Redis with the existing monitor role every15seconds. Alloy scrapes the private collector every15seconds and sends only16 allowed metric names (22 healthy series) over verified HTTPS to the one approved Grafana metrics endpoint. Redis credentials never go to Alloy or Grafana. No host, Docker, backend, PostgreSQL, logs, traces or profiles are collected. The existing default local monitoring stack is unchanged.

## Reproduce local acceptance

Use Windows PowerShell, Node, an already running local Docker daemon/Compose plugin, and the previously verified portable tools under `.artifacts/redis-monitor-local-20261103/`. The portable Go toolchain generates fixture-only certificates; native Prometheus/promtool3.11.3 and Grafana13.2.0 support the separate UI check. Nothing is globally installed; the runner does not start or repair Docker Desktop.

```powershell
node --test scripts/tests/redis-cloud-fixture.test.mjs
& ./scripts/tests/redis-cloud-local.ps1 -Mode Shipping
& ./scripts/tests/redis-cloud-local.ps1 -Mode UI
```

Local loopback/Docker named-pipe access can require an execution permission outside the restricted sandbox. The shipping runner fixes `DOCKER_HOST` to the local named pipe, removes inherited Docker/build context settings, and creates an owned empty-auth Docker configuration while retaining discovery of the installed Compose plugin. It never reads the user's Docker credential configuration. Anonymous pulls use the official digests in `image-provenance.json`; Prometheus uses the previously verified3.11.3 digest in the fixture Compose file.

Every run creates an owner-only `.artifacts/redis-cloud-local-20261103/run-<32hex>` directory and unique Docker labels. `commands.json`, `acceptance.json`, receiver status/hash/byte receipts and `cleanup.json` persist as each gate completes. They contain no token, auth header or raw remote-write body. Secrets and Redis configuration are synthetic and are removed in the owning `finally` cleanup. Only containers, network, volumes and built image tags belonging to the exact run are removed. There is no prune, shared resource deletion or Docker socket mount.

Shipping uses a five-service internal-only dual-stack network: synthetic Redis, the frozen collector, current sender packaging, a fixed HTTPS/basic-auth receiver, and real Prometheus remote-write ingestion. No host ports are published. A fixture-only relay adds adversarial labels, an unapproved metric family and an invalid error reason to the real collector response; the production Alloy scrape/relabel/queue bytes must remove them before the real receiver. The local configuration changes only the remote endpoint and trust root. Production destination validation rejects the fixture endpoint. A separate accelerated retention variant changes only5m/5m/2h into10s/10s/30s and is never a production setting or a two-hour test.

UI mode starts a separate localhost-only Prometheus/Grafana pair for at most12minutes. Open only the `dashboard` URL printed in its owned `endpoints.json`. This uses the new Cloud dashboard; only the owned provisioned copy replaces `${DS_PROMETHEUS}` with the fixture UID. The shipped import stays unchanged. Synthetic fixed-label samples explicitly preserve their original timestamps:

```powershell
# Use the exact run path printed by UI mode, in the same owning execution context.
Set-Content -NoNewline '<owned-run>/mode' 'frozen'
# After more than60seconds, verify current panels are Unavailable while history remains.
Set-Content -NoNewline '<owned-run>/mode' 'healthy'
# Verify recovery; then stop only this fixture.
New-Item -ItemType File '<owned-run>/stop'
```

Check desktop and390px narrow screenshots. Grafana lazily refreshes offscreen panels: allow a refresh after resizing/scrolling before judging a newly visible panel. Readable units and explicit Fresh/Enabled/OK/Unavailable text carry meaning independently of color. Synthetic UI acceptance is separate from actual Alloy reception and never establishes Grafana Cloud access or receipt.

## Runtime boundaries and failure behavior

`prepare.mjs` accepts only the exact approved HTTPS `prometheus-….grafana.net/api/prom/push` destination, decimal tenant and a private collector host:port. Railway examples use `[::]:19121`, one replica and no public endpoint. Redirects, proxies from ambient environment, exemplars, native histograms and metadata shipping are disabled. Do not substitute a fallback endpoint or token after an error.

The collector launcher accepts only the96-lowercase-hex monitor password and writes an owner-only runtime file. It removes unrelated credentials before executing the nonroot collector. The sender launcher accepts only its stack-scoped metrics-write token and endpoint/tenant settings, prepares a private file and removes Redis credentials from its child environment. Keep the monitor and sender secrets in separate service-specific stores. Never pass either through CLI arguments, screenshots, logs, diffs or deployment reports.

Every current value and domain alert requires collector-up1, scrape-up1, and sample timestamp age in the inclusive0–60second interval using query time. Missing, old and future samples are unavailable. Last-success wall time is diagnostic only. WAL backfill must retain original sample timestamps: old samples must not become current health. Counter resets and historical gaps remain visible.

The sender WAL is unrelated to Redis AOF. The proposed production settings are cleanup5minutes, minimum retention5minutes and maximum2hours, one queue shard and a dedicated1-GB volume. Time retention is not a hard disk quota. A normal named Docker volume cannot portably enforce a1-GB hard capacity. The fixture includes a192-MiB owned tmpfs scenario to model space errors and recovery; that scenario has not been verified and would say nothing about physical disk durability. Do not claim restart backfill or oldest-sample expiry from process health, configuration validity or an HTTP204 carrying newer data. Use the actual timestamp receipts and mark any unsuccessful gate explicitly.

The source acceptance checks require successful scrapes and queue growth in both expiry windows, original newer backlog timestamps after recovery, and absence of older queued samples. Missing queue instrumentation or no backfill fails this gate. Filesystem acceptance records latest received timestamps during the full condition, requires an observed delivery gap and fresh post-recovery timestamps, and distinguishes continued delivery from a demonstrated gap; bounded absence cannot prove permanent loss. Cleanup attempts all exact owned resource and secret removals, checks containers/volumes/networks/images/secrets, and fails the runner if any action or remaining-resource check fails. These source checks have focused test coverage; runtime retention/full acceptance remains unverified.

On shipping errors, collection must retain its fixed15second cadence. Never expand Redis ACLs, change rate-limit namespaces/quotas, restart the game, run persistence commands, or alter Redis to fix telemetry. Monitoring rollback stops the sender and collector; queued telemetry may be lost. Preserve/export safe diagnostics before removing an approved monitoring WAL volume.

## Resources, costs and unresolved gates

Proposed production ceilings remain collector0.1vCPU/64MiB and sender0.1vCPU/256MiB with1GB dedicated WAL. The fixture inspects actual caps/OOM state, samples memory/CPU, measures logical WAL bytes and compressed accepted payload bytes, and projects egress separately. Brief Docker samples are observations, not monthly billing or representative load tests. Do not silently resize a failing sender or infer fit from an old collector-only measurement.

One brief second-run observation measured sender55.77MiB/256MiB at Docker CPU0.19%, collector3.695MiB/64MiB at0.00%, both with enforced0.1CPU caps and no OOM kill. Logical WAL size was197,189bytes in8files. Accepted compressed payload was7,556bytes across371.487seconds, extrapolated mechanically to0.0491GiB/30days; outage phases make this unsuitable as a representative healthy egress forecast. Healthy cardinality implies88points/minute. These measurements do not establish sustained fit, a hard1-GB quota, monthly cost or billing.

USD1–3/month incremental Railway usage remains conditional on average combined memory0.10–0.25GB, CPU0.005–0.02vCPU,1GB storage and small egress. Use the approved design's unit-rate assumptions only as a planning calculation; verify current prices and real billing under a later grant. Review projected incremental monitoring cost aboveUSD3/month. A new cost decision is required before acceptingUSD5/month or increasing resources. There is no automated spending cutoff or scheduler.

Real Grafana Free plan/quota, rule support, private dashboard access, token expiry, Railway volume capacity, production private DNS/IP behavior, actual Cloud receipt and sustained cost/fit are unverified. A local failed WAL/expiry/full gate remains a release blocker until the controller adjudicates a concrete revision and it passes; this document does not waive those gates.

## Production approval DRAFT — not an access request

Fill and approve this only after reviewing the local report and resolving failed local gates. No actual production values are needed to review this draft.

Current decision: **DRAFT / deployment blocked**, pending demonstrated WAL replay with preserved timestamps, retention expiry, full-filesystem behavior and the later account/resource gates below. This draft grants no production access.

| Later approval item | Exact information/authority required |
| --- | --- |
| Account and destination | Named Owner account/Free stack, private dashboard access, exact metrics-write host/path and tenant; allowed data is the16-name/fixed-label numeric payload only. |
| Cloud credential | Named stack-specific `metrics:write` token source/destination,90-day expiry if supported, actual expiry/owner and manual rotation procedure; no admin/read fallback. |
| Redis monitor source | Exact existing monitor-role secret source and collector destination only; no bundle decoding containing app/backup credentials. |
| Railway resources | Exact environment/region, two new one-replica private services, reviewed artifacts/digests, CPU/memory ceilings,1-GB sender volume/cap method and current incremental cost decision. |
| Dashboard and rules | Import the separate Cloud dashboard with the selected datasource UID; verify all eight rules/holds and Free stack quota/support. No alert delivery destination is included. |
| First shipping | Enable only the approved endpoint; bounded receipt/cardinality/resource observation, private IPv4/IPv6 verification and dashboard freshness with safe monitoring-path interruptions only. No Redis outage/stress/ACL-denial probe. |
| Rollback and cleanup | Authority to stop the two monitor services, revoke the scoped token, and remove only their named resources/WAL after acknowledging telemetry loss. No Redis/backend/Web/PostgreSQL restart or migration. |

After a separately approved rollout, hand over the actual private dashboard link, access owner, token expiry, service/volume identities, observed series/rate/resources/cost, rollback result and remaining notification choice. Account creation, real tokens, uploads, Railway changes, managed rules and first real transfer remain separate actions requiring that concrete production grant.
