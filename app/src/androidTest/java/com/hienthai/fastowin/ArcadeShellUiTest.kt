package com.hienthai.fastowin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerProgressionSnapshot
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ArcadeShellUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun header_smallPhoneLargeText_keepsLongCopyAndBalancesInsideViewport() =
        assertHeaderLayout(320.dp, 568.dp, fontScale = 1.6f, verifyCallbacks = true)

    @Test
    fun header_landscape_keepsLongCopyAndBalancesInsideViewport() =
        assertHeaderLayout(720.dp, 400.dp, fontScale = 1.6f)

    @Test
    fun header_tablet_keepsLongCopyAndBalancesInsideViewport() =
        assertHeaderLayout(840.dp, 1_180.dp, fontScale = 1.6f)

    @Test
    fun bottomBar_smallPhoneLargeText_showsFiveTabsAndDispatchesCallbacks() =
        assertBottomBarLayout(320.dp, 568.dp, fontScale = 1.6f, verifyCallbacks = true)

    @Test
    fun bottomBar_landscape_showsFiveTabsInsideViewport() =
        assertBottomBarLayout(720.dp, 400.dp, fontScale = 1.6f)

    @Test
    fun bottomBar_tablet_showsFiveTabsInsideViewport() =
        assertBottomBarLayout(840.dp, 1_180.dp, fontScale = 1.6f)

    private fun assertHeaderLayout(
        width: Dp,
        height: Dp,
        fontScale: Float,
        verifyCallbacks: Boolean = false
    ) {
        var backClicks = 0
        var notificationClicks = 0

        setAdaptiveContent(width, height, fontScale) {
            Box(Modifier.fillMaxSize().testTag(VIEWPORT_TAG)) {
                FastToWinHeader(
                    title = LONG_TITLE,
                    subtitle = LONG_SUBTITLE,
                    gold = 1_234_567,
                    gems = 98_765,
                    unreadNotifications = 123,
                    onNotifications = { notificationClicks += 1 },
                    onBack = { backClicks += 1 }
                )
            }
        }

        val viewportBounds = composeRule.onNodeWithTag(VIEWPORT_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val headerBounds = composeRule.onNodeWithTag("app_header")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertWithin(headerBounds, viewportBounds)

        listOf(
            composeRule.onNodeWithText(LONG_TITLE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithText(LONG_SUBTITLE).assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithContentDescription("Quay lại").assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag("header_gold").assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag("header_gem").assertIsDisplayed().fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithContentDescription("Thông báo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        ).forEach { assertWithin(it, headerBounds) }
        composeRule.onNodeWithText("99+").assertIsDisplayed()

        val goldBounds = composeRule.onNodeWithTag("header_gold").fetchSemanticsNode().boundsInRoot
        val gemBounds = composeRule.onNodeWithTag("header_gem").fetchSemanticsNode().boundsInRoot
        val notificationBounds = composeRule.onNodeWithContentDescription("Thông báo")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Gold and Gem must not overlap", goldBounds.right <= gemBounds.left + BOUNDS_TOLERANCE)
        assertTrue(
            "Gem and notification action must not overlap",
            gemBounds.right <= notificationBounds.left + BOUNDS_TOLERANCE
        )

        if (verifyCallbacks) {
            composeRule.onNodeWithContentDescription("Quay lại").performClick()
            composeRule.onNodeWithContentDescription("Thông báo").performClick()
            composeRule.runOnIdle {
                assertEquals(1, backClicks)
                assertEquals(1, notificationClicks)
            }
        }
    }

    private fun assertBottomBarLayout(
        width: Dp,
        height: Dp,
        fontScale: Float,
        verifyCallbacks: Boolean = false
    ) {
        val stage = mutableStateOf(LobbyStage.SELECT_MODE)
        var homeClicks = 0
        var roomClicks = 0
        var leaderboardClicks = 0
        var clanClicks = 0
        var accountClicks = 0

        setAdaptiveContent(width, height, fontScale) {
            Box(Modifier.fillMaxSize().testTag(VIEWPORT_TAG)) {
                LobbyScreen(
                    state = shellState(stage.value),
                    onModeSelected = {
                        roomClicks += 1
                        stage.value = LobbyStage.ROOM_BROWSER
                    },
                    onStartMatchmaking = { _, _ -> },
                    onCancelMatchmaking = {},
                    onOpenRoomBrowser = {},
                    onCreateRoom = { _, _ -> },
                    onJoinRoom = { _, _ -> },
                    onLeaveRoom = {},
                    onSetReady = {},
                    onKickOpponent = {},
                    onRefreshRooms = {},
                    onOpenProfile = { accountClicks += 1 },
                    onOpenLeaderboard = { leaderboardClicks += 1 },
                    onOpenFriends = {},
                    onOpenFriendProfile = {},
                    onBackToMode = {
                        homeClicks += 1
                        stage.value = LobbyStage.SELECT_MODE
                    },
                    onLogout = {},
                    isGuest = false,
                    onUpgradeGuest = {},
                    onOpenNotifications = {},
                    onOpenClan = { clanClicks += 1 },
                    onOpenPractice = {},
                    onOpenTournament = {},
                    onOpenShop = {},
                    onShareRoom = { _, _ -> Result.success(Unit) },
                    onResolveRoomLink = {},
                    onClaimDailyCheckIn = {}
                )
            }
        }

        val viewportBounds = composeRule.onNodeWithTag(VIEWPORT_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val bottomBarBounds = composeRule.onNodeWithTag(BOTTOM_BAR_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertWithin(bottomBarBounds, viewportBounds)

        val tabBounds = BOTTOM_TAB_TAGS.map { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
                .also { assertWithin(it, bottomBarBounds) }
        }
        tabBounds.zipWithNext().forEach { (left, right) ->
            assertTrue("Bottom navigation tabs must not overlap", left.right <= right.left + BOUNDS_TOLERANCE)
        }

        composeRule.onNodeWithTag(HOME_TAB_TAG).assertIsSelected()
        BOTTOM_TAB_TAGS.drop(1).forEach { composeRule.onNodeWithTag(it).assertIsNotSelected() }

        if (verifyCallbacks) {
            composeRule.onNodeWithTag(ROOMS_TAB_TAG).performClick()
            composeRule.onNodeWithTag(ROOMS_TAB_TAG).assertIsSelected()
            composeRule.runOnIdle { assertEquals(1, roomClicks) }

            composeRule.onNodeWithTag(HOME_TAB_TAG).performClick()
            composeRule.onNodeWithTag(HOME_TAB_TAG).assertIsSelected()
            composeRule.runOnIdle {
                assertEquals(1, homeClicks)
                assertEquals(LobbyStage.SELECT_MODE, stage.value)
            }

            composeRule.onNodeWithTag(LEADERBOARD_TAB_TAG).performClick()
            composeRule.onNodeWithTag(CLAN_TAB_TAG).performClick()
            composeRule.onNodeWithTag(ACCOUNT_TAB_TAG).performClick()
            composeRule.runOnIdle {
                assertEquals(1, leaderboardClicks)
                assertEquals(1, clanClicks)
                assertEquals(1, accountClicks)
            }
        }
    }

    private fun setAdaptiveContent(
        width: Dp,
        height: Dp,
        fontScale: Float,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then
                    DeviceConfigurationOverride.FontScale(fontScale)
            ) {
                FastToWinTheme { content() }
            }
        }
    }

    private fun assertWithin(inner: Rect, outer: Rect) {
        assertTrue("Node starts outside its container", inner.left >= outer.left - BOUNDS_TOLERANCE)
        assertTrue("Node starts above its container", inner.top >= outer.top - BOUNDS_TOLERANCE)
        assertTrue("Node ends outside its container", inner.right <= outer.right + BOUNDS_TOLERANCE)
        assertTrue("Node ends below its container", inner.bottom <= outer.bottom + BOUNDS_TOLERANCE)
    }

    private fun shellState(stage: LobbyStage) = GameState(
        lobbyStage = stage,
        connectionStatus = ConnectionStatus.CONNECTED,
        player = PlayerState("Hiền"),
        profile = PlayerProfileSnapshot(
            userId = "arcade-shell-player",
            displayName = "Hiền",
            playerCode = "HIEN001",
            progression = PlayerProgressionSnapshot(gold = 1_234_567, gems = 98_765)
        )
    )

    private companion object {
        const val LONG_TITLE = "Thống kê, thành tích và hành trình chiến binh"
        const val LONG_SUBTITLE = "Theo dõi mùa giải, phần thưởng và tiến độ mới nhất của bạn"
        const val VIEWPORT_TAG = "arcade_shell_viewport"
        const val BOTTOM_BAR_TAG = "bottom_bar"
        const val HOME_TAB_TAG = "bottom_tab:home"
        const val ROOMS_TAB_TAG = "bottom_tab:rooms"
        const val LEADERBOARD_TAB_TAG = "bottom_tab:leaderboard"
        const val CLAN_TAB_TAG = "bottom_tab:clan"
        const val ACCOUNT_TAB_TAG = "bottom_tab:account"
        val BOTTOM_TAB_TAGS = listOf(
            HOME_TAB_TAG,
            ROOMS_TAB_TAG,
            LEADERBOARD_TAB_TAG,
            CLAN_TAB_TAG,
            ACCOUNT_TAB_TAG
        )
        const val BOUNDS_TOLERANCE = 1f
    }
}
