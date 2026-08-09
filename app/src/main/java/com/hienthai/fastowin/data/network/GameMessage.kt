package com.hienthai.fastowin.data.network

import kotlinx.serialization.Serializable

@Serializable
sealed class GameMessage {
    @Serializable
    data class Join(val playerName: String) : GameMessage()

    @Serializable
    data class Ready(val isReady: Boolean) : GameMessage()

    @Serializable
    data class StartGame(val grid: List<Int>) : GameMessage()

    @Serializable
    data class Move(val number: Int, val score: Int, val currentTarget: Int) : GameMessage()

    @Serializable
    data class SyncState(
        val opponentName: String,
        val opponentScore: Int,
        val opponentTarget: Int,
        val isOpponentReady: Boolean
    ) : GameMessage()
}
