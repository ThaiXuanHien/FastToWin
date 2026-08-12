package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.GameSocketClient
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.data.network.SocketConnectionState
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameController(serverUrl: String, resumeTokenStore: ResumeTokenStore) {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private val socket = GameSocketClient(serverUrl, resumeTokenStore)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var playerId: String? = null
    private var sessionJob: Job? = null
    private var messageJob: Job? = null
    private var connectionJob: Job? = null
    private var timerJob: Job? = null
    private var countdownJob: Job? = null
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

        _uiState.update {
            it.copy(
                player = PlayerState(normalizedName),
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
        ensureSocketSession(normalizedName)
    }

    private fun ensureSocketSession(displayName: String = _uiState.value.player.name) {
        if (sessionJob?.isActive == true) {
            requestRoomList()
            return
        }

        messageJob?.cancel()
        messageJob = scope.launch {
            socket.messages.collect(::handleMessage)
        }
        connectionJob?.cancel()
        connectionJob = scope.launch {
            socket.connectionState.collect(::handleConnectionState)
        }
        sessionJob = scope.launch {
            socket.connect(displayName)
        }
    }

    private fun handleConnectionState(socketState: SocketConnectionState) {
        val status = when (socketState) {
            SocketConnectionState.DISCONNECTED -> ConnectionStatus.DISCONNECTED
            SocketConnectionState.CONNECTING -> ConnectionStatus.CONNECTING
            SocketConnectionState.AUTHENTICATING -> ConnectionStatus.AUTHENTICATING
            SocketConnectionState.CONNECTED -> ConnectionStatus.CONNECTED
            SocketConnectionState.RECONNECTING -> ConnectionStatus.RECONNECTING
        }
        _uiState.update { state ->
            val waitingForConnection = status != ConnectionStatus.CONNECTED &&
                state.lobbyStage in setOf(LobbyStage.ROOM_BROWSER, LobbyStage.ROOM_WAITING)
            val reconnectMessage = when {
                status == ConnectionStatus.RECONNECTING && state.isMatchStarted ->
                    "Mất kết nối. Đang kết nối lại..."
                status == ConnectionStatus.CONNECTED && state.message == "Mất kết nối. Đang kết nối lại..." -> null
                else -> state.message
            }
            state.copy(
                connectionStatus = status,
                isSearching = waitingForConnection,
                message = reconnectMessage
            )
        }
    }

    fun requestRoomList() {
        if (sessionJob?.isActive != true) {
            _uiState.update { it.copy(isSearching = true, error = null) }
            ensureSocketSession()
            return
        }
        scope.launch { socket.sendMessage(ClientMessage.ListRooms) }
    }

    fun openProfile() {
        if (sessionJob?.isActive != true || playerId == null) {
            _uiState.update { it.copy(error = "Chưa kết nối được máy chủ.") }
            return
        }
        _uiState.update { it.copy(isProfileOpen = true, isProfileLoading = true, error = null) }
        scope.launch { socket.sendMessage(ClientMessage.GetProfile) }
    }

    fun closeProfile() {
        _uiState.update { it.copy(isProfileOpen = false, isProfileLoading = false) }
    }

    fun createRoom(roomName: String, password: String) {
        if (roomName.isBlank() || password.isEmpty()) {
            _uiState.update { it.copy(error = "Vui lòng nhập tên và mật khẩu phòng.") }
            return
        }
        _uiState.update { it.copy(isSearching = true, error = null) }
        scope.launch {
            socket.sendMessage(
                ClientMessage.CreateRoom(
                    roomName = roomName,
                    password = password,
                    gameMode = _uiState.value.gameMode.toProtocol()
                )
            )
        }
    }

    fun joinRoom(roomId: String, password: String) {
        val room = _uiState.value.availableRooms.firstOrNull { it.id == roomId }
        if (room == null) {
            _uiState.update { it.copy(error = "Phòng không còn khả dụng.") }
            return
        }
        _uiState.update {
            it.copy(
                gameMode = room.gameMode,
                lobbyStage = LobbyStage.ROOM_WAITING,
                currentRoomId = room.id,
                currentRoomName = room.name,
                isRoomHost = false,
                isSearching = true,
                error = null
            )
        }
        scope.launch { socket.sendMessage(ClientMessage.JoinRoom(roomId, password)) }
    }

    fun leaveRoom() {
        val roomId = _uiState.value.currentRoomId
        if (roomId != null) scope.launch { socket.sendMessage(ClientMessage.LeaveRoom(roomId)) }
        returnToRoomBrowser()
    }

    private suspend fun handleMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.SessionReady -> {
                playerId = message.playerId
                _uiState.update { it.copy(isSearching = false, error = null) }
                message.currentGame?.let { game ->
                    if (game.phase == RoomPhase.PLAYING || game.phase == RoomPhase.FINISHED) {
                        startGameWithSnapshot(game)
                    } else {
                        applyWaitingSnapshot(game)
                    }
                }
            }

            is ServerMessage.RoomList -> {
                _uiState.update { state ->
                    state.copy(
                        isSearching = false,
                        availableRooms = message.rooms.map { room ->
                            AvailableRoom(
                                id = room.id,
                                name = room.name,
                                hostName = room.hostName,
                                gameMode = room.gameMode.toUi(),
                                requiresPassword = room.requiresPassword,
                                lastSeenAtMillis = epochMillis()
                            )
                        }
                    )
                }
            }

            is ServerMessage.ProfileData -> {
                _uiState.update {
                    it.copy(profile = message.profile, isProfileLoading = false, error = null)
                }
            }

            is ServerMessage.RoomCreated -> applyWaitingSnapshot(message.game)
            is ServerMessage.GameStarted -> startMatchCountdown(message.game)
            is ServerMessage.GameStateUpdated -> applyGameSnapshot(message.game)
            is ServerMessage.RoomClosed -> {
                if (_uiState.value.currentRoomId == message.roomId) {
                    returnToRoomBrowser(message.reason)
                }
            }

            is ServerMessage.Error -> handleServerError(message)
        }
    }

    private fun applyWaitingSnapshot(game: GameSnapshot) {
        gameStarted = false
        val me = game.players.firstOrNull { it.id == playerId }
        val opponent = game.players.firstOrNull { it.id != playerId }
        _uiState.update { state ->
            state.copy(
                gameMode = game.gameMode.toUi(),
                player = PlayerState(me?.name ?: state.player.name, score = me?.score ?: 0),
                opponent = PlayerState(opponent?.name ?: DEFAULT_OPPONENT_NAME, score = opponent?.score ?: 0),
                lobbyStage = LobbyStage.ROOM_WAITING,
                currentRoomId = game.roomId,
                currentRoomName = game.roomName,
                isRoomHost = game.hostId == playerId,
                isSearching = false,
                error = null
            )
        }
    }

    private fun startMatchCountdown(game: GameSnapshot) {
        if (gameStarted && _uiState.value.currentRoomId == game.roomId) return
        gameStarted = true
        countdownJob?.cancel()
        countdownJob = scope.launch {
            applyWaitingSnapshot(game)
            gameStarted = true
            _uiState.update { it.copy(lobbyStage = LobbyStage.MATCHED) }
            for (count in 3 downTo 1) {
                _uiState.update { it.copy(countdown = count) }
                delay(1_000)
            }
            startGameWithSnapshot(game)
        }
    }

    private fun startGameWithSnapshot(game: GameSnapshot) {
        countdownJob?.cancel()
        gameStarted = game.phase == RoomPhase.PLAYING
        applyGameSnapshot(game, forceStart = true)
        if (game.gameMode == ProtocolGameMode.TIME_ATTACK && game.phase == RoomPhase.PLAYING) {
            startTimer(game.startedAtEpochMillis)
        }
    }

    private fun applyGameSnapshot(game: GameSnapshot, forceStart: Boolean = false) {
        val me = game.players.firstOrNull { it.id == playerId }
        val opponent = game.players.firstOrNull { it.id != playerId }
        val finished = game.phase == RoomPhase.FINISHED
        _uiState.update { state ->
            state.copy(
                numbers = if (game.numbers.isNotEmpty()) game.numbers else state.numbers,
                currentTarget = game.currentTarget,
                score = me?.score ?: 0,
                isGameOver = finished,
                gameMode = game.gameMode.toUi(),
                player = PlayerState(
                    name = me?.name ?: state.player.name,
                    score = me?.score ?: 0,
                    currentTarget = game.currentTarget
                ),
                opponent = PlayerState(
                    name = opponent?.name ?: DEFAULT_OPPONENT_NAME,
                    score = opponent?.score ?: 0,
                    currentTarget = game.currentTarget
                ),
                lobbyStage = LobbyStage.MATCHED,
                currentRoomId = game.roomId,
                currentRoomName = game.roomName,
                isRoomHost = game.hostId == playerId,
                isSearching = false,
                isMatchStarted = forceStart || state.isMatchStarted,
                countdown = null,
                error = null
            )
        }
        if (finished) {
            gameStarted = false
            timerJob?.cancel()
        }
    }

    private fun handleServerError(error: ServerMessage.Error) {
        if (error.code == "WRONG_NUMBER") {
            _uiState.update { it.copy(message = error.message) }
            scope.launch {
                delay(1_000)
                _uiState.update { it.copy(message = null) }
            }
            return
        }

        if (error.code in setOf("WRONG_PASSWORD", "ROOM_NOT_FOUND", "ROOM_FULL", "ALREADY_IN_ROOM")) {
            returnToRoomBrowser(error.message)
        } else {
            _uiState.update { it.copy(isSearching = false, error = error.message) }
        }
    }

    private fun startTimer(startedAtEpochMillis: Long?) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (gameStarted) {
                val elapsed = startedAtEpochMillis?.let { epochMillis() - it } ?: 0L
                val remaining = (60_000L - elapsed).coerceAtLeast(0L)
                _uiState.update { it.copy(timeLeftMillis = remaining) }
                if (remaining == 0L) {
                    gameStarted = false
                    _uiState.update { it.copy(isGameOver = true) }
                    break
                }
                delay(250)
            }
        }
    }

    fun onNumberClicked(number: Int) {
        val state = _uiState.value
        val roomId = state.currentRoomId ?: return
        if (!gameStarted || state.isGameOver) return
        scope.launch {
            socket.sendMessage(
                ClientMessage.SelectNumber(
                    roomId = roomId,
                    number = number,
                    requestId = randomId()
                )
            )
        }
    }

    fun resetGame() {
        val roomId = _uiState.value.currentRoomId
        val activeSession = sessionJob
        timerJob?.cancel()
        countdownJob?.cancel()
        gameStarted = false
        _uiState.value = GameState()

        sessionJob = null
        scope.launch {
            if (roomId != null) socket.sendMessage(ClientMessage.LeaveRoom(roomId))
            socket.disconnect()
            activeSession?.cancel()
        }
    }

    fun close() {
        timerJob?.cancel()
        countdownJob?.cancel()
        messageJob?.cancel()
        connectionJob?.cancel()
        sessionJob?.cancel()
        socket.close()
        scope.cancel()
    }

    private fun returnToRoomBrowser(error: String? = null) {
        gameStarted = false
        timerJob?.cancel()
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                numbers = emptyList(),
                opponent = PlayerState(DEFAULT_OPPONENT_NAME),
                lobbyStage = LobbyStage.ROOM_BROWSER,
                currentRoomId = null,
                currentRoomName = null,
                isRoomHost = false,
                isSearching = false,
                isMatchStarted = false,
                countdown = null,
                error = error
            )
        }
        requestRoomList()
    }

    private fun GameMode.toProtocol(): ProtocolGameMode = when (this) {
        GameMode.ORDER -> ProtocolGameMode.ORDER
        GameMode.TIME_ATTACK -> ProtocolGameMode.TIME_ATTACK
    }

    private fun ProtocolGameMode.toUi(): GameMode = when (this) {
        ProtocolGameMode.ORDER -> GameMode.ORDER
        ProtocolGameMode.TIME_ATTACK -> GameMode.TIME_ATTACK
    }

    private fun randomId(): String = buildString {
        repeat(2) { append(Random.nextLong().toULong().toString(16).padStart(16, '0')) }
    }
}
