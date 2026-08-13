package com.hienthai.fastowin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hienthai.fastowin.state.GameController
import com.hienthai.fastowin.state.AuthController
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.FriendsScreen
import com.hienthai.fastowin.ui.screens.RoomInvitationDialog
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.screens.FastToWinBottomBar
import com.hienthai.fastowin.ui.screens.GameModePickerDialog
import com.hienthai.fastowin.ui.screens.MainTab
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import com.hienthai.fastowin.platform.epochMillis

@Composable
fun FastToWinApp(
    serverUrl: String,
    resumeTokenStore: ResumeTokenStore,
    authSessionStore: AuthSessionStore,
    devicePlatform: String
) {
    val authController = remember(serverUrl, authSessionStore, devicePlatform) {
        AuthController(serverUrl, authSessionStore, resumeTokenStore, devicePlatform)
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
                    onOpenPasswordReset = authController::openPasswordReset,
                    onPlayAsGuest = authController::playAsGuest,
                    onLogin = authController::login,
                    onRegister = authController::register,
                    onUpgradeGuest = authController::upgradeGuest,
                    onRequestPasswordReset = authController::requestPasswordReset,
                    onConfirmPasswordReset = authController::confirmPasswordReset,
                    onBack = authController::backToWelcome,
                    onCancelUpgrade = authController::cancelGuestUpgrade
                )
            } else {
                GameContent(
                    serverUrl = serverUrl,
                    resumeTokenStore = resumeTokenStore,
                    accountUserId = authState.session?.userId,
                    accountDisplayName = authState.session?.displayName,
                    accessTokenProvider = authState.session?.let { { authController.validAccessToken() } },
                    onLogout = authController::logout,
                    isGuest = authState.isGuest,
                    onUpgradeGuest = authController::openGuestUpgrade,
                    onChangePassword = authController::changePassword,
                    onDeleteAccount = authController::deleteAccount,
                    authState = authState,
                    onSessionExpired = { authController.expireSession() },
                    onProfileDisplayNameChanged = authController::updateStoredDisplayName
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
    isGuest: Boolean,
    onUpgradeGuest: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    authState: com.hienthai.fastowin.state.AuthState,
    onSessionExpired: () -> Unit,
    onProfileDisplayNameChanged: (String) -> Unit
) {
    val controller = remember(serverUrl, resumeTokenStore, accountUserId) {
        GameController(
            serverUrl = serverUrl,
            resumeTokenStore = resumeTokenStore,
            accountDisplayName = accountDisplayName,
            accessTokenProvider = accessTokenProvider,
            onAccountSessionExpired = onSessionExpired,
            onProfileDisplayNameChanged = onProfileDisplayNameChanged
        )
    }
    val state by controller.uiState.collectAsState()
    val sessionStartedAtMillis = remember { epochMillis() }
    var showGameModePicker by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    state.roomInvitation?.let { invitation ->
        RoomInvitationDialog(invitation, controller::respondRoomInvitation)
    }
    if (showGameModePicker) {
        GameModePickerDialog(
            title = "Chọn chế độ chơi",
            onDismiss = { showGameModePicker = false },
            onSelect = { mode ->
                showGameModePicker = false
                controller.openHome()
                controller.selectMode(mode)
            }
        )
    }

    val showTopLevelNavigation = state.lobbyStage == com.hienthai.fastowin.state.LobbyStage.SELECT_MODE
    val openHome = controller::openHome
    val openLeaderboardTab = controller::openLeaderboard
    val openFriendsTab = {
        if (isGuest) onUpgradeGuest() else {
            controller.openFriends()
        }
    }
    val openAccountTab = {
        if (isGuest) onUpgradeGuest() else {
            controller.openProfile()
        }
    }

    when {
                state.isFriendsOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    selected = MainTab.FRIENDS,
                    onHome = openHome,
                    onLeaderboard = openLeaderboardTab,
                    onPlay = { showGameModePicker = true },
                    onFriends = controller::openFriends,
                    onAccount = openAccountTab
                ) { contentModifier -> FriendsScreen(
                    state = state,
                    onBack = controller::closeFriends,
                    onRefresh = controller::openFriends,
                    onSendRequest = controller::sendFriendRequest,
                    onRespondRequest = controller::respondFriendRequest,
                    onInviteFriend = controller::inviteFriend,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isLeaderboardOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    selected = MainTab.LEADERBOARD,
                    onHome = openHome,
                    onLeaderboard = controller::openLeaderboard,
                    onPlay = { showGameModePicker = true },
                    onFriends = openFriendsTab,
                    onAccount = openAccountTab
                ) { contentModifier -> LeaderboardScreen(
                    state = state,
                    onBack = controller::closeLeaderboard,
                    onRefresh = controller::openLeaderboard,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isProfileOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    selected = MainTab.ACCOUNT,
                    onHome = openHome,
                    onLeaderboard = openLeaderboardTab,
                    onPlay = { showGameModePicker = true },
                    onFriends = openFriendsTab,
                    onAccount = controller::openProfile
                ) { contentModifier -> ProfileScreen(
                    state = state,
                    onBack = controller::closeProfile,
                    onRefresh = controller::openProfile,
                    onSave = controller::updateProfile,
                    canEdit = !isGuest,
                    isAccountLoading = authState.isLoading,
                    accountError = authState.error,
                    onChangePassword = onChangePassword,
                    onDeleteAccount = onDeleteAccount,
                    onLogout = onLogout,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

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
                    onOpenFriends = controller::openFriends,
                    onBackToMode = controller::backToModeSelection,
                    onLogout = onLogout,
                    isGuest = isGuest,
                    onUpgradeGuest = onUpgradeGuest,
                    sessionStartedAtMillis = sessionStartedAtMillis
                )
    }
}

@Composable
private fun TopLevelTabIfNeeded(
    enabled: Boolean,
    selected: MainTab,
    onHome: () -> Unit,
    onLeaderboard: () -> Unit,
    onPlay: () -> Unit,
    onFriends: () -> Unit,
    onAccount: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    if (!enabled) {
        content(Modifier.fillMaxSize())
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        content(Modifier.weight(1f))
        FastToWinBottomBar(
            selected = selected,
            onHome = onHome,
            onLeaderboard = onLeaderboard,
            onPlay = onPlay,
            onFriends = onFriends,
            onAccount = onAccount
        )
    }
}
