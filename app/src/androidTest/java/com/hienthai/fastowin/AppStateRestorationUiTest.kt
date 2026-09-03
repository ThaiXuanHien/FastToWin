package com.hienthai.fastowin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        // Wait for the deliberately unreachable backend to show Offline rather
        // than racing the health check against the welcome/tutorial screens.
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("offline_screen").fetchSemanticsNodes().size == 1
        }
        onNodeWithText("LUYỆN TẬP OFFLINE").performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("practice_new").fetchSemanticsNodes().size == 1
        }
        onNodeWithTag("practice_new").assertIsDisplayed()

        restorationTester.emulateSaveAndRestore()
        // The launcher is part of GameContent: it cannot be restored if the
        // guest session was lost and the app returned to its auth flow.
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("practice_new").fetchSemanticsNodes().size == 1
        }
        onNodeWithText("Chơi với tư cách khách").assertDoesNotExist()
        onNodeWithTag("practice_new").assertIsDisplayed()
    }
}
