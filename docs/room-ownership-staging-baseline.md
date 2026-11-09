# Private room ownership staging baseline

This is the next bounded step for [#15](https://github.com/ThaiXuanHien/FastToWin/issues/15) after the private Railway smoke and local fault/load acceptance. The baseline implementation and Docker rehearsal were approved on 09/11/2026. A fresh paid Railway execution requires approval of the concrete launch scope after local verification. The earlier smoke window has expired.

## Runner contract

Use the existing test-only entry point `RailwayRoomSmokeKt` with `FTW_PILOT_SUITE=baseline`. The default remains the two-account/two-backend smoke. Baseline requires exactly 40 distinct synthetic accounts, the exact FastToWin Staging project/environment and a run-specific `room-pilot-<12 hex>-runner` service. Its deadline must have at least 900 seconds remaining and be no more than two hours away. `--seed` uses the same suite and account allowlist without backend targets.

Only the matching fixture PostgreSQL hostname and backend A hostname are accepted. Backend B must not be configured in the baseline runner. DNS must resolve exclusively to distinct private addresses. The runner rejects external database overrides and Railway management tokens. All provider management stays on the host through the existing authenticated CLI.

The baseline reuses `runRoomOwnershipLoad` without changing its gates: one backend, 20 rooms, 600 measured seconds, five selections per room per second, exactly 60,000 acknowledged selections, at least 95 selections/s and the existing bounded measurement tail. It verifies boards, identity, sequence, scoring and economy reconciliation. Synthetic acceptance rate caps and quota funding are the same as the local fixture. Backend A must set `FASTTOWIN_ROOM_OWNERSHIP_ACCEPTANCE=true` together with the development pilot configuration. This does not measure normal public admission limits.

The runner writes `receipts/load.json` and exports the same bytes as indexed `FTW_BASELINE_RECEIPT` log chunks with a SHA-256 checksum. `scripts/read-room-baseline-receipt.ps1` reads plain logs or Railway JSON log lines, tolerates identical duplicate/reordered chunks, rejects missing/conflicting/corrupt chunks, validates the full baseline, and writes the verified receipt. The export contains numeric samples and fixed-label metrics rather than fixture accounts, credentials or raw game payloads. Collect the entire chunk set, using bounded log pagination if needed; the older smoke controller's last-60-lines query is insufficient for the maximum receipt size. A summary line alone does not satisfy the baseline gate.

## Reproduce the private DNS rehearsal

Build and test the test-only runner with the repository JDK/Gradle setup. Supply the reviewed backend context and its expected manifest checksum to:

```powershell
./scripts/room-ownership-staging-baseline.ps1 `
  -BackendContext '.artifacts/room-ownership-staging-20261109/backend-f7097dd' `
  -BackendManifest '.artifacts/room-ownership-staging-20261109/backend-f7097dd-manifest.json' `
  -BackendManifestSha256 'b68e22087967dd3658532b6d48f49d6bdf85c0d905d89e4f47bd5737979489cc'
```

The script validates the frozen backend, verifies that the runner uses the identical server JAR, snapshots all seven implementation sources including untracked files, and records every runner file hash. Docker calls explicitly pin the validated local named pipe or Unix socket. The fixture uses pinned JRE/PostgreSQL/Redis digests and a private IPv6-only Docker network with the Railway DNS names, without host ports. Both backends start so their configured peer DNS resolves; B stops before baseline traffic. Cleanup removes only this generated project's containers, networks, volumes and labelled images, and independently verifies their absence.

This local run validates the package and private DNS path. It does not apply Railway's CPU/memory limits or establish cloud capacity.

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

Collect and reconstruct the complete receipt, record deployment IDs/status, limits and context checksums, and scan the export against generated fixture IDs/passwords. Cleanup runs on success or failure: stop all new deployments, clear their generated variables, delete only the five newly created service IDs after project/name checks, and verify they are absent. Existing staging services remain untouched. The read-only inventory on 09/11/2026 found all nine existing staging services without active deployments.

Cloud fault injection and the two-backend load profile remain later steps. This baseline package does not close those gates or authorize production rollout.
