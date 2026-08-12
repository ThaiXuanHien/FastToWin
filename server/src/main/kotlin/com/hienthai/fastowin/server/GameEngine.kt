package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.PlayerSnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.RoomSummary
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
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

class GameEngine(
    private val identityRepository: GuestIdentityRepository = InMemoryGuestIdentityRepository(),
    private val matchResultRepository: MatchResultRepository = NoOpMatchResultRepository,
    private val playerProfileRepository: PlayerProfileRepository = NoOpPlayerProfileRepository,
    private val timeAttackMillis: Long = DEFAULT_TIME_ATTACK_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val sessionsByPlayerId = mutableMapOf<String, GuestSession>()
    private val rooms = mutableMapOf<String, Room>()

    suspend fun connectGuest(displayName: String, resumeToken: String?): ConnectedGuest {
        val safeName = displayName.trim().take(MAX_PLAYER_NAME_LENGTH)
        require(safeName.isNotEmpty()) { "Tên người chơi không được để trống." }
        val identity = identityRepository.resolveGuest(safeName, resumeToken, nowMillis())

        return mutex.withLock {
            val session = sessionsByPlayerId[identity.playerId]?.also { existing ->
                existing.displayName = identity.displayName
            } ?: GuestSession(
                playerId = identity.playerId,
                resumeToken = identity.resumeToken,
                displayName = identity.displayName
            ).also { created ->
                sessionsByPlayerId[created.playerId] = created
            }
            session.isConnected = true
            session.disconnectedAtMillis = null

            ConnectedGuest(
                playerId = session.playerId,
                resumeToken = session.resumeToken,
                currentGame = roomFor(session.playerId)?.snapshot()
            )
        }
    }

    suspend fun handle(playerId: String, message: ClientMessage): List<Delivery> {
        if (message is ClientMessage.GetProfile) return loadProfile(playerId)
        val result = mutex.withLock {
            val player = sessionsByPlayerId[playerId]
                ?: return@withLock HandleResult(
                    listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
                )

            when (message) {
                is ClientMessage.ConnectGuest -> HandleResult(listOf(
                    error(playerId, "ALREADY_CONNECTED", "Phiên WebSocket đã được xác thực.")
                ))

                ClientMessage.ListRooms -> HandleResult(
                    listOf(Delivery(ServerMessage.RoomList(publicRooms()), setOf(playerId)))
                )
                ClientMessage.GetProfile -> HandleResult(emptyList())
                is ClientMessage.CreateRoom -> HandleResult(createRoom(player, message))
                is ClientMessage.JoinRoom -> HandleResult(joinRoom(player, message))
                is ClientMessage.LeaveRoom -> HandleResult(leaveRoom(player, message))
                is ClientMessage.SelectNumber -> selectNumber(player, message)
            }
        }
        result.completedMatch?.let { completed ->
            runCatching { matchResultRepository.save(completed) }
                .onFailure { System.err.println("Could not persist match ${completed.matchId}: ${it.message}") }
        }
        return result.deliveries
    }

    private suspend fun loadProfile(playerId: String): List<Delivery> {
        val session = mutex.withLock { sessionsByPlayerId[playerId]?.copy() }
            ?: return listOf(error(playerId, "SESSION_NOT_FOUND", "Phiên chơi không còn hợp lệ."))
        val persisted = runCatching { playerProfileRepository.findByPlayerId(playerId) }
            .onFailure { System.err.println("Could not load profile $playerId: ${it.message}") }
            .getOrNull()
        val profile = persisted ?: PlayerProfileSnapshot(
            displayName = session.displayName,
            playerCode = playerId.replace("-", "").take(10).uppercase()
        )
        return listOf(Delivery(ServerMessage.ProfileData(profile), setOf(playerId)))
    }

    suspend fun roomList(): ServerMessage.RoomList = mutex.withLock {
        ServerMessage.RoomList(publicRooms())
    }

    suspend fun advanceTimedGames(): List<Delivery> {
        val advances = mutex.withLock {
            rooms.values.mapNotNull { room ->
                if (
                    room.gameMode != com.hienthai.fastowin.protocol.ProtocolGameMode.TIME_ATTACK ||
                    room.phase != RoomPhase.PLAYING ||
                    nowMillis() - (room.startedAtEpochMillis ?: return@mapNotNull null) < timeAttackMillis
                ) return@mapNotNull null

                room.phase = RoomPhase.FINISHED
                room.sequence++
                TimedAdvance(
                    delivery = Delivery(ServerMessage.GameFinished(room.snapshot()), room.playerIds()),
                    completedMatch = room.takeCompletedMatch()
                )
            }
        }
        advances.mapNotNull(TimedAdvance::completedMatch).forEach { completed ->
            runCatching { matchResultRepository.save(completed) }
                .onFailure { System.err.println("Could not persist timed match ${completed.matchId}: ${it.message}") }
        }
        return advances.map(TimedAdvance::delivery)
    }

    suspend fun markDisconnected(playerId: String): List<Delivery> {
        val disconnectedAt = nowMillis()
        val deliveries = mutex.withLock {
            val session = sessionsByPlayerId[playerId] ?: return@withLock emptyList()
            session.isConnected = false
            session.disconnectedAtMillis = disconnectedAt
            val room = roomFor(playerId)
            if (room?.phase == RoomPhase.WAITING && room.hostId == playerId) {
                listOf(Delivery(ServerMessage.RoomList(publicRooms())))
            } else {
                emptyList()
            }
        }
        runCatching { identityRepository.markDisconnected(playerId, disconnectedAt) }
            .onFailure { System.err.println("Could not persist disconnected session $playerId: ${it.message}") }
        return deliveries
    }

    suspend fun cleanupExpiredSessions(): List<Delivery> = mutex.withLock {
        val now = nowMillis()
        val expiredPlayerIds = sessionsByPlayerId.values
            .filter { session ->
                if (session.isConnected) return@filter false
                val disconnectedAt = session.disconnectedAtMillis ?: return@filter false
                val timeout = if (roomFor(session.playerId) == null) {
                    IDLE_SESSION_TTL_MILLIS
                } else {
                    ROOM_RECONNECT_GRACE_MILLIS
                }
                now - disconnectedAt >= timeout
            }
            .map { it.playerId }

        if (expiredPlayerIds.isEmpty()) return@withLock emptyList()

        val deliveries = mutableListOf<Delivery>()
        val removedRoomIds = mutableSetOf<String>()
        expiredPlayerIds.forEach { playerId ->
            roomFor(playerId)?.takeIf { removedRoomIds.add(it.id) }?.let { room ->
                rooms.remove(room.id)
                deliveries += Delivery(
                    ServerMessage.RoomClosed(
                        roomId = room.id,
                        reason = "Người chơi đã mất kết nối quá lâu."
                    ),
                    room.playerIds()
                )
            }
            sessionsByPlayerId.remove(playerId)
        }
        if (removedRoomIds.isNotEmpty()) {
            deliveries += Delivery(ServerMessage.RoomList(publicRooms()))
        }
        deliveries
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
        room.startedAtEpochMillis = nowMillis()
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

    private fun selectNumber(player: GuestSession, command: ClientMessage.SelectNumber): HandleResult {
        val room = rooms[command.roomId]
            ?: return HandleResult(listOf(error(player.playerId, "ROOM_NOT_FOUND", "Phòng không còn tồn tại.", command.requestId)))
        if (player.playerId !in room.playerIds()) {
            return HandleResult(listOf(error(player.playerId, "NOT_IN_ROOM", "Bạn không ở trong phòng này.", command.requestId)))
        }
        if (command.requestId.isBlank() || command.requestId.length > MAX_REQUEST_ID_LENGTH) {
            return HandleResult(listOf(error(player.playerId, "INVALID_REQUEST_ID", "Mã yêu cầu không hợp lệ.")))
        }
        val requestKey = "${player.playerId}:${command.requestId}"
        room.processedRequests[requestKey]?.let { previous ->
            return HandleResult(listOf(Delivery(previous, setOf(player.playerId))))
        }
        if (room.processedRequests.size >= MAX_REQUESTS_PER_MATCH) {
            return HandleResult(listOf(error(player.playerId, "TOO_MANY_REQUESTS", "Trận đấu có quá nhiều lượt gửi.")))
        }
        room.refreshTimedState()
        if (room.phase != RoomPhase.PLAYING) {
            return HandleResult(
                deliveries = listOf(error(player.playerId, "GAME_NOT_PLAYING", "Trận đấu chưa bắt đầu hoặc đã kết thúc.", command.requestId)),
                completedMatch = room.takeCompletedMatch()
            )
        }
        if (command.number != room.currentTarget) {
            val rejected = error(player.playerId, "WRONG_NUMBER", "Chưa đúng số, thử lại nhé!", command.requestId)
            room.processedRequests[requestKey] = rejected.message
            room.recordSelection(player.playerId, command, SelectionResult.REJECTED)
            return HandleResult(listOf(rejected))
        }

        room.recordSelection(player.playerId, command, SelectionResult.ACCEPTED)
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
        room.processedRequests[requestKey] = event
        return HandleResult(
            deliveries = listOf(Delivery(event, room.playerIds())),
            completedMatch = room.takeCompletedMatch()
        )
    }

    private fun roomFor(playerId: String): Room? = rooms.values.firstOrNull { playerId in it.playerIds() }

    private fun publicRooms(): List<RoomSummary> = rooms.values
        .asSequence()
        .filter {
            it.phase == RoomPhase.WAITING &&
                it.guestId == null &&
                sessionsByPlayerId[it.hostId]?.isConnected == true
        }
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
            nowMillis() - startedAt >= timeAttackMillis
        ) {
            phase = RoomPhase.FINISHED
            sequence++
        }
    }

    private fun Room.takeCompletedMatch(): CompletedMatch? {
        if (phase != RoomPhase.FINISHED || resultQueued) return null
        val startedAt = startedAtEpochMillis ?: return null
        val highestScore = scores.values.maxOrNull() ?: 0
        val leaders = scores.filterValues { it == highestScore }.keys
        val winnerId = leaders.singleOrNull()
        resultQueued = true
        return CompletedMatch(
            matchId = id,
            roomName = name,
            gameMode = gameMode,
            startedAtMillis = startedAt,
            endedAtMillis = nowMillis(),
            winnerPlayerId = winnerId,
            players = playerIds().mapNotNull { playerId ->
                sessionsByPlayerId[playerId]?.let { session ->
                    CompletedMatchPlayer(
                        playerId = playerId,
                        displayName = session.displayName,
                        score = scores[playerId] ?: 0,
                        outcome = when {
                            winnerId == null -> MatchOutcome.DRAW
                            playerId == winnerId -> MatchOutcome.WIN
                            else -> MatchOutcome.LOSS
                        }
                    )
                }
            },
            events = selectionEvents.toList()
        )
    }

    private fun Room.recordSelection(
        playerId: String,
        command: ClientMessage.SelectNumber,
        result: SelectionResult
    ) {
        selectionEvents += MatchSelectionEvent(
            playerId = playerId,
            requestId = command.requestId,
            number = command.number,
            expectedNumber = currentTarget,
            result = result,
            occurredAtMillis = nowMillis(),
            sequence = selectionEvents.size + 1
        )
    }

    private fun error(
        playerId: String,
        code: String,
        message: String,
        requestId: String? = null
    ) = Delivery(ServerMessage.Error(code, message, requestId), setOf(playerId))

    private data class GuestSession(
        val playerId: String,
        val resumeToken: String,
        var displayName: String,
        var isConnected: Boolean = true,
        var disconnectedAtMillis: Long? = null
    )

    private data class HandleResult(
        val deliveries: List<Delivery>,
        val completedMatch: CompletedMatch? = null
    )

    private data class TimedAdvance(
        val delivery: Delivery,
        val completedMatch: CompletedMatch?
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
        var resultQueued: Boolean = false,
        val processedRequests: MutableMap<String, ServerMessage> = mutableMapOf(),
        val selectionEvents: MutableList<MatchSelectionEvent> = mutableListOf()
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
        const val MAX_REQUEST_ID_LENGTH = 64
        const val MAX_REQUESTS_PER_MATCH = 2_000
        const val DEFAULT_TIME_ATTACK_MILLIS = 60_000L
        const val ROOM_RECONNECT_GRACE_MILLIS = 30_000L
        const val IDLE_SESSION_TTL_MILLIS = 5 * 60_000L
        val secureRandom = SecureRandom()
    }
}
