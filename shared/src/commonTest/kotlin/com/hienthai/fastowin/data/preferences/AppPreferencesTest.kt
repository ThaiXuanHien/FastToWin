package com.hienthai.fastowin.data.preferences

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesTest {
    @Test
    fun legacyPreferencesFollowSystemWithoutLosingExistingSettings() {
        val legacy = """{"soundEnabled":false,"vibrationEnabled":false,"visualEffectsEnabled":false,"themeMode":"LIGHT","boardStyle":"OCEAN","fontScale":"LARGE","hasCompletedTutorial":true}"""
        val decoded = ProtocolJson.decodeFromString<AppPreferences>(legacy)

        assertEquals(AppPreferences(
            soundEnabled = false,
            vibrationEnabled = false,
            visualEffectsEnabled = false,
            themeMode = AppThemeMode.LIGHT,
            boardStyle = BoardStyle.OCEAN,
            fontScale = AppFontScale.LARGE,
            hasCompletedTutorial = true,
            languageCode = "system"
        ), decoded)
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
