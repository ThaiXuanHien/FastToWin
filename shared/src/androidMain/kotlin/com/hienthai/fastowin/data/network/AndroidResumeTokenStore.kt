package com.hienthai.fastowin.data.network

import android.content.Context

class AndroidResumeTokenStore(context: Context) : ResumeTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(serverUrl: String): String? = preferences.getString(key(serverUrl), null)

    override fun save(serverUrl: String, token: String) {
        preferences.edit().putString(key(serverUrl), token).apply()
    }

    override fun clear(serverUrl: String) {
        preferences.edit().remove(key(serverUrl)).apply()
    }

    private fun key(serverUrl: String): String = "resume_token.$serverUrl"

    private companion object {
        const val PREFERENCES_NAME = "fast_to_win_session"
    }
}
