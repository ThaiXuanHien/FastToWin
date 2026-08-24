package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.seasonCosmeticReward
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

    @Test
    fun `season cosmetic is unique by season and follows tier type`() {
        val bronze = seasonCosmeticReward(3, "Mùa Ba", RankedTier.BRONZE)
        val silver = seasonCosmeticReward(3, "Mùa Ba", RankedTier.SILVER)
        val gold = seasonCosmeticReward(3, "Mùa Ba", RankedTier.GOLD)
        val nextSeasonGold = seasonCosmeticReward(4, "Mùa Bốn", RankedTier.GOLD)

        assertEquals(CosmeticType.TITLE, bronze.type)
        assertEquals(CosmeticType.TITLE, silver.type)
        assertEquals(CosmeticType.FRAME, gold.type)
        assertEquals("season_3_gold", gold.id)
        assertEquals("Khung Mùa Ba • Vàng", gold.name)
        kotlin.test.assertNotEquals(gold.id, nextSeasonGold.id)
    }
}
