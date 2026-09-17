@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.hienthai.fastowin

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.platform.testTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlobalPromptSerializationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quotaAndPracticeSuppressInvitationInsteadOfRenderingMultipleRootModals() {
        composeRule.setContent {
            val selected = selectGlobalPrompt(
                hasPlayQuotaDialog = true,
                hasPracticeModePicker = true,
                hasPracticeLauncher = true,
                hasRoomInvitation = true,
                hasTournamentInvitation = false,
                hasChallengeError = false,
                hasFriendRequest = false,
                canShowWebUpdate = false,
                canShowSeasonSummary = false
            )
            GlobalPromptSlot(selected, GlobalPrompt.PLAY_QUOTA) {
                Box(Modifier.testTag("root_modal_quota"))
            }
            GlobalPromptSlot(selected, GlobalPrompt.PRACTICE_MODE_PICKER) {
                Box(Modifier.testTag("root_modal_practice_picker"))
            }
            GlobalPromptSlot(selected, GlobalPrompt.PRACTICE_LAUNCHER) {
                Box(Modifier.testTag("root_modal_practice_launcher"))
            }
            GlobalPromptSlot(selected, GlobalPrompt.ROOM_INVITATION) {
                Box(Modifier.testTag("root_modal_room_invitation"))
            }
        }

        assertEquals(1, composeRule.onAllNodesWithTag("root_modal_quota").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("root_modal_practice_picker").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("root_modal_practice_launcher").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("root_modal_room_invitation").fetchSemanticsNodes().size)
    }
}
