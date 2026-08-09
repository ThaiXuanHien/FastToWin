package com.hienthai.fastowin.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

enum class GameMode {
    ORDER, TIME_ATTACK
}

sealed interface FastToWinNavKey : NavKey {
    @Serializable
    data object Lobby : FastToWinNavKey

    @Serializable
    data class Game(val mode: GameMode) : FastToWinNavKey

    @Serializable
    data class Result(val score: Int, val mode: GameMode) : FastToWinNavKey
}
