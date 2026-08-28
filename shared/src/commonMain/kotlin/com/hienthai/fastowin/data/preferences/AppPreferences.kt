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
    val visualEffectsEnabled: Boolean = true,
    // The approved 2D Arcade canvases use the navy theme. Light and system modes
    // remain available in Settings, while new installs start with the branded look.
    val themeMode: AppThemeMode = AppThemeMode.DARK,
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
