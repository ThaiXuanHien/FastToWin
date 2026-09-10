package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MissionDifficulty
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MissionRotationTest {
    @Test
    fun `daily rotation has one easy two normal and one advanced mission`() {
        val missions = MissionRotation.forPeriod(MissionPeriod.DAILY, LocalDate.of(2026, 9, 10))

        assertEquals(4, missions.size)
        assertEquals(MissionDifficulty.EASY, missions[0].difficulty)
        assertEquals(listOf(MissionDifficulty.NORMAL, MissionDifficulty.NORMAL), missions.drop(1).take(2).map { it.difficulty })
        assertTrue(missions[3].difficulty in setOf(MissionDifficulty.HARD, MissionDifficulty.ELITE))
        assertEquals(4, missions.map { it.code }.distinct().size)
    }

    @Test
    fun `weekly rotation has one normal two hard and one elite mission`() {
        val missions = MissionRotation.forPeriod(MissionPeriod.WEEKLY, LocalDate.of(2026, 9, 7))

        assertEquals(
            listOf(
                MissionDifficulty.NORMAL,
                MissionDifficulty.HARD,
                MissionDifficulty.HARD,
                MissionDifficulty.ELITE
            ),
            missions.map { it.difficulty }
        )
        assertEquals(4, missions.map { it.code }.distinct().size)
    }

    @Test
    fun `rotation is stable for a period and changes across representative periods`() {
        val date = LocalDate.of(2026, 9, 10)
        val monday = LocalDate.of(2026, 9, 7)

        assertEquals(
            MissionRotation.forPeriod(MissionPeriod.DAILY, date).map { it.code },
            MissionRotation.forPeriod(MissionPeriod.DAILY, date).map { it.code }
        )
        assertNotEquals(
            MissionRotation.forPeriod(MissionPeriod.DAILY, date).map { it.code },
            MissionRotation.forPeriod(MissionPeriod.DAILY, date.plusDays(1)).map { it.code }
        )
        assertNotEquals(
            MissionRotation.forPeriod(MissionPeriod.WEEKLY, monday).map { it.code },
            MissionRotation.forPeriod(MissionPeriod.WEEKLY, monday.plusWeeks(1)).map { it.code }
        )
    }
}
