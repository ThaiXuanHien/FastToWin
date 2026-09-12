package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.MatchDetailSnapshot
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.TournamentHubSnapshot
import com.hienthai.fastowin.protocol.TournamentInvitationSnapshot
import com.hienthai.fastowin.protocol.WalletTransactionSnapshot
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.protocol.RoomPhase

const val GAME_NUMBER_COUNT = 50
const val DEFAULT_LOCAL_PLAYER_NAME = "Player"
const val DEFAULT_OPPONENT_NAME = "Opponent"

enum class LobbyStage {
    SELECT_MODE,
    ENTER_NAME,
    ROOM_BROWSER,
    ROOM_WAITING,
    MATCHMAKING,
    MATCHED
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    TERMINAL
}

data class AvailableRoom(
    val id: String,
    val name: String,
    val hostName: String,
    val gameMode: GameMode,
    val matchType: MatchType = MatchType.CASUAL,
    val requiresPassword: Boolean,
    val lastSeenAtMillis: Long
)

data class PlayerState(
    val name: String,
    val id: String? = null,
    val isReady: Boolean = false,
    val score: Int = 0,
    val currentTarget: Int = 1,
    val correctSelections: Int = 0,
    val wrongSelections: Int = 0,
    val averageReactionMillis: Long = 0,
    val selectedNumbers: List<Int> = emptyList(),
    val combo: Int = 0,
    val lives: Int = 3,
    val isFinished: Boolean = false,
    val fastestSegmentStart: Int = 0,
    val fastestSegmentEnd: Int = 0,
    val fastestSegmentAverageMillis: Long = 0,
    val slowestSegmentStart: Int = 0,
    val slowestSegmentEnd: Int = 0,
    val slowestSegmentAverageMillis: Long = 0,
    val teamId: String? = null,
    val isSpectator: Boolean = false,
    val avatarId: String? = null,
    val frameId: String = "frame_default"
)

data class EmojiEvent(
    val id: Long,
    val emojiId: String,
    val playerId: String
)

enum class PostMatchFriendStatus {
    UNAVAILABLE,
    AVAILABLE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIEND,
    BLOCKED
}

data class GameState(
    val numbers: List<Int> = emptyList(),
    val currentTarget: Int = 1,
    val score: Int = 0,
    val timeLeftMillis: Long = 0,
    val isGameOver: Boolean = false,
    val gameMode: GameMode = GameMode.ORDER,
    val matchType: MatchType = MatchType.CASUAL,
    val message: String? = null,
    val error: String? = null,
    
    // Lobby flow
    val lobbyStage: LobbyStage = LobbyStage.SELECT_MODE,
    
    // Multiplayer simulation
    val player: PlayerState = PlayerState(DEFAULT_LOCAL_PLAYER_NAME),
    val opponent: PlayerState = PlayerState(DEFAULT_OPPONENT_NAME),
    val teammates: List<PlayerState> = emptyList(),
    val opponents: List<PlayerState> = emptyList(),
    val spectators: List<PlayerState> = emptyList(),
    val isSearching: Boolean = false,
    val isMatchmaking: Boolean = false,
    val matchmakingStartedAtMillis: Long? = null,
    val matchmakingRatingRange: Int = 100,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val availableRooms: List<AvailableRoom> = emptyList(),
    val roomListVersion: Long = 0,
    val pendingRoomLinkId: String? = null,
    val pendingRoomLinkListVersion: Long = 0,
    val currentRoomId: String? = null,
    val currentRoomName: String? = null,
    val latestGameSequence: Long = -1L,
    val authoritativeSnapshotRevision: Long = 0L,
    val isRoomHost: Boolean = false,
    val hasOpponent: Boolean = false,
    val latencyMillis: Long? = null,
    val countdown: Int? = null,
    val isMatchStarted: Boolean = false,
    val currentMatchId: String? = null,
    val currentTournamentId: String? = null,
    val currentTournamentMatchId: String? = null,
    val currentTournamentRound: Int? = null,
    val winnerPlayerId: String? = null,
    val winnerTeamId: String? = null,
    val didForfeitLastMatch: Boolean = false,
    val isRematchRequestedByMe: Boolean = false,
    val isRematchRequestedByOpponent: Boolean = false,
    val isRematchActionPending: Boolean = false,
    val rematchExpiresAtEpochMillis: Long? = null,
    val rematchNotice: String? = null,
    val rematchNoticeErrorCode: String? = null,
    val lastMatchDurationMillis: Long? = null,
    val lastMatchEloChange: Int? = null,
    val lastMatchEloRating: Int? = null,
    val isProfileOpen: Boolean = false,
    val isProfileLoading: Boolean = false,
    val isProfileSaving: Boolean = false,
    val isPushPreferencesSaving: Boolean = false,
    val equippingCosmeticId: String? = null,
    val isClanOpen: Boolean = false,
    val isShopOpen: Boolean = false,
    val gemStorePackages: List<com.hienthai.fastowin.protocol.GemPackageSnapshot> = emptyList(),
    val isGemStoreCatalogLoading: Boolean = false,
    val storeSandboxEnabled: Boolean = false,
    val verifyingStorePurchaseRequestId: String? = null,
    val storePurchaseResult: com.hienthai.fastowin.protocol.ServerMessage.StorePurchaseResult? = null,
    val exchangingGoldRequestId: String? = null,
    val goldExchangeResult: com.hienthai.fastowin.protocol.ServerMessage.GoldExchangeResult? = null,
    val playQuota: PlayQuotaSnapshot? = null,
    val showPlayQuotaExhaustedDialog: Boolean = false,
    val rewardedAdBonusResult: ServerMessage.RewardedAdBonusResult? = null,
    val claimingRewardedAdRequestId: String? = null,
    val clanList: List<com.hienthai.fastowin.protocol.ClanSummarySnapshot> = emptyList(),
    val pendingClanJoinIds: Set<String> = emptySet(),
    val currentClan: com.hienthai.fastowin.protocol.ClanSnapshot? = null,
    val clanNotice: String? = null,
    val donatingClanRequestId: String? = null,
    val clanDonationResult: com.hienthai.fastowin.protocol.ServerMessage.ClanDonationResult? = null,
    val profileNotice: String? = null,
    val profile: PlayerProfileSnapshot? = null,
    val avatarRevision: Long = 0L,
    val walletTransactions: List<WalletTransactionSnapshot> = emptyList(),
    val isWalletHistoryLoading: Boolean = false,
    val isDailyCheckInClaiming: Boolean = false,
    val claimingMissionCode: String? = null,
    val viewedFriendUserId: String? = null,
    val friendProfile: PlayerProfileSnapshot? = null,
    val isFriendProfileOpen: Boolean = false,
    val isFriendProfileLoading: Boolean = false,
    val matchDetail: MatchDetailSnapshot? = null,
    val isMatchDetailLoading: Boolean = false,
    val isLeaderboardOpen: Boolean = false,
    val isLeaderboardLoading: Boolean = false,
    val leaderboard: LeaderboardSnapshot? = null,
    val isFriendsOpen: Boolean = false,
    val isFriendsLoading: Boolean = false,
    val social: FriendsSnapshot = FriendsSnapshot(),
    val roomInvitations: List<ServerMessage.RoomInvitation> = emptyList(),
    val sendingRoomInviteFriendIds: Set<String> = emptySet(),
    val invitedRoomFriendIds: Set<String> = emptySet(),
    val activeEmojis: List<EmojiEvent> = emptyList(),
    val roomInvitationPrompt: ServerMessage.RoomInvitation? = null,
    val socialNotice: String? = null,
    val isNotificationsOpen: Boolean = false,
    val isTournamentOpen: Boolean = false,
    val isTournamentLoading: Boolean = false,
    val tournamentHub: TournamentHubSnapshot = TournamentHubSnapshot(),
    val tournamentInvitationPrompt: TournamentInvitationSnapshot? = null,
    val tournamentNotice: String? = null,
    val notifications: List<AppNotification> = emptyList(),
    val dismissedNotificationIds: Set<String> = emptySet()
) {
    val pendingSocialInvitationCount: Int
        get() = social.incomingRequests.size + roomInvitations.size

    val unreadNotificationCount: Int
        get() = notifications.count { !it.isRead }

    val isTournamentMatch: Boolean
        get() = currentTournamentId != null && currentTournamentMatchId != null

    val postMatchFriendStatus: PostMatchFriendStatus
        get() {
            if (profile == null) return PostMatchFriendStatus.UNAVAILABLE
            val opponentId = opponent.id ?: return PostMatchFriendStatus.UNAVAILABLE
            return when {
                social.blockedPlayers.any { it.userId == opponentId } -> PostMatchFriendStatus.BLOCKED
                social.friends.any { it.userId == opponentId } -> PostMatchFriendStatus.FRIEND
                social.incomingRequests.any { it.userId == opponentId } -> PostMatchFriendStatus.REQUEST_RECEIVED
                social.outgoingRequests.any { it.userId == opponentId } -> PostMatchFriendStatus.REQUEST_SENT
                social.recentPlayers.any { it.userId == opponentId } -> PostMatchFriendStatus.AVAILABLE
                else -> PostMatchFriendStatus.UNAVAILABLE
            }
        }
}

internal fun GameState.prepareForMatchStart(): GameState = copy(
    isProfileOpen = false,
    isProfileLoading = false,
    equippingCosmeticId = null,
    isFriendProfileOpen = false,
    isFriendProfileLoading = false,
    viewedFriendUserId = null,
    friendProfile = null,
    isLeaderboardOpen = false,
    isLeaderboardLoading = false,
    isFriendsOpen = false,
    isFriendsLoading = false,
    isNotificationsOpen = false,
    isTournamentOpen = false,
    didForfeitLastMatch = false,
    roomInvitationPrompt = null,
    tournamentInvitationPrompt = null
)

internal fun GameState.registerOptimisticNumberSelection(number: Int): GameState {
    if (number == player.currentTarget) return this
    return copy(
        player = player.copy(
            wrongSelections = player.wrongSelections + 1,
            combo = 0
        )
    )
}

internal fun GameState.canApplyGameSnapshot(roomId: String, sequence: Long): Boolean =
    currentRoomId != roomId || sequence >= latestGameSequence

/** Copies the complete match view owned by the server; no optimistic match data survives. */
internal fun GameState.applyAuthoritativeSnapshot(game: GameSnapshot, playerId: String): GameState {
    val meSnapshot = game.players.firstOrNull { it.id == playerId }
        ?: game.spectators.firstOrNull { it.id == playerId }
    val myTeamId = meSnapshot?.teamId
    val (teammateSnapshots, opponentSnapshots) = game.players.filter { it.id != playerId }
        .partition { it.teamId != null && it.teamId == myTeamId }
    val opponent = opponentSnapshots.firstOrNull()
    val spectatorSnapshots = game.spectators.filter { it.id != playerId }
    val finished = game.phase == RoomPhase.FINISHED
    val playing = game.phase == RoomPhase.PLAYING
    val authoritativePlayer = meSnapshot?.toAuthoritativePlayerState(
        fallbackName = player.name,
        isSpectator = meSnapshot in game.spectators
    ) ?: player

    return copy(
        numbers = game.numbers,
        currentTarget = meSnapshot?.currentTarget ?: game.currentTarget,
        score = meSnapshot?.score ?: 0,
        timeLeftMillis = meSnapshot?.timeLeftMillis ?: 0L,
        isGameOver = finished,
        gameMode = game.gameMode.toUiGameMode(),
        matchType = game.matchType,
        player = authoritativePlayer,
        opponent = opponent?.toAuthoritativePlayerState(DEFAULT_OPPONENT_NAME) ?: PlayerState(DEFAULT_OPPONENT_NAME),
        teammates = teammateSnapshots.map { it.toAuthoritativePlayerState(DEFAULT_LOCAL_PLAYER_NAME) },
        opponents = opponentSnapshots.map { it.toAuthoritativePlayerState(DEFAULT_OPPONENT_NAME) },
        spectators = spectatorSnapshots.map { it.toAuthoritativePlayerState(DEFAULT_OPPONENT_NAME, isSpectator = true) },
        lobbyStage = if (playing || finished) LobbyStage.MATCHED else LobbyStage.ROOM_WAITING,
        currentRoomId = game.roomId,
        currentRoomName = game.roomName,
        latestGameSequence = game.sequence,
        authoritativeSnapshotRevision = authoritativeSnapshotRevision + 1L,
        isRoomHost = game.hostId == playerId,
        hasOpponent = opponentSnapshots.isNotEmpty(),
        isSearching = false,
        isMatchmaking = false,
        matchmakingStartedAtMillis = null,
        isMatchStarted = playing,
        currentMatchId = game.matchId,
        currentTournamentId = game.tournamentId,
        currentTournamentMatchId = game.tournamentMatchId,
        currentTournamentRound = game.tournamentRound,
        winnerPlayerId = game.winnerPlayerId,
        winnerTeamId = game.winnerTeamId,
        didForfeitLastMatch = false,
        isRematchRequestedByMe = playerId in game.rematchRequestedPlayerIds,
        isRematchRequestedByOpponent = game.rematchRequestedPlayerIds.any { it != playerId },
        isRematchActionPending = false,
        rematchExpiresAtEpochMillis = game.rematchExpiresAtEpochMillis,
        rematchNotice = null,
        rematchNoticeErrorCode = null,
        lastMatchDurationMillis = if (finished) {
            val startedAt = game.startedAtEpochMillis
            val finishedAt = game.finishedAtEpochMillis
            if (startedAt != null && finishedAt != null) (finishedAt - startedAt).coerceAtLeast(0L) else null
        } else null,
        lastMatchEloChange = null,
        lastMatchEloRating = null,
        countdown = null,
        message = null,
        error = null
    )
}

private fun PlayerSnapshot.toAuthoritativePlayerState(
    fallbackName: String,
    isSpectator: Boolean = false
): PlayerState = PlayerState(
    name = name.takeIf { it.isNotBlank() } ?: fallbackName,
    id = id,
    avatarId = avatarId,
    frameId = frameId,
    teamId = teamId,
    isReady = isReady,
    score = score,
    currentTarget = currentTarget,
    correctSelections = correctSelections,
    wrongSelections = wrongSelections,
    averageReactionMillis = averageReactionMillis,
    selectedNumbers = selectedNumbers,
    combo = combo,
    lives = lives,
    isFinished = isFinished,
    fastestSegmentStart = fastestSegmentStart,
    fastestSegmentEnd = fastestSegmentEnd,
    fastestSegmentAverageMillis = fastestSegmentAverageMillis,
    slowestSegmentStart = slowestSegmentStart,
    slowestSegmentEnd = slowestSegmentEnd,
    slowestSegmentAverageMillis = slowestSegmentAverageMillis,
    isSpectator = isSpectator
)

private fun ProtocolGameMode.toUiGameMode(): GameMode = when (this) {
    ProtocolGameMode.ORDER -> GameMode.ORDER
    ProtocolGameMode.RANDOM_TARGET -> GameMode.RANDOM_TARGET
    ProtocolGameMode.TIME_BONUS -> GameMode.TIME_BONUS
    ProtocolGameMode.SPEED_UP -> GameMode.SPEED_UP
    ProtocolGameMode.SURVIVAL -> GameMode.SURVIVAL
    ProtocolGameMode.COMBO -> GameMode.COMBO
    ProtocolGameMode.TIME_ATTACK -> GameMode.TIME_ATTACK
    ProtocolGameMode.TEAM_2V2 -> GameMode.TEAM_2V2
}

internal fun GameState.withReadySession(playerId: String): GameState {
    val clearRematchError = rematchNoticeErrorCode in REMATCH_CONNECTION_ERROR_CODES
    return copy(
        player = player.copy(id = playerId),
        isSearching = false,
        error = null,
        rematchNotice = if (clearRematchError) null else rematchNotice,
        rematchNoticeErrorCode = if (clearRematchError) null else rematchNoticeErrorCode
    )
}

internal fun GameState.withPlayQuota(message: ServerMessage.PlayQuotaData): GameState = copy(
    playQuota = message.quota,
    showPlayQuotaExhaustedDialog = showPlayQuotaExhaustedDialog && message.quota.remainingMatches <= 0,
    error = null
)

internal fun GameState.withRewardedAdBonus(
    message: ServerMessage.RewardedAdBonusResult,
    notice: String
): GameState = copy(
    playQuota = message.quota,
    rewardedAdBonusResult = message,
    claimingRewardedAdRequestId = null,
    showPlayQuotaExhaustedDialog = message.quota.remainingMatches <= 0,
    profileNotice = notice,
    error = null
)

internal fun GameState.withPlayQuotaError(
    error: ServerMessage.Error,
    localizedMessage: String = error.message
): GameState = if (error.code == "PLAY_QUOTA_EXHAUSTED") {
    copy(
        showPlayQuotaExhaustedDialog = true,
        isSearching = false,
        isMatchmaking = false,
        matchmakingStartedAtMillis = null,
        error = localizedMessage
    )
} else {
    this
}

internal fun GameState.withRematchError(
    error: ServerMessage.Error,
    localizedMessage: String = error.message
): GameState {
    if (!isGameOver || error.code !in REMATCH_ACTION_ERROR_CODES) return this
    // Background requests (including latency pings) are not rematch actions.
    if (error.code in REMATCH_CONNECTION_ERROR_CODES && !isRematchActionPending) return this
    return copy(
        isRematchActionPending = false,
        isRematchRequestedByMe = false,
        isRematchRequestedByOpponent = false,
        rematchNotice = localizedMessage,
        rematchNoticeErrorCode = error.code
    )
}

private val REMATCH_CONNECTION_ERROR_CODES = setOf(
    "CONNECTION_NOT_READY", "CONNECTION_FAILED", "SEND_FAILED"
)

private val REMATCH_ACTION_ERROR_CODES = REMATCH_CONNECTION_ERROR_CODES + setOf(
    "RATE_LIMITED", "NOT_IN_ROOM", "OPPONENT_LEFT", "TOURNAMENT_REMATCH_DISABLED",
    "RANKED_REMATCH_DISABLED", "REMATCH_NOT_AVAILABLE", "REMATCH_NOT_PENDING"
)

internal fun GameState.openNotificationsOverlay(): GameState = copy(
    isNotificationsOpen = true,
    error = null
)

internal fun GameState.closeNotificationsOverlay(): GameState = copy(
    isNotificationsOpen = false
)

internal fun GameState.prepareForRoomWaiting(): GameState = copy(
    isProfileOpen = false,
    isProfileLoading = false,
    equippingCosmeticId = null,
    isFriendProfileOpen = false,
    isFriendProfileLoading = false,
    viewedFriendUserId = null,
    friendProfile = null,
    isLeaderboardOpen = false,
    isLeaderboardLoading = false,
    isFriendsOpen = false,
    isFriendsLoading = false,
    isNotificationsOpen = false,
    isTournamentOpen = false,
    isTournamentLoading = false,
    isShopOpen = false,
    isClanOpen = false,
    didForfeitLastMatch = false,
    roomInvitationPrompt = null,
    tournamentInvitationPrompt = null
)
