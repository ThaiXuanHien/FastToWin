package com.hienthai.fastowin.localization

import com.hienthai.fastowin.localization.catalogs.residualTexts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalizationCatalogTest {
    @Test
    fun phaseOneKeysAreTranslatedWithoutEnglishFallback() {
        val keys = setOf(
            TextKey.ReconnectingMatch,
            TextKey.ReconnectAction,
            TextKey.ReconnectBlockingHint,
            TextKey.MatchExpiredOfficialLoss,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH)
        allLocalizationCatalogs
            .filterKeys { it != AppLanguage.ENGLISH }
            .forEach { (language, catalog) ->
                keys.forEach { key ->
                    assertTrue(catalog.texts.getValue(key).isNotBlank(), "$language/$key")
                    assertNotEquals(english.texts.getValue(key), catalog.texts.getValue(key), "$language/$key")
                }
            }
    }

    @Test
    fun residualCopyIsTranslatedForEveryNonEnglishCatalog() {
        val english = residualTexts(AppLanguage.ENGLISH)
        val representativeKeys = listOf(
            TextKey.SettingsInstallSubtitle,
            TextKey.SettingsNotificationsSubtitle,
            TextKey.SettingsFeedbackSubtitle,
            TextKey.PushPromptDescription,
            TextKey.GuestSessionMissing,
            TextKey.ReconnectingMatch,
            TextKey.RewardReceivedSummary,
        )

        AppLanguage.entries.filterNot { it in setOf(AppLanguage.ENGLISH, AppLanguage.VIETNAMESE) }
            .forEach { language ->
                val translated = residualTexts(language)
                assertEquals(english.keys, translated.keys, language.code)
                assertNotEquals(english, translated, language.code)
                representativeKeys.forEach { key ->
                    assertNotEquals(english.getValue(key), translated.getValue(key), "${language.code}/$key")
                }
            }
    }

    @Test
    fun resolvesDeviceTagsWithoutSelectingUnsupportedScriptsOrRegions() {
        val cases = mapOf(
            "vi-VN" to AppLanguage.VIETNAMESE,
            "EN_us" to AppLanguage.ENGLISH,
            "zh-CN" to AppLanguage.SIMPLIFIED_CHINESE,
            "zh-SG" to AppLanguage.SIMPLIFIED_CHINESE,
            "zh-Hans-CN" to AppLanguage.SIMPLIFIED_CHINESE,
            "zh-Hant-CN" to AppLanguage.ENGLISH,
            "zh-TW" to AppLanguage.ENGLISH,
            "pt-BR" to AppLanguage.BRAZILIAN_PORTUGUESE,
            "pt-PT" to AppLanguage.ENGLISH,
            "ja-JP" to AppLanguage.JAPANESE,
            "ar-EG" to AppLanguage.ENGLISH,
        )
        cases.forEach { (tag, expected) ->
            assertEquals(expected, resolveAppLanguage("system", listOf(tag)), tag)
        }
    }

    @Test
    fun explicitPreferenceWinsAndUnknownPreferenceUsesDevice() {
        assertEquals(AppLanguage.GERMAN, resolveAppLanguage("de", listOf("vi-VN")))
        assertEquals(AppLanguage.JAPANESE, resolveAppLanguage("future", listOf("ja-JP")))
        assertEquals(AppLanguage.ENGLISH, resolveAppLanguage("system", emptyList()))
        assertEquals(AppLanguage.FRENCH, resolveAppLanguage("system", listOf("ar", "fr-CA")))
    }

    @Test
    fun catalogsCoverEveryKeyAndHaveMatchingPlaceholders() {
        assertEquals(12, allLocalizationCatalogs.size)
        assertEquals(AppLanguage.entries.toSet(), allLocalizationCatalogs.keys)
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH)
        val tokens = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
        fun placeholders(text: String) = tokens.findAll(text).map { it.groupValues[1] }.toSet()
        allLocalizationCatalogs.forEach { (language, catalog) ->
            assertEquals(TextKey.entries.toSet(), catalog.texts.keys, language.code)
            assertEquals(QuantityKey.entries.toSet(), catalog.quantities.keys, language.code)
            catalog.texts.forEach { (key, value) ->
                assertTrue(value.isNotBlank(), "$language/$key")
                assertEquals(placeholders(english.texts.getValue(key)), placeholders(value))
            }
            catalog.quantities.forEach { (key, forms) ->
                assertTrue(PluralCategory.OTHER in forms)
                forms.values.forEach { value ->
                    assertTrue(value.isNotBlank())
                    assertEquals(placeholders(english.quantities.getValue(key).getValue(PluralCategory.OTHER)), placeholders(value))
                }
            }
        }
    }

    @Test
    fun rendersTranslatedTextAndPreservesUserArgumentsLiterally() {
        val service = LocalizationService(AppLanguage.VIETNAMESE)
        assertEquals("Hủy", service.text(TextKey.Cancel))
        assertEquals("Chào Hien {count} $!", service.text(TextKey.WelcomePlayer, mapOf("player" to "Hien {count} $")))
    }

    @Test
    fun sensitiveAuthCopyRendersWithoutLocalizationArguments() {
        val sensitiveKeys = listOf(
            TextKey.VerifyEmailDescription,
            TextKey.DevVerificationCode,
            TextKey.DevResetCode,
        )

        AppLanguage.entries.forEach { language ->
            val service = LocalizationService(language)
            sensitiveKeys.forEach { key ->
                assertTrue(service.text(key).isNotBlank(), "${language.code}/$key")
            }
        }
    }

    @Test
    fun missingArgumentFailsWithoutLeakingArgumentValues() {
        val error = assertFailsWith<IllegalArgumentException> {
            LocalizationService(AppLanguage.ENGLISH).text(TextKey.WelcomePlayer, mapOf("wrong" to "private-value"))
        }
        assertTrue("private-value" !in error.message.orEmpty())
    }

    @Test
    fun missingOrBlankTranslationFallsBackToEnglish() {
        val vietnamese = allLocalizationCatalogs.getValue(AppLanguage.VIETNAMESE)
        val catalog = vietnamese.copy(texts = vietnamese.texts - TextKey.Cancel + (TextKey.Save to " "))
        val service = LocalizationService(AppLanguage.VIETNAMESE, allLocalizationCatalogs + (AppLanguage.VIETNAMESE to catalog))
        assertEquals("Cancel", service.text(TextKey.Cancel))
        assertEquals("Save", service.text(TextKey.Save))
    }

    @Test
    fun englishQuantitiesUseCountAndCannotBeOverriddenByArguments() {
        val service = LocalizationService(AppLanguage.ENGLISH)
        assertEquals("1 player", service.quantity(QuantityKey.Players, 1))
        assertEquals("0 players", service.quantity(QuantityKey.Players, 0))
        assertEquals("2 players", service.quantity(QuantityKey.Players, 2, mapOf("count" to 999)))
        assertEquals("1 clan", service.quantity(QuantityKey.Clans, 1))
        assertEquals("2 clans", service.quantity(QuantityKey.Clans, 2))
        assertEquals("1 member", service.quantity(QuantityKey.Members, 1))
        assertEquals("2 members", service.quantity(QuantityKey.Members, 2))
        assertFailsWith<IllegalArgumentException> { service.quantity(QuantityKey.Players, -1) }
    }

    @Test
    fun russianQuantitiesHandleTeenAndLastDigitBoundaries() {
        val service = LocalizationService(AppLanguage.RUSSIAN)
        mapOf(0 to "0 игроков", 1 to "1 игрок", 2 to "2 игрока", 5 to "5 игроков",
            11 to "11 игроков", 14 to "14 игроков", 21 to "21 игрок", 22 to "22 игрока",
            25 to "25 игроков", 101 to "101 игрок", 111 to "111 игроков").forEach { (count, expected) ->
            assertEquals(expected, service.quantity(QuantityKey.Players, count))
        }
        assertEquals("1 матч", service.quantity(QuantityKey.Matches, 1))
        assertEquals("2 матча", service.quantity(QuantityKey.Matches, 2))
        assertEquals("5 матчей", service.quantity(QuantityKey.Matches, 5))
        assertEquals("21 сезон", service.quantity(QuantityKey.Seasons, 21))
        assertEquals("1 участник", service.quantity(QuantityKey.Members, 1))
        assertEquals("2 участника", service.quantity(QuantityKey.Members, 2))
        assertEquals("5 участников", service.quantity(QuantityKey.Members, 5))
    }

    @Test
    fun uninflectedLanguagesUseOtherAndPluralFallbackStaysInOneLanguage() {
        assertEquals("2 người chơi", LocalizationService(AppLanguage.VIETNAMESE).quantity(QuantityKey.Players, 2))
        val russian = allLocalizationCatalogs.getValue(AppLanguage.RUSSIAN).copy(quantities = emptyMap())
        val service = LocalizationService(AppLanguage.RUSSIAN, allLocalizationCatalogs + (AppLanguage.RUSSIAN to russian))
        assertEquals("21 players", service.quantity(QuantityKey.Players, 21))
    }
}
