package com.hienthai.fastowin

import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalPromptPriorityTest {
    @Test
    fun `only the highest priority global prompt is selected`() {
        assertEquals(
            GlobalPrompt.ROOM_INVITATION,
            selectGlobalPrompt(
                hasRoomInvitation = true,
                hasTournamentInvitation = true,
                hasChallengeError = true,
                hasFriendRequest = true,
                canShowWebUpdate = true,
                canShowSeasonSummary = true
            )
        )
        assertEquals(
            GlobalPrompt.CHALLENGE_ERROR,
            selectGlobalPrompt(
                hasRoomInvitation = false,
                hasTournamentInvitation = false,
                hasChallengeError = true,
                hasFriendRequest = true,
                canShowWebUpdate = true,
                canShowSeasonSummary = true
            )
        )
        assertEquals(
            GlobalPrompt.FRIEND_REQUEST,
            selectGlobalPrompt(
                hasRoomInvitation = false,
                hasTournamentInvitation = false,
                hasChallengeError = false,
                hasFriendRequest = true,
                canShowWebUpdate = true,
                canShowSeasonSummary = true
            )
        )
    }

    @Test
    fun `season summary waits until every higher priority prompt is gone`() {
        assertEquals(
            GlobalPrompt.SEASON_SUMMARY,
            selectGlobalPrompt(
                hasRoomInvitation = false,
                hasTournamentInvitation = false,
                hasChallengeError = false,
                hasFriendRequest = false,
                canShowWebUpdate = false,
                canShowSeasonSummary = true
            )
        )
    }
}
