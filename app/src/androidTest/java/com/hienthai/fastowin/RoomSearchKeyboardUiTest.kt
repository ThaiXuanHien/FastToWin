package com.hienthai.fastowin

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.screens.LobbyScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Catches sibling IME padding collapsing the room list when search receives focus. */
@OptIn(ExperimentalTestApi::class)
class RoomSearchKeyboardUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun focusedRoomSearchRemainsVisibleWithTheRealSoftwareKeyboard() {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            )
            val launcher = activity.packageManager.getActivityInfo(ComponentName(activity, MainActivity::class.java), 0)
            activity.window.attributes = activity.window.attributes.apply { softInputMode = launcher.softInputMode }
        }
        composeRule.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme {
                    ArcadeBackdrop(Modifier.fillMaxSize(), darkBackdrop = true) {
                    // Inert fixture: no controller, account, server connection or room mutation.
                    LobbyScreen(
                        state = GameState(lobbyStage = LobbyStage.ROOM_BROWSER,
                            connectionStatus = ConnectionStatus.CONNECTED, player = PlayerState("Room keyboard QA")),
                        modifier = Modifier.fillMaxSize(),
                        onModeSelected = {}, onStartMatchmaking = { _, _ -> }, onCancelMatchmaking = {},
                        onOpenRoomBrowser = {}, onCreateRoom = { _, _, _, _ -> }, onJoinRoom = { _, _ -> },
                        onLeaveRoom = {}, onSetReady = {}, onKickOpponent = {}, onRefreshRooms = {},
                        onOpenProfile = {}, onOpenLeaderboard = {}, onOpenFriends = {}, onOpenFriendProfile = {},
                        onBackToMode = {}, onLogout = {}, isGuest = false, onUpgradeGuest = {},
                        onOpenNotifications = {}, onOpenClan = {}, onOpenPractice = {}, onOpenTournament = {},
                        onOpenShop = {}, onShareRoom = { _, _ -> Result.success(Unit) }, onResolveRoomLink = {},
                        onClaimDailyCheckIn = {}
                    )
                    }
                }
            }
        }
        val field = composeRule.onNode(hasSetTextAction()).performScrollTo()
        DialogImeSynchronizer(field).use { ime ->
            try {
                field.performClick()
                composeRule.waitUntil(10_000) { ime.isSettled(true) }
                field.assertIsFocused().assertFullyVisibleAboveIme()
                composeRule.onNodeWithTag("room_list").assertIsDisplayed()
                field.performTextReplacement("Keyboard QA")
                field.assertTextContains("Keyboard QA").assertFullyVisibleAboveIme()
                capture("focused")
                closeSoftKeyboard()
                composeRule.waitUntil(10_000) { ime.isSettled(false) }
                field.assertTextContains("Keyboard QA").assertIsDisplayed()
                composeRule.onNodeWithTag("app_header").assertIsDisplayed()
                composeRule.onNodeWithTag("bottom_bar").assertIsDisplayed()
                capture("dismissed")
            } catch (failure: Throwable) {
                runCatching { capture("failure") }.onFailure(failure::addSuppressed)
                throw failure
            } finally {
                closeSoftKeyboard()
            }
        }
    }

    private fun capture(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, "room-search-keyboard-$label.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
