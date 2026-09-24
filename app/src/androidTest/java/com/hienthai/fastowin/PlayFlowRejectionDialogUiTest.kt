@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.hienthai.fastowin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayFlowRejectionDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeTournamentUsesArcadeDialogAndOffersRecovery() {
        var dismissCount = 0
        var openTournamentCount = 0
        val message = "Hãy rời hoặc hoàn tất giải đấu hiện tại trước khi tiếp tục."

        composeRule.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme {
                    PlayFlowRejectionDialog(
                        code = "TOURNAMENT_ACTIVE",
                        message = message,
                        onDismiss = { dismissCount++ },
                        onOpenTournament = { openTournamentCount++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("play_flow_rejection_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("arcade_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("KHÔNG THỂ TIẾP TỤC").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText("ĐẤU GIẢI").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(0, dismissCount)
            assertEquals(1, openTournamentCount)
        }
    }

    @Test
    fun roomErrorUsesTheSameArcadeDialogWithoutTournamentAction() {
        var dismissCount = 0

        composeRule.setContent {
            ProvideLocalization(AppLanguage.VIETNAMESE) {
                FastToWinTheme {
                    PlayFlowRejectionDialog(
                        code = "ROOM_FULL",
                        message = "Phòng đã đầy hoặc trận đấu đã bắt đầu.",
                        onDismiss = { dismissCount++ },
                        onOpenTournament = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("arcade_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("play_flow_rejection_open_tournament").assertDoesNotExist()
        composeRule.onNodeWithTag("play_flow_rejection_dismiss").performClick()

        composeRule.runOnIdle { assertEquals(1, dismissCount) }
    }
}
