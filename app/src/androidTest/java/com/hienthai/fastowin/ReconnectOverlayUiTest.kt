@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.hienthai.fastowin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReconnectOverlayUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reconnectOverlay_keepsGameVisible_blocksBoard_andRetriesOnce() {
        var numberClicks = 0
        var retries = 0
        val state = GameState(
            numbers = (1..50).toList(),
            currentRoomId = "room-1",
            currentMatchId = "match-1",
            isMatchStarted = true,
            connectionStatus = ConnectionStatus.RECONNECTING,
            player = PlayerState(id = "player-1", name = "Me"),
            opponent = PlayerState(id = "player-2", name = "Opponent")
        )

        composeRule.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme {
                    Box(Modifier.fillMaxSize()) {
                        GameScreen(state = state, onNumberClick = { numberClicks++ }, onFinish = {})
                        ReconnectOverlay(onRetry = { retries++ })
                    }
                }
            }
        }

        composeRule.onNodeWithTag("game_board").assertIsDisplayed()
        composeRule.onNodeWithTag("reconnect_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("game_number_1").performTouchInput { click(center) }
        composeRule.onNodeWithTag("reconnect_retry").performTouchInput { click(center) }
        composeRule.runOnIdle {
            assertEquals(0, numberClicks)
            assertEquals(1, retries)
        }
    }
}
