package com.hienthai.fastowin.data.network

import platform.Foundation.NSUserDefaults

class IosResumeTokenStore : ResumeTokenStore {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun load(serverUrl: String): String? = preferences.stringForKey(key(serverUrl))

    override fun save(serverUrl: String, token: String) {
        preferences.setObject(token, forKey = key(serverUrl))
    }

    override fun clear(serverUrl: String) {
        preferences.removeObjectForKey(key(serverUrl))
    }

    private fun key(serverUrl: String): String = "resume_token.$serverUrl"
}
