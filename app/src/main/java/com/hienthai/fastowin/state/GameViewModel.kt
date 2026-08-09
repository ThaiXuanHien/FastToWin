package com.hienthai.fastowin.state

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
    private var socketJob: Job? = null

    init {
        observeSocketMessages()
    }

    private fun observeSocketMessages() {
        socketJob = viewModelScope.launch {
            socketClient.messages.collect { message ->
                when (message) {
                    is GameMessage.SyncState -> {
                        _uiState.update { it.copy(
                            opponent = it.opponent.copy(
                                name = message.opponentName,
                                score = message.opponentScore,
                                currentTarget = message.opponentTarget,
                                isReady = message.isOpponentReady
                            )
                        ) }
                    }
                    is GameMessage.StartGame -> {
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
                    else -> {}
                }
            }
        }
    }

    fun findMatch(mode: GameMode) {
        _uiState.update { it.copy(isSearching = true, gameMode = mode) }
        viewModelScope.launch {
            try {
                socketClient.connect()
                socketClient.sendMessage(GameMessage.Join("Player ${System.currentTimeMillis() % 1000}"))
                _uiState.update { it.copy(isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, message = "Connection failed") }
            }
        }
    }

    fun readyUp() {
        _uiState.update { it.copy(player = it.player.copy(isReady = true)) }
        viewModelScope.launch {
            socketClient.sendMessage(GameMessage.Ready(true))
        }
        checkAutoStart()
    }

    private fun checkAutoStart() {
        val state = _uiState.value
        if (state.player.isReady && state.opponent.isReady && state.countdown == null) {
            // First player to be ready generates the grid (simplified logic)
            // In a real app, the server should do this.
            if (state.numbers.isEmpty()) {
                val shuffledNumbers = (1..100).shuffled()
                viewModelScope.launch {
                    socketClient.sendMessage(GameMessage.StartGame(shuffledNumbers))
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
            countdown = null
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
                socketClient.sendMessage(GameMessage.Move(number, nextScore, nextTarget))
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
        socketJob?.cancel()
        socketClient.close()
        _uiState.value = GameState(isMatchStarted = false)
        observeSocketMessages()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        socketJob?.cancel()
        socketClient.close()
    }
}
