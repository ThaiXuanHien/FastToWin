# Task 4 delivery — admin reward events and holiday drafts

Status: implemented and locally verified, awaiting controller's fresh task review. Base `8e30a63276844c31a8ef9d9ff1338c1f34ad9054`, branch `codex/admin-foundation`. One source worker; no subagents, external messages, GitHub changes, push, merge, deployment or production/provider changes. Accepted Tasks 1–3 source remains unchanged except this task's existing admin shell/assets/docs integration.

## Delivered behavior

- Lazy `admin-events.js` receives the existing explicit adapter; relative `/events` API paths, shared pre-import navigation invalidation, current session/view/role/request fences on success/error/finally. Owner/Operator list/search/state/history, create/edit draft and server preview; Owner schedule/cancel; Support navigation hidden and existing server authorization authoritative.
- Native white Material draft/preview/confirmation dialogs, text-only bilingual content, readable form/action labels, labelled 48px utility SVGs. Compact coin/star ×factor summaries have localized accessible currency labels/titles. Exact server preview definition/version/token drives confirmation; CSRF comes from the shared API wrapper. Current definitions are reloaded after conflict; pending fields and close/cancel/Escape/backdrop are blocked. Identical payload retries retain their request ID; uncertain failures never claim rollback.
- UTC-only entry is explicit, includes year and milliseconds, and never inherits gift Bangkok parsing. Separate valid IANA display timezone; exact UTC and zone display preview/detail; inclusive start/exclusive end. Published definitions read-only, ENDED/CANCELLED history terminal. Stable creator/publisher and latest actor displayed separately; legacy unknown and never-published truth retained.
- Eight editable draft suggestions, explicit year, manual Gregorian lunar New Year dates, no automatic repetition/publication or identity targeting. Independent max factors, excluded rewards and separate admin gifts explained in plain product copy.
- Explicit server/fixture asset allowlist, packaged new module, existing no-store/CSP/network-only policy retained. No new dependencies, frameworks/CDNs or global restyle. Event dialog header is outside its independently scrolling body so scrolled content cannot paint above it; gift/maintenance dialogs unchanged.

## Verification and preserved history

Evidence root: `.artifacts/admin-reward-events-delivery/task4/`. `commands.ps1`, `run-server-gates.ps1`, `start-preview.ps1` and `verify-live-database.ps1` retain commands/setup without secrets. Source identity: `source-hashes.json` (12 owned source/doc/test paths); PNG identity: `image-hashes.json`. Retained raw settings/password files were loaded silently and never printed.

| Run / evidence | Actual result |
|---|---|
| `red-browser.log`, `red-browser/` | 1 executed, 1 genuine behavioral failure after successful login: missing “Sự kiện thưởng” navigation, before production implementation. Synthetic trace/error context retained. Initial output-directory setup mistake is not RED. |
| `green-focused-first.log`, `focused-first-failures/` | 21 total: 13 pass, 7 fail, 1 live skip. Failures: actual search-tooltip overflow plus overly broad test locators/fixture override. |
| `overflow-diagnostic.log` | 2 failures identified overflowing search tooltip bounds (383px / 451px at 360px); scoped tooltip alignment corrected. |
| `green-focused-second.log` | 19 pass, 1 test synchronization failure, 1 live skip. Query assertion needed to await actual async request. |
| `red-modal-heading.log` | 1 pass, not RED despite the early filename: viewport-only close check. Later hit-test/geometry checks are stronger. |
| `focused-live-header-failure.log` | 20 pass, 1 fail, 0 skip: live API has raw `Cache-Control: no-store, no-store` from existing global interceptor and route. Final test asserts exact unique directive set `{no-store}`, not a weaker cache claim; no backend change. |
| `green-focused-final.log` | 25 executed/pass, 0 failure/error/skip, actual loopback public API enabled; traces off. Includes current preview superseding stale detail/version, exact schedule retry, late modal success/error after logout/new session and newer-filter fences. |
| `green-full-admin.log` | Full admin browser suite executed ONCE: 145 pass, 0 failure/skip, 1.4m. Includes actual loopback auth/directory/audit/logout and public event API. Traces off. |
| `modal-visibility-diagnostic.log`, `settled-modal.log` | 2 pass each, affected capture/visibility checks only; not additional unique feature coverage. |
| `red-header-scroll.log`, `red-header-results/` | 1 genuine geometry failure before scoped clipping fix: body top207 < header bottom220. |
| `green-events-after-modal-clip.log`, `final-event-results/` | Final affected event suite 25 pass, 0 failure/error/skip. Full suite was not rerun for the event-only CSS/capture fix. |
| `server-gates.log`, `server-xml/`, `test-counts.json` | BUILD SUCCESSFUL23s; 56 actual server tests across6 suites, 0 failures/errors/skips. Assets2, service8, routes2, PostgreSQL repository9, engine30, PostgreSQL settlement5. Retained loopback PostgreSQL configured, all PG tests execute. `installDist` and localization scanner/fixture task passed. |
| `repackage-final.log` | Final post-CSS `:server:installDist` BUILD SUCCESSFUL1s. No Kotlin/protocol/shared implementation changes after server gate. |
| `live-integration.log/json`, `live-database.json` | Final packaged source/served hashes match; actual browser+Ktor+WebSocket+PostgreSQL integration PASS, described below. |

Overlapping scoped/full/diagnostic runs are not summed as unique coverage. Broad Kotlin/platform/shared/protocol/player gates from accepted Tasks 1–3 were not repeated because Task4 changes only admin assets, allowlist test, e2e and docs. Existing experimental Android screenshot configuration warning and Node NO_COLOR/FORCE_COLOR warning remain; no new build warning was hidden.

## Actual isolated local integration

Retained PostgreSQL reverified at127.0.0.1:15432, PID24392; original preview18082 had no listener. Hidden owned Java preview started PID4644, then exact listener/executable/classpath checked before owned restarts17056 and final3856. Retained PG data/config/settings/secrets preserved; no init/reset/delete. Firebase unavailable/push disabled and external email/store/reward providers unconfigured. Final preview remains owned loopback18082 for controller verification.

Final `e2e/support/admin-events-live.mjs` ran from `e2e` with retained `ADMIN_LIVE_SETTINGS`, no traces. It created only labelled local fixture accounts/events, used real browser draft→preview→schedule and exact schedule replay, confirmed private draft404 and safe public DTO, captured two independent GOLD2/XP1 and GOLD1/XP2 sources in an actual CASUAL start, cancelled GOLD after capture and waited for XP expiry. Completion retained the frozen2/2 source context. Result:80+80=160 GOLD,24+24=48 XP. Player committed history and match detail equal the server breakdown; admin match history and wallet agree. Duplicate exact final selection plus repeated wallet read added no award. Fresh rematch captured neutral1/1 with no sources; fixture rematch was left and sockets/auth sessions closed.

Final match ID `054c6e60-26f8-46a8-80d6-2c6678b6f3ee`. Fixture GOLD event `b9c6c22c-15aa-40a4-9bae-47347aba8417` is CANCELLED; XP event `5374cd0a-c790-433a-b1f7-eca5cfc3221d` is ENDED. Final read-only SQL transaction independently verified actual stored match context, each persisted participant breakdown, exactly one total MATCH ledger row per participant and no EVENT_BONUS row. `live-database.json` preserves the redacted fixture result. Actual raw public cache header is `no-store, no-store`; exact unique directive set verified. Assets individually return `no-store`.

Runner history is explicitly not behavioral RED: `live-first-registration-failure.*` required200 while actual successful registration is201; `live-second-number-count-failure.*` reached real capture/cancel/expiry but awaited a separate game_finished message, while ordinary last-number completion is existing game_state_updated with phaseFINISHED. These harness assumptions were corrected without gameplay/server changes. Earlier passing run retained as `live-before-modal-clip.*`. Test data/accounts from earlier attempts remain; no existing users/campaigns were reset or deleted. Repository settlement rollback and exact result retry are separately exercised by the5 realPG settlement tests; duplicate WebSocket input is not a forced storage-failure/retry claim.

Final served SHA256 (also source hashes):

| Asset | SHA256 |
|---|---|
| `admin.js` | `32f9867babc87565eb9205a14e38cd000b438be4d5b6017b08ad83759ffd766f` |
| `admin.css` | `7c6202eaefaf5bddcbb1d6c9c04ddd61a60a61bf58ff267d4d3013a366d2e24e` |
| `admin-events.js` | `65d86df2eb537c549d2d2c67236311af7e3e0cee4e0aed25ddb328facd585168` |

## Visual inspection and capture diagnosis

Applied mandatory UIUX skill, existing MASTER/admin override, pro-rules/quick-reference and targeted native-dialog/focus/touch search (three actual UX matches). Inspected actual360/768/1024/1440 lists/forms/previews, phone scrolled footer, enlarged text and real live preview. Final paths `events-{360,768,1024,1440}-{list,form,form-end,preview}.png`; `events-enlarged.png`, `events-enlarged-modal-top.png`, `events-enlarged-modal.png`, `events-enlarged-default-settled-{top,footer}.png`; identity-free real `live-preview.png`. Early images retained in `images-before-polish/`.

Early enlarged capture was INVALID visual evidence: it caught the0→1 native-dialog entry opacity animation. Full-page capture alone was not the cause. Default-motion diagnostic in `modal-visibility.json` records entryopacity0, then settled top/footeropacity1, white rgb(255,255,255) background and close hit-testtrue. Reduced-motion also records1 with no active animations. A separate real scrolled-padding bleed was fixed by header/body separation; final header bottom220/body top240 and close within viewport. Final top/footer images were opened and inspected: opaque white panel, readable heading/close, no body painted above heading, scrollable content and reachable footer at200% text. No global shell restyle.

## Ownership, review and limits

Owned allowlist: `design-system/fast-to-win/pages/admin.md`, `docs/admin-reward-events.md`, `e2e/admin.playwright.config.mjs`, `e2e/support/admin-fixture.mjs`, `e2e/support/admin-event-fixture.mjs`, `e2e/support/admin-events-live.mjs`, `e2e/tests/admin-events.spec.mjs`, `server/src/main/kotlin/com/hienthai/fastowin/server/admin/AdminAssets.kt`, `server/src/main/resources/admin/{admin.js,admin.css,admin-events.js}`, `server/src/test/kotlin/com/hienthai/fastowin/server/admin/AdminAssetsTest.kt`, and this report. Explicit owned staging only; commit message `feat: manage reward events and holiday drafts`. Unrelated `.tmp/`, `debug.log`, `e2e/.artifacts/` and existing UIUX `__pycache__/` remain untouched. `git diff --check` passed; final staged verification before commit recorded in tool output.

Self-review checked adapter paths, pre-import fence, modal/read/mutation fences, exact preview/version/token/retry identity, UTC/IANA semantics, terminal state actions, legacy identities, text-only rendering, pending dismissal, packaging and no-store. Fresh controller spec/quality review still required; worker did not spawn a reviewer. No physical device, Safari/Firefox, iOS, production, external provider or load test claimed. UTC entry has no convenience local-zone conversion. No forced production-like outage in the owned running preview; actual repository rollback/retry is proven in isolated PG tests. Controller owns GitHub accepted-progress updates and final whole-branch review.
