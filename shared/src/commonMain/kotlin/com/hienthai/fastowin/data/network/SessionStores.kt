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
    val refreshExpiresAtEpochMillis: Long,
    val emailVerified: Boolean = true,
    val refreshAttemptSecret: String? = null
)

interface AuthSessionStore {
    fun load(serverUrl: String): StoredAuthSession?
    fun save(serverUrl: String, session: StoredAuthSession)
    fun clear(serverUrl: String)
}

/** Opt-in durable refresh ownership. Legacy and browser stores do not implement this. */
interface RecoverableAuthSessionStore : AuthSessionStore {
    /** Persist or reuse pending before HTTP; null means the credential generation changed. */
    fun prepareRefresh(serverUrl: String, expected: StoredAuthSession): StoredAuthSession?
    /** Match generation + pending, merge tokens into current metadata and clear pending atomically. */
    fun completeRefresh(serverUrl: String, expected: StoredAuthSession, refreshed: StoredAuthSession): Boolean
    /** Metadata-only CAS; neither credentials nor pending may change. */
    fun updateSession(serverUrl: String, expected: StoredAuthSession, updated: StoredAuthSession): Boolean
    /** Clear only the expected generation + pending owner. */
    fun clearSession(serverUrl: String, expected: StoredAuthSession): Boolean
}

internal fun StoredAuthSession.sameGeneration(other: StoredAuthSession?): Boolean =
    other != null && userId == other.userId && accessToken == other.accessToken && refreshToken == other.refreshToken

internal fun StoredAuthSession.sameRefreshOwner(other: StoredAuthSession?): Boolean =
    sameGeneration(other) && refreshAttemptSecret == other?.refreshAttemptSecret

class AuthSessionStoreException(
    val code: String,
    message: String
) : IllegalStateException(message)

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
