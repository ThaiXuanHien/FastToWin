package com.hienthai.fastowin.state

import com.hienthai.fastowin.navigation.GameMode

enum class LobbyStage {
    SELECT_MODE,
    ENTER_NAME,
    SEARCHING
}

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
    val countdown: Int? = null,
    val isMatchStarted: Boolean = false
)
