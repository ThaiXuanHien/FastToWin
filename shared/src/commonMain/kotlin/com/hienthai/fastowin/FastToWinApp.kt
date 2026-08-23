package com.hienthai.fastowin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.hienthai.fastowin.platform.ChallengeDeepLinkRouter
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.ClanScreen
import com.hienthai.fastowin.ui.screens.FriendsScreen
import com.hienthai.fastowin.ui.screens.RoomInvitationDialog
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ProfileSection
import com.hienthai.fastowin.ui.screens.ProfileSectionScreen
import com.hienthai.fastowin.ui.screens.LeaderboardScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.screens.FastToWinBottomBar
import com.hienthai.fastowin.ui.screens.GameModePickerDialog
import com.hienthai.fastowin.ui.screens.MainTab
import com.hienthai.fastowin.ui.screens.ShopScreen
import com.hienthai.fastowin.ui.screens.SettingsScreen
import com.hienthai.fastowin.ui.screens.TutorialScreen
import com.hienthai.fastowin.ui.screens.PracticeScreen
import com.hienthai.fastowin.ui.screens.PracticeLauncherDialog
import com.hienthai.fastowin.ui.screens.NotificationsScreen
import com.hienthai.fastowin.ui.screens.TournamentInvitationDialog
import com.hienthai.fastowin.ui.screens.TournamentScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.RoomDeepLink
import com.hienthai.fastowin.platform.RoomDeepLinkRouter
import com.hienthai.fastowin.platform.buildRoomShareText
import com.hienthai.fastowin.platform.rememberTextSharer
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.PracticeChallenge
import com.hienthai.fastowin.ui.components.FastToWinHeader

@Composable
fun FastToWinApp(
    serverUrl: String,
    resumeTokenStore: ResumeTokenStore,
    authSessionStore: AuthSessionStore,
    preferencesStore: AppPreferencesStore,
    devicePlatform: String,
    fcmToken: String? = null
) {
    val authController = remember(serverUrl, authSessionStore, devicePlatform) {
        AuthController(serverUrl, authSessionStore, resumeTokenStore, devicePlatform)
    }
    val authState by authController.state.collectAsState()
    val pendingRoomLink by RoomDeepLinkRouter.pendingLink.collectAsState()
    val pendingChallenge by ChallengeDeepLinkRouter.pendingChallenge.collectAsState()
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
                    fcmToken = fcmToken,
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
                    onPreferencesChange = updatePreferences,
                    pendingRoomLink = pendingRoomLink,
                    onRoomLinkConsumed = RoomDeepLinkRouter::consume,
                    pendingChallenge = pendingChallenge,
                    onChallengeLinkConsumed = ChallengeDeepLinkRouter::consume
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
    fcmToken: String?,
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
    onPreferencesChange: (AppPreferences) -> Unit,
    pendingRoomLink: RoomDeepLink?,
    onRoomLinkConsumed: (String) -> Unit,
    pendingChallenge: PracticeChallenge?,
    onChallengeLinkConsumed: (String) -> Unit
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
    val textSharer = rememberTextSharer()
    val sessionStartedAtMillis = remember { epochMillis() }
    var showPracticeLauncher by remember { mutableStateOf(false) }
    var showPracticeModePicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var profileSection by remember { mutableStateOf<ProfileSection?>(null) }
    var profileSectionExternal by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(!appPreferences.hasCompletedTutorial) }
    var practiceMode by remember { mutableStateOf<GameMode?>(null) }
    var practiceChallenge by remember { mutableStateOf<PracticeChallenge?>(null) }
    var challengeLinkError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(fcmToken, state.connectionStatus) {
        if (state.connectionStatus == com.hienthai.fastowin.state.ConnectionStatus.CONNECTED && fcmToken != null) {
            controller.sendFcmToken(fcmToken)
        }
    }

    LaunchedEffect(pendingRoomLink?.roomId, controller) {
        pendingRoomLink?.let { link ->
            controller.openRoomLink(link.roomId)
            onRoomLinkConsumed(link.roomId)
        }
    }

    LaunchedEffect(state.currentRoomId) {
        if (state.currentRoomId != null) {
            showPracticeLauncher = false
            showPracticeModePicker = false
            showSettings = false
            profileSection = null
            showTutorial = false
            practiceMode = null
            practiceChallenge = null
            challengeLinkError = null
        }
    }

    LaunchedEffect(
        pendingChallenge?.code,
        state.profile?.progression?.level,
        state.currentRoomId,
        state.isMatchStarted,
        state.isGameOver,
        state.isMatchmaking,
        isGuest
    ) {
        val challenge = pendingChallenge ?: return@LaunchedEffect
        if (!isGuest && state.profile == null) return@LaunchedEffect

        val playerLevel = state.profile?.progression?.level ?: 1
        challengeLinkError = when {
            state.currentRoomId != null || state.isMatchStarted || state.isGameOver || state.isMatchmaking ->
                "Hãy kết thúc hoặc rời trận hiện tại trước khi mở thử thách."
            playerLevel < challenge.mode.unlockLevel ->
                "Chế độ ${challenge.mode.title} mở khóa ở cấp ${challenge.mode.unlockLevel}."
            else -> null
        }

        if (challengeLinkError == null) {
            controller.backToModeSelection()
            controller.openHome()
            showPracticeLauncher = false
            showPracticeModePicker = false
            showSettings = false
            showTutorial = false
            practiceChallenge = challenge
            practiceMode = challenge.mode
        }
        onChallengeLinkConsumed(challenge.code)
    }

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
    state.tournamentInvitationPrompt?.let { invitation ->
        TournamentInvitationDialog(
            invitation = invitation,
            onRespond = { accept -> controller.respondTournamentInvitation(invitation.invitationId, accept) },
            onDefer = controller::dismissTournamentInvitationPrompt
        )
    }
    challengeLinkError?.let { message ->
        AlertDialog(
            onDismissRequest = { challengeLinkError = null },
            title = { Text("Không thể mở thử thách") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { challengeLinkError = null }) { Text("Đã hiểu") }
            }
        )
    }
    if (showPracticeModePicker) {
        GameModePickerDialog(
            title = "Chọn chế độ luyện tập",
            playerLevel = state.profile?.progression?.level ?: 1,
            onDismiss = { showPracticeModePicker = false },
            onSelect = { mode ->
                showPracticeModePicker = false
                practiceChallenge = null
                practiceMode = mode
            }
        )
    }
    if (showPracticeLauncher) {
        PracticeLauncherDialog(
            onDismiss = { showPracticeLauncher = false },
            onStartNew = {
                showPracticeLauncher = false
                showPracticeModePicker = true
            },
            onOpenChallenge = { challenge ->
                showPracticeLauncher = false
                practiceChallenge = challenge
                practiceMode = challenge.mode
            },
            playerLevel = state.profile?.progression?.level ?: 1
        )
    }

    val showTopLevelNavigation = state.lobbyStage == com.hienthai.fastowin.state.LobbyStage.SELECT_MODE
    val openHome = {
        profileSection = null
        controller.openHome()
    }
    val openLeaderboardTab = {
        profileSection = null
        controller.openLeaderboard()
    }
    val openFriendsTab = {
        if (isGuest) onUpgradeGuest() else {
            profileSection = null
            controller.openFriends()
        }
    }
    val openAccountTab = {
        if (isGuest) onUpgradeGuest() else {
            profileSection = null
            controller.openProfile()
        }
    }
    val openClanTab = {
        if (isGuest) onUpgradeGuest() else {
            profileSection = null
            controller.openClan()
        }
    }
    val openSettingsTab = { showSettings = true }
    val finishTutorial = {
        onPreferencesChange(appPreferences.copy(hasCompletedTutorial = true))
        showTutorial = false
    }

    when {
                state.isNotificationsOpen -> NotificationsScreen(
                    notifications = state.notifications,
                    onBack = controller::closeNotifications,
                    onOpen = controller::openNotification,
                    onDismiss = controller::dismissNotification,
                    onMarkAllRead = controller::markAllNotificationsRead,
                    onClearAll = controller::clearNotifications,
                    gold = state.profile?.progression?.gold ?: 0,
                    gems = state.profile?.progression?.gems ?: 0
                )

                showTutorial -> TutorialScreen(
                    onComplete = finishTutorial,
                    onSkip = finishTutorial
                )

                showSettings -> SettingsScreen(
                    preferences = appPreferences,
                    onPreferencesChange = onPreferencesChange,
                    onPreviewSound = { playFeedbackSound(GameFeedbackEffect.CORRECT) },
                    onOpenTutorial = {
                        showTutorial = true
                    },
                    onBack = { showSettings = false },
                    gold = state.profile?.progression?.gold ?: 0,
                    gems = state.profile?.progression?.gems ?: 0,
                    unreadNotifications = state.unreadNotificationCount,
                    onOpenNotifications = controller::openNotifications
                )

                state.isFriendProfileOpen && profileSection == null && !state.isNotificationsOpen -> ProfileScreen(
                    serverUrl = serverUrl,
                    state = state,
                    profileOverride = state.friendProfile,
                    isExternalProfile = true,
                    onBack = controller::closeFriendProfile,
                    onRefresh = controller::refreshFriendProfile,
                    onOpenMatchDetail = {},
                    onCloseMatchDetail = {},
                    onEquipCosmetics = { _, _ -> },
                    onClaimMissionReward = {},
                    onSave = { _, _ -> },
                    onUploadAvatar = {},
                    onInviteToClan = controller::inviteToClan,
                    onOpenNotifications = controller::openNotifications,
                    onOpenSection = { section ->
                        profileSectionExternal = true
                        profileSection = section
                    },
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

                profileSection != null -> {
                    val sectionProfile = if (profileSectionExternal) state.friendProfile else state.profile
                    if (sectionProfile == null) {
                        profileSection = null
                    } else {
                        ProfileSectionScreen(
                            state = state,
                            profile = sectionProfile,
                            section = checkNotNull(profileSection),
                            isExternalProfile = profileSectionExternal,
                            canEdit = !profileSectionExternal && !isGuest,
                            onBack = { profileSection = null },
                            onRefresh = if (profileSectionExternal) controller::refreshFriendProfile else controller::openProfile,
                            onOpenMatchDetail = controller::openMatchDetail,
                            onCloseMatchDetail = controller::closeMatchDetail,
                            onEquipCosmetics = controller::equipCosmetics,
                            onClaimMissionReward = controller::claimMissionReward,
                            onSave = controller::updateProfile,
                            onOpenNotifications = controller::openNotifications
                        )
                    }
                }

                state.isTournamentOpen -> TournamentScreen(
                    state = state,
                    onBack = controller::closeTournament,
                    onCreate = controller::createTournament,
                    onInvite = controller::inviteTournamentPlayer,
                    onRespondInvitation = controller::respondTournamentInvitation,
                    onStart = controller::startTournament,
                    onLeave = controller::leaveTournament,
                    onOpenFriendProfile = controller::openFriendProfile
                )

                state.isFriendsOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    state = state,
                    selected = MainTab.FRIENDS,
                    friendNotificationCount = state.pendingSocialInvitationCount,
                    onHome = openHome,
                    onLeaderboard = openLeaderboardTab,
                    onClan = openClanTab,
                    onFriends = controller::openFriends,
                    onAccount = openAccountTab,
                    onNotifications = controller::openNotifications
                ) { contentModifier -> FriendsScreen(
                    state = state,
                    onBack = if (showTopLevelNavigation) openHome else controller::closeFriends,
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
                    onOpenNotifications = controller::openNotifications,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isLeaderboardOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    state = state,
                    selected = MainTab.LEADERBOARD,
                    friendNotificationCount = state.pendingSocialInvitationCount,
                    onHome = openHome,
                    onLeaderboard = controller::openLeaderboard,
                    onClan = openClanTab,
                    onFriends = openFriendsTab,
                    onAccount = openAccountTab,
                    onNotifications = controller::openNotifications
                ) { contentModifier -> LeaderboardScreen(
                    state = state,
                    onBack = if (showTopLevelNavigation) openHome else controller::closeLeaderboard,
                    onRefresh = controller::openLeaderboard,
                    onOpenFriendProfile = controller::openFriendProfile,
                    onOpenNotifications = controller::openNotifications,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isProfileOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    state = state,
                    selected = MainTab.ACCOUNT,
                    friendNotificationCount = state.pendingSocialInvitationCount,
                    onHome = openHome,
                    onLeaderboard = openLeaderboardTab,
                    onClan = openClanTab,
                    onFriends = openFriendsTab,
                    onAccount = controller::openProfile,
                    onNotifications = controller::openNotifications
                ) { contentModifier -> ProfileScreen(
                    serverUrl = serverUrl,
                    state = state,
                    onBack = if (showTopLevelNavigation) openHome else controller::closeProfile,
                    onRefresh = controller::openProfile,
                    onOpenMatchDetail = controller::openMatchDetail,
                    onCloseMatchDetail = controller::closeMatchDetail,
                    onEquipCosmetics = controller::equipCosmetics,
                    onClaimMissionReward = controller::claimMissionReward,
                    onSave = controller::updateProfile,
                    onUploadAvatar = controller::updateAvatar,
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
                    sessionStartedAtMillis = sessionStartedAtMillis,
                    onOpenNotifications = controller::openNotifications,
                    onOpenSettings = openSettingsTab,
                    onOpenSection = { section ->
                        profileSectionExternal = false
                        profileSection = section
                    },
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }
                
                state.isShopOpen -> ShopScreen(
                    progression = state.profile?.progression,
                    onBuy = controller::buyCosmetic,
                    onEquip = controller::equipCosmetic,
                    onClose = controller::closeShop,
                    unreadNotifications = state.unreadNotificationCount,
                    onNotifications = controller::openNotifications
                )

                state.isClanOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    state = state,
                    selected = MainTab.CLAN,
                    friendNotificationCount = state.pendingSocialInvitationCount,
                    onHome = openHome,
                    onLeaderboard = openLeaderboardTab,
                    onClan = controller::openClan,
                    onFriends = openFriendsTab,
                    onAccount = openAccountTab,
                    onNotifications = controller::openNotifications
                ) { contentModifier -> ClanScreen(
                     serverUrl = serverUrl,
                     currentUserId = state.profile?.userId,
                     myClanId = state.profile?.clanId,
                     clanList = state.clanList,
                     pendingJoinClanIds = state.pendingClanJoinIds,
                     currentClan = state.currentClan,
                     notice = state.clanNotice ?: state.error,
                    onCreateClan = controller::createClan,
                    onJoinClan = controller::joinClan,
                    onLeaveClan = controller::leaveClan,
                    onSearch = controller::searchClan,
                     onKickMember = controller::kickClanMember,
                     onRespondJoinRequest = controller::respondClanJoinRequest,
                    onUpdateLogo = controller::updateClanLogo,
                    onClaimQuest = controller::claimClanQuestReward,
                    onViewClan = controller::viewClan,
                    onBack = if (showTopLevelNavigation) openHome else controller::closeClan,
                    gold = state.profile?.progression?.gold ?: 0,
                    gems = state.profile?.progression?.gems ?: 0,
                    unreadNotifications = state.unreadNotificationCount,
                    onOpenNotifications = controller::openNotifications,
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }

                state.isGameOver -> ResultScreen(
                    state = state,
                    onRestart = controller::resetGame,
                    onBack = if (state.isTournamentMatch) {
                        controller::openTournamentAfterMatch
                    } else {
                        controller::resetGame
                    },
                    onRematch = controller::requestRematch,
                    onCancelRematch = controller::cancelRematch,
                    onDeclineRematch = controller::declineRematch,
                    onConnectOpponent = controller::connectWithOpponent,
                    onBlockOpponent = controller::blockOpponentAfterMatch,
                    onOpenFriendProfile = controller::openFriendProfile,
                    onOpenTournament = controller::openTournamentAfterMatch,
                    preferences = appPreferences
                )

                state.isMatchStarted -> GameScreen(
                    state = state,
                    onNumberClick = controller::onNumberClicked,
                    onFinish = {},
                    onOpenFriendProfile = controller::openFriendProfile,
                    onExit = controller::leaveRoom,
                    allowExit = !state.isTournamentMatch,
                    preferences = appPreferences
                )

                practiceMode != null -> PracticeScreen(
                    mode = checkNotNull(practiceMode),
                    challenge = practiceChallenge,
                    preferences = appPreferences,
                    onBack = {
                        practiceMode = null
                        practiceChallenge = null
                    }
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
                    onOpenNotifications = controller::openNotifications,
                    onOpenClan = controller::openClan,
                    onOpenPractice = { showPracticeLauncher = true },
                    onOpenTournament = controller::openTournament,
                    onOpenShop = controller::openShop,
                    onShareRoom = { roomId, roomName ->
                        textSharer.share(buildRoomShareText(roomName, roomId), "Chia sẻ phòng")
                    },
                    onResolveRoomLink = controller::resolvePendingRoomLink,
                    onClaimDailyCheckIn = controller::claimDailyCheckIn
                )
    }
}

@Composable
private fun TopLevelTabIfNeeded(
    enabled: Boolean,
    state: com.hienthai.fastowin.state.GameState,
    selected: MainTab,
    friendNotificationCount: Int,
    onHome: () -> Unit,
    onLeaderboard: () -> Unit,
    onClan: () -> Unit,
    onFriends: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    if (!enabled) {
        content(Modifier.fillMaxSize())
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        FastToWinHeader(
            title = when (selected) {
                MainTab.HOME -> ""
                MainTab.LEADERBOARD -> "Xếp hạng"
                MainTab.CLAN -> "Bang hội"
                MainTab.FRIENDS -> "Bạn bè"
                MainTab.ACCOUNT -> "Tài khoản"
            },
            gold = state.profile?.progression?.gold ?: 0,
            gems = state.profile?.progression?.gems ?: 0,
            unreadNotifications = state.unreadNotificationCount,
            onNotifications = onNotifications,
            onBack = if (selected == MainTab.HOME) null else onHome
        )
        content(Modifier.weight(1f))
        FastToWinBottomBar(
            selected = selected,
            friendNotificationCount = friendNotificationCount,
            onHome = onHome,
            onLeaderboard = onLeaderboard,
            onClan = onClan,
            onFriends = onFriends,
            onAccount = onAccount
        )
    }
}
