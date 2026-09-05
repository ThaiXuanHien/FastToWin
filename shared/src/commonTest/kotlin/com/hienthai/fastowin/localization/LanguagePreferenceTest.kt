package com.hienthai.fastowin.localization

import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguagePreferenceTest {
    @Test
    fun savedSelectionOverridesDeviceLanguage() {
        assertEquals(AppLanguage.BRAZILIAN_PORTUGUESE, resolveSavedLanguage("pt-BR", listOf("vi-VN")))
        assertEquals(AppLanguage.JAPANESE, resolveSavedLanguage("ja", emptyList()))
    }

    @Test
    fun systemSelectionUsesCurrentDeviceTagsOnEachResolution() {
        val preferences = AppPreferences()
        assertEquals(AppLanguage.VIETNAMESE, resolveSavedLanguage(preferences.languageCode, listOf("vi-VN")))
        assertEquals(AppLanguage.GERMAN, resolveSavedLanguage(preferences.languageCode, listOf("de-DE")))
        assertEquals(AppLanguage.ENGLISH, resolveSavedLanguage(preferences.languageCode, listOf("ar-EG")))
    }

    @Test
    fun unknownSavedCodeFollowsDeviceWithoutMutatingPreferences() {
        val preferences = ProtocolJson.decodeFromString<AppPreferences>(
            """{"languageCode":"future-code","soundEnabled":false}"""
        )
        assertEquals(AppLanguage.JAPANESE, resolveSavedLanguage(preferences.languageCode, listOf("ja-JP")))
        assertEquals("future-code", preferences.languageCode)
        assertEquals(false, preferences.soundEnabled)
        assertEquals(AppLanguage.ENGLISH, resolveSavedLanguage("", emptyList()))
    }
}
