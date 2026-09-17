package com.hienthai.fastowin.localization

import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguagePreferenceTest {
    @Test
    fun savedSelectionOverridesDeviceLanguage() {
        assertEquals(AppLanguage.ENGLISH, resolveSavedLanguage("en", listOf("vi-VN")))
        assertEquals(AppLanguage.VIETNAMESE, resolveSavedLanguage("vi", listOf("en-US")))
    }

    @Test
    fun systemSelectionUsesCurrentDeviceTagsOnEachResolution() {
        val preferences = AppPreferences()
        assertEquals(AppLanguage.VIETNAMESE, resolveSavedLanguage(preferences.languageCode, listOf("vi-VN")))
        assertEquals(AppLanguage.ENGLISH, resolveSavedLanguage(preferences.languageCode, listOf("de-DE")))
        assertEquals(AppLanguage.ENGLISH, resolveSavedLanguage(preferences.languageCode, listOf("ar-EG")))
    }

    @Test
    fun unknownSavedCodeMigratesToEnglishWithoutMutatingPreferences() {
        val preferences = ProtocolJson.decodeFromString<AppPreferences>(
            """{"languageCode":"future-code","soundEnabled":false}"""
        )
        assertEquals(AppLanguage.ENGLISH, resolveSavedLanguage(preferences.languageCode, listOf("vi-VN")))
        assertEquals("future-code", preferences.languageCode)
        assertEquals(false, preferences.soundEnabled)
        assertEquals(AppLanguage.ENGLISH, resolveSavedLanguage("", emptyList()))
    }

    @Test
    fun unsupportedSavedCodeIsPresentedAsEnglishInsteadOfSystem() {
        assertEquals("en", normalizeAppLanguageSelection("ja"))
        assertEquals("en", normalizeAppLanguageSelection("future-code"))
        assertEquals("system", normalizeAppLanguageSelection("system"))
        assertEquals("vi", normalizeAppLanguageSelection("VI"))
    }
}
