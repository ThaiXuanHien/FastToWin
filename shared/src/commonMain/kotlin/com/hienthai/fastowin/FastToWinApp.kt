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
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppPreferencesStore
import com.hienthai.fastowin.platform.GameFeedbackEffect
import com.hienthai.fastowin.platform.playFeedbackSound
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
import com.hienthai.fastowin.ui.screens.SettingsScreen
import com.hienthai.fastowin.ui.screens.TutorialScreen
import com.hienthai.fastowin.ui.screens.PracticeScreen
import com.hienthai.fastowin.ui.screens.NotificationsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.navigation.GameMode

@Composable
fun FastToWinApp(
    serverUrl: String,
    resumeTokenStore: ResumeTokenStore,
    authSessionStore: AuthSessionStore,
    preferencesStore: AppPreferencesStore,
    devicePlatform: String
) {
    val authController = remember(serverUrl, authSessionStore, devicePlatform) {
        AuthController(serverUrl, authSessionStore, resumeTokenStore, devicePlatform)
    }
    val authState by authController.state.collectAsState()
    var appPreferences by remember(preferencesStore) {
        mutableStateOf(preferencesStore.load())
    }
    val updatePreferences: (AppPreferences) -> Unit = { updated ->
        preferencesStore.save(updated)
        appPreferences = updated
    }

    DisposableEffect(authController) {
        onDispose { authController.close() }
    }

    FastToWinTheme(preferences = appPreferences) {
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
                    accessTokenProvider = authState.session?.let {
                        { forceRefresh -> authController.validAccessToken(forceRefresh) }
                    },
                    onLogout = authController::logout,
                    isGuest = authState.isGuest,
                    onUpgradeGuest = authController::openGuestUpgrade,
                    onChangePassword = authController::changePassword,
                    onDeleteAccount = authController::deleteAccount,
                    onClearAccountFeedback = authController::clearFeedback,
                    onLoadSessions = authController::loadSessions,
                    onRevokeSession = authController::revokeSession,
                    onRevokeAllSessions = authController::revokeAllSessions,
                    authState = authState,
                    onSessionExpired = { authController.expireSession() },
                    onProfileDisplayNameChanged = authController::updateStoredDisplayName,
                    appPreferences = appPreferences,
                    onPreferencesChange = updatePreferences
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
    accessTokenProvider: (suspend (forceRefresh: Boolean) -> String?)?,
    onLogout: () -> Unit,
    isGuest: Boolean,
    onUpgradeGuest: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAccountFeedback: () -> Unit,
    onLoadSessions: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAllSessions: () -> Unit,
    authState: com.hienthai.fastowin.state.AuthState,
    onSessionExpired: () -> Unit,
    onProfileDisplayNameChanged: (String) -> Unit,
    appPreferences: AppPreferences,
    onPreferencesChange: (AppPreferences) -> Unit
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
    var showPracticeModePicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(!appPreferences.hasCompletedTutorial) }
    var practiceMode by remember { mutableStateOf<GameMode?>(null) }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    state.roomInvitationPrompt?.let { invitation ->
        RoomInvitationDialog(
            invitation = invitation,
            onRespond = { accept -> controller.respondRoomInvitation(invitation.invitationId, accept) },
            onDefer = controller::dismissRoomInvitationPrompt
        )
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
    if (showPracticeModePicker) {
        GameModePickerDialog(
            title = "Chọn chế độ luyện tập",
            onDismiss = { showPracticeModePicker = false },
            onSelect = { mode ->
                showPracticeModePicker = false
                practiceMode = mode
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
                state.isFriendProfileOpen -> ProfileScreen(
                    state = state,
                    profileOverride = state.friendProfile,
                    isExternalProfile = true,
                    onBack = controller::closeFriendProfile,
                    onRefresh = controller::refreshFriendProfile,
                    onOpenMatchDetail = {},
                    onCloseMatchDetail = {},
                    onEquipCosmetics = { _, _ -> },
                    onSave = { _, _ -> },
                    canEdit = false,
                    isAccountLoading = false,
                    accountError = null,
                    accountNotice = null,
                    accountSessions = emptyList(),
                    areSessionsLoading = false,
                    onChangePassword = { _, _ -> },
                    onDeleteAccount = {},
                    onClearAccountFeedback = {},
                    onLoadSessions = {},
                    onRevokeSession = {},
                    onRevokeAllSessions = {},
                    onLogout = {},
                    showBackButton = true
                )

                state.isNotificationsOpen -> NotificationsScreen(
                    notifications = state.notifications,
                    onBack = controller::closeNotifications,
                    onOpen = controller::openNotification,
                    onDismiss = controller::dismissNotification,
                    onMarkAllRead = controller::markAllNotificationsRead,
                    onClearAll = controller::clearNotifications
                )

                state.isFriendsOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    selected = MainTab.FRIENDS,
                    friendNotificationCount = state.pendingSocialInvitationCount,
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
                    onCancelRequest = controller::cancelFriendRequest,
                    onRemoveFriend = controller::removeFriend,
                    onBlockPlayer = controller::blockPlayer,
                    onUnblockPlayer = controller::unblockPlayer,
                    onInviteFriend = controller::inviteFriend,
                    onRespondRoomInvitation = controller::respondRoomInvitation,
                    onOpenFriendProfile = controller::openFriendProfile,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isLeaderboardOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    selected = MainTab.LEADERBOARD,
                    friendNotificationCount = state.pendingSocialInvitationCount,
                    onHome = openHome,
                    onLeaderboard = controller::openLeaderboard,
                    onPlay = { showGameModePicker = true },
                    onFriends = openFriendsTab,
                    onAccount = openAccountTab
                ) { contentModifier -> LeaderboardScreen(
                    state = state,
                    onBack = controller::closeLeaderboard,
                    onRefresh = controller::openLeaderboard,
                    onOpenFriendProfile = controller::openFriendProfile,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isProfileOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    selected = MainTab.ACCOUNT,
                    friendNotificationCount = state.pendingSocialInvitationCount,
                    onHome = openHome,
                    onLeaderboard = openLeaderboardTab,
                    onPlay = { showGameModePicker = true },
                    onFriends = openFriendsTab,
                    onAccount = controller::openProfile
                ) { contentModifier -> ProfileScreen(
                    state = state,
                    onBack = controller::closeProfile,
                    onRefresh = controller::openProfile,
                    onOpenMatchDetail = controller::openMatchDetail,
                    onCloseMatchDetail = controller::closeMatchDetail,
                    onEquipCosmetics = controller::equipCosmetics,
                    onSave = controller::updateProfile,
                    canEdit = !isGuest,
                    isAccountLoading = authState.isLoading,
                    accountError = authState.error,
                    accountNotice = authState.notice,
                    accountSessions = authState.accountSessions,
                    areSessionsLoading = authState.areSessionsLoading,
                    onChangePassword = onChangePassword,
                    onDeleteAccount = onDeleteAccount,
                    onClearAccountFeedback = onClearAccountFeedback,
                    onLoadSessions = onLoadSessions,
                    onRevokeSession = onRevokeSession,
                    onRevokeAllSessions = onRevokeAllSessions,
                    onLogout = onLogout,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isGameOver -> ResultScreen(
                    state = state,
                    onRestart = controller::resetGame,
                    onRematch = controller::requestRematch,
                    onCancelRematch = controller::cancelRematch,
                    onDeclineRematch = controller::declineRematch,
                    onConnectOpponent = controller::connectWithOpponent,
                    onBlockOpponent = controller::blockOpponentAfterMatch,
                    onOpenFriendProfile = controller::openFriendProfile,
                    preferences = appPreferences
                )

                state.isMatchStarted -> GameScreen(
                    state = state,
                    onNumberClick = controller::onNumberClicked,
                    onFinish = {},
                    onOpenFriendProfile = controller::openFriendProfile,
                    onExit = controller::leaveRoom,
                    preferences = appPreferences
                )

                showTutorial -> TutorialScreen(
                    onComplete = {
                        onPreferencesChange(appPreferences.copy(hasCompletedTutorial = true))
                        showTutorial = false
                    },
                    onSkip = {
                        onPreferencesChange(appPreferences.copy(hasCompletedTutorial = true))
                        showTutorial = false
                    }
                )

                practiceMode != null -> PracticeScreen(
                    mode = checkNotNull(practiceMode),
                    preferences = appPreferences,
                    onBack = { practiceMode = null }
                )

                showSettings -> SettingsScreen(
                    preferences = appPreferences,
                    onPreferencesChange = onPreferencesChange,
                    onPreviewSound = { playFeedbackSound(GameFeedbackEffect.CORRECT) },
                    onOpenTutorial = {
                        showSettings = false
                        showTutorial = true
                    },
                    onBack = { showSettings = false }
                )

                else -> LobbyScreen(
                    state = state,
                    onModeSelected = controller::selectMode,
                    onStartMatchmaking = controller::startMatchmaking,
                    onCancelMatchmaking = controller::cancelMatchmaking,
                    onOpenRoomBrowser = controller::openRoomBrowser,
                    onCreateRoom = controller::createRoom,
                    onJoinRoom = controller::joinRoom,
                    onLeaveRoom = controller::leaveRoom,
                    onSetReady = controller::setReady,
                    onKickOpponent = controller::kickOpponent,
                    onRefreshRooms = controller::requestRoomList,
                    onOpenProfile = controller::openProfile,
                    onOpenLeaderboard = controller::openLeaderboard,
                    onOpenFriends = controller::openFriends,
                    onOpenFriendProfile = controller::openFriendProfile,
                    onBackToMode = controller::backToModeSelection,
                    onLogout = onLogout,
                    isGuest = isGuest,
                    onUpgradeGuest = onUpgradeGuest,
                    onOpenSettings = { showSettings = true },
                    onOpenNotifications = controller::openNotifications,
                    onOpenPractice = { showPracticeModePicker = true },
                    onClaimDailyCheckIn = controller::claimDailyCheckIn,
                    sessionStartedAtMillis = sessionStartedAtMillis
                )
    }
}

@Composable
private fun TopLevelTabIfNeeded(
    enabled: Boolean,
    selected: MainTab,
    friendNotificationCount: Int,
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
            friendNotificationCount = friendNotificationCount,
            onHome = onHome,
            onLeaderboard = onLeaderboard,
            onPlay = onPlay,
            onFriends = onFriends,
            onAccount = onAccount
        )
    }
}
