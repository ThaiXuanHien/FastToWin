package com.hienthai.fastowin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationDestination
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.screens.NotificationsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationHeaderUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notifications_hidesItsOwnHeaderShortcutAndKeepsBackAvailable() {
        var wentBack = false
        composeRule.setContent {
            FastToWinTheme {
                NotificationsScreen(
                    notifications = emptyList(),
                    onBack = { wentBack = true },
                    onOpen = {},
                    onDismiss = {},
                    onMarkAllRead = {},
                    onClearAll = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Thông báo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Quay lại").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun notifications_systemBackUsesTheSameNavigationAction() {
        var wentBack = false
        composeRule.setContent {
            FastToWinTheme {
                NotificationsScreen(
                    notifications = emptyList(),
                    onBack = { wentBack = true },
                    onOpen = {},
                    onDismiss = {},
                    onMarkAllRead = {},
                    onClearAll = {}
                )
            }
        }

        pressSystemBack()

        composeRule.runOnIdle { assertTrue(wentBack) }
    }

    @Test
    fun notifications_placesBulkActionsInHeaderAndConfirmsBeforeClearing() {
        var markedAllRead = false
        var clearedAll = false
        val notification = AppNotification(
            id = "mission:daily",
            kind = AppNotificationKind.MISSION,
            title = "Hoàn thành nhiệm vụ",
            message = "Bạn có phần thưởng mới.",
            createdAtEpochMillis = 1L,
            destination = AppNotificationDestination.PROFILE
        )

        composeRule.setContent {
            FastToWinTheme {
                NotificationsScreen(
                    notifications = listOf(notification),
                    onBack = {},
                    onOpen = {},
                    onDismiss = {},
                    onMarkAllRead = { markedAllRead = true },
                    onClearAll = { clearedAll = true }
                )
            }
        }

        composeRule.onNodeWithTag("header_gold").assertDoesNotExist()
        composeRule.onNodeWithTag("header_gem").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Đánh dấu tất cả đã đọc")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(markedAllRead) }

        composeRule.onNodeWithContentDescription("Xóa tất cả thông báo").performClick()
        composeRule.onNodeWithText("Xóa tất cả thông báo?").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(!clearedAll) }
        composeRule.onNodeWithText("Hủy").performClick()
        composeRule.runOnIdle { assertTrue(!clearedAll) }

        composeRule.onNodeWithContentDescription("Xóa tất cả thông báo").performClick()
        composeRule.onNodeWithText("Xóa tất cả").performClick()
        composeRule.runOnIdle { assertTrue(clearedAll) }
    }

    @Test
    fun game_systemBackRequestsConfirmationBeforeLeaving() {
        var exited = false
        composeRule.setContent {
            FastToWinTheme {
                GameScreen(
                    state = GameState(
                        isMatchStarted = true,
                        currentRoomName = "Phòng kiểm tra",
                        player = PlayerState("Hiền"),
                        opponent = PlayerState("Hiếu")
                    ),
                    onNumberClick = {},
                    onFinish = {},
                    onExit = { exited = true }
                )
            }
        }

        pressSystemBack()

        composeRule.onNodeWithText("Rời trận?").assertIsDisplayed()
        composeRule.onNodeWithText("TIẾP TỤC CHƠI").performClick()
        composeRule.runOnIdle { assertTrue(!exited) }

        pressSystemBack()
        composeRule.onNodeWithText("RỜI TRẬN").performClick()
        composeRule.runOnIdle { assertTrue(exited) }
    }

    private fun pressSystemBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .single() as ComponentActivity
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Test
    fun notifications_loadMoreRevealsTheNextPage() {
        val notifications = (1..21).map { index ->
            AppNotification(
                id = "notification-$index",
                kind = AppNotificationKind.MISSION,
                title = "Thông báo $index",
                message = "Nội dung $index",
                createdAtEpochMillis = index.toLong(),
                destination = AppNotificationDestination.PROFILE
            )
        }
        composeRule.setContent {
            FastToWinTheme {
                NotificationsScreen(
                    notifications = notifications,
                    onBack = {},
                    onOpen = {},
                    onDismiss = {},
                    onMarkAllRead = {},
                    onClearAll = {}
                )
            }
        }

        composeRule.onNodeWithText("Thông báo 21").assertDoesNotExist()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("notifications_load_more"))
        composeRule.onNodeWithTag("notifications_load_more").performClick()
        composeRule.onNodeWithText("Thông báo 21").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notifications_load_more").assertDoesNotExist()
    }
}
