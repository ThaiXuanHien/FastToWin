package com.hienthai.fastowin.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 30
const val GAME_NUMBER_COUNT = 50
const val MAX_PROFILE_DISPLAY_NAME_LENGTH = 32
val DAILY_CHECK_IN_REWARDS_XP = listOf(10, 10, 15, 15, 20, 25, 40)
val DAILY_CHECK_IN_REWARDS_GOLD = listOf(50, 60, 70, 80, 100, 120, 200)
val DAILY_CHECK_IN_REWARDS_GEMS = listOf(0, 0, 0, 0, 0, 0, 1)
const val MATCH_WIN_REWARD_GOLD = 100
const val MATCH_DRAW_REWARD_GOLD = 70
const val MATCH_LOSS_REWARD_GOLD = 40
const val MATCH_WIN_REWARD_XP = 30
const val MATCH_DRAW_REWARD_XP = 20
const val MATCH_LOSS_REWARD_XP = 10
const val DAILY_CHECK_IN_STREAK_ACHIEVEMENT_TARGET = 7
const val DAILY_CHECK_IN_TITLE_TARGET = 30
const val DAILY_CHECK_IN_AVATAR_TARGET = 50
const val DAILY_CHECK_IN_FRAME_TARGET = 100
const val DAILY_CHECK_IN_AVATAR_ID = "calendar"

val STANDARD_PROFILE_AVATAR_IDS = listOf("bolt", "rocket", "target", "trophy", "crown", "star")
val PROFILE_AVATAR_IDS = (STANDARD_PROFILE_AVATAR_IDS + DAILY_CHECK_IN_AVATAR_ID).toSet()
val CLAN_AVATAR_IDS = listOf("shield", "sword", "flag", "dragon", "wolf", "eagle", "crown")

val ProtocolJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
enum class ProtocolGameMode(val unlockLevel: Int) {
    ORDER(1),
    RANDOM_TARGET(3),
    TIME_BONUS(5),
    SPEED_UP(7),
    SURVIVAL(10),
    COMBO(12),
    TEAM_2V2(5),

    /** Kept so existing match history and active-room snapshots remain readable. */
    TIME_ATTACK(1)
}

@Serializable
enum class MatchType { CASUAL, RANKED }

@Serializable
enum class RankedTier(val displayName: String, val minimumRating: Int) {
    BRONZE("Đồng", 0),
    SILVER("Bạc", 1_100),
    GOLD("Vàng", 1_300),
    PLATINUM("Bạch Kim", 1_500),
    DIAMOND("Kim Cương", 1_800),
    MASTER("Cao Thủ", 2_100),
    CHALLENGER("Thách Đấu", 2_400)
}

fun rankedTierFor(rating: Int): RankedTier = RankedTier.entries
    .lastOrNull { rating >= it.minimumRating }
    ?: RankedTier.BRONZE

@Serializable
enum class RoomPhase {
    WAITING,
    PLAYING,
    FINISHED
}

@Serializable
enum class TournamentPhase { LOBBY, RUNNING, FINISHED, CANCELLED }

@Serializable
enum class TournamentMatchPhase { PENDING, PLAYING, FINISHED }

@Serializable
data class TournamentPlayerSnapshot(
    val playerId: String,
    val displayName: String,
    val isHost: Boolean = false,
    val isOnline: Boolean = false
)

@Serializable
data class TournamentMatchSnapshot(
    val matchId: String,
    val round: Int,
    val position: Int,
    val playerOneId: String? = null,
    val playerTwoId: String? = null,
    val winnerPlayerId: String? = null,
    val roomId: String? = null,
    val phase: TournamentMatchPhase = TournamentMatchPhase.PENDING
)

@Serializable
data class TournamentSnapshot(
    val tournamentId: String,
    val name: String,
    val hostPlayerId: String,
    val gameMode: ProtocolGameMode,
    val phase: TournamentPhase,
    val maxPlayers: Int = 4,
    val entryFee: Int = 0,
    val prizePool: Int = 0,
    val players: List<TournamentPlayerSnapshot> = emptyList(),
    val matches: List<TournamentMatchSnapshot> = emptyList(),
    val championPlayerId: String? = null,
    val createdAtEpochMillis: Long,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null
)

@Serializable
data class TournamentInvitationSnapshot(
    val invitationId: String,
    val tournamentId: String,
    val tournamentName: String,
    val hostPlayerId: String,
    val hostDisplayName: String,
    val gameMode: ProtocolGameMode,
    val expiresAtEpochMillis: Long
)

@Serializable
data class TournamentHubSnapshot(
    val activeTournament: TournamentSnapshot? = null,
    val invitations: List<TournamentInvitationSnapshot> = emptyList(),
    val recentTournaments: List<TournamentSnapshot> = emptyList()
)

@Serializable
data class RoomSummary(
    val id: String,
    val name: String,
    val hostName: String,
    val gameMode: ProtocolGameMode,
    val requiresPassword: Boolean,
    val matchType: MatchType = MatchType.CASUAL
)

@Serializable
data class PlayerSnapshot(
    val id: String,
    val name: String,
    val score: Int,
    val isReady: Boolean = false,
    val correctSelections: Int = 0,
    val wrongSelections: Int = 0,
    val averageReactionMillis: Long = 0,
    val currentTarget: Int = 1,
    val selectedNumbers: List<Int> = emptyList(),
    val combo: Int = 0,
    val lives: Int = 3,
    val timeLeftMillis: Long = 0,
    val isFinished: Boolean = false,
    val fastestSegmentStart: Int = 0,
    val fastestSegmentEnd: Int = 0,
    val fastestSegmentAverageMillis: Long = 0,
    val slowestSegmentStart: Int = 0,
    val slowestSegmentEnd: Int = 0,
    val slowestSegmentAverageMillis: Long = 0,
    val teamId: String? = null,
    val avatarId: String? = null,
    val frameId: String = "frame_default"
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
data class GameModeStatisticsSnapshot(
    val gameMode: ProtocolGameMode,
    val totalMatches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val highestScore: Int = 0,
    val averageScore: Int = 0
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
    val eloChange: Int = 0,
    val matchType: MatchType = MatchType.CASUAL
)

@Serializable
data class PlayerProfileSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val statistics: PlayerStatisticsSnapshot = PlayerStatisticsSnapshot(),
    val recentMatches: List<MatchHistorySnapshot> = emptyList(),
    val achievements: List<AchievementSnapshot> = emptyList(),
    val progression: PlayerProgressionSnapshot = PlayerProgressionSnapshot(),
    val modeStatistics: List<GameModeStatisticsSnapshot> = emptyList(),
    val clanId: String? = null,
    val clanName: String? = null
)

@Serializable
enum class CosmeticType { FRAME, TITLE, AVATAR, CARD_BACK, BOARD_SKIN, EMOJI }

@Serializable
enum class MissionDifficulty { EASY, NORMAL, HARD, ELITE }

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
    val completed: Boolean,
    val rewardXp: Int = 0,
    val rewardGold: Int = 0,
    val rewardGems: Int = 0,
    val rewardClaimed: Boolean = false,
    val difficulty: MissionDifficulty = MissionDifficulty.EASY
)

@Serializable
data class DailyCheckInSnapshot(
    val claimedToday: Boolean = false,
    val cycleDay: Int = 1,
    val todayRewardXp: Int = 10,
    val todayRewardGold: Int = 50,
    val todayRewardGems: Int = 0,
    val nextRewardXp: Int = 10,
    val nextRewardGold: Int = 60,
    val nextRewardGems: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCheckIns: Int = 0,
    val lastCheckInDate: String? = null,
    val todayDate: String? = null,
    val historyDates: List<String> = emptyList()
)

@Serializable
data class SeasonSnapshot(
    val name: String,
    val tier: String,
    val rating: Int,
    val endsAtEpochMillis: Long,
    val rewardDescription: String,
    val placementMatchesPlayed: Int = 0,
    val placementMatchesRequired: Int = 5,
    val peakRating: Int = rating
)

@Serializable
data class PlayerProgressionSnapshot(
    val level: Int = 1,
    val experiencePoints: Int = 0,
    val gold: Int = 0,
    val gems: Int = 0,
    val currentLevelExperience: Int = 0,
    val nextLevelExperience: Int = 100,
    val dailyMissions: List<MissionSnapshot> = emptyList(),
    val weeklyMissions: List<MissionSnapshot> = emptyList(),
    val dailyCheckIn: DailyCheckInSnapshot = DailyCheckInSnapshot(),
    val cosmetics: List<CosmeticSnapshot> = emptyList(),
    val season: SeasonSnapshot? = null
)

@Serializable
data class WalletTransactionSnapshot(
    val id: String,
    val sourceType: String,
    val sourceId: String,
    val goldDelta: Int = 0,
    val gemsDelta: Int = 0,
    val xpDelta: Int = 0,
    val createdAtEpochMillis: Long
)

@Serializable
enum class StorePlatform { GOOGLE_PLAY, APP_STORE }

@Serializable
data class GemPackageSnapshot(
    val productId: String,
    val title: String,
    val gems: Int,
    val featured: Boolean = false
)

@Serializable
enum class StorePurchaseStatus { GRANTED, ALREADY_GRANTED, INVALID, UNAVAILABLE, FAILED }

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
    val eloRating: Int = 1000,
    val userId: String? = null,
    val avatarId: String? = null,
    val frameId: String = "frame_default"
)

@Serializable
data class ClanLeaderboardEntrySnapshot(
    val rank: Int,
    val clanId: String,
    val clanName: String,
    val totalElo: Int,
    val memberCount: Int
)

@Serializable
data class LeaderboardSnapshot(
    val topPlayers: List<LeaderboardEntrySnapshot> = emptyList(),
    val currentPlayer: LeaderboardEntrySnapshot? = null,
    val seasonName: String? = null,
    val seasonTopPlayers: List<LeaderboardEntrySnapshot> = emptyList(),
    val seasonCurrentPlayer: LeaderboardEntrySnapshot? = null,
    val topClans: List<ClanLeaderboardEntrySnapshot> = emptyList(),
    val currentClan: ClanLeaderboardEntrySnapshot? = null
)

@Serializable
enum class FriendPresence { OFFLINE, ONLINE, IN_ROOM, PLAYING }

@Serializable
data class FriendSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val presence: FriendPresence = FriendPresence.OFFLINE,
    val frameId: String = "frame_default"
)

@Serializable
data class FriendRequestSnapshot(
    val requestId: String,
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val frameId: String = "frame_default"
)

@Serializable
data class BlockedPlayerSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val frameId: String = "frame_default"
)

@Serializable
data class RecentPlayerSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val avatarId: String? = null,
    val lastPlayedAtEpochMillis: Long,
    val matchesPlayed: Int,
    val frameId: String = "frame_default"
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
    val spectators: List<PlayerSnapshot> = emptyList(),
    val matchType: MatchType = MatchType.CASUAL,
    val numbers: List<Int> = emptyList(),
    val selectedNumbers: List<Int> = emptyList(),
    val currentTarget: Int = 1,
    val sequence: Long = 0,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val winnerPlayerId: String? = null,
    val winnerTeamId: String? = null,
    val rematchRequestedPlayerIds: List<String> = emptyList(),
    val rematchExpiresAtEpochMillis: Long? = null,
    val tournamentId: String? = null,
    val tournamentMatchId: String? = null,
    val tournamentRound: Int? = null
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
    COSMETIC,
    CLAN_INVITATION
}

@Serializable
enum class NotificationDestination { FRIENDS, PROFILE, CLAN }

@Serializable
data class NotificationSnapshot(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val isRead: Boolean = false,
    val destination: NotificationDestination,
    val actionData: String? = null
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
    @SerialName("get_wallet_history")
    data object GetWalletHistory : ClientMessage()

    @Serializable
    @SerialName("get_gem_store_catalog")
    data object GetGemStoreCatalog : ClientMessage()

    @Serializable
    @SerialName("verify_store_purchase")
    data class VerifyStorePurchase(
        val requestId: String,
        val store: StorePlatform,
        val productId: String,
        val purchaseToken: String
    ) : ClientMessage()

    @Serializable
    @SerialName("claim_daily_check_in")
    data object ClaimDailyCheckIn : ClientMessage()

    @Serializable
    @SerialName("claim_mission_reward")
    data class ClaimMissionReward(val missionCode: String) : ClientMessage()

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
    @SerialName("get_tournament_hub")
    data object GetTournamentHub : ClientMessage()

    @Serializable
    @SerialName("create_tournament")
    data class CreateTournament(
        val name: String,
        val gameMode: ProtocolGameMode,
        val entryFee: Int = 0
    ) : ClientMessage()

    @Serializable
    @SerialName("invite_tournament_player")
    data class InviteTournamentPlayer(
        val tournamentId: String,
        val friendPlayerId: String
    ) : ClientMessage()

    @Serializable
    @SerialName("respond_tournament_invitation")
    data class RespondTournamentInvitation(
        val invitationId: String,
        val accept: Boolean
    ) : ClientMessage()

    @Serializable
    @SerialName("start_tournament")
    data class StartTournament(val tournamentId: String) : ClientMessage()

    @Serializable
    @SerialName("leave_tournament")
    data class LeaveTournament(val tournamentId: String) : ClientMessage()

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
    data class JoinRoom(val roomId: String, val password: String, val asSpectator: Boolean = false) : ClientMessage()

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
    @SerialName("create_clan")
    data class CreateClan(val name: String, val description: String) : ClientMessage()

    @Serializable
    @SerialName("join_clan")
    data class JoinClan(val clanId: String) : ClientMessage()

    @Serializable
    @SerialName("respond_clan_join_request")
    data class RespondClanJoinRequest(
        val clanId: String,
        val userId: String,
        val accept: Boolean
    ) : ClientMessage()

    @Serializable
    @SerialName("leave_clan")
    data object LeaveClan : ClientMessage()

    @Serializable
    @SerialName("get_clan_info")
    data class GetClanInfo(val clanId: String) : ClientMessage()

    @Serializable
    @SerialName("get_clan_list")
    data class GetClanList(val query: String? = null) : ClientMessage()

    @Serializable
    @SerialName("invite_to_clan")
    data class InviteToClan(val playerCode: String) : ClientMessage()

    @Serializable
    @SerialName("kick_clan_member")
    data class KickClanMember(val clanId: String, val memberId: String) : ClientMessage()

    @Serializable
    @SerialName("claim_clan_quest_reward")
    data class ClaimClanQuestReward(val clanId: String) : ClientMessage()

    @Serializable
    @SerialName("measure_latency")
    data class MeasureLatency(val clientSentAtEpochMillis: Long) : ClientMessage()

    @Serializable
    @SerialName("update_latency")
    data class UpdateLatency(val latencyMillis: Long) : ClientMessage()

    @Serializable
    @SerialName("update_fcm_token")
    data class UpdateFcmToken(val token: String) : ClientMessage()

    @Serializable
    @SerialName("join_matchmaking")
    data class JoinMatchmaking(
        val gameMode: ProtocolGameMode,
        val matchType: MatchType = MatchType.RANKED
    ) : ClientMessage()

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

    @Serializable
    @SerialName("send_emoji")
    data class SendEmoji(val roomId: String, val emojiId: String) : ClientMessage()

    @Serializable
    @SerialName("update_avatar")
    data class UpdateAvatar(val base64Data: String) : ClientMessage()

    @Serializable
    @SerialName("update_clan_logo")
    data class UpdateClanLogo(val clanId: String, val logoId: String) : ClientMessage()

    @Serializable
    @SerialName("buy_cosmetic")
    data class BuyCosmetic(val cosmeticId: String) : ClientMessage()

    @Serializable
    @SerialName("equip_cosmetic")
    data class EquipCosmetic(val cosmeticId: String) : ClientMessage()
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
    @SerialName("wallet_history")
    data class WalletHistory(val transactions: List<WalletTransactionSnapshot>) : ServerMessage()

    @Serializable
    @SerialName("gem_store_catalog")
    data class GemStoreCatalog(
        val packages: List<GemPackageSnapshot>,
        val sandboxEnabled: Boolean = false
    ) : ServerMessage()

    @Serializable
    @SerialName("store_purchase_result")
    data class StorePurchaseResult(
        val requestId: String,
        val productId: String,
        val status: StorePurchaseStatus,
        val gemsGranted: Int = 0,
        val message: String
    ) : ServerMessage()

    @Serializable
    @SerialName("daily_check_in_result")
    data class DailyCheckInResult(
        val claimed: Boolean,
        val rewardXp: Int,
        val rewardGold: Int = 0,
        val rewardGems: Int = 0
    ) : ServerMessage()

    @Serializable
    @SerialName("mission_reward_result")
    data class MissionRewardResult(
        val missionCode: String,
        val claimed: Boolean,
        val rewardXp: Int,
        val rewardGold: Int = 0,
        val rewardGems: Int = 0
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
    data class RoomInvitationsData(
        val invitations: List<RoomInvitation>,
        val outgoingFriendUserIds: List<String> = emptyList()
    ) : ServerMessage()

    @Serializable
    @SerialName("notifications_data")
    data class NotificationsData(val notifications: List<NotificationSnapshot>) : ServerMessage()

    @Serializable
    @SerialName("tournament_hub_data")
    data class TournamentHubData(val hub: TournamentHubSnapshot) : ServerMessage()

    @Serializable
    @SerialName("tournament_updated")
    data class TournamentUpdated(val tournament: TournamentSnapshot) : ServerMessage()

    @Serializable
    @SerialName("tournament_invitation")
    data class TournamentInvitation(val invitation: TournamentInvitationSnapshot) : ServerMessage()

    @Serializable
    @SerialName("tournament_notice")
    data class TournamentNotice(val message: String) : ServerMessage()

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
        val selectedByPlayerId: String,
        val selectionAccepted: Boolean = true
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
        val matchType: MatchType = MatchType.RANKED,
        val ratingRange: Int = 100
    ) : ServerMessage()

    @Serializable
    @SerialName("room_closed")
    data class RoomClosed(val roomId: String, val reason: String) : ServerMessage()

    @Serializable
    @SerialName("emoji_broadcast")
    data class EmojiBroadcast(val senderPlayerId: String, val emojiId: String) : ServerMessage()

    @Serializable
    @SerialName("clan_info_data")
    data class ClanInfoData(val clan: ClanSnapshot) : ServerMessage()

    @Serializable
    @SerialName("clan_list_data")
    data class ClanListData(
        val clans: List<ClanSummarySnapshot>,
        val pendingJoinClanIds: List<String> = emptyList()
    ) : ServerMessage()

    @Serializable
    @SerialName("clan_action_result")
    data class ClanActionResult(val success: Boolean, val message: String, val action: String) : ServerMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String,
        val requestId: String? = null
    ) : ServerMessage()
}
@Serializable
enum class ClanRole { LEADER, CO_LEADER, MEMBER }

@Serializable
data class ClanMemberSnapshot(
    val userId: String,
    val displayName: String,
    val role: ClanRole,
    val trophies: Int,
    val questContribution: Int = 0,
    val questRewardClaimed: Boolean = false
)

@Serializable
data class ClanQuestSnapshot(
    val progress: Int,
    val target: Int,
    val rewardGold: Int,
    val rewardXp: Int = 0,
    val rewardGems: Int = 0
)

@Serializable
data class ClanJoinRequestSnapshot(
    val userId: String,
    val displayName: String,
    val playerCode: String,
    val requestedAtEpochMillis: Long
)

@Serializable
data class ClanSnapshot(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val members: List<ClanMemberSnapshot>,
    val trophies: Int,
    val logoId: String? = null,
    val maxMembers: Int = 50,
    val quest: ClanQuestSnapshot? = null,
    val joinRequests: List<ClanJoinRequestSnapshot> = emptyList()
)

@Serializable
data class ClanSummarySnapshot(
    val id: String,
    val name: String,
    val memberCount: Int,
    val maxMembers: Int,
    val trophies: Int,
    val logoId: String? = null
)

@Serializable
data class ShopItem(
    val id: String,
    val name: String,
    val type: CosmeticType,
    val price: Int,
    val currency: String = "GOLD"
)

val SHOP_ITEMS = listOf(
    ShopItem("card_back_gold", "Mặt bài Hoàng Kim", CosmeticType.CARD_BACK, 500, "GOLD"),
    ShopItem("card_back_diamond", "Mặt bài Kim Cương", CosmeticType.CARD_BACK, 1500, "GOLD"),
    ShopItem("board_skin_dark", "Bàn số Bóng Đêm", CosmeticType.BOARD_SKIN, 1000, "GOLD"),
    ShopItem("board_skin_forest", "Bàn số Rừng Xanh", CosmeticType.BOARD_SKIN, 1000, "GOLD")
)
