package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import com.hienthai.fastowin.protocol.FriendsSnapshot
import com.hienthai.fastowin.protocol.ServerMessage

const val GAME_NUMBER_COUNT = 50
const val DEFAULT_OPPONENT_NAME = "Đối thủ"

enum class LobbyStage {
    SELECT_MODE,
    ENTER_NAME,
    ROOM_BROWSER,
    ROOM_WAITING,
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
    val requiresPassword: Boolean,
    val lastSeenAtMillis: Long
)

data class PlayerState(
    val name: String,
    val isReady: Boolean = false,
    val score: Int = 0,
    val currentTarget: Int = 1
)

data class GameState(
    val numbers: List<Int> = emptyList(),
    val currentTarget: Int = 1,
    val score: Int = 0,
    val timeLeftMillis: Long = 0,
    val isGameOver: Boolean = false,
    val gameMode: GameMode = GameMode.ORDER,
    val message: String? = null,
    val error: String? = null,
    
    // Lobby flow
    val lobbyStage: LobbyStage = LobbyStage.SELECT_MODE,
    
    // Multiplayer simulation
    val player: PlayerState = PlayerState("Bạn"),
    val opponent: PlayerState = PlayerState(DEFAULT_OPPONENT_NAME),
    val isSearching: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val availableRooms: List<AvailableRoom> = emptyList(),
    val currentRoomId: String? = null,
    val currentRoomName: String? = null,
    val isRoomHost: Boolean = false,
    val countdown: Int? = null,
    val isMatchStarted: Boolean = false,
    val isProfileOpen: Boolean = false,
    val isProfileLoading: Boolean = false,
    val isProfileSaving: Boolean = false,
    val profileNotice: String? = null,
    val profile: PlayerProfileSnapshot? = null,
    val isLeaderboardOpen: Boolean = false,
    val isLeaderboardLoading: Boolean = false,
    val leaderboard: LeaderboardSnapshot? = null,
    val isFriendsOpen: Boolean = false,
    val isFriendsLoading: Boolean = false,
    val social: FriendsSnapshot = FriendsSnapshot(),
    val roomInvitation: ServerMessage.RoomInvitation? = null,
    val socialNotice: String? = null
)
