package com.hienthai.fastowin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.screens.OfflineScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class OfflineScreenUiTest {
    @Test
    fun offlineScreen_exposesRecoveryActions() = runComposeUiTest {
        var retried = false
        var openedPractice = false

        setContent {
            FastToWinTheme(preferences = AppPreferences()) {
                ArcadeBackdrop(modifier = Modifier.fillMaxSize()) {
                    OfflineScreen(
                        onRetry = { retried = true },
                        onPractice = { openedPractice = true }
                    )
                }
            }
        }

        onNodeWithTag("offline_screen").assertIsDisplayed()
        onNodeWithText("THỬ KẾT NỐI LẠI").performClick()
        onNodeWithText("LUYỆN TẬP OFFLINE").performClick()

        assertTrue(retried)
        assertTrue(openedPractice)
    }
}
