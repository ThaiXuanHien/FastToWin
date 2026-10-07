# Admin Reward Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver approved Issue #18: scheduled holiday events with server-authoritative x2 GOLD/XP, frozen per-match rewards, player information and admin operations.

**Architecture:** A focused events module owns immutable published definitions, current admin authorization/audit, public metadata and a shared/exclusive event-configuration start gate. Existing engine and settlement persist the frozen context and one total MATCH award; shared player and native Material admin interfaces consume typed server values.

**Tech Stack:** Existing Kotlin/Ktor/PostgreSQL/Flyway, serialization and Compose Multiplatform; native browser ES modules/CSS/dialogs and existing Playwright. No production framework/CDN/dependency added.

**Spec:** `docs/superpowers/specs/2026-10-06-admin-reward-events-design.md`; approved GitHub https://github.com/ThaiXuanHien/FastToWin/issues/18.

## Global Constraints

- Independent GOLD/XP factors1 or2; max per asset, never x4/x3. Existing online CASUAL/RANKED only; intentional leave zero. No event Gem/Elo/mission/attendance/achievement/tournament/admin-gift award.
- Server time and `[startsAt,endsAt)` determine effective states. Published definitions immutable; cancellation affects later starts only. Legacy restored records remain neutral.
- Actual start snapshot persists with active/completed match; base/bonus/total/sources traceable; one idempotent MATCH transaction and earned totals, no second EVENT_BONUS credit.
- Owner/Operator drafts/preview; Owner schedule/cancel; Support cannot operate draft campaigns. Current authorization before replay, Origin/CSRF/version/request ID/reason and atomic audit.
- Admin global lock7421092187349 precedes exclusive event lock7421092187350. Start acquires shared event lock before engine mutex; no newly added event JDBC I/O while holding engine mutex. Existing frozen settlement needs no live catalog.
- Start callback cannot borrow another connection while holding the event gate. Stage quota before the gate with stable match identity, recheck admission afterward and recover abandoned reservations; actualPG pool-size2/queuedwriter case must prove no pool dependency cycle.
- A source transaction must not wait for a busy engine mutex while holding a pooled connection: other existing engine commands can borrow JDBC while holding that mutex. Use nonblocking engine lock acquisition, release the source transaction on contention and retry outside the gate with a bounded safe failure; no broad unrelated engine refactor.
- Public metadata no internal admin identities/reasons/secrets; draft404, all APIs no-store. No new unsolicited WS enum/message. Additive/default DTO compatibility verified.
- Admin white Material native dialogs and48px labelled utility icons; player existing arcade system. Mandatory UIUX skill, actual phone/desktop captures, Vietnamese/English.
- Holiday presets create drafts only; lunar dates supplied per year, no automatic repetition/publication/identity targeting. Keep default no-event legacy/test path and explicit configured storage failure.
- Continue authorized local checkout `codex/admin-foundation`; no push/merge/production campaign/provider changes. Preserve ignored artifacts/settings/data/secrets. One source worker at a time; fresh per-task spec+quality review, capped fix rounds; one final groupedfix/scopedreview.

## Task 1: Event authority, storage, HTTP and frozen contract

**Files:**
- Create protocol `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/RewardEventProtocol.kt`.
- Create server `events/RewardEventModels.kt`, `RewardEventRepository.kt`, `PostgresRewardEventRepository.kt`, `InMemoryRewardEventRepository.kt`, `RewardEventService.kt`, `RewardEventRoutes.kt`, `EventRewardSource.kt` under `server/src/main/kotlin/com/hienthai/fastowin/server/`.
- Create next unused Flyway migration (currentlyV54) for event definitions/request replay; do not alter applied migrations.
- Modify `Database.kt`, `Main.kt`, `Application.kt`; create `docs/admin-reward-events.md`.
- Test server `events/RewardEventServiceTest.kt`, `PostgresRewardEventRepositoryTest.kt`, `RewardEventRoutesTest.kt`; protocol `RewardEventProtocolTest.kt`.

**Interfaces:**
```kotlin
@Serializable enum class RewardEventState { DRAFT, SCHEDULED, ACTIVE, ENDED, CANCELLED }
@Serializable data class RewardEventSourceSnapshot(
    val id: String, val version: Long, val nameVi: String, val nameEn: String,
    val startsAtEpochMillis: Long, val endsAtEpochMillis: Long,
    val goldMultiplier: Int, val xpMultiplier: Int
)
@Serializable data class MatchRewardEventSnapshot(
    val capturedAtEpochMillis: Long? = null, val goldMultiplier: Int = 1,
    val xpMultiplier: Int = 1, val sources: List<RewardEventSourceSnapshot> = emptyList()
)
@Serializable data class MatchRewardBreakdown(
    val baseGold: Int = 0, val bonusGold: Int = 0, val totalGold: Int = 0,
    val baseXp: Int = 0, val bonusXp: Int = 0, val totalXp: Int = 0,
    val sources: List<RewardEventSourceSnapshot> = emptyList()
)
interface EventRewardSource {
    suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T
}
// Immutable catalog produced by EventRewardSource; implementation owns constructor/storage.
// Public member: fun snapshot(atEpochMillis: Long, matchType: MatchType): MatchRewardEventSnapshot
// NoRewardEventSource invokes block with a neutral catalog (legacy/test default).
```
Task1 owns concrete admin/public DTOs and exact API response documentation so downstream workers consume the implemented contract. Public typed page contains safe event name/content/timezone/bounds/factors/modes/state/version, serverNowEpochMillis and page metadata; default25/max100. `RewardEventService` exposes list/detail/create/update/preview/schedule/cancel with a token/origin/CSRF context and `publicList/publicDetail`. Admin request mutations bind expectedVersion/reason/requestId, schedule binds previewToken. Use current AdminService/JdbcAdminTransaction rather than parallel auth. Scheduling/cancelling take exclusive configuration gate; source shared gate loads published immutable catalog BEFORE callback and closes transaction AFTER callback, rollback/cancellation safe. Draft edits cannot affect captured live sources. Memory implementation shares admin transactional audit; unavailable implementation throws503.

- [ ] Step1: Add behavioral RED tests before production. Named boundary and overlap examples:
```kotlin
// With event A gold2/xp1 on [1000,2000), B gold1/xp2 on [1500,2500):
assertEquals(1, catalog.snapshot(999, MatchType.RANKED).goldMultiplier)
assertEquals(2, catalog.snapshot(1000, MatchType.RANKED).goldMultiplier)
assertEquals(2, catalog.snapshot(1500, MatchType.RANKED).xpMultiplier)
assertEquals(1, catalog.snapshot(2000, MatchType.RANKED).goldMultiplier)
// Use real repository/service fixtures; missing implementation is not alone acceptance.
```
Run actual `:server:test --tests '*RewardEvent*' :protocol:jvmTest --tests '*RewardEventProtocolTest' --console=plain`; save compiling behavioral failures separately from setup errors.
- [ ] Step2: Implement models/repository/migration, derived states, bilingual name1..200/content1..2000 Unicode code-point lengths/time validation (UTC bounds0..253402300799999, start<end, valid IANA timezone, nonempty known modes, factors1/2), roles/current-after-wait/atomic audit/replay/version, public-safe projection and gate ordering. Draft1/1 allowed because approved independent factors permit it; contributes no bonus sources. Publish requires future end; ended/cancelled cannot edit, ended read-only.
- [ ] Step3: Actual PostgreSQL tests must prove schedule vs start/cancel barrier, role revocation after waiting, failed audit rollback, mismatched replay409 and unavailable/no-DB safe errors. Authenticated mutations retain global ordering; public/catalog paths must not hold global admin lock or stall maintenance observer. Public draft404/no-store/CSRF tests use actual Ktor/service, not mocked authorization.
- [ ] Step4: Wire durable source/service without changing engine yet, record exact request/response contract in `docs/admin-reward-events.md`; preserve optional gameModule defaults. Run changed server/protocol/admin covering tests with retained local PG0skip, package/scanner when affected; copy XML/counts/logs before next task can overwrite. Do not replay unchanged platform/browser gates solely confirm.
- [ ] Step5: Self-review, own files only and commit `feat: add authoritative reward event campaigns`; report task-1-report.md with actual RED/GREEN, source hashes, gate ordering, API contract and test limitations.

## Task 2: Freeze at engine start and settle durable exact rewards

**Files:**
- Modify server `GameEngine.kt`, `ActiveRoomRepository.kt`, `MatchResultRepository.kt`, `RewardEconomy.kt`, `PostgresMatchResultRepository.kt`, `PostgresPlayerProfileRepository.kt`, `Main.kt`.
- Modify `PlayQuota.kt` and `PostgresPlayQuotaRepository.kt` only if the narrow reservation/recovery seam required below needs them.
- A focused internal `PreparedMatchStart.kt` helper is allowed for immutable admission/staging data; private Room transitions remain in GameEngine. Next migration may add quota reservation state/identity while preserving original quota dates and finalized receipts.
- Modify protocol `GameProtocol.kt` only for additive defaulted properties; use Task1 types. Create next unused migration (afterTask1) for match context/player breakdown.
- Test `RewardEventGameEngineTest.kt`, `PostgresRewardEventSettlementTest.kt`, legacy snapshot/protocol tests and relevant existing GameEngine/maintenance/match-result tests.

**Interfaces:** consume exact Task1 `EventRewardSource` and `RewardEventCatalog.snapshot`; `GameEngine` adds default `eventRewardSource: EventRewardSource = NoRewardEventSource` without breaking existing named/positional callers. Add default-neutral `eventRewards: MatchRewardEventSnapshot` to StoredActiveRoom and CompletedMatch and private Room, preserve on all store/restore/pending paths. Add nullable/default `eventRewards` and `rewardBreakdowns: Map<String,MatchRewardBreakdown> = emptyMap()` to GameSnapshot; optional `rewardBreakdown` to MatchHistorySnapshot. `RewardEconomy.matchReward` preserves its3-argument callers and adds frozen-event aware helper; `matchRewardBreakdown(matchType,outcome,intentionalLeave,eventRewards)` returns exact base/bonus/total. Determine tournament neutral at engine start and carry that neutral through completion, not by reevaluating live event.

- [ ] Step1: Genuine RED with actual engine players/repositories: ready start at boundary, matchmaking, rematch new snapshot, independent overlaps, source cancellation while catalog/start barrier held, end/cancel after start, round restart and legacyneutral. Assert completed context and repository reward, not just DTO equality.
```kotlin
val result = RewardEconomy.matchRewardBreakdown(MatchType.RANKED, MatchOutcome.WIN, false,
    MatchRewardEventSnapshot(goldMultiplier=2, xpMultiplier=2))
assertEquals(100, result.baseGold); assertEquals(100, result.bonusGold)
assertEquals(200, result.totalGold); assertEquals(60, result.totalXp)
```
- [ ] Step2: Wrap each eligible actualstart transition in source shared gate BEFORE engine mutex and choose fresh now at Room.startMatch. Cover finalready and rematch, matchmaking separately; no snapshot on merely entering lobby/readiness. Prepare existing quota consumption BEFORE the event gate with stable candidate/match identity, recheck room/participants/quota/maintenance after asynchronous work and recover abandoned reservations. Add narrow quota reservation/rollback seam in `PlayQuota.kt`/`PostgresPlayQuotaRepository.kt` if required, backed by real atomic tests; do not hold event transaction while borrowing quota/profile/persistence connections. Exclude tournament starts. Use nonblocking engine mutex acquisition inside source callback; release source transaction before waiting/retrying, with bounded safe failure. Test pool2 also with a busy engine command borrowing JDBC and a queued admin writer, not only a free engine mutex. No new event DB query under engine mutex and no periodic cache correctness assumption. Preserve callback gate cleanup and no nested reversed acquisition. Actualpool-size2 + queuedwriter regression required.
- [ ] Step3: Save frozen context with active room before advertising gameplay; preserve pending/recovery/maintenance activation behavior. On source/durable-start failure return safe error and preserve recoverable room/quota state; no fake neutral fallback with configured durable source. Existing frozen matches/settlement still work during source failure. Test slow/failing storage, concurrent cancel and restore, all pendingCompletedMatch paths.
- [ ] Step3a: Prepared starts may stage a detached immutable snapshot while the original room stays nonplayable/fenced until durable save succeeds outside the event gate. Reservation recovery checks durable active rooms, pending results and completed matches before refunding abandoned work; a started/settled or legacy finalized match must never be refunded. Exercise participant and maintenance transitions during preparation/save.
- [ ] Step4: Transactionally store context and breakdown plus totalMATCH stats/wallet/earned accounting, leave Elo/missions/achievements base semantics untouched. RealPG test rankedwin200/60, casualbases, leave0, replayonce, rollbackno partial and source matchsourceonly; tournamentneutral/offline/client never grants. History/detail reads persisted breakdown or null for legacy (no retroactive guessed x2).
- [ ] Step5: Verify old GameSnapshot serializer ignores new properties, old storedRoom/CompletedMatch decode neutral, no new WS discriminator. Run focusedPG+engine and covering maintenance/recovery/economy/protocol tests, localizedchecks only if affected, preserve XML/logs. Commit `feat: freeze event rewards and settle match bonuses once`; full report task-2-report.md before fresh review.

## Task 3: Player banner/details and authoritative result breakdown

**Files:**
- Create shared `data/network/RewardEventApiClient.kt`, `state/RewardEventController.kt`, `ui/components/RewardEventBanner.kt`, `ui/screens/RewardEventDetailsScreen.kt`.
- Modify existing common state/app/home/result/profile-history/detail integration at `GameState.kt`, `GameController.kt`, `FastToWinApp.kt`, `HomeScreen.kt`, `ResultScreen.kt`, `ProfileScreen.kt`; scoped extraction into focused reward UI file allowed when needed, no unrelated UI refactor.
- Modify protocol localization `TextKey.kt`, `LocalizationCatalog.kt`, create event catalog VI/EN plus existing fallback policy.
- Tests shared API/controller/state; protocol old/new snapshot and localization tests.
- Create `app/src/screenshotTest/kotlin/com/hienthai/fastowin/screenshot/RewardEventScreenshotTest.kt` using the existing Compose screenshot pipeline and phone/desktop-sized preview devices. Extend `ArcadeScreenshotFixtures.kt` only for reward fixtures if needed; own corresponding generated reference images. Captures are rendered UI evidence, not physical-device acceptance.

**Interfaces:** consume Task1 actual public HTTP DTO/contracts from docs, Task2 server GameSnapshot/history rewardBreakdown. HTTP `/api/events` network-only, same configured base/client transport; eventscontroller has explicit loading/error/unavailable/empty/current items, selection and request/account/view/language generation. Clear on auth change/route away, fence success/error/finally, cancel refreshes, no persistence of private state. Load on home entry and refresh using server times; invalidate banner at known start/end/cancel refresh and never infer award eligibility from banner. Existing GameState stores provided context/breakdowns additively; client uses no multiplier arithmetic to create award.

- [ ] Step1: Read mandatory UIUX/master/pro-rules/quick-reference and use targeted verified Compose/search guidance. Controller/API behavioral RED: real transport URL/no-store + two delayed account/language/view responses, offline/retry/error, current event expiring and empty page. Saved controls don't show stale event as active after expiry; unknown is not fabricated empty. No popups over a live match.
- [ ] Step2: Implement compact arcade home banner and details with bilingual text, explicit timezone/bounds, GOLD/XP and eligible modes. No new bottom navigation item; nativeback/details navigation. Labels, touch targets>=48dp, safeareas/fontscale, reducedmotion and no decoration competing with playCTA. Active event can lead to details; user can inspect scheduled/published history through list/detail where relevant.
- [ ] Step3: Online result renders authoritative base/bonus/total/source names from the completed snapshot; intentionalleave0, CASUAL base and tournamentneutral correct. Preserve frozen source explanation after expiry/cancel. Legacy absent breakdown remains clearly legacy/base behavior rather than fabricated event amounts; offline remains its existing path. Profile history/detail uses persisted breakdown when available. Do not imply a failed pending settlement has already credited wallet.
- [ ] Step4: Run shared/protocol/controller/localization covering tests, localizedscanner+fixtures, APK dev build and JS/Wasm compile. iOS unavailableWindows documented, no claimed device acceptance. Produce/inspect actual phone compact/enlarged and desktop home/details/result screenshots with fixture event names and exact200/60 labels; store paths/hashes, avoid merely describing captures.
- [ ] Step5: Commit owned work `feat: show reward events and server reward breakdown`; report task-3-report.md actual executed counts/commands/REDGREEN/images/limits; fresh review before task4.

## Task 4: Admin events, holiday drafts and final integration

**Files:**
- Create `server/src/main/resources/admin/admin-events.js`; modify `admin.js`, scoped `admin.css`, `admin/AdminAssets.kt`, assets tests.
- Modify `e2e/admin.playwright.config.mjs`, shared fixture support; add event fixture and `e2e/tests/admin-events.spec.mjs`.
- Update admin design override/eventscontract docs; retain publicworker `/api/*` exclusions and add focused worker coverage if new route changes require it.

**Interfaces:** consume Task1 realadmin DTO/API, exactpreviewtoken/version, and the current admin adapter passed from `admin.js` to the lazy module (including `{shell,api,channel,modal,iconButton}`). Primitives are supplied through the adapter rather than imported as exports. Adapter `api(path,request,payload,method)` uses relative `/events` paths. Newnav uses accepted latest shared navigation fence: selection invalidates outgoingview BEFORE lazyimport; do not recreate independent channels. Owner/Operator list/draft/preview, Owner schedule/cancel; Support hidden and server403. All modal/data/import completion success/error/finally fences use current session/view/role/request.

- [ ] Step1: Read mandatoryUIUX and existing whiteadmin override; targeted native dialog/focus/touch searches, no generic marketing restyle/CDN/fonts. Browser behavioralRED proves missingeventnav/list then permission/staleversion/requestretry/pendingcancel. Test public safeeventAPI in actuallocalKtor via fixtures or live settings with sensitive tracesOFF.
- [ ] Step2: Build paginated list/history/status/search, draftedit native modal, bilingualdetails/preview, explicit schedule/cancel confirmation with serverpreviewtoken/currentversion. Display time with timezone and exact[GOLD,XP] factors. Published rows readonly; ENDEDhistory no edit/cancel; errors don't misrepresent mutationrollback or allclientsupdated. Conflict requiresreload, idempotentrequest retrypreserves payload/ID, fields/close blockedwhilepending.
- [ ] Step3: Provide editable holiday draft suggestions for lunarNewYear (manual Gregoriandate/year), January1, March8, October20, October31, November19, November20, December24–25. Defaults are suggestions not autoannualpublish; user explicitlyenters year/start/end. No gender/jobtargeting. Explain overlapmaxperasset, separategiftcampaign and excludedrewards. Accessible48pxutilityicons and readableSave/Cancel/Schedule.
- [ ] Step4: Scopedbrowser all permission/modal/timezone/longtext/fontzoom/lateAPI/import/role/logout cases then fulladminbrowser ONCE with actuallocalsettings enabled and tracesOFF, actual360/768/1024/1440+phone modal/enlarged screenshots inspect. Relevantserverassets/routes/events+protocol/shared changed gates, localization andinstallDist; repeatplatformonly ifnewchangedsourcewarrants. Keep failed/RED evidence. Localownedpreview restart actualverifiedPID only, retainedPG15432/settings noreset/providersOFF; verifyhealth/servedassetsSHA/no-store.
- [ ] Step5: Local isolatedtest event draft→preview→schedule, actual eligiblematch captured x2, expiry/cancel/settlement/retry and server/playerhistory prove oneMATCH total, labelledfixtureonly. No existingusers/campaignreset or productionchange. Commit `feat: manage reward events and holiday drafts`; report task-4-report.md evidencecounts/commands/screenshots/sourcehashes/recovery and limits. Fresh taskreview then mostcapable wholebranchreview from verifiedmergebase, one groupedfinalfixwave/one scopedreview. UpdateGitHub#18/#21 progress with exactacceptedlocalparts; don't claim production or close prematurely.
