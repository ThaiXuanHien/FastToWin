package com.hienthai.fastowin.ui.components

import com.hienthai.fastowin.protocol.RankedTier
import kotlin.test.Test
import kotlin.test.assertEquals

class SeasonProgressCardTest {
    @Test
    fun ratingProgressUsesTheCurrentTierRange() {
        assertEquals(RankedTier.SILVER, nextRankedTier(1_000))
        assertEquals(1_000f / 1_100f, ratingProgressWithinTier(1_000), 0.001f)
        assertEquals(0.5f, ratingProgressWithinTier(1_200), 0.001f)
        assertEquals(1f, ratingProgressWithinTier(2_400), 0.001f)
    }

    @Test
    fun remainingTimeUsesCompactVietnameseUnits() {
        val now = 1_000_000L
        assertEquals("Mùa đã kết thúc", seasonTimeRemaining(now, now))
        assertEquals("Còn 45 phút", seasonTimeRemaining(now + 45 * 60_000L, now))
        assertEquals("Còn 3 giờ 15 phút", seasonTimeRemaining(now + (3 * 60 + 15) * 60_000L, now))
        assertEquals("Còn 2 ngày 3 giờ", seasonTimeRemaining(now + 51 * 60 * 60_000L, now))
    }
}
