package com.hienthai.fastowin.data.preferences

import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesTest {
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
