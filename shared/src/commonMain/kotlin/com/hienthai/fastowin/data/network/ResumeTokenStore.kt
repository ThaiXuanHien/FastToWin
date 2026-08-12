package com.hienthai.fastowin.data.network

interface ResumeTokenStore {
    fun load(serverUrl: String): String?
    fun save(serverUrl: String, token: String)
    fun clear(serverUrl: String)
}

class InMemoryResumeTokenStore : ResumeTokenStore {
    private val tokens = mutableMapOf<String, String>()

    override fun load(serverUrl: String): String? = tokens[serverUrl]

    override fun save(serverUrl: String, token: String) {
        tokens[serverUrl] = token
    }

    override fun clear(serverUrl: String) {
        tokens.remove(serverUrl)
    }
}
