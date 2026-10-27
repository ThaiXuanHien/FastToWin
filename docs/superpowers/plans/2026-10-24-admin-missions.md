# Admin missions implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin quản lý catalog, lịch và thưởng nhiệm vụ cá nhân/bang; app đọc API và nhận thưởng an toàn, giữ tiến độ cũ.

**Architecture:** Một module nhiệm vụ server chịu trách nhiệm revision, kỳ, tiến độ và claim; các repository trận/điểm danh/quyên góp dùng cùng transaction với module này. HTTP và WebSocket legacy chia sẻ nguồn tiến độ và grant. Admin và app chỉ trình bày dữ liệu đã xác minh.

**Tech Stack:** Kotlin/JVM, Ktor, PostgreSQL/Flyway, Kotlin Multiplatform/Compose, admin JavaScript modules, JUnit/Kotlin test, Node/browser tests.

**Spec:** `docs/superpowers/specs/2026-10-24-admin-missions-design.md` (approved 24/10/2026 in chat).

## Global Constraints

- Server là nguồn quyết định tiến độ, target, reward và quyền nhận. App không có catalog production cố định hoặc fallback danh sách khi API lỗi.
- Chu kỳ dùng Asia/Bangkok; ngày 00:00, tuần thứ Hai 00:00. Published revision và kế hoạch kỳ đang chạy bất biến.
- Chỉ dùng action game đã hỗ trợ; tách action khỏi mission code. Migration mới phải giữ quyền nhận và ledger của nhiệm vụ legacy, không sửa migration đã áp dụng.
- Claim, ví/XP, ledger và dấu đã nhận cùng transaction. HTTP mới/command legacy không được phát hai lần.
- Owner/Operator làm draft; Owner publish/archive, Support không sửa kinh tế. Tái xác thực current role/session sau mọi khóa chờ; mutation có Origin/CSRF/version/request ID/reason và audit.
- App Arcade, admin Material sáng; áp dụng ui-ux-pro-max, TextKey đủ ngôn ngữ hiện có, ảnh phone/desktop và accessibility.
- Không sửa file .idea/.scratch của chủ dự án; không deploy/migrate production, bật provider, gửi review Google hoặc chạy Android emulator.
- Phần đua top, online/hồ sơ bang, icon/cấp, giá tạo bang, bot và hai lỗi UI nằm ngoài phần triển khai đầu tiên. Không coi metadata điểm nhiệm vụ là hệ thống top tuần đã hoàn tất.

## Task 1: Backend catalog, progress, migration and API

**Files:**
- Create `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/MissionProtocol.kt`.
- Create focused production files under `server/src/main/kotlin/com/hienthai/fastowin/server/missions/` for models, rules, repository/JDBC transaction support, service, routes, legacy migration/bridge.
- Create new Flyway migrations after the highest current version (baseline V63).
- Modify `MissionRules.kt`, `MissionRotation.kt`, `PostgresPlayerProfileRepository.kt`, `PostgresMatchResultRepository.kt`, `PostgresClanRepository.kt`, `Database.kt`, `Main.kt`, `Application.kt` and narrow GameEngine integrations as needed.
- Tests under `server/src/test/kotlin/com/hienthai/fastowin/server/missions/`, plus existing mission/claim/PostgreSQL regression tests.

**Interfaces:**
- Produces `MissionProtocol.kt` shared DTOs, authenticated `/api/missions` player endpoints and `/admin/api/missions` admin endpoints.
- Produces one transactional progress/claim seam for all old/new callers. Record concrete signatures, DTO JSON examples, paths, runtime wiring and tests in task report for Tasks 2/3; no UI task may invent an alternative contract.
- Admin operations: list/detail/create/update draft/preview/publish/archive/delete draft. Player operations: read personal and current-clan missions and claim by instance with request ID.

- [ ] Write focused failing tests first, including this observable behavior shape, adapted to the public signatures implemented in this task:

```kotlin
@Test fun `two same-action missions retain separate targets and rewards after draft changes`() = runTest {
    // Publish two PLAY definitions with targets 1 and 3 for the next DAILY period.
    // Commit one match for an eligible account; assert progress 1/1 and 1/3.
    // Edit the next revision to a higher reward; current instance still returns old reward.
    // Claim the first via HTTP, then retry via legacy; wallet ledger contains one grant.
}
```

  Add literal assertions on wallet amounts and number/source of ledgers, real Postgres replay/concurrency/rollback, legacy claimed/unclaimed migration, current role/CSRF, day/week boundaries, pool shortages, archive and identity authorization. No source-text/mocked-method assertions.
- [ ] Run focused tests before implementation and retain red output demonstrating missing behavior. Use JBR 17+ with repository Gradle wrapper; use a dedicated local test schema/database, never production or an existing user's schema.
- [ ] Implement catalog/revision and immutable period selection. Persist migrated legacy definitions; route the match/check-in/donation progress actions by action type instead of mission code. Preserve existing daily/weekly selection and legacy active instances/claims.
- [ ] Implement transactional per-player/per-clan instances, source-event deduplication, claim, ledger/source metadata and current membership checks. A donation event progressing a donation mission and direct donation contribution must retain distinct source identities.
- [ ] Wire Ktor routes into Application/Main/Database and bridge legacy profile/claim commands to the same storage. Require real storage; development/test in-memory support may exist explicitly but not hide production outages.
- [ ] Run focused and full server/protocol tests with real PostgreSQL, inspect XML counts for failures/skips and migration effects. Keep all existing assertions; failures are diagnosed, not bypassed.
- [ ] Commit only Task 1 files. Write report with red/green commands/output, database isolation, schema/migration compatibility, precise HTTP examples, public method names and any concerns.

## Task 2: App API, lifecycle, UI and localization

**Files:**
- Create `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/MissionApiClient.kt` and `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/MissionController.kt` (or narrower files in the same directories if required by the Task 1 contract).
- Modify `FastToWinApp.kt`, `ProfileScreen.kt`, `ClanScreen.kt` and focused mission UI components; extend localization catalogs/TextKey only where existing dynamic templates are insufficient.
- Tests in shared commonTest/JVM host tests and focused screenshot coverage.

**Interfaces:**
- Consumes Task 1 protocol and HTTP JSON. Inspect Task 1 report and source before changing UI; no duplicate client-side reward engine or catalog.
- Produces authenticated API gateway/controller with refresh/claim and identity epoch fencing; embeds personal/clan mission snapshots in existing screens.

- [ ] Write failing tests using real controller and controlled gateway completions. Assert late response from account A cannot fill account B UI; response ordering/cancellation/claim retry cannot reset claimed state.

```kotlin
@Test fun `old-account response cannot replace current mission list`() = runTest {
    // Start A load, switch context to B, finish B then A.
    // Assert visible IDs belong only to B, and A has no claim affordance.
}
```

- [ ] Run the focused tests red, then implement HTTP auth/refresh/no-store/error contract using existing network conventions.
- [ ] Bind API state to existing personal and clan mission screens. Show progress, target, reward, period and claim state from server. Preserve player entry points; replacing hardcoded definitions does not remove the UI.
- [ ] Add loading/empty/error/retry and pending/claimed feedback, request fencing for account/token/clan/view changes, and reconnect refresh. Refresh authoritative profile/wallet after claim instead of locally adding rewards.
- [ ] Use localized action templates with target parameters and all supported languages. Verify arbitrary admin targets render correctly, not only legacy 1/3/15 examples.
- [ ] Run shared/protocol focused tests, localization validation, Android and JS/Wasm compilation, focused host screenshots. View phone/desktop images; exercise large text and landscape. Do not run an emulator.
- [ ] Commit Task 2 and report red/green evidence, API behavior, screenshots and limits.

## Task 3: Admin mission management UI

**Files:**
- Create `server/src/main/resources/admin/admin-missions.js` and necessary focused CSS additions.
- Modify admin `index.html`, `admin.js` and asset serving/security allowlist only as needed to register the module.
- Add Node and browser tests following the existing admin-events/announcements test harnesses under their current test directories.

**Interfaces:**
- Consumes Task 1 `/admin/api/missions` DTOs and current admin framework: API client, channel fencing, node/append, modal, notices, roles and paging.
- Produces two-group mission list, draft form, preview and publish/archive/delete UI. No separate ad hoc auth or raw innerHTML of admin text.

- [ ] Write a failing browser/DOM test that creates a personal/clan draft, sets target/reward/schedule, previews and publishes; verify the payload/returned UI values against a controlled backend, not source-text checks.
- [ ] Run it red, implement list/detail/form with supported action units and validation. Include empty/loading/error, keyboard labels, inline errors and stale revision reload.
- [ ] Make Owner/Operator draft actions visible as authorized; publish/archive only Owner. Backend remains authoritative. Unknown result retries retain request ID and body; changed body receives a new ID. Async completion after logout/navigation cannot mutate current UI.
- [ ] Preview shows target, reward, difficulty, scope and effective period. Published revision cannot be edited in place. Used missions archive instead of destructive deletion; drafts can be deleted.
- [ ] Run Node/browser tests, verify escaped content, CSRF/version/current-role rejection with real backend tests from Task 1. Capture and view phone 375px and desktop images, large text/focus and dark app compatibility; admin stays Material light.
- [ ] Commit Task 3 and report red/green, UI screenshots, role behavior and limits.

## Task 4: Integrated acceptance and documentation

**Files:**
- Add focused real-backend acceptance script/test under existing e2e/test conventions; create `docs/admin-missions.md` for API/admin lifecycle/migration/rollback compatibility.
- Modify only defects identified by acceptance, with a failing regression test before each fix.

**Interfaces:**
- Consumes all prior task reports and running local dedicated PostgreSQL/backend/admin/app fixtures.
- Produces evidence that one admin-published mission is visible through the app API and completes/claims exactly once; preserved legacy rights verified across HTTP/WS.

- [ ] Run a real PostgreSQL flow: create admin Owner, publish a mission for a controlled next period, create synthetic account/clan, apply valid server game/donation events, load as player, claim twice concurrently, reload and inspect one wallet ledger. Use frozen test clock or historical fixture dates, never change machine/production time.
- [ ] Exercise draft update during current period, archive, claimed/unclaimed legacy migration, account switch, membership change and storage error; inspect exact invariant results.
- [ ] Run server/protocol/shared suites and relevant compile/localization/screenshot/admin/browser checks once on final tree. Inspect actual output and test counts, not exit code alone; intentional platform skips remain disclosed.
- [ ] View final phone/desktop images. Write usage/configuration and migration notes; include no-production-deploy status and unresolved provider/device tests honestly.
- [ ] Commit documentation and final fixes; retain reports and evidence. Mark all tasks complete only after independent review gates and final verification.
