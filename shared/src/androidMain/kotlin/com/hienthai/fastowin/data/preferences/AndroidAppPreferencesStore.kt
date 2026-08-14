package com.hienthai.fastowin.data.preferences

import android.content.Context
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class AndroidAppPreferencesStore(context: Context) : AppPreferencesStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): AppPreferences {
        val json = preferences.getString(KEY_SETTINGS, null) ?: return AppPreferences()
        return runCatching { ProtocolJson.decodeFromString<AppPreferences>(json) }
            .getOrElse {
                preferences.edit().remove(KEY_SETTINGS).apply()
                AppPreferences()
            }
    }

    override fun save(preferences: AppPreferences) {
        this.preferences.edit()
            .putString(KEY_SETTINGS, ProtocolJson.encodeToString(preferences))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "fast_to_win_preferences"
        const val KEY_SETTINGS = "app_settings"
    }
}
