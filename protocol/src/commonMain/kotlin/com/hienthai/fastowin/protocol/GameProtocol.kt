package com.hienthai.fastowin.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 1
const val GAME_NUMBER_COUNT = 50

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
    val score: Int
)

@Serializable
data class GameSnapshot(
    val roomId: String,
    val roomName: String,
    val hostId: String,
    val gameMode: ProtocolGameMode,
    val phase: RoomPhase,
    val players: List<PlayerSnapshot>,
    val numbers: List<Int> = emptyList(),
    val selectedNumbers: List<Int> = emptyList(),
    val currentTarget: Int = 1,
    val sequence: Long = 0,
    val startedAtEpochMillis: Long? = null
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
    @SerialName("list_rooms")
    data object ListRooms : ClientMessage()

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
        val resumeToken: String,
        val currentGame: GameSnapshot? = null,
        val protocolVersion: Int = PROTOCOL_VERSION
    ) : ServerMessage()

    @Serializable
    @SerialName("room_list")
    data class RoomList(val rooms: List<RoomSummary>) : ServerMessage()

    @Serializable
    @SerialName("room_created")
    data class RoomCreated(val game: GameSnapshot) : ServerMessage()

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
