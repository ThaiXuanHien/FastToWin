package com.hienthai.fastowin.state

import com.hienthai.fastowin.localization.*
import kotlin.test.*

class ClanRacePresentationTest {
    @Test fun `millionth units format without floating point loss or positive zero rounding`() {
        assertEquals("0.000001", raceUnitsText("1"))
        assertEquals("0.0001", raceUnitsText("100"))
        assertEquals("12", raceUnitsText("12000000"))
        assertEquals("99999999999999999999999999999999.999999", raceUnitsText("99999999999999999999999999999999999999"))
    }
    @Test fun `race context and per member reward copy exist in all twelve catalogs`() {
        for (language in AppLanguage.entries) {
            for (name in listOf("ClanRaceTitle", "ClanRacePrizes", "ClanRacePerMember", "ClanRaceEligibility", "ClanRaceGift", "ClanRaceWalletSource", "ClanRaceMailboxIntro", "ClanRaceGiftExpires")) {
                val key = assertNotNull(TextKey.entries.find { it.name == name })
                assertFalse(allLocalizationCatalogs.getValue(language).texts[key].isNullOrBlank(), "$language $name")
            }
            val gift = LocalizationService(language).text(TextKey.entries.first { it.name == "ClanRaceGift" },
                mapOf("week" to "2026-10-26", "clan" to "Name_{rank}", "rank" to 3))
            assertContains(gift, "2026-10-26")
            assertContains(gift, "Name_{rank}")
            assertContains(gift, "3")
            val expiresKey = assertNotNull(TextKey.entries.find { it.name == "ClanRaceGiftExpires" })
            assertContains(LocalizationService(language).text(expiresKey, mapOf("time" to "2026-11-02 00:00")), "2026-11-02 00:00")
        }
    }
}
