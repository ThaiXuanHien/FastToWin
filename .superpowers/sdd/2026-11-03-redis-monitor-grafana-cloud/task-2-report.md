# Task 2: Cloud timestamp-aware rules and dashboard

Status: implemented and locally verified on 3 November 2026. No Cloud account, real token, production shipping, resource change, SSH, push, CI, merge, scheduler or external alert destination was used. No server or browser was started for this task.

## Delivered interfaces

- Standalone eight-rule variant: `deploy/monitoring/redis-monitor-cloud/redis-alerts.yml`, group `fasttowin-redis-cloud`.
- Native alert fixture: `deploy/monitoring/redis-monitor-cloud/redis-alerts.test.yml` (15 scenarios, 50 alert assertions).
- Self-contained import dashboard: `deploy/monitoring/grafana/dashboards/fasttowin-redis-cloud.json`, UID `fasttowin-redis-cloud`, title `Fast To Win · Redis Cloud`.
- Dashboard datasource input is `DS_PROMETHEUS`; every panel datasource UID is the literal `${DS_PROMETHEUS}`. Grafana import must resolve it to the selected private metrics datasource. A local file provisioner does not resolve import inputs automatically: Task 3 should substitute the synthetic datasource UID in its owned artifact copy before provisioning, leaving the shipped import file intact.
- Behavior test: `scripts/tests/redis-cloud-dashboard.test.mjs`. It reads and executes the actual shipped panel and rule expressions with native promtool. `PROMTOOL` can override the executable path; `REDIS_CLOUD_DASHBOARD` and `REDIS_CLOUD_RULES` permit artifact-only mutation inputs.

All selectors use the fixed `job="fasttowin-redis-monitor",instance="redis-primary"` contract. All current panels require own-up 1, scrape-up 1, and query-time scrape timestamp age in inclusive 0–60 seconds. Each history query uses the same guard at its historical evaluation step, preserves WAL source timestamps, and leaves gaps. Stat queries remain instant, missing values read `Unavailable`, AOF statuses retain text as well as color, memory retains byte units and `Unlimited`, and history retains legends and `spanNulls:false`.

The eight conditions preserve the source wording/severities and holds: one minute except high memory five minutes. Missing collection distinguishes absent/down/stale/future scrape samples from a reachable, current scrape with unsuccessful/missing collector own-up. Domain conditions are suppressed while end-to-end data is unavailable. Eviction uses reset-safe `increase`; unlimited maxmemory never becomes a ratio divisor.

## Actual RED evidence

Tests were written first. `.artifacts/task2-tests.mjs` created only artifact copies of the existing ungated local rules/dashboard, with fixed redacted selectors. The original local files were never modified.

Command (PowerShell):

```powershell
& '.artifacts/redis-monitor-local-20261103/tools/prometheus-windows/promtool.exe' test rules '.artifacts/redis-cloud-task2/red-rules.test.yml'
```

Actual exit 1. The test expected `FastToWinRedisCollectorMissing` at 2m15s after a stopped sender's last scrape at zero, but got no alert. Ungated AOF disabled/write/rewrite, unlimited-memory and eviction alerts incorrectly remained firing. Delayed-recovery tests also found domain holds already firing instead of restarting. Output retained at `.artifacts/redis-cloud-task2/red-rules.txt` (ignored local evidence).

Dashboard command before implementation:

```powershell
$env:REDIS_CLOUD_DASHBOARD='.artifacts/redis-cloud-task2/ungated-dashboard.json'
node --test scripts/tests/redis-cloud-dashboard.test.mjs
Remove-Item Env:REDIS_CLOUD_DASHBOARD
```

Actual exit 1: 0 passed, 2 failed at that stage. Native PromQL returned the old own-up value 1 and old domain values at evaluation t=76s after the last scrape t=15s, where the expected result was empty. The second failure correctly detected the missing Cloud UID/input. Output retained at `.artifacts/redis-cloud-task2/red-dashboard.txt`.

## Actual GREEN verification

Executable: native Windows/amd64 promtool 3.11.3, revision `eb173f5256d4022afba1e9bc3d19740a76859fae`, existing verified tool/provenance under `.artifacts/redis-monitor-local-20261103/tools/prometheus-windows/`.

```powershell
& '.artifacts/redis-monitor-local-20261103/tools/prometheus-windows/promtool.exe' check rules 'deploy/monitoring/redis-monitor-cloud/redis-alerts.yml'
```

Actual output: `SUCCESS: 8 rules found`, exit 0.

```powershell
& '.artifacts/redis-monitor-local-20261103/tools/prometheus-windows/promtool.exe' test rules 'deploy/monitoring/redis-monitor-cloud/redis-alerts.test.yml'
```

Actual output: `SUCCESS`, exit 0. The 15 scenarios / 50 assertions cover absence, scrape failure, missing/zero own-up, retained stale values, AOF off and missing details, AOF write/rewrite failures, recovery and one-minute holds, eviction increases/reset/recovery, high-memory five-minute hold/recovery, unlimited memory, sender death inside the ordinary lookback window, and delayed recovery restarting the domain hold.

Final scoped behavior/config command:

```powershell
node --test scripts/tests/redis-cloud-dashboard.test.mjs scripts/tests/redis-cloud-config.test.mjs
```

Actual result: 7 tests, 7 passed, 0 failed, exit 0. The three new tests are:

1. `shipped queries evaluate boundaries, outages, WAL age, future samples and recovery using real promtool` — 196 direct PromQL expected-result assertions against all 15 dashboard targets, including all eight current stats and historical target expressions. Cases include age zero/exactly 60/61 seconds, scrape zero, own-up zero/missing, old WAL timestamps, future-only data, negative timestamp age, gaps/recovery and reset-safe counters.
2. `each shipped rule expression rejects stale and negative ages using real PromQL` — 51 direct PromQL assertions against all eight shipped expressions, including active AOF/eviction/memory conditions, unlimited memory, and failed own-up.
3. `cloud import resolves a selected datasource and renders explicit text states with units and gaps` — import substitution and status/unit/history contracts. This checks the dashboard definition, not rendered pixels.

The other four passing tests verify the unchanged Task 1 destination/target validation and fixed bounded allowlist pipeline.

Future timestamp qualification: normal instant selectors exclude future samples, which is covered directly. To exercise the defensive negative-age branch, query tests pin only the timestamp source with `@ 120` while evaluating at t=60; a t=120 probe ensures promtool loads that future sample. No host clock was changed. An initial fixture attempt omitted the later probe and therefore loaded no future data; the fixture was corrected without weakening the expected-empty assertion. A later fixture extension accidentally set synthetic scrape-up to 10; its continuation was corrected to preserve each metric's last value. These were test-fixture corrections, not production rule changes.

## Self-review and remaining gates

Self-review checked the exact fixed selectors, all eight holds/severities, absent/down/stale/future handling, positive maxmemory requirement, counter reset semantics, all 15 target guards, datasource import input, instant current stats and historical gaps. Changes are confined to the four new Task 2 files and this report. Core collector, Task 1 runtime, local rules/dashboard, default Compose and game modules are unchanged.

The UI skill was read and its local chart guidance queried using the already installed portable Python runtime. The verified relevant recommendation was status text in addition to color; the existing readable stat/history layout, byte units and legends were retained. Desktop/narrow actual Grafana rendering belongs to Task 3 and was deliberately not claimed here.

An unnecessary broader regression command also included `scripts/tests/redis-cloud-launch.test.mjs`: 8/10 combined tests passed, but its two container success-path tests failed before launcher execution with `permission denied while trying to connect to the docker API at npipe:////./pipe/docker_engine`. The fail-closed launcher test passed. No Docker repair/escalation or launcher changes were attempted. This environment limitation does not affect native promtool or the passing required pure configuration/dashboard checks; it remains explicit for the parent task.

Old-WAL tests here verify timestamp/query semantics, not real transport receipt or queue replay. Task 3 owns local receiver/Alloy/Grafana integration and desktop/narrow screenshots. Actual Cloud private access, selected datasource/stack UID, managed-rule support/Free limits, rule loading and production delivery remain future account-specific gates. No alert delivery destination is configured or promised.
