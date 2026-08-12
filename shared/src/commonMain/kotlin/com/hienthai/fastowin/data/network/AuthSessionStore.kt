package com.hienthai.fastowin.data.network

import kotlinx.serialization.Serializable

@Serializable
data class StoredAuthSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMillis: Long,
    val refreshExpiresAtEpochMillis: Long
)

interface AuthSessionStore {
    fun load(serverUrl: String): StoredAuthSession?
    fun save(serverUrl: String, session: StoredAuthSession)
    fun clear(serverUrl: String)
}

class InMemoryAuthSessionStore : AuthSessionStore {
    private val sessions = mutableMapOf<String, StoredAuthSession>()

    override fun load(serverUrl: String): StoredAuthSession? = sessions[serverUrl]

    override fun save(serverUrl: String, session: StoredAuthSession) {
        sessions[serverUrl] = session
    }

    override fun clear(serverUrl: String) {
        sessions.remove(serverUrl)
    }
}
