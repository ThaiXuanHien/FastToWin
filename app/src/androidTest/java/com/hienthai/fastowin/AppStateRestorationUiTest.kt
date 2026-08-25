package com.hienthai.fastowin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.hienthai.fastowin.data.network.InMemoryAuthSessionStore
import com.hienthai.fastowin.data.network.InMemoryResumeTokenStore
import com.hienthai.fastowin.data.preferences.InMemoryAppPreferencesStore
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AppStateRestorationUiTest {
    @Test
    fun guestSession_survivesSavedStateRestoration() = runComposeUiTest {
        val restorationTester = StateRestorationTester(this)
        val resumeTokenStore = InMemoryResumeTokenStore()
        val authSessionStore = InMemoryAuthSessionStore()
        val preferencesStore = InMemoryAppPreferencesStore()

        restorationTester.setContent {
            FastToWinApp(
                serverUrl = "ws://127.0.0.1:1/game",
                resumeTokenStore = resumeTokenStore,
                authSessionStore = authSessionStore,
                preferencesStore = preferencesStore,
                devicePlatform = "android-test"
            )
        }
        waitForIdle()

        onNodeWithText("Chơi với tư cách khách").performClick()
        waitForIdle()
        onNodeWithText("Bỏ qua").assertIsDisplayed()

        restorationTester.emulateSaveAndRestore()
        waitForIdle()

        onNodeWithText("Chơi với tư cách khách").assertIsNotDisplayed()
        onNodeWithText("Bỏ qua").assertIsDisplayed()
    }
}
