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
  were **not executed** in this verification; discovery is not a passing E2E result.

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

## Remaining release gates

Keep the PR Draft and #53 open until dependency integration, suitable hosted CI,
staging end-to-end acceptance, signed Android internal release and physical-device
checks are complete. Recheck Play Data safety against that exact shipped build.
No production database migration, moderation action, enforcement enablement,
public store submission or paid infrastructure change was performed here.
`FASTTOWIN_CONTENT_TERMS_ENFORCED` remains false by default. Follow
[the rollout guide](ugc-moderation-rollout.md) before considering production.
