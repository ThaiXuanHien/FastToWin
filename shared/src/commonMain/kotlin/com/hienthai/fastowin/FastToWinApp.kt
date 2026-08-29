package com.hienthai.fastowin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hienthai.fastowin.state.GameController
import com.hienthai.fastowin.state.AuthController
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.data.network.ServiceStatusClient
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
import com.hienthai.fastowin.ui.screens.SeasonHistoryScreen
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
import com.hienthai.fastowin.ui.screens.MaintenanceScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.RoomDeepLink
import com.hienthai.fastowin.platform.RoomDeepLinkRouter
import com.hienthai.fastowin.platform.buildRoomShareText
import com.hienthai.fastowin.platform.rememberTextSharer
import com.hienthai.fastowin.platform.rememberStoreBillingGateway
import com.hienthai.fastowin.platform.PlatformStorePurchase
import com.hienthai.fastowin.protocol.StorePurchaseStatus
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.PracticeChallenge
import com.hienthai.fastowin.state.createPracticeChallenge
import com.hienthai.fastowin.state.parsePracticeChallenge
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.SeasonRewardSummaryDialog
import kotlinx.coroutines.delay

@Composable
fun FastToWinApp(
    serverUrl: String,
    resumeTokenStore: ResumeTokenStore,
    authSessionStore: AuthSessionStore,
    preferencesStore: AppPreferencesStore,
    devicePlatform: String,
    fcmToken: String? = null
) {
    var restoreGuestSession by rememberSaveable { mutableStateOf(false) }
    val serviceStatusClient = remember(serverUrl) { ServiceStatusClient(serverUrl) }
    var serviceStatus by remember(serverUrl) {
        mutableStateOf<com.hienthai.fastowin.protocol.ServiceStatusResponse?>(null)
    }
    val authController = remember(serverUrl, authSessionStore, devicePlatform) {
        AuthController(
            serverUrl = serverUrl,
            store = authSessionStore,
            resumeTokenStore = resumeTokenStore,
            devicePlatform = devicePlatform,
            initialGuestSession = restoreGuestSession
        )
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

    DisposableEffect(serviceStatusClient) {
        onDispose { serviceStatusClient.close() }
    }

    LaunchedEffect(serviceStatusClient) {
        while (true) {
            serviceStatusClient.fetchOrNull()?.let { serviceStatus = it }
            val pollSeconds = serviceStatus?.pollAfterSeconds?.coerceIn(15, 300) ?: 30
            delay(pollSeconds * 1_000L)
        }
    }

    LaunchedEffect(authState.isGuest, authState.session, authState.stage) {
        if (authState.session != null || authState.stage == AuthStage.WELCOME) {
            restoreGuestSession = false
        }
    }

    FastToWinTheme(preferences = appPreferences) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            if (serviceStatus?.maintenance == true) {
                ArcadeBackdrop(modifier = Modifier.fillMaxSize()) {
                    MaintenanceScreen(message = serviceStatus?.message)
                }
            } else if (authState.stage != AuthStage.PLAYING) {
                AuthScreen(
                    state = authState,
                    onOpenLogin = authController::openLogin,
                    onOpenRegister = authController::openRegister,
                    onOpenPasswordReset = authController::openPasswordReset,
                    onPlayAsGuest = {
                        restoreGuestSession = true
                        authController.playAsGuest()
                    },
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
                    onLogout = {
                        restoreGuestSession = false
                        authController.logout()
                    },
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
    val storeBillingGateway = rememberStoreBillingGateway()
    val storeBillingState by storeBillingGateway.state.collectAsState()
    val pendingStorePurchases = remember { mutableStateMapOf<String, PlatformStorePurchase>() }
    val textSharer = rememberTextSharer()
    val sessionStartedAtMillis = rememberSaveable { epochMillis() }
    var showPracticeLauncher by rememberSaveable { mutableStateOf(false) }
    var showPracticeModePicker by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSeasonHistory by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(storeBillingGateway) {
        onDispose { storeBillingGateway.close() }
    }

    LaunchedEffect(storeBillingGateway) {
        storeBillingGateway.purchases.collect { purchase ->
            pendingStorePurchases[purchase.requestId] = purchase
            controller.verifyStorePurchase(
                requestId = purchase.requestId,
                store = purchase.store,
                productId = purchase.productId,
                purchaseToken = purchase.purchaseToken
            )
        }
    }

    LaunchedEffect(state.gemStorePackages, state.storeSandboxEnabled) {
        if (state.gemStorePackages.isNotEmpty()) {
            storeBillingGateway.connect(state.gemStorePackages, state.storeSandboxEnabled)
        }
    }

    LaunchedEffect(state.storePurchaseResult) {
        val result = state.storePurchaseResult ?: return@LaunchedEffect
        val purchase = pendingStorePurchases.remove(result.requestId)
        if (purchase != null && result.status in setOf(
                StorePurchaseStatus.GRANTED,
                StorePurchaseStatus.ALREADY_GRANTED
            )
        ) {
            storeBillingGateway.finishPurchase(purchase.purchaseToken)
        }
        controller.clearStorePurchaseResult()
        pendingStorePurchases.values.firstOrNull()?.let { next ->
            controller.verifyStorePurchase(
                requestId = next.requestId,
                store = next.store,
                productId = next.productId,
                purchaseToken = next.purchaseToken
            )
        }
    }
    var profileSection by remember { mutableStateOf<ProfileSection?>(null) }
    var profileSectionExternal by remember { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(!appPreferences.hasCompletedTutorial) }
    var savedPracticeRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val practiceChallenge = savedPracticeRoute?.let(::parsePracticeChallenge)
    val practiceMode = practiceChallenge?.mode
    var challengeLinkError by rememberSaveable { mutableStateOf<String?>(null) }
    val screenStateHolder = rememberSaveableStateHolder()
    val activePracticeStateKey = practiceChallenge?.code?.let { code -> "practice:$code" }
    val clearPracticeSession = {
        activePracticeStateKey?.let(screenStateHolder::removeState)
        savedPracticeRoute = null
    }
    
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
            showSeasonHistory = false
            profileSection = null
            showTutorial = false
            clearPracticeSession()
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
            showSeasonHistory = false
            showTutorial = false
            clearPracticeSession()
            savedPracticeRoute = challenge.code
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
                val now = epochMillis()
                clearPracticeSession()
                savedPracticeRoute = createPracticeChallenge(
                    mode = mode,
                    seed = (now xor (now ushr 32)).toInt()
                ).code
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
                clearPracticeSession()
                savedPracticeRoute = challenge.code
            },
            playerLevel = state.profile?.progression?.level ?: 1
        )
    }

    val pendingSeasonReward = state.profile?.progression?.latestSeasonReward?.takeIf {
        !it.acknowledged && it.seasonNumber > 0
    }
    val canShowSeasonSummary = pendingSeasonReward != null &&
        state.currentRoomId == null &&
        !state.isMatchmaking &&
        state.roomInvitationPrompt == null &&
        state.tournamentInvitationPrompt == null &&
        !showTutorial &&
        !showSettings &&
        !showSeasonHistory &&
        !showPracticeLauncher &&
        !showPracticeModePicker &&
        practiceMode == null
    pendingSeasonReward?.takeIf { canShowSeasonSummary }?.let { receipt ->
        SeasonRewardSummaryDialog(
            receipt = receipt,
            onAcknowledge = {
                controller.acknowledgeSeasonReward(receipt.seasonNumber)
            }
        )
    }

    val showTopLevelNavigation = state.lobbyStage == com.hienthai.fastowin.state.LobbyStage.SELECT_MODE
    val openHome = {
        profileSection = null
        showSeasonHistory = false
        controller.openHome()
    }
    val openLeaderboardTab = {
        profileSection = null
        showSeasonHistory = false
        controller.backToModeSelection()
        controller.openLeaderboard()
    }
    val openRoomsTab = {
        profileSection = null
        showSeasonHistory = false
        controller.openRoomBrowser(state.profile?.displayName ?: state.player.name)
    }
    val openFriendsTab = {
        if (isGuest) onUpgradeGuest() else {
            profileSection = null
            showSeasonHistory = false
            controller.openFriends()
        }
    }
    val openAccountTab = {
        if (isGuest) onUpgradeGuest() else {
            profileSection = null
            showSeasonHistory = false
            controller.backToModeSelection()
            controller.openProfile()
        }
    }
    val openClanTab = {
        if (isGuest) onUpgradeGuest() else {
            profileSection = null
            showSeasonHistory = false
            controller.backToModeSelection()
            controller.openClan()
        }
    }
    val openSettingsTab = { showSettings = true }
    val finishTutorial = {
        onPreferencesChange(appPreferences.copy(hasCompletedTutorial = true))
        showTutorial = false
    }

    val screenStateKey = when {
        state.isNotificationsOpen -> "notifications"
        showTutorial -> "tutorial"
        showSettings -> "settings"
        state.isFriendProfileOpen && profileSection == null ->
            "friend_profile:${state.friendProfile?.userId.orEmpty()}"
        profileSection != null ->
            "profile_section:${if (profileSectionExternal) state.friendProfile?.userId.orEmpty() else "self"}:${profileSection?.name}"
        state.isTournamentOpen -> "tournament"
        showSeasonHistory -> "season_history"
        state.isFriendsOpen -> "friends"
        state.isLeaderboardOpen -> "leaderboard"
        state.isProfileOpen -> "profile"
        state.isShopOpen -> "shop"
        state.isClanOpen -> "clan"
        state.isGameOver -> "result"
        state.isMatchStarted -> "game"
        activePracticeStateKey != null -> activePracticeStateKey
        else -> "lobby"
    }
    screenStateHolder.SaveableStateProvider(screenStateKey) {
        ArcadeBackdrop(modifier = Modifier.fillMaxSize()) {
            when {
                state.isNotificationsOpen -> NotificationsScreen(
                    notifications = state.notifications,
                    onBack = controller::closeNotifications,
                    onOpen = controller::openNotification,
                    onDismiss = controller::dismissNotification,
                    onMarkAllRead = controller::markAllNotificationsRead,
                    onClearAll = controller::clearNotifications
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
                            onRefresh = when {
                                profileSectionExternal -> controller::refreshFriendProfile
                                profileSection == ProfileSection.WALLET -> controller::refreshWalletHistory
                                else -> controller::openProfile
                            },
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
                    onOpenFriendProfile = controller::openFriendProfile,
                    onOpenNotifications = controller::openNotifications
                )

                showSeasonHistory -> SeasonHistoryScreen(
                    state = state,
                    onBack = {
                        showSeasonHistory = false
                        if (!state.isLeaderboardOpen) controller.openLeaderboard()
                    },
                    onRefresh = controller::refreshProfile,
                    onOpenNotifications = controller::openNotifications
                )

                state.isFriendsOpen -> FriendsScreen(
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
                    onOpenNotifications = controller::openNotifications,
                    showBackButton = true,
                    modifier = Modifier.fillMaxSize()
                )

                state.isLeaderboardOpen -> TopLevelTabIfNeeded(
                    enabled = showTopLevelNavigation,
                    state = state,
                    selected = MainTab.LEADERBOARD,
                    friendNotificationCount = state.pendingSocialInvitationCount,
                    onHome = openHome,
                    onRooms = openRoomsTab,
                    onLeaderboard = controller::openLeaderboard,
                    onClan = openClanTab,
                    onAccount = openAccountTab,
                    onNotifications = controller::openNotifications
                ) { contentModifier -> LeaderboardScreen(
                    state = state,
                    onBack = if (showTopLevelNavigation) openHome else controller::closeLeaderboard,
                    onRefresh = controller::openLeaderboard,
                    onOpenFriendProfile = controller::openFriendProfile,
                    onOpenSeasonHistory = { showSeasonHistory = true },
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
                    onRooms = openRoomsTab,
                    onLeaderboard = openLeaderboardTab,
                    onClan = openClanTab,
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
                        if (section == ProfileSection.WALLET) controller.refreshWalletHistory()
                    },
                    showBackButton = !showTopLevelNavigation,
                    modifier = contentModifier
                ) }
                
                state.isShopOpen -> ShopScreen(
                    progression = state.profile?.progression,
                    gemPackages = state.gemStorePackages,
                    billingState = storeBillingState,
                    isCatalogLoading = state.isGemStoreCatalogLoading,
                    isAccount = !isGuest,
                    onBuyGems = { productId ->
                        storeBillingGateway.purchase(productId, state.profile?.userId)
                    },
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
                    onRooms = openRoomsTab,
                    onLeaderboard = openLeaderboardTab,
                    onClan = controller::openClan,
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
                    onRestart = controller::leaveRoom,
                    onBack = if (state.isTournamentMatch) {
                        controller::openTournamentAfterMatch
                    } else {
                        controller::leaveRoom
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
                    onSendEmoji = controller::sendEmoji,
                    preferences = appPreferences
                )

                practiceMode != null -> PracticeScreen(
                    mode = checkNotNull(practiceMode),
                    challenge = practiceChallenge,
                    preferences = appPreferences,
                    onBack = {
                        clearPracticeSession()
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
                    onOpenProfile = openAccountTab,
                    onOpenLeaderboard = openLeaderboardTab,
                    onOpenFriends = controller::openFriends,
                    onOpenFriendProfile = controller::openFriendProfile,
                    onBackToMode = controller::backToModeSelection,
                    onLogout = onLogout,
                    isGuest = isGuest,
                    onUpgradeGuest = onUpgradeGuest,
                    onOpenNotifications = controller::openNotifications,
                    onOpenClan = openClanTab,
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
    }
}

@Composable
private fun TopLevelTabIfNeeded(
    enabled: Boolean,
    state: com.hienthai.fastowin.state.GameState,
    selected: MainTab,
    friendNotificationCount: Int,
    onHome: () -> Unit,
    onRooms: () -> Unit,
    onLeaderboard: () -> Unit,
    onClan: () -> Unit,
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
                MainTab.ROOMS -> "Phòng"
                MainTab.LEADERBOARD -> "Xếp hạng"
                MainTab.CLAN -> "Bang hội"
                MainTab.ACCOUNT -> "Tài khoản"
            },
            gold = state.profile?.progression?.gold ?: 0,
            gems = state.profile?.progression?.gems ?: 0,
            unreadNotifications = state.unreadNotificationCount,
            onNotifications = onNotifications,
            onBack = null
        )
        content(Modifier.weight(1f))
        FastToWinBottomBar(
            selected = selected,
            friendNotificationCount = friendNotificationCount,
            onHome = onHome,
            onRooms = onRooms,
            onLeaderboard = onLeaderboard,
            onClan = onClan,
            onAccount = onAccount
        )
    }
}
