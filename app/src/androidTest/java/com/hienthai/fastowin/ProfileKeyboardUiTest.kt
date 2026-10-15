@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.hienthai.fastowin

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.screens.MainTab
import com.hienthai.fastowin.ui.screens.ProfileScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class ProfileKeyboardUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private var saves = 0
    private var savedName: String? = null

    @Test fun tabEditorRemainsVisibleAndScrollableWithSoftwareKeyboard() = checkEditor("standard")

    @Test fun tabEditorKeepsActionsReachableWithLargeTextAndSoftwareKeyboard() = checkEditor("large-text", 1.4f)

    private fun checkEditor(scenario: String, fontScale: Float = 1f) {
        val expectedOrientation = InstrumentationRegistry.getArguments().getString("qaOrientation")
        if (expectedOrientation != null) {
            composeRule.waitUntil(10_000) {
                val actual = composeRule.activity.resources.configuration.orientation
                actual == if (expectedOrientation == "landscape") android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    else android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
        }
        val orientation = if (composeRule.activity.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        val label = "$orientation-$scenario"
        // Use the launcher's real adjustResize setting and edge-to-edge behavior in the test window.
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
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    FastToWinTheme {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Editor() }
                    }
                }
            }
        }
        var testFailure: Throwable? = null
        try {
            composeRule.onNodeWithTag("profile_edit").performScrollTo().performClick()
            composeRule.onNodeWithTag("profile_display_name").performScrollTo().performClick()
            composeRule.waitUntil(10_000) { imeVisible() }
            // Allow the IME animation to settle, then check actual clipped bounds, not node existence.
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("profile_display_name").assertIsDisplayed().assertIsFocused()
                .performTextReplacement("Keyboard QA")
            assertFullyVisible(composeRule.onNodeWithTag("profile_display_name"))
            assertTrue("Keyboard must still be open", imeVisible())
            capture("$label-field")
            composeRule.onNodeWithText("Lưu").performScrollTo().assertIsDisplayed().assertIsEnabled()
            assertFullyVisible(composeRule.onNodeWithText("Lưu"))
            composeRule.onNodeWithText("Hủy").performScrollTo().assertIsDisplayed().assertIsEnabled()
            assertFullyVisible(composeRule.onNodeWithText("Hủy"))
            assertTrue("Scrolling must not dismiss the keyboard", imeVisible())
            capture("$label-actions")
            assertEquals(0, saves)
            composeRule.onNodeWithText("Lưu").performScrollTo().assertIsDisplayed().performClick()
            composeRule.runOnIdle { assertEquals(1, saves); assertEquals("Keyboard QA", savedName) }
            closeSoftKeyboard()
            composeRule.waitUntil(10_000) { !imeVisible() }
            composeRule.onNodeWithTag("app_header").assertIsDisplayed()
            composeRule.onNodeWithTag("bottom_bar").assertIsDisplayed()
            composeRule.onNodeWithTag("profile_display_name").performScrollTo().assertTextContains("Keyboard QA")
            capture("$label-keyboard-closed")
        } catch (failure: Throwable) {
            testFailure = failure
            runCatching { capture("$label-failure") }.onFailure(failure::addSuppressed)
            throw failure
        } finally {
            runCatching { closeSoftKeyboard() }.onFailure { cleanupFailure ->
                val original = testFailure
                if (original != null) original.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
    }

    private fun assertFullyVisible(interaction: SemanticsNodeInteraction) {
        val node = interaction.fetchSemanticsNode()
        var visible = node.boundsInRoot
        var parent = node.parent
        while (parent != null) {
            visible = visible.intersect(parent.boundsInRoot)
            parent = parent.parent
        }
        assertTrue("Field/action must be fully visible inside its scroll viewport: visible=$visible size=${node.size}",
            visible.height >= node.size.height - 1f && visible.width >= node.size.width - 1f)
        val boundsInWindow = node.boundsInWindow
        var imeTop = 0
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val imeBottom = ViewCompat.getRootWindowInsets(decor)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            imeTop = decor.height - imeBottom
        }
        assertTrue("Field/action must be above the IME: bounds=$boundsInWindow imeTop=$imeTop",
            boundsInWindow.bottom <= imeTop + 1f)
    }

    private fun imeVisible(): Boolean {
        var visible = false
        composeRule.runOnUiThread {
            visible = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun capture(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir
        var windowInfo = ""
        composeRule.runOnUiThread {
            val decor = composeRule.activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
            windowInfo = "decor=${decor.width}x${decor.height} imeBottom=${insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom} imeVisible=${insets?.isVisible(WindowInsetsCompat.Type.ime())}\n"
        }
        File(cache, "profile-keyboard-$label.txt").writeText(windowInfo + composeRule.onRoot(useUnmergedTree = true).printToString())
        File(cache, "profile-keyboard-$label.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Composable private fun Editor() {
        val state = GameState(profile = PlayerProfileSnapshot(userId = "keyboard-qa", displayName = "Saved QA", playerCode = "QA001"))
        TopLevelTabIfNeeded(
            enabled = true, state = state, selected = MainTab.ACCOUNT, friendNotificationCount = 0,
            onHome = {}, onRooms = {}, onLeaderboard = {}, onClan = {}, onAccount = {}, onNotifications = {}
        ) { modifier ->
            ProfileScreen(
                serverUrl = "", state = state, modifier = modifier,
                onBack = {}, onRefresh = {}, onOpenMatchDetail = {}, onCloseMatchDetail = {},
                onEquipCosmetics = { _, _ -> }, onClaimMissionReward = {},
                onSave = { name, _, _ -> saves++; savedName = name },
                canEdit = true, isAccountLoading = false, accountError = null, accountNotice = null,
                accountSessions = emptyList(), areSessionsLoading = false,
                onChangePassword = { _, _ -> }, onDeleteAccount = {}, onClearAccountFeedback = {},
                onLoadSessions = {}, onRevokeSession = {}, onRevokeAllSessions = {}, onLogout = {},
                showBackButton = false
            )
        }
    }
}