# Public Tournaments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add public/private tournament creation, filtered public discovery, confirmed paid joining, and invitations for offline friends.

**Architecture:** Extend the existing serializable tournament model and WebSocket command stream. The server's in-memory active tournament registry remains authoritative and PostgreSQL continues persisting complete snapshots as JSON; one shared admission function handles invitation and public joins under the existing mutex. Compose renders discovery from explicit state and reissues the latest filter after an invalidation.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization, Ktor WebSockets, PostgreSQL JSONB snapshots, Jetpack Compose Multiplatform, Android Compose UI tests, Kotlin coroutines tests.

**Spec:** `docs/superpowers/specs/2026-09-23-public-tournaments-and-pwa-input-design.md`

## Global Constraints

- Existing serialized tournament snapshots without visibility must decode as `PRIVATE`.
- Newly created tournaments default to `PUBLIC`.
- Discovery returns only public `LOBBY` tournaments with at least one free slot, capped at 50.
- Paid admission must be idempotent and must not modify wallet or prize pool on failure.
- Invitation acceptance and public joining must share the same internal admission operation.
- Existing unrelated local changes in `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt` must be preserved and reviewed before editing that file.
- User-visible copy is required in Vietnamese and English.

---

### Task 1: Protocol model and backward compatibility

**Files:**
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt:159-193`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt:834-860`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt:1146-1160`
- Test: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/protocol/GameProtocolCompatibilityTest.kt`

**Interfaces:**
- Produces: `TournamentVisibility`, `TournamentFeeFilter`, `PublicTournamentSummary`, `PublicTournamentQuery`.
- Produces: `ClientMessage.GetPublicTournaments`, `ClientMessage.JoinPublicTournament`.
- Produces: `ServerMessage.PublicTournamentsData`, `ServerMessage.PublicTournamentsInvalidated`.
- Extends: `ClientMessage.CreateTournament(..., visibility: TournamentVisibility = PUBLIC)` and `TournamentSnapshot.visibility: TournamentVisibility = PRIVATE`.

- [ ] **Step 1: Write compatibility and round-trip tests**

Add tests that decode a literal legacy snapshot without a visibility field and round-trip the new messages:

```kotlin
@Test
fun `legacy tournament snapshot defaults to private`() {
    val decoded = ProtocolJson.decodeFromString<TournamentSnapshot>(
        """{"tournamentId":"00000000-0000-0000-0000-000000000001","name":"Legacy","hostPlayerId":"00000000-0000-0000-0000-000000000002","gameMode":"ORDER","phase":"LOBBY","createdAtEpochMillis":1}"""
    )
    assertEquals(TournamentVisibility.PRIVATE, decoded.visibility)
}

@Test
fun `public tournament query and response round trip`() {
    val query = ClientMessage.GetPublicTournaments(
        PublicTournamentQuery("cup", ProtocolGameMode.ORDER, 8, TournamentFeeFilter.PAID)
    )
    assertEquals(query, ProtocolJson.decodeFromString<ClientMessage>(ProtocolJson.encodeToString<ClientMessage>(query)))

    val response: ServerMessage = ServerMessage.PublicTournamentsData(
        listOf(PublicTournamentSummary(
            tournamentId = "00000000-0000-0000-0000-000000000001",
            name = "Cup",
            hostPlayerId = "00000000-0000-0000-0000-000000000002",
            hostDisplayName = "Host",
            gameMode = ProtocolGameMode.ORDER,
            playerCount = 2,
            maxPlayers = 4,
            entryFee = 100,
            prizePool = 200,
            createdAtEpochMillis = 1L,
        ))
    )
    assertEquals(response, ProtocolJson.decodeFromString<ServerMessage>(ProtocolJson.encodeToString(response)))
}
```

- [ ] **Step 2: Run protocol tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :protocol:jvmTest --tests "*GameProtocolCompatibilityTest" --no-daemon
```

Expected: compilation fails because the new tournament protocol types do not exist.

- [ ] **Step 3: Add the minimal protocol types**

Add serializable types and commands with these signatures:

```kotlin
@Serializable enum class TournamentVisibility { PUBLIC, PRIVATE }
@Serializable enum class TournamentFeeFilter { ALL, FREE, PAID }

@Serializable
data class PublicTournamentQuery(
    val nameQuery: String = "",
    val gameMode: ProtocolGameMode? = null,
    val maxPlayers: Int? = null,
    val fee: TournamentFeeFilter = TournamentFeeFilter.ALL,
)

@Serializable
data class PublicTournamentSummary(
    val tournamentId: String,
    val name: String,
    val hostPlayerId: String,
    val hostDisplayName: String,
    val gameMode: ProtocolGameMode,
    val playerCount: Int,
    val maxPlayers: Int,
    val entryFee: Int,
    val prizePool: Int,
    val createdAtEpochMillis: Long,
)
```

Add `visibility: TournamentVisibility = TournamentVisibility.PRIVATE` to `TournamentSnapshot`, but use `TournamentVisibility.PUBLIC` as the default in `CreateTournament`. Add serial names `get_public_tournaments`, `join_public_tournament`, `public_tournaments_data`, and `public_tournaments_invalidated`.

Use these command payloads so joining cannot trust stale client-side fee or capacity data:

```kotlin
@Serializable
@SerialName("get_public_tournaments")
data class GetPublicTournaments(val query: PublicTournamentQuery = PublicTournamentQuery()) : ClientMessage()

@Serializable
@SerialName("join_public_tournament")
data class JoinPublicTournament(val tournamentId: String) : ClientMessage()
```

- [ ] **Step 4: Run protocol tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit the protocol slice**

```powershell
git add protocol/src/commonMain/kotlin/com/hienthai/fastowin/protocol/GameProtocol.kt protocol/src/commonTest/kotlin/com/hienthai/fastowin/protocol/GameProtocolCompatibilityTest.kt
git commit -m "feat: add public tournament protocol"
```

---

### Task 2: Server discovery and shared admission

**Files:**
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt:250-310`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt:470-720`
- Modify: `server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt:3670-3725`
- Test: `server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt`

**Interfaces:**
- Consumes: protocol types from Task 1.
- Produces: `publicTournaments(playerId, query)` behavior and `admitTournamentPlayer(...)` shared admission logic.
- Produces: invalidation deliveries after public-list mutations.

- [ ] **Step 1: Write failing discovery tests**

Create public and private tournaments, then assert filtering and ordering:

```kotlin
@Test
fun `public discovery returns only open public tournaments and applies filters`() = runTest {
    val engine = GameEngine()
    val host = engine.connectOnlineTestPlayer("Host")
    val guest = engine.connectOnlineTestPlayer("Guest")
    engine.handle(host.playerId, ClientMessage.CreateTournament(
        "Public Cup", ProtocolGameMode.ORDER, 100, 4, TournamentVisibility.PUBLIC
    ))
    engine.handle(guest.playerId, ClientMessage.CreateTournament(
        "Private Cup", ProtocolGameMode.ORDER, 0, 4, TournamentVisibility.PRIVATE
    ))

    val list = engine.handle(
        guest.playerId,
        ClientMessage.GetPublicTournaments(PublicTournamentQuery(nameQuery = "public", fee = TournamentFeeFilter.PAID))
    ).map(Delivery::message).filterIsInstance<ServerMessage.PublicTournamentsData>().single().tournaments

    assertEquals(listOf("Public Cup"), list.map { it.name })
}
```

Add separate tests for the 50-item cap and “fewest remaining slots, then newest” ordering.

- [ ] **Step 2: Write failing admission tests**

Cover free joining, paid joining, insufficient funds, private rejection, and two players racing for the last slot. Use `async(start = CoroutineStart.LAZY)` plus `awaitAll()` for the race and assert only one response contains the joined snapshot. Capture wallet mutation calls and assert one `TOURNAMENT_ENTRY` call for the admitted player.

- [ ] **Step 3: Run server tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :server:test --tests "*GameEngineTest*public tournament*" --no-daemon
```

Expected: compilation fails on new commands or assertions fail because discovery/join behavior is absent.

- [ ] **Step 4: Implement filtered discovery**

Handle `GetPublicTournaments` before the general mutex dispatch. Under the tournament mutex, map eligible records into `PublicTournamentSummary`, apply case-insensitive trimmed name matching and exact optional filters, sort with:

```kotlin
compareBy<PublicTournamentSummary> { it.maxPlayers - it.playerCount }
    .thenByDescending { it.createdAtEpochMillis }
    .thenBy { it.tournamentId }
```

Take 50 and deliver only to the requesting player.

- [ ] **Step 5: Extract one shared admission operation**

Inside the tournament mutex, introduce a private operation with an explicit admission source:

```kotlin
private enum class TournamentAdmissionSource { INVITATION, PUBLIC_DISCOVERY }

private suspend fun admitTournamentPlayerLocked(
    playerId: String,
    tournament: Tournament,
    source: TournamentAdmissionSource,
): List<Delivery>
```

The operation validates visibility only for `PUBLIC_DISCOVERY`, performs all common capacity/busy/payment checks, appends exactly one participant, updates prize pool only after an applied wallet mutation, creates the tournament update, and records the snapshot for persistence. Adapt invitation acceptance to call this function after validating/removing its invitation. Handle `JoinPublicTournament` by resolving the tournament and calling the same function.

- [ ] **Step 6: Add invalidation deliveries**

After a public tournament is created, joined, left, cancelled, started, or filled, append:

```kotlin
Delivery(
    ServerMessage.PublicTournamentsInvalidated,
    sessionsByPlayerId.values.filter { it.isConnected && it.resumeToken == null }
        .mapTo(mutableSetOf()) { it.playerId }
)
```

Do not expose private tournament mutations through discovery; emitting an invalidation for them is unnecessary.

- [ ] **Step 7: Run focused and regression server tests**

```powershell
.\gradlew.bat :server:test --tests "*GameEngineTest*public tournament*" --tests "*GameEngineTest*private*tournament*" --no-daemon
```

Expected: PASS, including existing private tournament flows.

- [ ] **Step 8: Commit the server slice**

```powershell
git add server/src/main/kotlin/com/hienthai/fastowin/server/GameEngine.kt server/src/test/kotlin/com/hienthai/fastowin/server/GameEngineTest.kt
git commit -m "feat: discover and join public tournaments"
```

---

### Task 3: Shared state and controller integration

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt:190-205`
- Modify carefully: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt:423-484`
- Modify carefully: `shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt:1208-1255`
- Test: `shared/src/commonTest/kotlin/com/hienthai/fastowin/state/GameStateTest.kt`

**Interfaces:**
- Consumes: public tournament protocol from Task 1.
- Produces: `PublicTournamentFilters`, public list/loading state, create visibility, filter refresh, and join actions used by UI.

- [ ] **Step 1: Inspect and preserve the existing local GameController change**

Run:

```powershell
git diff -- shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt
```

Record the existing hunk boundaries. Do not stage or rewrite unrelated hunks.

- [ ] **Step 2: Write failing reducer/controller tests**

Test that `PublicTournamentsData` replaces the list and clears loading, and that `PublicTournamentsInvalidated` requests the latest filter only while the tournament screen is open. Also use virtual time to prove rapid search changes emit only the final query after a 250 ms debounce. Use the existing fake socket test harness and assert the exact `GetPublicTournaments(currentFilters.toQuery())` message.

- [ ] **Step 3: Run shared tests and verify RED**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :shared:testAndroidHostTest --tests "*GameStateTest*public tournament*" --no-daemon
```

Expected: compilation failure because the public list state/actions are absent.

- [ ] **Step 4: Add state and controller actions**

Add immutable state:

```kotlin
data class PublicTournamentFilters(
    val nameQuery: String = "",
    val gameMode: ProtocolGameMode? = null,
    val maxPlayers: Int? = null,
    val fee: TournamentFeeFilter = TournamentFeeFilter.ALL,
) {
    fun toQuery() = PublicTournamentQuery(nameQuery.trim(), gameMode, maxPlayers, fee)
}
```

Add `publicTournaments`, `publicTournamentFilters`, and `isPublicTournamentsLoading` to `GameState`. Update `openTournament()` to request friends, hub, and the unfiltered public list. Add `updatePublicTournamentFilters`, `refreshPublicTournaments`, and `joinPublicTournament`. Extend `createTournament` with `visibility: TournamentVisibility`. Store one controller-owned filter `Job`; cancel and replace it on each filter change, delay 250 ms, then send the latest query only while the tournament screen remains open. Explicit refresh and server invalidation bypass the debounce.

When receiving invalidation, reissue the latest query only if `isTournamentOpen`. When receiving data, replace the list and clear list loading without changing the active tournament loading flag.

- [ ] **Step 5: Run shared tests and verify GREEN**

Run the command from Step 3. Expected: PASS.

- [ ] **Step 6: Commit only the intended shared hunks**

Use interactive staging so the unrelated pre-existing change is excluded:

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameState.kt
git add -p shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt
git add shared/src/commonTest/kotlin/com/hienthai/fastowin/state/GameStateTest.kt
git commit -m "feat: manage public tournament discovery state"
```

---

### Task 4: Tournament discovery, visibility, confirmation, and offline invitations UI

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TournamentScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt:1182-1191`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/ProtocolMessageKeys.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/SocialShopCatalogTexts.kt`
- Modify: `protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ProtocolMessageCatalogTexts.kt`
- Test: `app/src/androidTest/java/com/hienthai/fastowin/TournamentScreenTest.kt`
- Test: `protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/SocialScreenLocalizationTest.kt`

**Interfaces:**
- Consumes: state/actions from Task 3.
- Produces: public discovery UI, paid join confirmation, public/private selector, and offline friend invitation rows.

- [ ] **Step 1: Write failing Compose UI tests**

Add focused tests with stable tags:

```kotlin
@Test
fun publicTournamentRequiresConfirmationBeforePaidJoin() {
    var joined: String? = null
    setTournamentContent(publicTournamentState(entryFee = 100), onJoinPublic = { joined = it })
    composeRule.onNodeWithTag("public_tournament_join_tournament-1").performClick()
    composeRule.runOnIdle { assertNull(joined) }
    composeRule.onNodeWithTag("confirm_public_tournament_join").performClick()
    composeRule.runOnIdle { assertEquals("tournament-1", joined) }
}

@Test
fun offlineFriendCanBeInvitedFromLobby() {
    setTournamentContent(hostLobbyState(friendPresence = FriendPresence.OFFLINE))
    composeRule.onNodeWithTag("tournament_invite_friend-1").assertIsDisplayed()
}
```

Also test free direct join, visibility default/selection, filter callbacks, and the no-eligible-friends explanation.

- [ ] **Step 2: Run UI tests and verify RED**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.TournamentScreenTest" --no-daemon
```

Expected: compilation fails on the new callbacks/types or nodes are absent.

- [ ] **Step 3: Implement discovery components**

Extend `TournamentScreen` with callbacks for filter changes, refresh, and public join. Add focused private composables `PublicTournamentSection`, `PublicTournamentCard`, `TournamentFilters`, and `PaidTournamentJoinDialog`. Keep each composable dependent only on immutable values and callbacks.

Use tags:

```text
public_tournament_search
public_tournament_filter_mode
public_tournament_filter_size
public_tournament_filter_fee
public_tournament_refresh
public_tournament_<id>
public_tournament_join_<id>
confirm_public_tournament_join
```

Render this section before invitations only when `activeTournament == null`.

- [ ] **Step 4: Add visibility choice and offline friend behavior**

Keep `var visibility by rememberSaveable { mutableStateOf(TournamentVisibility.PUBLIC) }`. Add equal-width Public/Private chips and pass visibility through `onCreate`. Change eligible friends to:

```kotlin
val availableFriends = state.social.friends.filter { friend ->
    tournament.players.none { it.playerId == friend.userId }
}
```

Always render the invitation section for a host-owned non-full lobby. If the list is empty, render the localized “add friends first” message; otherwise give each row `testTag("tournament_invite_${friend.userId}")`.

- [ ] **Step 5: Add Vietnamese and English copy and localization coverage**

Add keys for visibility, public list title/empty/loading, search, filters, join confirmation, stale/full errors, and empty friend invitations. Map server error codes for no-longer-public, full, closed, already-joined, busy, and insufficient-Gold through `ProtocolMessageKeys`. Add exact English and Vietnamese entries to the UI and protocol-message catalogs, then extend `SocialScreenLocalizationTest` to assert every new UI key is explicit in both supported languages.

- [ ] **Step 6: Wire the controller callbacks**

Update `FastToWinApp` so `TournamentScreen` receives `controller::updatePublicTournamentFilters`, `controller::refreshPublicTournaments`, and `controller::joinPublicTournament`; update `onCreate` to include visibility.

- [ ] **Step 7: Run UI and localization tests and verify GREEN**

```powershell
.\gradlew.bat :protocol:jvmTest --tests "*SocialScreenLocalizationTest" :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.TournamentScreenTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit the UI slice**

```powershell
git add shared/src/commonMain/kotlin/com/hienthai/fastowin/ui/screens/TournamentScreen.kt shared/src/commonMain/kotlin/com/hienthai/fastowin/FastToWinApp.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/TextKey.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/ProtocolMessageKeys.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/SocialShopCatalogTexts.kt protocol/src/commonMain/kotlin/com/hienthai/fastowin/localization/catalogs/ProtocolMessageCatalogTexts.kt app/src/androidTest/java/com/hienthai/fastowin/TournamentScreenTest.kt protocol/src/commonTest/kotlin/com/hienthai/fastowin/localization/SocialScreenLocalizationTest.kt
git commit -m "feat: add public tournament discovery UI"
```

---

### Task 5: Integrated verification

**Files:**
- Verify only; modify failing source/test files only when the failure is caused by this feature.

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: one verified public-tournament feature ready for the PWA plans and deployment.

- [ ] **Step 1: Run the combined non-device suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :protocol:jvmTest :server:test :shared:testAndroidHostTest :app:compileDevDebugAndroidTestKotlin :webApp:compileKotlinWasmJs --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the tournament device suite**

```powershell
.\gradlew.bat :app:connectedDevDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.hienthai.fastowin.TournamentScreenTest" --no-daemon
```

Expected: BUILD SUCCESSFUL and all TournamentScreen tests pass.

- [ ] **Step 3: Review the final diff and local-change boundary**

```powershell
git status --short
git diff HEAD~4..HEAD -- protocol server shared app
git diff -- shared/src/commonMain/kotlin/com/hienthai/fastowin/state/GameController.kt
```

Confirm no pre-existing GameController hunk was accidentally committed.

- [ ] **Step 4: Commit any test-only integration corrections**

If integration required an in-scope correction, stage its exact files and commit:

```powershell
git commit -m "test: verify public tournament flow"
```

If no correction was required, do not create an empty commit.
