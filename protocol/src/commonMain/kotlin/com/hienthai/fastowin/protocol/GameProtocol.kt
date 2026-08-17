package com.hienthai.fastowin.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 16
const val GAME_NUMBER_COUNT = 50
const val MAX_PROFILE_DISPLAY_NAME_LENGTH = 32
val DAILY_CHECK_IN_REWARDS_XP = listOf(5, 10, 10, 15, 15, 20, 25)

val PROFILE_AVATAR_IDS = setOf("bolt", "rocket", "target", "trophy", "crown", "star")

val ProtocolJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
enum class ProtocolGameMode {
    ORDER,
    TIME_ATTACK
}

@Serializable
enum class RoomPhase {
    WAITING,
    PLAYING,
    FINISHED
}

@Serializable
data class RoomSummary(
    val id: String,
    val name: String,
    val hostName: String,
    val gameMode: ProtocolGameMode,
    val requiresPassword: Boolean
)

@Serializable
data class PlayerSnapshot(
    val id: String,
    val name: String,
    val score: Int,
    val isReady: Boolean = false,
    val correctSelections: Int = 0,
    val wrongSelections: Int = 0,
    val averageReactionMillis: Long = 0
)

@Serializable
enum class MatchHistoryOutcome {
    WIN,
    LOSS,
    DRAW
}

@Serializable
data class PlayerStatisticsSnapshot(
    val totalMatches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val highestScore: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val correctSelections: Int = 0,
    val wrongSelections: Int = 0,
    val averageReactionMillis: Long = 0,
    val eloRating: Int = 1000
)

@Serializable
data class MatchHistorySnapshot(
    val matchId: String,
    val roomName: String,
    val gameMode: ProtocolGameMode,
    val opponentName: String,
    val playerScore: Int,
    val opponentScore: Int,
    val outcome: MatchHistoryOutcome,
    val endedAtEpochMillis: Long,
    val eloChange: Int = 0
)

@Serializable
data class PlayerProfileSnapshot(
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val statistics: PlayerStatisticsSnapshot = PlayerStatisticsSnapshot(),
    val recentMatches: List<MatchHistorySnapshot> = emptyList(),
    val achievements: List<AchievementSnapshot> = emptyList(),
    val progression: PlayerProgressionSnapshot = PlayerProgressionSnapshot()
)

@Serializable
enum class CosmeticType { FRAME, TITLE }

@Serializable
data class CosmeticSnapshot(
    val id: String,
    val name: String,
    val type: CosmeticType,
    val unlocked: Boolean,
    val equipped: Boolean
)

@Serializable
data class MissionSnapshot(
    val code: String,
    val title: String,
    val progress: Int,
    val target: Int,
    val completed: Boolean
)

@Serializable
data class DailyCheckInSnapshot(
    val claimedToday: Boolean = false,
    val cycleDay: Int = 1,
    val todayRewardXp: Int = 5,
    val nextRewardXp: Int = 10,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCheckIns: Int = 0,
    val lastCheckInDate: String? = null
)

@Serializable
data class SeasonSnapshot(
    val name: String,
    val tier: String,
    val rating: Int,
    val endsAtEpochMillis: Long,
    val rewardDescription: String
)

@Serializable
data class PlayerProgressionSnapshot(
    val level: Int = 1,
    val experiencePoints: Int = 0,
    val currentLevelExperience: Int = 0,
    val nextLevelExperience: Int = 100,
    val dailyMissions: List<MissionSnapshot> = emptyList(),
    val weeklyMissions: List<MissionSnapshot> = emptyList(),
    val dailyCheckIn: DailyCheckInSnapshot = DailyCheckInSnapshot(),
    val cosmetics: List<CosmeticSnapshot> = emptyList(),
    val season: SeasonSnapshot? = null
)

@Serializable
data class AchievementSnapshot(
    val code: String,
    val title: String,
    val description: String,
    val unlockedAtEpochMillis: Long
)

@Serializable
data class MatchEventSnapshot(
    val sequence: Int,
    val playerName: String,
    val isCurrentPlayer: Boolean,
    val number: Int,
    val expectedNumber: Int,
    val accepted: Boolean,
    val occurredAtEpochMillis: Long
)

@Serializable
data class MatchDetailSnapshot(
    val summary: MatchHistorySnapshot,
    val durationMillis: Long,
    val events: List<MatchEventSnapshot> = emptyList()
)

@Serializable
data class LeaderboardEntrySnapshot(
    val rank: Int,
    val displayName: String,
    val playerCode: String,
    val wins: Int,
    val totalMatches: Int,
    val highestScore: Int,
    val eloRating: Int = 1000
)

@Serializable
data class LeaderboardSnapshot(
    val topPlayers: List<LeaderboardEntrySnapshot> = emptyList(),
    val currentPlayer: LeaderboardEntrySnapshot? = null,
    val seasonName: String? = null,
    val seasonTopPlayers: List<LeaderboardEntrySnapshot> = emptyList(),
    val seasonCurrentPlayer: LeaderboardEntrySnapshot? = null
)

@Serializable
enum class FriendPresence { OFFLINE, ONLINE, IN_ROOM, PLAYING }

@Serializable
data class FriendSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val presence: FriendPresence = FriendPresence.OFFLINE
)

@Serializable
data class FriendRequestSnapshot(
    val requestId: String,
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null
)

@Serializable
data class BlockedPlayerSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null
)

@Serializable
data class RecentPlayerSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val lastPlayedAtEpochMillis: Long,
    val matchesPlayed: Int
)

@Serializable
data class FriendsSnapshot(
    val friends: List<FriendSnapshot> = emptyList(),
    val incomingRequests: List<FriendRequestSnapshot> = emptyList(),
    val outgoingRequests: List<FriendRequestSnapshot> = emptyList(),
    val blockedPlayers: List<BlockedPlayerSnapshot> = emptyList(),
    val recentPlayers: List<RecentPlayerSnapshot> = emptyList()
)

@Serializable
data class GameSnapshot(
    val roomId: String,
    val matchId: String = roomId,
    val roomName: String,
    val hostId: String,
    val gameMode: ProtocolGameMode,
    val phase: RoomPhase,
    val players: List<PlayerSnapshot>,
    val numbers: List<Int> = emptyList(),
    val selectedNumbers: List<Int> = emptyList(),
    val currentTarget: Int = 1,
    val sequence: Long = 0,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val rematchRequestedPlayerIds: List<String> = emptyList(),
    val rematchExpiresAtEpochMillis: Long? = null
)

@Serializable
enum class RematchEvent {
    REQUESTED,
    CANCELLED,
    DECLINED,
    EXPIRED
}

@Serializable
enum class NotificationKind {
    FRIEND_REQUEST,
    ROOM_INVITATION,
    MISSION,
    ACHIEVEMENT,
    COSMETIC
}

@Serializable
enum class NotificationDestination { FRIENDS, PROFILE }

@Serializable
data class NotificationSnapshot(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val isRead: Boolean = false,
    val destination: NotificationDestination
)

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("connect_guest")
    data class ConnectGuest(
        val displayName: String,
        val resumeToken: String? = null,
        val protocolVersion: Int = PROTOCOL_VERSION
    ) : ClientMessage()

    @Serializable
    @SerialName("connect_account")
    data class ConnectAccount(
        val accessToken: String,
        val protocolVersion: Int = PROTOCOL_VERSION
    ) : ClientMessage()

    @Serializable
    @SerialName("list_rooms")
    data object ListRooms : ClientMessage()

    @Serializable
    @SerialName("get_profile")
    data object GetProfile : ClientMessage()

    @Serializable
    @SerialName("claim_daily_check_in")
    data object ClaimDailyCheckIn : ClientMessage()

    @Serializable
    @SerialName("get_friend_profile")
    data class GetFriendProfile(val friendUserId: String) : ClientMessage()

    @Serializable
    @SerialName("get_match_detail")
    data class GetMatchDetail(val matchId: String) : ClientMessage()

    @Serializable
    @SerialName("update_profile")
    data class UpdateProfile(
        val displayName: String,
        val avatarId: String?
    ) : ClientMessage()

    @Serializable
    @SerialName("equip_cosmetics")
    data class EquipCosmetics(val frameId: String, val titleId: String) : ClientMessage()

    @Serializable
    @SerialName("get_leaderboard")
    data object GetLeaderboard : ClientMessage()

    @Serializable
    @SerialName("get_friends")
    data object GetFriends : ClientMessage()

    @Serializable
    @SerialName("send_friend_request")
    data class SendFriendRequest(val playerCode: String) : ClientMessage()

    @Serializable
    @SerialName("respond_friend_request")
    data class RespondFriendRequest(val requestId: String, val accept: Boolean) : ClientMessage()

    @Serializable
    @SerialName("cancel_friend_request")
    data class CancelFriendRequest(val requestId: String) : ClientMessage()

    @Serializable
    @SerialName("remove_friend")
    data class RemoveFriend(val friendUserId: String) : ClientMessage()

    @Serializable
    @SerialName("block_player")
    data class BlockPlayer(val playerUserId: String) : ClientMessage()

    @Serializable
    @SerialName("unblock_player")
    data class UnblockPlayer(val playerUserId: String) : ClientMessage()

    @Serializable
    @SerialName("invite_friend")
    data class InviteFriend(val friendUserId: String, val roomId: String) : ClientMessage()

    @Serializable
    @SerialName("respond_room_invitation")
    data class RespondRoomInvitation(val invitationId: String, val accept: Boolean) : ClientMessage()

    @Serializable
    @SerialName("get_room_invitations")
    data object GetRoomInvitations : ClientMessage()

    @Serializable
    @SerialName("get_notifications")
    data object GetNotifications : ClientMessage()

    @Serializable
    @SerialName("sync_notifications")
    data class SyncNotifications(val notifications: List<NotificationSnapshot>) : ClientMessage()

    @Serializable
    @SerialName("mark_notifications_read")
    data class MarkNotificationsRead(val notificationId: String? = null) : ClientMessage()

    @Serializable
    @SerialName("dismiss_notifications")
    data class DismissNotifications(val notificationId: String? = null) : ClientMessage()

    @Serializable
    @SerialName("create_room")
    data class CreateRoom(
        val roomName: String,
        val password: String,
        val gameMode: ProtocolGameMode
    ) : ClientMessage()

    @Serializable
    @SerialName("join_room")
    data class JoinRoom(val roomId: String, val password: String) : ClientMessage()

    @Serializable
    @SerialName("leave_room")
    data class LeaveRoom(val roomId: String) : ClientMessage()

    @Serializable
    @SerialName("set_ready")
    data class SetReady(val roomId: String, val ready: Boolean) : ClientMessage()

    @Serializable
    @SerialName("kick_player")
    data class KickPlayer(val roomId: String, val playerId: String) : ClientMessage()

    @Serializable
    @SerialName("measure_latency")
    data class MeasureLatency(val clientSentAtEpochMillis: Long) : ClientMessage()

    @Serializable
    @SerialName("join_matchmaking")
    data class JoinMatchmaking(val gameMode: ProtocolGameMode) : ClientMessage()

    @Serializable
    @SerialName("cancel_matchmaking")
    data object CancelMatchmaking : ClientMessage()

    @Serializable
    @SerialName("request_rematch")
    data class RequestRematch(val roomId: String) : ClientMessage()

    @Serializable
    @SerialName("respond_rematch")
    data class RespondRematch(val roomId: String, val accept: Boolean) : ClientMessage()

    @Serializable
    @SerialName("select_number")
    data class SelectNumber(
        val roomId: String,
        val number: Int,
        val requestId: String
    ) : ClientMessage()
}

@Serializable
sealed class ServerMessage {
    @Serializable
    @SerialName("session_ready")
    data class SessionReady(
        val playerId: String,
        val resumeToken: String? = null,
        val currentGame: GameSnapshot? = null,
        val protocolVersion: Int = PROTOCOL_VERSION
    ) : ServerMessage()

    @Serializable
    @SerialName("room_list")
    data class RoomList(val rooms: List<RoomSummary>) : ServerMessage()

    @Serializable
    @SerialName("profile_data")
    data class ProfileData(val profile: PlayerProfileSnapshot) : ServerMessage()

    @Serializable
    @SerialName("daily_check_in_result")
    data class DailyCheckInResult(
        val claimed: Boolean,
        val rewardXp: Int
    ) : ServerMessage()

    @Serializable
    @SerialName("friend_profile_data")
    data class FriendProfileData(
        val friendUserId: String,
        val profile: PlayerProfileSnapshot
    ) : ServerMessage()

    @Serializable
    @SerialName("match_detail_data")
    data class MatchDetailData(val detail: MatchDetailSnapshot) : ServerMessage()

    @Serializable
    @SerialName("leaderboard_data")
    data class LeaderboardData(val leaderboard: LeaderboardSnapshot) : ServerMessage()

    @Serializable
    @SerialName("friends_data")
    data class FriendsData(val social: FriendsSnapshot) : ServerMessage()

    @Serializable
    @SerialName("room_invitation")
    data class RoomInvitation(
        val invitationId: String,
        val fromUserId: String,
        val fromDisplayName: String,
        val roomId: String,
        val roomName: String,
        val expiresAtEpochMillis: Long
    ) : ServerMessage()

    @Serializable
    @SerialName("room_invitations_data")
    data class RoomInvitationsData(val invitations: List<RoomInvitation>) : ServerMessage()

    @Serializable
    @SerialName("notifications_data")
    data class NotificationsData(val notifications: List<NotificationSnapshot>) : ServerMessage()

    @Serializable
    @SerialName("social_notice")
    data class SocialNotice(val message: String) : ServerMessage()

    @Serializable
    @SerialName("room_created")
    data class RoomCreated(val game: GameSnapshot) : ServerMessage()

    @Serializable
    @SerialName("room_updated")
    data class RoomUpdated(val game: GameSnapshot) : ServerMessage()

    @Serializable
    @SerialName("game_started")
    data class GameStarted(val game: GameSnapshot) : ServerMessage()

    @Serializable
    @SerialName("game_state_updated")
    data class GameStateUpdated(
        val game: GameSnapshot,
        val acceptedNumber: Int,
        val selectedByPlayerId: String
    ) : ServerMessage()

    @Serializable
    @SerialName("game_finished")
    data class GameFinished(val game: GameSnapshot) : ServerMessage()

    @Serializable
    @SerialName("rematch_status")
    data class RematchStatus(
        val game: GameSnapshot,
        val event: RematchEvent = RematchEvent.REQUESTED,
        val actorPlayerId: String? = null
    ) : ServerMessage()

    @Serializable
    @SerialName("latency_pong")
    data class LatencyPong(val clientSentAtEpochMillis: Long) : ServerMessage()

    @Serializable
    @SerialName("matchmaking_status")
    data class MatchmakingStatus(
        val isSearching: Boolean,
        val gameMode: ProtocolGameMode? = null,
        val ratingRange: Int = 100
    ) : ServerMessage()

    @Serializable
    @SerialName("room_closed")
    data class RoomClosed(val roomId: String, val reason: String) : ServerMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String,
        val requestId: String? = null
    ) : ServerMessage()
}
