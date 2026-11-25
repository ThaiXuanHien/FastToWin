package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.state.AuthStage
import com.hienthai.fastowin.state.AuthState
import com.hienthai.fastowin.ui.screens.AuthScreen
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.screens.ResultScreen

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun login() = ArcadeScreenshotFrame {
    AuthScreen(
        state = AuthState(stage = AuthStage.LOGIN),
        onOpenLogin = {},
        onOpenRegister = {},
        onOpenPasswordReset = {},
        onPlayAsGuest = {},
        onLogin = { _, _ -> },
        onRegister = { _, _, _, _ -> },
        onUpgradeGuest = { _, _ -> },
        onRequestPasswordReset = {},
        onConfirmPasswordReset = { _, _, _ -> },
        onRequestEmailVerification = {},
        onConfirmEmailVerification = {},
        onBack = {},
        onCancelUpgrade = {},
        onCancelEmailVerification = {}
    )
}

@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun home() = ArcadeScreenshotFrame {
    LobbyScreen(state = ArcadeScreenshotFixtures.homeState(), callbacks = lobbyCallbacks())
}

@PreviewTest
@Preview(name = "calendar_footer_and_missions", widthDp = 390, heightDp = 1500)
@Composable
fun homeCheckInDetails() = ArcadeScreenshotFrame {
    LobbyScreen(state = ArcadeScreenshotFixtures.homeState(), callbacks = lobbyCallbacks(), claimableMissionCount = 3)
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
        onSave = { _, _, _ -> },
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
    callbacks: LobbyCallbacks,
    claimableMissionCount: Int = 0
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
        onClaimDailyCheckIn = {},
        claimableMissionCount = claimableMissionCount
    )
}

private fun lobbyCallbacks() = LobbyCallbacks()

/** Màn Tìm trận A1 (xếp hạng). */
@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun matchmakingRanked() = ArcadeScreenshotFrame {
    LobbyScreen(
        state = ArcadeScreenshotFixtures.homeState().copy(
            lobbyStage = com.hienthai.fastowin.state.LobbyStage.MATCHMAKING,
            matchType = com.hienthai.fastowin.protocol.MatchType.RANKED
        ),
        callbacks = lobbyCallbacks()
    )
}

/** Màn Chọn chế độ A1 (tab Chơi). */
@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun modeSelect() = ArcadeScreenshotFrame {
    com.hienthai.fastowin.ui.components.ArcadeBackdrop(androidx.compose.ui.Modifier.fillMaxSize()) {
        com.hienthai.fastowin.ui.screens.ModeSelectContent(
            playerLevel = 8,
            quota = ArcadeScreenshotFixtures.homeState().playQuota,
            onPlay = { _, _ -> },
            onPractice = {},
            onQuotaExhausted = {},
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp)
        )
    }
}

private fun roomWaitingState(ready: Boolean) = ArcadeScreenshotFixtures.homeState().copy(
    lobbyStage = com.hienthai.fastowin.state.LobbyStage.ROOM_WAITING,
    currentRoomId = "4f7k2a91-0000-4000-8000-000000000000",
    currentRoomName = "Phòng của Minh",
    isRoomHost = false,
    hasOpponent = true,
    latencyMillis = 38,
    gameMode = com.hienthai.fastowin.navigation.GameMode.ORDER,
    matchType = com.hienthai.fastowin.protocol.MatchType.CASUAL,
    player = com.hienthai.fastowin.state.PlayerState("HienTX", id = "player-1", avatarId = "male", frameId = "frame_diamond", isReady = ready),
    opponent = com.hienthai.fastowin.state.PlayerState("Minh", id = "player-2", avatarId = "female", frameId = "frame_emperor", isReady = true)
)

/** Phòng chờ A1 (khách, đối thủ đã sẵn sàng). */
@PreviewTest
@ArcadeScreenshotDevices
@Composable
fun roomWaiting() = ArcadeScreenshotFrame {
    LobbyScreen(state = roomWaitingState(ready = false), callbacks = lobbyCallbacks())
}

/** Đếm ngược vào trận. */
@PreviewTest
@Preview(name = "large_phone", widthDp = 430, heightDp = 932)
@Composable
fun matchCountdown() = ArcadeScreenshotFrame {
    LobbyScreen(
        state = roomWaitingState(ready = true).copy(lobbyStage = com.hienthai.fastowin.state.LobbyStage.MATCHED, countdown = 3,
            matchType = com.hienthai.fastowin.protocol.MatchType.RANKED),
        callbacks = lobbyCallbacks()
    )
}

/** Hộp thoại nhập mã phòng. */
@PreviewTest
@Preview(name = "large_phone", widthDp = 430, heightDp = 932)
@Composable
fun joinByCodeDialog() = ArcadeScreenshotFrame {
    com.hienthai.fastowin.ui.screens.JoinByCodeDialog(rooms = emptyList(), onDismiss = {}, onSelect = {})
}

/** Hộp thoại vào phòng riêng (mật khẩu). */
@PreviewTest
@Preview(name = "large_phone", widthDp = 430, heightDp = 932)
@Composable
fun joinPrivateRoomDialog() = ArcadeScreenshotFrame {
    com.hienthai.fastowin.ui.screens.JoinRoomDialog(
        room = com.hienthai.fastowin.state.AvailableRoom("room-9", "Team lớp 12A", "Lan", com.hienthai.fastowin.navigation.GameMode.ORDER,
            com.hienthai.fastowin.protocol.MatchType.CASUAL, true, 0L),
        onDismiss = {}, onJoin = {}
    )
}

/** Bảng tạo phòng mới. */
@PreviewTest
@Preview(name = "large_phone", widthDp = 430, heightDp = 932)
@Composable
fun createRoomSheet() = ArcadeScreenshotFrame {
    com.hienthai.fastowin.ui.screens.CreateRoomSheet(isLoading = false, defaultRoomName = "Phòng của HienTX", playerLevel = 8,
        onDismiss = {}, onCreate = { _, _, _, _ -> })
}
