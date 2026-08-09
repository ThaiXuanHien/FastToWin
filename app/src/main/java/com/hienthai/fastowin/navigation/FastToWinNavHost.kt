package com.hienthai.fastowin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hienthai.fastowin.state.GameViewModel
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ResultScreen

@Composable
fun FastToWinNavHost(
    modifier: Modifier = Modifier,
    gameViewModel: GameViewModel = viewModel()
) {
    val backStack = rememberNavBackStack(FastToWinNavKey.Lobby)
    val uiState by gameViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.isMatchStarted) {
        if (uiState.isMatchStarted && backStack.lastOrNull() is FastToWinNavKey.Lobby) {
            backStack.add(FastToWinNavKey.Game(uiState.gameMode))
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { 
            if (backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            }
        },
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = { key ->
            when (key) {
                is FastToWinNavKey.Lobby -> androidx.navigation3.runtime.NavEntry(key) {
                    LobbyScreen(
                        state = uiState,
                        onModeSelected = { mode -> gameViewModel.selectMode(mode) },
                        onStartSearching = { name -> gameViewModel.startSearching(name) },
                        onBackToMode = { gameViewModel.backToModeSelection() },
                        onReadyUp = { gameViewModel.readyUp() }
                    )
                }
                is FastToWinNavKey.Game -> androidx.navigation3.runtime.NavEntry(key) {
                    GameScreen(
                        state = uiState,
                        onNumberClick = { gameViewModel.onNumberClicked(it) },
                        onFinish = {
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                backStack.add(FastToWinNavKey.Result(uiState.score, key.mode))
                            }
                        }
                    )
                }
                is FastToWinNavKey.Result -> androidx.navigation3.runtime.NavEntry(key) {
                    ResultScreen(
                        state = uiState,
                        onRestart = dropUnlessResumed {
                            gameViewModel.resetGame()
                            backStack.clear()
                            backStack.add(FastToWinNavKey.Lobby)
                        }
                    )
                }
                else -> error("Unknown key: $key")
            }
        }
    )
}
