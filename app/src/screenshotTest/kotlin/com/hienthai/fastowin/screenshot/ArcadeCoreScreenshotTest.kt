package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ResultScreen

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun home() = ArcadeScreenshotFrame {
    LobbyScreen(state = ArcadeScreenshotFixtures.homeState(), callbacks = lobbyCallbacks())
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun roomBrowser() = ArcadeScreenshotFrame {
    LobbyScreen(state = ArcadeScreenshotFixtures.roomBrowserState(), callbacks = lobbyCallbacks())
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun activeGame() = ArcadeScreenshotFrame {
    GameScreen(
        state = ArcadeScreenshotFixtures.activeGameState(),
        onNumberClick = {},
        onFinish = {},
        preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun rankedWinResult() = ArcadeScreenshotFrame {
    ResultScreen(
        state = ArcadeScreenshotFixtures.rankedWinState(),
        onRestart = {},
        onBack = {},
        onRematch = {},
        onCancelRematch = {},
        onDeclineRematch = {},
        onConnectOpponent = {},
        onBlockOpponent = {},
        preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun profileOverview() = ArcadeScreenshotFrame {
    ProfileScreen(
        serverUrl = "",
        state = ArcadeScreenshotFixtures.homeState(),
        onBack = {},
        onRefresh = {},
        onOpenMatchDetail = {},
        onCloseMatchDetail = {},
        onEquipCosmetics = { _, _ -> },
        onClaimMissionReward = {},
        onSave = { _, _ -> },
        onUploadAvatar = {},
        canEdit = true,
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
        showBackButton = true,
        imagePicker = { _, content -> content({}) }
    )
}

private data class LobbyCallbacks(
    val unused: Unit = Unit
)

@Composable
private fun LobbyScreen(
    state: com.hienthai.fastowin.state.GameState,
    callbacks: LobbyCallbacks
) {
    callbacks.unused
    LobbyScreen(
        state = state,
        onModeSelected = {},
        onStartMatchmaking = { _, _ -> },
        onCancelMatchmaking = {},
        onOpenRoomBrowser = {},
        onCreateRoom = { _, _, _, _ -> },
        onJoinRoom = { _, _ -> },
        onLeaveRoom = {},
        onSetReady = {},
        onKickOpponent = {},
        onRefreshRooms = {},
        onOpenProfile = {},
        onOpenLeaderboard = {},
        onOpenFriends = {},
        onOpenFriendProfile = {},
        onBackToMode = {},
        onLogout = {},
        isGuest = false,
        onUpgradeGuest = {},
        onOpenNotifications = {},
        onOpenClan = {},
        onOpenPractice = {},
        onOpenTournament = {},
        onOpenShop = {},
        onShareRoom = { _, _ -> Result.success(Unit) },
        onResolveRoomLink = {},
        onClaimDailyCheckIn = {}
    )
}

private fun lobbyCallbacks() = LobbyCallbacks()
