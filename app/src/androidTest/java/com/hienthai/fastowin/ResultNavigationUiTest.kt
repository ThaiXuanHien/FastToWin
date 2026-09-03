package com.hienthai.fastowin

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.ResultNavigationActions
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.screens.ResultScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ResultNavigationUiTest {
    @Test
    fun returnToLobby_afterRestoration_doesNotFollowHistoryToFriends() = runComposeUiTest {
        var route = "/room/test-room"
        var leaveCalls = 0
        var backCalls = 0
        val navigation = ResultNavigationActions(
            leaveRoom = { leaveCalls++; route = "/rooms" },
            openTournament = { error("Not a tournament") },
            navigateBack = { backCalls++; route = "/friends" },
            isTournamentMatch = false
        )
        val restorationTester = StateRestorationTester(this)
        restorationTester.setContent {
            FastToWinTheme {
                ResultScreen(
                    state = GameState(
                        isGameOver = true,
                        currentRoomId = "test-room",
                        player = PlayerState("Hiền", id = "hien", score = 260),
                        opponent = PlayerState("Hiếu", id = "hieu", score = 240),
                        winnerPlayerId = "hien"
                    ),
                    onRestart = navigation.returnToLobby,
                    onBack = navigation.back,
                    onRematch = {},
                    onCancelRematch = {},
                    onDeclineRematch = {},
                    onConnectOpponent = {},
                    onBlockOpponent = {},
                    preferences = AppPreferences(soundEnabled = false, vibrationEnabled = false)
                )
            }
        }

        // Back and the explicit destination must not be interchangeable on web.
        onNodeWithContentDescription("Quay lại").performClick()
        runOnIdle {
            assertEquals("/friends", route)
            assertEquals(1, backCalls)
            assertEquals(0, leaveCalls)
            route = "/room/test-room"
        }

        restorationTester.emulateSaveAndRestore()
        onNodeWithText("Về sảnh").performScrollTo().assertIsDisplayed().performClick()

        runOnIdle {
            assertEquals("/rooms", route)
            assertEquals(1, leaveCalls)
            assertEquals(1, backCalls)
        }
    }
}
