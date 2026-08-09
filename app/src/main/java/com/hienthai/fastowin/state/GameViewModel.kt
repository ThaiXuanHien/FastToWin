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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private val socketClient = GameSocketClient()
    private var timerJob: Job? = null
    private var connectionJob: Job? = null
    private var messageJob: Job? = null

    private val TAG = "GameViewModel"

    init {
        observeSocketMessages()
    }

    private fun observeSocketMessages() {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            socketClient.messages.collect { message ->
                Log.d(TAG, "Observed message: $message")
                when (message) {
                    is GameMessage.Join -> {
                        val myName = _uiState.value.player.name
                        if (message.playerName != myName) {
                            Log.d(TAG, "Opponent joined: ${message.playerName}")
                            _uiState.update { it.copy(
                                opponent = it.opponent.copy(name = message.playerName)
                            ) }
                            
                            // Host logic: The player with lexicographically smaller name is Host
                            if (myName.isNotEmpty() && myName < message.playerName) {
                                Log.d(TAG, "I am Host ($myName < ${message.playerName}). Generating grid...")
                                val shuffledNumbers = (1..100).shuffled()
                                socketClient.sendMessage(GameMessage.StartGame(shuffledNumbers))
                            }
                        }
                    }
                    is GameMessage.SyncState -> {
                        _uiState.update { it.copy(
                            isSearching = false,
                            opponent = it.opponent.copy(
                                name = message.opponentName,
                                score = message.opponentScore,
                                currentTarget = message.opponentTarget,
                                isReady = message.isOpponentReady
                            )
                        ) }
                    }
                    is GameMessage.StartGame -> {
                        Log.d(TAG, "Game Starting with grid size: ${message.grid.size}")
                        _uiState.update { it.copy(isSearching = false) }
                        startGameWithGrid(message.grid)
                    }
                    is GameMessage.Move -> {
                        _uiState.update { it.copy(
                            opponent = it.opponent.copy(
                                score = message.score,
                                currentTarget = message.currentTarget
                            )
                        ) }
                    }
                    else -> {
                        Log.d(TAG, "Unhandled message type: ${message::class.simpleName}")
                    }
                }
            }
        }
    }

    fun selectMode(mode: GameMode) {
        _uiState.update { it.copy(gameMode = mode, lobbyStage = LobbyStage.ENTER_NAME) }
    }

    fun backToModeSelection() {
        _uiState.update { it.copy(lobbyStage = LobbyStage.SELECT_MODE) }
    }

    fun startSearching(playerName: String) {
        Log.d(TAG, "Starting search for $playerName")
        _uiState.update { it.copy(
            player = it.player.copy(name = playerName),
            lobbyStage = LobbyStage.SEARCHING,
            isSearching = true,
            error = null
        ) }
        
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            // Wait for connection then send Join
            launch {
                socketClient.isConnected.collect { connected ->
                    if (connected) {
                        Log.d(TAG, "Socket connected, sending Join for $playerName")
                        socketClient.sendMessage(GameMessage.Join(playerName))
                    }
                }
            }

            try {
                socketClient.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}")
                _uiState.update { it.copy(
                    isSearching = false, 
                    error = "Connection failed: ${e.message}",
                    lobbyStage = LobbyStage.SEARCHING
                ) }
            }
        }
    }

    fun readyUp() {
        _uiState.update { it.copy(player = it.player.copy(isReady = true)) }
        viewModelScope.launch {
            try {
                socketClient.sendMessage(GameMessage.Ready(true))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to send ready status") }
            }
        }
        checkAutoStart()
    }

    private fun checkAutoStart() {
        val state = _uiState.value
        if (state.player.isReady && state.opponent.isReady && state.countdown == null) {
            if (state.numbers.isEmpty()) {
                val shuffledNumbers = (1..100).shuffled()
                viewModelScope.launch {
                    try {
                        socketClient.sendMessage(GameMessage.StartGame(shuffledNumbers))
                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = "Failed to start game") }
                    }
                }
            }
        }
    }

    private fun startGameWithGrid(grid: List<Int>) {
        _uiState.update { it.copy(
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
        ) }

        if (_uiState.value.gameMode == GameMode.TIME_ATTACK) {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeLeftMillis > 0) {
                delay(1000)
                _uiState.update { it.copy(timeLeftMillis = it.timeLeftMillis - 1000) }
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
            
            val isFinished = nextTarget > 100
            _uiState.update { it.copy(
                score = nextScore, 
                currentTarget = nextTarget,
                isGameOver = isFinished,
                player = it.player.copy(score = nextScore, currentTarget = nextTarget)
            ) }

            viewModelScope.launch {
                try {
                    socketClient.sendMessage(GameMessage.Move(number, nextScore, nextTarget))
                } catch (e: Exception) {
                    // Non-fatal error for move
                }
            }

            if (isFinished) {
                timerJob?.cancel()
            }
        } else {
            _uiState.update { it.copy(message = "Wrong number!") }
            viewModelScope.launch {
                delay(1000)
                _uiState.update { it.copy(message = null) }
            }
        }
    }

    fun resetGame() {
        timerJob?.cancel()
        connectionJob?.cancel()
        messageJob?.cancel()
        viewModelScope.launch {
            socketClient.disconnect()
        }
        _uiState.value = GameState(isMatchStarted = false)
        observeSocketMessages()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        connectionJob?.cancel()
        messageJob?.cancel()
        socketClient.close()
    }
}
