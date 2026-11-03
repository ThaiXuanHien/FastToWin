# Redis AOF and eviction monitoring — local design

Date: 03 November 2026. Direction approved by Owner in chat: a separate read-only collector using `ftw_monitor`, polling every 15 seconds, integrated with the existing Prometheus/Grafana stack, with local implementation and tests first.

This document is the concrete design for review. It is not a production access or deployment grant. The completed Redis cutover and temporary-key revocation remain recorded in [the production handoff](../../redis-production-handoff-2026-11-03.md).

## Outcome and boundaries

Make Redis persistence failures, unexpected eviction, memory pressure and lost/stale monitoring visible without putting the monitor credential in the game backend. A successful result includes local collector behavior, real Redis ACL acceptance, tested alert rules and dashboard/config validation. These are separate evidence gates: synthetic tests cannot substitute for real Redis acceptance or an actual rendered dashboard.

Production remains one backend using the existing private Redis. This work does not alter game code, Redis ACL/bootstrap, quotas, namespace, persistence, backup schedules, provider flags, CI or any cloud resource. No existing production credential blob or password file is read during local development. External alert delivery is outside this local increment: the existing Alertmanager receiver has no email/webhook destination.

## Selected approach

Add a small standalone Go collector using only the standard library, with a bounded RESP2 reader and fixed commands. A background poller publishes one immutable snapshot to a private HTTP metrics endpoint. The existing Prometheus configuration and Grafana dashboard consume it. Go provides a cancellable network dial, including DNS, and connection deadlines without a JVM distribution or a third-party Redis client's additional handshake/probing commands. The implementation must verify these bounds rather than infer them from a mock.

Alternatives considered:

- An off-the-shelf Redis exporter offers broader coverage but requires auditing/disabling additional command paths against the current `PING`/`INFO` ACL. Broadening the ACL is outside the approved direction.
- A manual on-request inspection has less deployment work but does not provide continuous detection. Owner selected the separate collector direction.

Go is not currently available on PATH. Implementation may locate an existing cached official toolchain or prepare a checksum-verified official portable toolchain inside the workspace; it must record the selected version/checksum, avoid global installation/settings changes, and not use unverified binaries. Container packaging must pin official build/runtime inputs before execution; no `latest` tag is an acceptance artifact.

## Collector interface and isolation

Proposed module: `tools/redis-monitor/`, with separate configuration, Redis collection/parsing, snapshot/exposition and process entry-point files. Tests exercise their public behavior. Packaging and local fixtures live under `deploy/monitoring/redis-monitor/`; operator guidance lives in `docs/redis-monitoring.md`.

Configuration:

- `FASTTOWIN_REDIS_MONITOR_ADDRESS`: one `host:port`, without URI scheme, credentials, path, query or fragment. Exactly one Redis target; no request-supplied target override.
- `FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE`: a readable regular file containing exactly 96 lowercase hexadecimal characters, with at most one trailing line ending. Read at startup, bounded to 128 bytes; reject missing/invalid files without exposing their contents. No fallback to app/backup credentials or a password argument.
- Username is fixed to `ftw_monitor` in the collector. It is not configurable.
- `FASTTOWIN_REDIS_MONITOR_TLS`: `false` by default for the isolated local fixture/private-network proposal, or `true` for certificate-verified TLS using the host name and system roots. No insecure TLS override.
- `FASTTOWIN_REDIS_MONITOR_LISTEN`: defaults to `127.0.0.1:19121`. Container examples explicitly listen on `0.0.0.0:19121` within the container network and publish no public host port. Local interactive access, if enabled, binds a host port only to loopback.

Do not mount the application Redis password, backup password, DB secrets, Docker socket or Redis data volume into this collector. The container runs as a non-root user, with a read-only root filesystem, dropped capabilities and no-new-privileges. The local resource cap is 0.1 CPU / 64 MiB, subject to measured acceptance; inability to fit is reported, not silently increased. Cloud sizing/cost is a later decision.

## Fixed collection protocol

Each attempt opens one connection and sends, in order:

1. `AUTH ftw_monitor <secret>`.
2. `PING`, requiring `PONG`.
3. `INFO memory persistence stats`.

The socket is then closed; no `HELLO`, `SELECT`, `CLIENT`, `COMMAND`, `CONFIG`, key read/scan, Lua, persistence operation or mutation is issued. The transport has no arbitrary-command interface exposed by HTTP or CLI.

An attempt has an absolute three-second deadline from its start, covering dial/DNS, optional TLS, authentication and response reads. Set socket deadlines after dial as well as a dial context; close the active connection when cancellation occurs. No retries within an attempt. Poll immediately after startup, then once every 15 seconds using a monotonic schedule; skip missed ticks and never overlap attempts. HTTP requests only read snapshots and cannot cause Redis requests.

Read at most 1 MiB of bulk INFO data, with bounded reply headers and INFO line lengths. Accept only the RESP types needed for AUTH/PING success and the INFO bulk reply; reject errors, malformed lengths, truncation, oversized responses and unexpected types without echoing raw bytes. Ignore unknown INFO fields, but reject duplicate selected fields and malformed selected values. Numeric values must be nonnegative bounded integers; state flags must be 0/1 and status strings exactly `ok`/`err`.

## Metrics contract

Every metric starts with `fasttowin_redis_monitor_`. Domain metrics have no labels. No IP, account, Redis key, namespace, username, endpoint, file path or raw INFO string is exported.

| Suffix | Type | Source / meaning |
| --- | --- | --- |
| `up` | gauge | Latest complete collection succeeded and is at most 45 seconds old: 1, otherwise 0 |
| `collection_duration_seconds` | gauge | Monotonic duration of the latest completed attempt |
| `last_success_timestamp_seconds` | gauge | Local wall-clock time of last success; 0 explicitly means never succeeded |
| `collection_errors_total{reason}` | counter | Fixed reasons: connect, timeout, auth, protocol, oversized, missing, invalid |
| `memory_used_bytes` | gauge | `used_memory` |
| `memory_max_bytes` | gauge | `maxmemory`; zero is valid Redis unlimited configuration, not a divisor |
| `evicted_keys_total` | counter | `evicted_keys` |
| `aof_enabled` | gauge | `aof_enabled` |
| `aof_current_size_bytes` | gauge | `aof_current_size` |
| `aof_base_size_bytes` | gauge | `aof_base_size` |
| `aof_last_write_ok` | gauge | `aof_last_write_status`: ok=1, err=0 |
| `aof_last_rewrite_ok` | gauge | `aof_last_bgrewrite_status`: ok=1, err=0 |
| `aof_rewrite_in_progress` | gauge | `aof_rewrite_in_progress` |
| `aof_pending_fsync` | gauge | `aof_pending_bio_fsync` |
| `aof_delayed_fsync_total` | counter | `aof_delayed_fsync` |

The core memory, eviction and AOF-enabled fields are required. When AOF is enabled, all selected AOF detail fields are required. When disabled, export `aof_enabled=0` and omit AOF detail fields that are absent; never invent zero sizes or successful status. Status `err` is a valid collected fault, not a collection failure. AOF size may fall after rewrite and is a gauge, not a counter. INFO AOF size is not filesystem free space or total volume usage. Memory-used/max is a diagnostic pressure indicator, not a guarantee that a write will succeed.

Publish a snapshot atomically after complete validation. On the latest failed attempt, export `up=0` and collector diagnostics only; omit all previous Redis-domain values. If a successful snapshot becomes older than 45 seconds, treat it identically as stale even if the poller hangs. Use monotonic time for age; wall-clock changes must not make old data fresh. Retain last-success time only as a diagnostic, never as current Redis data. Counter values come directly from Redis; do not accumulate across restarts or manufacture continuity.

## HTTP, errors and shutdown

- `GET /metrics`: Prometheus text format, HTTP 200 including diagnostic `up=0` on collection failure. Export valid HELP/TYPE metadata and finite numeric samples. No raw error text.
- `GET /health`: process liveness only, 200 while serving. Redis failure must not request a game-backend restart.
- `GET /ready`: 200 only with a current successful snapshot; otherwise 503 with a fixed message.
- Other paths: 404; other methods: 405. No debug, configuration, collection-trigger or credential endpoint.
- Allow at most 16 active HTTP connections, including header reads, and at most 16 handlers. Reject excess connections with a fixed 503 where possible, otherwise close them without a worker. Limit headers to 8 KiB, header/read/write deadlines to two seconds and idle time to ten seconds. Accept no request body. Requests must not hold the snapshot lock during a Redis operation. HTTP concurrency does not create collection workers.
- On termination, cancel the poller, close the active connection and gracefully stop HTTP within five seconds. No restart, repair, backup, rewrite or quota operation is performed.

Logs contain stable event/reason codes only. No raw exception, request URL, INFO response, config dump, credential, authentication frame or stack trace is printed. Tests inject distinct secret markers into every failure path and check output/logs. Local fixture artifacts containing synthetic secrets are owner-only and removed after their owning test.

## Existing monitoring integration

Add a self-contained opt-in local Compose fixture (Redis, collector, Prometheus, Alertmanager and Grafana; no application backend or PostgreSQL) and a separate configuration example for adding the collector to the existing monitoring stack. Reuse its pinned monitoring versions and provisioning conventions. Do not make the existing Compose stack require a new secret unconditionally: disabled Compose profiles can still cause configuration interpolation, so a separate file is the opt-in boundary. Preserve original backend-only monitoring when this option is absent. Load new Redis alert rules from a separate file only in the Redis-monitor configuration, so an unselected target does not trigger a missing-target alert in the default stack. Scrape the single configured collector target every 15 seconds with a five-second scrape timeout. Scope rules/dashboard selectors to job `fasttowin-redis-monitor`; no hard-coded production endpoint or credential is checked in.

Initial alert rules:

| Alert | Condition | Hold |
| --- | --- | --- |
| Collector missing | `up` for expected collector target absent/0, including no target series | 1 minute |
| Redis collection failed/stale | Exporter's own `up` absent/0 while collector scrape is reachable | 1 minute |
| AOF disabled | Fresh `aof_enabled != 1` | 1 minute |
| AOF write/rewrite failure | Fresh corresponding status gauge is 0 | 1 minute |
| Unexpected eviction | Increase of `evicted_keys_total` over five minutes is positive | 1 minute |
| High Redis memory | Fresh used/max ratio > 0.80 and max > 0 | 5 minutes |
| No memory limit | Fresh max is 0 | 1 minute |

Use `increase` for eviction, handling counter resets. Gate domain alerts on fresh collector success. Test absent targets, missing own-up samples, restart resets, stale suppression, the hold intervals and recovery. Existing backend alerts remain unchanged. No automatic response is attached to an alert.

Add a separate Redis dashboard provisioned by the existing Grafana mechanism: collector freshness/status, memory used/limit, AOF current/base size, write/rewrite status, eviction increase and delayed/pending fsync. Preserve existing dashboard panels. Show missing/stale values as unavailable rather than green/zero. Follow the project UI skill and existing visual conventions when implementing/rendering the dashboard.

The UI skill's focused missing-data searches returned no applicable database match. Dashboard guidance therefore uses its general accessibility/chart defaults and the existing Grafana theme: explicit text/state labels, visible units/legends, no color-only status, no interpolation across missing samples, and inspected desktop/narrow layouts. No unverified palette or chart recommendation is persisted.

Local Alertmanager remains dashboard-only. This increment does not claim that an email/phone notification has been delivered. The VPS Compose examples are not proof that Prometheus/Grafana already run on Railway; a later cloud plan must select where collection, storage, dashboards and notification delivery run and obtain its own access/cost approval.

## Local acceptance gates

1. Configuration/secret validation rejects missing, malformed, conflicting or unsafe values without leaks; username cannot become app/backup. Plain and certificate-verified TLS behavior are covered; failed certificate verification never falls back to plaintext.
2. A fake RESP peer records the exact AUTH/PING/INFO sequence. Test fragmented replies, errors containing secret markers, duplicate/missing/invalid fields, wrong types, overlong lines, invalid lengths, truncation, AOF off/error states, integer bounds and unknown fields.
3. Real loopback tests verify absolute deadline/cancellation across slow reads and TLS, clean connections, no overlap/catch-up storm, atomic snapshots, immediate failure invalidation, 45-second staleness and wall-clock jumps. Exporter HTTP errors/concurrent scrapes do not amplify Redis traffic.
4. Start only a uniquely labelled isolated Redis fixture using the already pinned official Redis digest and existing bootstrap, fresh synthetic role secrets, private test network/loopback access and a newly owned volume. Verify collector succeeds with unchanged `ftw_monitor` ACL; a separate fixture probe proves monitor key reads/writes, scan, CONFIG and destructive commands are denied. Never probe production or reset a shared Redis. Cleanup verifies exact labels/paths before removing owned resources.
5. Real Redis tests observe AOF-enabled metrics and persistence-size changes after isolated fixture writes; restart demonstrates counter/reset recovery without implying disk-failure tolerance. Injected responses test write/rewrite errors; do not claim those injections simulate a physical disk-full failure.
6. Promtool validates configuration/exposition and tests firing/resolution/missing-series/reset cases. Validate optional Compose paths and credential mounts. Record official tool/image versions and hashes used.
7. Render the local Grafana dashboard with synthetic/local Redis data on desktop and a narrow viewport, inspect screenshots and verify unavailable states. Run the isolated collector under its proposed cap and record actual resource behavior. If Docker/Grafana/runtime is unavailable, report that gate incomplete rather than converting a skip or mock into acceptance. Do not start paid/cloud resources as a substitute.
8. Prepare sanitized evidence and an operator handoff with remaining production/deployment/notification decisions. Local work does not push, run/check CI, publish an issue, read production secrets, deploy or create a scheduler. Any later requested CI follows AGENTS.md: all jobs, dispatch once, return link, then wait for the user.

## Sources and interpretation

- [Redis INFO](https://redis.io/docs/latest/commands/info/): field meanings and AOF-conditional fields. The metric allowlist, validation and failure rules above are project design decisions.
- [Redis RESP specification](https://redis.io/docs/latest/develop/reference/protocol-spec/): RESP2 requests/replies. This is a fixed monitoring exchange, not a general Redis client.
- [Prometheus exporter guidance](https://prometheus.io/docs/instrumenting/writing_exporters/): metric naming/types and distinguishing exporter reachability from target collection success. Snapshot polling and its 45-second cutoff are project decisions.
- [Go network API](https://pkg.go.dev/net#Dialer.DialContext): dial cancellation/deadlines, including name resolution. Subsequent connection deadlines and cancellation closure remain implementation requirements.

## Review and next step

The Owner approved the direction before this document was written. This concrete spec needs Owner review before the architectural implementation plan is prepared. After review, use writing-plans and then the normal isolated, test-first implementation workflow. Keep #12 presence/remaining local limits and #15 distributed game ownership separate from this collector.
