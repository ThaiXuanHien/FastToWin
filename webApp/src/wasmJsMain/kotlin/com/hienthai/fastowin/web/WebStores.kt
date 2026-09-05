@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import com.hienthai.fastowin.data.network.AuthSessionStore
import com.hienthai.fastowin.data.network.ResumeTokenStore
import com.hienthai.fastowin.data.network.StoredAuthSession
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppPreferencesStore
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal class WebAuthSessionStore : AuthSessionStore {
    override fun load(serverUrl: String): StoredAuthSession? {
        clearLegacyStoredAuthTokens()
        return localStorageGet(authKey(serverUrl))?.let { encoded ->
            runCatching { ProtocolJson.decodeFromString<StoredAuthSession>(encoded) }.getOrNull()
                ?.let { stored ->
                    if (stored.accessToken.isNotEmpty() || stored.refreshToken.isNotEmpty()) {
                        // Legacy Web sessions stored bearer credentials in localStorage. Remove them
                        // immediately; the user signs in once more to receive the protected cookie.
                        clear(serverUrl)
                        null
                    } else {
                        stored
                    }
                }
        }
    }

    override fun save(serverUrl: String, session: StoredAuthSession) {
        localStorageSet(
            authKey(serverUrl),
            ProtocolJson.encodeToString(
                session.copy(
                    accessToken = "",
                    refreshToken = "",
                    accessExpiresAtEpochMillis = 0L
                )
            )
        )
    }

    override fun clear(serverUrl: String) = localStorageRemove(authKey(serverUrl))
}

internal class WebResumeTokenStore : ResumeTokenStore {
    override fun load(serverUrl: String): String? = localStorageGet(resumeKey(serverUrl))
    override fun save(serverUrl: String, token: String) = localStorageSet(resumeKey(serverUrl), token)
    override fun clear(serverUrl: String) = localStorageRemove(resumeKey(serverUrl))
}

internal class WebAppPreferencesStore : AppPreferencesStore {
    override fun load(): AppPreferences = localStorageGet(PREFERENCES_KEY)?.let { encoded ->
        runCatching { ProtocolJson.decodeFromString<AppPreferences>(encoded) }.getOrNull()
    } ?: AppPreferences()

    override fun save(preferences: AppPreferences) {
        localStorageSet(PREFERENCES_KEY, ProtocolJson.encodeToString(preferences))
    }
}

private fun authKey(serverUrl: String) = "fasttowin.auth.$serverUrl"
private fun resumeKey(serverUrl: String) = "fasttowin.resume.$serverUrl"

private fun localStorageGet(key: String): String? = js("window.localStorage.getItem(key)")
private fun localStorageSet(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")
private fun localStorageRemove(key: String): Unit = js("window.localStorage.removeItem(key)")
private fun clearLegacyStoredAuthTokens(): Unit = js(
    "(() => { for (const key of Object.keys(window.localStorage)) { if (!key.startsWith('fasttowin.auth.')) continue; try { const value = JSON.parse(window.localStorage.getItem(key)); if (value && ((value.accessToken || '') !== '' || (value.refreshToken || '') !== '')) window.localStorage.removeItem(key); } catch (_) {} } })()"
)

private const val PREFERENCES_KEY = "fasttowin.preferences"
