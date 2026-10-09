# Admin Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver approved Issue22 in-app admin announcements, optional Home banner and conservative opt-in push with durable device state.

**Architecture:** Extend existing notification persistence with one connection-bound announcement transaction and compatible nullable metadata. Shared notification materialization/read state supports scheduled batches, typed internal actions, separate consent and a durable one-attempt push protocol; native/admin UI consume implemented contracts.

**Tech Stack:** Existing Kotlin/Ktor/Flyway/PostgreSQL, kotlinx serialization/Compose Multiplatform, Firebase adapter, ES modules/native dialogs/Playwright. No new framework/provider dependency.

**Spec:** `docs/superpowers/specs/2026-10-09-admin-announcements-design.md`; approved https://github.com/ThaiXuanHien/FastToWin/issues/22.

## Global Constraints

- Retain authorized `codex/admin-foundation` at accepted baselinef7cacf6, current runtime/settings/data/unrelated artifacts. No push/merge/deploy/provider configuration/production message/data reset.
- Owner/Operator draft/preview; only Owner publish/cancel; Support read only. Current auth/Origin/CSRF/version/request/reason before replay; atomic audit; admin advisory7421092187349.
- Canonical UUID identity, immutable published bilingual title1..160/body1..4000 UTF-16, priority0..100, required UTC half-open bounds0..253402300799999, in-app always; banner optional; push explicit defaultfalse.
- ALL_REGISTERED publication snapshot and verified USER_IDS1..10000 only; no guest/activity/platform inference. Announcement never mutates gifts/wallet/event factors/maintenance/update installation.
- Existing NotificationKind/NotificationDestination/discriminators unchanged; default-null public announcement context, legacyCOSMETIC/PROFILE/nullactionData fallback; actual old-shaped decoding test before rollout. Reject client-authored context/reserved IDs.
- Same connection/copied notification state for definition/recipient/materialized notification/checkpoint/replay/audit. Lock announcement→recipient/user/notification; legacy read/dismiss recipient→notification without inverse announcement lock.
- V58 additive, all applied migrations untouched. Full paginated authenticated player history, durable read/inbox-dismiss/banner-hide, explicit expired/cancelled states, exact decimal-string counts/no-store/safe errors.
- Separate admin/event consent defaultfalse, legacy four preferences cannot overwrite it. Push after in-app; at most one committed attempt/user/announcement,20second deadline, stale120seconds→UNKNOWN with system audit, never automatic UNKNOWN retry.
- Mandatory UIUX/MASTER, white Material admin/native modals/48px labelled utilities, arcade player light/dark. No automatic popup or match interruption; actual phone/desktop/enlarged images, no physical/iOS/production claim.
- One source worker at a time, genuine behavioral RED, scoped gates/exact saved identity, fresh spec+quality reviews, capped task fixes, final most-capable usable wholebranch review with one groupedfix/one scopedrereview. Root owns ledger/GitHub/acceptance docs.

## Task 1: Shared notification persistence and compatible announcement foundation

**Files:** Create `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/AnnouncementProtocol.kt`; modify default-null NotificationSnapshot in `GameProtocol.kt`. Create `server/src/main/kotlin/com/hienthai/fastowin/server/announcements/{AnnouncementModels.kt,AnnouncementRepository.kt,PostgresAnnouncementRepository.kt,InMemoryAnnouncementRepository.kt,NotificationPersistence.kt}`. Modify `NotificationRepository.kt`, narrowly `GameEngine.kt` client sync guards and memory-admin player read seam only as needed; create `V58__admin_announcements.sql`, `docs/admin-announcements.md`. Tests new `announcements/{AnnouncementRepositoryTest.kt,PostgresAnnouncementRepositoryTest.kt}`, affected NotificationRepository/GameEngine and protocol compatibility tests.

**Interfaces:** NotificationSnapshot gains `announcement: AdminAnnouncementContext? = null`; old enum values remain exact. AnnouncementRepository has `suspend fun <T> transaction(admin: Boolean = false, block: AnnouncementTransaction.() -> T): T`; transaction owns `val admin: AdminTransaction` and synchronous notification persistence, announcement/recipient/replay/preference/attempt operations on its existing connection/state. NotificationRepository exposes `val announcements: AnnouncementRepository` with unavailable default. Task1 owns concrete model/store/protocol methods and documents exact signatures for Task2; no downstream guessed method names. Existing room invitation API is unaffected.

```kotlin
// Task1 owns test-only observation helpers below: reloadStoredNotification returns
// the persisted NotificationSnapshot; durableRecipientCount queries its unique
// recipient; durableNotificationCountAfterInjectedRollback queries rows after a
// forced transaction exception. oldPayload and forgedContextSyncResult are actual
// legacy wire/engine results, not mocks of the behavior under test.
assertNull(ProtocolJson.decodeFromString<NotificationSnapshot>(oldPayload).announcement)
assertEquals("announcement-id", reloadStoredNotification().announcement!!.id)
assertEquals(1, durableRecipientCount(announcementId, userId))
assertEquals(0, durableNotificationCountAfterInjectedRollback())
assertEquals("INVALID_NOTIFICATIONS", forgedContextSyncResult.code)
```

- [ ] Step1: Introduce only fixture-required compatible types/schema support to compile actual legacy persistence/sync assertions; retain genuine RED for metadata lost on reload and/or forged context accepted, not missing-symbol/errors. Test actual old-shaped reader decoding new NotificationsData, old payload default-null, reserved prefix blocked even with null metadata; no new wire enum/discriminator.
- [ ] Step2: Implement connection-bound notification helper and atomic announcement repository with additive schema, stable lock order, replay namespace, frozen recipient uniqueness, existing notification read/dismiss integration and separately persisted banner/preferences/attempt facts. Legacy memory constructor and social/invitation behavior remain valid; no-op storage returns unavailable for announcement authority. No production publication/send loop yet.
- [ ] Step3: Real PG verifies atomic checkpoint+notification rollback, read/dismiss state across repository reload/two clients, metadata and locale fallback, unique recipient/notification, source/private DTO guards, hard-delete behavior and no legacy regressions. Prove legacy preference updates cannot overwrite separate announcement consent. Document exact store/public projection types before Task2.
- [ ] Step4: Run affected server `--tests '*AnnouncementRepository*' --tests '*NotificationRepository*'` and concrete changed GameEngine forged-sync tests with real local PG0skip; `:protocol:jvmTest` and server package/scanner if affected. Preserve exact command/filter, RED/GREEN logs/XML/source hashes. Existing accepted baseline tests are not repeated.
- [ ] Step5: Self-review/explicit allowlist/diffcheck/commit `feat: add compatible announcement persistence`; report `task-1-report.md`. Controller fresh combined spec/quality review; GitHub accepted foundation only, fixture publications do not claim lifecycle authority.

## Task 2: Publication, scheduled materialization, player state and push authority

**Files:** Create `announcements/{AnnouncementService.kt,AnnouncementRoutes.kt,AnnouncementPushDelivery.kt}`; extend Task1 models/store only through documented contract. Modify `PushNotificationService.kt` with a default-compatible announcement method/capability and conservative result, `Main.kt`, `Application.kt`, `docs/admin-announcements.md`; legacy reminder/social outcome behavior retained. Tests `AnnouncementServiceTest.kt`, `PostgresAnnouncementDeliveryTest.kt`, `AnnouncementRoutesTest.kt`, `AnnouncementPushDeliveryTest.kt` plus affected push/social/admin-off/security tests.

**Interfaces:** Consume accepted Task1 AnnouncementRepository/NotificationPersistence/protocol context. Produce exact documented admin `/admin/api/announcements` list/create/detail/update/preview/publish/cancel/capabilities; authenticated player `/api/announcements` paginated inbox/detail/banners/read/read-all/dismiss/dismiss-all/hide-banner/push-preferences. Version/request/token/strict body contracts owned here; page default25/max100, totals strings/serverNow; separate prefs version, safe idempotent same-value retry. Default-compatible PushNotificationService announcement method accepts raw bilingual copy and returns scoped ACCEPTED/FAILED/UNKNOWN with configured capability. Delivery and push intent claims use synchronous existing transaction; external I/O only after commit.

Task2 defines the test-only observation helpers in the following examples using the accepted Task1 store, an injected clock, real service calls and a fake external provider. They are fixture methods, not production APIs: replay returns the committed publication response; before/after/restart methods count durable notifications; catch-up returns the persisted snapshot; push calls count external invocations; durableAttempt queries storage; device fetch reads real route responses. Wallet observations use the existing player repository.

```kotlin
assertEquals(first, publishIdenticalRequestAgain())
assertEquals(0, notificationsBeforeStartsAt())
assertEquals(1, notificationsAfterRestartAtStartsAt())
assertEquals("EXPIRED", catchUpAfterEndsAt().announcement!!.state.name)
assertEquals(1, fakePushCallsAfterTimeoutAndRestart())
assertEquals("UNKNOWN", durableAttempt().status.name)
assertTrue(hideBannerOnDeviceAThenFetchOnDeviceB().banners.isEmpty())
assertEquals(walletBefore, walletAfterReadHideAndCancel())
```

- [ ] Step1: Genuine behavioral RED before lifecycle: publication replay/snapshot/role/version conflict, future no delivery, exact start/end/cancel/restart catch-up, push ambiguous retry prevented and read/banner-hide device reload. Scaffold errors are not RED evidence; use compiling fixtures and assert behavior.
- [ ] Step2: Implement normalized draft/action/correction validation, coherent audience/token preview, atomic publish/freeze/audit/replay and immutable/cancelled reporting. Link only published internal events/campaigns; no arbitrary URL/HTML/guest targeting. Admin-off routes404 including early CORS no-store. Authenticated full player history/projection/state/prefs have safe unavailable behavior and precise errors.
- [ ] Step3: Implement scheduled bounded batch materialization at startsAt, late expired history without banner/push, cancellation stops pending, deleted skips, checkpoint rollback/restart. Add one-attempt push claim/completion/recovery with20second deadline/stale120second UNKNOWN/system audit/no automatic retry; current consent/token/language, disabled/provider unavailable outcomes, shortened240-unit reminder preview. Legacy push calls retain behavior, production provider never configured by UI.
- [ ] Step4: Real PG covers publication races/revoked role after admin wait, rollback, batch restart/claim cancellation/hard-delete, notification/receipt lock seam and per-user uniqueness. Fake provider SENT/definitive failure/timeout/interruption/unfinished restart proves no resend and independent in-app copy. Counts distinguish stored/read/provider acceptance; tests assert unchanged maintenance policy/gift recipients/wallet. Strict route/API/legacy compatibility tests included.
- [ ] Step5: Run only affected announcement/notification/push/admin route scopes with actual retainedPG enabled0skip, package/localization scanner+fixtures. Protocol only if changed. Exact executed/skipped counts/commands/log/XML/source hashes in report `task-2-report.md`; commit `feat: publish scheduled announcements with safe push outcomes`. Fresh controller review/GitHub update before UI.

## Task 3: Player history, banner, consent settings and internal actions

**Files:** Create `shared/.../data/network/AnnouncementApiClient.kt`, `state/AnnouncementController.kt`, `ui/components/AnnouncementBanner.kt`, `ui/screens/{AnnouncementDetailScreen.kt,AnnouncementUpdateScreen.kt}`. Modify `AppNotification.kt`, `NotificationsScreen.kt`, `SettingsScreen.kt`, `FastToWinApp.kt`, `HomeScreen.kt` and narrow GameController announcement sync/navigation guards as needed; localization keys/catalog/tests; create `app/.../screenshot/AnnouncementScreenshotTest.kt` with owned references. No backend/admin implementation edits.

**Interfaces:** Consume exact Task2 player HTTP DTOs and default-null NotificationSnapshot context. AnnouncementGateway offers inbox/detail/banners/read/dismiss/hide/preferences through bearer-auth network-only APIs. Controller owns independent account/token/language/view/request epochs, pagination, serverNow reconciliation and retry/conflict feedback. Render server-authored admin history from paginated HTTP and legacy social rows from existing notifications, deduplicate IDs, never echo announcements into SyncNotifications. Existing read/dismiss APIs/typed internal navigation retain authority; update screen uses existing AppUpdateBridge only on user action.

Task3 defines the test-only observation helpers below around the actual controller with an injected clock/gateway: exclusive-end tick and a late response after account change return controller state; gateway and updateBridge record side effects. The automatic-dialog/award observation records actual dispatched effects and must not be a constant assertion.

```kotlin
assertEquals("EXPIRED", stateAfterTickAtExclusiveEnd().selected!!.announcement!!.state.name)
assertTrue(stateAfterAccountChangeAndLateResponse().items.isEmpty())
assertEquals(1, gateway.hideBannerCalls.size)
assertEquals(0, updateBridge.applyCallsBeforeExplicitUserClick)
assertEquals(0, automaticDialogOrAwardCalls)
```

- [ ] Step1: Genuine localization/controller RED for admin context versus legacy, paginated expired/cancelled history, server read/hide/prefs, boundary and account/language/late-response fences. Test active-match guards and internal target whitelist, no automatic modal/reward/update action, no source echo. Do not write tests duplicating icon implementation.
- [ ] Step2: Implement labelled plain-text announcement history/detail and safe event/mailbox/update/correction actions. Home has one active unhidden highest-priority banner, no focus theft; server hide is separate from inbox removal/read. Keep ready geometry during background refresh; immediate local end/cancel/account invalidation. Settings expose separate server-backed admin/event push switches defaultfalse with pending/uncertain retry/version conflict; old preference controls remain intact.
- [ ] Step3: Actual Compose screenshots phone320/430/desktop1024,VI/EN,dark/light,font1.6,active/ended/cancelled/legacy/longcopy/zero-empty/landscape and consent/update capability. Targeted matrix covers every condition without full Cartesian repetition; record exact manifest and inspect originals/contact sheet. No physical/TalkBack/iOS claim.
- [ ] Step4: Run affected protocol/shared/controller tests, localization scanner/fixtures, APK/JS/Wasm and owned screenshot update+validation. Count each unique scenario once; update+validate are the same cases. Existing notification references affected by UI must be rerendered and explicitly counted; no unchanged backend/browser suite.
- [ ] Step5: Self-review/allowlist commit `feat: show admin announcements and consent in app`; report `task-3-report.md` with API/controller/source/PNG hashes/limits. Fresh controller review and GitHub accepted native progress.

## Task 4: Minimal admin announcement dialogs and isolated local integration

**Files:** Create `server/.../resources/admin/admin-announcements.js`, modify explicit AdminAssets allowlist/tests/admin.js adapter/nav/feature-scoped admin.css, admin design override and docs/admin-announcements.md. Create `e2e/support/{admin-announcement-fixture.mjs,admin-announcements-live.mjs}`, `e2e/tests/admin-announcements.spec.mjs`; extend fixture/config only as required.

**Interfaces:** Lazy `openAnnouncements(ui)` uses existing node/append/button/iconButton/field/modal/feedback/channel/api/role/shell adapter and pre-import navigation fence. BigInt-only aggregates/pagination; no reuse of Number aggregate helpers. Exact implemented Task2 paths/DTOs, unchanged same-request/body retry and current session/view/dialog guards. Provider status/copy/privacy are plain meaningful UI facts, not a new configuration flow.

```javascript
await expect(page.getByRole('button', {name:'Thông báo', exact:true})).toBeVisible();
await expect(page.getByRole('dialog')).toHaveAccessibleName(/Xem trước/);
await expect(page.getByText('9.007.199.254.740.993', {exact:true})).toBeVisible();
// Distinct provider accepted/read/unknown fixtures must assert their distinct values.
```

- [ ] Step1: Authenticated fixture genuine RED for missing module/navigation; cover Owner/Operator/Support, immutable history, both languages/UTC, audience/source/reference validation, exact aggregates and distinct push stored/read/accepted/unknown counters, explicit push off/provider capability, uncertain retry/conflicts/pending guards/import/session/account fences.
- [ ] Step2: White Material list/history/native create-edit-preview-confirm-cancel/report dialogs, one primarycreate/48px labelled utilityicons. Preview full in-app and shortened push text separately, explicit consent/provider semantics, recipient counts/token/version. Published content read-only; cancellation acknowledges in-flight push may finish and has no gift/maintenance effect.
- [ ] Step3: Capture actual settled360/768/1024/1440 list/forms/preview/report top/footer,200% normal/reduced motion, keyboard focus/long copy/wrapping. Native modal header stays visible/body scrolls/footer reachable. Inspect originals and geometry, not transparent entry-animation/full-page compositing artifacts.
- [ ] Step4: Real owned UUID-schema loopback browser/Ktor/PG: publish scheduled→offline materialization→read/banner-hide two authenticated clients→identical publish retry→expired/cancelled history→late registration exclusion and unchanged wallet/gift/maintenance. Fake/no provider only; production push never enabled. SQL and public JSON/source-served identity retained, secrets/traces excluded. Preserve existing settings/data/runtime ownership.
- [ ] Step5: Focused browser during iteration; full admin once after final module, then affected only if code changes. Changed assetserver/package, existing worker network-only policy for/api/announcements if affected. Explicit commit `feat: manage admin announcements in console`, full `task-4-report.md`; fresh review/GitHub update, then actual-merge-base wholebranch review/one groupedfix/one scopedrereview, final local delivery doc/hash/archive. No push/merge/deploy/production claim.
