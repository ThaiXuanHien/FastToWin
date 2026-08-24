package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.RankedTier
import kotlin.test.Test
import kotlin.test.assertEquals

class SeasonRewardTest {
    @Test
    fun `season reward uses the highest tier reached`() {
        assertEquals(RankedTier.BRONZE, seasonRewardForPeakRating(1_099).tier)
        assertEquals(RankedTier.SILVER, seasonRewardForPeakRating(1_100).tier)
        assertEquals(RankedTier.GOLD, seasonRewardForPeakRating(1_499).tier)
        assertEquals(RankedTier.CHALLENGER, seasonRewardForPeakRating(2_700).tier)
        assertEquals(6_000, seasonRewardForPeakRating(2_700).gold)
        assertEquals(12, seasonRewardForPeakRating(2_700).gems)
    }
}
