package com.hienthai.fastowin.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GameMessage {
    @Serializable
    @SerialName("join")
    data class Join(val playerName: String) : GameMessage()

    @Serializable
    @SerialName("ready")
    data class Ready(val isReady: Boolean) : GameMessage()

    @Serializable
    @SerialName("start_game")
    data class StartGame(val grid: List<Int>) : GameMessage()

    @Serializable
    @SerialName("move")
    data class Move(
        val playerName: String,
        val number: Int,
        val score: Int,
        val currentTarget: Int
    ) : GameMessage()

    @Serializable
    @SerialName("sync_state")
    data class SyncState(
        val opponentName: String,
        val opponentScore: Int,
        val opponentTarget: Int,
        val isOpponentReady: Boolean
    ) : GameMessage()
}
