package com.hienthai.fastowin

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.GameScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalTestApi::class)
class ArcadeGameUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fiftyNumberBoard_smallPhone_reachesLastNumberWithoutLegacyErrorText() =
        assertFiftyNumberBoard(width = 320.dp, height = 568.dp, fontScale = 1.2f)

    @Test
    fun fiftyNumberBoard_landscape_reachesLastNumberWithoutLegacyErrorText() =
        assertFiftyNumberBoard(width = 720.dp, height = 400.dp, fontScale = 1f)

    private fun assertFiftyNumberBoard(width: Dp, height: Dp, fontScale: Float) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width, height)) then
                    DeviceConfigurationOverride.FontScale(fontScale)
            ) {
                FastToWinTheme {
                    GameScreen(
                        state = gameState(),
                        onNumberClick = {},
                        onFinish = {},
                        preferences = AppPreferences(
                            soundEnabled = false,
                            vibrationEnabled = false,
                            visualEffectsEnabled = false
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithTag("game_screen").assertIsDisplayed()
        composeRule.onNodeWithText(LEGACY_ERROR_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithTag("number_grid")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("game_number_50").assertIsDisplayed()
        if (width <= 320.dp && height >= 568.dp) {
            val cellBounds = composeRule.onNodeWithTag("game_number_1")
                .fetchSemanticsNode().boundsInRoot
            val aspectRatio = cellBounds.width / cellBounds.height
            assertTrue(
                "Small-screen number cells must remain close to square, actual ratio=$aspectRatio",
                aspectRatio in 0.8f..1.25f
            )
            val localScoreBounds = composeRule.onNodeWithTag("player_score_local")
                .fetchSemanticsNode().boundsInRoot
            val opponentScoreBounds = composeRule.onNodeWithTag("player_score_opponent")
                .fetchSemanticsNode().boundsInRoot
            val scoreCardHeightRatio = max(localScoreBounds.height, opponentScoreBounds.height) /
                min(localScoreBounds.height, opponentScoreBounds.height)
            assertTrue(
                "Small-screen player cards must keep equal compact heights, actual ratio=$scoreCardHeightRatio",
                scoreCardHeightRatio <= 1.1f
            )
        }
    }

    private fun gameState() = GameState(
        isMatchStarted = true,
        numbers = (1..50).toList(),
        currentTarget = 1,
        connectionStatus = ConnectionStatus.CONNECTED,
        currentRoomName = "Phòng kiểm thử giao diện 2D Arcade",
        message = LEGACY_ERROR_MESSAGE,
        player = PlayerState("Hiền", id = "player-hien", avatarId = "crown", frameId = "frame_gold"),
        opponent = PlayerState("Hiếu", id = "player-hieu", avatarId = "target", frameId = "frame_persistent")
    )

    private companion object {
        const val LEGACY_ERROR_MESSAGE = "Bạn vừa bấm sai, hãy thử lại"
    }
}
