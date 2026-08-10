package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.RoomSummary
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Delivery(
    val message: ServerMessage,
    val recipients: Set<String>? = null
)

data class ConnectedGuest(
    val playerId: String,
    val resumeToken: String,
    val currentGame: GameSnapshot?
)

class GameEngine {
    private val mutex = Mutex()
    private val sessionsByToken = mutableMapOf<String, GuestSession>()
    private val sessionsByPlayerId = mutableMapOf<String, GuestSession>()
    private val rooms = mutableMapOf<String, Room>()

    suspend fun connectGuest(displayName: String, resumeToken: String?): ConnectedGuest = mutex.withLock {
        val safeName = displayName.trim().take(MAX_PLAYER_NAME_LENGTH)
        require(safeName.isNotEmpty()) { "Tên người chơi không được để trống." }

        val session = resumeToken?.let(sessionsByToken::get)?.also { existing ->
            existing.displayName = safeName
        } ?: GuestSession(
            playerId = UUID.randomUUID().toString(),
            resumeToken = newToken(),
            displayName = safeName
        ).also { created ->
            sessionsByToken[created.resumeToken] = created
            sessionsByPlayerId[created.playerId] = created
        }

        ConnectedGuest(
            playerId = session.playerId,
            resumeToken = session.resumeToken,
            currentGame = roomFor(session.playerId)?.snapshot()
        )
    }

    suspend fun handle(playerId: String, message: ClientMessage): List<Delivery> = mutex.withLock {
        val player = sessionsByPlayerId[playerId]
            ?: return@withLock listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))

        when (message) {
            is ClientMessage.ConnectGuest -> listOf(
                error(playerId, "ALREADY_CONNECTED", "Phiên WebSocket đã được xác thực.")
            )

            ClientMessage.ListRooms -> listOf(Delivery(ServerMessage.RoomList(publicRooms()), setOf(playerId)))
            is ClientMessage.CreateRoom -> createRoom(player, message)
            is ClientMessage.JoinRoom -> joinRoom(player, message)
            is ClientMessage.LeaveRoom -> leaveRoom(player, message)
            is ClientMessage.SelectNumber -> selectNumber(player, message)
        }
    }

    suspend fun roomList(): ServerMessage.RoomList = mutex.withLock {
        ServerMessage.RoomList(publicRooms())
    }

    private fun createRoom(player: GuestSession, command: ClientMessage.CreateRoom): List<Delivery> {
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng khác."))
        }
        val name = command.roomName.trim().take(MAX_ROOM_NAME_LENGTH)
        if (name.isEmpty()) {
            return listOf(error(player.playerId, "INVALID_ROOM_NAME", "Tên phòng không được để trống."))
        }
        if (command.password.isEmpty()) {
            return listOf(error(player.playerId, "INVALID_PASSWORD", "Mật khẩu phòng không được để trống."))
        }

        val room = Room(
            id = UUID.randomUUID().toString(),
            name = name,
            hostId = player.playerId,
            password = PasswordHash.create(command.password),
            gameMode = command.gameMode
        )
        rooms[room.id] = room
        return listOf(
            Delivery(ServerMessage.RoomCreated(room.snapshot()), setOf(player.playerId)),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun joinRoom(player: GuestSession, command: ClientMessage.JoinRoom): List<Delivery> {
        if (roomFor(player.playerId) != null) {
            return listOf(error(player.playerId, "ALREADY_IN_ROOM", "Bạn đang ở trong một phòng khác."))
        }
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại."))
        if (room.phase != RoomPhase.WAITING || room.guestId != null) {
            return listOf(error(player.playerId, "ROOM_FULL", "Phòng đã đủ người."))
        }
        if (!room.password.matches(command.password)) {
            return listOf(error(player.playerId, "WRONG_PASSWORD", "Mật khẩu phòng không đúng."))
        }

        room.guestId = player.playerId
        room.phase = RoomPhase.PLAYING
        room.numbers = (1..GAME_NUMBER_COUNT).shuffled()
        room.startedAtEpochMillis = Instant.now().toEpochMilli()
        room.sequence++
        room.scores[player.playerId] = 0
        val participants = room.playerIds()
        return listOf(
            Delivery(ServerMessage.GameStarted(room.snapshot()), participants),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun leaveRoom(player: GuestSession, command: ClientMessage.LeaveRoom): List<Delivery> {
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại."))
        if (player.playerId !in room.playerIds()) {
            return listOf(error(player.playerId, "NOT_IN_ROOM", "Bạn không ở trong phòng này."))
        }

        val participants = room.playerIds()
        rooms.remove(room.id)
        return listOf(
            Delivery(ServerMessage.RoomClosed(room.id, "Một người chơi đã rời phòng."), participants),
            Delivery(ServerMessage.RoomList(publicRooms()))
        )
    }

    private fun selectNumber(player: GuestSession, command: ClientMessage.SelectNumber): List<Delivery> {
        val room = rooms[command.roomId]
            ?: return listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại.", command.requestId))
        if (player.playerId !in room.playerIds()) {
            return listOf(error(player.playerId, "NOT_IN_ROOM", "Bạn không ở trong phòng này.", command.requestId))
        }
        room.processedRequests[command.requestId]?.let { previous ->
            return listOf(Delivery(previous, setOf(player.playerId)))
        }
        room.refreshTimedState()
        if (room.phase != RoomPhase.PLAYING) {
            return listOf(error(player.playerId, "GAME_NOT_PLAYING", "Trận đấu chưa bắt đầu hoặc đã kết thúc.", command.requestId))
        }
        if (command.number != room.currentTarget) {
            return listOf(error(player.playerId, "WRONG_NUMBER", "Chưa đúng số, thử lại nhé!", command.requestId))
        }

        room.selectedNumbers += command.number
        room.scores[player.playerId] = room.scores.getValue(player.playerId) + SCORE_PER_NUMBER
        room.currentTarget++
        room.sequence++
        if (room.currentTarget > GAME_NUMBER_COUNT) room.phase = RoomPhase.FINISHED

        val event = ServerMessage.GameStateUpdated(
            game = room.snapshot(),
            acceptedNumber = command.number,
            selectedByPlayerId = player.playerId
        )
        room.processedRequests[command.requestId] = event
        return listOf(Delivery(event, room.playerIds()))
    }

    private fun roomFor(playerId: String): Room? = rooms.values.firstOrNull { playerId in it.playerIds() }

    private fun publicRooms(): List<RoomSummary> = rooms.values
        .asSequence()
        .filter { it.phase == RoomPhase.WAITING && it.guestId == null }
        .map { room ->
            RoomSummary(
                id = room.id,
                name = room.name,
                hostName = sessionsByPlayerId[room.hostId]?.displayName.orEmpty(),
                gameMode = room.gameMode,
                requiresPassword = true
            )
        }
        .sortedBy { it.name.lowercase() }
        .toList()

    private fun Room.snapshot(): GameSnapshot {
        refreshTimedState()
        return GameSnapshot(
            roomId = id,
            roomName = name,
            hostId = hostId,
            gameMode = gameMode,
            phase = phase,
            players = playerIds().mapNotNull { id ->
                sessionsByPlayerId[id]?.let { session ->
                    PlayerSnapshot(id = id, name = session.displayName, score = scores[id] ?: 0)
                }
            },
            numbers = numbers,
            selectedNumbers = selectedNumbers.toList(),
            currentTarget = currentTarget,
            sequence = sequence,
            startedAtEpochMillis = startedAtEpochMillis
        )
    }

    private fun Room.refreshTimedState() {
        val startedAt = startedAtEpochMillis ?: return
        if (
            gameMode == com.hienthai.fastowin.protocol.ProtocolGameMode.TIME_ATTACK &&
            phase == RoomPhase.PLAYING &&
            Instant.now().toEpochMilli() - startedAt >= TIME_ATTACK_MILLIS
        ) {
            phase = RoomPhase.FINISHED
            sequence++
        }
    }

    private fun error(
        playerId: String,
        code: String,
        message: String,
        requestId: String? = null
    ) = Delivery(ServerMessage.Error(code, message, requestId), setOf(playerId))

    private fun newToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private data class GuestSession(
        val playerId: String,
        val resumeToken: String,
        var displayName: String
    )

    private data class Room(
        val id: String,
        val name: String,
        val hostId: String,
        val password: PasswordHash,
        val gameMode: com.hienthai.fastowin.protocol.ProtocolGameMode,
        var guestId: String? = null,
        var phase: RoomPhase = RoomPhase.WAITING,
        var numbers: List<Int> = emptyList(),
        val selectedNumbers: MutableList<Int> = mutableListOf(),
        var currentTarget: Int = 1,
        val scores: MutableMap<String, Int> = mutableMapOf(hostId to 0),
        var sequence: Long = 0,
        var startedAtEpochMillis: Long? = null,
        val processedRequests: MutableMap<String, ServerMessage.GameStateUpdated> = mutableMapOf()
    ) {
        fun playerIds(): Set<String> = setOfNotNull(hostId, guestId)
    }

    private data class PasswordHash(val salt: ByteArray, val value: ByteArray) {
        fun matches(password: String): Boolean = MessageDigest.isEqual(value, derive(password, salt))

        companion object {
            fun create(password: String): PasswordHash {
                val salt = ByteArray(16).also(secureRandom::nextBytes)
                return PasswordHash(salt, derive(password, salt))
            }

            private fun derive(password: String, salt: ByteArray): ByteArray {
                val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            }
        }
    }

    private companion object {
        const val MAX_PLAYER_NAME_LENGTH = 32
        const val MAX_ROOM_NAME_LENGTH = 48
        const val SCORE_PER_NUMBER = 10
        const val TIME_ATTACK_MILLIS = 60_000L
        val secureRandom = SecureRandom()
    }
}
