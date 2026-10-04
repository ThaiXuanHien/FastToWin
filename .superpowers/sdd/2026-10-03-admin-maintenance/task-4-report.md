# Task 4 report — maintenance controls and player feedback

Status: DONE_WITH_CONCERNS; controller review pending. Branch `codex/admin-foundation`, exact base `8199dcc8e7108eb0648216675c8362bf8d477fcf`. Scope is Task 4 only; accepted Tasks 1–3 contracts and root paid-receipt finalizer are preserved. No subagents, production actions, real payment/store provisioning, push, merge, deployment or issue closure.

All paths below are relative to `D:/HienTX/Work/Android/FastToWin`. Local ignored logs and screenshots are retained. Filenames containing “green” identify an attempt, not an assertion that it passed.

## Delivered behavior

- White Material admin module, explicit asset allowlist, existing native modal/API/channel/session fences, no new framework/auth/billing layer. Owner configure/disable, current Operator/Support reads, environment override read-only. Saved policy and live observed version/time/match count are separately displayed; unknown is never zero and a mutation acknowledgment does not assert all sockets closed.
- Planned/emergency/off payloads follow exact DTOs, with version and immutable request ID/body for uncertain retries. Enabled schedule fields retain saved durations; other edits preserve deadlines. Planned-to-emergency escalation is allowed, reverse/new schedule explains disable then enable. 409/MODE_LOCKED requires reload. Text is textContent; no CSP relaxation/private caching.
- Shared status client reads `/api/status`, propagates cancellation and distinguishes unavailable from explicit state. Last known closure survives failure. New rooms/matches/rematches/tournament starts and checkout disable for drain/closed/unknown/unreachable; admitted game composition stays alive in DRAINING. Existing root paid finalization/profileRevision behavior remains above full-screen maintenance.
- Compact Arcade banner shows state/countdown with 48dp Details button. Full runtime message, guidance, advisory Bangkok end and offline explanation use the existing native Arcade dialog, scrollable body and separately visible close action. Full closure uses established Arcade presentation with corrected server-reopens copy. VI/EN additions use existing English fallback for other languages.
- The old 15-second poll minimum is removed: server interval bounded 5–300s, failure retry5s, unknown initial fallback10s, request timeout5s. Current ACTIVE advertises30s, so discovering maintenance may take30s plus request time; known maintenance advertises5s. The server runtime5s application bound is not a client discovery guarantee. Countdown/end time never auto-open service.
- Worker shell-v8 skips exact `/status` and `/api/status` before cache/navigation branches and purges same-origin entries from all caches. Existing admin/mailbox exclusions and offline shell remain. New client alias bypasses the old controlling worker even with a waiting update. Already-running old JavaScript is not retroactively fixed; reload/new code or worker activation is required.

## TDD and attempts

| Evidence | Actual outcome |
|---|---|
| `.tmp/task4-admin-red.log` | Behavioral RED: absent maintenance navigation/control. |
| `.tmp/task4-admin-green1.log` | 14 actual browser tests passed8.8s. Three later cases and phone scroll/focus polish were covered by the final complete suite. |
| `.tmp/task4-worker-red.log` | Fixture failure: old/current bytes identical, no installing worker. Not behavioral RED. |
| `.tmp/task4-worker-red2.log` | Behavioral RED: old status cache entries remained, expected0 received3. |
| `.tmp/task4-worker-green.log` | Real Chromium old-v7/waiting/current upgrade,1passed2.7s. |
| `.tmp/task4-status-red.log` | Behavioral RED:3tests,2failures for old status route/cancellation swallowing. |
| `.tmp/task4-status-state-red.log` | Behavioral RED:5tests,4failures including closure retention and15s clamp. |
| `.tmp/task4-status-green1.log` | FAILED12s: protocol catalog completeness,93tests/1failure; additive fallback map corrected. |
| `.tmp/task4-status-render-green2.log` | SUCCESS20s:7status tests,93protocol,scanner and six initial Compose renders. |
| `.tmp/task4-status-cors-red.log` | Self-review behavioral RED3s:3tests/1failure, unnecessary Cache-Control request header. Removed custom header instead of widening CORS. |
| `.tmp/task4-client-final-green.log` | SUCCESS26s after compact banner/header correction:7status tests,93protocol,scanner,APK/JS/Wasm and ten targeted renders. |

Supplemental assertions/fixtures were not all individually observed failing first. The report distinguishes compilation/fixture failures from behavioral RED; no blanket TDD claim.

## Build and browser verification

Environment: Windows PowerShell, JBR `C:/Program Files/Android/Android Studio/jbr`; `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets`. Authorized escalated Gradle/socket commands used. No automatic approval rejection.

`.tmp/task4-build-gate.log`: **SUCCESS36s**, command:

```text
./gradlew.bat :shared:testAndroidHostTest :protocol:jvmTest :shared:checkLocalizedUiText :app:assembleDevDebug :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :server:installDist :server:test --tests '*Maintenance*' --tests '*StorePurchase*' --tests '*AdminAssets*' :app:updateDevDebugScreenshotTest --tests '*MaintenanceScreenshotTestKt*'
```

Real test PostgreSQL settings were read silently, not printed. The shared/server tests in this gate passed; their later focused XML outputs replace earlier outputs, so no reconstructed total is claimed. `.tmp/task4-install-render-validation.log`: SUCCESS12s for `:server:installDist :app:validateDevDebugScreenshotTest --tests '*MaintenanceScreenshotTestKt*'`, after final admin resource polish.

Complete browser command, **cwd `e2e`**, Node `C:/Users/Hien Hue/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe`:

```text
node node_modules/playwright/cli.js test --config admin.playwright.config.mjs
```

`PLAYWRIGHT_BROWSERS_PATH` is absolute root `.artifacts/pw-browsers`; `ADMIN_LIVE_SETTINGS` absolute `.artifacts/admin-live/settings.json`; sensitive traces disabled. `.tmp/task4-admin-full.log`:74passed/1failed58.3s because live preview was not yet listening (connection refused); this was an environment failure. After safe startup, `.tmp/task4-admin-full-live-green.log`: **75passed55s**, including17 maintenance browser cases, the real worker migration and existing admin/gift/live smoke gates. No admin source changed after this success.

Final client-only refresh after self-review:

```text
./gradlew.bat :shared:testAndroidHostTest --tests '*ServiceStatus*' :protocol:jvmTest :shared:checkLocalizedUiText :app:assembleDevDebug :webApp:compileKotlinJs :webApp:compileKotlinWasmJs :app:updateDevDebugScreenshotTest --tests '*MaintenanceScreenshotTestKt*'
```

`.tmp/task4-client-final-green.log`: SUCCESS26s. Extracting the full notice into its actual dialog with visible close footer was followed by `.tmp/task4-details-render.log`: SUCCESS27s for scanner/APK/JS/Wasm and twelve maintenance renders. Final targeted reference validation: `.tmp/task4-render-final-validation.log`, `:app:validateDevDebugScreenshotTest --tests '*MaintenanceScreenshotTestKt*'`: **SUCCESS11s,12tests/0failures/0errors**.

Archived `.tmp/task4-final-xml.json`: shared2suites/7tests, protocol39suites/93tests, Netty1suite/1test; all0failures/errors/skips. Source XML paths: `shared/build/test-results/testAndroidHostTest`, `protocol/build/test-results/jvmTest`, `server/build/test-results/test/TEST-com.hienthai.fastowin.server.maintenance.MaintenanceNettyAcceptanceTest.xml`; screenshot validation XML under `app/build/test-results/validateDevDebugScreenshotTest`. APK: `app/build/outputs/apk/dev/debug/app-dev-debug.apk`. Non-fatal existing experimental Gradle and renderer SLF4J/native-access warnings are present.

## Live owned preview and database evidence

No stale PID assumption. Before startup there was no18082 listener; refreshed installDist then started `.artifacts/admin-live/start-live.ps1`. Verified actual127.0.0.1:18082 listener PID22268 has MainKt and this checkout's `server/build/install/server/lib/*` command line. Did not stop unrelated processes. Existing loopback PostgreSQL15432/admin_test settings read silently from `.artifacts/admin-postgres/test-settings.json`; no reset/drop of an existing database/schema.

`e2e/support/maintenance-live.mjs` uses actual browser controls and authenticated WebSockets against `http://127.0.0.1:18082`, scoped by exact settings guard. Command from e2e: `node support/maintenance-live.mjs`. `.tmp/task4-preview-live.log`: fixture Promise-not-awaited failure, corrected. `.tmp/task4-preview-live2.log`: normal EMAIL_NOT_VERIFIED gate exposed missing fixture verification, corrected; cleanup restoredACTIVE. Neither is a product pass. `.tmp/task4-preview-live3.log`: **PASS** with sanitized `.artifacts/admin-maintenance-live-result.json`:

- Actual UI sequence ACTIVE→ANNOUNCING→DRAINING→MAINTENANCE→ACTIVE; emergency MAINTENANCE→ACTIVE.
- Admitted ORDER match accepted a number during drain; runtime observed activeMatches1. A new room from another authenticated socket was rejected MAINTENANCE. Deadline closed old match sockets; emergency closed an old idle socket with1001.
- Disposable OPERATOR and SUPPORT saw read-only controls; mutations403; after Owner revocation both existing sessions read401. Audit contained MAINTENANCE_UPDATE and MAINTENANCE_TRANSITION. Created admin fixtures deactivated, player refresh sessions logged out, policy restoredACTIVE in finally.
- Screenshots `.artifacts/admin-maintenance-live-announcing.png`, `-draining.png`, `-closed.png` are actual live preview captures. Test-created players/audits remain as local acceptance history; no existing data cleared.

`MaintenanceNettyAcceptanceTest` uses a real loopback Netty HTTP listener and actual PostgreSQL in its own UUID `maintenance_live_*` schema (drop only its created schema in finally), with an explicitly injected trusted store verifier. This is local transport/durability acceptance, not a real provider payment. `.tmp/task4-netty-acceptance.log`: compile failure because Java17 HttpClient is not Closeable; fixed test fixture. `.tmp/task4-netty-acceptance2.log`: **SUCCESS5s** for `:server:test --tests '*MaintenanceNettyAcceptanceTest*'`. Actual assertions/output:

```text
NETTY_ACCEPTANCE PASS override=ENVIRONMENT saved=ACTIVE disable=409 receipts=1 walletRows=1 gems=80 otherGems=0 replay=ALREADY_GRANTED unauth=401 revokedAdmin=401 provider=TRUSTED_TEST_DOUBLE
```

Concurrent receipt HTTP calls yielded exactly GRANTED+ALREADY_GRANTED, repeat ALREADY_GRANTED, other owner INVALID; actual SQL one store receipt and one STORE_PURCHASE ledger row, owner80gems/other0. Override was in this isolated process, never enabled on preview or production.

Final sanitized `.tmp/task4-preview-final.json`: verified PID22268/current installDist; `/api/status` ACTIVE and no-store; new `/admin/admin-maintenance.js`200/no-store; exact existing CSP `default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'`. Preview retained running ACTIVE.

## Visual review and limits

Mandatory ui-ux-pro-max, master, admin and maintenance overrides applied. Actual Chromium screenshots at360/768/1024/1440 are `.artifacts/admin-maintenance-{width}-{list,form,form-end,confirm}.png` (16files). Personally inspected360form/top/end,768list,1024confirm,1440list; controller also inspected phone/desktop. Initial reason autofocus clipped the top of the form; fixed first-mode focus and separate body scroll. Final phone title/focus and end actions are visible without overlap. Browser tests exercise keyboard/focus/guard/session/role behavior, touch landscape and reduced motion; no screen-reader audit is claimed.

Actual Android Compose screenshot renderer previews, not browser/device captures, live at `app/src/screenshotTestDevDebug/reference/com/hienthai/fastowin/screenshot/MaintenanceScreenshotTestKt/`:

| Image filename | Scope |
|---|---|
| `maintenanceClosed_phone_acc5ed92_0.png` |390×844 closure |
| `maintenanceClosed_desktop_56dec76c_0.png` |1024×768 closure |
| `maintenanceClosed_landscape_51ed5b8d_0.png` |844×390 closure |
| `maintenanceClosed_large_text_a7440ccd_0.png` |390×844,2×font |
| `maintenanceDraining_phone_acc5ed92_0.png` |390×844 actual GameScreen below compact notice |
| `maintenanceDraining_desktop_56dec76c_0.png` |1024×768 game/notice |
| `maintenanceLongNotice_landscape_long_message_66b5600c_0.png` |844×390,1,000-character runtime message |
| `maintenanceLongNotice_phone_long_message_large_text_72b68c4d_0.png` |390×844,1,000characters,2×font |
| `maintenanceGameBaseline_landscape_baseline_cca45c6f_0.png` |Matched game-only landscape control |
| `maintenanceGameBaseline_phone_large_text_baseline_55243827_0.png` |Matched game-only2×font control |
| `maintenanceNoticeDetails_phone_details_large_text_82e7b4e8_0.png` |Actual open notice dialog,2×font,scroll body/visibleclose |
| `maintenanceNoticeDetails_landscape_details_67542897_0.png` |Actual open notice dialog,shortlandscape/visibleclose |

All12 were visually inspected during the task, including regenerated compact/long/dialog and corrected reopen copy. Only these new references were added; no blanket baseline update. Deterministic fixture date is in the past, not a realtime estimate. Normal phone/desktop retain complete game board. Compact notice is bounded by status/countdown/control, not message length;2× title may ellipsize but full native text semantics and dialog title remain. Short-landscape game cells are small in the matched baseline and further shrink with the notice; no universal48dp game-cell/playability claim. Existing game layout was not redesigned.

Native dialog TextButton/ArcadeActionButton provide keyboard/touch semantics, dismiss/back handlers and a48dp minimum. Closing/reopening details stays local; removal of the banner on ACTIVE/full closure disposes it, while an announcing→draining update uses current status. Player dialog scrolling, keyboard traversal and assistive announcements were source-reviewed/rendered, **not physically exercised on a device/browser player**. Closed-screen landscape overflow uses existing vertical scroll; static previews cannot prove the gesture. No iOS build/device, TalkBack/VoiceOver, physical Android, production CDN/proxy, real provider or real store acceptance was performed on this Windows host.

## Self-review and handoff

Self-review caught and fixed cross-origin preflight regression from a custom Cache-Control request header; response no-store and old-worker alias safety remain intact. Compact notice replaced a tall long-text banner that compressed the board, with matched baseline renders and full dialog retained. Admin focus/scroll correction preserves native helper fences. No new CORS/header policy, grants, auth, runtime/tournament design or unrelated screen styling was introduced. Remaining concerns are the device/assistive/small-game-cell limits above and external proxy/provider acceptance.

`git diff --check` passed before staging. Owned source/tests/new references/design/docs/report only are committed; existing `.tmp`, `.artifacts`, `debug.log`, e2e artifacts and Python cache are intentionally preserved untracked/ignored. Controller performs independent per-task review after this commit.
