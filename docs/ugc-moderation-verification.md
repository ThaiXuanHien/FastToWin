# UGC moderation verification — #53

Recorded 2026-10-22–23 (Asia/Bangkok). Core regression source: `2fd6f3b`, based on
`336dd53` from Draft PR [#54](https://github.com/ThaiXuanHien/FastToWin/pull/54).
Draft implementation PR: [#55](https://github.com/ThaiXuanHien/FastToWin/pull/55).
This record covers source and isolated local verification. It does not certify
production, a physical device, a signed release or Google Play acceptance.

## Approved behavior and evidence

| Requirement | Implementation / verification |
| --- | --- |
| Explicit versioned consent before publishing UGC | `ContentEntryConsent`, `UgcController`, auth/guest integration; current version `2026-10-20`, unchecked consent, old-version rejection, guest restart and registered reconnect coverage. Viewing, login and deletion stay available. |
| Profile/avatar/clan/room/tournament reports | Server-derived reporter identity, authoritative typed target/revision, localized player entry points; no optimistic success before server receipt. |
| Abuse and retry controls | Service/route tests cover 5/10-minute and 20/day identity quotas, IP gateway limits, stable request ID, conflicting payload, open-report deduplication and stale revision. |
| Admin moderation | SUPPORT reads/triages; OWNER/OPERATOR close/remove/restrict. Routes recheck role/session/origin/CSRF inside coordinated transactions. No prechecked removal, stale-target protection or wallet changes. |
| Exact removal and account restrictions | Typed ID/revision projection on snapshots, history, notifications and future room/tournament pushes; avatar endpoint denial, active-session restriction, login/refresh denial and current-authority startup regression. |
| Retention and erasure | V62 PostgreSQL tests cover active retention, closed report/private-action expiry after 90 days, reporter and target-owner erasure, replay/suppression/restriction cleanup and unrelated-record preservation. Backup policy stays 14 days. |
| Live clients and draft privacy | Compatible-socket invalidation, refreshed read-only views, avatar cache invalidation, retained same-account edit/report drafts and identity isolation. Background refresh is not a save acknowledgement. |
| UI consistency | Existing dark game and white admin themes retained. Production Compose components rendered at phone/desktop, with small-width, landscape and 1.6 text-scale cases. Admin long-detail scrolling and keyboard access inspected in localhost fixture. |

## Final local regression

Windows, Android Studio JBR, Android SDK, one Gradle worker, no parallel build,
no emulator. `TEST_DATABASE_*` points to a private local PostgreSQL test instance;
tests create isolated schemas and clean up their own schemas. Credentials are
outside Git and are not included here.

```powershell
.\gradlew.bat :protocol:jvmTest :server:test :shared:checkLocalizedUiText :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :app:assembleDevDebug :webApp:compileKotlinWasmJs :webApp:compileKotlinJs :server:koverVerify :shared:koverVerify --max-workers=1 --no-parallel --no-daemon --no-configuration-cache '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process'
```

Result: **BUILD SUCCESSFUL**, 4m42s. XML results:

| Suite | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| Server (including real PostgreSQL) | 788 | 0 / 0 / 0 |
| Protocol JVM | 112 | 0 / 0 / 0 |
| Shared Android host | 325 | 0 / 0 / 0 |

Both unchanged Kover minimum-line gates passed (server 86%, shared 52%).
Localization validation, Android instrumentation compilation, dev APK assembly,
Web Wasm and JavaScript compilation passed. Compilation emitted the existing
Compose plugin/runtime version mismatch warning; no dependency upgrade was
folded into this feature.

Additional checks:

- `node --test scripts/tests/admin-moderation.test.mjs`: 4 passed, zero skips.
- `node --test scripts/tests/legal-worker.test.mjs`: 8 passed, zero skips.
- `scripts/tests/CaddyRouting.Tests.ps1`: both Caddy configurations passed,
  including 12 logged-out legal URLs each, no SPA fallback, protected API forwarding
  and retained CSP/no-store behavior.
- Admin Playwright configuration discovers 8 new moderation cases. These cases
  were **not executed** in this original verification; see the integrated local
  acceptance below for their subsequent execution. Discovery alone is not E2E evidence.

## UI rendering and browser fixture

All 15 new UGC previews rendered without initialization errors. Initial validation
failed because new reference images were absent; references were added only after
visual review, through the filtered Gradle update task. Existing 145 references
were not rewritten. The first full 160-image run caught an extra empty lazy-list
item in clan details when reporting was unavailable (157 passed, 3 failed).
The item is now omitted using the same availability rule as its button; the
original three baselines remain unchanged. A separate scoped source review found
no Critical/Important issue in that visibility guard. Final full validation:
**160 tests, 0 failures, 0 errors, 0 skips; BUILD SUCCESSFUL in 2m15s**.

```powershell
.\gradlew.bat :app:updateDevDebugScreenshotTest --tests 'com.hienthai.fastowin.screenshot.UgcScreenshotTestKt' --max-workers=1 --no-parallel --no-daemon --no-configuration-cache
.\gradlew.bat :app:validateDevDebugScreenshotTest --max-workers=1 --no-parallel --no-daemon --no-configuration-cache
```

Only the 15 new UGC PNGs were added. No prior reference file changed.
After the final visibility guard, localization validation, Android UI-test
compilation/APK assembly and Web Wasm/JS compilation were repeated successfully
in 1m26s (`.tmp/ugc-final-ui-client-compile.log`, local ignored artifact).
See [testing.md](testing.md) for the full baseline workflow.

The report preview deliberately includes `<b>Player nickname</b>` as literal text
to check that player content is not interpreted as markup. At 320dp/1.6 text scale
and short landscape height, dialog content scrolls; the initial viewport need not
show the bottom actions. These still require touch/keyboard acceptance on device.

Local admin fixture was inspected at 375px phone and 1280px desktop widths:
long report details scroll within the modal, no inner horizontal overflow, bottom
actions remain reachable, Escape/cancel return focus, and markup stays plain text.
This uses disposable fixture data, not production reports or real player accounts.

## Samsung component acceptance — 2026-10-23

Physical Samsung SM-A366B, Android 16, ADB device (no emulator). Four new
`UgcSafetyUiTest` instrumentation cases passed: 4 tests, 0 failures/errors/skips.
They exercise the real production dialogs/controller with isolated protocol
fixtures, not a running backend or real player accounts:

- Consent starts unchecked, requires a deliberate check and disables submission
  and checkbox while the request is pending.
- A typed report note survives disconnect; first outbound target/note are correct,
  retry reuses the entire request, and a receipt appears only after explicit ack.
- Vietnamese block confirmation renders player markup literally and invokes the
  block callback only on the explicit action.
- Self/missing report targets remain hidden, another player keeps the correct action.

```powershell
$env:ANDROID_SERIAL='<physical-device-serial>'
.\gradlew.bat :app:connectedDevDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.UgcSafetyUiTest' --max-workers=1 --no-parallel --no-daemon --no-configuration-cache
```

The first run found a test-harness scroll request on a non-scroll block dialog;
that call was corrected to assert visibility and click, without production edits.
The strengthened payload assertions passed on a fresh run (41s). A further 28s
run retained the Dev APK long enough to pull four fixture screenshots for visual
inspection; captures contain no live account data. The Dev package is separate
from the installed production app. This component result does not replace the
remaining staging/auth/avatar/moderation end-to-end device acceptance.

CI configuration was validated with checksum-verified actionlint 1.7.12 and an
independent review. No Critical/Important finding; automatic host jobs are
unchanged, emulator job is an explicit typed boolean opt-in, default false.

## Independent review and decisions

Core, server integration, player client and admin UI received separate reviews.
Final whole-feature review found a missing localization argument, split memory
admin authority and unsanitized future invitation pushes. All three were fixed
in `5626202` and the scoped rereview found no new Critical/Important defect.
The direct PostgreSQL target-owner erasure coverage was added in the same fix.

Release localization validation then found four literals. `2fd6f3b` preserves
legacy Vietnamese fallback text through the existing transparent marker, uses a
machine error identifier for the terms API, and supplies the required player
argument in the existing Friends block dialog. A separate scoped review found
no Critical/Important issue in these three changes. Full regression above includes
these corrections. The one-line Friends correction also fixes a confirmed
pre-existing failure in the separately required block flow.

Other deliberate decisions:

- Hot projection reads use read-only transactions without the global admin lock;
  mutations keep that lock. Invalidation/refetch and mutation revalidation address
  a simultaneous stale read.
- New invalidation events are negotiated through `GetContentTerms`. Legacy
  clients remain connected and receive sanitized later snapshots. An old binary's
  cached avatar may remain until refresh/update; compatible release is a rollout gate.
- Initial draft tips used `[skip ci]` to avoid the then-automatic emulator job.
  On 2026-10-23 the owner approved retaining automatic host/build/coverage/Web
  checks while making emulator UI tests an explicit, default-off manual input.
  Local results above are not a hosted CI verdict. Coverage thresholds and all
  host regression jobs remain unchanged; physical-device acceptance is separate.

## Isolated staging backend and CI follow-up — 2026-10-23

Paid Hobby active status and target guards were checked before starting the
existing staging services. Fresh synthetic database `ugc_acceptance_20261023`
preserves previous staging databases. Backend migration/startup and private
health passed; financial, push and production credentials are not used.

Over a loopback-only SSH tunnel, actual staging HTTP/WebSocket checks passed:
public terms/version, missing-consent rejection, receipt/replay/conflict,
unauthenticated queue rejection, invalid CSRF, SUPPORT triage versus denied
removal/restriction, stale version conflict, OWNER exact-profile removal,
live invalidation and sanitized refetch, active-session restriction/unblock,
and unchanged gold/Gem balances. A separate synthetic player was registered and
verified using the actual private Mailpit email, without reading OTP from SQL.
These are backend checks, not a claim that every client entry point was accepted.

Server distribution plus production Wasm/JS compatibility Web build passed in
17m49s. Context files were hashed; staging Web uses same-origin configuration
without production push configuration. Public staging HTTPS health returned OK.
Private runtime manifests/credentials and fixture screenshots stay outside Git.

Hosted run `39881330275` passed all screenshot and iOS build checks and skipped
the emulator job as intended. It found one server-test teardown failure:
`PostgresStorePurchaseRoutesTest` closed Hikari before outer `testApplication`
stopped its timer. The application scope now ends inside the pool lifetime;
all original race/receipt/wallet/safe-503 assertions are retained. Independent
rereview found no Critical/Important issue. After restarting the existing local
test PostgreSQL and waiting for crash recovery/readiness, the full server suite
passed again: **788 tests, 0 failures/errors/skips**, Kover gate passed, 3m11s.
The earlier local attempt while PostgreSQL was unavailable is not counted as green.
No production code, coverage threshold, or retry allowance was changed for this fix.
A new hosted result is still required for the corrected revision.

## Integrated local acceptance — 2026-10-23

Integrated reviewed legal/Billing commit `4f75eacda937fb32ff96ae441d0157e9e109eded`
into UGC at merge `cdd1290ec0f186a6b03bbe29e51f3036542431b5`. The only conflict
was the screenshot count in `docs/testing.md`. The initial integration mistakenly
committed its delimiters despite recording it resolved; the review follow-up removed
the delimiters and obsolete 145 line, retaining the current 160 references.
Billing mapping/configuration and app-version files have no differences against
that reviewed legal integration. No production UI or runtime code changed here.

A new owned PostgreSQL 17.11 cluster was initialized in the ignored
`.artifacts/play-integration-20261023/task2-pg-data` directory only after port
15432 was confirmed free. It listened on `127.0.0.1:15432`; `admin_test` and all
credentials were synthetic. Tests migrated their own UUID schemas through V62.
Existing PostgreSQL data directories and staging/cloud fixtures were not reused.
After verification, owned schemas and backend processes were removed/stopped and
the owned PostgreSQL cluster was stopped. No production settings were changed.

With `TEST_DATABASE_*` set to that owned database and the JBR/SDK/socket settings
above, executed:

```powershell
.\gradlew.bat :protocol:jvmTest :server:test :shared:checkLocalizedUiText :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :app:assembleDevDebug :webApp:compileKotlinWasmJs :webApp:compileKotlinJs :server:koverVerify :shared:koverVerify :server:installDist --max-workers=1 --no-parallel --no-daemon --no-configuration-cache '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process'
node --test scripts/tests/admin-moderation.test.mjs scripts/tests/legal-worker.test.mjs
.\gradlew.bat :app:validateDevDebugScreenshotTest --max-workers=1 --no-parallel --no-daemon --no-configuration-cache
```

Results: Gradle **BUILD SUCCESSFUL in 5m20s**, server **798**, protocol JVM **112**,
shared Android host **330**, each with **0 failures/errors/skips**. In particular,
`PostgresUgcIntegrationTest` ran **5** and `PostgresModerationRepositoryTest` ran
**8** real PostgreSQL cases, with no skipped DB tests. Both unchanged Kover gates
(server 86%, shared 52%), localization validation, Android instrumentation
compilation/APK assembly, server distribution and Web Wasm/JS compilation passed.
Node checks: **12 passed, 0 skipped**. Full screenshot validation: **160 passed,
0 failures/errors/skips**, **BUILD SUCCESSFUL in 2m**; no reference PNG changed.
Existing compiler/SDK/SLF4J warnings remain; no dependency upgrade was included.

### Eight fixture browser cases and one database-backed browser flow

From `e2e`, executed the existing admin Playwright command directly:

```powershell
node node_modules/playwright/cli.js test --config=admin.playwright.config.mjs tests/admin-moderation.spec.mjs --output=D:/HienTX/Work/Android/FastToWin/.artifacts/play-integration-20261023/task2-admin-fixture-results
```

**8 passed in 7.5s**, no skips: OWNER/OPERATOR/SUPPORT controls, literal evidence,
retry identity/current-version/CSRF, keyboard dismissal/focus and long-detail
scrolling at 375px/1280px. These use `admin-fixture.mjs`, an in-process synthetic
HTTP fixture serving production assets. They do **not** connect to PostgreSQL.

Added one opt-in flow, `e2e/support/admin-moderation-live.mjs`. It reuses
`openLocalPostgres`, rejects non-loopback/unknown database targets and occupied
backend port 18084, owns a random schema, bootstraps a synthetic Owner offline,
starts the built backend with an allowlist of environment variables and removes
its schema after stopping the backend. It inherits no email/Billing/Firebase/R2
provider settings. Traces are off; generated Owner credentials are removed.
The caller owns PostgreSQL startup/shutdown. Run from the repository root:

```powershell
# E2E_POSTGRES_SETTINGS points to a private {url,user,passwordFile} JSON.
# url must be jdbc:postgresql://127.0.0.1:15432/admin_test (no schema override).
# JAVA_HOME and JAVA_TOOL_OPTIONS use the JBR/socket values recorded above.
$env:E2E_POSTGRES_SETTINGS='<private-local-settings-json>'
$env:E2E_PSQL='<local-postgresql-bin>/psql.exe'
$env:UGC_E2E_ARTIFACT_DIR='D:/HienTX/Work/Android/FastToWin/.artifacts/play-integration-20261023/task2-admin-live'
node e2e/support/admin-moderation-live.mjs
```

**1 browser/HTTP/PostgreSQL flow passed** on the final source. It creates two
disposable guests/avatars, receives a server report receipt, logs the Owner in
through the actual browser, views the current avatar and removes that exact
revision. SQL proves report `RESOLVED`, version 2 and one matching typed/revision
suppression. A negotiated socket receives invalidation; `/api/avatar/{id}` returns
404 with no-store/nosniff. The other avatar and both players' profile fields,
gold/Gem balances and avatar revisions remain unchanged. After actual backend
restart, the guest resume token restores the same identity and persisted terms
`2026-10-20`; browser reload preserves the resolved report and avatar HTTP 404.
Browser storage stays empty. Served admin JS/CSS hashes match repository assets.
The flow runs with `FASTTOWIN_CONTENT_TERMS_ENFORCED=false`.

The new package alias is `pnpm test:admin-moderation-live` from `e2e`. This
managed worktree shares `node_modules` with the main checkout; pnpm's automatic
install refused that junction with `ERR_PNPM_UNSAFE_MODULES_DIR`. Shared modules
were left untouched; the equivalent native Node entry above passed. Initial
verification-only failures (old browser-cache selection and a harness query of
wallet columns on `profiles` instead of `player_stats`) were diagnosed/corrected;
the retained failure logs are not counted as passing evidence.

Captured and visually inspected actual backend admin detail top/footer at 375px
and 1280px. The white Material theme, readable labels, contained scrolling,
no horizontal overflow and reachable bottom actions are retained. Existing game
Dark Arcade rendering is covered by the unchanged 160-reference comparison.
Images/logs/XML summaries are ignored artifacts under
`.artifacts/play-integration-20261023/` (`task2-*`). No emulator, physical device,
cloud moderation fixture, provider acceptance or signed release ran in this task.

### Review follow-up: merge markers and teardown failure handling

Review found the initial report's merge-resolution claim was incorrect: the
committed `docs/testing.md` still contained delimiters and both counts. The fix
removes all four stale lines and retains 160. Exact tracked-file conflict-marker
scan and whitespace checks across both the fix and full integration ranges pass.

The new opt-in harness teardown now independently attempts socket/context/browser
closure and password removal, then stops the owned backend; schema deletion runs
only after that stop succeeds. Only `ENOENT` is accepted for password removal.
Original acceptance errors and all cleanup failures are retained in an
`AggregateError`; evidence records each cleanup outcome and `FAIL` when any stage
fails, without claiming a failed password removal succeeded.

Six focused injected-failure checks were first run against the extracted old
teardown: **6 failed** (RED). With the corrected helper, executed:

```powershell
node --test scripts/tests/admin-moderation-cleanup.test.mjs scripts/tests/admin-moderation.test.mjs scripts/tests/legal-worker.test.mjs
```

**18 passed, 0 failed/skipped** (GREEN): browser/context rejection still reaches
password/backend/schema cleanup, password `EACCES` is preserved, failed backend
stop retains the schema, only missing-password `ENOENT` is benign, and original
acceptance/evidence-write errors cannot be replaced by a false successful result.

Repeated the one actual browser/PostgreSQL flow using the same command/environment
above with `UGC_E2E_ARTIFACT_DIR` ending in `task2-r1-admin-live`: **PASS**. Its
cleanup evidence reports sockets/context/browser/backend/schema `done`, password
`already-absent`, and no failed steps. Confirmed zero owned UUID schemas, absent
Owner password file, stopped owned PostgreSQL and connection refusal on ports
15432/18084. Artifacts are `task2-r1-cleanup-red.log`, `task2-r1-node-green.log`,
`task2-r1-admin-live.log`, `task2-r1-admin-live/acceptance.json` and
`task2-r1-cleanup.log` under the same ignored artifact root. Gradle/screenshots
were not repeated for this test-harness/documentation-only correction; the prior
results above remain their exact historical evidence. No runtime/UI change.
### Remaining matrix mapped to evidence

| Surface / gate | Executed evidence and precise limit |
| --- | --- |
| Profile/name and avatar report entry | Compiled production Profile/Leaderboard/Friends/room/player components and `UgcReportDialog` player-content choice; controller report/retry tests, PostgreSQL target resolution and exact removal; new real browser avatar flow above. Traversal of every player screen on the shipped client remains pending. |
| Clan, room and tournament entries | Compiled `ReportableText`/`UgcReportButton` wiring in their existing screens; unchanged screenshots including clan/tournament; PostgreSQL all-target-type resolution, sanitizer/projection and room invalidation tests passed. Full compatible-client click-through on staging/device remains pending. |
| Enforcement false/true and stale terms | `UgcRolloutConfigTest`, `UgcSessionIntegrationTest`, `UgcIntegrationTest`, `UgcEntryConsentTest` and `UgcControllerTest` passed: explicit acknowledgement/version checks, old terms rejection and false-gate publication. Production enforcement stays false. |
| Registered upgrade/reconnect | PostgreSQL guest upgrade preserves identity/acceptance; authentication and host `GameStateTest` reconnect/authoritative-profile refresh plus controller draft/consent tests passed. A signed-client registered upgrade/reconnect journey against staging remains pending. |
| Guest restart | New flow resumes the same guest and accepted terms after an actual backend restart; PostgreSQL identity/consent tests passed. Restart of an installed physical-client binary remains a separate gate. |
| Self/missing targets, blocked users and roles | Existing physical component record above proves self/missing player controls are hidden; no device test was repeated. Full host/server run covers blocked identity/session/reconnect and transactional RBAC, and eight fixture browser cases cover role-specific controls. Self-report hiding is a client UI rule; no new server-side self-target rejection was introduced. |
| Avatar invalidation and unchanged content | New browser flow proves typed revision SQL suppression, socket invalidation, HTTP 200→404 across restart and unrelated avatar/profile/wallet preservation; shared avatar-cache host tests passed. Compatible client cache refresh on the signed physical build remains pending. |

## Remaining release gates

Keep #53 open for the remaining compatible-client, staged end-to-end and signed
physical-device feature acceptance. The controller owns PR readiness/merge and
the authorized internal 1.0.2 (3) release after review, CI and deployment gates.
Closed testing (Alpha) is a draft only, with no tester group: do not start its
rollout or submit that draft for review. This Alpha scope does not cancel the
separately authorized internal release. Recheck Play Data safety against the
exact build before distribution. Local results do not close device/provider gates.
No production database migration, moderation action, enforcement enablement,
public store submission or paid infrastructure change was performed here.
`FASTTOWIN_CONTENT_TERMS_ENFORCED` remains false by default. Follow
[the rollout guide](ugc-moderation-rollout.md) before considering production.

## PWA consent acceptance and guest cancellation — 2026-10-30

Browser acceptance used the built Web compatibility bundle, an actual local Ktor
backend and a fresh UUID PostgreSQL schema containing only synthetic accounts.
Email/provider delivery was disabled. Terms enforcement was enabled only on that
loopback backend; no production or staging configuration was changed.

- Signup: unchecked consent blocks Continue; cancellation preserves the draft and
  creates no account; reopening resets the checkbox. Accepting creates the account
  with server version `2026-10-20` and advances to local email verification.
- Guest upgrade: the browser completed upgrade against PostgreSQL, preserving user
  ID/player code and accepted version. Reload and explicit logout/login retained
  the upgraded identity. The preliminary in-memory backend's unsupported upgrade
  response was excluded from successful acceptance evidence.
- Existing user: a synthetic account without a consent record could still reopen
  its account. Nickname publication stayed pending, and cancellation retained the draft
  without changing the database. Server-confirmed acceptance then saved the draft
  and the required version.
- VI/EN consent and public legal pages were inspected. Vietnamese consent was
  visually checked at desktop and 390x844 phone sizes. These were browser viewport
  checks, not installed iOS PWA or physical-device acceptance.

This audit exposed a separate guest cancellation loop: after opening `/account`,
logging out and choosing Play as guest, Back from upgrade reopened upgrade. The
fix reads the current host route when GameContent remounts and replaces an
account/friends/clan destination with Home before cancelling upgrade. A newer
guest-accessible browser Back destination, such as Rooms, is preserved. No new
history entry is added; native hosts with no navigation bridge keep their
existing cancellation behavior. Signup/consent/token rules are unchanged.

Executed regression evidence for the fix:

- Before the fix, the new account-cancellation browser regression failed because
  the URL remained `/account`, reproducing the reported loop.
- **21 focused host tests passed**, covering guest cancellation, route request
  ownership, navigation ordering, AuthController and entry consent.
- Both Wasm and JavaScript development Web bundles built successfully.
- **9 new browser cases passed**: the three cancellation/history journeys on
  Chromium Wasm, WebKit Wasm and Chromium JavaScript fallback. Four existing
  Chromium/WebKit history cases and four existing JavaScript smoke cases also
  passed. Two initial test assertions were corrected to the actual Vietnamese CTA
  and the intended Room List outcome before rerunning all nine new cases.
- Independent review reported no Critical/Important/Minor findings. Phone and
  desktop result screenshots were inspected. Owned local backends/schemas and
  temporary credentials were cleaned up; production backups were untouched.

Detailed local evidence is retained under
`.artifacts/ugc-consent-20261030/` and
`.artifacts/guest-upgrade-back-20261030/`. These local checks do not enable mandatory
terms, close #53, update a deployed client, or authorize a Play rollout. Samsung
testing remains cancelled by the owner; cancellation is not a physical test pass.
