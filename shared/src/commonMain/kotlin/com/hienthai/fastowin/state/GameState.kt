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

const val GAME_NUMBER_COUNT = 50
const val DEFAULT_OPPONENT_NAME = "Äá»‘i thá»§"

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
    RECONNECTING
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
    val isSpectator: Boolean = false
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
    val player: PlayerState = PlayerState("Bạn"),
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
    val isRematchRequestedByMe: Boolean = false,
    val isRematchRequestedByOpponent: Boolean = false,
    val rematchExpiresAtEpochMillis: Long? = null,
    val rematchNotice: String? = null,
    val lastMatchDurationMillis: Long? = null,
    val lastMatchEloChange: Int? = null,
    val lastMatchEloRating: Int? = null,
    val isProfileOpen: Boolean = false,
    val isProfileLoading: Boolean = false,
    val isProfileSaving: Boolean = false,
    val isClanOpen: Boolean = false,
    val isShopOpen: Boolean = false,
    val clanList: List<com.hienthai.fastowin.protocol.ClanSummarySnapshot> = emptyList(),
    val currentClan: com.hienthai.fastowin.protocol.ClanSnapshot? = null,
    val profileNotice: String? = null,
    val profile: PlayerProfileSnapshot? = null,
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
    roomInvitationPrompt = null,
    tournamentInvitationPrompt = null
)
