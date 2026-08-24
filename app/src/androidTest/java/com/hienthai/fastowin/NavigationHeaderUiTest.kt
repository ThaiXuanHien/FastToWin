package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
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

        pressBack()

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

        pressBack()

        composeRule.onNodeWithText("Rời trận đấu?").assertIsDisplayed()
        composeRule.onNodeWithText("Tiếp tục chơi").performClick()
        composeRule.runOnIdle { assertTrue(!exited) }

        pressBack()
        composeRule.onNodeWithText("Rời trận").performClick()
        composeRule.runOnIdle { assertTrue(exited) }
    }
}
