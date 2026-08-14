package com.hienthai.fastowin.data.preferences

import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import platform.Foundation.NSUserDefaults

class IosAppPreferencesStore : AppPreferencesStore {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    override fun load(): AppPreferences {
        val json = userDefaults.stringForKey(KEY_SETTINGS) ?: return AppPreferences()
        return runCatching { ProtocolJson.decodeFromString<AppPreferences>(json) }
            .getOrElse {
                userDefaults.removeObjectForKey(KEY_SETTINGS)
                AppPreferences()
            }
    }

    override fun save(preferences: AppPreferences) {
        userDefaults.setObject(ProtocolJson.encodeToString(preferences), forKey = KEY_SETTINGS)
    }

    private companion object {
        const val KEY_SETTINGS = "fast_to_win.app_settings"
    }
}
