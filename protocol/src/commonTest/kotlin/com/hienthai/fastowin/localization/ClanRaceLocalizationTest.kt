package com.hienthai.fastowin.localization

import kotlin.test.*

class ClanRaceLocalizationTest {
    @Test fun `all race keys have twelve translations with matching placeholders`() {
        val keys = TextKey.entries.filter { it.name.startsWith("ClanRace") }
        assertTrue(keys.size >= 27)
        val placeholders = Regex("\\{[A-Za-z][A-Za-z0-9_]*\\}")
        for (key in keys) {
            val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts.getValue(key)
            for (language in AppLanguage.entries) {
                val text = allLocalizationCatalogs.getValue(language).texts.getValue(key)
                assertTrue(text.isNotBlank(), "$key $language")
                assertEquals(placeholders.findAll(english).map { it.value }.toSet(),
                    placeholders.findAll(text).map { it.value }.toSet(), "$key $language")
                // Shared spellings such as French "Page" are valid translations.
                if (language != AppLanguage.ENGLISH && key == TextKey.ClanRaceTitle)
                    assertNotEquals(english, text, "$key $language must be translated")
            }
        }
    }
}
