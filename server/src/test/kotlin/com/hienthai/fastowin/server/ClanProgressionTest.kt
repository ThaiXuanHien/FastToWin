package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanDonationCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClanProgressionTest {
    @Test
    fun `gold and gems grant the approved clan experience`() {
        assertEquals(10, ClanProgression.donationExperience(ClanDonationCurrency.GOLD, 100))
        assertEquals(50, ClanProgression.donationExperience(ClanDonationCurrency.GOLD, 500))
        assertEquals(10, ClanProgression.donationExperience(ClanDonationCurrency.GEMS, 1))
        assertEquals(500, ClanProgression.donationExperience(ClanDonationCurrency.GEMS, 50))
    }

    @Test
    fun `invalid donation amounts do not grant experience`() {
        assertNull(ClanProgression.donationExperience(ClanDonationCurrency.GOLD, 0))
        assertNull(ClanProgression.donationExperience(ClanDonationCurrency.GOLD, 99))
        assertNull(ClanProgression.donationExperience(ClanDonationCurrency.GOLD, 150))
        assertNull(ClanProgression.donationExperience(ClanDonationCurrency.GEMS, 0))
        assertNull(ClanProgression.donationExperience(ClanDonationCurrency.GEMS, -1))
        assertNull(ClanProgression.donationExperience(ClanDonationCurrency.GEMS, Int.MAX_VALUE))
    }

    @Test
    fun `clan level follows the increasing curve and stops at fifty`() {
        assertEquals(1, ClanProgression.levelForExperience(0))
        assertEquals(1, ClanProgression.levelForExperience(999))
        assertEquals(2, ClanProgression.levelForExperience(1_000))
        assertEquals(2, ClanProgression.levelForExperience(2_499))
        assertEquals(3, ClanProgression.levelForExperience(2_500))
        assertEquals(50, ClanProgression.levelForExperience(Long.MAX_VALUE))
    }

    @Test
    fun `level progress reports current and next experience`() {
        assertEquals(
            ClanLevelProgress(level = 1, currentExperience = 0, nextLevelExperience = 1_000),
            ClanProgression.progress(0)
        )
        assertEquals(
            ClanLevelProgress(level = 2, currentExperience = 400, nextLevelExperience = 1_500),
            ClanProgression.progress(1_400)
        )
        assertEquals(
            ClanLevelProgress(level = 50, currentExperience = 0, nextLevelExperience = 0),
            ClanProgression.progress(Long.MAX_VALUE)
        )
    }
}
