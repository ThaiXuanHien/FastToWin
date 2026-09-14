package com.hienthai.fastowin.screenshot

import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.AchievementSnapshot
import com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanQuestSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.CosmeticSnapshot
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.MissionDifficulty
import com.hienthai.fastowin.protocol.MissionSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.protocol.TournamentHubSnapshot
import com.hienthai.fastowin.protocol.TournamentMatchPhase
import com.hienthai.fastowin.protocol.TournamentMatchSnapshot
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentPlayerSnapshot
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationDestination
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.state.AvailableRoom
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState

object ArcadeScreenshotFixtures {
    const val NOW_MILLIS: Long = 1_788_793_200_000L

    fun profile(): PlayerProfileSnapshot = PlayerProfileSnapshot(
        userId = "player-1",
        displayName = "HienTX",
        playerCode = "HIEN042",
        avatarId = "male",
        statistics = PlayerStatisticsSnapshot(
            totalMatches = 286,
            wins = 184,
            losses = 91,
            draws = 11,
            highestScore = 50,
            currentWinStreak = 6,
            bestWinStreak = 19,
            correctSelections = 9_642,
            wrongSelections = 317,
            averageReactionMillis = 684,
            eloRating = 1_742
        ),
        recentMatches = listOf(
            MatchHistorySnapshot(
                matchId = "match-101",
                roomName = "Đấu trường Tia Chớp",
                gameMode = ProtocolGameMode.ORDER,
                opponentName = "Minh Anh",
                playerScore = 50,
                opponentScore = 43,
                outcome = MatchHistoryOutcome.WIN,
                endedAtEpochMillis = NOW_MILLIS - 3_600_000L,
                eloChange = 18,
                matchType = MatchType.RANKED
            ),
            MatchHistorySnapshot(
                matchId = "match-100",
                roomName = "Phòng luyện phản xạ",
                gameMode = ProtocolGameMode.SPEED_UP,
                opponentName = "Kenji",
                playerScore = 42,
                opponentScore = 50,
                outcome = MatchHistoryOutcome.LOSS,
                endedAtEpochMillis = NOW_MILLIS - 86_400_000L,
                eloChange = -12,
                matchType = MatchType.RANKED
            )
        ),
        achievements = listOf(
            AchievementSnapshot(
                code = "WIN_10",
                title = "Bách Chiến",
                description = "Thắng 10 trận đấu",
                unlockedAtEpochMillis = NOW_MILLIS - 604_800_000L,
                progress = 10,
                target = 10,
                difficulty = MissionDifficulty.NORMAL,
                rewardGold = 500
            ),
            AchievementSnapshot(
                code = "SPEED_50",
                title = "Thần Tốc",
                description = "Hoàn thành bàn số trong thời gian kỷ lục",
                unlockedAtEpochMillis = 0L,
                progress = 37,
                target = 50,
                difficulty = MissionDifficulty.HARD,
                rewardGems = 5
            )
        ),
        progression = PlayerProgressionSnapshot(
            level = 42,
            experiencePoints = 48_600,
            gold = 12_500,
            gems = 320,
            currentLevelExperience = 4_860,
            nextLevelExperience = 5_170,
            dailyMissions = listOf(
                MissionSnapshot(
                    code = "DAILY_PLAY_3",
                    title = "Chơi 3 trận hôm nay",
                    progress = 2,
                    target = 3,
                    completed = false,
                    rewardXp = 40,
                    rewardGold = 120,
                    difficulty = MissionDifficulty.EASY
                ),
                MissionSnapshot(
                    code = "DAILY_WIN_1",
                    title = "Thắng một trận đấu",
                    progress = 1,
                    target = 1,
                    completed = true,
                    rewardXp = 60,
                    rewardGold = 200,
                    difficulty = MissionDifficulty.NORMAL
                )
            ),
            weeklyMissions = listOf(
                MissionSnapshot(
                    code = "WEEKLY_CORRECT_100",
                    title = "Chọn đúng 100 số",
                    progress = 73,
                    target = 100,
                    completed = false,
                    rewardXp = 180,
                    rewardGold = 800,
                    rewardGems = 3,
                    difficulty = MissionDifficulty.HARD
                )
            ),
            dailyCheckIn = DailyCheckInSnapshot(
                claimedToday = false,
                cycleDay = 5,
                todayRewardXp = 50,
                todayRewardGold = 250,
                todayRewardGems = 1,
                currentStreak = 12,
                bestStreak = 24,
                totalCheckIns = 86
            ),
            cosmetics = listOf(
                CosmeticSnapshot("frame_default", "Khung mặc định", CosmeticType.FRAME, true, false),
                CosmeticSnapshot("frame_lightning", "Tia Chớp", CosmeticType.FRAME, true, true),
                CosmeticSnapshot("frame_warrior", "Chiến Binh", CosmeticType.FRAME, true, false),
                CosmeticSnapshot("frame_dragon_might", "Long Uy", CosmeticType.FRAME, false, false),
                CosmeticSnapshot("title_rookie", "Khai Chiến", CosmeticType.TITLE, true, false),
                CosmeticSnapshot("title_godspeed", "Thần Tốc", CosmeticType.TITLE, true, true),
                CosmeticSnapshot("title_unbeaten", "Bất Bại", CosmeticType.TITLE, false, false)
            )
        ),
        clanId = "clan-1",
        clanName = "Thần Tốc Việt"
    )

    fun homeState(): GameState = GameState(
        lobbyStage = LobbyStage.SELECT_MODE,
        player = localPlayer(),
        profile = profile(),
        connectionStatus = ConnectionStatus.CONNECTED,
        playQuota = quota(),
        notifications = notifications()
    )

    fun roomBrowserState(): GameState = homeState().copy(
        lobbyStage = LobbyStage.ROOM_BROWSER,
        availableRooms = listOf(
            AvailableRoom("room-1", "Phòng Tia Chớp", "Minh Anh", GameMode.ORDER, MatchType.RANKED, false, NOW_MILLIS),
            AvailableRoom("room-2", "Biệt đội Bất Khuất", "Kenji", GameMode.TEAM_2V2, MatchType.CASUAL, true, NOW_MILLIS - 1_000L),
            AvailableRoom("room-3", "Đua top buổi tối", "Sofia", GameMode.SPEED_UP, MatchType.RANKED, false, NOW_MILLIS - 2_000L)
        )
    )

    fun activeGameState(): GameState = GameState(
        numbers = (23..50).toList() + (1..22).toList(),
        currentTarget = 23,
        score = 28,
        timeLeftMillis = 80_000L,
        gameMode = GameMode.ORDER,
        matchType = MatchType.RANKED,
        player = localPlayer().copy(
            score = 28,
            currentTarget = 23,
            correctSelections = 22,
            wrongSelections = 1,
            selectedNumbers = (1..22).toList(),
            combo = 7
        ),
        opponent = PlayerState(
            name = "Minh Anh",
            id = "player-2",
            score = 24,
            currentTarget = 20,
            correctSelections = 19,
            wrongSelections = 2,
            selectedNumbers = (1..19).toList(),
            avatarId = "female",
            frameId = "frame_fire"
        ),
        connectionStatus = ConnectionStatus.CONNECTED,
        currentRoomId = "room-ranked-37",
        currentRoomName = "Đấu trường Tia Chớp",
        currentMatchId = "match-live-37",
        hasOpponent = true,
        isMatchStarted = true,
        latencyMillis = 42L,
        profile = profile()
    )

    fun rankedWinState(): GameState = activeGameState().copy(
        isGameOver = true,
        currentTarget = 51,
        player = activeGameState().player.copy(score = 50, currentTarget = 51, correctSelections = 50, isFinished = true),
        opponent = activeGameState().opponent.copy(score = 43, currentTarget = 44, correctSelections = 43, isFinished = true),
        winnerPlayerId = "player-1",
        lastMatchDurationMillis = 74_000L,
        lastMatchEloChange = 18,
        lastMatchEloRating = 1_760
    )

    fun leaderboardState(): GameState {
        val entries = listOf(
            leaderboardEntry(1, "Linh Chi", "LINH001", 2_468, "player-8", "frame_challenger"),
            leaderboardEntry(2, "HienTX", "HIEN042", 1_742, "player-1", "frame_lightning"),
            leaderboardEntry(3, "Akira", "AKIRA77", 1_698, "player-9", "frame_diamond")
        )
        return homeState().copy(
            leaderboard = LeaderboardSnapshot(
                topPlayers = entries,
                currentPlayer = entries[1],
                seasonName = "Mùa Khởi Đầu",
                seasonTopPlayers = entries,
                seasonCurrentPlayer = entries[1],
                topGoldPlayers = entries.sortedByDescending { it.lifetimeEarnedGold },
                currentGoldPlayer = entries[1],
                topGemPlayers = entries.sortedByDescending { it.lifetimeEarnedGems },
                currentGemPlayer = entries[1]
            )
        )
    }

    fun clanState(): GameState = homeState().copy(
        currentClan = clan(),
        clanList = emptyList()
    )

    fun tournamentState(): GameState = homeState().copy(
        tournamentHub = TournamentHubSnapshot(activeTournament = tournament())
    )

    fun notifications(): List<AppNotification> = listOf(
        AppNotification(
            id = "notification-1",
            kind = AppNotificationKind.ACHIEVEMENT,
            title = "Mở khóa thành tích",
            message = "Bạn đã nhận danh hiệu Thần Tốc và 500 Vàng.",
            createdAtEpochMillis = NOW_MILLIS,
            destination = AppNotificationDestination.PROFILE
        ),
        AppNotification(
            id = "notification-2",
            kind = AppNotificationKind.ROOM_INVITATION,
            title = "Lời mời vào phòng",
            message = "Minh Anh mời bạn vào Đấu trường Tia Chớp.",
            createdAtEpochMillis = NOW_MILLIS - 3_600_000L,
            destination = AppNotificationDestination.FRIENDS,
            isRead = true
        ),
        AppNotification(
            id = "notification-3",
            kind = AppNotificationKind.MISSION,
            title = "Nhiệm vụ hoàn thành",
            message = "Phần thưởng đang chờ bạn nhận trong Hồ sơ.",
            createdAtEpochMillis = NOW_MILLIS - 86_400_000L,
            destination = AppNotificationDestination.PROFILE
        )
    )

    fun gemPackages(): List<GemPackageSnapshot> = listOf(
        GemPackageSnapshot("gems_80", "Túi Gem", 80),
        GemPackageSnapshot("gems_320", "Rương Gem", 320, featured = true),
        GemPackageSnapshot("gems_800", "Kho Gem", 800)
    )

    fun requireValid() {
        check(homeState().profile != null)
        check(roomBrowserState().availableRooms.size >= 3)
        check(activeGameState().numbers.size == 50)
        check(rankedWinState().isGameOver)
        check(leaderboardState().leaderboard != null)
        check(clanState().currentClan != null)
        check(tournamentState().tournamentHub.activeTournament?.maxPlayers == 8)
        check(notifications().any { it.isRead } && notifications().any { !it.isRead })
        check(gemPackages().size == 3)
    }

    private fun quota() = PlayQuotaSnapshot(
        quotaDate = "2026-09-13",
        matchesConsumed = 2,
        remainingMatches = 8,
        nextResetAtEpochMillis = NOW_MILLIS + 43_200_000L,
        rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
    )

    private fun localPlayer() = PlayerState(
        name = "HienTX",
        id = "player-1",
        avatarId = "male",
        frameId = "frame_lightning"
    )

    private fun leaderboardEntry(
        rank: Int,
        name: String,
        code: String,
        elo: Int,
        userId: String,
        frameId: String
    ) = LeaderboardEntrySnapshot(
        rank = rank,
        displayName = name,
        playerCode = code,
        wins = 100 - rank * 7,
        totalMatches = 140,
        highestScore = 50,
        eloRating = elo,
        userId = userId,
        avatarId = if (rank == 1) "female" else "male",
        frameId = frameId,
        lifetimeEarnedGold = 50_000L - rank * 3_500L,
        lifetimeEarnedGems = 2_000L - rank * 125L
    )

    private fun clan() = ClanSnapshot(
        id = "clan-1",
        name = "Thần Tốc Việt",
        description = "Nhanh, chính xác và luôn đồng đội.",
        ownerId = "player-1",
        members = listOf(
            ClanMemberSnapshot("player-1", "HienTX", ClanRole.LEADER, 1_742, 86, true, 120_000, 1_200),
            ClanMemberSnapshot("player-2", "Minh Anh", ClanRole.CO_LEADER, 1_698, 72, true, 95_000, 980),
            ClanMemberSnapshot("player-3", "Kenji", ClanRole.MEMBER, 1_521, 55, false, 63_000, 610),
            ClanMemberSnapshot("player-4", "Sofia", ClanRole.MEMBER, 1_488, 48, false, 51_000, 440)
        ),
        trophies = 6_449,
        logoId = "dragon",
        quest = ClanQuestSnapshot(progress = 261, target = 350, rewardGold = 5_000, rewardXp = 800, rewardGems = 10),
        level = 18,
        experiencePoints = 228_400,
        currentLevelExperience = 8_400,
        nextLevelExperience = 15_000,
        donatedGold = 329_000,
        donatedGems = 3_230
    )

    private fun tournament(): TournamentSnapshot {
        val players = listOf("HienTX", "Minh Anh", "Kenji", "Sofia", "Linh Chi", "Akira", "Lucas", "Mia")
            .mapIndexed { index, name ->
                TournamentPlayerSnapshot(
                    playerId = "t-player-${index + 1}",
                    displayName = name,
                    isHost = index == 0,
                    isOnline = true
                )
            }
        return TournamentSnapshot(
            tournamentId = "tournament-8",
            name = "Cúp Vô Song",
            hostPlayerId = players.first().playerId,
            gameMode = ProtocolGameMode.ORDER,
            phase = TournamentPhase.RUNNING,
            maxPlayers = 8,
            entryFee = 500,
            prizePool = 4_000,
            players = players,
            matches = listOf(
                TournamentMatchSnapshot("quarter-1", 1, 1, players[0].playerId, players[1].playerId, players[0].playerId, phase = TournamentMatchPhase.FINISHED),
                TournamentMatchSnapshot("quarter-2", 1, 2, players[2].playerId, players[3].playerId, players[3].playerId, phase = TournamentMatchPhase.FINISHED),
                TournamentMatchSnapshot("quarter-3", 1, 3, players[4].playerId, players[5].playerId, phase = TournamentMatchPhase.PLAYING),
                TournamentMatchSnapshot("quarter-4", 1, 4, players[6].playerId, players[7].playerId),
                TournamentMatchSnapshot("semi-1", 2, 1, players[0].playerId, players[3].playerId),
                TournamentMatchSnapshot("semi-2", 2, 2),
                TournamentMatchSnapshot("final", 3, 1)
            ),
            createdAtEpochMillis = NOW_MILLIS - 86_400_000L,
            startedAtEpochMillis = NOW_MILLIS - 1_800_000L
        )
    }
}
