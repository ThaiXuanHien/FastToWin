package com.hienthai.fastowin.server

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyCheckInRulesTest {
    private val today = LocalDate.of(2026, 8, 17)

    @Test
    fun `first check in starts day one with gold and xp`() {
        val decision = dailyCheckInDecision(0, null, today)

        assertFalse(decision.claimedToday)
        assertEquals(1, decision.resultingStreak)
        assertEquals(1, decision.cycleDay)
        assertEquals(10, decision.rewardXp)
        assertEquals(50, decision.rewardGold)
        assertEquals(0, decision.rewardGems)
    }

    @Test
    fun `seven day cycle wraps while streak continues`() {
        val daySeven = dailyCheckInDecision(6, today.minusDays(1), today)
        val dayEight = dailyCheckInDecision(7, today.minusDays(1), today)

        assertEquals(7, daySeven.cycleDay)
        assertEquals(40, daySeven.rewardXp)
        assertEquals(200, daySeven.rewardGold)
        assertEquals(1, daySeven.rewardGems)
        assertEquals(1, dayEight.cycleDay)
        assertEquals(10, dayEight.rewardXp)
        assertEquals(8, dayEight.resultingStreak)
    }

    @Test
    fun `missing a day resets streak and reward cycle`() {
        val decision = dailyCheckInDecision(20, today.minusDays(2), today)

        assertEquals(1, decision.resultingStreak)
        assertEquals(1, decision.cycleDay)
        assertEquals(10, decision.rewardXp)
    }

    @Test
    fun `same day request is idempotent`() {
        val decision = dailyCheckInDecision(4, today, today)

        assertTrue(decision.claimedToday)
        assertEquals(4, decision.resultingStreak)
        assertEquals(4, decision.cycleDay)
        assertEquals(15, decision.rewardXp)
    }

    @Test
    fun `weekly rewards increase and reserve gem for day seven`() {
        assertEquals(135, (1..7).sumOf(::dailyCheckInReward))
        assertEquals(680, (1..7).sumOf(::dailyCheckInGoldReward))
        assertEquals(1, (1..7).sumOf(::dailyCheckInGemReward))
    }
}
