package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ClanJoinRequestSnapshot
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import com.hienthai.fastowin.protocol.RematchEvent
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.protocol.StorePurchaseStatus
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
    fun `account appearance is included in room snapshots and persisted`() = runTest {
        val playerId = UUID.randomUUID().toString()
        val profile = PlayerProfileSnapshot(
            userId = playerId,
            displayName = "Hiền",
            playerCode = "HIEN001",
            avatarId = "crown",
            progression = PlayerProgressionSnapshot(
                cosmetics = listOf(
                    CosmeticSnapshot("frame_gold", "Khung Vàng", CosmeticType.FRAME, unlocked = true, equipped = true)
                )
            )
        )
        val profileRepository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = profile
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
        }
        val activeRoomRepository = InMemoryActiveRoomRepository()
        val engine = GameEngine(
            playerProfileRepository = profileRepository,
            activeRoomRepository = activeRoomRepository
        )
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(playerId), profile.displayName))

        val room = engine.handle(
            playerId,
            ClientMessage.CreateRoom("Phòng có khung", "", ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game

        val player = room.players.single()
        assertEquals("crown", player.avatarId)
        assertEquals("frame_gold", player.frameId)
        val storedHost = activeRoomRepository.loadAll().single().host
        assertEquals("crown", storedHost.avatarId)
        assertEquals("frame_gold", storedHost.frameId)
    }

    @Test
    fun `daily check in refreshes profile and duplicate request gives no xp`() = runTest {
        val playerId = UUID.randomUUID().toString()
        var profile = PlayerProfileSnapshot(userId = "user1",
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
    fun `mission reward refreshes profile and rejects duplicate claim`() = runTest {
        val playerId = UUID.randomUUID().toString()
        var claimed = false
        var profile = PlayerProfileSnapshot(userId = "user1",
            displayName = "Hiền",
            playerCode = "HIEN001",
            progression = PlayerProgressionSnapshot(
                dailyMissions = listOf(
                    MissionSnapshot("DAILY_WIN_1", "Thắng 1 trận hôm nay", 1, 1, true, 15)
                )
            )
        )
        val repository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = profile
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
            override suspend fun claimMissionReward(playerId: String, missionCode: String): MissionRewardClaimResult {
                if (claimed) return MissionRewardClaimResult(MissionRewardClaimStatus.ALREADY_CLAIMED)
                claimed = true
                profile = profile.copy(
                    progression = profile.progression.copy(
                        experiencePoints = 15,
                        dailyMissions = profile.progression.dailyMissions.map { it.copy(rewardClaimed = true) }
                    )
                )
                return MissionRewardClaimResult(MissionRewardClaimStatus.CLAIMED, 15)
            }
        }
        val engine = GameEngine(playerProfileRepository = repository)
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(playerId), "Hiền"))

        val first = engine.handle(playerId, ClientMessage.ClaimMissionReward("DAILY_WIN_1"))
            .map(Delivery::message)
        assertEquals(15, first.filterIsInstance<ServerMessage.MissionRewardResult>().single().rewardXp)
        assertTrue(first.filterIsInstance<ServerMessage.ProfileData>().single()
            .profile.progression.dailyMissions.single().rewardClaimed)

        val duplicate = engine.handle(playerId, ClientMessage.ClaimMissionReward("DAILY_WIN_1"))
        assertEquals(
            "MISSION_ALREADY_CLAIMED",
            assertIs<ServerMessage.Error>(duplicate.single().message).code
        )
    }

    @Test
    fun `account can load its wallet history while guest is rejected`() = runTest {
        val playerId = UUID.randomUUID().toString()
        val transaction = WalletTransactionSnapshot(
            id = UUID.randomUUID().toString(),
            sourceType = "MATCH",
            sourceId = "match-1",
            goldDelta = 100,
            xpDelta = 30,
            createdAtEpochMillis = 1_000L
        )
        val repository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = null
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
            override suspend fun loadWalletHistory(playerId: String, limit: Int) = listOf(transaction)
        }
        val engine = GameEngine(playerProfileRepository = repository)
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(playerId), "Hiền"))

        val history = engine.handle(playerId, ClientMessage.GetWalletHistory)
            .map(Delivery::message)
            .filterIsInstance<ServerMessage.WalletHistory>()
            .single()
        assertEquals(listOf(transaction), history.transactions)

        val guest = engine.connectGuest("Khách", null)
        val denied = engine.handle(guest.playerId, ClientMessage.GetWalletHistory)
        assertEquals("ACCOUNT_REQUIRED", assertIs<ServerMessage.Error>(denied.single().message).code)
    }

    @Test
    fun `verified store purchase grants gems once and catalog enables sandbox`() = runTest {
        val playerId = UUID.randomUUID().toString()
        var profile = PlayerProfileSnapshot(
            userId = playerId,
            displayName = "Hiền",
            playerCode = "HIEN123",
            progression = PlayerProgressionSnapshot(gems = 5)
        )
        val grantedTransactions = mutableSetOf<String>()
        val repository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = profile
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
            override suspend fun grantStorePurchase(
                playerId: String,
                store: String,
                productId: String,
                transactionId: String,
                gems: Int
            ): StorePurchaseGrantStatus {
                if (!grantedTransactions.add(transactionId)) return StorePurchaseGrantStatus.ALREADY_GRANTED
                profile = profile.copy(progression = profile.progression.copy(gems = profile.progression.gems + gems))
                return StorePurchaseGrantStatus.GRANTED
            }
        }
        val verifier = StorePurchaseVerifier {
            StoreVerificationResult(StoreVerificationStatus.PURCHASED, "OK")
        }
        val engine = GameEngine(
            playerProfileRepository = repository,
            storePurchaseVerifier = verifier,
            storeSandboxEnabled = true
        )
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(playerId), "Hiền"))

        val catalog = engine.handle(playerId, ClientMessage.GetGemStoreCatalog)
            .map(Delivery::message).filterIsInstance<ServerMessage.GemStoreCatalog>().single()
        assertTrue(catalog.sandboxEnabled)
        assertEquals(listOf(80, 250, 650), catalog.packages.map { it.gems })

        val request = ClientMessage.VerifyStorePurchase(
            requestId = "purchase-1",
            store = StorePlatform.GOOGLE_PLAY,
            productId = "fasttowin_gems_80",
            purchaseToken = "dev:GOOGLE_PLAY:fasttowin_gems_80:test"
        )
        val first = engine.handle(playerId, request).map(Delivery::message)
        assertEquals(
            StorePurchaseStatus.GRANTED,
            first.filterIsInstance<ServerMessage.StorePurchaseResult>().single().status
        )
        assertEquals(85, first.filterIsInstance<ServerMessage.ProfileData>().single().profile.progression.gems)

        val duplicate = engine.handle(playerId, request).map(Delivery::message)
        assertEquals(
            StorePurchaseStatus.ALREADY_GRANTED,
            duplicate.filterIsInstance<ServerMessage.StorePurchaseResult>().single().status
        )
        assertEquals(1, grantedTransactions.size)
    }

    @Test
    fun `matchmaking pairs connected accounts with nearby elo`() = runTest {
        val firstId = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val profiles = mapOf(
            firstId to PlayerProfileSnapshot(userId = "user1",
                "First", "FIRST001",
                statistics = com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot(eloRating = 1_020)
            ),
            secondId to PlayerProfileSnapshot(userId = "user1",
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
        assertEquals(MatchType.RANKED, matched.game.matchType)
        assertEquals(50, matched.game.numbers.size)
    }

    @Test
    fun `public room can be created and joined without password`() = runTest {
        val engine = GameEngine()
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)

        val created = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Public room", "", ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        val summary = engine.handle(guest.playerId, ClientMessage.ListRooms)
            .map(Delivery::message).filterIsInstance<ServerMessage.RoomList>().single()
            .rooms.single { it.id == created.roomId }
        assertFalse(summary.requiresPassword)

        val joined = engine.handle(guest.playerId, ClientMessage.JoinRoom(created.roomId, ""))
            .map(Delivery::message).filterIsInstance<ServerMessage.RoomUpdated>().single().game
        assertEquals(setOf(host.playerId, guest.playerId), joined.players.map { it.id }.toSet())
    }

    @Test
    fun `casual and ranked matchmaking queues never cross match`() = runTest {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val thirdId = UUID.randomUUID()
        val engine = GameEngine()
        engine.connectAccount(AuthenticatedAccount(firstId, "Ranked"))
        engine.connectAccount(AuthenticatedAccount(secondId, "Casual one"))
        engine.connectAccount(AuthenticatedAccount(thirdId, "Casual two"))

        engine.handle(
            firstId.toString(),
            ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER, MatchType.RANKED)
        )
        val casualWaiting = engine.handle(
            secondId.toString(),
            ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER, MatchType.CASUAL)
        ).map(Delivery::message)
        assertTrue(casualWaiting.filterIsInstance<ServerMessage.GameStarted>().isEmpty())

        val matched = engine.handle(
            thirdId.toString(),
            ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER, MatchType.CASUAL)
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single().game
        assertEquals(MatchType.CASUAL, matched.matchType)
        assertEquals(setOf(secondId.toString(), thirdId.toString()), matched.players.map { it.id }.toSet())
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
        val friendProfile = PlayerProfileSnapshot(userId = "user1",
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
        var storedProfile = PlayerProfileSnapshot(userId = "user1","Tên cũ", "PLAYER123")
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

        val lockedAvatar = engine.handle(
            player.playerId,
            ClientMessage.UpdateProfile("Tên mới", DAILY_CHECK_IN_AVATAR_ID)
        )
        assertEquals("AVATAR_LOCKED", assertIs<ServerMessage.Error>(lockedAvatar.single().message).code)

        storedProfile = storedProfile.copy(
            progression = PlayerProgressionSnapshot(
                cosmetics = listOf(
                    CosmeticSnapshot(
                        DAILY_CHECK_IN_AVATAR_ID,
                        "Ảnh đại diện Điểm danh",
                        CosmeticType.AVATAR,
                        unlocked = true,
                        equipped = false
                    )
                )
            )
        )
        val unlockedAvatar = engine.handle(
            player.playerId,
            ClientMessage.UpdateProfile("Tên mới", DAILY_CHECK_IN_AVATAR_ID)
        )
        assertEquals(
            DAILY_CHECK_IN_AVATAR_ID,
            unlockedAvatar.map(Delivery::message)
                .filterIsInstance<ServerMessage.ProfileData>()
                .single().profile.avatarId
        )

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

        val invitationResponse = engine.handle(
            hostId,
            ClientMessage.InviteFriend(guestId, room.roomId)
        ).map(Delivery::message)
        val invitation = invitationResponse.filterIsInstance<ServerMessage.RoomInvitation>().single()
        assertEquals(
            listOf(guestId),
            invitationResponse.filterIsInstance<ServerMessage.RoomInvitationsData>()
                .single().outgoingFriendUserIds
        )
        assertTrue(invitationResponse.filterIsInstance<ServerMessage.SocialNotice>().isEmpty())
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
        assertTrue(expiredInvitations.all { it.invitations.isEmpty() })
        assertTrue(expiredInvitations.all { it.outgoingFriendUserIds.isEmpty() })

        val declinedInvitation = engine.handle(
            hostId,
            ClientMessage.InviteFriend(guestId, room.roomId)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomInvitation>().single()
        val declineResponse = engine.handle(
            guestId,
            ClientMessage.RespondRoomInvitation(declinedInvitation.invitationId, accept = false)
        ).map(Delivery::message)
        val invitationStatesAfterDecline =
            declineResponse.filterIsInstance<ServerMessage.RoomInvitationsData>()
        assertTrue(invitationStatesAfterDecline.size >= 2)
        assertTrue(invitationStatesAfterDecline.all { it.invitations.isEmpty() })
        assertTrue(invitationStatesAfterDecline.all { it.outgoingFriendUserIds.isEmpty() })

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
        assertTrue(
            acceptResponse.filterIsInstance<ServerMessage.RoomInvitationsData>()
                .all { it.invitations.isEmpty() }
        )
        assertTrue(
            acceptResponse.filterIsInstance<ServerMessage.RoomInvitationsData>()
                .all { it.outgoingFriendUserIds.isEmpty() }
        )
    }

    @Test
    fun `guest leaving a waiting room closes it for the host`() = runTest {
        val repository = InMemoryActiveRoomRepository()
        val engine = GameEngine(activeRoomRepository = repository)
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Waiting room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        engine.handle(guest.playerId, ClientMessage.JoinRoom(room.roomId, PASSWORD))

        val leaving = engine.handle(guest.playerId, ClientMessage.LeaveRoom(room.roomId))
        val closedForHost = leaving.single {
            it.recipients?.contains(host.playerId) == true && it.message is ServerMessage.RoomClosed
        }.message as ServerMessage.RoomClosed

        assertEquals(room.roomId, closedForHost.roomId)
        assertTrue(repository.loadAll().isEmpty())
        assertTrue(
            engine.handle(host.playerId, ClientMessage.SetReady(room.roomId, true))
                .map(Delivery::message)
                .filterIsInstance<ServerMessage.Error>()
                .any { it.code == "ROOM_NOT_FOUND" }
        )
    }

    @Test
    fun `clan applicant only becomes a member after owner approval`() = runTest {
        val ownerId = UUID.randomUUID().toString()
        val applicantId = UUID.randomUUID().toString()
        val clanId = UUID.randomUUID().toString()
        val members = linkedSetOf(ownerId)
        val pending = linkedSetOf<String>()
        val profiles = mapOf(
            ownerId to PlayerProfileSnapshot(ownerId, "Bang chủ", "OWNER01", clanId = clanId, clanName = "Speed"),
            applicantId to PlayerProfileSnapshot(applicantId, "Tân binh", "NEWBIE01")
        )
        val profileRepository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? =
                profiles[playerId]?.copy(
                    clanId = clanId.takeIf { playerId in members },
                    clanName = "Speed".takeIf { playerId in members }
                )
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
        }
        val clanRepository = object : ClanRepository {
            private fun snapshot() = ClanSnapshot(
                id = clanId,
                name = "Speed",
                description = "Nhanh là thắng",
                ownerId = ownerId,
                members = members.map { memberId ->
                    ClanMemberSnapshot(
                        userId = memberId,
                        displayName = profiles.getValue(memberId).displayName,
                        role = if (memberId == ownerId) ClanRole.LEADER else ClanRole.MEMBER,
                        trophies = 1_000
                    )
                },
                trophies = 1_000,
                joinRequests = pending.map { userId ->
                    val profile = profiles.getValue(userId)
                    ClanJoinRequestSnapshot(userId, profile.displayName, profile.playerCode, 1_000L)
                }
            )

            override suspend fun createClan(ownerId: String, name: String, description: String) = null
            override suspend fun requestJoinClan(userId: String, clanId: String): ClanJoinRequestResult {
                if (userId in members) return ClanJoinRequestResult.ALREADY_MEMBER
                pending += userId
                return ClanJoinRequestResult.REQUESTED
            }
            override suspend fun respondJoinRequest(
                clanId: String,
                ownerId: String,
                userId: String,
                accept: Boolean
            ): ClanJoinResponseResult {
                if (!pending.remove(userId)) return ClanJoinResponseResult.REQUEST_NOT_FOUND
                if (accept) members += userId
                return if (accept) ClanJoinResponseResult.APPROVED else ClanJoinResponseResult.REJECTED
            }
            override suspend fun getPendingJoinClanIds(userId: String) =
                if (userId in pending) listOf(clanId) else emptyList()
            override suspend fun leaveClan(userId: String) = members.remove(userId)
            override suspend fun getClanByUserId(userId: String) = snapshot().takeIf { userId in members }
            override suspend fun getClanById(clanId: String) = snapshot().takeIf { it.id == clanId }
            override suspend fun getClanList(limit: Int, offset: Int, query: String?) = listOf(
                ClanSummarySnapshot(clanId, "Speed", members.size, 50, 1_000)
            )
            override suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String) = false
            override suspend fun updateLogoId(clanId: String, logoId: String) = false
            override suspend fun addClanTrophies(clanId: String, amount: Int) = false
            override suspend fun addQuestProgress(clanId: String, userId: String, amount: Int) = false
            override suspend fun claimQuestReward(clanId: String, userId: String) = false
        }
        val engine = GameEngine(
            playerProfileRepository = profileRepository,
            clanRepository = clanRepository
        )
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(ownerId), "Bang chủ"))
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(applicantId), "Tân binh"))

        val request = engine.handle(applicantId, ClientMessage.JoinClan(clanId))
        assertTrue(applicantId in pending)
        assertFalse(applicantId in members)
        assertEquals(
            "request_join_clan",
            request.map(Delivery::message).filterIsInstance<ServerMessage.ClanActionResult>().single().action
        )

        val approval = engine.handle(
            ownerId,
            ClientMessage.RespondClanJoinRequest(clanId, applicantId, accept = true)
        )
        assertFalse(applicantId in pending)
        assertTrue(applicantId in members)
        assertTrue(
            approval.any {
                it.recipients == setOf(applicantId) &&
                    (it.message as? ServerMessage.ClanActionResult)?.action == "join_clan_approved"
            }
        )
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
    fun `leaving an active room counts as a loss and persists the result`() = runTest {
        val repository = InMemoryActiveRoomRepository()
        val savedMatches = mutableListOf<CompletedMatch>()
        val engine = GameEngine(
            activeRoomRepository = repository,
            matchResultRepository = MatchResultRepository { savedMatches += it }
        )
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Leave active room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)
        assertEquals(room.roomId, repository.loadAll().single().roomId)

        val leaveDeliveries = engine.handle(host.playerId, ClientMessage.LeaveRoom(room.roomId))
        val finishedDelivery = leaveDeliveries.single { it.message is ServerMessage.GameFinished }
        val finished = (finishedDelivery.message as ServerMessage.GameFinished).game
        assertEquals(guest.playerId, finished.winnerPlayerId)
        assertEquals(setOf(guest.playerId), finishedDelivery.recipients)
        assertEquals(
            setOf(host.playerId),
            leaveDeliveries.single { it.message is ServerMessage.RoomClosed }.recipients
        )
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, repository.loadAll().single().phase)
        assertEquals(setOf(host.playerId), repository.loadAll().single().departedPlayerIds)
        val saved = savedMatches.single()
        assertEquals(guest.playerId, saved.winnerPlayerId)
        assertEquals(MatchOutcome.LOSS, saved.players.single { it.playerId == host.playerId }.outcome)
        assertEquals(MatchOutcome.WIN, saved.players.single { it.playerId == guest.playerId }.outcome)

        val rejected = engine.handle(
            guest.playerId,
            ClientMessage.SelectNumber(room.roomId, 1, "after-opponent-left")
        ).map(Delivery::message).filterIsInstance<ServerMessage.Error>().single()
        assertEquals("GAME_NOT_PLAYING", rejected.code)

        val hostReconnect = engine.connectGuest("Host", host.resumeToken)
        val guestReconnect = engine.connectGuest("Guest", guest.resumeToken)
        assertEquals(null, hostReconnect.currentGame)
        assertEquals(room.roomId, guestReconnect.currentGame?.roomId)

        val rematchRejected = engine.handle(
            guest.playerId,
            ClientMessage.RespondRematch(room.roomId, accept = true)
        ).map(Delivery::message).filterIsInstance<ServerMessage.Error>().single()
        assertEquals("OPPONENT_LEFT", rematchRejected.code)

        engine.handle(guest.playerId, ClientMessage.LeaveRoom(room.roomId))
        assertTrue(repository.loadAll().isEmpty())
    }

    @Test
    fun `leaving a completed result only returns that player to lobby`() = runTest {
        val repository = InMemoryActiveRoomRepository()
        val engine = GameEngine(activeRoomRepository = repository)
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Independent result exit", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)
        repeat(50) { index ->
            engine.handle(
                host.playerId,
                ClientMessage.SelectNumber(room.roomId, index + 1, "complete-result-$index")
            )
        }

        val hostExit = engine.handle(host.playerId, ClientMessage.LeaveRoom(room.roomId))
        val closed = hostExit.single { it.message is ServerMessage.RoomClosed }
        assertEquals(setOf(host.playerId), closed.recipients)
        assertTrue(hostExit.none { it.recipients?.contains(guest.playerId) == true })
        assertEquals(setOf(host.playerId), repository.loadAll().single().departedPlayerIds)
        assertEquals(room.roomId, engine.connectGuest("Guest", guest.resumeToken).currentGame?.roomId)
        assertEquals(null, engine.connectGuest("Host", host.resumeToken).currentGame)

        engine.handle(guest.playerId, ClientMessage.LeaveRoom(room.roomId))
        assertTrue(repository.loadAll().isEmpty())
    }

    @Test
    fun `emoji is broadcast to both players during a match`() = runTest {
        val engine = GameEngine()
        val host = engine.connectGuest("Host", null)
        val guest = engine.connectGuest("Guest", null)
        val room = engine.handle(
            host.playerId,
            ClientMessage.CreateRoom("Emoji room", PASSWORD, ProtocolGameMode.ORDER)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        startRoom(engine, host.playerId, guest.playerId, room.roomId)

        val delivery = engine.handle(host.playerId, ClientMessage.SendEmoji(room.roomId, "😂")).single()
        val broadcast = assertIs<ServerMessage.EmojiBroadcast>(delivery.message)
        assertEquals(host.playerId, broadcast.senderPlayerId)
        assertEquals("😂", broadcast.emojiId)
        assertEquals(setOf(host.playerId, guest.playerId), delivery.recipients)
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
        assertEquals(11, hostResult.fastestSegmentStart)
        assertEquals(20, hostResult.fastestSegmentEnd)
        assertEquals(100L, hostResult.fastestSegmentAverageMillis)
        assertEquals(1, hostResult.slowestSegmentStart)
        assertEquals(10, hostResult.slowestSegmentEnd)
        assertEquals(110L, hostResult.slowestSegmentAverageMillis)
        assertEquals(host.playerId, finished.winnerPlayerId)
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

    @Test
    fun `locked modes require the configured player level`() = runTest {
        val engine = GameEngine()
        val guest = engine.connectGuest("Người mới", null)

        val result = engine.handle(
            guest.playerId,
            ClientMessage.CreateRoom("Phòng ngẫu nhiên", PASSWORD, ProtocolGameMode.RANDOM_TARGET)
        )

        val error = assertIs<ServerMessage.Error>(result.single().message)
        assertEquals("MODE_LOCKED", error.code)
        assertTrue(error.message.contains("cấp 3"))
    }

    @Test
    fun `time bonus gives each player an independent board and adjusts time`() = runTest {
        val fixture = createUnlockedRoomFixture(ProtocolGameMode.TIME_BONUS)
        val started = startRoom(fixture.engine, fixture.hostId, fixture.guestId, fixture.roomId)
        val hostBefore = started.players.single { it.id == fixture.hostId }
        val guestBefore = started.players.single { it.id == fixture.guestId }

        val updated = fixture.engine.handle(
            fixture.hostId,
            ClientMessage.SelectNumber(fixture.roomId, hostBefore.currentTarget, "bonus-correct")
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameStateUpdated>().single().game
        val hostAfter = updated.players.single { it.id == fixture.hostId }
        val guestAfter = updated.players.single { it.id == fixture.guestId }

        assertEquals(2, hostAfter.currentTarget)
        assertEquals(guestBefore.currentTarget, guestAfter.currentTarget)
        assertEquals(listOf(1), hostAfter.selectedNumbers)
        assertTrue(hostAfter.timeLeftMillis > hostBefore.timeLeftMillis)
    }

    @Test
    fun `combo increases multiplier and wrong selection resets it`() = runTest {
        val fixture = createUnlockedRoomFixture(ProtocolGameMode.COMBO)
        var snapshot = startRoom(fixture.engine, fixture.hostId, fixture.guestId, fixture.roomId)

        repeat(5) { index ->
            val target = snapshot.players.single { it.id == fixture.hostId }.currentTarget
            snapshot = fixture.engine.handle(
                fixture.hostId,
                ClientMessage.SelectNumber(fixture.roomId, target, "combo-$index")
            ).map(Delivery::message).filterIsInstance<ServerMessage.GameStateUpdated>().single().game
        }
        val streak = snapshot.players.single { it.id == fixture.hostId }
        assertEquals(5, streak.combo)
        assertEquals(60, streak.score)

        val wrongNumber = if (streak.currentTarget == 1) 2 else 1
        val reset = fixture.engine.handle(
            fixture.hostId,
            ClientMessage.SelectNumber(fixture.roomId, wrongNumber, "combo-wrong")
        ).map(Delivery::message).filterIsInstance<ServerMessage.GameStateUpdated>().single().game
        assertEquals(0, reset.players.single { it.id == fixture.hostId }.combo)
    }

    @Test
    fun `survival ends after three wrong selections`() = runTest {
        val fixture = createUnlockedRoomFixture(ProtocolGameMode.SURVIVAL)
        val started = startRoom(fixture.engine, fixture.hostId, fixture.guestId, fixture.roomId)
        val target = started.players.single { it.id == fixture.hostId }.currentTarget
        val wrongNumber = if (target == 1) 2 else 1
        var snapshot = started

        repeat(3) { index ->
            snapshot = fixture.engine.handle(
                fixture.hostId,
                ClientMessage.SelectNumber(fixture.roomId, wrongNumber, "survival-$index")
            ).map(Delivery::message).filterIsInstance<ServerMessage.GameStateUpdated>().single().game
        }

        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, snapshot.phase)
        assertEquals(0, snapshot.players.single { it.id == fixture.hostId }.lives)
        assertEquals(fixture.guestId, snapshot.winnerPlayerId)
    }

    @Test
    fun `speed up ends when target deadline expires`() = runTest {
        var now = 1_000L
        val fixture = createUnlockedRoomFixture(ProtocolGameMode.SPEED_UP) { now }
        startRoom(fixture.engine, fixture.hostId, fixture.guestId, fixture.roomId)
        now += 5_001L

        val finished = fixture.engine.advanceTimedGames()
            .map(Delivery::message).filterIsInstance<ServerMessage.GameFinished>().single()

        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, finished.game.phase)
        assertTrue(finished.game.players.all { it.isFinished })
    }

    @Test
    fun `private four player tournament advances from semifinals to champion`() = runTest {
        val playerIds = List(4) { UUID.randomUUID().toString() }
        val names = listOf("Hiền", "Hiếu", "An", "Bình")
        val profiles = playerIds.mapIndexed { index, playerId ->
            playerId to PlayerProfileSnapshot(userId = "user1",
                displayName = names[index],
                playerCode = "PLAYER${index + 1}",
                progression = PlayerProgressionSnapshot(level = 20)
            )
        }.toMap()
        val profileRepository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = profiles[playerId]
            override suspend fun updateProfile(
                playerId: String,
                displayName: String,
                avatarId: String?
            ) = false
        }
        val friendRepository = object : FriendRepository {
            override suspend fun load(userId: String) = StoredFriends(
                friends = playerIds.filterNot { it == userId }.map { friendId ->
                    FriendSnapshot(
                        userId = friendId,
                        displayName = profiles.getValue(friendId).displayName,
                        playerCode = profiles.getValue(friendId).playerCode
                    )
                }
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
                firstUserId != secondUserId && firstUserId in playerIds && secondUserId in playerIds
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String) = false
        }
        val engine = GameEngine(
            playerProfileRepository = profileRepository,
            friendRepository = friendRepository
        )
        playerIds.forEachIndexed { index, playerId ->
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(playerId), names[index]))
        }

        val created = engine.handle(
            playerIds[0],
            ClientMessage.CreateTournament("Cúp bạn bè", ProtocolGameMode.SURVIVAL)
        ).map(Delivery::message).filterIsInstance<ServerMessage.TournamentUpdated>().single().tournament
        assertEquals(1, created.players.size)

        playerIds.drop(1).forEach { inviteeId ->
            val invitation = engine.handle(
                playerIds[0],
                ClientMessage.InviteTournamentPlayer(created.tournamentId, inviteeId)
            ).map(Delivery::message).filterIsInstance<ServerMessage.TournamentInvitation>().single().invitation
            engine.handle(
                inviteeId,
                ClientMessage.RespondTournamentInvitation(invitation.invitationId, accept = true)
            )
        }

        val startedDeliveries = engine.handle(
            playerIds[0],
            ClientMessage.StartTournament(created.tournamentId)
        ).map(Delivery::message)
        val semifinals = startedDeliveries.filterIsInstance<ServerMessage.GameStarted>()
            .map(ServerMessage.GameStarted::game)
        assertEquals(2, semifinals.size)
        assertTrue(semifinals.all { it.matchType == MatchType.CASUAL })
        assertTrue(semifinals.all { it.tournamentId == created.tournamentId && it.tournamentRound == 1 })

        val firstSemifinal = semifinals.single { playerIds[0] in it.players.map { player -> player.id } }
        loseSurvivalTournamentMatch(engine, firstSemifinal, playerIds[3], "semi-one")

        val secondSemifinal = semifinals.single { playerIds[1] in it.players.map { player -> player.id } }
        val secondFinish = loseSurvivalTournamentMatch(engine, secondSemifinal, playerIds[2], "semi-two")
        val finalGame = secondFinish.filterIsInstance<ServerMessage.GameStarted>().single().game
        assertEquals(2, finalGame.tournamentRound)
        assertEquals(setOf(playerIds[0], playerIds[1]), finalGame.players.map { it.id }.toSet())

        val finalFinish = loseSurvivalTournamentMatch(engine, finalGame, playerIds[1], "final")
        val completed = finalFinish.filterIsInstance<ServerMessage.TournamentUpdated>()
            .last().tournament
        assertEquals(com.hienthai.fastowin.protocol.TournamentPhase.FINISHED, completed.phase)
        assertEquals(playerIds[0], completed.championPlayerId)
        assertTrue(completed.matches.all {
            it.phase == com.hienthai.fastowin.protocol.TournamentMatchPhase.FINISHED
        })

        val history = engine.handle(playerIds[0], ClientMessage.GetTournamentHub)
            .map(Delivery::message).filterIsInstance<ServerMessage.TournamentHubData>().single().hub
        assertEquals(playerIds[0], history.recentTournaments.single().championPlayerId)
        assertEquals(null, history.activeTournament)
    }

    private suspend fun loseSurvivalTournamentMatch(
        engine: GameEngine,
        game: com.hienthai.fastowin.protocol.GameSnapshot,
        loserId: String,
        requestPrefix: String
    ): List<ServerMessage> {
        val target = game.players.single { it.id == loserId }.currentTarget
        val wrongNumber = if (target == 1) 2 else 1
        var messages = emptyList<ServerMessage>()
        repeat(3) { index ->
            messages = engine.handle(
                loserId,
                ClientMessage.SelectNumber(game.roomId, wrongNumber, "$requestPrefix-$index")
            ).map(Delivery::message)
        }
        return messages
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
    ): com.hienthai.fastowin.protocol.GameSnapshot {
        val joined = engine.handle(guestId, ClientMessage.JoinRoom(roomId, password))
            .map(Delivery::message).filterIsInstance<ServerMessage.RoomUpdated>().single().game
        assertEquals(setOf(hostId, guestId), joined.players.map { it.id }.toSet())
        engine.handle(hostId, ClientMessage.SetReady(roomId, true))
        val started = engine.handle(guestId, ClientMessage.SetReady(roomId, true))
            .map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single().game
        assertEquals(com.hienthai.fastowin.protocol.RoomPhase.PLAYING, started.phase)
        return started
    }

    private suspend fun createUnlockedRoomFixture(
        mode: ProtocolGameMode,
        nowMillis: () -> Long = System::currentTimeMillis
    ): Fixture {
        val hostId = UUID.randomUUID().toString()
        val guestId = UUID.randomUUID().toString()
        val profiles = mapOf(
            hostId to PlayerProfileSnapshot(userId = "user1",
                "Hiền", "HIEN001", progression = PlayerProgressionSnapshot(level = 12)
            ),
            guestId to PlayerProfileSnapshot(userId = "user1",
                "Hiếu", "HIEU001", progression = PlayerProgressionSnapshot(level = 12)
            )
        )
        val repository = object : PlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String) = profiles[playerId]
            override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
        }
        val engine = GameEngine(playerProfileRepository = repository, nowMillis = nowMillis)
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(hostId), "Hiền"))
        engine.connectAccount(AuthenticatedAccount(UUID.fromString(guestId), "Hiếu"))
        val room = engine.handle(
            hostId,
            ClientMessage.CreateRoom("Phòng ${mode.name}", PASSWORD, mode)
        ).map(Delivery::message).filterIsInstance<ServerMessage.RoomCreated>().single().game
        return Fixture(engine, hostId, guestId, room.roomId)
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
