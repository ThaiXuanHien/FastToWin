package com.hienthai.fastowin.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hienthai.fastowin.data.network.GameMessage
import com.hienthai.fastowin.data.network.GameSocketClient
import com.hienthai.fastowin.navigation.GameMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private val socket = GameSocketClient()

    private var sessionJob: Job? = null   // owns: connect + beacon + message collector
    private var timerJob: Job? = null

    // True once StartGame has been processed — prevents duplicate game starts
    @Volatile private var gameStarted = false

    private val TAG = "GameViewModel"

    // ── Mode / name selection ────────────────────────────────────────────────

    fun selectMode(mode: GameMode) {
        _uiState.update { it.copy(gameMode = mode, lobbyStage = LobbyStage.ENTER_NAME) }
    }

    fun backToModeSelection() {
        _uiState.update { it.copy(lobbyStage = LobbyStage.SELECT_MODE) }
    }

    // ── Matchmaking ──────────────────────────────────────────────────────────

    fun startSearching(playerName: String) {
        Log.d(TAG, "startSearching: $playerName")
        gameStarted = false

        _uiState.update {
            it.copy(
                player = it.player.copy(name = playerName),
                opponent = PlayerState("Opponent"),
                lobbyStage = LobbyStage.SEARCHING,
                isSearching = true,
                error = null
            )
        }

        // Cancel any previous session cleanly before starting a new one
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {

            // 1. Message collector — runs for the lifetime of this session
            launch {
                socket.messages.collect { message ->
                    handleMessage(message, playerName)
                }
            }

            // 2. Beacon — sends Join every 2 s until we match or game starts
            launch {
                // Wait for the socket to be connected first
                socket.isConnected.first { it }
                Log.d(TAG, "Beacon started")

                // Always announce this player at least once. The other player's Join can
                // arrive immediately after connecting and mark us as matched before this
                // coroutine gets scheduled; without this first send, matchmaking becomes
                // one-sided because the other device never learns our name.
                Log.d(TAG, "Beacon → Join($playerName)")
                socket.sendMessage(GameMessage.Join(playerName))

                while (!gameStarted && _uiState.value.opponent.name == "Opponent") {
                    delay(2_000)
                    if (gameStarted || _uiState.value.opponent.name != "Opponent") break
                    Log.d(TAG, "Beacon → Join($playerName)")
                    socket.sendMessage(GameMessage.Join(playerName))
                }
                Log.d(TAG, "Beacon stopped")
            }

            // 3. WebSocket connection — suspends until disconnected
            try {
                socket.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "Connection failed: ${e.message}",
                        lobbyStage = LobbyStage.SEARCHING
                    )
                }
            }
        }
    }

    // ── Message handling ─────────────────────────────────────────────────────

    private suspend fun handleMessage(message: GameMessage, myName: String) {
        Log.d(TAG, "handleMessage: $message")
        when (message) {
            is GameMessage.Join -> {
                if (message.playerName == myName) {
                    Log.d(TAG, "Ignoring own Join echo")
                    return
                }
                // If we are already matched (or game already started) ignore further Joins
                if (_uiState.value.opponent.name != "Opponent" || gameStarted) {
                    Log.d(TAG, "Already matched, ignoring Join from ${message.playerName}")
                    return
                }

                // A Join is not retained by the broadcast server. If this device joined
                // first, its last beacon may have been sent before the opponent connected.
                // Reply before marking the match complete so both devices learn each
                // other's name even when they connect between beacon intervals.
                Log.d(TAG, "Join acknowledged → Join($myName)")
                socket.sendMessage(GameMessage.Join(myName))

                Log.d(TAG, "Matched with ${message.playerName}")
                _uiState.update { it.copy(opponent = it.opponent.copy(name = message.playerName)) }

                // Host election: lexicographically smaller name drives the game
                if (myName < message.playerName) {
                    if (gameStarted) return          // double-check under fast scheduling
                    gameStarted = true
                    Log.d(TAG, "I am Host → sending StartGame")
                    val grid = (1..100).shuffled()
                    socket.sendMessage(GameMessage.StartGame(grid))
                    // Host also starts its own countdown directly
                    startMatchCountdown(grid)
                } else {
                    Log.d(TAG, "I am Guest → waiting for StartGame")
                }
            }

            is GameMessage.StartGame -> {
                if (gameStarted) {
                    Log.d(TAG, "Duplicate StartGame ignored")
                    return
                }
                gameStarted = true
                Log.d(TAG, "StartGame received, grid size=${message.grid.size}")
                startMatchCountdown(message.grid)
            }

            is GameMessage.Move -> {
                _uiState.update {
                    it.copy(
                        opponent = it.opponent.copy(
                            score = message.score,
                            currentTarget = message.currentTarget
                        )
                    )
                }
            }

            else -> Log.d(TAG, "Unhandled: ${message::class.simpleName}")
        }
    }

    // ── Game lifecycle ───────────────────────────────────────────────────────

    private fun startMatchCountdown(grid: List<Int>) {
        viewModelScope.launch {
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
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeLeftMillis > 0) {
                delay(1_000)
                _uiState.update { it.copy(timeLeftMillis = it.timeLeftMillis - 1_000) }
            }
            _uiState.update { it.copy(isGameOver = true) }
        }
    }

    fun onNumberClicked(number: Int) {
        val state = _uiState.value
        if (state.isGameOver) return

        if (number == state.currentTarget) {
            val nextTarget = state.currentTarget + 1
            val nextScore = state.score + 10
            val finished = nextTarget > 100

            _uiState.update {
                it.copy(
                    score = nextScore,
                    currentTarget = nextTarget,
                    isGameOver = finished,
                    player = it.player.copy(score = nextScore, currentTarget = nextTarget)
                )
            }

            viewModelScope.launch {
                socket.sendMessage(GameMessage.Move(number, nextScore, nextTarget))
            }

            if (finished) timerJob?.cancel()
        } else {
            _uiState.update { it.copy(message = "Wrong number!") }
            viewModelScope.launch {
                delay(1_000)
                _uiState.update { it.copy(message = null) }
            }
        }
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    fun resetGame() {
        timerJob?.cancel()
        sessionJob?.cancel()
        gameStarted = false
        viewModelScope.launch { socket.disconnect() }
        _uiState.value = GameState()
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        sessionJob?.cancel()
        socket.close()
    }
}
