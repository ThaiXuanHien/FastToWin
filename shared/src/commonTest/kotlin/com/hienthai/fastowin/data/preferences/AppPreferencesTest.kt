package com.hienthai.fastowin.data.preferences

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesTest {
    @Test
    fun legacyThemesMigrateToDarkWithoutLosingExistingSettings() {
        for (mode in listOf("LIGHT", "SYSTEM")) {
            val legacy = """{"soundEnabled":false,"vibrationEnabled":false,"visualEffectsEnabled":false,"themeMode":"$mode","boardStyle":"OCEAN","fontScale":"LARGE","hasCompletedTutorial":true,"languageCode":"en"}"""
            val decoded = ProtocolJson.decodeFromString<AppPreferences>(legacy)

            assertEquals(AppPreferences(
                soundEnabled = false,
                vibrationEnabled = false,
                visualEffectsEnabled = false,
                themeMode = AppThemeMode.DARK,
                boardStyle = BoardStyle.OCEAN,
                fontScale = AppFontScale.LARGE,
                hasCompletedTutorial = true,
                languageCode = "en"
            ), decoded)
            assertEquals(decoded, ProtocolJson.decodeFromString<AppPreferences>(ProtocolJson.encodeToString(decoded)))
        }
    }

    @Test
    fun onlyDarkThemeCanBeSelected() {
        assertEquals(listOf("DARK"), AppThemeMode.entries.map { it.name })
    }

    @Test
    fun languageSurvivesSerializedStorageWithOtherSettings() {
        val preferences = AppPreferences(soundEnabled = false, languageCode = "pt-BR")
        val encoded = ProtocolJson.encodeToString(preferences)
        assertEquals(preferences, ProtocolJson.decodeFromString<AppPreferences>(encoded))
    }

    @Test
    fun futureLanguageCodeDoesNotCausePreferencesToBeDiscarded() {
        val json = """{"soundEnabled":false,"languageCode":"future-language"}"""
        val decoded = ProtocolJson.decodeFromString<AppPreferences>(json)
        assertEquals("future-language", decoded.languageCode)
        assertEquals(false, decoded.soundEnabled)
    }

    @Test
    fun preferencesCanBeUpdatedAndReloaded() {
        val store = InMemoryAppPreferencesStore()
        val updated = AppPreferences(
            soundEnabled = false,
            vibrationEnabled = false,
            themeMode = AppThemeMode.DARK,
            boardStyle = BoardStyle.HIGH_CONTRAST,
            fontScale = AppFontScale.LARGE
        )

        store.save(updated)

        assertEquals(updated, store.load())
    }
}
