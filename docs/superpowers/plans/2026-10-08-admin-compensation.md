# Admin Compensation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver approved Issue20 incident-based compensation, atomic existing-mailbox rewards and auditable explicit supplemental rounds.

**Architecture:** Extend the existing gift transaction with a connection-bound compensation store; shared pure gift helpers support compensation authority while common delivery/claim remains unchanged. Typed compatible player metadata and a lazy white Material admin module consume that authority.

**Tech Stack:** Existing Kotlin/Ktor/Flyway/PostgreSQL, serialization/Compose Multiplatform, browser ES modules/native dialogs and Playwright. No new framework/provider dependency.

**Spec:** `docs/superpowers/specs/2026-10-08-admin-compensation-design.md`; approved https://github.com/ThaiXuanHien/FastToWin/issues/20.

## Global Constraints

- Existing admin/gift/maintenance/events accepted local377ace2; retain authorized `codex/admin-foundation`, existing test/runtime/settings/data and unrelated artifacts. No push/merge/deploy/provider/production compensation or data reset.
- Owner/Operator incidents/drafts/preview; Owner publish/cancel; Support history/read only. Current auth/Origin/CSRF/version/request/reason before replay, atomic audit, existing admin lock7421092187349.
- Incident unique immutable canonical key, exact UTC half-open affected interval, bilingual player-safe explanation; internal evidence/error code/admin identity never player projection. Incident immutable once any round published.
- Explicit unique `(incident,number)` rounds; same UUID as gift campaign/wallet sourceID; one current draft; supplemental reason and expected max+1, never automatic allocation on retry.
- ALL_REGISTERED and verified USER_IDS supported. ACTIVITY explicitly unavailable400 `ACTIVITY_SOURCE_UNAVAILABLE`; no guessed last-seen/registration/app-version/platform targeting.
- Same JDBC/memory gift transaction; no nested/suspending gift service calls in transaction. Existing claim/delivery/maintenance/cancel/overflow/restart invariants preserved. COMPENSATION source server-owned, no event multiplier/earned leaderboard credit.
- New compensation aggregate/count transport decimal strings; previous distinct users/entitlements/selected-overlap are separate facts from one receipt query. Frozen recipients/content, default expiry30days, no public diagnostics.
- Additive migration afterV56, applied migrations untouched. Default-null optional mailbox/campaign metadata; no WS/notification enum, extra delivery loop or push.
- Mandatory UIUX/master/whiteadmin native modals48px labelled controls; Mission GOLD/XP glyphs/tints; VI/EN, actual phone/desktop/enlarged images. No physical/iOS/production claim.
- Genuine compiling behavioral RED before source implementation; scope gates and saved XML/log/hash identities. One worker at a time, fresh spec+quality review, capped fix rounds; final review one groupedfix/onescopedrereview. Controller GitHub progress after accepted parts.

## Task 1: Incident/store and compensation claim foundation

**Files:**
- Create `server/src/main/kotlin/com/hienthai/fastowin/server/compensation/{CompensationModels.kt,CompensationStore.kt,JdbcCompensationStore.kt,MemoryCompensationStore.kt,CompensationIncidentService.kt,CompensationIncidentRoutes.kt}`.
- Modify existing `gifts/{GiftModels.kt,PostgresGiftRepository.kt,InMemoryGiftRepository.kt,GiftService.kt}` and `admin/InMemoryAdminRepository.kt`, `Main.kt`, `Application.kt`.
- Modify `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GiftProtocol.kt`; create next unused Flyway migration (currentlyV57), `docs/admin-compensation.md`.
- Tests `server/src/test/kotlin/com/hienthai/fastowin/server/compensation/{CompensationIncidentServiceTest.kt,PostgresCompensationStoreTest.kt,CompensationIncidentRoutesTest.kt}` plus gift/PG claim and protocol compatibility tests.

**Interfaces:** Add `GiftTransaction.val compensation: CompensationStore`, implemented using the SAME copied memory state or JDBC Connection. Store contains incident CRUD/pagination, round mapping CRUD/pagination/max/draft/published checks, shared actor/request replay and prior receipt-summary queries; it cannot open connections/commit. Task1 owns exact method signatures/concrete admin DTOs and must document them for Task2; no guessed downstream types. `CompensationRound` mapping retains gift campaign UUID, incident UUID, roundNumber and internal supplemental/VI–EN receiving reasons; the campaign holds reward/audience/schedule. `CompensationReplay` stores operation fingerprint and immutable typed-response JSON privately. Incident service methods `list/detail/create/update/capabilities` consume existing AdminService and GiftRepository.

```kotlin
@Serializable data class CompensationMailboxContext(
    val incidentId: String, val incidentKey: String, val roundNumber: Int,
    val affectedFromEpochMillis: Long, val affectedUntilEpochMillis: Long,
    val receivingReasonVi: String, val receivingReasonEn: String
)
// Add last properties with defaults; existing constructor callsites remain valid.
// MailboxGift.compensation: CompensationMailboxContext? = null
// GiftCampaign.compensation: CompensationMailboxContext? = null
internal fun GiftCampaign.walletSourceType() = if (compensation == null) "ADMIN_GIFT" else "COMPENSATION"
```

- [ ] Step1: Add actual behavioral RED exercising incident normalized duplicate key/invalid affected bounds, same actor request replay/conflict/revocation, plus a persisted compensated campaign claim using existing GiftService. Before production support the fixture can store additive metadata as needed to compile, but assert wallet source COMPENSATION versus the unchanged ADMIN_GIFT award path. Save that compiling assertion failure separately from missing-type/build/environment failures. Protocol test asserts old missing context decodes null and old reader ignoring unknown fields accepts new metadata.
- [ ] Step2: Implement exact store/schema/incident validation/current-auth/audit/replay and admin-only incident/capability routes, off-switch and safe errors. Incident CRUD uses request/version guards and stable actor facts. Table unique key plus rounds unique mapping and replay namespace. Campaign indexed source column and storage default preserve legacy JSON. Generic ADMIN_GIFT admin operations filter/reject compensation campaigns before mutation or disclosure; common player claim/delivery processes both. Both memory/PG derive wallet source from stored server context, never request input; V47 unchanged.
- [ ] Step3: Real PG proves source wallet once under concurrent claim/retry, actual rollback of audit/wallet, unique keys/mapping, reload/checkpoints/deleted-skips, legacy source remains ADMIN_GIFT and unchanged earned totals. Test active event configuration cannot influence compensated claim. Current role revoked while waiting admin lock cannot replay; public mailbox JSON lacks incident evidence/cause/responsible admin. Pure fixture compensated campaigns do not imply round authority is delivered; Task2 supplies production creation/publication.
- [ ] Step4: Run affected `:server:test --tests '*Compensation*' --tests '*Gift*' --tests '*AdminRoutesTest'` with existing local real PG enabled, `:protocol:jvmTest`, then server packaging. Save executed/skipped/XML/source hashes; preserve legacy gifted receipt assertions. No unchanged browser/platform gate at this backend stage. Exact commands/environment documented in report.
- [ ] Step5: Self-review/source allowlist/diffcheck, commit `feat: add compensation incident and wallet foundation`; report `task-1-report.md` with exact store/admin contract, RED/GREEN, migration and source hashes, partial scope limits. Controller independent review/GitHub accepted-part update.

## Task 2: Explicit rounds, preview, publication and report authority

**Files:** Create `server/src/main/kotlin/com/hienthai/fastowin/server/compensation/{CompensationRoundService.kt,CompensationRoundRoutes.kt,CompensationGiftContent.kt}` and focused shared `gifts/GiftCampaignOperations.kt`; extend Task1 models/store only if required by documented interface. Modify GiftService to consume pure shared operations, Main/Application and `docs/admin-compensation.md`. Tests `CompensationRoundServiceTest.kt`, `PostgresCompensationRoundTest.kt`, `CompensationRoundRoutesTest.kt` and affected legacy gift tests.

**Interfaces:** Consume exact Task1 `GiftTransaction.compensation`, incident/mapping/replay/current auth and optional context. Produce typed round list/detail/create/update/preview/publish/cancel APIs under incident UUID. Request bodies bind incident version and campaign expectedVersion; round create binds expectedNextRoundNumber explicitly. Definition and preview responses document exact field names/types. `CompensationPreview` returns current incident/campaign versions, token, sample IDs<=10, invalid/duplicate IDs, selected source/conditions and decimal-string counts/totals/previous claims. `CompensationRoundDetail` includes incident key/number, immutable campaign definition, supplemental reasons/internal audit facts and decimal-string receipt report. Helpers execute synchronously on existing GiftTransaction; never call a suspending GiftService API inside another transaction.

```kotlin
// Acceptance examples, adapted to the documented fixture DTO signatures:
assertEquals("1", preview.previousClaimedUserCount)
assertEquals("1", preview.previousClaimedEntitlementCount)
assertEquals("1", preview.selectedPreviouslyClaimedUserCount)
assertEquals(firstPublished, publishSameRequestAgain())
assertEquals(1, queryWalletCount(playerId, "COMPENSATION", round1Id))
assertEquals(2, queryIncidentRoundCount(incidentId)) // explicit supplemental round2
assertEquals(2, queryWalletCount(playerId, "COMPENSATION")) // one receipt each round
```

- [ ] Step1: Genuine RED before flow implementation: concurrent/new-request duplicate default round1 rejected, exact publish retry does not allocate round2, supplemental number/reason required, outside-frozen account cannot receive, missing ACTIVITY coverage explicitly rejects instead of selecting zero. Test previous user distinct differs from receipt count when one user claimed multiple earlier rounds. Save real assertion failures.
- [ ] Step2: Implement normalized shared gift helpers and authoritative explicit round lifecycle. Use global auth→incident/mapping→campaign ordering for admin transactions; claim/cancel remains campaign→recipient→user/stats without inverse incident-lock dependency. Public content uses frozen incident/receiving reason/exact UTC interval; incident locks out future edits after first publish. Preview hash binds full incident/round definition, audience and coherent prior receipt summary. Publication snapshot/audit/replay/currentrole atomic; source/allregistered changing causes preview conflict. Cancel draft/unclaimed without reclaiming paid assets; immutable published content and original replay responses retained.
- [ ] Step3: Real PG coverage: multiple admins same next number, audit/recipient freeze failure rollback, claim/cancel race, restart/batched skip/report, same sourceID receipts once after loss/retry, old account deletion retains previous paid counts, user registration after publication excluded. Supplementary round explicit repeat payment allowed; active×2 events never change wallet grant/earned counts. Source unavailable/noDB safe503; strict routes/CSRF/off-switch. No fake cash/refund/quota functionality.
- [ ] Step4: Run focused compensation+affected gift/admin/maintenance tests with real local PG0skip, protocol if context changed, package/scanner as affected. Preserve logs/XML/hash identities before Task3. Document exact HTTP DTOs and source capability rationale. No unchanged full engine/browser/Compose suite repetition.
- [ ] Step5: Self-review/owned commit `feat: publish auditable incident compensation rounds`, full `task-2-report.md`; controller fresh independent spec/quality review and GitHub update. Task3/4 consume implemented typed contract, not plan guesses.

## Task 3: Compatible player mailbox presentation and mission icons

**Files:** Modify `shared/.../ui/screens/GiftMailboxScreen.kt`, localization gift keys/catalog, protocol localization tests and affected mailbox shared tests. Create `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/CompensationMailboxScreenshotTest.kt` with owned references. Reuse `RewardComponents.kt` mission identity; do not restyle Mission screen. No server/admin source change.

**Interfaces:** Consume Task1 default-null `MailboxGift.compensation` and Task2 generated player-safe body; existing GiftApiClient/controller/claim/profile refresh remain authority. New metadata affects labels only; missing metadata stays ordinary gift. Optional context unknown future fields remains compatible with ProtocolJson. Per-gift currency rows use MonetizationOn/GoldColor and Star/primary with visible exact amounts and localized semantics. Zero values/long amounts must wrap rather than shrink text.

```kotlin
// Rendering fixture exercises real GiftMailboxScreen rather than a recreated card.
val compensation = CompensationMailboxContext("00000000-0000-0000-0000-000000000001", "INC-DEMO", 2,
    1791424800000, 1791428400000, "Bạn có tên trong danh sách đã xác minh.", "You are on the verified list.")
val gift = MailboxGift("00000000-0000-0000-0000-000000000002", "Đền bù sự cố", "Incident compensation",
    "Thông tin sự cố đã xác minh.", "Verified incident information.", 100, 30,
    1791432000000, 1794024000000, GiftState.AVAILABLE, compensation = compensation)
```

- [ ] Step1: Add focused localization/protocol/shared behavioral RED for compensation versus ordinary/default metadata and zero/exact amounts, confirming no local wallet arithmetic or extra claim/navigation. Do not add tests that merely duplicate icon implementation; actual renders prove glyph/layout.
- [ ] Step2: Add minimal localized compensation/round label and existing safe content display; retain state badges/expiry/claim/retry/fences/profile refresh. Replace currency text in reward amount rows with same Mission icons/tints plus merged localized descriptions, adequate LIGHT contrast. No popup or new route.
- [ ] Step3: Actual Compose screenshot previews for phone320/430, desktop1024, VI/EN, DARK/LIGHT, fontScale1.6, long incident key/body, AVAILABLE/CLAIMED/EXPIRED/CANCELLED and legacy gift. Keep compact readable CTA and wrapping. Inspect originals/contactsheet; capture actual renders and source/reference SHA256.
- [ ] Step4: Run affected shared/protocol tests, localization scanner/fixtures, APK/JS/Wasm and owned screenshot update+validation; record actual executed tests/skips and iOS/device/browser limits. Count same previews once. No unchanged backend suite rerun.
- [ ] Step5: Self-review/explicit allowlist commit `feat: show incident compensation in player mailbox`; full `task-3-report.md`; controller fresh spec/quality review/GitHub update.

## Task 4: Minimal admin incident/round dialogs and local integration

**Files:** Create `server/src/main/resources/admin/admin-compensation.js`, modify explicit AdminAssets allowlist/tests, admin.js scoped adapter/nav and admin.css only feature-scoped rules, `design-system/fast-to-win/pages/admin.md`, `docs/admin-compensation.md`. Create `e2e/support/admin-compensation-fixture.mjs`, `admin-compensation-live.mjs`, `e2e/tests/admin-compensation.spec.mjs`; extend existing admin fixture/config as required.

**Interfaces:** Lazy `openCompensation(ui)` with explicit existing node/append/button/icon/iconButton/field/modal/feedback/shell/channel/pagination/api/role adapter; relative `/incidents` paths. Shared navigation fence advances before import. Consume implemented Task1/2 exact contract. Use BigInt(decimalString) for all aggregate transport, never Number parsing of count/total. Same requestID+payload retries and current session/view/dialog fences.

```javascript
// Named acceptance assertions in the real module/fixture browser suite:
await expect(page.getByRole('button', { name: 'Đền bù', exact: true })).toBeVisible();
await expect(page.getByRole('dialog')).toHaveAccessibleName(/Xem trước/);
await expect(page.getByText('9.007.199.254.740.993', { exact: true })).toBeVisible();
// Fixture aggregate is decimal-string "9007199254740993", not a Number.
```

- [ ] Step1: Genuine browser RED after existing authenticated fixture login: missingcompensationnav/control/explicitroundpreview failure. Add tests for currentroles, incidentversuspubliccopy, source capability, supplemental reason/number/previous counts, exact aggregates, strictUTC, pendingdismissal/conflict/retry/accountlate-response and publishedreadonly.
- [ ] Step2: Implement incident paginated list/details and native incident/round draft/preview/publish/cancel dialogs, history and exact counters. One primarycreate,48px labelled utilityicons, white Material, visible internal evidence boundary, user-safe VI/EN copy, no automatic activity/round assumption. Mission coin/star identity in amount/factor visuals with contrast/semantics; retain readable formlabels and confirmationactions.
- [ ] Step3: Capture actual fixture browser360/768/1024/1440 list/form/preview/report, enlarged200% top/footer, default/reducedmotion settled visibility and keyboardfocus. Inspect PNGs/geometry; avoid transparententry-animation captures. Long IDs/text wrap; header separate from scrollbody, pendingdismissalblocked.
- [ ] Step4: Real retained loopback browser/Ktor/PG flow creates ownedincident→round1preview/publish→delivery/playerclaim→identicalretry→explicitround2 with previouspaidpreview→claim→cancelunclaimed. SQL oneCOMPENSATION receipt perround/player, noADMIN_GIFT/noearnedbonus; late accountexcluded, publicJSONnodiagnostics, server/source assets match. Use isolated UUIDs/test accounts, no reset/deleteexistingdata, no secrets/traces/provider. Real storage rollback/concurrency remains repositorytest evidence, not duplicateinput claims.
- [ ] Step5: Run focusedbrowser whiledeveloping; fulladmin once afterfinalmodule, then repeatonlyaffectedtests ifchanged. Changedassetserver tests/package, liveintegration andsource/PNG/count maps. No unchanged shared/device/iOSgate rerun. Self-review/ownedcommit `feat: manage incident compensation in admin console`, report `task-4-report.md`; controller freshreview/GitHubupdate, then one wholebranchfinalreview from actualbranchmergebase, onegroupedfixwave/onescopedrereview, localacceptance only.
