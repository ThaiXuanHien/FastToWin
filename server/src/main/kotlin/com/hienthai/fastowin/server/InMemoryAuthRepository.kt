package com.hienthai.fastowin.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryAuthRepository : AuthRepository {
    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, AccountCredentials>()
    private val sessions = mutableMapOf<String, StoredSession>()
    private val sessionsByAccessToken = mutableMapOf<String, StoredSession>()
    private val displayNamesByUserId = mutableMapOf<UUID, String>()

    override suspend fun createAccount(account: NewAccount): Boolean = mutex.withLock {
        if (account.emailNormalized in accounts) return@withLock false
        accounts[account.emailNormalized] = AccountCredentials(
            account.userId,
            account.passwordHash,
            account.displayName
        )
        displayNamesByUserId[account.userId] = account.displayName
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
        sessionsByAccessToken.entries.removeAll { it.value.sessionId == current.sessionId }
        store(current.userId, replacement)
        current.userId
    }

    override suspend fun revokeSession(refreshTokenHash: String, nowMillis: Long): Boolean = mutex.withLock {
        val current = sessions[refreshTokenHash] ?: return@withLock false
        sessions[refreshTokenHash] = current.copy(revoked = true)
        sessionsByAccessToken.entries.firstOrNull { it.value.sessionId == current.sessionId }
            ?.setValue(current.copy(revoked = true))
        true
    }

    override suspend fun findActiveSession(
        accessTokenHash: String,
        nowMillis: Long
    ): AuthenticatedAccount? = mutex.withLock {
        val session = sessionsByAccessToken[accessTokenHash]
            ?.takeIf { !it.revoked && it.accessExpiresAtMillis > nowMillis }
            ?: return@withLock null
        val displayName = displayNamesByUserId[session.userId] ?: return@withLock null
        AuthenticatedAccount(session.userId, displayName)
    }

    override suspend fun upgradeGuest(upgrade: GuestUpgrade): GuestUpgradeResult =
        GuestUpgradeResult.Unsupported

    private fun store(userId: UUID, session: NewAuthSession) {
        val stored = StoredSession(
            sessionId = session.sessionId,
            userId = userId,
            accessExpiresAtMillis = session.accessExpiresAtMillis,
            expiresAtMillis = session.refreshExpiresAtMillis
        )
        sessions[session.refreshTokenHash] = stored
        sessionsByAccessToken[session.accessTokenHash] = stored
    }

    private data class StoredSession(
        val sessionId: UUID,
        val userId: UUID,
        val accessExpiresAtMillis: Long,
        val expiresAtMillis: Long,
        val revoked: Boolean = false
    )
}
