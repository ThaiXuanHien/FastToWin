# Admin Gift Campaigns and Mailbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement approved Issue #19 as working, durable gift campaigns in the admin console and a manual-claim mailbox on Android/iOS/Web.

**Architecture:** Keep campaigns, frozen recipients and entitlements in the existing Ktor/PostgreSQL backend. Publishing and audit share a transaction; claiming locks campaign, entitlement and player balance and writes its wallet transaction atomically. Native browser modules provide the white Material admin UI, while shared Compose provides the player mailbox independently of notifications.

**Tech Stack:** Existing Kotlin/Ktor, serialization, PostgreSQL/Flyway, Compose Multiplatform and native JS/CSS. No new production framework or external service.

**Spec:** https://github.com/ThaiXuanHien/FastToWin/issues/19, with roles and operations from #21, approved 01/10/2026. The user instructed continuation after approving the admin UI on 02/10/2026.

## Global Constraints

- MVP tặng Vàng và XP với số nguyên không âm, ít nhất một khoản dương. Gem, vật phẩm và lượt online tách thành mở rộng.
- Người nhận: tài khoản đã đăng ký tại thời điểm công bố; MVP không cấp cho guest. Công bố đóng băng nội dung, phần thưởng, tập ID và lịch phát.
- ID không tồn tại làm bước công bố bị từ chối, không bỏ qua âm thầm; ID lặp được gộp và thông báo số lượng.
- Hạn mặc định 30 ngày từ giờ phát dự kiến, lưu một mốc hết hạn cụ thể. Nhận hợp lệ trong `[availableAt,expiresAt)`; phát chậm không tự kéo dài hạn.
- Khóa duy nhất `(campaign_id,user_id)`. Wallet dùng `source_type=ADMIN_GIFT`, `source_id=campaign_id`.
- Khi nhận, trạng thái + ví + lịch sử phải cùng giao dịch; retry không cộng lại. Quà admin không tăng lifetime earned dùng cho bảng xếp hạng V47.
- Hủy chặn phát mới và quà chưa nhận; quà đã nhận giữ nguyên. Nhận/hủy phải khóa trạng thái chiến dịch để tránh cuộc đua.
- Phát batch có checkpoint bền vững, restart/retry không tạo thư trùng. Notification không quyết định entitlement; không tự gửi push trong công việc này.
- Owner công bố/hủy. Operator soạn/xem trước. Support không tạo/sửa/công bố; token người chơi không cấp quyền quản trị. Mỗi request kiểm tra quyền hiện tại; mutation yêu cầu Origin/CSRF.
- Audit và thay đổi quản trị cùng transaction. Request ID chống lặp tạo/công bố/hủy; payload khác dùng lại ID trả 409.
- Giờ lưu UTC; admin nhập/hiển thị Asia/Bangkok. Nội dung gửi người chơi có Việt–Anh.
- Giữ giao diện admin Material nền trắng, modal và icon cho thao tác quen thuộc; có accessible name, tooltip và vùng chạm tối thiểu 48px. Đọc ui-ux-pro-max trước mọi thay đổi UI.
- Không push, merge, deploy, cấp tài khoản production, công bố chiến dịch production hoặc gửi push hàng loạt. Local tests only; leave existing test preview and DB available after verification.

## Execution and interface decisions

- Continue in the existing checkout on `codex/admin-foundation`, baseline `08216a1`; preserve `.tmp/`, skill caches and `debug.log`. The tracked tree is clean. This is the continuation of the approved in-place admin work.
- Select ALL_REGISTERED, USER_IDS or REGISTERED_BETWEEN (inclusive start/exclusive end). Activity filtering must be unavailable with an explicit explanation while no official last-activity source exists; never substitute created-at for activity.
- Public player API uses authenticated registered-account bearer tokens on `/gifts`; a guessed campaign ID does not disclose another user's gift. No new websocket message enum is needed.
- Keep protocol DTOs in `GiftProtocol.kt`. Gift titles/bodies use `titleVi`, `titleEn`, `bodyVi`, `bodyEn`; rewards `gold` and `xp`; dates use epoch-millisecond Long fields. Reward/balance overflow must fail atomically rather than wrap.
- Administrator API prefix `/admin/api/gift-campaigns`: GET list, POST create, GET `/{id}`, PUT `/{id}` draft update, POST `/{id}/preview`, POST `/{id}/publish`, POST `/{id}/cancel`. List/detail include delivery/claim counters and actual claimed reward totals.
- Create/update inputs carry bilingual content, reason, gold/xp, audience, user IDs or registered-date bounds, availableAtEpochMillis, expiresAtEpochMillis, requestId; update adds expectedVersion. POST preview carries expectedVersion and returns campaign/version, recipientCount, duplicateCount, invalidUserIds, totalGold/totalXp and previewToken.
- Publish input `{expectedVersion,previewToken,requestId,reason}`. Server recalculates recipients and totals; reject 409 if previewToken/version no longer matches. Frozen recipient rows and published campaign are committed together. Configuration cannot be edited after publication; correction creates a new campaign.
- Cancel input `{expectedVersion,requestId,reason}`. Replay of the same publish/cancel request succeeds without duplicate effects even after state/version changes; another payload with the ID conflicts.
- Player API GET `/gifts/mailbox?page=0&pageSize=25` returns paged gift summaries with AVAILABLE/CLAIMED/EXPIRED/CANCELLED states; unstarted or undelivered gifts are not claimable or exposed as available. POST `/gifts/mailbox/{campaignId}/claim` returns final gift state plus awarded gold/xp and current balances. POST `/gifts/mailbox/claim-all` body `{campaignIds:[...]}` processes at most 100 IDs independently and returns each item's result/error; UI can process pages in batches.
- Capture server request-received time for claim boundary checks. A valid request waiting for locks must not expire merely while waiting. On replay, return already claimed result without paying again.
- Delivery selects due published campaigns and processes a bounded batch of frozen recipients, marking deleted accounts SKIPPED and recording progress in the same transaction as entitlements. Integrate a restart-safe periodic loop with application lifetime and an explicit callable batch operation for tests; never depend on process-only queue state.
- Do not alter the player's existing game theme globally. Fit a clear mailbox into its existing design system, with readable Vietnamese/English content, reward summaries and explicit Claim/Claim all labels.

## Verification environment

Use JBR `C:/Program Files/Android/Android Studio/jbr`, JAVA_TOOL_OPTIONS `-Djdk.net.unixdomain.tmpdir=D:/HienTX/Work/Android/FastToWin/.tmp/jvm-sockets`. Run Gradle with escalation as needed. PostgreSQL is already running at loopback15432 with settings in `.artifacts/admin-postgres/test-settings.json` and password in its referenced file; do not print secrets or reset/drop this database. Integration tests must create/drop their own isolated schemas. Admin preview is loopback18082 with ignored `.artifacts/admin-live/start-live.ps1` and settings.json. Portable gh is `.artifacts/tools/bin/gh.exe`.

### Task 1: Durable backend, protocol and player mailbox API

**Files:**
- Create `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GiftProtocol.kt` for safe player DTOs.
- Create focused `server/src/main/kotlin/com/hienthai/fastowin/server/gifts/` files for models, service, repository, PostgreSQL/in-memory implementations, HTTP routes and delivery lifecycle.
- Create migration `server/src/main/resources/db/migration/V50__add_admin_gift_campaigns_and_mailbox.sql` (verify next unused version before writing).
- Modify `admin/AdminRepository.kt`, PostgreSQL/in-memory admin repositories and AdminService/Routes only as needed for transaction-sharing authorization/audit; `Database.kt`, `Main.kt`, `Application.kt` for wiring.
- Test under `server/src/test/kotlin/com/hienthai/fastowin/server/gifts/`; use actual repositories/Ktor hosts, not mocked authorization.
- Document final exact request/response shapes in `docs/admin-gifts.md` for Task 2/3 consumers.

**Interfaces:** Produces the HTTP routes above, protocol DTOs, a callable delivery-batch operation and service injected into application. Expose transaction-bound helpers for current admin authorization/audit so business mutations share the existing DB transaction. Reuse player progression and wallet conventions without invoking a separate wallet transaction after entitlement commit.

- [ ] Write failing tests first: unprivileged publish, Operator publish, CSRF/origin rejection, invalid IDs, duplicates, changed preview, stale version and request-ID replay. Tests must also prove claim ownership, boundary times, rollback, concurrent claim/cancel and no earned-leaderboard increment.

```kotlin
@Test fun `two concurrent claims award one wallet transaction`() = runTest {
    val campaign = publishGift(gold = 100, xp = 30)
    deliverDueBatch()
    coroutineScope { List(2) { async { claim(player.id, campaign.id) } }.awaitAll() }
    assertEquals(100, balanceDelta(player.id).gold)
    assertEquals(30, balanceDelta(player.id).xp)
    assertEquals(1, walletEntries(player.id, "ADMIN_GIFT", campaign.id).size)
    assertEquals(0, earnedGoldDelta(player.id))
}
```

Implement fixture helper names against real repository behavior; inject a clock to test availableAt−1, availableAt, expiresAt−1, expiresAt. Verify duplicate IDs, nonexistent IDs, guests, post-publish registrations, a deleted user, notification deletion independence, transaction failure and integer overflow. Simulate partial delivery + repository restart + resume, asserting every frozen recipient has exactly one final delivery result.

- [ ] Run focused test classes to capture RED before implementation with the JBR environment above.
- [ ] Implement migrations/repositories/service/routes and application wiring, maintaining canonical lock order and safe error handling. Batch failures retry safely from persisted state, never crash the entire application scope.
- [ ] Run focused GREEN, real PostgreSQL integration tests and the full server suite once. Add protocol serialization/backward compatibility tests for new independent HTTP DTOs. Record exact counts and any skips.
- [ ] Self-review transaction boundaries, race tests, sensitive audit payloads and API contract; write `docs/admin-gifts.md` and commit all Task 1 files explicitly.

### Task 2: Minimal Material admin campaign console

**Files:** `server/src/main/resources/admin/admin.js`, `admin.css`, optional focused native module `admin-gifts.js`; `e2e/support/admin-fixture.mjs`, new gift browser tests and `e2e/admin.playwright.config.mjs`; `design-system/fast-to-win/pages/admin.md`, `docs/admin-management.md`.

**Interfaces:** Consume Task 1's exact contract from docs/admin-gifts.md. Reuse shell/api/channel/modal/pagination and access/session guards; if splitting a module, pass these helpers through one explicit adapter rather than duplicating auth or UI state.

- [ ] Read ui-ux-pro-max and current design system. Add a Quà admin section for Owner/Operator, hiding drafting actions from Support. Keep the user-approved white Material, icon buttons and modal forms.
- [ ] Write a failing browser test covering draft → preview → publish confirmation and report, stale preview rejection, role denial, cancellation, request retries and session expiry. Keep fixture responses aligned with the real backend DTOs.

```javascript
await page.getByRole('button', { name: 'Quà admin', exact: true }).click();
await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click();
await expect(page.getByRole('dialog', { name: 'Tạo chiến dịch quà' })).toBeVisible();
// Populate bilingual content, gold/xp, audience and Bangkok dates.
// Preview must show recipient count and total assets before confirmation.
await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
await expect(page.getByText('Tổng Vàng', { exact: true })).toBeVisible();
```

- [ ] Implement paginated list/detail/status filters, draft create/edit modal, audience inputs, default30-day expiry, two-language preview and exact count/asset confirmation. Display unsupported activity filtering explicitly. Keep editable fields only on draft; require a cancellation reason. Preserve request IDs on identical retries and pending guards on all dismissal paths.
- [ ] Report eligible/delivered/claimed/expired/cancelled/skipped and actual claimed assets; failed/unknown operations must not present successful publication or stale preview. Expiry/revocation clears dialogs, inputs and campaign data.
- [ ] Run full browser suite and JS syntax checks; capture/inspect 360/768/1024/1440 screenshots for list, form, preview and confirm. Rebuild local server distribution, restart only the validated local preview process, and verify actual API/UI with local test data. Do not publish outside disposable local data.
- [ ] Update design/usage docs and commit Task 2 files explicitly, keeping ignored screenshots/test credentials out of Git.

### Task 3: Shared Compose player mailbox and final integration

**Files:** new `shared/src/commonMain/kotlin/com/hienthai/fastowin/data/network/GiftApiClient.kt`, `ui/screens/GiftMailboxScreen.kt`, focused controller/state under existing game package; `FastToWinApp.kt` and Home/Notifications entry point; localization keys/catalog modules in protocol; client tests following existing test structure.

**Interfaces:** Consume GiftProtocol DTOs and `/gifts` API; use existing account-token/session flow, HTTP configuration, reconnect/error conventions and refresh profile after a successful claim. Notification removal must not hide the mailbox.

- [ ] Read ui-ux-pro-max; inspect existing shared design/controller/navigation patterns. Write failing meaningful controller/API/localization tests: partial claim-all, retry after dropped response, account switch/late response, expired/cancelled item, server wallet refresh.
- [ ] Implement a discoverable Hộp thư quà entry, server-paged mailbox with states and bilingual title/body, reward/horizon summary, Nhận and Nhận tất cả. Disable pending claims; process each item result independently and retain retryable failures. Registered account only, explicit guest guidance. Do not claim on open, notification tap or banner dismissal.
- [ ] Clear mailbox state and pending callbacks when account/token changes; reopening/refetching restores server truth. Local language changes choose stored bilingual fields without changing server state.
- [ ] Run common/protocol/client tests, localization scanner and supported JVM/Android/Web compilation checks. Record iOS verification limitations on Windows precisely; shared code must compile for supported local targets without introducing platform-specific APIs.
- [ ] Run final server + admin + integration checks and inspect screenshots. Record acceptance coverage against Issue #19, update docs and commit Task 3 files explicitly. Leave local preview running, no automatic GitHub issue closure or deployment.
