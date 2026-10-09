# Task 2 — explicit compensation rounds, preview, publication and reports

Status: DONE. Recovered and finished on 09/10/2026 in the existing authorized `codex/admin-foundation` checkout. Task 1 was consumed from its documented transaction/store/API contract; it was not reimplemented. No subagents were dispatched by this implementer.

## Commit and final identity

- Base: `42840c357806c7b69ba35c048ebbd9e6c624fbcb`.
- Owned source commit: `fa0a5f7f1ac9fd364296522ee7401910cb925531` — `feat: publish auditable incident compensation rounds` (13 allowlisted source/test/contract paths).
- A report-only commit follows that source commit. The full final Task 2 HEAD and report commit are resolved after committing this file and retained in `.artifacts/admin-compensation-delivery/task2/final-head.json`; a report cannot embed its own commit hash. This file and final response identify the source commit independently.
- The controller's uncommitted `docs/superpowers/specs/2026-10-08-admin-compensation-design.md` clarification was excluded from staging. Unrelated `.tmp`, debug/e2e artifacts and skill pycache were preserved and excluded.

## Implemented behavior

Typed list/detail/create/update/preview/publish/cancel routes exist under `/admin/api/incidents/{incidentId}/rounds`. Exact requests, response properties, types/defaults, pagination, replay and reporting semantics are documented in `docs/admin-compensation.md` for Tasks 3/4 to consume. Enabled-admin wiring is in Main/Application; early incident CORS responses receive no-store.

Round numbering is explicit and unique, with at most one draft per incident. New-request duplicate round 1 conflicts. Creation requires expected max+1; supplemental rounds require a separate reason; cancelled numbers remain used. Committed exact retries return the original typed result and never allocate another round. Incident/campaign optimistic versions, request/reason validation and current role/session/Origin/CSRF apply before authoritative mutation/replay. Support reads only, Operator creates/updates/previews, Owner publishes/cancels. Advisory authorization lock then incident/mapping/campaign ordering uses the existing single transaction. Claim/delivery/cancellation retain the existing campaign/recipient/user/stats path; there are no nested or suspending GiftService calls inside it.

Shared synchronous `GiftCampaignOperations` owns the extracted gift normalization/audience/preview/detail helpers. Ordinary gift behavior and its 4000 body limit remain the same. Compensation uses a private server-generated 5200 envelope with the complete bilingual incident description, receiving reason and exact ISO UTC half-open interval. The extremal 4000+1000 copy in both locales is tested through publication, mailbox and an old reader ignoring unknown metadata; no truncation or client bypass is introduced.

ALL_REGISTERED and verified USER_IDS are supported. ACTIVITY explicitly returns 400 `ACTIVITY_SOURCE_UNAVAILABLE` because the existing mutable session/match sources do not establish complete retained incident activity. It is never treated as zero recipients or inferred from registration/platform/app version. Preview returns selected source/normalized explicit conditions, safe sorted sample <=10, invalid/duplicate diagnostics, decimal-string counts/totals, and three separate coherent previous-paid facts. Its token binds the incident, mapping/campaign definitions and versions, selected/invalid IDs and one prior receipt summary. Changes in registration, incident or prior claims conflict with publication until a fresh preview.

Publication freezes recipients, content, rewards, context, audit and immutable replay atomically on the existing gift transaction. Audit/freeze failures roll back every write. First publication permanently freezes incident edits; cancellation does not unfreeze it. Later registered accounts cannot claim that round. Common claim endpoints award exactly one COMPENSATION wallet transaction per user/round source UUID; explicit supplemental rounds intentionally permit another payment. Active x2 events do not multiply reward or leaderboard earned counters.

Receipt reports are decimal strings and retain paid facts after account deletion. Restart/batch delivery preserves checkpoints and reports deleted recipients as skipped. Claimed assets remain intact through cancellation; pending/unclaimed entitlement cancellation is reported separately from overlapping delivered/claimed counters. Detail reads current reports; retries retain the original report snapshot.

## TDD and recovery evidence

The previous worker completed the initial RED/implementation before hitting its quota. This resumer inspected the existing sources and the task brief/recovery/constraints/spec; it did not reconstruct RED, replace the implementation or repeat Task 1.

Authoritative original RED: `.artifacts/admin-compensation-delivery/task2/red.log` and `red-xml/test/` contain **7 compiling assertion failures, 0 errors, 0 skips** (round service 6, early CORS 1). Recoverable Gradle task is `:server:test`. The exact original invocation/filter arguments were not retained through the quota recovery, and neither worker recovery notes nor controller had them; they are explicitly unavailable and are not guessed here. `red-source-hashes.json` identifies the then-permissive scaffold source/test bytes. Expected failures include duplicate round creation succeeding, concurrent creation returning 2 successes instead of 1, ACTIVITY succeeding, missing explicit number/reason succeeding, retry generating different results, prior distinct count returning 0 instead of 1, and missing CORS no-store. The older `red-xml/` root snapshot has 5 failures and is a separate earlier run, not added to the authoritative 7.

The initial previous-count fixture used durable claimedAt markers, not actual awards. That was subsequently strengthened to real GiftService claims in `green-actual-claims.log`/XML and remains actual claims in final memory/PG tests. `paid-prior-red.log` was a failed retroactive DTO-compilation attempt; it is **not behavioral RED** and is excluded. Outside-frozen account exclusion and full PostgreSQL lifecycle coverage are later GREEN tests, not claimed as independently demonstrated original RED scenarios. No new missing behavior was identified in this resumption; only DTO documentation and EOF whitespace were completed.

Recovered lifecycle PG gate: `lifecycle-pg-gate.log`/`lifecycle-pg-xml/`, 5 suites / 28 tests, 0 failures/errors/skips, including 10 real PG round tests. These are historical recovery evidence, not the final source identity.

## Final GREEN and exact commands

Final executed gate exit 0, `BUILD SUCCESSFUL in 43s`: **18 suites / 114 tests / 0 failures / 0 errors / 0 skips**. Actual PostgreSQL: round 10, store 8, existing gifts 17, maintenance repository 10 = **45 PG tests executed**. `:server:test` and `:server:installDist` executed after the final EOF-only correction. Compilation has no Kotlin warnings in the final log; runtime fixtures emit normal Ktor logs and expected rejected-request/rollback diagnostics, not test failures. Gradle plugin configuration tasks marked SKIPPED are not skipped tests.

| Scope | Tests |
| --- | ---: |
| Compensation (incident, source claim, rounds, PG store/rounds) | 42 |
| Existing gifts (memory, PG, routes, maintenance) | 26 |
| Admin routes/assets | 8 |
| Maintenance service/runtime/routes/HTTP/PG repository | 38 |
| Total | 114 |

Working directory: `D:/HienTX/Work/Android/FastToWin`, PowerShell. Exact retained execution script: `.artifacts/admin-compensation-delivery/task2/final-gate.ps1`. It silently loads the existing credential file, performs authenticated SQL in the **same elevated command** as Gradle, removes password environment variables and preserves logs. Sequential escalation is for the retained local runtime/Gradle cache and local Git writes only.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets'
$taskSettings = Get-Content -LiteralPath '.artifacts/admin-postgres/test-settings.json' | ConvertFrom-Json
$env:TEST_DATABASE_URL = $taskSettings.url
$env:TEST_DATABASE_USER = $taskSettings.user
$env:TEST_DATABASE_PASSWORD = (Get-Content -Raw -LiteralPath $taskSettings.passwordFile).Trim()
$env:PGPASSWORD = $env:TEST_DATABASE_PASSWORD
& (Join-Path $taskSettings.bin 'psql.exe') -h 127.0.0.1 -p 15432 -U $taskSettings.user -d admin_test -c 'SELECT 1'
# Assert exit 0, remove PGPASSWORD, then in the same command:
& ./gradlew.bat :server:test --tests '*Compensation*' --tests '*Gift*' --tests '*AdminRoutesTest' --tests '*AdminAssetsTest' --tests '*MaintenanceRoutesTest' --tests '*MaintenanceHttpTest' --tests '*MaintenanceRuntimeTest' --tests '*MaintenanceServiceTest' --tests '*PostgresMaintenanceRepositoryTest' :server:installDist --console=plain
```

Actual final launch was `& ./.artifacts/admin-compensation-delivery/task2/final-gate.ps1`, whose full script includes exact redirection/error/finally handling. Authenticated readiness output is SELECT 1 returning one row. XML was copied before any later runs to `final-xml/`; `final-counts.json` retains suite detail. Source SHA256 and normalized Git blobs were captured before the final gate and checked unchanged afterward and against every staged blob before the source commit.

## Runtime diagnosis and preserved artifacts

The first fresh readiness check on 09/10 refused loopback 15432, before Gradle ran (`pre-recovery-postgres-readiness.log`). Elevated CIM found no postgres.exe, pg_ctl **status** reported no server running, pg_isready reported no response. The existing bin/data resolved inside `.artifacts/admin-postgres`. One Hidden direct `postgres.exe` launch used those same preserved paths (`-D existing-data -h 127.0.0.1 -p 15432`) with separate Task 2 stdout/stderr logs. Up to 20 half-second readiness probes preceded authenticated SELECT 1 and Gradle in the same elevated command. No competing pg_ctl start, initialization, data reset or deletion occurred. Test mutations/cleanup use the existing isolated UUID-schema policy only.

Final artifacts are local and intentionally not staged:

- `final-xml/`, `final-counts.json`, `final-scoped-gate.log`, `final-postgres-readiness.log` — final executed test/SQL evidence.
- `owned-paths.json`, `final-source-hashes.json` — all 13 owned file SHA256 and normalized Git blobs; unchanged through the final gate.
- `final-evidence-hashes.json` — per-XML/log/script/count/source/package SHA256 identity manifest.
- `final-package-hashes.json`, `final-package-entries.txt` — server/protocol jars, required round/helper classes and retained V57 migration packaged.
- `final-head.json` — source/report commits and resolved final Task 2 HEAD after the report-only commit.

SHA256 anchors:

```text
final-source-hashes.json   616cfd2ff1d50249ada89325ad3703342a6a68c1bbc9d634233cd96cdee16f00
final-evidence-hashes.json a0c7b29edde59854f85cc838fa09808cfb51d9f446cf26a3920e1eee01d91907
final-scoped-gate.log      c635000cae91ef01cf4517024f774f94abfb6df294e3979be24b00317386c40d
installed server.jar      9a8f9f95089ba54c1684555d345515f3cd3f744925ca0c78029d60d8777e3c74
installed protocol-jvm.jar b22ce6b8742b4270a157cf86fdef27e6054341bb26f3aa36152d639d5e4c7d86
```

## Owned files and self-review

Added CompensationRoundService/Routes/GiftContent, GiftCampaignOperations and round service/routes/PG tests. Extended CompensationModels (default-null mapping audit facts plus typed round DTOs), Main/Application, shared-helper consumers in GiftService, incident CORS regression test and exact admin-compensation contract. The staged name set exactly matched `owned-paths.json`; staged blobs exactly matched final tested normalized blobs. Staged `diff --check` passed after two new-file EOF blank lines were removed and the gate rerun. No migration or protocol/shared/client/UI source changed in this task.

Self-review checked explicit numbering/replay semantics, current auth after lock wait, incident-before-campaign admin lock order, claim/cancel lock compatibility, no nested transactions, atomic audit/freeze/replay rollback, frozen public projections, coherent prior paid counts, receipt deletion resilience and unchanged generic gift invariants. No remaining implementation defect was identified. The controller owns fresh independent spec/quality reviews; this report is not their approval.

## Limits and handoff

Historical exact RED command/filter reconstruction is unavailable as stated above. Protocol source/context did not change relative to the Task 1 base, so protocol tests were not repeated; the server compiled/packaged the unchanged protocol dependency. No localization/client/scanner input changed, so no unchanged client localization scan was repeated; affected admin asset security behavior was exercised by AdminAssetsTest. No whole game engine/browser/Compose, physical device, iOS, load, provider, production compensation, push, deployment, merge or shared-data reset claims are made. Tasks 3/4 consume the typed contract in `docs/admin-compensation.md`. GitHub progress and fresh independent reviews remain controller-owned.
