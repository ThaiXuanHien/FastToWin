package com.hienthai.fastowin.ui.format

import com.hienthai.fastowin.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class A1NumberFormatTest {
    @Test
    fun vietnameseCompactFollowsDesignRule() {
        assertEquals("0", formatCompactNumber(0))
        assertEquals("320", formatCompactNumber(320))
        assertEquals("9.999", formatCompactNumber(9_999))
        assertEquals("10K", formatCompactNumber(10_000))
        assertEquals("12,5K", formatCompactNumber(12_500))
        assertEquals("12,5K", formatCompactNumber(12_599))
        assertEquals("999,9K", formatCompactNumber(999_999))
        assertEquals("1,25M", formatCompactNumber(1_250_000))
        assertEquals("1M", formatCompactNumber(1_000_000))
        assertEquals("1,2B", formatCompactNumber(1_234_567_890))
        assertEquals("-12,5K", formatCompactNumber(-12_500))
    }

    @Test
    fun englishUsesDotDecimalAndCommaGrouping() {
        assertEquals("9,999", formatCompactNumber(9_999, AppLanguage.ENGLISH))
        assertEquals("12.5K", formatCompactNumber(12_500, AppLanguage.ENGLISH))
        assertEquals("1.25M", formatCompactNumber(1_250_000, AppLanguage.ENGLISH))
    }

    @Test
    fun fullNumberGroupsThousands() {
        assertEquals("20.000", formatFullNumber(20_000))
        assertEquals("1.234.567", formatFullNumber(1_234_567))
        assertEquals("1,234,567", formatFullNumber(1_234_567, AppLanguage.ENGLISH))
        assertEquals("-500", formatFullNumber(-500))
    }
}
