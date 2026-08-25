package com.hienthai.fastowin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.createPracticeChallenge
import com.hienthai.fastowin.ui.screens.PracticeScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PracticeStateRestorationUiTest {
    @Test
    fun savedStateRegistry_keepsProgressAndContinuesTheSameBoard() = runComposeUiTest {
        val restorationTester = StateRestorationTester(this)
        val challenge = createPracticeChallenge(GameMode.ORDER, seed = 0x12345678)
        restorationTester.setContent {
            FastToWinTheme {
                PracticeScreen(
                    mode = GameMode.ORDER,
                    challenge = challenge,
                    preferences = AppPreferences(
                        soundEnabled = false,
                        vibrationEnabled = false,
                        visualEffectsEnabled = false
                    ),
                    onBack = {}
                )
            }
        }
        waitForIdle()

        onNodeWithTag("number_grid").performScrollToIndex(challenge.numbers.indexOf(1))
        onNodeWithTag("game_number_1").performClick()
        waitForIdle()
        onNodeWithTag("practice_score").assertTextEquals("10")
        onNodeWithTag("practice_target").assertTextEquals("2")

        restorationTester.emulateSaveAndRestore()
        waitForIdle()

        onNodeWithTag("practice_score").assertTextEquals("10")
        onNodeWithTag("practice_target").assertTextEquals("2")
        onNodeWithTag("number_grid").performScrollToIndex(challenge.numbers.indexOf(2))
        onNodeWithTag("game_number_2").performClick()
        waitForIdle()
        onNodeWithTag("practice_score").assertTextEquals("20")
        onNodeWithTag("practice_target").assertTextEquals("3")
    }
}
