package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.DailyCheckInRewardSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileCalendarTest {
    @Test
    fun `iso date parser validates calendar dates`() {
        assertEquals(ProfileCalendarDate(2026, 8, 17), "2026-08-17".toProfileCalendarDate())
        assertEquals(ProfileCalendarDate(2028, 2, 29), "2028-02-29".toProfileCalendarDate())
        assertNull("2026-02-29".toProfileCalendarDate())
        assertNull("not-a-date".toProfileCalendarDate())
    }

    @Test
    fun `calendar starts on monday and includes leap day`() {
        val august2026 = profileCalendarCells(ProfileCalendarMonth(2026, 8))
        assertEquals(List(5) { null }, august2026.take(5))
        assertEquals(1, august2026[5])
        assertEquals(31, august2026.count { it != null })

        val february2028 = profileCalendarCells(ProfileCalendarMonth(2028, 2))
        assertEquals(29, february2028.count { it != null })
    }

    @Test
    fun `month navigation crosses year boundaries`() {
        assertEquals(ProfileCalendarMonth(2026, 1), ProfileCalendarMonth(2025, 12).shift(1))
        assertEquals(ProfileCalendarMonth(2025, 12), ProfileCalendarMonth(2026, 1).shift(-1))
    }

    @Test
    fun `month shows every date with today's reward and projects the existing streak across months`() {
        val checkIn = DailyCheckInSnapshot(todayDate = "2028-01-30", cycleDay = 6,
            todayRewardGold = 123, todayRewardXp = 27, nextRewardGold = 200, nextRewardXp = 40, nextRewardGems = 1)
        val january = monthlyCheckInRewards(ProfileCalendarMonth(2028, 1), checkIn)
        val february = monthlyCheckInRewards(ProfileCalendarMonth(2028, 2), checkIn)
        assertEquals((1..31).toList(), january.map { it.day })
        assertEquals((1..29).toList(), february.map { it.day })
        assertEquals(123, january[29].reward?.gold)
        assertEquals(27, january[29].reward?.xp)
        assertEquals(1, january[30].reward?.gems)
        assertEquals(50, february.first().reward?.gold)
        assertNull(january[28].reward)
        assertEquals(28, monthlyCheckInRewards(ProfileCalendarMonth(2027, 2), checkIn).size)
        assertEquals(30, monthlyCheckInRewards(ProfileCalendarMonth(2027, 4), checkIn).size)
    }

    @Test
    fun `recorded reward amounts take precedence and unsupported history remains claimed`() {
        val earned = DailyCheckInRewardSnapshot(gold = 315, xp = 14, gems = 2)
        val checkIn = DailyCheckInSnapshot(
            todayDate = "2028-03-01",
            historyDates = listOf("2028-02-28", "2028-02-29"),
            historyRewards = mapOf("2028-02-29" to earned)
        )
        val february = monthlyCheckInRewards(ProfileCalendarMonth(2028, 2), checkIn)
        assertEquals(earned, february.last().reward)
        assertEquals(true, february.last().claimed)
        assertEquals(true, february[27].claimed)
        assertNull(february[27].reward)
        assertEquals(false, february[26].claimed)
    }
}
