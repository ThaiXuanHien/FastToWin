package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.LocalizedMessageMapper
import com.hienthai.fastowin.data.network.InMemoryResumeTokenStore
import com.hienthai.fastowin.data.network.SocketSession
import com.hienthai.fastowin.data.network.SocketTransport
import com.hienthai.fastowin.data.network.GiftGateway
import com.hienthai.fastowin.data.network.StorePurchaseGateway
import com.hienthai.fastowin.platform.*
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.GiftClaimAllResult
import com.hienthai.fastowin.protocol.GiftClaimItem
import com.hienthai.fastowin.protocol.GiftClaimResult
import com.hienthai.fastowin.protocol.GiftMailboxPage
import com.hienthai.fastowin.protocol.GiftState
import com.hienthai.fastowin.protocol.MailboxGift
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.PublicTournamentQuery
import com.hienthai.fastowin.protocol.PublicTournamentSummary
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.TournamentFeeFilter
import com.hienthai.fastowin.protocol.TournamentVisibility
import com.hienthai.fastowin.navigation.GameMode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameStateTest {
    @Test
    fun `maintenance rematch rejection clears pending action and gives localized new room recovery`() {
        val error = ServerMessage.Error("MAINTENANCE_REMATCH_UNAVAILABLE", "legacy")
        for ((language, expected) in listOf(
            AppLanguage.ENGLISH to "This match was stopped for maintenance. Leave this room and create or join a new room.",
            AppLanguage.VIETNAMESE to "Trận này đã dừng do bảo trì. Hãy rời phòng rồi tạo hoặc tham gia phòng mới."
        )) {
            val localized = LocalizedMessageMapper(LocalizationService(language)).message(error)
            val failed = GameState(isGameOver = true, isRematchActionPending = true).withRematchError(error, localized)
            assertFalse(failed.isRematchActionPending)
            assertEquals(expected, failed.rematchNotice)
        }
    }

    @Test
    fun `conflated durable then failed receipt queues final profile behind older response`() = runTest {
        val controllers = mutableListOf<GameController>()
        try {
            for (durable in listOf(StorePurchaseStatus.GRANTED, StorePurchaseStatus.ALREADY_GRANTED)) {
                for (nondurable in listOf(StorePurchaseStatus.FAILED, StorePurchaseStatus.UNAVAILABLE)) {
                    val fixture = publicTournamentFixture()
                    controllers += fixture.controller
                    fixture.session.server(ServerMessage.SessionReady("player-1"))
                    runCurrent()
                    fixture.controller.refreshProfile()
                    runCurrent()
                    assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
                    val finished = mutableListOf<String>()
                    val billing = object : StoreBillingGateway {
                        override val state = MutableStateFlow(StoreBillingState(StorePlatform.GOOGLE_PLAY))
                        override val purchases = emptyFlow<PlatformStorePurchase>()
                        override fun connect(packages: List<GemPackageSnapshot>, sandboxEnabled: Boolean) = Unit
                        override fun purchase(productId: String, accountId: String?) = Unit
                        override fun finishPurchase(purchaseToken: String) { finished += purchaseToken }
                        override fun close() = Unit
                    }
                    val finalizer = StorePurchaseFinalizer(billing)
                    finalizer.setAccount("player-1", StorePurchaseGateway { request ->
                        ServerMessage.StorePurchaseResult(request.requestId, request.productId,
                            if (request.requestId == "paid-a") durable else nondurable, message = "", messageKey = "ServerUnavailable")
                    })
                    finalizer.enqueue(PlatformStorePurchase("paid-a", StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "receipt-a"))
                    finalizer.enqueue(PlatformStorePurchase("paid-b", StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "receipt-b"))
                    finalizer.retryPending() // No UI observation between A and B.
                    val observed = finalizer.state.value
                    assertEquals(1, observed.profileRevision)
                    assertEquals(nondurable, observed.result?.status)
                    assertEquals(listOf("receipt-a"), finished)
                    assertEquals(1, finalizer.pendingCount)
                    fixture.controller.onStorePurchaseFinalizationObserved("other-account", observed)
                    runCurrent()
                    assertNull(fixture.controller.uiState.value.storePurchaseResult)
                    assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
                    fixture.controller.onStorePurchaseFinalizationObserved("player-1", observed)
                    runCurrent()
                    assertEquals(nondurable, fixture.controller.uiState.value.storePurchaseResult?.status)
                    assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
                    fixture.session.server(ServerMessage.ProfileData(PlayerProfileSnapshot(
                        userId = "player-1", displayName = "Player", playerCode = "PLAYER01",
                        progression = PlayerProgressionSnapshot(gems = 0))))
                    runCurrent()
                    assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
                    fixture.session.server(ServerMessage.ProfileData(PlayerProfileSnapshot(
                        userId = "player-1", displayName = "Player", playerCode = "PLAYER01",
                        progression = PlayerProgressionSnapshot(gems = 80))))
                    runCurrent()
                    assertEquals(80, fixture.controller.uiState.value.profile?.progression?.gems)
                    finalizer.retryPending() // B failure changes feedback, never the durable revision.
                    fixture.controller.onStorePurchaseFinalizationObserved("player-1", finalizer.state.value)
                    runCurrent()
                    assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
                }
            }
        } finally { controllers.forEach(GameController::close) }
    }
    @Test
    fun `HTTP receipt refresh waits for earlier profile response and then requests authoritative balances`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.session.server(ServerMessage.SessionReady("player-1"))
        runCurrent()
        fixture.controller.refreshProfile()
        runCurrent()
        assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
        val observed = StoreFinalizationState("player-1", 1, ServerMessage.StorePurchaseResult("paid", "fasttowin_gems_80",
            com.hienthai.fastowin.protocol.StorePurchaseStatus.GRANTED, 80, "", "BillingGemsAdded"))
        fixture.controller.onStorePurchaseFinalizationObserved("player-1", observed)
        runCurrent()
        assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
        fixture.session.server(ServerMessage.ProfileData(PlayerProfileSnapshot(userId = "player-1", displayName = "Player", playerCode = "PLAYER01",
            progression = PlayerProgressionSnapshot(gems = 0))))
        runCurrent()
        assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
        fixture.session.server(ServerMessage.ProfileData(PlayerProfileSnapshot(userId = "player-1", displayName = "Player", playerCode = "PLAYER01",
            progression = PlayerProgressionSnapshot(gems = 80))))
        runCurrent()
        assertEquals(80, fixture.controller.uiState.value.profile?.progression?.gems)
        fixture.controller.onStorePurchaseFinalizationObserved("player-1", observed)
        runCurrent()
        assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
        fixture.controller.close()
    }
    @Test
    fun `route away during second claim batch still refreshes final authoritative profile`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.session.server(ServerMessage.SessionReady("player-1"))
        runCurrent()
        val gifts = (1..101).map { id ->
            MailboxGift("gift-$id", "Quà", "Gift", "Nội dung", "Message", 1, 0, 1000, 9000, GiftState.AVAILABLE)
        }
        val secondEntered = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val gateway = object : GiftGateway {
            var batches = 0
            override suspend fun page(page: Int, pageSize: Int) = GiftMailboxPage(
                gifts.drop(page * pageSize).take(pageSize), page, pageSize, gifts.size.toLong()
            )
            override suspend fun claim(id: String) = error("unused")
            override suspend fun claimAll(ids: List<String>): GiftClaimAllResult {
                batches++
                if (batches == 2) {
                    secondEntered.complete(Unit)
                    withContext(NonCancellable) { releaseSecond.await() }
                }
                return GiftClaimAllResult(ids.map { id ->
                    GiftClaimItem(id, result = GiftClaimResult(
                        gifts.first { it.campaignId == id }.copy(state = GiftState.CLAIMED), 1, 0, batches, 0
                    ))
                })
            }
        }
        fixture.controller.refreshProfile()
        runCurrent()
        assertTrue(fixture.controller.uiState.value.isProfileLoading)
        assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)

        val mailbox = GiftMailboxController(gateway, fixture.controller::refreshProfileAfterGiftClaim)
        mailbox.open()
        val claim = async { mailbox.claimAll() }
        secondEntered.await()
        try {
            runCurrent()
            assertEquals(2, gateway.batches)
            assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
            // The first 100 receipts already belong to this account while batch 2 is pending.
            fixture.session.server(ServerMessage.ProfileData(
                PlayerProfileSnapshot(userId = "player-1", displayName = "Me", playerCode = "ME000001")
            ))
            runCurrent()
            assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
            mailbox.reset() // Same-account route away: clear the view but retain wallet refreshes.
        } finally {
            releaseSecond.complete(Unit)
        }
        claim.await()
        runCurrent()
        assertFalse(mailbox.state.value.isOpen)
        assertTrue(mailbox.state.value.items.isEmpty())
        assertTrue(fixture.controller.uiState.value.isProfileLoading)
        fixture.session.server(ServerMessage.ProfileData(
            PlayerProfileSnapshot(
                userId = "player-1", displayName = "Me", playerCode = "ME000001",
                progression = PlayerProgressionSnapshot(gold = 100, experiencePoints = 100)
            )
        ))
        runCurrent()
        assertEquals(3, fixture.session.sent.filterIsInstance<ClientMessage.GetProfile>().size)
        assertTrue(fixture.controller.uiState.value.isProfileLoading)
        fixture.session.server(ServerMessage.ProfileData(
            PlayerProfileSnapshot(
                userId = "player-1", displayName = "Me", playerCode = "ME000001",
                progression = PlayerProgressionSnapshot(gold = 101, experiencePoints = 101)
            )
        ))
        runCurrent()
        assertEquals(101, fixture.controller.uiState.value.profile?.progression?.gold)
        assertEquals(101, fixture.controller.uiState.value.profile?.progression?.experiencePoints)
        assertFalse(fixture.controller.uiState.value.isProfileLoading)
        mailbox.close()
        fixture.controller.close()
    }

    @Test
    fun `public tournament data replaces list without clearing active tournament loading`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.openTournament()
        runCurrent()
        assertTrue(fixture.controller.uiState.value.isPublicTournamentsLoading)
        val first = publicTournament("first")
        fixture.session.server(ServerMessage.PublicTournamentsData(listOf(first)))
        runCurrent()
        assertEquals(listOf(first), fixture.controller.uiState.value.publicTournaments)
        assertFalse(fixture.controller.uiState.value.isPublicTournamentsLoading)
        assertTrue(fixture.controller.uiState.value.isTournamentLoading)

        val second = publicTournament("second")
        fixture.session.server(ServerMessage.PublicTournamentsData(listOf(second)))
        runCurrent()
        assertEquals(listOf(second), fixture.controller.uiState.value.publicTournaments)
        fixture.controller.close()
    }

    @Test
    fun `public tournament screen requests friends hub and unfiltered list`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.openTournament()
        runCurrent()
        assertTrue(ClientMessage.GetFriends in fixture.session.sent)
        assertTrue(ClientMessage.GetTournamentHub in fixture.session.sent)
        assertEquals(
            listOf(ClientMessage.GetPublicTournaments(PublicTournamentQuery())),
            fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>()
        )
        fixture.controller.close()
    }

    @Test
    fun `public tournament invalidation requests latest filters only while screen open`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.openTournament()
        runCurrent()
        fixture.controller.updatePublicTournamentFilters(
            PublicTournamentFilters(nameQuery = "  Sunday  ", gameMode = ProtocolGameMode.ORDER, fee = TournamentFeeFilter.FREE)
        )
        fixture.session.server(ServerMessage.PublicTournamentsInvalidated)
        runCurrent()
        val expected = ClientMessage.GetPublicTournaments(
            PublicTournamentQuery("Sunday", ProtocolGameMode.ORDER, null, TournamentFeeFilter.FREE)
        )
        assertEquals(expected, fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().last())
        assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().size)
        fixture.controller.closeTournament()
        fixture.session.server(ServerMessage.PublicTournamentsInvalidated)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().size)
        fixture.controller.close()
    }

    @Test
    fun `public tournament rapid search emits only final query after 250 milliseconds`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.openTournament()
        runCurrent()
        fixture.controller.updatePublicTournamentFilters(PublicTournamentFilters(nameQuery = "S"))
        runCurrent()
        advanceTimeBy(100)
        fixture.controller.updatePublicTournamentFilters(PublicTournamentFilters(nameQuery = "Su"))
        runCurrent()
        advanceTimeBy(100)
        fixture.controller.updatePublicTournamentFilters(PublicTournamentFilters(nameQuery = "Sun"))
        runCurrent()
        advanceTimeBy(249)
        runCurrent()
        assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(
            listOf(
                ClientMessage.GetPublicTournaments(PublicTournamentQuery()),
                ClientMessage.GetPublicTournaments(PublicTournamentQuery(nameQuery = "Sun"))
            ),
            fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>()
        )
        fixture.controller.close()
    }

    @Test
    fun `public tournament refresh bypasses pending debounce and join and create use visibility`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.openTournament()
        runCurrent()
        fixture.controller.updatePublicTournamentFilters(PublicTournamentFilters(nameQuery = " Finals "))
        fixture.controller.refreshPublicTournaments()
        fixture.controller.joinPublicTournament("tournament-1")
        fixture.controller.createTournament("Private", GameMode.ORDER, visibility = TournamentVisibility.PRIVATE)
        runCurrent()
        assertEquals(
            ClientMessage.GetPublicTournaments(PublicTournamentQuery(nameQuery = "Finals")),
            fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().last()
        )
        assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().size)
        assertTrue(ClientMessage.JoinPublicTournament("tournament-1") in fixture.session.sent)
        assertTrue(
            fixture.session.sent.filterIsInstance<ClientMessage.CreateTournament>()
                .any { it.visibility == TournamentVisibility.PRIVATE }
        )
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().size)
        fixture.controller.close()
    }

    @Test
    fun `public tournament creation defaults to public while explicit private remains private`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.createTournament("Open", GameMode.ORDER)
        fixture.controller.createTournament("Invite only", GameMode.ORDER, visibility = TournamentVisibility.PRIVATE)
        runCurrent()

        assertEquals(
            listOf(TournamentVisibility.PUBLIC, TournamentVisibility.PRIVATE),
            fixture.session.sent.filterIsInstance<ClientMessage.CreateTournament>().map { it.visibility }
        )
        fixture.controller.close()
    }

    @Test
    fun `public tournament send failure clears public list loading`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.session.close()
        runCurrent()

        fixture.controller.openTournament()
        assertTrue(fixture.controller.uiState.value.isPublicTournamentsLoading)
        runCurrent()

        assertFalse(fixture.controller.uiState.value.isPublicTournamentsLoading)
        assertEquals(emptyList(), fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>())
        fixture.controller.close()
    }

    @Test
    fun `public tournament unrelated server error keeps list loading`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.openTournament()
        runCurrent()
        fixture.session.server(ServerMessage.Error("UNRELATED_ERROR", "Unrelated action failed"))
        runCurrent()

        assertTrue(fixture.controller.uiState.value.isPublicTournamentsLoading)
        fixture.controller.close()
    }

    @Test
    fun `public tournament clean close recovers loading and refreshes latest query once on reconnect`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.session.server(ServerMessage.SessionReady("player-1"))
        runCurrent()
        fixture.controller.openTournament()
        runCurrent()
        assertEquals(1, fixture.session.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().size)
        assertTrue(fixture.controller.uiState.value.isPublicTournamentsLoading)

        fixture.session.close()
        runCurrent()
        assertFalse(fixture.controller.uiState.value.isPublicTournamentsLoading)
        fixture.controller.updatePublicTournamentFilters(PublicTournamentFilters(nameQuery = "Old"))
        fixture.controller.updatePublicTournamentFilters(PublicTournamentFilters(
            nameQuery = "  Finals  ", gameMode = ProtocolGameMode.ORDER, fee = TournamentFeeFilter.PAID
        ))
        advanceTimeBy(1_000)
        runCurrent()
        val reconnectedSession = fixture.transport.sessions.last()
        reconnectedSession.server(ServerMessage.SessionReady("player-1"))
        runCurrent()
        assertEquals(
            listOf(ClientMessage.GetPublicTournaments(PublicTournamentQuery(
                nameQuery = "Finals", gameMode = ProtocolGameMode.ORDER, fee = TournamentFeeFilter.PAID
            ))),
            reconnectedSession.sent.filterIsInstance<ClientMessage.GetPublicTournaments>()
        )
        assertTrue(fixture.controller.uiState.value.isPublicTournamentsLoading)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, reconnectedSession.sent.filterIsInstance<ClientMessage.GetPublicTournaments>().size)
        reconnectedSession.server(ServerMessage.PublicTournamentsData(emptyList()))
        runCurrent()
        assertFalse(fixture.controller.uiState.value.isPublicTournamentsLoading)
        fixture.controller.close()
    }

    @Test
    fun `scheduled creation serializes its instant and zone and manual stays optional`() = runTest {
        val fixture = publicTournamentFixture()
        val schedule = TournamentScheduleRequest(2_000_000, "Asia/Bangkok")
        fixture.controller.createTournament("Scheduled", GameMode.ORDER, schedule = schedule)
        runCurrent()
        assertEquals(schedule, fixture.session.sent.filterIsInstance<ClientMessage.CreateTournament>().single().schedule)
        fixture.controller.close()
    }

    @Test
    fun `check in targets active lobby and prevents concurrent duplicate requests`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.session.server(ServerMessage.SessionReady("player-1"))
        val now = epochMillis()
        val tournament = TournamentSnapshot(
            "scheduled", "Cup", "player-1", ProtocolGameMode.ORDER, TournamentPhase.LOBBY,
            players = listOf(TournamentPlayerSnapshot("player-1", "Tester", true, true)),
            schedule = TournamentScheduleSnapshot(now + 60_000, "UTC", now - 840_000, now - 840_000),
            serverTimeEpochMillis = now, createdAtEpochMillis = now
        )
        fixture.session.server(ServerMessage.TournamentUpdated(tournament))
        runCurrent()
        fixture.controller.checkInTournament()
        fixture.controller.checkInTournament()
        runCurrent()
        assertEquals(listOf(ClientMessage.CheckInTournament("scheduled")), fixture.session.sent.filterIsInstance<ClientMessage.CheckInTournament>())
        assertTrue(fixture.controller.uiState.value.isTournamentLoading)
        fixture.controller.close()
    }

    @Test
    fun `check in without scheduled lobby or after disconnect does not start loading`() = runTest {
        val fixture = publicTournamentFixture()
        fixture.controller.checkInTournament()
        runCurrent()
        assertFalse(fixture.controller.uiState.value.isTournamentLoading)
        fixture.session.close()
        runCurrent()
        fixture.controller.checkInTournament()
        runCurrent()
        assertTrue(fixture.session.sent.filterIsInstance<ClientMessage.CheckInTournament>().isEmpty())
        assertFalse(fixture.controller.uiState.value.isTournamentLoading)
        fixture.controller.close()
    }

    private fun TestScope.publicTournamentFixture(): PublicTournamentFixture {
        val transport = PublicTournamentTransport()
        val controller = GameController(
            "ws://test", InMemoryResumeTokenStore(), transport = transport, controllerScope = backgroundScope
        )
        controller.openRoomBrowser("Tester")
        runCurrent()
        return PublicTournamentFixture(controller, transport)
    }

    private fun publicTournament(id: String) = PublicTournamentSummary(
        tournamentId = id,
        name = id,
        hostPlayerId = "host",
        hostDisplayName = "Host",
        gameMode = ProtocolGameMode.ORDER,
        playerCount = 1,
        maxPlayers = 4,
        entryFee = 0,
        prizePool = 0,
        createdAtEpochMillis = 1L
    )

    @Test
    fun `matchmaking rejection stops search and preserves actionable reason`() {
        val rejected = GameState(
            lobbyStage = LobbyStage.MATCHMAKING,
            isSearching = true,
            isMatchmaking = true,
            matchmakingStartedAtMillis = 123L,
            error = "stale"
        ).withPlayFlowRejection(
            code = "TOURNAMENT_ACTIVE",
            message = "Hãy rời hoặc hoàn tất giải đấu hiện tại trước."
        )

        assertEquals(LobbyStage.SELECT_MODE, rejected.lobbyStage)
        assertFalse(rejected.isSearching)
        assertFalse(rejected.isMatchmaking)
        assertNull(rejected.matchmakingStartedAtMillis)
        assertEquals(
            PlayFlowRejection(
                code = "TOURNAMENT_ACTIVE",
                message = "Hãy rời hoặc hoàn tất giải đấu hiện tại trước."
            ),
            rejected.playFlowRejection
        )
        assertNull(rejected.error)
    }

    @Test
    fun `room creation rejection stops loading without changing room browser`() {
        val rejected = GameState(
            lobbyStage = LobbyStage.ROOM_BROWSER,
            isSearching = true,
            error = "stale"
        ).withPlayFlowRejection(
            code = "TOURNAMENT_ACTIVE",
            message = "Hãy rời hoặc hoàn tất giải đấu hiện tại trước khi tiếp tục."
        )

        assertEquals(LobbyStage.ROOM_BROWSER, rejected.lobbyStage)
        assertFalse(rejected.isSearching)
        assertEquals("TOURNAMENT_ACTIVE", rejected.playFlowRejection?.code)
        assertNull(rejected.error)
    }

    @Test
    fun `authoritative room snapshot keeps avatar and frame synchronized for every player`() {
        val snapshot = GameSnapshot(
            roomId = "room-1",
            roomName = "Avatar room",
            hostId = "player-1",
            gameMode = ProtocolGameMode.ORDER,
            phase = RoomPhase.WAITING,
            players = listOf(
                PlayerSnapshot(
                    id = "player-1",
                    name = "Me",
                    score = 0,
                    avatarId = "avatar-uploaded",
                    frameId = "frame_lightning"
                ),
                PlayerSnapshot(
                    id = "player-2",
                    name = "Opponent",
                    score = 0,
                    avatarId = "avatar-opponent",
                    frameId = "frame_wildfire"
                )
            )
        )

        val synchronized = GameState(
            player = PlayerState(
                name = "Stale",
                id = "player-1",
                avatarId = "avatar-stale",
                frameId = "frame_default"
            )
        ).applyAuthoritativeSnapshot(snapshot, "player-1")

        assertEquals("avatar-uploaded", synchronized.player.avatarId)
        assertEquals("frame_lightning", synchronized.player.frameId)
        assertEquals("avatar-opponent", synchronized.opponent.avatarId)
        assertEquals("frame_wildfire", synchronized.opponent.frameId)
    }

    @Test
    fun `returning home clears a failed room deep link and its error`() {
        val state = GameState(
            pendingRoomLinkId = "expired-room",
            pendingRoomLinkListVersion = 7,
            error = "Room no longer exists",
            isSearching = true,
            lobbyStage = LobbyStage.ROOM_BROWSER
        )

        val home = state.returnHome()

        assertNull(home.pendingRoomLinkId)
        assertEquals(home.roomListVersion, home.pendingRoomLinkListVersion)
        assertNull(home.error)
        assertFalse(home.isSearching)
        assertEquals(LobbyStage.SELECT_MODE, home.lobbyStage)
    }

    @Test
    fun `quota messages replace local snapshot with server truth`() {
        val staleQuota = quota(remaining = 3)
        val refreshedQuota = quota(remaining = 5)

        val loaded = GameState().withPlayQuota(ServerMessage.PlayQuotaData(staleQuota))
        val rewarded = loaded.withRewardedAdBonus(
            ServerMessage.RewardedAdBonusResult(
                requestId = "request-1",
                status = RewardedAdBonusStatus.GRANTED,
                quota = refreshedQuota
            ),
            notice = "2 online matches added"
        )

        assertEquals(5, rewarded.playQuota?.remainingMatches)
        assertEquals(RewardedAdBonusStatus.GRANTED, rewarded.rewardedAdBonusResult?.status)
        assertEquals("2 online matches added", rewarded.profileNotice)
        assertFalse(rewarded.showPlayQuotaExhaustedDialog)
    }

    @Test
    fun `play quota exhausted opens dialog without changing lobby destination`() {
        val state = GameState(
            lobbyStage = LobbyStage.ROOM_BROWSER,
            currentRoomId = null,
            isProfileOpen = false
        )

        val updated = state.withPlayQuotaError(
            ServerMessage.Error("PLAY_QUOTA_EXHAUSTED", "No online matches left"),
            localizedMessage = "No online matches left"
        )

        assertTrue(updated.showPlayQuotaExhaustedDialog)
        assertEquals(LobbyStage.ROOM_BROWSER, updated.lobbyStage)
        assertFalse(updated.isProfileOpen)
        assertEquals("No online matches left", updated.error)
    }

    @Test
    fun `rematch failure reducer uses localized message instead of legacy fallback`() {
        val error = ServerMessage.Error(
            code = "OPPONENT_LEFT",
            message = "Đối thủ đã rời phòng.",
        )
        val localized = LocalizedMessageMapper(LocalizationService(AppLanguage.ENGLISH)).message(error)

        val failed = GameState(isGameOver = true, isRematchActionPending = true)
            .withRematchError(error, localized)

        assertEquals("The action could not be completed. Please try again.", failed.rematchNotice)
        assertEquals("OPPONENT_LEFT", failed.rematchNoticeErrorCode)
    }

    @Test
    fun `older snapshot from current room cannot overwrite rematch state`() {
        val state = GameState(
            currentRoomId = "room-1",
            latestGameSequence = 42L,
            isRematchRequestedByOpponent = true
        )

        assertFalse(state.canApplyGameSnapshot("room-1", 41L))
        assertTrue(state.canApplyGameSnapshot("room-1", 42L))
        assertTrue(state.canApplyGameSnapshot("room-2", 1L))
    }

    @Test
    fun `pending social badge counts incoming friend and room invitations only`() {
        val incoming = listOf(
            friendRequest("incoming-1", "player-1"),
            friendRequest("incoming-2", "player-2")
        )
        val outgoing = listOf(friendRequest("outgoing-1", "player-3"))
        val roomInvitation = ServerMessage.RoomInvitation(
            invitationId = "room-invitation-1",
            fromUserId = "player-4",
            fromDisplayName = "Player 4",
            roomId = "room-1",
            roomName = "Test room",
            expiresAtEpochMillis = 10_000L
        )
        val state = GameState(
            social = FriendsSnapshot(incomingRequests = incoming, outgoingRequests = outgoing),
            roomInvitations = listOf(roomInvitation)
        )

        assertEquals(3, state.pendingSocialInvitationCount)
    }

    @Test
    fun `match start closes overlays and pending room invitation prompt`() {
        val invitation = ServerMessage.RoomInvitation(
            invitationId = "room-invitation-1",
            fromUserId = "player-1",
            fromDisplayName = "Player 1",
            roomId = "room-1",
            roomName = "Test room",
            expiresAtEpochMillis = 10_000L
        )
        val state = GameState(
            isProfileOpen = true,
            isProfileLoading = true,
            isLeaderboardOpen = true,
            isLeaderboardLoading = true,
            isFriendsOpen = true,
            isFriendsLoading = true,
            roomInvitationPrompt = invitation
        )

        val prepared = state.prepareForMatchStart()

        assertFalse(prepared.isProfileOpen)
        assertFalse(prepared.isProfileLoading)
        assertFalse(prepared.isLeaderboardOpen)
        assertFalse(prepared.isLeaderboardLoading)
        assertFalse(prepared.isFriendsOpen)
        assertFalse(prepared.isFriendsLoading)
        assertNull(prepared.roomInvitationPrompt)
    }

    @Test
    fun `notifications preserve the previous destination when opened and closed`() {
        val previous = GameState(
            isProfileOpen = true,
            isFriendsOpen = true,
            isClanOpen = true,
            isShopOpen = true,
            error = "Old error"
        )

        val opened = previous.openNotificationsOverlay()

        assertTrue(opened.isNotificationsOpen)
        assertTrue(opened.isProfileOpen)
        assertTrue(opened.isFriendsOpen)
        assertTrue(opened.isClanOpen)
        assertTrue(opened.isShopOpen)
        assertNull(opened.error)

        val closed = opened.closeNotificationsOverlay()

        assertFalse(closed.isNotificationsOpen)
        assertTrue(closed.isProfileOpen)
        assertTrue(closed.isFriendsOpen)
        assertTrue(closed.isClanOpen)
        assertTrue(closed.isShopOpen)
    }

    @Test
    fun `post match friend action recognizes recent opponent and pending requests`() {
        val opponentId = "player-2"
        val recent = RecentPlayerSnapshot(
            userId = opponentId,
            displayName = "Opponent",
            playerCode = "OPPONENT",
            lastPlayedAtEpochMillis = 1_000L,
            matchesPlayed = 1
        )
        val base = GameState(
            profile = PlayerProfileSnapshot(userId = "player-1", displayName = "Me", playerCode = "ME000001"),
            opponent = PlayerState("Opponent", id = opponentId),
            social = FriendsSnapshot(recentPlayers = listOf(recent))
        )
        assertEquals(PostMatchFriendStatus.AVAILABLE, base.postMatchFriendStatus)

        val outgoing = base.copy(
            social = base.social.copy(outgoingRequests = listOf(friendRequest("request-1", opponentId)))
        )
        assertEquals(PostMatchFriendStatus.REQUEST_SENT, outgoing.postMatchFriendStatus)

        val incoming = base.copy(
            social = base.social.copy(incomingRequests = listOf(friendRequest("request-2", opponentId)))
        )
        assertEquals(PostMatchFriendStatus.REQUEST_RECEIVED, incoming.postMatchFriendStatus)
    }

    @Test
    fun `friend relationship takes priority over recent opponent`() {
        val opponentId = "player-2"
        val state = GameState(
            profile = PlayerProfileSnapshot(userId = "player-1", displayName = "Me", playerCode = "ME000001"),
            opponent = PlayerState("Opponent", id = opponentId),
            social = FriendsSnapshot(
                friends = listOf(FriendSnapshot(opponentId, "Opponent", "OPPONENT")),
                recentPlayers = listOf(
                    RecentPlayerSnapshot(opponentId, "Opponent", "OPPONENT", lastPlayedAtEpochMillis = 1_000L, matchesPlayed = 2)
                )
            )
        )

        assertEquals(PostMatchFriendStatus.FRIEND, state.postMatchFriendStatus)
    }

    @Test
    fun `wrong selection updates local mistake count immediately`() {
        val state = GameState(
            currentTarget = 7,
            player = PlayerState("Me", currentTarget = 7, wrongSelections = 2, combo = 4)
        )

        val updated = state.registerOptimisticNumberSelection(9)

        assertEquals(3, updated.player.wrongSelections)
        assertEquals(0, updated.player.combo)
        assertEquals(state, state.registerOptimisticNumberSelection(7))
    }

    @Test
    fun `connection loss during play cannot leak a rematch notice into results`() {
        val playing = GameState(
            isMatchStarted = true,
            currentRoomId = "room-1",
            currentTarget = 4,
            player = PlayerState("Me", score = 20),
            opponent = PlayerState("Opponent", score = 10)
        )

        for (code in listOf("CONNECTION_NOT_READY", "CONNECTION_FAILED", "SEND_FAILED")) {
            val disconnected = playing.withRematchError(ServerMessage.Error(code, "Connection failed"))
            assertEquals(playing, disconnected)

            val recovered = disconnected.withReadySession("player-1")
            val result = recovered.copy(isGameOver = true)
            assertNull(result.rematchNotice)
            assertNull(result.rematchNoticeErrorCode)
            assertEquals("room-1", result.currentRoomId)
            assertEquals(4, result.currentTarget)
            assertEquals(20, result.player.score)
            assertEquals(10, result.opponent.score)
        }
    }

    @Test
    fun `background connection errors preserve confirmed rematch invitations`() {
        for (requestedByMe in listOf(true, false)) {
            val waiting = GameState(
                isGameOver = true,
                isRematchRequestedByMe = requestedByMe,
                isRematchRequestedByOpponent = !requestedByMe,
                rematchExpiresAtEpochMillis = 10_000L,
                rematchNotice = "Confirmed invitation"
            )
            val updated = waiting.withRematchError(
                ServerMessage.Error("CONNECTION_NOT_READY", "Connection failed")
            )

            assertEquals(waiting, updated)
            assertEquals("Confirmed invitation", updated.withReadySession("me").rematchNotice)
        }
    }

    @Test
    fun `failed pending rematch becomes retryable and clears its network notice on resume`() {
        for (code in listOf("CONNECTION_NOT_READY", "CONNECTION_FAILED", "SEND_FAILED")) {
            val pending = GameState(
                isGameOver = true,
                isRematchActionPending = true,
                isRematchRequestedByMe = true,
                rematchNotice = "Sending request",
                error = "Old error"
            )
            val failed = pending.withRematchError(ServerMessage.Error(code, "Connection failed"))

            assertFalse(failed.isRematchActionPending)
            assertFalse(failed.isRematchRequestedByMe)
            assertEquals("Connection failed", failed.rematchNotice)
            assertEquals(code, failed.rematchNoticeErrorCode)

            val recovered = failed.withReadySession("me")
            assertNull(recovered.rematchNotice)
            assertNull(recovered.rematchNoticeErrorCode)
            assertNull(recovered.error)
            assertFalse(recovered.isSearching)
            assertEquals("me", recovered.player.id)
        }
    }

    @Test
    fun `session recovery preserves non network rematch failures`() {
        val failed = GameState(isGameOver = true, isRematchActionPending = true)
            .withRematchError(ServerMessage.Error("OPPONENT_LEFT", "Opponent left"))

        val recovered = failed.withReadySession("me")

        assertFalse(recovered.isRematchActionPending)
        assertEquals("Opponent left", recovered.rematchNotice)
        assertEquals("OPPONENT_LEFT", recovered.rematchNoticeErrorCode)
    }

    @Test
    fun `session recovery does not classify rematch notices by their text`() {
        val state = GameState(isGameOver = true, rematchNotice = "Connection failed")
        assertEquals("Connection failed", state.withReadySession("me").rematchNotice)
    }

    @Test
    fun `transport failures remain visible until an authenticated session recovers`() {
        for (code in listOf("CONNECTION_NOT_READY", "CONNECTION_FAILED", "SEND_FAILED")) {
            val failed = GameState(error = globalErrorMessage(ServerMessage.Error(code, "Connection failed"), "Unavailable"))
            assertEquals("Unavailable", failed.error)
            assertNull(failed.withReadySession("me").error)
        }
        assertEquals(
            "Wrong password",
            globalErrorMessage(ServerMessage.Error("WRONG_PASSWORD", "Wrong password"), "Wrong password")
        )
    }

    @Test
    fun `unrelated server errors leave rematch state unchanged`() {
        val state = GameState(
            isGameOver = true,
            isRematchActionPending = true,
            isRematchRequestedByOpponent = true,
            rematchNotice = "Accepting invitation"
        )
        assertEquals(state, state.withRematchError(ServerMessage.Error("WRONG_PASSWORD", "Wrong password")))
    }

    private fun friendRequest(requestId: String, userId: String) = FriendRequestSnapshot(
        requestId = requestId,
        userId = userId,
        displayName = userId,
        playerCode = userId.uppercase()
    )

    private fun quota(remaining: Int) = PlayQuotaSnapshot(
        quotaDate = "2026-09-12",
        matchesConsumed = 10 - remaining,
        remainingMatches = remaining,
        nextResetAtEpochMillis = 1_789_148_400_000L,
        rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
    )
}

private data class PublicTournamentFixture(
    val controller: GameController,
    val transport: PublicTournamentTransport
) {
    val session get() = transport.sessions.first()
}

private class PublicTournamentTransport : SocketTransport {
    val sessions = mutableListOf(PublicTournamentSession())
    private var firstAttempt = true

    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        val session = if (firstAttempt) {
            firstAttempt = false
            sessions.first()
        } else {
            PublicTournamentSession().also(sessions::add)
        }
        block(session)
    }
}

private class PublicTournamentSession : SocketSession {
    override val incoming = Channel<Frame>(Channel.UNLIMITED)
    val sent = mutableListOf<ClientMessage>()

    override suspend fun send(frame: Frame) {
        sent += ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText())
    }

    override suspend fun close() { incoming.close() }

    override suspend fun closeReason(): String? = null

    suspend fun server(message: ServerMessage) {
        incoming.send(Frame.Text(ProtocolJson.encodeToString(message)))
    }
}
