package com.hienthai.fastowin.data.preferences

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AppThemeModeSerializer::class)
enum class AppThemeMode {
    DARK
}

// Decode legacy LIGHT/SYSTEM values without discarding the other preferences.
object AppThemeModeSerializer : KSerializer<AppThemeMode> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AppThemeMode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AppThemeMode {
        decoder.decodeString()
        return AppThemeMode.DARK
    }

    override fun serialize(encoder: Encoder, value: AppThemeMode) {
        encoder.encodeString(value.name)
    }
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
    // The game always uses the navy theme, including legacy saved preferences.
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val boardStyle: BoardStyle = BoardStyle.CLASSIC,
    val fontScale: AppFontScale = AppFontScale.STANDARD,
    val hasCompletedTutorial: Boolean = false,
    // Keep unknown codes readable when returning from a newer app version.
    val languageCode: String = "system"
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
