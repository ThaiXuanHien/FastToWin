# Task 1 delivery report — compensation incident and wallet foundation

Status: IMPLEMENTED AND LOCALLY VERIFIED; ready for controller's fresh spec/quality review. This is a partial Issue 20 delivery, not production round authority or UI acceptance.

Actual starting BASE: `27e3b6804649cc4572c66937328271b765c6b78c`, branch `codex/admin-foundation`. Owned source commit: `e19b92823bcd99e39af5a3c16e9f707d13d0419b` (`feat: add compensation incident and wallet foundation`). A following report-only commit records this file; its identity is available in git history and the controller handoff. No other worker edited source during this task.

## Delivered contracts

The complete incident/capability HTTP, concrete DTO and store contract is in `docs/admin-compensation.md`, owned by the source commit. The actual declarations are `compensation/CompensationModels.kt` and `CompensationStore.kt`. No guessed Task 2 round HTTP DTOs were added, as confirmed by the controller.

Incident service methods: `list(GiftAdminContext, PageRequest): CompensationPage<CompensationIncident>`, `detail(GiftAdminContext, id: String): CompensationIncident`, `create(GiftAdminContext, CompensationIncidentRequest): CompensationIncident`, `update(GiftAdminContext, id: String, CompensationIncidentRequest): CompensationIncident`, `capabilities(GiftAdminContext): CompensationCapabilities`. All are suspend; authorization/read/mutation occur inside the existing gift transaction. Public synchronous `requireOrigin(String?)` guards mutation parsing.

Implemented HTTP: GET/POST `/admin/api/incidents`; GET/PUT `/{id}`; GET `/capabilities`. Default page25/max100; bounded strict application/json, admin cookie/current role/session, exact mutation Origin/CSRF, no-store, safe 400/401/403/404/409/503 errors. All mutations and immutable replay receipts commit with audit. Incident key is canonical/unique/immutable, fields use specified UTF-16 unit bounds, affected interval is exact half-open UTC, responsible account is active when assigned, version increments safely, creator remains stable, and any persisted first publication freezes all incident edits. Existing admin-off-switch routing is retained; injected incident service cannot register routes when admin is disabled.

`GiftTransaction.val compensation: CompensationStore` binds JDBC to the SAME Connection and memory to the SAME copied state. No nested/suspending gift calls, connections or commits are added in the store. Exact methods:

```kotlin
fun incident(id: String, lock: Boolean = false): CompensationIncident?
fun incidentByKey(key: String): CompensationIncident?
fun incidents(page: PageRequest): CompensationPage<CompensationIncident>
fun putIncident(incident: CompensationIncident)
fun round(campaignId: String): CompensationRound?
fun rounds(incidentId: String, page: PageRequest): CompensationPage<CompensationRound>
fun putRound(round: CompensationRound)
fun maximumRoundNumber(incidentId: String): Int
fun hasDraftRound(incidentId: String): Boolean
fun hasPublishedRound(incidentId: String): Boolean
fun replay(actorId: String, requestId: String): CompensationReplay?
fun putReplay(actorId: String, requestId: String, replay: CompensationReplay)
fun previousClaims(incidentId: String, beforeRoundNumber: Int,
    selectedUserIds: List<String>): CompensationPreviousClaims
```

Mapping is `CompensationRound(campaignId, incidentId, roundNumber, supplementalReason?, receivingReasonVi, receivingReasonEn)`. Campaign owns reward/audience/schedule. Persisted compensation context must match mapping incident/number. One draft check, immutable identity, max number, descending pagination and first-publication evidence are available to Task 2. No deletion/automatic number allocation is exposed.

Private non-DTO `CompensationReplay(fingerprint, responseType, internal responseJson)` is insert-only, shared by actor/requestId across incident and future round operations; current authorization precedes replay. Incident responseType is `INCIDENT`; complete raw request and operation/target bind the fingerprint. `CompensationPreviousClaims(distinctUsers: String, entitlements: String, selectedOverlap: String)` uses one receipt query, does not join current users, and distinguishes previously paid users from multiple-round entitlements and selected overlap. New page/count transport uses decimal strings.

Persisted optional default-null `CompensationMailboxContext` contains exactly incident UUID/key, round number, affected bounds and VI/EN receiving reasons. Shared claim/delivery handles both sources. Both memory and PG derive wallet source solely from stored campaign context: COMPENSATION when non-null, legacy ADMIN_GIFT otherwise. Generic gift admin list filters compensation; detail/update/preview/publish/cancel reject before disclosure/replay. Generic bodies cannot select source. Player projection contains no internal cause/evidence/responsible identity. Existing maintenance/expiry/received-time/cancellation/overflow/restart behavior remains in the shared engine.

Migration `V57__compensation_incident_foundation.sql` adds source index plus incident, round and actor/request replay tables. V47/V50 applied migrations are untouched. V47 excludes COMPENSATION from earned gold/gems; there is no lifetime-earned-XP column. Actual claim tests prove exact XP balance credit, exact gold credit, unchanged earned gold/gems and independence from an active x2 event.

ACTIVITY capability is unavailable with a coverage/retention explanation. No last-seen/registration/platform inference occurs. Forged ACTIVITY targeting mutation rejection with `ACTIVITY_SOURCE_UNAVAILABLE` belongs to Task 2's targeting routes; Task 1 exposes no targeting mutation route.

## RED/GREEN evidence

Artifact root: `.artifacts/admin-compensation-delivery/task1` (local ignored evidence retained before overwriting Gradle outputs).

The first run encountered a Gradle wrapper cache lock permission failure. `environment-gradle-lock.log` records that infrastructure failure; it is NOT RED. There were no missing-type or compiler failures counted as RED. Additive protocol/campaign declarations and a clearly identified permissive incident service scaffold let the assertions compile without implementing incident/store authority or changing either hardcoded award path.

`red.log`, `red-xml/`, `red-source-hashes.json`: 4 tests executed, 4 assertion failures, zero skips. Failures: memory compensation fixture claim expected COMPENSATION but got ADMIN_GIFT; normalized key duplicate accepted; invalid affected interval accepted; identical actor/request returned a different incident. Both hardcoded award paths were still unchanged from BASE. The claim failure precedes the production source fix. The scaffold returned a fresh definition without persistence/validation and was completely replaced for GREEN.

`green-first.log`, `green-first-xml/`: those 4 compensation tests passed. Protocol JVM executed 97 tests with zero failures/errors/skips, including missing-context null decoding and legacy ignore-unknown reader compatibility. Later authorization/rollback/privacy/routing/store tests extend coverage; they were not claimed as separate initial REDs.

`scoped-gate-1.log`, `scoped-gate-1-xml/`, `scoped-gate-1-counts.json`: first complete scoped pass, 50 server tests, 0 failures/errors/skips; PG compensation8 + legacy PG gifts17 actually executed; protocol unchanged/UP-TO-DATE, server packaging successful.

Self-review found ordinary campaigns could be inserted as incident round mappings, incorrectly implying publication history. Controller approved the scoped persisted-context invariant. `round-source-red.log`, `round-source-red-xml/`, `round-source-red-hashes.json`: one compiling semantic RED, expected 409 rejection but ordinary mapping was accepted. Memory/PG stores now require matching persisted compensation context. The existing memory freeze fixture was updated to persist compensation metadata rather than treating an ordinary campaign as a valid compensation round. The PG legacy test also rejects ordinary mapping and mismatched compensated round number.

`final-scoped-gate.log`, `final-xml/server/`, `final-counts.json`: final source ran 51 server tests, 0 failures, 0 errors, 0 skipped. Actual PostgreSQL suites executed 8 compensation + 17 existing gift tests. New PG suite requires TEST_DATABASE_URL and cannot silently skip. `final-xml/protocol/` and `final-protocol-counts.json` retain 97 passing protocol tests executed at initial GREEN; protocol source/test bytes were unchanged afterward and final gate reported UP-TO-DATE. `:server:installDist` executed successfully after the final source change.

| Final server suite | Executed | Failed/errors/skipped |
| --- | ---: | --- |
| AdminRoutesTest | 6 | 0/0/0 |
| CompensationGiftClaimTest | 1 | 0/0/0 |
| CompensationIncidentRoutesTest | 3 | 0/0/0 |
| CompensationIncidentServiceTest | 7 | 0/0/0 |
| PostgresCompensationStoreTest | 8 | 0/0/0 |
| GiftMaintenanceTest | 2 | 0/0/0 |
| GiftRoutesTest | 5 | 0/0/0 |
| InMemoryGiftRepositoryTest | 2 | 0/0/0 |
| PostgresGiftRepositoryTest | 17 | 0/0/0 |

PG compensation covers concurrent exact-once claim/retry/reload, active x2 event isolation, wallet rollback, incident/audit/replay rollback, current operator role revoked while actually waiting on advisory lock, duplicate keys/mappings and one draft, publication/cancelled-publication freeze, prior receipt counts retained after account deletion, delivery checkpoint restart/deleted skips, player privacy, ordinary admin isolation and legacy source/earned totals. Existing PG gift receipt/concurrency/overflow/maintenance/claim-cancel/delivery assertions remain intact and passed. Current lock-wait test confirms PostgreSQL wait_event_type=Lock before changing the current role; it does not infer waiting from a sleep.

## Exact scoped commands and environment

Working directory was `D:/HienTX/Work/Android/FastToWin`, PowerShell. Gradle required sequential sandbox escalation for the existing user cache; no credentials were printed.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets'
# Initial RED (exit1 assertion failures after environment-only failed attempt):
& ./gradlew.bat :server:test --tests '*CompensationGiftClaimTest' --tests '*CompensationIncidentServiceTest' --console=plain
# Initial GREEN, including executed protocol tests (exit0):
& ./gradlew.bat :server:test --tests '*CompensationGiftClaimTest' --tests '*CompensationIncidentServiceTest' :protocol:jvmTest --console=plain
# Supplemental mapping RED (exit1 assertion failure):
& ./gradlew.bat :server:test --tests '*CompensationIncidentServiceTest.round mapping rejects ordinary gift authority*' --console=plain
# First complete and final complete gates used this exact command (both exit0):
& ./gradlew.bat :server:test --tests '*Compensation*' --tests '*Gift*' --tests '*AdminRoutesTest' :protocol:jvmTest :server:installDist --console=plain
```

Each command redirected all Gradle streams to its named artifact log, captured `$LASTEXITCODE`, displayed only the log tail and returned that code. XML was copied before subsequent runs. PostgreSQL credentials were loaded silently in each scoped PG gate:

```powershell
$settings = Get-Content .artifacts/admin-postgres/test-settings.json | ConvertFrom-Json
$env:TEST_DATABASE_URL = $settings.url
$env:TEST_DATABASE_USER = $settings.user
$env:TEST_DATABASE_PASSWORD = (Get-Content -Raw -LiteralPath $settings.passwordFile).Trim()
$env:PGPASSWORD = $env:TEST_DATABASE_PASSWORD
& (Join-Path $settings.bin 'psql.exe') -h 127.0.0.1 -p 15432 -U $settings.user -d admin_test -c 'SELECT 1'
if ($LASTEXITCODE -ne 0) { throw 'Existing SQL runtime unavailable' }
Remove-Item Env:PGPASSWORD
# Run scoped Gradle command above in this SAME elevated command.
Remove-Item Env:TEST_DATABASE_PASSWORD
```

Initial and final SQL readiness output `1` is retained in `postgres-readiness.log` and `final-postgres-readiness.log`. Existing UUID-schema test policy was read from PostgresGiftRepositoryTest; this task creates only `compensation_test_<UUID>` schemas and cleans up only those isolated fixture schemas. No shared accounts are reset or deleted.

## Local PostgreSQL runtime diagnosis

The retained configured bin/data paths were checked under this workspace `.artifacts/admin-postgres`. Initial pg_ctl status and pg_isready reported stopped/no response. A Hidden pg_ctl launch recovered preserved data and logged PID26296 ready at19:42:21. Subsequent sandbox probes reported no response while elevated netstat/CIM showed that exact process and listener. A retry `pg_ctl start` then logged new PID22312 failing port bind at19:44:44. The original process subsequently logged missing postmaster.pid and immediate shutdown at19:45:21. No agent command intentionally removed that file; failed concurrent-start automatic lockfile handling is a possible cause, not a proven data reset or a deliberate deletion. Process visibility alone was not accepted as test readiness.

The successful recovery used one Hidden direct process launch, followed by loopback readiness and authenticated SELECT1 in the same elevated command:

```powershell
Start-Process -FilePath (Join-Path $settings.bin 'postgres.exe') `
  -ArgumentList @('-D',$settings.data.Replace('\','/'),'-h','127.0.0.1','-p','15432') `
  -WindowStyle Hidden `
  -RedirectStandardOutput D:/HienTX/Work/Android/FastToWin/.artifacts/admin-postgres/compensation-task1-runtime.out.log `
  -RedirectStandardError D:/HienTX/Work/Android/FastToWin/.artifacts/admin-postgres/compensation-task1-runtime.err.log
```

Successful readiness was checked with up to20 half-second pg_isready probes before SELECT1. The final gate reused the running process and performed SELECT1 before Gradle, with no helper restart. No initialization/reset command, recursive cleanup, unrelated schema/account mutation, provider call or production action was performed. Runtime remains local and preserved; future runners must freshly verify SQL readiness instead of trusting the earlier PID.

## Ownership, self-review and identities

`owned-paths.json` is the explicit21-file source/test/docs allowlist. Staged paths were compared exactly against it before committing. Only this report is added in the following report-only commit. Preexisting untracked `.tmp/`, debug.log, e2e artifacts and skill pycache remain excluded. `git diff --check` and staged diff check passed. Self-review checked source authority, current auth before replay, same transaction/no nested connection, mapping identity and draft/publication checks, safe projection, decimal counts, unchanged V47/V50 and exact scoped evidence. Controller performs independent fresh review.

`final-source-hashes.json` maps all21 final owned source paths to SHA-256 bytes. `final-xml-hashes.json` maps all retained XML to SHA-256. `migration-package-hashes.json` records V47/V50 unchanged identities, new V57 and packaged server.jar. These are local working-file byte hashes, not Git blob hashes. Source commit tree is authoritative for normalized Git contents. A post-commit source hash verification checks the same21 bytes before handoff.

Migration/package SHA-256:

- V47: `8CB668885A3A2F1D5FEDF1F0F2DDA0E5CC693B9C62BA75AF111BCB5C962860FE`
- V50: `E26AB3C1350CD26B33DEEFDC583B6DD3A08B481A8F98BFC7A32BC01294F5410D`
- V57: `61DCEBC598BF6D41ABF7755469D881087FA264E7E735B290421A2AB47701B6B5`
- final packaged server.jar: `AA894EC9CA8805E0D6D63A599A2B57387376706841B8839A4F2BFC94B125623E`

## Limits and next task

Task 2 must add round workflow service/HTTP/DTOs, explicit supplemental numbering, audience validation/ACTIVITY rejection, immutable public body generation with exact UTC interval, preview token binding of incident/round/audience/prior-claims, Owner publish/cancel and decimal-string report counters. All fixture compensated campaigns here are pure persisted test data, not production creation/publication endpoints. Browser/Compose/phone/desktop/iOS/load/provider/production/push/deploy/merge/GitHub gates were deliberately not run for this backend-only stage. No independent notification enum/delivery loop was introduced. Test evidence establishes this foundation and retained common gift invariants only.
