package com.hienthai.fastowin.platform

import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey
import kotlin.test.*

class RewardedAdFeedbackTest {
    @Test fun `quota feedback renders cancellation alongside pending in checking timeout and retry states`() {
        assertBothVisible(RewardedAdShowResult.DISMISSED, TextKey.RewardedAdCancelled)
    }
    @Test fun `quota feedback renders show failure alongside pending in checking timeout and retry states`() {
        assertBothVisible(RewardedAdShowResult.UNAVAILABLE, TextKey.RewardedAdUnavailable)
    }
    private fun assertBothVisible(outcome: RewardedAdShowResult, key: TextKey) {
        for (loading in listOf(true, false, true)) {
            val state = RewardedAdGatewayState(isReady = true, isLoading = loading, isPendingVerification = true,
                presentationOutcome = outcome, notice = LocalizedText(TextKey.RewardedAdPending))
            assertEquals(listOf(key, TextKey.RewardedAdPending), rewardedAdFeedback(state).map { it.key })
        }
    }
}
