package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MatchType
import kotlin.test.Test
import kotlin.test.assertEquals

class RewardEconomyTest {
    @Test
    fun `casual match rewards use the approved balance`() {
        assertEquals(RewardGrant(gold = 80, xp = 24), rewardFor(MatchType.CASUAL, MatchOutcome.WIN))
        assertEquals(RewardGrant(gold = 40, xp = 16), rewardFor(MatchType.CASUAL, MatchOutcome.DRAW))
        assertEquals(RewardGrant(gold = 20, xp = 8), rewardFor(MatchType.CASUAL, MatchOutcome.LOSS))
    }

    @Test
    fun `ranked match rewards use the approved balance`() {
        assertEquals(RewardGrant(gold = 100, xp = 30), rewardFor(MatchType.RANKED, MatchOutcome.WIN))
        assertEquals(RewardGrant(gold = 50, xp = 20), rewardFor(MatchType.RANKED, MatchOutcome.DRAW))
        assertEquals(RewardGrant(gold = 25, xp = 10), rewardFor(MatchType.RANKED, MatchOutcome.LOSS))
    }

    @Test
    fun `intentional leave grants no currency or experience`() {
        MatchType.entries.forEach { matchType ->
            MatchOutcome.entries.forEach { outcome ->
                assertEquals(
                    RewardGrant(),
                    rewardFor(matchType, outcome, intentionalLeave = true),
                    "$matchType $outcome must not reward an intentional leave"
                )
            }
        }
    }

    private fun rewardFor(
        matchType: MatchType,
        outcome: MatchOutcome,
        intentionalLeave: Boolean = false
    ): RewardGrant = RewardEconomy.matchReward(matchType, outcome, intentionalLeave)
}
