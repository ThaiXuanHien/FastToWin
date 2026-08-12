package com.hienthai.fastowin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hienthai.fastowin.state.GameController
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Composable
fun FastToWinApp(serverUrl: String, resumeTokenStore: ResumeTokenStore) {
    val controller = remember(serverUrl, resumeTokenStore) {
        GameController(serverUrl, resumeTokenStore)
    }
    val state by controller.uiState.collectAsState()

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    FastToWinTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLeaderboardOpen -> LeaderboardScreen(
                    state = state,
                    onBack = controller::closeLeaderboard,
                    onRefresh = controller::openLeaderboard
                )

                state.isProfileOpen -> ProfileScreen(
                    state = state,
                    onBack = controller::closeProfile,
                    onRefresh = controller::openProfile
                )

                state.isGameOver -> ResultScreen(
                    state = state,
                    onRestart = controller::resetGame
                )

                state.isMatchStarted -> GameScreen(
                    state = state,
                    onNumberClick = controller::onNumberClicked,
                    onFinish = {}
                )

                else -> LobbyScreen(
                    state = state,
                    onModeSelected = controller::selectMode,
                    onOpenRoomBrowser = controller::openRoomBrowser,
                    onCreateRoom = controller::createRoom,
                    onJoinRoom = controller::joinRoom,
                    onLeaveRoom = controller::leaveRoom,
                    onRefreshRooms = controller::requestRoomList,
                    onOpenProfile = controller::openProfile,
                    onOpenLeaderboard = controller::openLeaderboard,
                    onBackToMode = controller::backToModeSelection
                )
            }
        }
    }
}
