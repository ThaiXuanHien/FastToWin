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
    suspend fun resolveGuest(displayName: String, resumeToken: String?, nowMillis: Long, acceptedContentTermsVersion: String?): GuestIdentity =
        resolveGuest(displayName, resumeToken, nowMillis)

    /** Validate persisted acceptance before publishing a resumed nickname, inside the identity write lock. */
    suspend fun resolveGuest(
        displayName: String, resumeToken: String?, nowMillis: Long, acceptedContentTermsVersion: String?,
        requiredContentTermsVersion: String?, storedContentTermsVersion: suspend (String) -> String?
    ): GuestIdentity {
        if (requiredContentTermsVersion != null && acceptedContentTermsVersion != requiredContentTermsVersion)
            throw com.hienthai.fastowin.server.moderation.ModerationException(403,"CONTENT_TERMS_REQUIRED","CONTENT_TERMS_REQUIRED")
        return resolveGuest(displayName,resumeToken,nowMillis,acceptedContentTermsVersion)
    }

    suspend fun markDisconnected(playerId: String, nowMillis: Long) = Unit
}

class InMemoryGuestIdentityRepository : GuestIdentityRepository {
    private val mutex = Mutex()
    private val identitiesByToken = mutableMapOf<String, GuestIdentity>()

    override suspend fun resolveGuest(
        displayName: String,
        resumeToken: String?,
        nowMillis: Long
    ): GuestIdentity = resolveGuest(displayName,resumeToken,nowMillis,null,null) { null }

    override suspend fun resolveGuest(displayName: String,resumeToken: String?,nowMillis: Long,acceptedContentTermsVersion: String?): GuestIdentity =
        resolveGuest(displayName,resumeToken,nowMillis,acceptedContentTermsVersion,null) { null }

    override suspend fun resolveGuest(
        displayName: String,resumeToken: String?,nowMillis: Long,acceptedContentTermsVersion: String?,
        requiredContentTermsVersion: String?,storedContentTermsVersion: suspend (String) -> String?
    ): GuestIdentity = mutex.withLock {
        resumeToken?.let(identitiesByToken::get)?.let { existing ->
            val stored = storedContentTermsVersion(existing.playerId)
            if (requiredContentTermsVersion != null && acceptedContentTermsVersion != requiredContentTermsVersion && stored != requiredContentTermsVersion)
                throw com.hienthai.fastowin.server.moderation.ModerationException(403,"CONTENT_TERMS_REQUIRED","CONTENT_TERMS_REQUIRED")
            val updated = existing.copy(displayName = displayName)
            identitiesByToken[resumeToken] = updated
            return@withLock updated
        }

        if (requiredContentTermsVersion != null && acceptedContentTermsVersion != requiredContentTermsVersion)
            throw com.hienthai.fastowin.server.moderation.ModerationException(403,"CONTENT_TERMS_REQUIRED","CONTENT_TERMS_REQUIRED")
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
