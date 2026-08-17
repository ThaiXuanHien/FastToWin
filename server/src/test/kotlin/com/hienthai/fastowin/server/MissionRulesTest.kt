package com.hienthai.fastowin.server

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MissionRulesTest {
    @Test
    fun `mission rewards increase with weekly difficulty`() {
        assertEquals(
            mapOf(
                "DAILY_PLAY_3" to 20,
                "DAILY_WIN_1" to 15,
                "WEEKLY_CORRECT_100" to 50,
                "WEEKLY_PERFECT_1" to 75
            ),
            MISSION_DEFINITIONS.associate { it.code to it.rewardXp }
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
