# Redis monitoring with Grafana Cloud Free and Railway

Owner local-test revision,2026-11-03: “Duyệt thử tạo lại WAL monitoring local” authorizes one opt-in synthetic `FullReset` test after actual ENOSPC/gap and restored capacity. Deliberately discard only unsent monitoring history in `/var/lib/alloy/prometheus.remote_write.grafana/wal` inside the exact run-owned held tmpfs volume, after observed stopped exit137/noOOM, ownership/path/holder checks and saved sanitized prior WAL evidence. Require an empty UID/GID65532 WAL directory before one exact-sender restart, unchanged config/pins/caps/private TLS/cadence and at least two distinct fresh original receiver timestamps age0..60, retaining the gap. This is manual destructive local recovery; automatic free-only and preserved-WAL failures remain unchanged. No Redis AOF/game/backend data, real production WAL, automatic reset, deployment, Cloud, CI or push grant. Any remaining startup repair/discard or failed control stops non-green without fallback.

Date: 3 November 2026. Status: Owner approved this concrete design and local preparation in chat. No account, real token, managed resource, production data transfer or deployment is authorized by that approval.

Revision, 3 November 2026: Owner explicitly chose “Chấp nhận khoảng trống khi restart (đề xuất)”. Historical backfill across sender restart is no longer guaranteed. Require fresh original post-restart timestamps, unavailable current state during missing/stale samples, preserved gaps and no retimestamping/fabricated history. Correctly timestamped older samples are allowed if received; loss itself is not required. The two original failed backfill receipts remain failures against the former requirement. Still-running sender buffering remains separate. No pin, queue policy, topology, cap or deployment grant changes.

Controller clarification, same date: WAL retention means expiry from disk WAL including checkpoints, not a wire-age TTL for samples already queued in RAM. Accept local accelerated expiry only with complete before/after timestamp snapshots proving old samples were present and then removed while recent samples remain. Record any older wire delivery separately; do not add `sample_age_limit` or infer expiry from absent replay. Retention/full-filesystem gates are not waived by accepting restart gaps.

## Intended result

Provide an authenticated Grafana Cloud dashboard showing real FastToWin production Redis memory, persistence status, eviction and collection freshness. Reuse the read-only collector and dashboard accepted locally in [the monitoring handoff](../../redis-monitoring.md). Do not move Redis, modify game behavior, or put its credential in Grafana Cloud.

Selected topology:

```text
Railway production private network
  existing Redis <-- AUTH / PING / INFO -- redis-monitor
                                            ^ private HTTP /metrics
                                            |
                                       metrics-sender (Alloy)
                                            |
                                            +-- verified HTTPS remote_write --> Grafana Cloud Free
                                                                                 ^ Owner login
                                                                                 | browser
```

Two new Railway services, one replica each, in the same environment/region as Redis. Collector and sender have no public domain or TCP proxy. Grafana Cloud provides the browser endpoint and storage. No additional Prometheus, Grafana, Alertmanager or database server runs on Railway in this option.

Alternatives considered: a complete self-hosted monitoring stack on Railway gives more hosting control but adds processes, storage and maintenance; Railway Metrics alone adds no monitor services but cannot supply this Redis persistence dashboard. The Owner selected the managed dashboard option for cost and convenience.

## Reuse and deployment boundaries

- Reuse the collector's fixed `ftw_monitor` role, fixed commands, three-second attempt bound, 15-second polling, 45-second monotonic snapshot expiry and bounded HTTP/log/shutdown behavior. No Redis ACL expansion, quota changes, namespace change, restart, persistence operation, key read or scan.
- Collector exposes its existing `/metrics`, `/health` and `/ready` on a private listener. Select a verified IPv4/IPv6-compatible binding for Railway; the existing config accepts `[::]:19121`. Confirm actual private DNS resolution/reachability before enabling shipping. Private WireGuard transport is not claimed to be Redis TLS; preserve the current approved Redis transport configuration.
- Alloy scrapes that single private endpoint every 15 seconds with a five-second scrape timeout. One sender, one static target, no clustering or autodiscovery. No backend, host, Docker, database, logs, traces or profile collection.
- Use a pinned official Alloy version compatible with the project tooling, selected and digest-verified before execution. Do not use `latest`. Existing Grafana Alloy conventions are a starting point, not proof that the production service exists.
- Separate opt-in cloud configuration and dashboard/rules variants; default Compose and local acceptance fixtures remain usable without a Grafana token. Core collector behavior does not change to support shipping.

## Data sent to Grafana

Ship only an exact metric-name allowlist: the 15 existing `fasttowin_redis_monitor_` metric families plus the scrape-generated `up`. No prefix-only forwarding of arbitrary new metrics. A healthy target currently produces 22 numeric series (11 domain, three single-series diagnostics, seven bounded error reasons, one scrape-up), or about 88 data points per minute at 15-second scrapes. Validate the complete emitted payload rather than infer it from this count.

Permitted labels are `__name__`, fixed `job="fasttowin-redis-monitor"`, fixed `instance="redis-primary"`, and `reason` only on the error counter, limited to its seven defined values. Explicitly rewrite/drop the discovered endpoint, private DNS/IP, hostname and any other labels before remote write. Labels and metadata must contain no player/account identifier, game key, namespace, file path, credential or request/response content. Disable exemplars and Alloy usage reporting; do not enable support bundles/profiling or publish the Alloy UI. Metric descriptions are static project text.

The intended external recipient is the Owner's selected Grafana Cloud stack, at its exact HTTPS remote-write endpoint. Endpoint selection must be explicit, use normal certificate verification and reject non-HTTPS, credentials in URLs and unexpected hosts/redirect destinations. No arbitrary HTTP target is accepted from a dashboard or request. Cloud credential entry and first real shipping need their own concrete approval.

## Secrets and access

- The collector receives only the existing monitor-role password, through a private file with the existing 96-lowercase-hex validation. It receives no app, backup, PostgreSQL or Grafana token. Preparing the file from a Railway secret must avoid shell tracing, printing and command-line arguments; run as nonroot with restrictive file permissions.
- The sender receives only a Grafana Cloud stack-specific `metrics:write` token and the non-secret stack endpoint/tenant identifier. It receives no Redis credential. Use a private token file with `password_file`, no fallback credential or admin/read scope. Propose a 90-day token expiry if supported; record the actual expiry and manual rotation procedure before rollout.
- Grafana browser access uses the Owner's own account. No anonymous viewer, public dashboard snapshot, shared password or self-registration in a self-hosted service is needed. Confirm the stack is on the intended Free plan and dashboard access is private before data is sent; do not enable a paid upgrade/trial-dependent feature as a prerequisite.
- No real credential is read during local preparation. A later grant must name the exact local monitor-secret source (or operator entry) and target Railway service, and the exact Cloud token source/destination. Do not decode a bundle containing app/backup roles just because the monitor role is needed.

## Queue, storage and failure handling

Alloy remote write uses its own WAL; it is unrelated to Redis AOF or backup. Propose a dedicated 1-GB sender volume and bounded queue settings, with WAL cleanup every five minutes, minimum retained age five minutes and maximum age two hours. Set a small fixed shard count/queue after validating support in the pinned version. Time retention is not a disk-size guarantee: verify actual WAL use, full-volume behavior and limits locally. If Railway cannot supply the proposed volume/cap, stop and revise the design before creation.

When Cloud is unreachable, local collection continues and the bounded sender buffers/retries using Alloy's queue. It must not call Redis more often, change quotas, restart the game backend, reset a namespace or turn off Redis. An outage longer than retention can lose monitoring samples; show a gap and disclose it, never manufacture healthy values. Authentication/endpoint errors remain failures, not a reason to use another token or destination. Stopping the sender/collector is the rollback for monitoring; it does not restore or mutate Redis data.

The collector's health is process liveness, not Redis readiness. Sender health/config validity does not prove that Cloud accepted a sample; actual remote-write reception and timestamps must be verified separately. No automatic Cloud/resource repair or backup scheduler is introduced.

## Dashboard and alert semantics

Import a cloud variant of the accepted dashboard with the selected metrics datasource UID. Preserve explicit Unavailable states, instant current-value queries, source counter resets and historical gaps. Keep the local dashboard unchanged. Follow the required project UI skill and inspect desktop/narrow views before acceptance.

Cloud shipping adds another possible outage: a sender can stop while its old `up=1` remains in a Prometheus lookback window. Consequently, every current-value panel and domain alert must require all of:

1. Fresh successful collector snapshot (`fasttowin_redis_monitor_up=1`).
2. Reachable scrape (`up=1`) with matching fixed job/instance.
3. Scrape sample timestamp age in the inclusive range 0–60 seconds, evaluated using Cloud query time. Missing, old or future-dated samples are unavailable. Last-success wall timestamp is diagnostic only.

The collector's internal 45-second cutoff is unchanged; the 60-second end-to-end sample gate is separate. Old WAL samples retain original timestamps, so a replay cannot make them current. History may be backfilled within retention, never re-timestamped as now. Add rule/query tests for sender death, exporter death, collection error, delayed/replayed samples, future timestamps and recovery. Missing shipping must become a distinct unavailable/alert condition without waiting for the full query lookback expiry.

Propose porting the eight Redis conditions to Cloud data-source-managed Prometheus rules, preserving existing holds (one minute except memory pressure five minutes) and adding end-to-end freshness gating to the missing-collector condition. Missing samples must not become AOF-success or zero memory. Verify rule support/quotas on the actual Free stack before provisioning; if unavailable, do not silently drop alerts. Initially the requested delivery is dashboard/rule visibility only. Email, push, webhook or phone destinations are a separate choice and authorization; none are promised by this design.

## Resource sizing and cost

Initial proposed ceilings, to be tested locally before any deployment:

| Service | CPU ceiling | Memory ceiling | Persistent storage |
| --- | --- | --- | --- |
| Read-only collector | 0.1 vCPU | 64 MiB | None |
| Alloy sender | 0.1 vCPU | 256 MiB | 1 GB WAL volume |

These are operational ceilings, not reserved usage or a billing cap. Collector's earlier bounded real-Redis sample was 3.953 MiB; sender memory/CPU/WAL use has not yet been measured for this config. Do not claim the existing collector-only measurement includes Alloy.

Planning estimate: **USD 1–3/month incremental Railway usage**, separate from the existing Redis service and other application resources. This assumes roughly 0.10–0.25 GB average aggregate memory, 0.005–0.02 average vCPU, up to 1 GB storage and small egress. Railway's published container rates are USD10/GB-memory-month, USD20/vCPU-month, USD0.15/GB-volume-month and USD0.05/GB egress. This estimate is conditional, not an accepted measured bill or hard spending limit; full ceiling use could exceed it.

Grafana currently advertises Free metrics with 10,000 active series and 14-day retention; confirm account/stack limits including data-point rate before activation. Do not multiply the 22-series estimate into a blanket free-tier guarantee. Start with the Free plan only. Propose review if projected incremental monitoring usage exceeds USD3/month and require a new cost decision before accepting USD5/month or increasing resources. This is an operator review policy, not an automatic Railway cutoff or scheduled monitor.

## Preparation, acceptance and production approval

After Owner review of this design, prepare a local implementation plan and isolated opt-in packaging/config with synthetic credentials only. Local acceptance must cover:

- Actual Alloy configuration validation, fixed allowlist/label sanitation at a local remote-write receiver, no accidental host telemetry or raw credentials, verified TLS failure without fallback, and bounded error/queue behavior.
- Collector-to-sender private IPv4/IPv6 compatibility, single-scrape cadence, sender outage/restart/WAL replay, token rejection and oldest-sample expiry. Observe actual memory/CPU/WAL use under the proposed ceilings and preserve explicit gaps.
- PromQL alert/instant-query tests against original timestamps, stale/future/missing series and counter resets. Desktop/narrow dashboard states with synthetic data, without treating that as Cloud receipt evidence.
- Exact owned local process/container/volume and synthetic-secret cleanup. No production key, account registration, Cloud token or managed resource is needed for these tests.

Only after the local package, pinned inputs and rollback/cost/access summary are reviewable, ask for a production grant naming: exact Grafana account/stack and ingestion data; stack-scoped token creation/use; exact monitor-role source access; upload artifacts and new Railway services/volume/settings/costs; dashboard/rule import; first shipping and bounded real-receipt observation; and cleanup/rollback authority. No Redis, backend, Web or PostgreSQL restart/migration is planned. Validate that adding monitor traffic does not alter game rate limits.

Production acceptance must show the real target's numeric samples in the private Cloud dashboard, correct freshness and access, intended rule loading, observed low-cardinality/usage and service resource state. Use only bounded safe monitoring-path interruptions for failure proof; no production Redis stop, stress writes or ACL-denial probes are authorized by monitor deployment. Hand over the actual dashboard link, access ownership, token expiry, resource identities, measured cost estimate, rollback and remaining notification decision.

## Sources and interpretation

- [Grafana pricing](https://grafana.com/pricing/): advertised Free retention/series limits; account limits still require verification.
- [Railway container pricing](https://docs.railway.com/pricing/plans): unit rates; all workload assumptions and cost review thresholds above are project proposals.
- [Railway private networking](https://docs.railway.com/networking/private-networking): same-environment private discovery/transport, not a claim of application TLS.
- [Alloy remote write](https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.remote_write/): WAL, queue, authentication and component-health semantics.
- [Alloy scrape](https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.scrape/) and [relabel](https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.relabel/): scrape/egress data shaping.
- [Alloy run](https://grafana.com/docs/alloy/latest/reference/cli/run/): runtime listener/reporting controls.
- [Cloud credential roles](https://grafana.com/docs/grafana-cloud/platform/security-and-account-management/security-and-access/authentication-and-permissions/access-policies/manage-cloud-data-source-credentials/): write token is separate from browser/read access.

## Review state

Self-check: the topology, secrets, namespace safety and measurement gates agree; local preparation never implies real shipping. Sender failure/backfill has an explicit freshness gate. The USD1–3 figure remains an estimate with assumptions, not a limit. Account/stack/token/resource values are deliberately supplied only under a later grant, not missing implementation defaults. Cloud rules/free limits, Alloy sizing, private IPv6 and production receipt are required gates, not assumed accomplishments. Owner approved this concrete design for planning and local implementation; real Cloud/Railway access remains outside that grant.
