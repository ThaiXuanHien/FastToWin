package com.hienthai.fastowin.ui.screens

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
}
