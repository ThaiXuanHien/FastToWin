# Admin gift totals — precision follow-up

Issue19/21 follow-up, 10 October2026 (Asia/Bangkok), on local `codex/admin-foundation`. Reviewed base `402f3a43f3e2d92fb3a4117e579a829a395a5ae9`.

Ordinary-gift admin totals previously crossed HTTP as JSON numbers, so browser JSON parsing could round values above2^53. Every admin campaign counter, preview recipientCount/totalGold/totalXp and list total now crosses HTTP as a canonical decimal string. A separate HTTP projection keeps domain Long arithmetic, persisted replay JSON, wallet transactions and player mailbox/claim protocol unchanged. Preview duplicateCount, versions, pagination indices and individual Int rewards remain numbers.

The gift view uses BigInt for formatting, total pagination comparisons and zero-recipient publication gating. A conditional layout gives long totals sufficient width; normal totals retain their previous layout. This applies to preview, final confirmation, list/pagination and report. No migration or reward calculation change is required.

Verification evidence is retained in `.artifacts/admin-gift-precision/`:

- Genuine server RED: one route assertion failed because counters were numeric. Final7 route/projection cases pass, zero failures/errors/skips, covering every admin response/replay, maximum Long preview/page totals, unchanged domain JSON and existing player/auth routes.
- Genuine browser RED: string `"0"` left publication enabled; two later geometry assertions established long totals wrapping into fragments. The initial locator failures are test defects, not behavioral RED. Large-string formatting itself already passed in the existing browser and is labelled baseline evidence.
- Final19 gift browser cases pass. A later one-case rerun covers the final invalid-recipient string fixture change; this is19 unique cases, not20. No full admin suite was repeated.
- Four settled original screenshots at360/1440 cover final confirmation and report. All four were inspected after animations completed and the report was scrolled into view. Exact values9007199254740993 and9223372036854775807 remain readable. Assertions also check no horizontal page overflow and exact list/pagination text.
- Server installDist passed. Same owned18083/schema preview resumed; health200, exact served admin.js/admin.css/admin-gifts.js hashes, authenticated list total string, usable Owner and logout/rejected session were checked. No campaign was created/published by this runtime check; the current list is empty. Large aggregate HTTP evidence uses the controlled repository boundary and browser fixtures, not a production-sized database.

Runnable gates used the bundled Java/Node runtimes: `gradlew.bat :server:test --tests com.hienthai.fastowin.server.gifts.GiftRoutesTest :server:installDist --console=plain`; from `e2e`, `node node_modules/playwright/cli.js test -c admin.playwright.config.mjs admin-gifts.spec.mjs`. Logs and XML retain exact results. Source/evidence manifests and independent scoped review are retained alongside this record.

Local acceptance only. No push, merge, deployment, physical-device or load acceptance is implied. Existing Node color and Gradle screenshot-plugin notices remain. The preview and private account access file remain under the announcement-owned local runtime; credentials and browser traces are excluded from evidence manifests.
