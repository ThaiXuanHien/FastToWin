package com.hienthai.fastowin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.GiftState
import com.hienthai.fastowin.protocol.MailboxGift
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.RewardEventPublicView
import com.hienthai.fastowin.protocol.RewardEventState
import com.hienthai.fastowin.state.AvailableRoom
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.GiftMailboxState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.state.RewardEventLoad
import com.hienthai.fastowin.state.RewardEventUiState
import com.hienthai.fastowin.state.createPracticeChallenge
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.GiftMailboxScreen
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.PracticeScreen
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.screens.RewardEventDetailsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test

class LocalizedGameplayUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeUsesEnglishCopy() {
        setLocalizedContent(AppLanguage.ENGLISH) {
            LocalizedLobbyScreen(state = lobbyState(LobbyStage.SELECT_MODE), isGuest = false)
        }

        composeRule.onNodeWithText("PLAY NOW").assertIsDisplayed()
        // A1: trang chủ dùng các ô lối tắt thay cho mục "Explore".
        composeRule.onNodeWithText("Tournament").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun roomBrowserUsesVietnameseCopy() {
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            LocalizedLobbyScreen(state = lobbyState(LobbyStage.ROOM_BROWSER), isGuest = false)
        }

        // A1: danh sách phòng mở đầu bằng hàng nút Tạo phòng / Nhập mã.
        composeRule.onNodeWithText("Nhập mã").assertIsDisplayed()
        composeRule.onNodeWithText("Tạo phòng", ignoreCase = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rewardEventListAndDetailShowTheConfiguredTimeZone() {
        val event = RewardEventPublicView(
            "reward", 1, RewardEventState.SCHEDULED, "Ngày đặc biệt", "Special day",
            "Nhân đôi Vàng và XP", "Double Gold and XP", "Asia/Ho_Chi_Minh",
            Instant.parse("2026-11-22T10:44:44.061Z").toEpochMilliseconds(),
            Instant.parse("2026-11-29T10:44:48.374Z").toEpochMilliseconds(),
            2, 2, setOf(MatchType.CASUAL, MatchType.RANKED)
        )
        val selected = mutableStateOf<RewardEventPublicView?>(null)
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            RewardEventDetailsScreen(
                state = RewardEventUiState(load = RewardEventLoad.READY, items = listOf(event), selected = selected.value),
                onBack = {}, onRefresh = {}, onSelect = { selected.value = event }, onMore = {}
            )
        }

        fun assertBounds(start: String, end: String, zone: String) {
            val bounds = "Bắt đầu: $start\nKết thúc: $end\nMúi giờ: $zone"
            composeRule.onNodeWithText(bounds, substring = true).performScrollTo().assertIsDisplayed()
        }
        assertBounds("22/11/2026 17:44:44.061", "29/11/2026 17:44:48.374", "Giờ Việt Nam (UTC+7)")
        composeRule.onNodeWithText("Xem sự kiện").performScrollTo().performClick()
        assertBounds("22/11/2026 17:44:44.061", "29/11/2026 17:44:48.374", "Giờ Việt Nam (UTC+7)")
        composeRule.runOnIdle {
            selected.value = event.copy(
                timezone = "America/New_York",
                startsAtEpochMillis = Instant.parse("2026-11-01T05:30:00Z").toEpochMilliseconds(),
                endsAtEpochMillis = Instant.parse("2026-11-01T06:30:00Z").toEpochMilliseconds()
            )
        }
        assertBounds("01/11/2026 01:30:00.000 (UTC-04:00)", "01/11/2026 01:30:00.000 (UTC-05:00)", "America/New_York")
        composeRule.runOnIdle { selected.value = event.copy(timezone = "Unknown/Zone") }
        assertBounds("22/11/2026 10:44:44.061", "29/11/2026 10:44:48.374", "UTC")
    }

    @Test
    fun giftMailboxHidesClaimActionsAtTheServerDeadline() {
        val expiresAt = 2_000L
        val serverNow = mutableStateOf(expiresAt - 1)
        val gift = MailboxGift("gift", "Quà thử", "Test gift", "Chúc mừng", "Congrats", 10, 5,
            1_000L, expiresAt, GiftState.AVAILABLE)
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            GiftMailboxScreen(
                state = GiftMailboxState(isOpen = true, items = listOf(gift), total = 1),
                isGuest = false, onBack = {}, onRefresh = {}, onLoadMore = {}, onClaim = {},
                onClaimAll = {}, onDismissClaimedGift = {}, nowMillis = serverNow.value
            )
        }
        composeRule.onNodeWithText("Nhận").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gift_claim_all").assertExists()
        composeRule.runOnIdle { serverNow.value = expiresAt }
        composeRule.onNodeWithText("Đã hết hạn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nhận").assertDoesNotExist()
        composeRule.onNodeWithTag("gift_claim_all").assertDoesNotExist()
    }

    @Test
    fun roomBrowserHidesPublicTeamRooms() {
        setLocalizedContent(AppLanguage.ENGLISH) {
            LocalizedLobbyScreen(
                state = lobbyState(LobbyStage.ROOM_BROWSER).copy(
                    availableRooms = listOf(
                        AvailableRoom("classic", "Classic Room", "Hien", GameMode.ORDER,
                            requiresPassword = false, lastSeenAtMillis = 0),
                        AvailableRoom("team", "Team Room", "Hien", GameMode.TEAM_2V2,
                            requiresPassword = false, lastSeenAtMillis = 0)
                    )
                ),
                isGuest = false
            )
        }

        composeRule.onNodeWithText("Classic Room").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Team Room").assertDoesNotExist()
    }

    @Test
    fun liveGameShowsComboOnlyInComboMode() {
        val mode = mutableStateOf(GameMode.ORDER)
        setLocalizedContent(AppLanguage.ENGLISH) {
            val state = activeGameState()
            GameScreen(
                state = state.copy(gameMode = mode.value, player = state.player.copy(combo = 5)),
                onNumberClick = {},
                onFinish = {},
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithText("COMBO x2").assertDoesNotExist()
        composeRule.runOnIdle { mode.value = GameMode.COMBO }
        composeRule.onNodeWithText("COMBO x2").assertIsDisplayed()
    }

    @Test
    fun resultShowsComboOnlyInComboMode() {
        val mode = mutableStateOf(GameMode.ORDER)
        setLocalizedContent(AppLanguage.ENGLISH) {
            ResultScreen(
                state = finishedGameState().copy(gameMode = mode.value),
                onRestart = {},
                onRematch = {},
                onCancelRematch = {},
                onDeclineRematch = {},
                onConnectOpponent = {},
                onBlockOpponent = {},
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithText("Accuracy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Combo").assertDoesNotExist()
        composeRule.runOnIdle { mode.value = GameMode.COMBO }
        composeRule.onNodeWithText("Combo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gameUsesVietnameseCopy() {
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            GameScreen(
                state = activeGameState(),
                onNumberClick = {},
                onFinish = {},
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithText("SỐ TIẾP THEO").assertIsDisplayed()
        composeRule.onNodeWithText("Cổ điển · Xếp hạng").assertIsDisplayed()
    }

    @Test
    fun resultUsesVietnameseCopy() {
        setLocalizedContent(AppLanguage.VIETNAMESE) {
            ResultScreen(
                state = finishedGameState(),
                onRestart = {},
                onRematch = {},
                onCancelRematch = {},
                onDeclineRematch = {},
                onConnectOpponent = {},
                onBlockOpponent = {},
                preferences = quietPreferences()
            )
        }

        composeRule.onNodeWithText("CHIẾN THẮNG!").assertIsDisplayed()
        composeRule.onNodeWithText("Kết quả trận").assertIsDisplayed()
    }

    @Test
    fun resultShowsLocalizedEnglishRematchFailure() {
        setLocalizedContent(AppLanguage.ENGLISH) {
            ResultScreen(
                state = finishedGameState().copy(
                    isRematchActionPending = false,
                    rematchNotice = "The action could not be completed. Please try again.",
                    rematchNoticeErrorCode = "OPPONENT_LEFT",
                ),
                onRestart = {},
                onRematch = {},
                onCancelRematch = {},
                onDeclineRematch = {},
                onConnectOpponent = {},
                onBlockOpponent = {},
                preferences = quietPreferences(),
            )
        }

        composeRule.onNodeWithText("The action could not be completed. Please try again.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Đối thủ đã rời phòng.").assertDoesNotExist()
    }

    @Test
    fun switchingPracticeToEnglishKeepsBoardProgress() {
        val language = mutableStateOf(AppLanguage.VIETNAMESE)
        val challenge = createPracticeChallenge(GameMode.ORDER, seed = 0x12345678)
        composeRule.setContent {
            ProvideLocalization(language.value) {
                FastToWinTheme {
                    PracticeScreen(
                        mode = GameMode.ORDER,
                        challenge = challenge,
                        preferences = quietPreferences(),
                        onBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("game_number_1").performClick()
        composeRule.onNodeWithTag("practice_score").assertTextEquals("10")
        composeRule.onNodeWithTag("practice_target").assertTextEquals("2")

        composeRule.runOnIdle { language.value = AppLanguage.ENGLISH }

        composeRule.onNodeWithText("Score").assertIsDisplayed()
        composeRule.onNodeWithTag("practice_score").assertTextEquals("10")
        composeRule.onNodeWithTag("practice_target").assertTextEquals("2")
    }

    private fun setLocalizedContent(language: AppLanguage, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            ProvideLocalization(language) {
                FastToWinTheme { content() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LocalizedLobbyScreen(state: GameState, isGuest: Boolean) {
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
            isGuest = isGuest,
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

    private fun lobbyState(stage: LobbyStage) = GameState(
        lobbyStage = stage,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hien", id = "hien")
    )

    private fun activeGameState() = GameState(
        isMatchStarted = true,
        numbers = (1..50).toList(),
        currentTarget = 1,
        matchType = com.hienthai.fastowin.protocol.MatchType.RANKED,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hien", id = "hien"),
        opponent = PlayerState("Hieu", id = "hieu")
    )

    private fun finishedGameState() = GameState(
        isGameOver = true,
        winnerPlayerId = "hien",
        player = PlayerState("Hien", id = "hien", score = 30),
        opponent = PlayerState("Hieu", id = "hieu", score = 20)
    )

    private fun quietPreferences() = AppPreferences(
        soundEnabled = false,
        vibrationEnabled = false,
        visualEffectsEnabled = false
    )
}
