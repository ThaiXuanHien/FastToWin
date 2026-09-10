# Economy and Progression Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the shop to Gem and Gold, centralize match rewards, rotate a larger mission catalog, and ship 20 achievements with 16 named frames and 12 named titles while preserving legacy ownership.

**Architecture:** Add a deep `RewardEconomy` module whose small interface owns server-authoritative reward and exchange decisions. Keep stable IDs and serializable display metadata in protocol, persist unlock/reward state in PostgreSQL, and let Compose render server snapshots without calculating currency or unlock rules. Existing card/board cosmetics and season-specific cosmetic IDs remain readable and equippable but disappear from the sales catalog.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization, Ktor WebSocket server, PostgreSQL 17/Flyway, Compose Multiplatform, kotlin.test and Android Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-08-economy-clan-quota-platform-design.md`

## Global Constraints

- The server is authoritative for balances, rewards, exchange rates, mission rotation, progress and unlocks.
- Store tabs are exactly `Gem` and `Vàng`; old `CARD_BACK` and `BOARD_SKIN` ownership/equipment data is retained.
- Gold exchange offers are 10 Gem → 1,000 Gold, 45 Gem → 5,000 Gold and 80 Gem → 10,000 Gold.
- Match rewards are Casual W/D/L = 80/40/20 Gold and 24/16/8 XP; Ranked W/D/L = 100/50/25 Gold and 30/20/10 XP; an intentional leave after start grants zero.
- Daily check-in and season rewards remain unchanged.
- Four deterministic daily missions and four deterministic weekly missions are visible for a period; reconnecting or changing devices cannot change the selection.
- Achievement rewards are granted once in the same transaction as the first unlock.
- Stable IDs are English identifiers; all user-visible names use `TextKey` in all 12 catalogs.
- Previously unlocked daily-check-in and season cosmetics remain owned and equippable.
- Every production behavior is implemented test-first and each focused test must be observed failing for the missing behavior before implementation.

---

### Task 1: Central reward and store catalogs

**Files:**
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/RewardEconomy.kt`
- Create: `server/src/test/kotlin/com/hienthai/fastowin/server/RewardEconomyTest.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`
- Test: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/protocol/EconomyCatalogTest.kt`

**Interfaces:**
- Produces: `RewardEconomy.matchReward(matchType: MatchType, outcome: MatchOutcome, intentionalLeave: Boolean): RewardGrant`.
- Produces: `RewardEconomy.goldExchange(offerId: String): GoldExchangeOffer?`.
- Produces protocol types `GoldExchangeOffer`, `ClientMessage.ExchangeGemsForGold(requestId, offerId)` and `ServerMessage.GoldExchangeResult(requestId, offerId, spentGems, receivedGold, status)`.
- Removes `SHOP_ITEMS` as a sales catalog while retaining `CosmeticType.CARD_BACK` and `CosmeticType.BOARD_SKIN`.

- [x] **Step 1: Write failing catalog and reward tests**

```kotlin
@Test
fun `gold exchange catalog has the approved rates`() {
    assertEquals(
        listOf(
            GoldExchangeOffer("gold_bag", 10, 1_000),
            GoldExchangeOffer("gold_chest", 45, 5_000),
            GoldExchangeOffer("gold_vault", 80, 10_000)
        ),
        GOLD_EXCHANGE_OFFERS
    )
}

@Test
fun `match reward depends on match type outcome and intentional leave`() {
    assertEquals(RewardGrant(gold = 80, xp = 24), RewardEconomy.matchReward(MatchType.CASUAL, MatchOutcome.WIN, false))
    assertEquals(RewardGrant(gold = 50, xp = 20), RewardEconomy.matchReward(MatchType.RANKED, MatchOutcome.DRAW, false))
    assertEquals(RewardGrant(), RewardEconomy.matchReward(MatchType.RANKED, MatchOutcome.LOSS, true))
}
```

- [x] **Step 2: Run tests and verify RED**

Run: `./gradlew.bat :protocol:jvmTest :server:test --tests "*RewardEconomyTest" --no-daemon`

Expected: compilation/test failure because `GoldExchangeOffer`, `GOLD_EXCHANGE_OFFERS` and `RewardEconomy` do not exist.

- [x] **Step 3: Implement the minimal catalogs and pure reward module**

```kotlin
internal data class RewardGrant(val gold: Int = 0, val gems: Int = 0, val xp: Int = 0)

internal object RewardEconomy {
    fun matchReward(matchType: MatchType, outcome: MatchOutcome, intentionalLeave: Boolean): RewardGrant {
        if (intentionalLeave) return RewardGrant()
        return when (matchType to outcome) {
            MatchType.CASUAL to MatchOutcome.WIN -> RewardGrant(80, xp = 24)
            MatchType.CASUAL to MatchOutcome.DRAW -> RewardGrant(40, xp = 16)
            MatchType.CASUAL to MatchOutcome.LOSS -> RewardGrant(20, xp = 8)
            MatchType.RANKED to MatchOutcome.WIN -> RewardGrant(100, xp = 30)
            MatchType.RANKED to MatchOutcome.DRAW -> RewardGrant(50, xp = 20)
            MatchType.RANKED to MatchOutcome.LOSS -> RewardGrant(25, xp = 10)
        }
    }
}
```

- [x] **Step 4: Run focused tests and verify GREEN**

Run: `./gradlew.bat :protocol:jvmTest :server:test --tests "*RewardEconomyTest" --no-daemon`

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add protocol/src server/src/main/kotlin/com/hienthai/fastowin/server/RewardEconomy.kt server/src/test/kotlin/com/hienthai/fastowin/server/RewardEconomyTest.kt
git commit -m "feat: centralize reward and exchange catalogs"
```

### Task 2: Atomic Gem-to-Gold exchange and two-tab shop

**Files:**
- Create: `server/src/main/resources/db/migration/V43__add_gold_exchange_requests.sql`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PlayerProfileRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ShopScreen.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresGoldExchangeTest.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`
- Test: `app/src/androidTest/java/com/hienthai/fastowin/CriticalFlowsUiTest.kt`

**Interfaces:**
- Produces: `PlayerProfileRepository.exchangeGemsForGold(playerId, requestId, offer): GoldExchangeStatus`.
- Consumes: the three `GOLD_EXCHANGE_OFFERS` from Task 1.
- UI sends only stable offer ID and UUID request ID; server looks up amounts.

- [x] **Step 1: Write failing repository tests** proving sufficient balance exchanges atomically, insufficient balance changes nothing, and replaying the same request ID grants Gold once.

```kotlin
assertEquals(GoldExchangeStatus.GRANTED, repository.exchangeGemsForGold(id, requestId, GOLD_EXCHANGE_OFFERS[0]))
assertEquals(990, repository.get(id)!!.progression.gems)
assertEquals(1_000, repository.get(id)!!.progression.gold)
assertEquals(GoldExchangeStatus.ALREADY_GRANTED, repository.exchangeGemsForGold(id, requestId, GOLD_EXCHANGE_OFFERS[0]))
assertEquals(1_000, repository.get(id)!!.progression.gold)
```

- [x] **Step 2: Run `PostgresGoldExchangeTest` and verify RED** because the migration and repository method are absent.

- [x] **Step 3: Add migration V43** with `gold_exchange_requests(request_id UUID PRIMARY KEY, user_id UUID, offer_id VARCHAR(32), gems_spent INTEGER, gold_granted INTEGER, created_at TIMESTAMPTZ)` and a unique wallet source `(user_id, 'GOLD_EXCHANGE', request_id)` through the existing `wallet_transactions` constraint.

- [x] **Step 4: Implement the transaction**: lock `player_stats` with `FOR UPDATE`, detect duplicate request, reject insufficient Gem, subtract Gem, add Gold, insert `GOLD_EXCHANGE` wallet transaction and request receipt, then commit; rollback on every failure.

- [x] **Step 5: Run repository tests and verify GREEN**.

- [x] **Step 6: Write failing GameEngine protocol tests** for granted, insufficient and duplicate responses, then route `ExchangeGemsForGold` through the repository and refresh the profile snapshot.

- [x] **Step 7: Write failing Compose UI tests** asserting exactly two tabs (`GEMS`, `GOLD`), no number/board shop tabs, three offer cards, a disabled exchange button when Gem is insufficient, and a confirmation dialog before exchange.

- [x] **Step 8: Replace cosmetic sales UI** with `GemStorePreview` and `GoldExchangeList`. Remove `onBuy`/`onEquip` from `ShopScreen`; do not remove equipped legacy cosmetics from profiles or gameplay rendering.

- [x] **Step 9: Run focused server and Android compilation/UI tests and verify GREEN**.

- [x] **Step 10: Commit**

```bash
git add protocol/src server/src shared/src app/src/androidTest
git commit -m "feat: simplify shop to gem and gold"
```

### Task 3: Apply balanced match rewards

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresMatchResultRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/MatchResultRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresMatchResultRepositoryTest.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`

**Interfaces:**
- Consumes: `RewardEconomy.matchReward` from Task 1.
- `CompletedMatchPlayer` carries `intentionalLeave: Boolean = false`, preserving decoding/call-site compatibility.

- [x] **Step 1: Write failing table-driven integration tests** for all six Casual/Ranked W/D/L combinations and both intentional-leave variants using literal expected wallet deltas.

- [x] **Step 2: Run the focused tests and verify RED** against the current unified constants.

- [x] **Step 3: Replace `matchExperienceReward` and `matchGoldReward`** with one `RewardEconomy.matchReward` decision per player and persist its Gold/XP values to `player_stats` and `wallet_transactions` in the existing match transaction.

- [x] **Step 4: Ensure leave outcome propagation** marks the leaving player before `CompletedMatch` persistence; do not alter Elo loss behavior for Ranked.

- [x] **Step 5: Run focused tests and verify GREEN**, then run all server match tests.

- [x] **Step 6: Commit**

```bash
git add server/src
git commit -m "feat: balance rewards by match type"
```

### Task 4: Deterministic mission catalog and rotation

**Files:**
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/MissionRotation.kt`
- Create: `server/src/test/kotlin/com/hienthai/fastowin/server/MissionRotationTest.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/MissionRules.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresMatchResultRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/MissionRulesTest.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresMatchResultRepositoryTest.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresDailyCheckInTest.kt`

**Interfaces:**
- Produces: `MissionRotation.forPeriod(period: MissionPeriod, periodStart: LocalDate): List<MissionDefinition>`.
- The selector returns daily difficulties `[EASY, NORMAL, NORMAL, HARD-or-ELITE]` and weekly `[NORMAL, HARD, HARD, ELITE]`, deterministically seeded from the period date.
- Clan donation mission definitions are present but receive progress only from the future `ClanProgression` module.

- [ ] **Step 1: Expand `MissionRulesTest` first** to assert all exact 10 daily and 8 weekly definitions, targets, difficulties and reward tuples from spec section 6.4.

- [ ] **Step 2: Write rotation tests first** asserting four entries, required difficulty slots, no duplicates, stable output for the same date, and changed selection across representative consecutive dates/weeks.

- [ ] **Step 3: Run focused tests and verify RED** because 14 definitions and rotation are missing.

- [ ] **Step 4: Add the 18 definitions and pure deterministic selector** using a stable integer seed derived from ISO date, not `Random.Default` and not process-local state.

- [ ] **Step 5: Run catalog/rotation tests and verify GREEN**.

- [ ] **Step 6: Write failing match integration tests** for play, Casual, Ranked, win, correct-count, 90% accuracy, perfect win, weekly streak and weekly perfect progress. Assertions target only missions active for the fixture period.

- [ ] **Step 7: Generalize match mission progress** through `missionIncrement(definition, matchType, playerOutcome, metrics, currentStreak)` and upsert only active definitions.

- [ ] **Step 8: Write a failing check-in integration test** for `DAILY_CHECK_IN`, then increment it in the same daily-check-in transaction. Leave donation mission increments absent until the clan phase rather than fabricating client-driven progress.

- [ ] **Step 9: Change profile mission loading** to return the active four daily and four weekly definitions with stored progress defaulting to zero; retain old claimed rows for wallet history.

- [ ] **Step 10: Run all mission/check-in/match tests and verify GREEN**.

- [ ] **Step 11: Commit**

```bash
git add server/src
git commit -m "feat: add rotating mission catalog"
```

### Task 5: Achievement catalog, progress and one-time rewards

**Files:**
- Create: `server/src/main/kotlin/com/hienthai/fastowin/server/AchievementRules.kt`
- Create: `server/src/test/kotlin/com/hienthai/fastowin/server/AchievementRulesTest.kt`
- Create: `server/src/main/resources/db/migration/V44__expand_achievements.sql`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresMatchResultRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/PostgresAchievementRewardTest.kt`

**Interfaces:**
- Produces: `AchievementDefinition(code, titleKey, descriptionKey, difficulty, target, frameId?, titleId?)` for all 20 approved achievements.
- Extends `AchievementSnapshot` compatibly with `unlocked`, `progress`, `target`, `difficulty`, `rewardXp`, `rewardGold`, `rewardGems`, `frameId` and `titleId`, all with safe defaults.
- Produces: `grantNewAchievements(connection, userId, candidates, occurredAt, matchId)` that inserts unlocks and wallet rewards once.

- [ ] **Step 1: Write failing pure catalog tests** asserting exactly 20 stable codes and difficulty rewards: Easy `100/0/20`, Normal `250/0/50`, Hard `500/1/100`, Elite `1,000/3/200` as Gold/Gem/XP.

- [ ] **Step 2: Run and verify RED**.

- [ ] **Step 3: Implement the catalog and migration V44**. Add `difficulty`, `target`, `reward_xp`, `reward_gold`, `reward_gems`, `frame_id`, `title_id` and `is_active`; upsert the 20 approved definitions and mark legacy-only `WIN_10`, `STREAK_5`, `PERFECT_GAME` and `SPEED_50` inactive. Keep their historical `user_achievements` rows untouched. `FIRST_WIN` remains active under the same ID. New achievements are independently evaluated from persisted statistics/events, so a stricter new requirement is never unlocked merely because a legacy code existed.

- [ ] **Step 4: Write failing PostgreSQL tests** proving a first unlock inserts one `user_achievements` row, one `ACHIEVEMENT` wallet transaction and the exact balance change; repeated evaluation changes nothing.

- [ ] **Step 5: Implement atomic grant logic** using `INSERT ... ON CONFLICT DO NOTHING RETURNING achievement_code`; award only rows returned by the insert.

- [ ] **Step 6: Implement current match-driven progress** for wins, ranked wins, streak, perfect matches, accuracy and response-time achievements from persisted match/event data. Implement check-in and player-level achievements in the profile/check-in transaction. Keep the five clan achievements visible with zero/current progress until Task 3 of the parent roadmap provides authoritative clan counters.

- [ ] **Step 7: Return all 20 snapshots** by left-joining definitions with unlocks and calculating bounded progress; locked achievements use `unlockedAtEpochMillis = 0`.

- [ ] **Step 8: Run achievement, profile, match and check-in tests and verify GREEN**.

- [ ] **Step 9: Commit**

```bash
git add protocol/src server/src
git commit -m "feat: expand achievements and rewards"
```

### Task 6: Sixteen frames and twelve titles

**Files:**
- Create: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/ProgressionCatalog.kt`
- Test: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/protocol/ProgressionCatalogTest.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/PostgresPlayerProfileRepository.kt`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/SeasonLifecycleRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/components/PlayerAvatar.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/ProfileScreen.kt`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_lightning.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_wildfire.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_warrior.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_veteran.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_glory.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_unyielding.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_legend.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_emperor.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_speed_shadow.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_champion.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_immortal.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_dragon_might.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_supreme.xml`
- Create: `shared/src/commonMain/composeResources/drawable/arcade_frame_peerless.xml`

**Interfaces:**
- Produces `FRAME_CATALOG` with 16 approved names and `TITLE_CATALOG` with 12 approved names, each identified by stable English ID and localized key.
- Reuses existing Diamond and Challenger frame art where applicable; creates 14 distinct 2D Arcade vector treatments for the other named frames.
- Legacy frame/title IDs remain renderable; they are not counted in the new catalogs.

- [ ] **Step 1: Write failing catalog tests** asserting the exact 16 frame IDs/names, exact 12 title IDs/names, unique IDs and every achievement reward reference resolves to a catalog entry.

- [ ] **Step 2: Run protocol tests and verify RED**.

- [ ] **Step 3: Implement stable catalogs** and replace repository-local `unlockedFrameIds`/`unlockedTitleIds` conditionals with unlock IDs stored by achievement/season receipts.

- [ ] **Step 4: Add the 14 vector frame assets** with distinct silhouette, accent palette and glow/border treatment; map all 16 IDs in `PlayerAvatar.framePainter` and `frameDisplayName`.

- [ ] **Step 5: Write failing profile tests** proving locked items are visible, unlocked items can equip, locked items cannot equip, and legacy equipped IDs still render after migration.

- [ ] **Step 6: Implement profile collection rendering** from the catalogs plus owned legacy/season items. Preserve scroll position and avoid optimistic profile reload flicker when equipping.

- [ ] **Step 7: Make a Challenger season receipt unlock `frame_challenger`; award `title_speed_king` only to final rank 1 during season settlement. Retain old `season_*` receipts as owned legacy entries and make both new grants idempotent through the existing season claim**.

- [ ] **Step 8: Run protocol, server profile and shared tests and verify GREEN**.

- [ ] **Step 9: Commit**

```bash
git add protocol/src server/src shared/src
git commit -m "feat: add frames and titles catalog"
```

### Task 7: Localize all new economy and progression content

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/SocialShopCatalogTexts.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ProfileCatalogTexts.kt`
- Modify: all 12 language catalog files under `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/AppNotification.kt`
- Test: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/LocalizationCatalogTest.kt`
- Test: `app/src/androidTest/java/com/hienthai/fastowin/LocalizedSocialShopUiTest.kt`

**Interfaces:**
- Adds keys for Gold tab/offers/exchange states, 18 mission names, 20 achievement names/descriptions, 16 frame names/unlock descriptions and 12 title names/unlock descriptions.
- Backend and protocol send key names plus arguments; Vietnamese fallback text remains only for backward compatibility.

- [ ] **Step 1: Extend localization tests first** so all 12 explicit languages must resolve every new key without falling back to Vietnamese or English.

- [ ] **Step 2: Run catalog tests and verify RED** with the missing key report.

- [ ] **Step 3: Add all `TextKey` members and translations** for Vietnamese, English, Simplified Chinese, Japanese, Korean, Spanish, Brazilian Portuguese, French, German, Indonesian, Thai and Russian. Use concise game vocabulary and preserve `Gem`, numeric arguments and stable IDs unchanged.

- [ ] **Step 4: Replace shop/profile/mission/achievement hard-coded display names** with `localized(key, arguments)` and extend notification resolvers for exchange and achievement rewards.

- [ ] **Step 5: Run localization catalog tests, UI text scanner and localized Compose tests; verify GREEN**.

- [ ] **Step 6: Commit**

```bash
git add protocol/src shared/src app/src/androidTest
git commit -m "feat: localize economy progression content"
```

### Task 8: Seed compatibility, responsive validation and phase verification

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/DevFullAccountSeed.kt`
- Modify: `docs/roadmap.md`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/DevFullAccountSeedTest.kt`
- Test: `app/src/androidTest/java/com/hienthai/fastowin/CriticalFlowsUiTest.kt`

**Interfaces:**
- The dev full account owns all non-clan Phase 2 achievement rewards and enough Gem to test every Gold offer.
- Existing production accounts retain balances, equipped legacy cosmetics, purchase history and mission claims.

- [ ] **Step 1: Write a failing seed integration test** asserting the full account exposes the two-tab shop data, all new catalog items, a representative unlocked frame/title and no purchasable card/board item.

- [ ] **Step 2: Run the seed test and verify RED**.

- [ ] **Step 3: Update the seed idempotently** without deleting user-created accounts or resetting existing production data.

- [ ] **Step 4: Add responsive UI assertions** for small phone, large phone and tablet widths: two equal fixed-height tabs, non-overflowing offer cards/dialog, and collection grid cards with bounded width.

- [ ] **Step 5: Run the full verification suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :protocol:jvmTest :server:test :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :webApp:compileKotlinWasmJs --no-daemon
```

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 6: When an emulator is available, run focused connected UI tests**

```powershell
.\gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.CriticalFlowsUiTest,com.hienthai.fastowin.LocalizedSocialShopUiTest" --no-daemon
```

Expected: all selected tests pass.

- [ ] **Step 7: Update roadmap status** to mark Phase 2 complete and explicitly leave clan donation mission/achievement progress for Phase 3.

- [ ] **Step 8: Commit**

```bash
git add server/src docs/roadmap.md app/src/androidTest
git commit -m "test: verify economy progression phase"
```
