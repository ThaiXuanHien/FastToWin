package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode

enum class LobbyStage {
    SELECT_MODE,
    ENTER_NAME,
    ROOM_BROWSER,
    ROOM_WAITING,
    MATCHED
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
    val player: PlayerState = PlayerState("You"),
    val opponent: PlayerState = PlayerState("Opponent"),
    val isSearching: Boolean = false,
    val availableRooms: List<AvailableRoom> = emptyList(),
    val currentRoomId: String? = null,
    val currentRoomName: String? = null,
    val isRoomHost: Boolean = false,
    val countdown: Int? = null,
    val isMatchStarted: Boolean = false
)
