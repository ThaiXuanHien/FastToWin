package com.hienthai.fastowin.data.preferences

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
enum class BoardStyle {
    CLASSIC,
    OCEAN,
    HIGH_CONTRAST
}

@Serializable
enum class AppFontScale(val multiplier: Float) {
    COMPACT(0.9f),
    STANDARD(1f),
    LARGE(1.15f)
}

@Serializable
data class AppPreferences(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val boardStyle: BoardStyle = BoardStyle.CLASSIC,
    val fontScale: AppFontScale = AppFontScale.STANDARD,
    val hasCompletedTutorial: Boolean = false
)

interface AppPreferencesStore {
    fun load(): AppPreferences
    fun save(preferences: AppPreferences)
}

class InMemoryAppPreferencesStore(
    initial: AppPreferences = AppPreferences()
) : AppPreferencesStore {
    private var preferences = initial

    override fun load(): AppPreferences = preferences

    override fun save(preferences: AppPreferences) {
        this.preferences = preferences
    }
}
