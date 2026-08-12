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
import com.hienthai.fastowin.state.AuthController
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Composable
fun FastToWinApp(
    serverUrl: String,
    resumeTokenStore: ResumeTokenStore,
    authSessionStore: AuthSessionStore,
    devicePlatform: String
) {
    val authController = remember(serverUrl, authSessionStore, devicePlatform) {
        AuthController(serverUrl, authSessionStore, devicePlatform)
    }
    val authState by authController.state.collectAsState()

    DisposableEffect(authController) {
        onDispose { authController.close() }
    }

    FastToWinTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (authState.stage != AuthStage.PLAYING) {
                AuthScreen(
                    state = authState,
                    onOpenLogin = authController::openLogin,
                    onOpenRegister = authController::openRegister,
                    onPlayAsGuest = authController::playAsGuest,
                    onLogin = authController::login,
                    onRegister = authController::register,
                    onBack = authController::backToWelcome
                )
            } else {
                GameContent(
                    serverUrl = serverUrl,
                    resumeTokenStore = resumeTokenStore,
                    accountUserId = authState.session?.userId,
                    accountDisplayName = authState.session?.displayName,
                    accessTokenProvider = authState.session?.let { { authController.validAccessToken() } },
                    onLogout = authController::logout,
                    onSessionExpired = { authController.expireSession() }
                )
            }
        }
    }
}

@Composable
private fun GameContent(
    serverUrl: String,
    resumeTokenStore: ResumeTokenStore,
    accountUserId: String?,
    accountDisplayName: String?,
    accessTokenProvider: (suspend () -> String?)?,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val controller = remember(serverUrl, resumeTokenStore, accountUserId) {
        GameController(
            serverUrl = serverUrl,
            resumeTokenStore = resumeTokenStore,
            accountDisplayName = accountDisplayName,
            accessTokenProvider = accessTokenProvider,
            onAccountSessionExpired = onSessionExpired
        )
    }
    val state by controller.uiState.collectAsState()

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

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
                    onBackToMode = controller::backToModeSelection,
                    onLogout = onLogout
                )
    }
}
