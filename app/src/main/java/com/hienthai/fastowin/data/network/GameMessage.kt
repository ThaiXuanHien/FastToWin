package com.hienthai.fastowin.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GameMessage {
    @Serializable
    @SerialName("room_list_request")
    data class RoomListRequest(val requesterId: String) : GameMessage()

    @Serializable
    @SerialName("room_advertise")
    data class RoomAdvertise(
        val roomId: String,
        val roomName: String,
        val hostId: String,
        val hostName: String,
        val gameMode: String,
        val requiresPassword: Boolean
    ) : GameMessage()

    @Serializable
    @SerialName("room_join")
    data class RoomJoin(
        val roomId: String,
        val playerId: String,
        val playerName: String,
        val passwordHash: String
    ) : GameMessage()

    @Serializable
    @SerialName("room_joined")
    data class RoomJoined(
        val roomId: String,
        val hostId: String,
        val hostName: String,
        val guestId: String,
        val guestName: String
    ) : GameMessage()

    @Serializable
    @SerialName("room_join_rejected")
    data class RoomJoinRejected(
        val roomId: String,
        val playerId: String,
        val reason: String
    ) : GameMessage()

    @Serializable
    @SerialName("room_closed")
    data class RoomClosed(val roomId: String, val hostId: String) : GameMessage()

    // Kept so messages from older app builds can still be decoded and ignored.
    @Serializable
    @SerialName("join")
    data class Join(val playerName: String) : GameMessage()

    @Serializable
    @SerialName("ready")
    data class Ready(val isReady: Boolean) : GameMessage()

    @Serializable
    @SerialName("start_game")
    data class StartGame(val roomId: String, val grid: List<Int>) : GameMessage()

    @Serializable
    @SerialName("move")
    data class Move(
        val roomId: String,
        val playerId: String,
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
