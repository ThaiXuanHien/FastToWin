package com.hienthai.fastowin.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GameMessage {
    @Serializable
    @SerialName("com.hienthai.fastowin.data.network.GameMessage.Join")
    data class Join(val playerName: String) : GameMessage()

    @Serializable
    @SerialName("com.hienthai.fastowin.data.network.GameMessage.Ready")
    data class Ready(val isReady: Boolean) : GameMessage()

    @Serializable
    @SerialName("com.hienthai.fastowin.data.network.GameMessage.StartGame")
    data class StartGame(val grid: List<Int>) : GameMessage()

    @Serializable
    @SerialName("com.hienthai.fastowin.data.network.GameMessage.Move")
    data class Move(val number: Int, val score: Int, val currentTarget: Int) : GameMessage()

    @Serializable
    @SerialName("com.hienthai.fastowin.data.network.GameMessage.SyncState")
    data class SyncState(
        val opponentName: String,
        val opponentScore: Int,
        val opponentTarget: Int,
        val isOpponentReady: Boolean
    ) : GameMessage()
}
