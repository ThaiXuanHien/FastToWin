package com.hienthai.fastowin.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class GuestIdentity(
    val playerId: String,
    val resumeToken: String,
    val displayName: String
)

interface GuestIdentityRepository {
    suspend fun resolveGuest(displayName: String, resumeToken: String?, nowMillis: Long): GuestIdentity

    suspend fun markDisconnected(playerId: String, nowMillis: Long) = Unit
}

class InMemoryGuestIdentityRepository : GuestIdentityRepository {
    private val mutex = Mutex()
    private val identitiesByToken = mutableMapOf<String, GuestIdentity>()

    override suspend fun resolveGuest(
        displayName: String,
        resumeToken: String?,
        nowMillis: Long
    ): GuestIdentity = mutex.withLock {
        resumeToken?.let(identitiesByToken::get)?.let { existing ->
            val updated = existing.copy(displayName = displayName)
            identitiesByToken[resumeToken] = updated
            return@withLock updated
        }

        GuestIdentity(
            playerId = UUID.randomUUID().toString(),
            resumeToken = newResumeToken(),
            displayName = displayName
        ).also { identitiesByToken[it.resumeToken] = it }
    }
}

internal fun newResumeToken(): String {
    val bytes = ByteArray(32)
    tokenSecureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private val tokenSecureRandom = SecureRandom()
