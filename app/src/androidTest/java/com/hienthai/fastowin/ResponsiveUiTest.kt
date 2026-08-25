package com.hienthai.fastowin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.screens.TutorialScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class ResponsiveUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_fitsSmallPhone() = assertHomeViewport(320.dp, 568.dp)

    @Test
    fun home_fitsLargePhone() = assertHomeViewport(430.dp, 932.dp)

    @Test
    fun home_fitsTablet() = assertHomeViewport(840.dp, 1_180.dp)

    @Test
    fun home_fitsLandscape() = assertHomeViewport(720.dp, 400.dp)

    @Test
    fun home_largeTextAndLongContentRemainScrollable() {
        setViewportContent(width = 320.dp, height = 568.dp, fontScale = 1.6f) {
            TestResponsiveLobby()
        }

        composeRule.onNodeWithTag("home_screen").assertExists()
        composeRule.onNodeWithText("Luyện tập offline").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun home_compactHeaderAndSevenDayRewardsStayAlignedWithLargeText() {
        setViewportContent(width = 320.dp, height = 568.dp, fontScale = 1.6f) {
            TestResponsiveLobby()
        }

        composeRule.onNodeWithTag("app_header").assertIsDisplayed()
        composeRule.onNodeWithText("Xin chào,", substring = true).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("FAST TO WIN").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Cài đặt").fetchSemanticsNodes().isEmpty())

        val rewardHeights = (1..7).map { day ->
            composeRule.onNodeWithTag("daily_reward_day_$day").fetchSemanticsNode().boundsInRoot.height
        }
        assertTrue(rewardHeights.max() - rewardHeights.min() <= 1f)

        val goldBounds = composeRule.onNodeWithTag("header_gold").fetchSemanticsNode().boundsInRoot
        val gemBounds = composeRule.onNodeWithTag("header_gem").fetchSemanticsNode().boundsInRoot
        val minimumGapPx = with(composeRule.density) { 4.dp.toPx() }
        assertTrue(gemBounds.left - goldBounds.right >= minimumGapPx)
    }

    @Test
    fun tutorial_keepsContinueButtonVisibleAtBottomOnSmallPhone() {
        setViewportContent(width = 320.dp, height = 568.dp, fontScale = 1.4f) {
            TutorialScreen(onComplete = {}, onSkip = {})
        }

        composeRule.onNodeWithTag("tutorial_continue").assertIsDisplayed()
    }

    private fun assertHomeViewport(width: Dp, height: Dp) {
        setViewportContent(width, height) { TestResponsiveLobby() }
        composeRule.onNodeWithTag("home_screen").assertExists()
        composeRule.onNodeWithTag("daily_check_in_card").assertExists()
        composeRule.onNodeWithTag("home_quick_match").assertExists()
        composeRule.onNodeWithTag("home_action:Tạo phòng").assertExists()
    }

    private fun setViewportContent(
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                Box(modifier = Modifier.requiredSize(width, height)) {
                    FastToWinTheme { content() }
                }
            }
        }
    }
}

@Composable
private fun TestResponsiveLobby() {
    val longName = "Người chơi có biệt danh rất dài để kiểm tra giao diện không bị tràn"
    LobbyScreen(
        state = GameState(
            lobbyStage = LobbyStage.SELECT_MODE,
            connectionStatus = ConnectionStatus.CONNECTED,
            player = PlayerState(longName),
            profile = PlayerProfileSnapshot(
                userId = "player-responsive",
                displayName = longName,
                playerCode = "PLAYER0001"
            )
        ),
        onModeSelected = {},
        onStartMatchmaking = { _, _ -> },
        onCancelMatchmaking = {},
        onOpenRoomBrowser = {},
        onCreateRoom = { _, _ -> },
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
