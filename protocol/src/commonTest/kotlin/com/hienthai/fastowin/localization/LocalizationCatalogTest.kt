package com.hienthai.fastowin.localization

import com.hienthai.fastowin.localization.catalogs.residualTexts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalizationCatalogTest {
    @Test
    fun shippedCatalogsContainOnlyVietnameseAndEnglish() {
        assertEquals(setOf("vi", "en"), allLocalizationCatalogs.keys.map { it.code }.toSet())
        assertEquals(setOf("vi", "en"), AppLanguage.entries.map { it.code }.toSet())
    }

    @Test
    fun shopDiscoveryCopyDescribesOnlyTheCurrentGemAndGoldEconomy() {
        val expected = mapOf(
            AppLanguage.VIETNAMESE to "Mua Gem hoặc đổi Gem lấy Vàng",
            AppLanguage.ENGLISH to "Buy Gems or exchange Gems for Gold",
        )

        expected.forEach { (language, copy) ->
            assertEquals(
                copy,
                allLocalizationCatalogs.getValue(language).texts.getValue(TextKey.ShopDiscoveryDescription),
                language.code,
            )
        }
    }

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

        AppLanguage.entries.filterNot { it == AppLanguage.ENGLISH }
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
            "zh-CN" to AppLanguage.ENGLISH,
            "zh-SG" to AppLanguage.ENGLISH,
            "zh-Hans-CN" to AppLanguage.ENGLISH,
            "zh-Hant-CN" to AppLanguage.ENGLISH,
            "zh-TW" to AppLanguage.ENGLISH,
            "pt-BR" to AppLanguage.ENGLISH,
            "pt-PT" to AppLanguage.ENGLISH,
            "ja-JP" to AppLanguage.ENGLISH,
            "ar-EG" to AppLanguage.ENGLISH,
        )
        cases.forEach { (tag, expected) ->
            assertEquals(expected, resolveAppLanguage("system", listOf(tag)), tag)
        }
    }

    @Test
    fun unsupportedPreferencesUseEnglishAndSystemSelectionUsesDevice() {
        assertEquals(AppLanguage.ENGLISH, resolveAppLanguage("de", listOf("vi-VN")))
        assertEquals(AppLanguage.ENGLISH, resolveAppLanguage("future", listOf("ja-JP")))
        assertEquals(AppLanguage.ENGLISH, resolveAppLanguage("system", emptyList()))
        assertEquals(AppLanguage.ENGLISH, resolveAppLanguage("system", listOf("ar", "fr-CA")))
        assertEquals(
            listOf(AppLanguage.VIETNAMESE, AppLanguage.ENGLISH),
            selectableAppLanguages
        )
    }

    @Test
    fun catalogsCoverEveryKeyAndHaveMatchingPlaceholders() {
        assertEquals(2, allLocalizationCatalogs.size)
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
    fun vietnameseUsesOtherAndMissingQuantitiesFallBackToEnglish() {
        assertEquals("2 người chơi", LocalizationService(AppLanguage.VIETNAMESE).quantity(QuantityKey.Players, 2))
        val vietnamese = allLocalizationCatalogs.getValue(AppLanguage.VIETNAMESE).copy(quantities = emptyMap())
        val service = LocalizationService(AppLanguage.VIETNAMESE, allLocalizationCatalogs + (AppLanguage.VIETNAMESE to vietnamese))
        assertEquals("1 player", service.quantity(QuantityKey.Players, 1))
        assertEquals("21 players", service.quantity(QuantityKey.Players, 21))
    }
}
