# Redis monitoring (local, opt-in)

The standalone collector in `tools/redis-monitor` authenticates as the fixed `ftw_monitor` identity and sends only AUTH, PING and `INFO memory persistence stats`. It polls immediately, then every 15 seconds without overlapping attempts or retries. Each attempt has a three-second absolute deadline including DNS/TLS/I/O. No game backend or PostgreSQL is part of this fixture.

The default Compose, Prometheus configuration, backend rules and existing panels are unchanged. `deploy/monitoring/redis-monitor/prometheus.integration.yml` is an **opt-in example**, retaining the existing backend and Prometheus jobs and adding one collector target plus the separate Redis rules. Mount the original `alerts.yml` and new `redis-alerts.yml` alongside that config. It does not activate automatically.

## Portable Windows prerequisites

Use the prepared checksum-verified portable tools recorded in `.artifacts/redis-monitor-local-20261103/toolchain.json`, `tools/prometheus-windows/provenance.json` and `tools/grafana-provenance.json`. This run used Go 1.27.3, Prometheus/promtool 3.11.3, Grafana 13.2.0, Node 24.21.0 and Docker Compose 5.3.1. The runner uses their recorded paths, installs nothing globally, and rebuilds native Windows and static Linux amd64 binaries from the current source. Public official container digests and manifest provenance are checked in at `deploy/monitoring/redis-monitor/image-provenance.json`; the distroless static nonroot image includes CA roots. The collector never disables TLS certificate verification.

From the repository root, on a machine with a working Docker daemon:

```powershell
./scripts/tests/redis-monitor-local.ps1 -Mode Docker
```

This creates a unique `run-<uuid>` directory with an owner-only Windows ACL, fresh independent 96-character lowercase hexadecimal app/monitor/backup/Grafana secrets, a private labelled network and Redis data volume. The collector image context contains only the static binary and its Dockerfile. Redis uses the existing pinned Dockerfile/bootstrap and unchanged ACL; only the monitor password file is mounted in the collector. Redis role credentials remain in the fixture's owner-only env file for bootstrap and are never printed. All published endpoints bind only to `127.0.0.1` with ephemeral host ports.

The real acceptance runner checks fixed monitor commands and denied GET/HSET/SCAN/CONFIG/FLUSHDB, writes only a UUID-named hash under the fixture namespace with its separate app identity, observes eventual AOF size growth, stops/restarts only its owned Redis and records recovery plus the source eviction counter. AOF logical size is not disk free space; physical file size need not change after every write. `noeviction` means this fixture does not manufacture real eviction traffic. Synthetic rule tests separately cover eviction increases, resets and recovery. It inspects the collector's 0.1 CPU / 64 MiB cap and records an actual `docker stats` sample. This does not prove physical disk-full, interrupted fsync, host-loss or backup durability.

Every container/network/volume/build-image removal checks the exact resource ID and unique ownership label first. The runner forces the local Windows Docker named pipe and an empty owned Docker config, avoiding stored registry credentials, remote contexts and cloud builders. There is no `prune`, shared database reset, WSL shutdown, Docker Desktop launch/repair or production fallback. Secrets are removed in `finally`; `docker-result.json` preserves sanitized results and any incomplete cleanup flags. If interrupted forcibly, retain the named directory and IDs for inspection; remove only resources whose `fasttowin.monitor-run` label matches that run. Do not run broad cleanup commands.

## Independent native dashboard fixture

When Docker is unavailable, the following runs the actual collector against a **synthetic fixed RESP peer**, plus native Prometheus and Grafana. It is UI/protocol integration evidence only, never real Redis ACL/AOF/container acceptance:

```powershell
./scripts/tests/redis-monitor-local.ps1 -Mode Native
```

The runner prints its owned artifact directory and loopback endpoints. Children launch with hidden windows, exact PIDs/executable paths are saved in `processes.json`, and native data/logs stay within that owner-only directory. Grafana exposes a local anonymous Viewer for synthetic dashboard inspection and a fresh synthetic admin password; no real login/account is used. In a separate terminal, substitute the printed directory:

```powershell
$fixture = '.artifacts/redis-monitor-local-20261103/run-<printed-uuid>'
node scripts/tests/redis-monitor-native-check.mjs $fixture healthy
node scripts/tests/redis-monitor-native-check.mjs $fixture fault
node scripts/tests/redis-monitor-native-check.mjs $fixture recovery
node scripts/tests/redis-monitor-native-check.mjs $fixture unreachable
# Stop the owned native fixture after screenshots/checks:
Set-Content -LiteralPath "$fixture/stop" -Value stop
```

The `unreachable` phase stops only the collector child originally created by that fixture. The owning process closes the peer and its exact remaining children on stop, records `cleanup.json`, removes synthetic secret files and Grafana's synthetic local authentication database, and preserves sanitized evidence. Close only the browser tabs opened for these localhost endpoints. Native Windows processes do not establish the Linux resource-cap or race-detector gates.

## Metrics, freshness and dashboard

All samples use prefix `fasttowin_redis_monitor_`; Redis-domain values have no labels. Counters come directly from Redis and may reset after restart. Collection failures immediately remove domain samples; successful snapshots older than 45 monotonic seconds also become unavailable. The last-success wall timestamp is diagnostic and is never evidence of current freshness. Absent fields are not replaced with zero. AOF-disabled details can be absent. `memory_max_bytes=0` is unlimited, not a valid divisor.

The separate **Fast To Win · Redis** dashboard is discovered by the existing Grafana provisioning directory. Current stat queries are instant queries, gated by both collector scrape reachability and authoritative own-up. Missing/stale/down values display **Unavailable**, never a retained last healthy value. Historical charts retain earlier healthy data with gaps; interpolation across missing data is disabled. Units, legends and text statuses complement color. Narrow layout uses Grafana's vertical panels; long chart headings retain their full accessible names.

Collector HTTP defaults to `127.0.0.1:19121`; only the container example explicitly binds inside its private network. `/health` indicates process liveness, `/ready` requires fresh Redis data, and `/metrics` remains HTTP 200 with diagnostic own-up=0 after failure. It has no command/config/debug endpoint. Limits: 16 active connections/handlers, 8 KiB headers, two-second header/read/write timeout, ten-second idle timeout, no request body, shutdown within five seconds. Never wire Redis collection failure to a game-backend restart.

## Rules and checks

Redis rules are loaded only by the opt-in configuration and scoped to `job="fasttowin-redis-monitor"`. Collector missing/down and failed/stale collection hold for one minute. AOF disabled/write/rewrite faults, unexpected eviction increase over five minutes and unlimited memory hold for one minute. Memory pressure above 80% holds for five minutes and requires a positive configured limit. All domain rules require both own-up and a reachable scrape; retained samples after scrape-down cannot trigger domain faults. The unit fixture covers absent collector/own-up, scrape-down, stale suppression, AOF disabled/absent detail, faults/recovery, eviction reset/recovery and memory holds/unlimited.

```powershell
node --test scripts/tests/redis-monitor-package.test.mjs scripts/tests/redis-monitor-peer.test.mjs scripts/tests/redis-monitor-probe.test.mjs
$promtool = '.artifacts/redis-monitor-local-20261103/tools/prometheus-windows/promtool.exe'
& $promtool check config deploy/monitoring/redis-monitor/prometheus.local.yml
& $promtool check rules deploy/monitoring/redis-monitor/redis-alerts.yml
& $promtool test rules deploy/monitoring/redis-monitor/redis-alerts.test.yml
# Native checker also pipes actual collector exposition to promtool check metrics.
```

Alertmanager remains **dashboard-only**: no email/webhook/phone delivery is configured or claimed. Production location, private networking/TLS, secret delivery, storage/retention, capacity/cost, external notifications and deployment authorization remain later decisions.

## Acceptance status — 3 November 2026

Offline Compose isolation, official digest resolution, native builds, actual promtool checks and synthetic native desktop/narrow healthy→failed→recovered→unreachable dashboard checks are separate from the outstanding real runtime gates. Docker Desktop is blocked by its existing runtime socket failure; the single permitted repair failed and was stopped. No further launch/repair was attempted. Real Redis ACL/AOF/restart, Linux container/resource-cap and graceful signal shutdown measurements, and Go race acceptance remain **incomplete**. Windows fixture child termination proves cleanup rather than POSIX graceful shutdown. There was no C compiler for the race detector. See `.superpowers/sdd/2026-11-03-redis-monitor-local/task-3-report.md` for commands, hashes, screenshots and cleanup. This is not complete production rollout acceptance and does not authorize cloud operations, CI, push, merge or a scheduler.
