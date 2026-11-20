package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.PendingEmailVerificationResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryAuthRepository : AuthRepository {
    private val mutex = Mutex()
    private val recoveries = mutableMapOf<UUID, RefreshRecovery>()
    private val accounts = mutableMapOf<String, AccountCredentials>()
    private val sessions = mutableMapOf<String, StoredSession>()
    private val sessionsByAccessToken = mutableMapOf<String, StoredSession>()
    private val displayNamesByUserId = mutableMapOf<UUID, String>()
    private val passwordResets = mutableMapOf<String, StoredPasswordReset>()
    private val emailVerifications = mutableMapOf<UUID, StoredEmailVerification>()
    private var emailVerificationSequence = 0L

    override suspend fun createAccount(account: NewAccount): Boolean = mutex.withLock {
        if (account.referralCode != null) throw InvalidReferralCodeException()
        if (account.emailNormalized in accounts) return@withLock false
        accounts[account.emailNormalized] = AccountCredentials(
            account.userId,
            account.passwordHash,
            account.displayName,
            emailVerified = false
        )
        displayNamesByUserId[account.userId] = account.displayName
        store(account.userId, account.devicePlatform, account.session)
        true
    }

    override suspend fun findActiveAccount(emailNormalized: String): AccountCredentials? = mutex.withLock {
        accounts[emailNormalized]
    }

    override suspend fun findActiveAccountById(userId: UUID): AccountCredentials? = mutex.withLock {
        accounts.values.firstOrNull { it.userId == userId }
    }

    override suspend fun replaceSessions(userId: UUID, devicePlatform: String?, session: NewAuthSession) {
        mutex.withLock {
            revokeUserSessions(userId)
            store(userId, devicePlatform, session)
        }
    }

    override suspend fun rotateSession(refreshTokenHash: String, replacement: NewAuthSession): UUID? = mutex.withLock {
        val current = sessions[refreshTokenHash] ?: return@withLock null
        if (current.revoked || current.expiresAtMillis <= replacement.nowMillis) return@withLock null
        sessions.remove(refreshTokenHash)
        recoveries.remove(current.sessionId)
        sessionsByAccessToken.entries.removeAll { it.value.sessionId == current.sessionId }
        val rotated = current.copy(
            accessExpiresAtMillis = replacement.accessExpiresAtMillis,
            expiresAtMillis = replacement.refreshExpiresAtMillis,
            lastSeenAtMillis = replacement.nowMillis,
            revoked = false
        )
        sessions[replacement.refreshTokenHash] = rotated
        sessionsByAccessToken[replacement.accessTokenHash] = rotated
        current.userId
    }

    override suspend fun rotateRecoverableSession(rotation: RefreshRotation): AuthSessionResponse? = mutex.withLock {
        val receipt = recoveries.values.firstOrNull { it.predecessorHash == rotation.predecessorHash && it.attemptHash == rotation.attemptHash }
        if (receipt != null) {
            val current = sessions[receipt.successorRefreshHash] ?: return@withLock null
            val now = rotation.nowMillis()
            if (current.sessionId != receipt.sessionId || current.revoked || current.expiresAtMillis <= now ||
                sessionsByAccessToken[receipt.successorAccessHash]?.sessionId != current.sessionId ||
                accounts.values.none { it.userId == current.userId } || receipt.recoveryExpiresAtMillis <= now) return@withLock null
            return@withLock RefreshRecoveryCodec.open(receipt, rotation.predecessorToken, rotation.attemptSecret)
        }
        val current = sessions[rotation.predecessorHash] ?: return@withLock null
        val now = rotation.nowMillis()
        if (current.revoked || current.expiresAtMillis <= now || accounts.values.none { it.userId == current.userId }) return@withLock null
        val (replacement, response) = rotation.issue(current.userId, now)
        // Authenticate and encode before changing any state, so codec failure leaves the old generation intact.
        val created = rotation.receipt(current.sessionId, replacement, response)
        val rotated = current.copy(accessExpiresAtMillis = replacement.accessExpiresAtMillis,
            expiresAtMillis = replacement.refreshExpiresAtMillis, lastSeenAtMillis = now)
        sessions.remove(rotation.predecessorHash)
        sessionsByAccessToken.entries.removeAll { it.value.sessionId == current.sessionId }
        sessions[replacement.refreshTokenHash] = rotated
        sessionsByAccessToken[replacement.accessTokenHash] = rotated
        recoveries[current.sessionId] = created
        response
    }

    override suspend fun revokePendingSession(predecessorHash: String, attemptHash: String, nowMillis: Long): Boolean = mutex.withLock {
        val receipt = recoveries.values.firstOrNull { it.predecessorHash == predecessorHash && it.attemptHash == attemptHash }
        val token = if (sessions.containsKey(predecessorHash)) predecessorHash else {
            if (receipt == null || receipt.metadataExpiresAtMillis <= nowMillis) return@withLock false
            val candidate = sessions[receipt.successorRefreshHash] ?: return@withLock false
            if (candidate.sessionId != receipt.sessionId || sessionsByAccessToken[receipt.successorAccessHash]?.sessionId != candidate.sessionId) return@withLock false
            receipt.successorRefreshHash
        }
        val current = sessions[token] ?: return@withLock false
        sessions[token] = current.copy(revoked = true)
        sessionsByAccessToken.entries.filter { it.value.sessionId == current.sessionId }.forEach { it.setValue(current.copy(revoked = true)) }
        recoveries.remove(current.sessionId)
        true
    }

    override suspend fun cleanupRefreshRecoveries(nowMillis: Long, batchSize: Int): Int = mutex.withLock {
        require(batchSize in 1..1000)
        val expired = recoveries.values.filter { it.metadataExpiresAtMillis <= nowMillis ||
            (it.recoveryExpiresAtMillis <= nowMillis && it.ciphertext != null) }.take(batchSize)
        expired.forEach {
            if (it.metadataExpiresAtMillis <= nowMillis) recoveries.remove(it.sessionId)
            else recoveries[it.sessionId] = it.copy(nonce = null, ciphertext = null)
        }
        expired.size
    }

    override suspend fun revokeSession(refreshTokenHash: String, nowMillis: Long): Boolean = mutex.withLock {
        val current = sessions[refreshTokenHash] ?: return@withLock false
        recoveries.remove(current.sessionId)
        sessions[refreshTokenHash] = current.copy(revoked = true)
        sessionsByAccessToken.entries.firstOrNull { it.value.sessionId == current.sessionId }
            ?.setValue(current.copy(revoked = true))
        true
    }

    override suspend fun findActiveSession(
        accessTokenHash: String,
        nowMillis: Long
    ): AuthenticatedAccount? = mutex.withLock {
        val session = activeAccessSession(accessTokenHash, nowMillis) ?: return@withLock null
        val displayName = displayNamesByUserId[session.userId] ?: return@withLock null
        val account = accounts.values.firstOrNull { it.userId == session.userId } ?: return@withLock null
        AuthenticatedAccount(session.userId, displayName, session.sessionId, account.emailVerified, account.supportEmailVerificationExempt)
    }

    override suspend fun listActiveSessions(
        userId: UUID,
        nowMillis: Long
    ): List<StoredAccountSession> = mutex.withLock {
        sessions.values
            .filter { it.userId == userId && !it.revoked && it.expiresAtMillis > nowMillis }
            .distinctBy { it.sessionId }
            .sortedByDescending { it.lastSeenAtMillis }
            .map { session ->
                StoredAccountSession(
                    sessionId = session.sessionId,
                    devicePlatform = session.devicePlatform,
                    createdAtMillis = session.createdAtMillis,
                    lastSeenAtMillis = session.lastSeenAtMillis,
                    expiresAtMillis = session.expiresAtMillis
                )
            }
    }

    override suspend fun revokeSessionById(
        userId: UUID,
        sessionId: UUID,
        nowMillis: Long
    ): Boolean = mutex.withLock {
        val matching = sessions.entries.filter { (_, session) ->
            session.userId == userId && session.sessionId == sessionId && !session.revoked
        }
        if (matching.isEmpty()) return@withLock false
        recoveries.remove(sessionId)
        matching.forEach { (token, session) -> sessions[token] = session.copy(revoked = true, lastSeenAtMillis = nowMillis) }
        sessionsByAccessToken.entries.forEach { entry ->
            if (entry.value.userId == userId && entry.value.sessionId == sessionId) {
                entry.setValue(entry.value.copy(revoked = true, lastSeenAtMillis = nowMillis))
            }
        }
        true
    }

    override suspend fun revokeAllSessions(userId: UUID, nowMillis: Long): Int = mutex.withLock {
        val activeSessionIds = sessions.values
            .filter { it.userId == userId && !it.revoked }
            .mapTo(mutableSetOf()) { it.sessionId }
        sessions.entries.forEach { entry ->
            if (entry.value.userId == userId && !entry.value.revoked) {
                entry.setValue(entry.value.copy(revoked = true, lastSeenAtMillis = nowMillis))
            }
        }
        sessionsByAccessToken.entries.forEach { entry ->
            if (entry.value.userId == userId && !entry.value.revoked) {
                entry.setValue(entry.value.copy(revoked = true, lastSeenAtMillis = nowMillis))
            }
        }
        activeSessionIds.forEach(recoveries::remove)
        activeSessionIds.size
    }

    override suspend fun upgradeGuest(upgrade: GuestUpgrade): GuestUpgradeResult =
        GuestUpgradeResult.Unsupported

    override suspend fun updatePasswordAndRevokeSessions(
        userId: UUID,
        passwordHash: String,
        nowMillis: Long
    ): Boolean = mutex.withLock {
        val entry = accounts.entries.firstOrNull { it.value.userId == userId } ?: return@withLock false
        accounts[entry.key] = entry.value.copy(passwordHash = passwordHash)
        revokeUserSessions(userId)
        true
    }

    override suspend fun createPasswordReset(
        emailNormalized: String,
        reset: NewPasswordReset
    ): Boolean = mutex.withLock {
        val account = accounts[emailNormalized] ?: return@withLock false
        passwordResets.entries.removeAll { it.value.userId == account.userId }
        passwordResets[reset.tokenHash] = StoredPasswordReset(
            userId = account.userId,
            emailNormalized = emailNormalized,
            expiresAtMillis = reset.expiresAtMillis
        )
        true
    }

    override suspend fun consumePasswordReset(
        emailNormalized: String,
        tokenHash: String,
        passwordHash: String,
        nowMillis: Long
    ): Boolean = mutex.withLock {
        val reset = passwordResets.remove(tokenHash)
            ?.takeIf { it.emailNormalized == emailNormalized && it.expiresAtMillis > nowMillis }
            ?: return@withLock false
        val account = accounts[emailNormalized]
            ?.takeIf { it.userId == reset.userId }
            ?: return@withLock false
        accounts[emailNormalized] = account.copy(passwordHash = passwordHash)
        revokeUserSessions(account.userId)
        true
    }

    override suspend fun stageEmailVerification(
        userId: UUID,
        verification: NewEmailVerification
    ): EmailVerificationDestination? = mutex.withLock {
        val entry = accounts.entries.firstOrNull { it.value.userId == userId } ?: return@withLock null
        if (!entry.value.emailVerified) {
            emailVerifications[verification.id] = StoredEmailVerification(
                userId = userId,
                codeHash = verification.codeHash,
                expiresAtMillis = verification.expiresAtMillis,
                sequence = ++emailVerificationSequence
            )
        }
        EmailVerificationDestination(entry.key, entry.value.emailVerified, entry.value.supportEmailVerificationExempt)
    }

    override suspend fun finishEmailVerification(userId: UUID, verificationId: UUID, delivered: Boolean): Boolean =
        mutex.withLock {
            val staged = emailVerifications[verificationId]?.takeIf { it.userId == userId } ?: return@withLock false
            if (!delivered) {
                if (!staged.delivered) emailVerifications.remove(verificationId)
                return@withLock false
            }
            emailVerifications[verificationId] = staged.copy(delivered = true)
            emailVerifications.entries.removeAll { it.value.userId == userId && it.value.sequence < staged.sequence }
            true
        }

    override suspend fun findEmailVerificationStatus(
        accessTokenHash: String,
        nowMillis: Long
    ): PendingEmailVerificationResponse? = mutex.withLock {
        val session = activeAccessSession(accessTokenHash, nowMillis) ?: return@withLock null
        val account = accounts.entries.firstOrNull { it.value.userId == session.userId } ?: return@withLock null
        PendingEmailVerificationResponse(account.key, account.value.emailVerified)
    }

    override suspend fun changePendingEmail(
        userId: UUID,
        accessTokenHash: String,
        emailNormalized: String,
        nowMillis: Long
    ): PendingEmailVerificationResult = mutex.withLock {
        if (activeAccessSession(accessTokenHash, nowMillis)?.userId != userId) {
            return@withLock PendingEmailVerificationResult.InvalidAccessToken
        }
        val account = accounts.entries.firstOrNull { it.value.userId == userId }
            ?: return@withLock PendingEmailVerificationResult.InvalidAccessToken
        if (account.value.emailVerified || account.value.supportEmailVerificationExempt) {
            return@withLock PendingEmailVerificationResult.EmailAlreadyVerified
        }
        if (account.key != emailNormalized) {
            if (emailNormalized in accounts) return@withLock PendingEmailVerificationResult.EmailAlreadyExists
            accounts[emailNormalized] = account.value
            accounts.remove(account.key)
            emailVerifications.entries.removeAll { it.value.userId == userId }
            passwordResets.entries.removeAll { it.value.userId == userId }
        }
        PendingEmailVerificationResult.Current(
            PendingEmailVerificationResponse(emailNormalized, emailVerified = false)
        )
    }

    override suspend fun consumeEmailVerification(
        userId: UUID,
        codeHash: String,
        nowMillis: Long
    ): Boolean = mutex.withLock {
        val entry = accounts.entries.firstOrNull { it.value.userId == userId } ?: return@withLock false
        if (entry.value.emailVerified || emailVerifications.values.none {
                it.userId == userId && it.codeHash == codeHash && it.delivered && it.expiresAtMillis > nowMillis
            }
        ) return@withLock false
        accounts[entry.key] = entry.value.copy(emailVerified = true)
        emailVerifications.entries.removeAll { it.value.userId == userId }
        true
    }

    override suspend fun deleteAccount(userId: UUID): Boolean = mutex.withLock {
        val removed = accounts.entries.removeAll { it.value.userId == userId }
        if (!removed) return@withLock false
        displayNamesByUserId.remove(userId)
        passwordResets.entries.removeAll { it.value.userId == userId }
        emailVerifications.entries.removeAll { it.value.userId == userId }
        revokeUserSessions(userId)
        true
    }

    private fun revokeUserSessions(userId: UUID) {
        sessions.values.filter { it.userId == userId }.forEach { recoveries.remove(it.sessionId) }
        sessions.entries.removeAll { it.value.userId == userId }
        sessionsByAccessToken.entries.removeAll { it.value.userId == userId }
    }

    private fun activeAccessSession(accessTokenHash: String, nowMillis: Long): StoredSession? =
        sessionsByAccessToken[accessTokenHash]?.takeIf { !it.revoked && it.accessExpiresAtMillis > nowMillis }

    private fun store(userId: UUID, devicePlatform: String?, session: NewAuthSession) {
        val stored = StoredSession(
            sessionId = session.sessionId,
            userId = userId,
            devicePlatform = devicePlatform,
            createdAtMillis = session.nowMillis,
            lastSeenAtMillis = session.nowMillis,
            accessExpiresAtMillis = session.accessExpiresAtMillis,
            expiresAtMillis = session.refreshExpiresAtMillis
        )
        sessions[session.refreshTokenHash] = stored
        sessionsByAccessToken[session.accessTokenHash] = stored
    }

    private data class StoredSession(
        val sessionId: UUID,
        val userId: UUID,
        val devicePlatform: String?,
        val createdAtMillis: Long,
        val lastSeenAtMillis: Long,
        val accessExpiresAtMillis: Long,
        val expiresAtMillis: Long,
        val revoked: Boolean = false
    )

    private data class StoredPasswordReset(
        val userId: UUID,
        val emailNormalized: String,
        val expiresAtMillis: Long
    )

    private data class StoredEmailVerification(
        val userId: UUID,
        val codeHash: String,
        val expiresAtMillis: Long,
        val sequence: Long,
        val delivered: Boolean = false
    )
}
