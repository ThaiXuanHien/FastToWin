# Private room ownership staging baseline

This is the next bounded step for [#15](https://github.com/ThaiXuanHien/FastToWin/issues/15) after the private Railway smoke and local fault/load acceptance. The baseline implementation and Docker rehearsal were approved on 09/11/2026. A fresh paid Railway execution requires approval of the concrete launch scope after local verification. The earlier smoke window has expired.

## Runner contract

Use the existing test-only entry point `RailwayRoomSmokeKt` with `FTW_PILOT_SUITE=baseline`. The default remains the two-account/two-backend smoke. Baseline requires exactly 40 distinct synthetic accounts, the exact FastToWin Staging project/environment and a run-specific `room-pilot-<12 hex>-runner` service. Its deadline must have at least 900 seconds remaining and be no more than two hours away. `--seed` uses the same suite and account allowlist without backend targets.

Only the matching fixture PostgreSQL hostname and backend A hostname are accepted. Backend B must not be configured in the baseline runner. DNS must resolve exclusively to distinct private addresses. The runner rejects external database overrides and Railway management tokens. All provider management stays on the host through the existing authenticated CLI.

The baseline reuses `runRoomOwnershipLoad` without changing its gates: one backend, 20 rooms, 600 measured seconds, five selections per room per second, exactly 60,000 acknowledged selections, at least 95 selections/s and the existing bounded measurement tail. It verifies boards, identity, sequence, scoring and economy reconciliation. Synthetic acceptance rate caps and quota funding are the same as the local fixture. Backend A must set `FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE=true` together with the development pilot configuration. This does not measure normal public admission limits.

The runner writes `receipts/load.json` and exports the same bytes as indexed `FTW_BASELINE_RECEIPT` log chunks with a SHA-256 checksum. `scripts/read-room-baseline-receipt.ps1` reads plain logs or Railway JSON log lines, tolerates identical duplicate/reordered chunks, rejects missing/conflicting/corrupt chunks, validates the full baseline, and writes the verified receipt. The export contains numeric samples and fixed-label metrics rather than fixture accounts, credentials or raw game payloads. Collect the entire chunk set, using bounded log pagination if needed; the older smoke controller's last-60-lines query is insufficient for the maximum receipt size. A summary line alone does not satisfy the baseline gate.

Failures now write `receipts/baseline-failure.json` and emit `FTW_BASELINE_FAILURE` chunks using the same checksum/index scheme. Read them with `scripts/read-room-baseline-receipt.ps1 -Failure`. This is diagnostic evidence, never an acceptance pass. It includes a fixed phase/category, final measured room counts/attempts, four allowed database counts and selected numeric gauges/counters. Exception text, stack traces, SQL, dynamic labels, unknown keys, non-finite metric values and oversized diagnostic files are excluded. The local private stack can still be inspected separately during profiling; it is not included in the log export.

## Reproduce the private DNS rehearsal

Build and test the test-only runner with the repository JDK/Gradle setup. Supply the reviewed backend context and its expected manifest checksum to:

```powershell
./scripts/room-ownership-staging-baseline.ps1 `
  -BackendContext '.artifacts/room-ownership-staging-20261109/backend-f7097dd' `
  -BackendManifest '.artifacts/room-ownership-staging-20261109/backend-f7097dd-manifest.json' `
  -BackendManifestSha256 'b68e22087967dd3658532b6d48f49d6bdf85c0d905d89e4f47bd5737979489cc'
```

The script validates the frozen backend, verifies that the runner uses the identical server JAR, snapshots the baseline implementation and verification sources including untracked files, and records every runner file hash. Docker calls explicitly pin the validated local named pipe or Unix socket. The fixture uses pinned JRE/PostgreSQL/Redis digests and a private IPv6-only Docker network with the Railway DNS names, without host ports. Both backends start so their configured peer DNS resolves; B stops before baseline traffic. Cleanup removes only this generated project's containers, networks, volumes and labelled images, and independently verifies their absence.

This local run validates the package and private DNS path. It does not apply Railway's CPU/memory limits or establish cloud capacity.

To reproduce the cloud resource envelope locally, add `-RailwayLimits`. The override uses the exact decimal-byte memory limits and disables swap. Add `-Profile` to collect Docker resource samples, numeric PostgreSQL statistics/query costs and a backend JFR. The profile retains the frozen JVM heap and OOM flags, disables initial environment/system-property/JVM-command-line recording, and uses the same JRE's profile settings. Keep raw recordings private, retain only bounded numeric/class-name summaries after analysis and remove the raw files. Sampling and final collection are best effort; available runner diagnostics are copied before cleanup even if telemetry fails.

For local controls only, `-Profile -PostgresCpu 2` changes PostgreSQL from 0.5 to 2 CPU; `-Profile -PostgresCpu 2 -BackendCpu 2` then changes backend A from 1 to 2 CPU. `-BackendCpu 3` provides the next backend-only control. These retain the original RAM limits, rate caps, application JAR and full load gates. The summary records the altered envelope as `local-cpu-control`; these switches do not grant a new cloud run or change the approved cloud scope. A run without `-RailwayLimits` is explicitly labelled `uncapped-local` and does not claim applied CPU ceilings. Verify each comparison's actual runtime ceilings. Avoid oversubscribing the Docker host while comparing controls.

On Windows, a long temporary path can make JDK 25's AF_UNIX selector initialization fail before Gradle runs. Use a short process-local `TEMP`/`TMP` directory inside the workspace if that specific error occurs; no machine-wide Java or networking setting needs changing.

### Local DB latency reproduction

Add `-CloudDatabaseLatency` together with `-RailwayLimits -Profile -PostgresCpu 2 -BackendCpu 3` to reproduce the measured DB-path sensitivity. A digest-pinned Toxiproxy on the private IPv6 network adds fixed downstream delay of 1 ms for backend DB traffic and 3 ms for fixture DB traffic, with zero jitter. The override replaces PostgreSQL's direct fixture alias, assigns that strict hostname to the proxy, and uses separate internal ports for the two paths. No proxy or database port is published. The host records the active toxics and verifies the proxy's own 1-CPU / 128-MB ceiling alongside the original application/DB limits. See [Toxiproxy latency semantics](https://github.com/Shopify/toxiproxy#latency) and [Compose alias replacement semantics](https://docs.docker.com/reference/compose-file/merge/#replace-value).

This is a reproducible latency stress model, not an exact Railway replica: proxy/timer scheduling and the local host can add overhead beyond the requested delay. Compare the measured preflight and workload timings, not just the configured milliseconds. Keep rate caps, account counts and resource limits fixed between source comparisons.

For a shorter local screening loop, add `-DiagnosticOnly`. A separate test entry point runs the same workload for 60 seconds and emits `baselineAccepted=false`. The host records `diagnosticOnly=true`, requires exactly 6,000 ACKs / 300 per room for a completed diagnostic, and keeps `baselineAccepted=false` even when collection succeeds. Use the receipt reader's `-Diagnostic` mode for those files. A partial/timeout diagnostic remains failed; a completed diagnostic reports its observed rate without claiming the full-load gate. The normal reader and Railway entry point retain the strict 600-second / 60,000-ACK contract. Always verify the full profile after screening changes.

## Concrete Railway launch package

The prepared scope is `.artifacts/room-ownership-staging-20261109/baseline-launch-scope.json`. It pins the frozen backend and runner manifests and names five new fixture services in project `b5c5db71-2df8-46da-84fb-d9b9df6d7951`, environment `8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9`. Resource ceilings are:

| Role | vCPU | GB RAM | Lifecycle |
| --- | ---: | ---: | --- |
| PostgreSQL | 0.5 | 0.512 | Fresh ephemeral marked fixture database |
| Redis | 0.25 | 0.25 | Fresh fixture namespaces |
| Backend A | 1 | 1 | Sole backend during measurement |
| Backend B | 1 | 1 | Bootstrap peer DNS, stop before measurement |
| Runner | 0.5 | 0.5 | Seed, then one full baseline attempt |

Memory values use the Railway limits API's decimal GB unit.

Use one replica per service, no public domains/TCP proxies, no permanent volumes and no production resources. The maximum rehearsal window is two hours from launch. Record the exact UTC deadline before creating resources. The runner has a separate 900-second process timeout. Stop early on any correctness/readiness/export gate failure. A failed full attempt requires a new decision before retrying it.

Before upload, recheck all context hashes, project/environment identity, existing staging inventory and available deadline. Create only the five named services, record their returned IDs, set limits/restart policy/private settings, and verify their identities before each mutation. Generate all accounts, passwords and peer secrets fresh on the host. Seed the empty dedicated database with the runner, deploy both backends, prove readiness, then stop B and verify its deployment is stopped before launching baseline with only A configured. Do not pass a workspace token to the runner.

During backend bootstrap use the provider healthcheck `/health`. The `/ready` route is registered only when pilot ownership is enabled, so switch the provider probe to `/ready` for the pilot deployments. A `REMOVING` state is pending, not stopped. Railway can retain a `SUCCESS` deployment in `activeDeployments` after setting `deploymentStopped=true`; inspect that flag on each retained record and on the latest deployment rather than interpreting the collection name as proof of running compute.

Collect and reconstruct the complete receipt, record deployment IDs/status, limits and context checksums, and scan the export against generated fixture IDs/passwords. Cleanup runs on success or failure: stop all new deployments, clear their generated variables, delete only the five newly created service IDs after project/name checks, and verify they are absent. Existing staging services remain untouched. The read-only inventory on 09/11/2026 found all nine existing staging services without active deployments.

Cloud fault injection and the two-backend load profile remain later steps. This baseline package does not close those gates or authorize production rollout.

## Execution result from 09/11/2026

The concrete scope was approved and executed. After correcting the bootstrap healthcheck before any load, both pilot backends passed readiness and B stopped before the single full baseline attempt. That attempt failed during load; the last progress sample was 20,949 selections at 588 seconds, approximately 35.63/s against the 95/s gate. No accepted full load receipt was produced and no second full attempt was started.

PostgreSQL memory reached 0.507465728 GB against its confirmed 0.512 GB ceiling. This is an observed capacity signal; the exact bottleneck is not isolated. The existing exception sanitizer preserved only the failed phase, and the runner's local detailed diagnostics were not recovered. A follow-up should export bounded numeric failure counts/metrics and a safe error category, then profile at the cloud resource ceilings locally before requesting another cloud run.

All five temporary services were deleted. Independent inventory verification found the nine prior staging services unchanged and without active compute. See `docs/room-ownership-staging-handoff.md` for the evidence paths and limitations.

## Local capacity follow-up

Safe failure export and four capped local profiles followed, without another cloud run. The original 1-CPU backend / 0.5-CPU PostgreSQL envelope reproduced low throughput and a room-recreation timeout. PostgreSQL 2 CPU alone reached only 28,743 ACKs by the 630-second timeout. Backend 2 CPU / PostgreSQL 2 CPU reached 59,957 ACKs; it still failed because all 60,000 scheduled selections are mandatory.

Backend 3 CPU / PostgreSQL 2 CPU passed the full profile: 60,000 ACKs in 604.321 seconds at 99.285/s, 1,200 matches and zero selection retries. All RAM limits stayed unchanged. CPU throttling and the response to the controls identify backend CPU headroom as a demonstrated local constraint. Cache-inclusive PostgreSQL memory readings do not isolate a RAM bottleneck. The full comparison, profiling limitations and cleanup evidence are recorded in the handoff. A future Railway run with higher ceilings requires a fresh scope and approval; the old 1/0.5-CPU cloud scope is unchanged.

## Timing instrumentation for the next diagnostic build

The pilot repository records fixed-size numeric aggregates for connection acquisition, transaction body, statement execution, row reading/decoding, commit and rollback. Metrics use the fixed prefix `fasttowin_room_ownership_db_<stage>_seconds_` and suffixes `count`, `sum`, `max`, `p95_upper_bound` and `failures_total`. The p95 value is a histogram bucket upper bound, not an exact percentile; no samples or exception/SQL text are retained. `fasttowin_room_ownership_available_processors` reports the JVM's effective processor count.

The batched implementation adds `setup_and_first_statement` for mutation transactions' first SQL batch and `read_transaction` for a complete single-statement read-only transaction. The legacy standalone `transaction_setup` series remains present with zero samples. Transaction body now includes the batched setup, execution and decoding; read batches also include the server-side commit, while the separate `commit` series counts explicit mutation commits. These series overlap and must not be summed. Compare old setup-plus-body with the new body when interpreting connection hold time, and treat old/new stage means as different measurement scopes.

Single-statement read paths send fixed transaction-local timeouts, an explicitly read-only query and commit in one parameterized pgjdbc batch. The result set is decoded after the read-only commit; decoding cannot commit application writes. A guard rejects a second helper query or update on this path. Mutation transactions batch only setup and their first statement, retain their original lock order and callback/commit decision, and rollback on failure. First-query lock/statement timeout tests and connection-idle checks cover both error paths. Full command reads retain replay replies; only delivery projections omit them.

The runner performs twelve bounded probes after verifying the exact fixture marker/account allowlist and readiness. Each borrows its existing connection pool, executes `SELECT 1`, performs a same-value marker update guarded by that fixture UUID, and commits a real WAL-producing transaction. It then measures a complete `/ready` HTTP response. The marker value and player/game/economy data remain unchanged; the marker's physical row version and WAL necessarily change. These are preflight round trips, not measurements of production command SQL.

`baseline-latency.json` and indexed `FTW_BASELINE_LATENCY` chunks contain probe counts/failures and exact small-sample mean/max/p95 milliseconds, plus numeric backend metrics before load and the fixture's own workload connection-acquisition/body timing aggregates. Fixture body includes its SQL and verification/decoding work; it is distinct from the backend transaction timings. Read with `read-room-baseline-receipt.ps1 -Latency`. Partial probes are marked incomplete; a successful load does not become a latency-probe pass automatically. The host verifies both file/log checksums and field/metric allowlists.

Backend metrics are process-lifetime counters. Use before/after count and sum differences only if counters remain monotonic; flag a process restart rather than calculating a misleading delta. Maxima and percentile bucket gauges are not subtractable. Existing full-load counts, deadlines and correctness gates are unchanged. This build needs fresh frozen backend/runner contexts and local verification before another separately approved cloud run.

The timing build at `39965f3` has now passed the capped local 3/2-CPU full rehearsal: 60,000 selections in 601.488 seconds (99.753/s), 1,200 matches and zero retries. All twelve probes per stage completed without errors; both file/log exports matched and all owned Docker resources were removed. Evidence is `.artifacts/room-ownership-staging-baseline/room-pilot-6a235121b3a8/diagnostic-summary.json`; exact build/receipt checksums and timing interpretation are recorded in the handoff. This verifies the diagnostic package locally. No new cloud run was performed, and the older cloud controller/scope predates the latency export and new metric allowlist.

## Prepared 3/2-CPU Railway scope

The next concrete package is `.artifacts/room-ownership-staging-20261109/baseline-launch-scope-cpu3.json`, SHA-256 `9aaff477c558d98419ea7e0a44b2a799024074ba98ae18422a0a129bba4d4535`. It names only five new `room-pilot-3e56bfb2be37-*` services in the same isolated staging project/environment. PostgreSQL is capped at 2 CPU / 0.512 GB, backend A at 3 CPU / 1 GB, bootstrap backend B at 1 CPU / 1 GB, Redis at 0.25 CPU / 0.25 GB and runner at 0.5 CPU / 0.5 GB. B stops before measurement. The package allows one full 20-room/600-second baseline, forty synthetic accounts, no fault injections, no public endpoints or permanent volumes, and a maximum two-hour window with cleanup on success or failure. Existing services and production remain untouched.

The backend is the same frozen package; the runner uses the accepted local manifest `c709166957490f2da94433efb30ad10132cb824abb91ba949aba4195d4203b28` from source checkpoint `6a4c7f8`. The host controller `railway-baseline-cpu3.mjs` now validates and retains complete safe failure exports before cleanup, preserves the corrected healthcheck lifecycle, and permits only one execution. Its 17 offline checks and read-only staging preflight passed; independent review found no important blockers. It has a separate owned-resource cleanup mode that verifies the scope and service/deployment identities.

Automatic approval review initially rejected launch before process creation because the 3/2-CPU ceilings exceeded the prior grant. The user subsequently explicitly approved the exact five-service scope; that approval is recorded in `.artifacts/room-ownership-staging-20261109/cpu3-explicit-approval.json`. The scope was executed once, starting at `2026-11-09T13:27:09.966Z` with the unchanged two-hour deadline `2026-11-09T15:27:09.966Z`.

## 3/2-CPU cloud result

The Railway run failed the load gate despite the accepted local capacity profile. It reached 25,772 acknowledged selections in 630.009 seconds, approximately 40.907/s, then exported a complete checksum-verified `load/timeout` diagnostic. All twenty room slots remained below the required 3,000 selections. No successful `load.json` was produced. The partial progress recorded 507 completed matches; database counts were 507 matches, 507 settlements and one pending settlement at cancellation. Those snapshots do not establish a full economy acceptance pass.

Observed rate-limit rejections, takeovers, fence rejections, resyncs and settlement-failure counters were zero. The final pool sample was 9 active connections out of 10 and queue age was 0.659 seconds. Provider metadata confirmed the requested resource ceilings and the same `sfo` region configuration for backend A, PostgreSQL and runner. The bottleneck is still not isolated; a locally successful CPU envelope does not reproduce cloud network, storage or CPU behavior.

Cleanup encountered a transient Railway HTTP 503 while inspecting backend A. The controller's scoped cleanup-only mode then removed the remaining backend without repeating the load. Independent verification found zero fixture services, the nine prior staging identities unchanged and without active compute, and the existing October `staging-postgres-volume` unchanged. There were no persistent-volume links to any fixture service and no new permanent volumes.

Evidence is `.artifacts/room-ownership-staging-20261109/cloud-room-pilot-3e56bfb2be37/result-summary.json`, with `baseline-failure.json`, `provider-resources-final.json`, `deployment-region-config.json`, volume checks and `independent-cleanup.json` alongside it. Failure receipt SHA-256 is `f931d94d895429e673594fa78aef73339dab789f52dd763fafa7eed5bd40e1de`. The next diagnostic should separate connection acquisition, SQL execution, commit and fixture round-trip time before another cloud baseline. This scope's single full attempt has been consumed; any further full cloud run needs a new approved scope.

## Instrumented cloud result

The new timing build was executed once under a fresh `room-pilot-cf82b07290e9-*` scope with the same 3/2-CPU envelope. It timed out after 41,599 ACKs in 630.007 seconds (66.029/s). Complete checksum-verified failure and latency exports were collected before cleanup; all twelve preflight probes completed without errors. Backend mean pool acquisition / setup / body / commit times were 2.727 / 1.641 / 2.736 / 0.944 ms; fixture workload DB body averaged 3.494 ms versus 0.391 ms locally. These measurements show increased DB-path time and contention, without isolating a single network/storage/query cause. Full counts and local/cloud comparisons are in the handoff.

Evidence is `.artifacts/room-ownership-latency-20261109/cloud-room-pilot-cf82b07290e9/diagnostic-summary.json`. All five new services were deleted, the nine prior identities and existing volumes were unchanged, and independent verification found zero active prior compute or remaining fixture services. No second attempt ran. The cloud load gate remains open; the next diagnostic work can reproduce DB latency locally and reduce measured round-trip costs before proposing another cloud scope.
