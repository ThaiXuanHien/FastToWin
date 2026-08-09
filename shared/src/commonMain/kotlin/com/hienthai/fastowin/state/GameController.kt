package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.GameMessage
import com.hienthai.fastowin.data.network.GameSocketClient
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.epochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameController {

    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private val socket = GameSocketClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playerId = randomId()

    private var sessionJob: Job? = null
    private var messageJob: Job? = null
    private var roomAdvertiseJob: Job? = null
    private var roomCleanupJob: Job? = null
    private var pendingJoinJob: Job? = null
    private var timerJob: Job? = null

    private var roomPasswordHash = ""
    private var pendingRoomId: String? = null

    private var gameStarted = false

    fun selectMode(mode: GameMode) {
        _uiState.update { it.copy(gameMode = mode, lobbyStage = LobbyStage.ENTER_NAME) }
    }

    fun backToModeSelection() {
        _uiState.update { it.copy(lobbyStage = LobbyStage.SELECT_MODE, error = null) }
    }

    fun openRoomBrowser(playerName: String) {
        val normalizedName = playerName.trim()
        if (normalizedName.isEmpty()) return

        gameStarted = false
        _uiState.update {
            it.copy(
                player = it.player.copy(name = normalizedName),
                opponent = PlayerState(DEFAULT_OPPONENT_NAME),
                lobbyStage = LobbyStage.ROOM_BROWSER,
                isSearching = true,
                availableRooms = emptyList(),
                currentRoomId = null,
                currentRoomName = null,
                isRoomHost = false,
                error = null
            )
        }
        ensureSocketSession()
    }

    private fun ensureSocketSession() {
        if (sessionJob?.isActive == true) {
            requestRoomList()
            return
        }

        messageJob?.cancel()
        roomCleanupJob?.cancel()

        messageJob = scope.launch {
            socket.messages.collect { message -> handleMessage(message) }
        }

        roomCleanupJob = scope.launch {
            while (true) {
                delay(2_000)
                val oldestAllowed = epochMillis() - ROOM_TTL_MILLIS
                _uiState.update { state ->
                    state.copy(
                        availableRooms = state.availableRooms.filter {
                            it.lastSeenAtMillis >= oldestAllowed
                        }
                    )
                }
            }
        }

        sessionJob = scope.launch {
            val connectedJob = launch {
                socket.isConnected.first { it }
                _uiState.update { it.copy(isSearching = false, error = null) }
                socket.sendMessage(GameMessage.RoomListRequest(playerId))
            }

            try {
                socket.connect()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("GameController: Connection error: ${error.message}")
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "Không thể kết nối: ${error.message}"
                    )
                }
            } finally {
                connectedJob.cancel()
            }
        }
    }

    fun requestRoomList() {
        if (!socket.isConnected.value) {
            _uiState.update { it.copy(isSearching = true, error = null) }
            ensureSocketSession()
            return
        }
        scope.launch {
            socket.sendMessage(GameMessage.RoomListRequest(playerId))
        }
    }

    fun createRoom(roomName: String, password: String) {
        val normalizedRoomName = roomName.trim()
        if (normalizedRoomName.isEmpty()) return
        if (password.isEmpty()) {
            _uiState.update { it.copy(error = "Vui lòng nhập mật khẩu phòng.") }
            return
        }
        if (!socket.isConnected.value) {
            _uiState.update { it.copy(error = "Chưa kết nối máy chủ. Vui lòng thử lại.") }
            return
        }

        val roomId = randomId()
        roomPasswordHash = hashPassword(password)
        pendingRoomId = null
        gameStarted = false

        _uiState.update {
            it.copy(
                opponent = PlayerState(DEFAULT_OPPONENT_NAME),
                lobbyStage = LobbyStage.ROOM_WAITING,
                currentRoomId = roomId,
                currentRoomName = normalizedRoomName,
                isRoomHost = true,
                isSearching = true,
                error = null
            )
        }
        startRoomAdvertising(roomId)
    }

    private fun startRoomAdvertising(roomId: String) {
        roomAdvertiseJob?.cancel()
        roomAdvertiseJob = scope.launch {
            while (
                !gameStarted &&
                _uiState.value.currentRoomId == roomId &&
                _uiState.value.isRoomHost
            ) {
                advertiseCurrentRoom()
                delay(2_000)
            }
        }
    }

    private suspend fun advertiseCurrentRoom() {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        val roomName = state.currentRoomName ?: return
        if (!state.isRoomHost || gameStarted || !socket.isConnected.value) return

        socket.sendMessage(
            GameMessage.RoomAdvertise(
                roomId = roomId,
                roomName = roomName,
                hostId = playerId,
                hostName = state.player.name,
                gameMode = state.gameMode.name,
                requiresPassword = true
            )
        )
    }

    fun joinRoom(roomId: String, password: String) {
        val room = _uiState.value.availableRooms.firstOrNull { it.id == roomId }
        if (room == null) {
            _uiState.update { it.copy(error = "Phòng không còn khả dụng.") }
            return
        }
        if (!socket.isConnected.value) {
            _uiState.update { it.copy(error = "Chưa kết nối máy chủ. Vui lòng thử lại.") }
            return
        }

        pendingRoomId = roomId
        gameStarted = false
        _uiState.update {
            it.copy(
                gameMode = room.gameMode,
                lobbyStage = LobbyStage.ROOM_WAITING,
                currentRoomId = room.id,
                currentRoomName = room.name,
                isRoomHost = false,
                isSearching = true,
                opponent = PlayerState(DEFAULT_OPPONENT_NAME),
                error = null
            )
        }

        scope.launch {
            socket.sendMessage(
                GameMessage.RoomJoin(
                    roomId = roomId,
                    playerId = playerId,
                    playerName = _uiState.value.player.name,
                    passwordHash = hashPassword(password)
                )
            )
        }

        pendingJoinJob?.cancel()
        pendingJoinJob = scope.launch {
            delay(JOIN_TIMEOUT_MILLIS)
            if (pendingRoomId != roomId) return@launch
            pendingRoomId = null
            _uiState.update {
                it.copy(
                    lobbyStage = LobbyStage.ROOM_BROWSER,
                    currentRoomId = null,
                    currentRoomName = null,
                    isRoomHost = false,
                    isSearching = false,
                    error = "Phòng không phản hồi. Hãy làm mới và thử lại."
                )
            }
            requestRoomList()
        }
    }

    fun leaveRoom() {
        val state = _uiState.value
        val roomId = state.currentRoomId
        roomAdvertiseJob?.cancel()
        pendingJoinJob?.cancel()
        pendingRoomId = null
        gameStarted = false

        if (state.isRoomHost && roomId != null) {
            scope.launch {
                socket.sendMessage(GameMessage.RoomClosed(roomId, playerId))
            }
        }

        _uiState.update {
            it.copy(
                opponent = PlayerState(DEFAULT_OPPONENT_NAME),
                lobbyStage = LobbyStage.ROOM_BROWSER,
                currentRoomId = null,
                currentRoomName = null,
                isRoomHost = false,
                isSearching = false,
                error = null
            )
        }
        requestRoomList()
    }

    private suspend fun handleMessage(message: GameMessage) {
        println("GameController: handleMessage: $message")
        when (message) {
            is GameMessage.RoomListRequest -> {
                if (message.requesterId != playerId) advertiseCurrentRoom()
            }

            is GameMessage.RoomAdvertise -> {
                if (message.hostId == playerId || _uiState.value.lobbyStage != LobbyStage.ROOM_BROWSER) {
                    return
                }
                val mode = runCatching { GameMode.valueOf(message.gameMode) }.getOrDefault(GameMode.ORDER)
                val room = AvailableRoom(
                    id = message.roomId,
                    name = message.roomName,
                    hostName = message.hostName,
                    gameMode = mode,
                    requiresPassword = message.requiresPassword,
                    lastSeenAtMillis = epochMillis()
                )
                _uiState.update { state ->
                    state.copy(
                        availableRooms = (state.availableRooms.filterNot { it.id == room.id } + room)
                            .sortedBy { it.name.lowercase() }
                    )
                }
            }

            is GameMessage.RoomJoin -> handleRoomJoin(message)

            is GameMessage.RoomJoined -> {
                if (
                    message.guestId != playerId ||
                    pendingRoomId != message.roomId ||
                    _uiState.value.currentRoomId != message.roomId
                ) {
                    return
                }
                pendingJoinJob?.cancel()
                pendingRoomId = null
                _uiState.update {
                    it.copy(
                        opponent = PlayerState(message.hostName),
                        isSearching = false,
                        error = null
                    )
                }
            }

            is GameMessage.RoomJoinRejected -> {
                if (message.playerId != playerId || pendingRoomId != message.roomId) return
                pendingJoinJob?.cancel()
                pendingRoomId = null
                _uiState.update {
                    it.copy(
                        lobbyStage = LobbyStage.ROOM_BROWSER,
                        currentRoomId = null,
                        currentRoomName = null,
                        isRoomHost = false,
                        isSearching = false,
                        error = message.reason
                    )
                }
            }

            is GameMessage.RoomClosed -> {
                _uiState.update { state ->
                    state.copy(availableRooms = state.availableRooms.filterNot { it.id == message.roomId })
                }
                if (
                    !_uiState.value.isRoomHost &&
                    _uiState.value.currentRoomId == message.roomId &&
                    !gameStarted
                ) {
                    pendingJoinJob?.cancel()
                    pendingRoomId = null
                    _uiState.update {
                        it.copy(
                            lobbyStage = LobbyStage.ROOM_BROWSER,
                            currentRoomId = null,
                            currentRoomName = null,
                            isSearching = false,
                            error = "Chủ phòng đã đóng phòng."
                        )
                    }
                }
            }

            is GameMessage.StartGame -> {
                if (message.roomId != _uiState.value.currentRoomId || gameStarted) return
                gameStarted = true
                startMatchCountdown(message.grid)
            }

            is GameMessage.Move -> handleMove(message)

            // Messages from pre-room app versions are deliberately ignored.
            is GameMessage.Join,
            is GameMessage.Ready,
            is GameMessage.SyncState -> Unit
        }
    }

    private suspend fun handleRoomJoin(message: GameMessage.RoomJoin) {
        val state = _uiState.value
        if (
            !state.isRoomHost ||
            state.currentRoomId != message.roomId ||
            state.lobbyStage != LobbyStage.ROOM_WAITING
        ) {
            return
        }

        val rejectionReason = when {
            gameStarted || state.opponent.name != DEFAULT_OPPONENT_NAME -> "Phòng đã đủ người."
            message.passwordHash != roomPasswordHash -> "Mật khẩu phòng không đúng."
            else -> null
        }

        if (rejectionReason != null) {
            socket.sendMessage(
                GameMessage.RoomJoinRejected(message.roomId, message.playerId, rejectionReason)
            )
            return
        }

        roomAdvertiseJob?.cancel()
        _uiState.update {
            it.copy(opponent = PlayerState(message.playerName), isSearching = false, error = null)
        }

        socket.sendMessage(
            GameMessage.RoomJoined(
                roomId = message.roomId,
                hostId = playerId,
                hostName = state.player.name,
                guestId = message.playerId,
                guestName = message.playerName
            )
        )

        gameStarted = true
        val grid = (1..GAME_NUMBER_COUNT).shuffled()
        socket.sendMessage(GameMessage.StartGame(message.roomId, grid))
        startMatchCountdown(grid)
    }

    private fun handleMove(message: GameMessage.Move) {
        if (
            message.roomId != _uiState.value.currentRoomId ||
            message.playerId == playerId ||
            !gameStarted
        ) {
            return
        }

        _uiState.update {
            val nextSharedTarget = if (
                message.number == it.currentTarget &&
                message.currentTarget == it.currentTarget + 1
            ) {
                message.currentTarget
            } else {
                it.currentTarget
            }

            it.copy(
                currentTarget = nextSharedTarget,
                isGameOver = it.isGameOver || nextSharedTarget > GAME_NUMBER_COUNT,
                player = it.player.copy(currentTarget = nextSharedTarget),
                opponent = it.opponent.copy(
                    score = maxOf(it.opponent.score, message.score),
                    currentTarget = nextSharedTarget
                )
            )
        }

        if (_uiState.value.isGameOver) timerJob?.cancel()
    }

    private fun startMatchCountdown(grid: List<Int>) {
        scope.launch {
            _uiState.update { it.copy(lobbyStage = LobbyStage.MATCHED, isSearching = false) }
            for (i in 3 downTo 1) {
                _uiState.update { it.copy(countdown = i) }
                delay(1_000)
            }
            startGameWithGrid(grid)
        }
    }

    private fun startGameWithGrid(grid: List<Int>) {
        _uiState.update {
            it.copy(
                numbers = grid,
                currentTarget = 1,
                score = 0,
                timeLeftMillis = if (it.gameMode == GameMode.TIME_ATTACK) 60_000L else 0L,
                isGameOver = false,
                player = it.player.copy(score = 0, currentTarget = 1),
                opponent = it.opponent.copy(score = 0, currentTarget = 1),
                isMatchStarted = true,
                countdown = null,
                error = null
            )
        }
        if (_uiState.value.gameMode == GameMode.TIME_ATTACK) startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_uiState.value.timeLeftMillis > 0) {
                delay(1_000)
                _uiState.update { it.copy(timeLeftMillis = it.timeLeftMillis - 1_000) }
            }
            _uiState.update { it.copy(isGameOver = true) }
        }
    }

    fun onNumberClicked(number: Int) {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (state.isGameOver || !gameStarted) return

        if (number == state.currentTarget) {
            val nextTarget = state.currentTarget + 1
            val nextScore = state.score + 10
            val finished = nextTarget > GAME_NUMBER_COUNT

            _uiState.update {
                it.copy(
                    score = nextScore,
                    currentTarget = nextTarget,
                    isGameOver = finished,
                    player = it.player.copy(score = nextScore, currentTarget = nextTarget),
                    opponent = it.opponent.copy(currentTarget = nextTarget)
                )
            }

            scope.launch {
                socket.sendMessage(
                    GameMessage.Move(
                        roomId = roomId,
                        playerId = playerId,
                        playerName = state.player.name,
                        number = number,
                        score = nextScore,
                        currentTarget = nextTarget
                    )
                )
            }

            if (finished) timerJob?.cancel()
        } else {
            _uiState.update { it.copy(message = "Chưa đúng số, thử lại nhé!") }
            scope.launch {
                delay(1_000)
                _uiState.update { it.copy(message = null) }
            }
        }
    }

    fun resetGame() {
        val state = _uiState.value
        timerJob?.cancel()
        roomAdvertiseJob?.cancel()
        pendingJoinJob?.cancel()
        gameStarted = false
        pendingRoomId = null

        scope.launch {
            if (state.isRoomHost && state.currentRoomId != null) {
                socket.sendMessage(GameMessage.RoomClosed(state.currentRoomId, playerId))
            }
            socket.disconnect()
        }

        sessionJob?.cancel()
        messageJob?.cancel()
        roomCleanupJob?.cancel()
        _uiState.value = GameState()
    }

    fun close() {
        timerJob?.cancel()
        roomAdvertiseJob?.cancel()
        pendingJoinJob?.cancel()
        roomCleanupJob?.cancel()
        messageJob?.cancel()
        sessionJob?.cancel()
        socket.close()
        scope.cancel()
    }

    private fun hashPassword(password: String): String {
        return sha256(password.encodeToByteArray())
    }

    private fun randomId(): String = buildString {
        repeat(4) {
            append(Random.nextLong().toULong().toString(16).padStart(16, '0'))
        }
    }

    private companion object {
        const val ROOM_TTL_MILLIS = 7_000L
        const val JOIN_TIMEOUT_MILLIS = 8_000L
    }
}
