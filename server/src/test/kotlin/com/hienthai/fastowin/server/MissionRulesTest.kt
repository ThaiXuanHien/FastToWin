package com.hienthai.fastowin.server

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionRulesTest {
    @Test
    fun `mission rewards increase with weekly difficulty`() {
        assertEquals(
            mapOf(
                "DAILY_PLAY_3" to Triple(20, 100, 0),
                "DAILY_WIN_1" to Triple(25, 150, 0),
                "WEEKLY_CORRECT_100" to Triple(75, 400, 0),
                "WEEKLY_PERFECT_1" to Triple(120, 600, 2)
            ),
            MISSION_DEFINITIONS.associate { it.code to Triple(it.rewardXp, it.rewardGold, it.rewardGems) }
        )
    }

    @Test
    fun `daily and weekly periods reset at their boundaries`() {
        val sunday = LocalDate.of(2026, 8, 23)
        val daily = missionDefinition("DAILY_PLAY_3")!!
        val weekly = missionDefinition("WEEKLY_CORRECT_100")!!

        assertEquals(sunday, missionPeriodStart(daily, sunday))
        assertEquals(LocalDate.of(2026, 8, 17), missionPeriodStart(weekly, sunday))
        assertEquals(LocalDate.of(2026, 8, 24), missionPeriodStart(weekly, sunday.plusDays(1)))
    }
}
