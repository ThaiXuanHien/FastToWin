# Final grouped fix report — I1 admin maintenance off switch

Completed 2026-10-08 by `/root/events_final_offswitch_fix`. This is the one authorized final source fix wave, scoped to Important I1 in `final-review.md`. The four Minor groups remain deferred. A scoped independent rereview remains the coordinator's next step; this report is not that rereview.

## Identity and changes

- Branch: `codex/admin-foundation`.
- Actual fix BASE: `71648160e2ee826b2e3df10fdb229c1b4d53b041`.
- Implementation commit: `06a1d05a4600d019ebb1ec4edf99f9c2e2d4832f` (`fix: honor disabled admin maintenance routes`).
- The following report-only commit contains this report and no further source change; its parent is the implementation commit above. Its own identity is available from the containing Git commit rather than a self-referential hash.
- Owned source files: `server/src/main/kotlin/com/hienthai/fastowin/server/Application.kt` and `server/src/test/kotlin/com/hienthai/fastowin/server/maintenance/MaintenanceRoutesTest.kt`.

Application now registers administrative maintenance routes only when `adminService != null`. The supplied maintenance service, runtime initialization, public status provider, observation workers, transitions and engine admission remain intact. This closes the Main-shaped case where durable maintenance storage is configured but administrative services are disabled, as required by `docs/admin-management.md:3` and `:8`.

The new test injects a real MaintenanceService with `admins = null`, a test-only memory repository containing an ANNOUNCING snapshot at version 7, and the retained MaintenanceRuntime/GameEngine into `gameModule`. It asserts GET and POST `/admin/api/maintenance` both return 404, effective/public policy stays available and unchanged, and both `/status` aliases return the expected status with 200 and `no-store`. Advancing the clock then explicitly refreshes the runtime, persists ANNOUNCING → DRAINING → MAINTENANCE at version 9 for an empty drain, closes new-intent admission and retains 200/no-store public status.

The wall clock is AtomicLong-controlled and monotonic time is fixed. Before advancing time, repeated background observations are idempotent; after advancing, automatic and explicit observation converge to the same terminal policy. The route assertions always capture both responses before comparing them, so the RED does not depend on beating the background recovery worker. No sleeps, production test hooks or scheduler timing assertions were added.

## Diagnosis and genuine RED

Read the final-review I1 finding, constraints and the management contract; traced Main's nonnull service/admin-null wiring through Application registration, MaintenanceRoutes and MaintenanceService. The incorrect registration permits GET authorization to enter the storage wrapper, mark its shared observation unavailable on absent admins, and return 503. POST returns 503 at its absent-admin Origin check. The supported fix is the route-registration gate, rather than removing the durable service.

Wrote the regression before the production change. The first sandbox launch could not write the existing Gradle wrapper cache lock; retained `red-sandbox-launch.log` and its exit record. This was an environment launch failure, not behavioral RED. The subsequent authorized Gradle run compiled `:server:compileTestKotlin` and executed the new test against the unchanged production registration:

- Exit 1; **1 test / 1 failure / 0 errors / 0 skips**.
- Exact assertion: `expected: <[404 Not Found, 404 Not Found]> but was: <[503 Service Unavailable, 503 Service Unavailable]>`.
- Saved the RED XML and source hashes before running GREEN.

## GREEN and self-review

The one-line production guard was then applied. Fresh scoped Gradle execution exited 0 (`BUILD SUCCESSFUL`): **35 tests / 6 suites / 0 failures / 0 errors / 0 skips**, parsed from the actual XML and copied before any subsequent run.

| Suite | Tests |
| --- | ---: |
| AdminRoutesTest | 6 |
| MaintenanceRoutesTest | 4 |
| MaintenanceServiceTest | 16 |
| MaintenanceRuntimeTest | 5 |
| MaintenanceHttpTest | 3 |
| MaintenanceObservationConcurrencyTest | 1 |

Enabled route authorization, bounded/invalid bodies, exact Origin, CSRF, replay/conflict behavior, unavailable storage and no-store checks remain green. Service/runtime/HTTP coverage also includes durable transitions, environment status, fail-closed observation, recovery and admission. The retained HTTP 503s in GREEN are expected fail-closed scenarios asserted by existing tests, not test failures.

Self-review checked the complete two-file diff against I1 and the off-switch contract. Removing the new guard reproduces the captured RED; dropping the supplied runtime/service would fail the independently specified initial public policy or later transition/admission assertions. The change does not touch service authorization, repository behavior or public route registration. `git diff --check` and staged diff checking exited 0; only owned paths were explicitly staged. Git emitted its existing LF/CRLF normalization notices. The Gradle experimental Android screenshot option warning remains; this is not warning-free evidence.

## Commands and retained evidence

Working directory: `D:/HienTX/Work/Android/FastToWin`. Both runs used:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets'
```

RED:

```powershell
./gradlew.bat :server:test --tests com.hienthai.fastowin.server.maintenance.MaintenanceRoutesTest.disabledAdminKeepsDurableRuntimeWithoutRegisteringMaintenanceRoutes --console=plain
```

GREEN:

```powershell
./gradlew.bat :server:test --tests com.hienthai.fastowin.server.maintenance.MaintenanceRoutesTest --tests com.hienthai.fastowin.server.maintenance.MaintenanceServiceTest --tests com.hienthai.fastowin.server.maintenance.MaintenanceRuntimeTest --tests com.hienthai.fastowin.server.maintenance.MaintenanceHttpTest --tests com.hienthai.fastowin.server.maintenance.MaintenanceObservationConcurrencyTest --tests com.hienthai.fastowin.server.admin.AdminRoutesTest --console=plain
```

Evidence directory: `.artifacts/admin-reward-events-delivery/final-fix/`. Retained `red.log`, `green.log`, exit records, `red-command.txt`, `green-command.txt`, `red-xml/`, `green-xml/`, parsed `red-summary.json`/`green-summary.json`, `red-source-hashes.json`/`green-source-hashes.json`, `source.diff`, diff-check evidence and `evidence-hashes.json`. Logs were redirected there and XML copied before overwrite. These ignored artifacts are preserved locally, not staged into the commit.

| Tested file | RED SHA-256 | GREEN SHA-256 |
| --- | --- | --- |
| Application.kt | `6425f0151388258a18b2d74a7ef44515f9068c14b5553cf21613c69074c49ac1` | `a1fc4c49ca4e7ed5d9be2edfd0a59d45641249d4014ddb7b4cca85c6793892dc` |
| MaintenanceRoutesTest.kt | `5310011b74a705d69e69fe96b0a6d41a96aade5022f5887392c41a903d150317` | `5310011b74a705d69e69fe96b0a6d41a96aade5022f5887392c41a903d150317` |

## Limits and disposition

This verifies local Ktor wiring with real maintenance service/runtime and transaction-equivalent test-only memory storage. No PostgreSQL run is required for this registration/memory regression; the report does not claim new database, production/load or proxy verification. Unchanged full admin/browser/platform suites were not rerun. No UI changed, so no new screenshot acceptance was needed. No GitHub write, push, merge, deployment, settings change or data reset occurred. Preexisting untracked/ignored files were preserved. I1 has implementation and scoped RED/GREEN evidence; the four Minor groups remain deferred and no additional source fix wave was attempted.
