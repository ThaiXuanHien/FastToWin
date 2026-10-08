package com.hienthai.fastowin.localization

import kotlin.test.*

class RewardEventLocalizationTest {
    @Test fun `event and settlement copy is complete in Vietnamese English and fallback languages`() {
        val keys = TextKey.entries.filter { it.name.startsWith("RewardEvent") || it.name.startsWith("RewardResult") }
        val vi = LocalizationService(AppLanguage.VIETNAMESE)
        val en = LocalizationService(AppLanguage.ENGLISH)
        assertEquals("Sự kiện thưởng",vi.text(TextKey.RewardEventsTitle))
        assertEquals("Reward events",en.text(TextKey.RewardEventsTitle))
        val arguments = mapOf("gold" to 200,"xp" to 60,"count" to 2,"modes" to "Casual","start" to "2026-10-01 00:00Z","end" to "2026-10-02 00:00Z","zone" to "UTC")
        for (language in AppLanguage.entries) for (key in keys) {
            val value = LocalizationService(language).text(key,arguments)
            assertTrue(value.isNotBlank())
            assertNotEquals(key.name,value)
            if (language != AppLanguage.VIETNAMESE) assertEquals(en.text(key,arguments),value)
        }
        assertEquals("GOLD ×2 · XP ×1",en.text(TextKey.RewardEventFactors,mapOf("gold" to "2","xp" to "1")))
    }
}
