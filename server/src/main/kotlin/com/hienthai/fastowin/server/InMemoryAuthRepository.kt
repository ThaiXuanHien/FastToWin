package com.hienthai.fastowin.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryAuthRepository : AuthRepository {
    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, AccountCredentials>()
    private val sessions = mutableMapOf<String, StoredSession>()

    override suspend fun createAccount(account: NewAccount): Boolean = mutex.withLock {
        if (account.emailNormalized in accounts) return@withLock false
        accounts[account.emailNormalized] = AccountCredentials(account.userId, account.passwordHash)
        store(account.userId, account.session)
        true
    }

    override suspend fun findActiveAccount(emailNormalized: String): AccountCredentials? = mutex.withLock {
        accounts[emailNormalized]
    }

    override suspend fun createSession(userId: UUID, devicePlatform: String?, session: NewAuthSession) {
        mutex.withLock { store(userId, session) }
    }

    override suspend fun rotateSession(refreshTokenHash: String, replacement: NewAuthSession): UUID? = mutex.withLock {
        val current = sessions.remove(refreshTokenHash) ?: return@withLock null
        if (current.revoked || current.expiresAtMillis <= replacement.nowMillis) return@withLock null
        store(current.userId, replacement)
        current.userId
    }

    override suspend fun revokeSession(refreshTokenHash: String, nowMillis: Long): Boolean = mutex.withLock {
        val current = sessions[refreshTokenHash] ?: return@withLock false
        sessions[refreshTokenHash] = current.copy(revoked = true)
        true
    }

    private fun store(userId: UUID, session: NewAuthSession) {
        sessions[session.refreshTokenHash] = StoredSession(userId, session.refreshExpiresAtMillis)
    }

    private data class StoredSession(
        val userId: UUID,
        val expiresAtMillis: Long,
        val revoked: Boolean = false
    )
}
