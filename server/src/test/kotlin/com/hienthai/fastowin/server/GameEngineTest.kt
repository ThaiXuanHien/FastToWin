package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import com.hienthai.fastowin.protocol.RematchEvent
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.UUID

class GameEngineTest {
    @Test
    fun `daily check in refreshes profile and duplicate request gives no xp`() = runTest {
        val playerId = UUID.randomUUID().toString()
        var profile = PlayerProfileSnapshot(
            displayName = "Hiền",
            playerCode = "HIEN001",
            progression = PlayerProgressionSnapshot(
                dailyCheckIn = DailyCheckInSnapshot(todayRewardXp = 5)
            )
        )
        var claimed = false
        val repository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = profile
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
            override suspend fun claimDailyCheckIn(playerId: String): DailyCheckInClaimResult {
                if (claimed) return DailyCheckInClaimResult(claimed = false, rewardXp = 0)
                claimed = true
                profile = profile.copy(
                    progression = profile.progression.copy(
                        experiencePoints = 5,
                        currentLevelExperience = 5,
                        dailyCheckIn = DailyCheckInSnapshot(
                            claimedToday = true,
                            cycleDay = 1,
                            todayRewardXp = 5,
                            nextRewardXp = 10,
                            currentStreak = 1,
                            bestStreak = 1,
                            totalCheckIns = 1
                        )
                    )
                )
                return DailyCheckInClaimResult(claimed = true, rewardXp = 5)
            }
        }
        val engine = GameEngine(playerProfileRepository = repository)
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(playerId), "Hiền"))

        val first = engine.handle(playerId, ClientMessage.ClaimDailyCheckIn).map(Delivery::message)
        assertEquals(5, first.filterIsInstance<ServerMessage.ProfileData>().single().profile.progression.experiencePoints)
        assertEquals(5, first.filterIsInstance<ServerMessage.DailyCheckInResult>().single().rewardXp)
        assertTrue(first.filterIsInstance<ServerMessage.DailyCheckInResult>().single().claimed)

        val duplicate = engine.handle(playerId, ClientMessage.ClaimDailyCheckIn).map(Delivery::message)
            .filterIsInstance<ServerMessage.DailyCheckInResult>().single()
        assertFalse(duplicate.claimed)
        assertEquals(0, duplicate.rewardXp)

        val guest = engine.connectGuest("Khách", null)
        val denied = engine.handle(guest.playerId, ClientMessage.ClaimDailyCheckIn)
        assertEquals("ACCOUNT_REQUIRED", assertIs<ServerMessage.Error>(denied.single().message).code)
    }

    @Test
    fun `matchmaking pairs connected accounts with nearby elo`() = runTest {
        val firstId = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val profiles = mapOf(
            firstId to PlayerProfileSnapshot(
                "First", "FIRST001",
                statistics = com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot(eloRating = 1_020)
            ),
            secondId to PlayerProfileSnapshot(
                "Second", "SECOND01",
                statistics = com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot(eloRating = 1_080)
            )
        )
        val profileRepository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = profiles[playerId]
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
        }
        val engine = GameEngine(playerProfileRepository = profileRepository)
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(firstId), "First"))
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(secondId), "Second"))

        val waiting = engine.handle(firstId, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
            .map(Delivery::message).filterIsInstance<ServerMessage.MatchmakingStatus>().single()
        assertTrue(waiting.isSearching)
        assertEquals(100, waiting.ratingRange)

        val matched = engine.handle(secondId, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
            .map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single()
        assertEquals(setOf(firstId, secondId), matched.game.players.map { it.id }.toSet())
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.PLAYING, matched.game.phase)
        assertEquals(50, matched.game.numbers.size)
    }

    @Test
    fun `connecting upgraded account clears guest resume token in memory`() = runTest {
        val repository = InMemoryGuestIdentityRepository()
        val engine = GameEngine(identityRepository = repository)
        val guest = engine.connectGuest("Guest", null)

        val account = engine.connectAccount(
            AuthenticatedAccount(UUID.fromString(guest.playerId), "Guest")
        )

        assertEquals(guest.playerId, account.playerId)
        assertEquals(null, account.resumeToken)
    }

    @Test
    fun `guest can resume the same server identity`() = runTest {
        val engine = GameEngine()
        val first = engine.connectGuest("Hiền", null)
        val resumed = engine.connectGuest("Hiền", first.resumeToken)

        assertEquals(first.playerId, resumed.playerId)
        assertEquals(first.resumeToken, resumed.resumeToken)
    }

    @Test
    fun `guest identity survives a game engine restart when repository is persistent`() = runTest {
        val repository = InMemoryGuestIdentityRepository()
        val first = GameEngine(identityRepository = repository).connectGuest("Hiền", null)

        val resumed = GameEngine(identityRepository = repository)
            .connectGuest("Hiền mới", first.resumeToken)

        assertEquals(first.playerId, resumed.playerId)
        assertEquals(first.resumeToken, resumed.resumeToken)
    }

    @Test
    fun `active match survives engine restart and keeps request idempotency`() = runTest {
        val identityRepository = InMemoryGuestIdentityRepository()
        val activeRoomRepository = InMemoryActiveRoomRepository()
        val firstEngine = GameEngine(
            identityRepository = identityRepository,
            activeRoomRepository = activeRoomRepository
        )
        val host = firstEngine.connectGuest("Host", null)
        val guest = firstEngine.connectGuest("Guest", null)
        val created = firstEngine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Restart room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(firstEngine, host.playerId, guest.playerId, created.roomId)

        val rejectedBeforeRestart = firstEngine.handle(
            host.playerId,
            ClientMessage.SelectNumber(created.roomId, 50, "stable-wrong-request")
        ).map(Delivery::message)
        val beforeRestart = firstEngine.handle(
            guest.playerId,
            ClientMessage.SelectNumber(created.roomId, 1, "accepted-before-restart")
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameStateUpdated>().single().game

        val restartedEngine = GameEngine(
            identityRepository = identityRepository,
            activeRoomRepository = activeRoomRepository
        )
        val resumedHost = restartedEngine.connectGuest("Host renamed", host.resumeToken)
        val resumedGuest = restartedEngine.connectGuest("Guest", guest.resumeToken)
        val restored = assertNotNull(resumedHost.currentGame)

        assertEquals(created.roomId, restored.roomId)
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.PLAYING, restored.phase)
        assertEquals(beforeRestart.numbers, restored.numbers)
        assertEquals(listOf(1), restored.selectedNumbers)
        assertEquals(2, restored.currentTarget)
        assertEquals(10, restored.players.single { it.id == guest.playerId }.score)
        assertEquals(created.roomId, resumedGuest.currentGame?.roomId)

        val duplicateAfterRestart = restartedEngine.handle(
            host.playerId,
            ClientMessage.SelectNumber(created.roomId, 50, "stable-wrong-request")
        ).map(Delivery::message)
        assertEquals(rejectedBeforeRestart, duplicateAfterRestart)

        val continued = restartedEngine.handle(
            host.playerId,
            ClientMessage.SelectNumber(created.roomId, 2, "accepted-after-restart")
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameStateUpdated>().single().game
        assertEquals(3, continued.currentTarget)
        assertEquals(listOf(1, 2), continued.selectedNumbers)
    }

    @Test
    fun `profile request returns safe empty statistics without database`() = runTest {
        val engine = GameEngine()
        val guest = engine.connectGuest("Hiền", null)

        val response = engine.handle(guest.playerId, ClientMessage.GetProfile)
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.ProfileData>()
            .single()

        assertEquals("Hiền", response.profile.displayName)
        assertTrue(response.profile.playerCode.isNotBlank())
        assertEquals(0, response.profile.statistics.totalMatches)
        assertTrue(response.profile.recentMatches.isEmpty())
    }

    @Test
    fun `account can load full profile of a friend only`() = runTest {
        val firstId = UUID.randomUUID().toString()
        val friendId = UUID.randomUUID().toString()
        val strangerId = UUID.randomUUID().toString()
        val friendProfile = PlayerProfileSnapshot(
            displayName = "Bạn thân",
            playerCode = "FRIEND01",
            statistics = com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot(
                totalMatches = 12,
                wins = 8,
                eloRating = 1_234
            ),
            achievements = listOf(
                com.hienthai.fastowin.protocol.AchievementSnapshot("FIRST_WIN", "Chiến thắng đầu tiên", "Thắng một trận.", 1L)
            )
        )
        val profileRepository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) =
                if (playerId == friendId) friendProfile else null

            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
        }
        val friendRepository = object : FriendRepository by NoOpFriendRepository {
            override suspend fun areFriends(firstUserId: String, secondUserId: String) =
                firstUserId == firstId && secondUserId == friendId
        }
        val engine = GameEngine(
            playerProfileRepository = profileRepository,
            friendRepository = friendRepository
        )
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(firstId), "Người chơi"))

        val response = engine.handle(firstId, ClientMessage.GetFriendProfile(friendId))
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.FriendProfileData>()
            .single()
        assertEquals(friendId, response.friendUserId)
        assertEquals(friendProfile, response.profile)

        val forbidden = engine.handle(firstId, ClientMessage.GetFriendProfile(strangerId))
        assertEquals(
            "FRIEND_PROFILE_FORBIDDEN",
            assertIs<ServerMessage.Error>(forbidden.single().message).code
        )
    }

    @Test
    fun `profile update persists safe name and avatar`() = runTest {
        var storedProfile = PlayerProfileSnapshot("Tên cũ", "PLAYER123")
        val profileRepository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = storedProfile

            override suspend fun updateProfile(
                playerId: String,
                displayName: String,
                avatarId: String?
            ): Boolean {
                storedProfile = storedProfile.copy(displayName = displayName, avatarId = avatarId)
                return true
            }
        }
        val engine = GameEngine(playerProfileRepository = profileRepository)
        val player = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "Tên cũ"))

        val deliveries = engine.handle(
            player.playerId,
            ClientMessage.UpdateProfile("  Tên mới  ", "rocket")
        )
        val response = deliveries.map(Delivery::message)
            .filterIsInstance<ServerMessage.ProfileData>()
            .single()

        assertEquals("Tên mới", response.profile.displayName)
        assertEquals("rocket", response.profile.avatarId)

        val invalid = engine.handle(
            player.playerId,
            ClientMessage.UpdateProfile("Tên khác", "external-url")
        )
        assertEquals("INVALID_AVATAR", assertIs<ServerMessage.Error>(invalid.single().message).code)
        assertEquals("Tên mới", storedProfile.displayName)

        val guest = engine.connectGuest("Khách", null)
        val guestUpdate = engine.handle(
            guest.playerId,
            ClientMessage.UpdateProfile("Khách mới", "star")
        )
        assertEquals("ACCOUNT_REQUIRED", assertIs<ServerMessage.Error>(guestUpdate.single().message).code)
    }

    @Test
    fun `leaderboard request returns an empty safe response without database`() = runTest {
        val engine = GameEngine()
        val guest = engine.connectGuest("Hiền", null)

        val response = engine.handle(guest.playerId, ClientMessage.GetLeaderboard)
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.LeaderboardData>()
            .single()

        assertTrue(response.leaderboard.topPlayers.isEmpty())
        assertEquals(null, response.leaderboard.currentPlayer)
    }

    @Test
    fun `online friends can invite and join a waiting room without password`() = runTest {
        val hostId = UUID.randomUUID().toString()
        val guestId = UUID.randomUUID().toString()
        var currentTimeMillis = 10_000L
        val repository = object : FriendRepository {
            override suspend fun load(userId: String) = StoredFriends(
                friends = listOf(
                    if (userId == hostId) FriendSnapshot(guestId, "Guest", "GUEST123")
                    else FriendSnapshot(hostId, "Host", "HOST123")
                ),
                recentPlayers = if (userId == hostId) listOf(
                    RecentPlayerSnapshot(guestId, "Guest", "GUEST123", null, 2_000L, 2)
                ) else emptyList()
            )
            override suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long) =
                FriendRequestResult.PlayerNotFound
            override suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long) =
                FriendResponseResult.NotFound
            override suspend fun cancelRequest(userId: String, requestId: String) =
                FriendCancellationResult.NotFound
            override suspend fun removeFriend(userId: String, friendUserId: String) =
                SocialMutationResult.NotFound
            override suspend fun blockPlayer(userId: String, playerUserId: String, nowMillis: Long) =
                SocialMutationResult.NotFound
            override suspend fun unblockPlayer(userId: String, playerUserId: String) =
                SocialMutationResult.NotFound
            override suspend fun areFriends(firstUserId: String, secondUserId: String) =
                setOf(firstUserId, secondUserId) == setOf(hostId, guestId)
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String) = false
        }
        val engine = GameEngine(friendRepository = repository, nowMillis = { currentTimeMillis })
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(hostId), "Host"))
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(guestId), "Guest"))
        val recentPlayer = engine.handle(hostId, ClientMessage.GetFriends)
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.FriendsData>()
            .single()
            .social
            .recentPlayers
            .single()
        assertEquals(guestId, recentPlayer.userId)
        assertEquals(2, recentPlayer.matchesPlayed)
        val room = engine.handle(
            hostId,
            ClientMessage.CreateRoom("Friend room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game

        val invitation = engine.handle(
            hostId,
            ClientMessage.InviteFriend(guestId, room.roomId)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomInvitation>().single()
        val listedInvitation = engine.handle(guestId, ClientMessage.GetRoomInvitations)
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.RoomInvitationsData>()
            .single()
            .invitations
            .single()
        assertEquals(invitation.invitationId, listedInvitation.invitationId)

        currentTimeMillis += 6 * 60_000L
        val expiredInvitations = engine.cleanupExpiredSessions()
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.RoomInvitationsData>()
            .single()
        assertTrue(expiredInvitations.invitations.isEmpty())

        val activeInvitation = engine.handle(
            hostId,
            ClientMessage.InviteFriend(guestId, room.roomId)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomInvitation>().single()

        val acceptResponse = engine.handle(
            guestId,
            ClientMessage.RespondRoomInvitation(activeInvitation.invitationId, accept = true)
        ).map(Delivery::message)
        assertEquals(1, acceptResponse.filterIsInstance<ServerMessage.RoomUpdated>().size)
        engine.handle(hostId, ClientMessage.SetReady(room.roomId, true))
        val started = engine.handle(guestId, ClientMessage.SetReady(room.roomId, true))
            .map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>()

        assertEquals(1, started.size)
        assertEquals(setOf(hostId, guestId), started.first().game.players.map { it.id }.toSet())
        assertEquals(50, started.first().game.numbers.size)
        assertTrue(acceptResponse.filterIsInstance<ServerMessage.RoomInvitationsData>().single().invitations.isEmpty())
    }

    @Test
    fun `room invitation and notification survive engine restart`() = runTest {
        val hostId = UUID.randomUUID().toString()
        val guestId = UUID.randomUUID().toString()
        val friendRepository = friendRepositoryForPair(hostId, guestId)
        val activeRooms = InMemoryActiveRoomRepository()
        val notifications = InMemoryNotificationRepository()
        val firstEngine = GameEngine(
            friendRepository = friendRepository,
            activeRoomRepository = activeRooms,
            notificationRepository = notifications,
            nowMillis = { 10_000L }
        )
        firstEngine.connectAccount(AuthenticatedAccount(UUID.fromString(hostId), "Host"))
        firstEngine.connectAccount(AuthenticatedAccount(UUID.fromString(guestId), "Guest"))
        val room = firstEngine.handle(
            hostId,
            ClientMessage.CreateRoom("Restart invitation", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        val invitation = firstEngine.handle(hostId, ClientMessage.InviteFriend(guestId, room.roomId))
            .map(Delivery::message).filterIsInstance<ServerMessage.RoomInvitation>().single()

        val restartedEngine = GameEngine(
            friendRepository = friendRepository,
            activeRoomRepository = activeRooms,
            notificationRepository = notifications,
            nowMillis = { 20_000L }
        )
        restartedEngine.connectAccount(AuthenticatedAccount(UUID.fromString(hostId), "Host"))
        restartedEngine.connectAccount(AuthenticatedAccount(UUID.fromString(guestId), "Guest"))

        val restoredInvitation = restartedEngine.handle(guestId, ClientMessage.GetRoomInvitations)
            .map(Delivery::message).filterIsInstance<ServerMessage.RoomInvitationsData>()
            .single().invitations.single()
        assertEquals(invitation.invitationId, restoredInvitation.invitationId)
        val restoredNotification = restartedEngine.handle(guestId, ClientMessage.GetNotifications)
            .map(Delivery::message).filterIsInstance<ServerMessage.NotificationsData>()
            .single().notifications.single()
        assertEquals("room:${invitation.invitationId}", restoredNotification.id)
    }

    @Test
    fun `blocked players cannot receive room invitations`() = runTest {
        val hostId = UUID.randomUUID().toString()
        val guestId = UUID.randomUUID().toString()
        val repository = object : FriendRepository {
            override suspend fun load(userId: String) = StoredFriends()
            override suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long) =
                FriendRequestResult.Blocked
            override suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long) =
                FriendResponseResult.NotFound
            override suspend fun cancelRequest(userId: String, requestId: String) =
                FriendCancellationResult.NotFound
            override suspend fun removeFriend(userId: String, friendUserId: String) =
                SocialMutationResult.NotFound
            override suspend fun blockPlayer(userId: String, playerUserId: String, nowMillis: Long) =
                SocialMutationResult.NotFound
            override suspend fun unblockPlayer(userId: String, playerUserId: String) =
                SocialMutationResult.NotFound
            override suspend fun areFriends(firstUserId: String, secondUserId: String) = true
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String) = true
        }
        val engine = GameEngine(friendRepository = repository)
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(hostId), "Host"))
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(guestId), "Guest"))
        val room = engine.handle(
            hostId,
            ClientMessage.CreateRoom("Blocked room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game

        val response = engine.handle(hostId, ClientMessage.InviteFriend(guestId, room.roomId))

        val error = assertIs<ServerMessage.Error>(response.single().message)
        assertEquals("INTERACTION_BLOCKED", error.code)
    }

    @Test
    fun `wrong password cannot join room`() = runTest {
        val fixture = createRoomFixture()
        val deliveries = fixture.engine.handle(
            fixture.guestId,
            ClientMessage.JoinRoom(fixture.roomId, "sai-mat-khau")
        )

        val error = assertIs<ServerMessage.Error>(deliveries.single().message)
        assertEquals("WRONG_PASSWORD", error.code)
    }

    @Test
    fun `invalid room id returns not found without touching persistence`() = runTest {
        val repository = object : ActiveRoomRepository {
            override suspend fun loadAll() = emptyList<StoredActiveRoom>()
            override suspend fun save(room: StoredActiveRoom) = error("Unexpected snapshot save")
            override suspend fun delete(roomId: String) = error("Unexpected snapshot delete")
        }
        val engine = GameEngine(activeRoomRepository = repository)
        val player = engine.connectGuest("Guest", null)

        val response = engine.handle(
            player.playerId,
            ClientMessage.JoinRoom("not-a-uuid", PASSWORD)
        )

        assertEquals("ROOM_NOT_FOUND", assertIs<ServerMessage.Error>(response.single().message).code)
    }

    @Test
    fun `two simultaneous selections advance target only once`() = runTest {
        val fixture = createRoomFixture()
        startRoom(fixture.engine, fixture.hostId, fixture.guestId, fixture.roomId)

        val hostResult = async {
            fixture.engine.handle(
                fixture.hostId,
                ClientMessage.SelectNumber(fixture.roomId, 1, "host-request")
            )
        }
        val guestResult = async {
            fixture.engine.handle(
                fixture.guestId,
                ClientMessage.SelectNumber(fixture.roomId, 1, "guest-request")
            )
        }

        val messages = (hostResult.await() + guestResult.await()).map(Delivery::message)
        val accepted = messages.filterIsInstance<ServerMessage.GameStateUpdated>().single()
        val rejected = messages.filterIsInstance<ServerMessage.Error>().single()

        assertEquals(2, accepted.game.currentTarget)
        assertEquals(10, accepted.game.players.sumOf { it.score })
        assertEquals("WRONG_NUMBER", rejected.code)
        assertNotEquals(accepted.selectedByPlayerId, "")
    }

    @Test
    fun `waiting room is hidden while host disconnects and restored on resume`() = runTest {
        var now = 1_000L
        val engine = GameEngine { now }
        val host = engine.connectGuest("Hiền", null)
        engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng reconnect", PASSWORD, ProtocolGameMode.ORDER)
        )

        assertEquals(1, engine.roomList().rooms.size)
        engine.markDisconnected(host.playerId)
        assertTrue(engine.roomList().rooms.isEmpty())

        now += 10_000L
        val resumed = engine.connectGuest("Hiền", host.resumeToken)
        assertEquals(host.playerId, resumed.playerId)
        assertEquals(1, engine.roomList().rooms.size)
        assertEquals("Phòng reconnect", resumed.currentGame?.roomName)
    }

    @Test
    fun `room closes after disconnected player exceeds reconnect grace period`() = runTest {
        var now = 1_000L
        val activeRoomRepository = InMemoryActiveRoomRepository()
        val engine = GameEngine(
            activeRoomRepository = activeRoomRepository,
            nowMillis = { now }
        )
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val created = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng hết hạn", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single()
        startRoom(engine, host.playerId, guest.playerId, created.game.roomId)

        engine.markDisconnected(host.playerId)
        now += 29_999L
        assertTrue(engine.cleanupExpiredSessions().isEmpty())

        now += 1L
        val cleanupMessages = engine.cleanupExpiredSessions().map(Delivery::message)
        assertEquals(1, cleanupMessages.filterIsInstance<ServerMessage.RoomClosed>().size)

        val result = engine.handle(
            guest.playerId,
            ClientMessage.SelectNumber(created.game.roomId, 1, "after-expiry")
        )
        assertEquals("ROOM_NOT_FOUND", assertIs<ServerMessage.Error>(result.single().message).code)
        assertTrue(activeRoomRepository.loadAll().isEmpty())
    }

    @Test
    fun `disconnected player resumes current state after opponent continues playing`() = runTest {
        var now = 1_000L
        val engine = GameEngine(nowMillis = { now })
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Reconnect during match", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)

        engine.markDisconnected(host.playerId)
        engine.handle(
            guest.playerId,
            ClientMessage.SelectNumber(room.roomId, 1, "guest-while-host-offline")
        )
        now += 29_999L

        val resumed = engine.connectGuest("Host", host.resumeToken)
        val restored = assertNotNull(resumed.currentGame)
        assertEquals(room.roomId, restored.roomId)
        assertEquals(2, restored.currentTarget)
        assertEquals(listOf(1), restored.selectedNumbers)

        val continued = engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 2, "host-after-resume")
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameStateUpdated>().single().game
        assertEquals(3, continued.currentTarget)
        assertEquals(listOf(1, 2), continued.selectedNumbers)
    }

    @Test
    fun `cancelled and disconnected matchmaking entries cannot create ghost matches`() = runTest {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val thirdId = UUID.randomUUID()
        val engine = GameEngine()
        engine.connectAccount(AuthenticatedAccount(firstId, "First"))
        engine.connectAccount(AuthenticatedAccount(secondId, "Second"))
        engine.connectAccount(AuthenticatedAccount(thirdId, "Third"))

        engine.handle(firstId.toString(), ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        engine.handle(firstId.toString(), ClientMessage.CancelMatchmaking)
        val secondWaiting = engine.handle(
            secondId.toString(),
            ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)
        ).map(Delivery::message)
        assertTrue(secondWaiting.filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertTrue(secondWaiting.filterIsInstance<ServerMessage.MatchmakingStatus>().single().isSearching)
        engine.handle(secondId.toString(), ClientMessage.CancelMatchmaking)

        engine.handle(firstId.toString(), ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        engine.markDisconnected(firstId.toString())
        val thirdWaiting = engine.handle(
            thirdId.toString(),
            ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)
        ).map(Delivery::message)
        assertTrue(thirdWaiting.filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertTrue(thirdWaiting.filterIsInstance<ServerMessage.MatchmakingStatus>().single().isSearching)

        engine.connectAccount(AuthenticatedAccount(firstId, "First"))
        val matched = engine.handle(
            firstId.toString(),
            ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single().game
        assertEquals(setOf(firstId.toString(), thirdId.toString()), matched.players.map { it.id }.toSet())
    }

    @Test
    fun `leaving an active room closes it and deletes the persisted snapshot`() = runTest {
        val repository = InMemoryActiveRoomRepository()
        val engine = GameEngine(activeRoomRepository = repository)
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Leave active room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)
        assertEquals(room.roomId, repository.loadAll().single().roomId)

        val closed = engine.handle(host.playerId, ClientMessage.LeaveRoom(room.roomId))
            .map(Delivery::message).filterIsInstance<ServerMessage.RoomClosed>().single()
        assertEquals(room.roomId, closed.roomId)
        assertTrue(repository.loadAll().isEmpty())

        val rejected = engine.handle(
            guest.playerId,
            ClientMessage.SelectNumber(room.roomId, 1, "after-opponent-left")
        ).map(Delivery::message).filterIsInstance<ServerMessage.Error>().single()
        assertEquals("ROOM_NOT_FOUND", rejected.code)
    }

    @Test
    fun `completed match is persisted once with winner and statistics outcome`() = runTest {
        val savedMatches = mutableListOf<CompletedMatch>()
        val repository = MatchResultRepository { match -> savedMatches += match }
        val engine = GameEngine(matchResultRepository = repository)
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng lịch sử", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)

        val firstWrong = engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 50, "same-wrong-request")
        )
        val duplicateWrong = engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 50, "same-wrong-request")
        )
        assertEquals(firstWrong.map(Delivery::message), duplicateWrong.map(Delivery::message))

        repeat(50) { index ->
            engine.handle(
                host.playerId,
                ClientMessage.SelectNumber(room.roomId, index + 1, "finish-$index")
            )
        }
        engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 50, "finish-49")
        )

        val saved = assertNotNull(savedMatches.singleOrNull())
        assertEquals(room.roomId, saved.matchId)
        assertEquals(host.playerId, saved.winnerPlayerId)
        assertEquals(MatchOutcome.WIN, saved.players.single { it.playerId == host.playerId }.outcome)
        assertEquals(MatchOutcome.LOSS, saved.players.single { it.playerId == guest.playerId }.outcome)
        assertEquals(500, saved.players.single { it.playerId == host.playerId }.score)
        assertEquals(51, saved.events.size)
        assertEquals(50, saved.events.count { it.result == SelectionResult.ACCEPTED })
        assertEquals(1, saved.events.count { it.result == SelectionResult.REJECTED })
        assertEquals(1, saved.events.single { it.result == SelectionResult.REJECTED }.expectedNumber)
    }

    @Test
    fun `rematch starts only after both players agree and uses a new match id`() = runTest {
        val savedMatches = mutableListOf<CompletedMatch>()
        val engine = GameEngine(matchResultRepository = MatchResultRepository { savedMatches += it })
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Rematch room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)
        repeat(50) { index ->
            engine.handle(host.playerId, ClientMessage.SelectNumber(room.roomId, index + 1, "round-one-$index"))
        }

        val waiting = engine.handle(host.playerId, ClientMessage.RequestRematch(room.roomId))
            .map(Delivery::message).filterIsInstance<ServerMessage.RematchStatus>().single().game
        assertEquals(listOf(host.playerId), waiting.rematchRequestedPlayerIds)
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, waiting.phase)

        val restarted = engine.handle(guest.playerId, ClientMessage.RequestRematch(room.roomId))
            .map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single().game
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.PLAYING, restarted.phase)
        assertNotEquals(room.matchId, restarted.matchId)
        assertEquals(1, restarted.currentTarget)
        assertTrue(restarted.rematchRequestedPlayerIds.isEmpty())
        assertTrue(restarted.players.all { it.score == 0 })
        assertEquals(1, savedMatches.size)
    }

    @Test
    fun `player can cancel and opponent can decline a pending rematch`() = runTest {
        val engine = GameEngine()
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Rematch response", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)
        repeat(50) { index ->
            engine.handle(host.playerId, ClientMessage.SelectNumber(room.roomId, index + 1, "finish-$index"))
        }

        engine.handle(host.playerId, ClientMessage.RespondRematch(room.roomId, accept = true))
        val cancelled = engine.handle(host.playerId, ClientMessage.RespondRematch(room.roomId, accept = false))
            .map(Delivery::message).filterIsInstance<ServerMessage.RematchStatus>().single()
        assertEquals(RematchEvent.CANCELLED, cancelled.event)
        assertEquals(host.playerId, cancelled.actorPlayerId)
        assertTrue(cancelled.game.rematchRequestedPlayerIds.isEmpty())
        assertEquals(null, cancelled.game.rematchExpiresAtEpochMillis)

        engine.handle(host.playerId, ClientMessage.RespondRematch(room.roomId, accept = true))
        val declined = engine.handle(guest.playerId, ClientMessage.RespondRematch(room.roomId, accept = false))
            .map(Delivery::message).filterIsInstance<ServerMessage.RematchStatus>().single()
        assertEquals(RematchEvent.DECLINED, declined.event)
        assertEquals(guest.playerId, declined.actorPlayerId)
        assertTrue(declined.game.rematchRequestedPlayerIds.isEmpty())
    }

    @Test
    fun `pending rematch expires on the server`() = runTest {
        var now = 10_000L
        val engine = GameEngine(rematchTimeoutMillis = 1_000L, nowMillis = { now })
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Expiring rematch", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)
        repeat(50) { index ->
            engine.handle(host.playerId, ClientMessage.SelectNumber(room.roomId, index + 1, "finish-$index"))
        }

        val requested = engine.handle(host.playerId, ClientMessage.RespondRematch(room.roomId, accept = true))
            .map(Delivery::message).filterIsInstance<ServerMessage.RematchStatus>().single()
        assertEquals(11_000L, requested.game.rematchExpiresAtEpochMillis)

        now = 11_001L
        val expired = engine.advanceTimedGames()
            .map(Delivery::message).filterIsInstance<ServerMessage.RematchStatus>().single()
        assertEquals(RematchEvent.EXPIRED, expired.event)
        assertTrue(expired.game.rematchRequestedPlayerIds.isEmpty())
        assertEquals(null, expired.game.rematchExpiresAtEpochMillis)
    }

    @Test
    fun `game snapshot reports per-player accuracy reaction and duration`() = runTest {
        var now = 1_000L
        val engine = GameEngine(nowMillis = { now })
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Performance room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)

        now = 1_100L
        engine.handle(host.playerId, ClientMessage.SelectNumber(room.roomId, 2, "wrong"))
        repeat(50) { index ->
            now += 100L
            engine.handle(host.playerId, ClientMessage.SelectNumber(room.roomId, index + 1, "correct-$index"))
        }
        val finished = assertNotNull(engine.connectGuest("Host", host.resumeToken).currentGame)
        val hostResult = finished.players.single { it.id == host.playerId }
        assertEquals(50, hostResult.correctSelections)
        assertEquals(1, hostResult.wrongSelections)
        assertEquals(102L, hostResult.averageReactionMillis)
        assertEquals(5_100L, assertNotNull(finished.finishedAtEpochMillis) - assertNotNull(finished.startedAtEpochMillis))
    }

    @Test
    fun `rematch vote survives engine restart`() = runTest {
        val identityRepository = InMemoryGuestIdentityRepository()
        val activeRoomRepository = InMemoryActiveRoomRepository()
        val firstEngine = GameEngine(
            identityRepository = identityRepository,
            activeRoomRepository = activeRoomRepository
        )
        val host = firstEngine.connectGuest("Host", null)
        val guest = firstEngine.connectGuest("Guest", null)
        val room = firstEngine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Persistent rematch", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(firstEngine, host.playerId, guest.playerId, room.roomId)
        repeat(50) { index ->
            firstEngine.handle(
                host.playerId,
                ClientMessage.SelectNumber(room.roomId, index + 1, "finish-before-restart-$index")
            )
        }
        firstEngine.handle(host.playerId, ClientMessage.RequestRematch(room.roomId))

        val restartedEngine = GameEngine(
            identityRepository = identityRepository,
            activeRoomRepository = activeRoomRepository
        )
        val resumedHost = restartedEngine.connectGuest("Host", host.resumeToken)
        restartedEngine.connectGuest("Guest", guest.resumeToken)
        assertEquals(
            listOf(host.playerId),
            assertNotNull(resumedHost.currentGame).rematchRequestedPlayerIds
        )

        val restarted = restartedEngine.handle(guest.playerId, ClientMessage.RequestRematch(room.roomId))
            .map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single().game
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.PLAYING, restarted.phase)
        assertNotEquals(room.matchId, restarted.matchId)
        assertTrue(restarted.rematchRequestedPlayerIds.isEmpty())
    }

    @Test
    fun `server finishes time attack and persists a draw exactly once`() = runTest {
        var now = 10_000L
        val savedMatches = mutableListOf<CompletedMatch>()
        val engine = GameEngine(
            matchResultRepository = MatchResultRepository { savedMatches += it },
            nowMillis = { now }
        )
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng 60 giây", PASSWORD, ProtocolGameMode.TIME_ATTACK)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)

        now += 59_999L
        assertTrue(engine.advanceTimedGames().isEmpty())
        now += 1L
        val finished = engine.advanceTimedGames().map(Delivery::message)
            .filterIsInstance<ServerMessage.GameFinished>()
            .single()

        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, finished.game.phase)
        assertEquals(2, savedMatches.single().players.size)
        assertTrue(savedMatches.single().players.all { it.outcome == MatchOutcome.DRAW })
        assertTrue(engine.advanceTimedGames().isEmpty())
        assertEquals(1, savedMatches.size)
    }

    @Test
    fun `selection at time limit broadcasts finish and persists result`() = runTest {
        var now = 10_000L
        val savedMatches = mutableListOf<CompletedMatch>()
        val engine = GameEngine(
            matchResultRepository = MatchResultRepository { savedMatches += it },
            nowMillis = { now }
        )
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Timed race", PASSWORD, ProtocolGameMode.TIME_ATTACK)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)

        now += 60_000L
        val finished = engine.handle(
            host.playerId,
            ClientMessage.SelectNumber(room.roomId, 1, "at-time-limit")
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameFinished>().single()

        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, finished.game.phase)
        assertEquals(setOf(host.playerId, guest.playerId), savedMatches.single().players.map { it.playerId }.toSet())
        assertTrue(engine.advanceTimedGames().isEmpty())
        assertEquals(1, savedMatches.size)
    }

    private suspend fun createRoomFixture(): Fixture {
        val engine = GameEngine()
        val host = engine.connectGuest("Hiền", null)
        val guest = engine.connectGuest("Hiếu", null)
        val deliveries = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Phòng test", PASSWORD, ProtocolGameMode.ORDER)
        )
        val created = deliveries.map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single()
        assertTrue(created.game.numbers.isEmpty())
        return Fixture(engine, host.playerId, guest.playerId, created.game.roomId)
    }

    private suspend fun startRoom(
        engine: GameEngine,
        hostId: String,
        guestId: String,
        roomId: String,
        password: String = PASSWORD
    ) {
        val joined = engine.handle(guestId, ClientMessage.JoinRoom(roomId, password))
            .map(Delivery::message).filterIsInstance<ServerMessage.RoomUpdated>().single().game
        assertEquals(setOf(hostId, guestId), joined.players.map { it.id }.toSet())
        engine.handle(hostId, ClientMessage.SetReady(roomId, true))
        val started = engine.handle(guestId, ClientMessage.SetReady(roomId, true))
            .map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single().game
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.PLAYING, started.phase)
    }

    private fun friendRepositoryForPair(firstId: String, secondId: String) = object : FriendRepository {
        override suspend fun load(userId: String) = StoredFriends(
            friends = listOf(
                if (userId == firstId) FriendSnapshot(secondId, "Guest", "GUEST123")
                else FriendSnapshot(firstId, "Host", "HOST123")
            )
        )
        override suspend fun sendRequest(userId: String, playerCode: String, nowMillis: Long) =
            FriendRequestResult.PlayerNotFound
        override suspend fun respond(userId: String, requestId: String, accept: Boolean, nowMillis: Long) =
            FriendResponseResult.NotFound
        override suspend fun cancelRequest(userId: String, requestId: String) = FriendCancellationResult.NotFound
        override suspend fun removeFriend(userId: String, friendUserId: String) = SocialMutationResult.NotFound
        override suspend fun blockPlayer(userId: String, playerUserId: String, nowMillis: Long) =
            SocialMutationResult.NotFound
        override suspend fun unblockPlayer(userId: String, playerUserId: String) = SocialMutationResult.NotFound
        override suspend fun areFriends(firstUserId: String, secondUserId: String) =
            setOf(firstUserId, secondUserId) == setOf(firstId, secondId)
        override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String) = false
    }

    private data class Fixture(
        val engine: GameEngine,
        val hostId: String,
        val guestId: String,
        val roomId: String
    )

    private companion object {
        const val PASSWORD = "123456"
    }
}
