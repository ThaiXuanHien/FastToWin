package com.hienthai.fastowin.server.ads

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.Base64

internal const val SHOW_WINDOW = 30 * 60_000L
internal const val CLOCK_TOLERANCE = 5 * 60_000L

data class AdMobSession(
    val id: String, val userId: String, val requestId: String, val customData: String,
    val adUnitId: String, val createdAt: Long, val expiresAt: Long,
    val transactionId: String? = null, val eventAt: Long? = null
) {
    fun accepts(event: VerifiedAdMobEvent): Boolean =
        (event.adUnit == adUnitId || event.adUnit == adUnitId.substringAfter('/')) &&
        event.timestampMillis >= createdAt - CLOCK_TOLERANCE && event.timestampMillis <= expiresAt + CLOCK_TOLERANCE
}
interface AdMobRepository {
    suspend fun findByCustomData(customData: String?): AdMobSession?
    suspend fun findRequest(userId: String, requestId: String): AdMobSession?
    suspend fun prepare(userId: String, requestId: String, unit: String, now: Long): AdMobSession
    suspend fun find(userId: String, id: String): AdMobSession?
    /** Session outcome and quota grant commit together. Duplicate session/transaction never credits again. */
    suspend fun accept(event: VerifiedAdMobEvent, now: Long): AdMobSession?
}
internal fun newAdMobNonce(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

/** Local development only; production wiring requires the durable repository. */
class InMemoryAdMobRepository(private val quota: PlayQuotaRepository) : AdMobRepository {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, AdMobSession>()
    private val transactions = mutableMapOf<String, String>()
    override suspend fun findByCustomData(customData: String?) = mutex.withLock {
        sessions.values.find { it.customData == customData }
    }
    override suspend fun findRequest(userId: String, requestId: String) = mutex.withLock {
        sessions.values.find { it.userId == userId && it.requestId == requestId }
    }
    override suspend fun prepare(userId: String, requestId: String, unit: String, now: Long) = mutex.withLock {
        sessions.values.find { it.userId == userId && it.requestId == requestId }?.let { return@withLock it }
        if (sessions.values.count { it.userId == userId && it.transactionId == null && it.expiresAt > now } >= 3) throw AdMobTooManySessions()
        val session = AdMobSession(newAdMobNonce(), userId, requestId, newAdMobNonce(), unit, now, now + SHOW_WINDOW)
        sessions[session.id] = session; session
    }
    override suspend fun find(userId: String, id: String) = mutex.withLock { sessions[id]?.takeIf { it.userId == userId } }
    override suspend fun accept(event: VerifiedAdMobEvent, now: Long): AdMobSession? = mutex.withLock {
        val session = sessions.values.find { it.customData == event.customData } ?: return@withLock null
        if (!session.accepts(event)) throw AdMobInvalidRequest()
        if (session.transactionId != null) return@withLock session
        val previous = transactions[event.transactionId]
        if (previous != null && previous != session.id) throw AdMobInvalidRequest()
        val grant = quota.grantRewardedAd(session.userId, RewardedAdProvider.ADMOB_ANDROID, event.transactionId, now)
        if (grant.status != RewardedAdBonusStatus.GRANTED) throw AdMobInvalidRequest()
        val accepted = session.copy(transactionId = event.transactionId, eventAt = event.timestampMillis)
        sessions[session.id] = accepted; transactions[event.transactionId] = session.id; accepted
    }
}

class AdMobRewardedAdVerifier(private val repository: AdMobRepository) : RewardedAdVerifier {
    override suspend fun verify(userId: String, provider: RewardedAdProvider, providerTransactionId: String, proof: String): VerifiedRewardedAd? {
        if (provider != RewardedAdProvider.ADMOB_ANDROID || proof.length !in 1..128) return null
        val session = repository.find(userId, proof) ?: return null
        val transactionId = session.transactionId ?: return null
        if (transactionId != providerTransactionId) return null
        return VerifiedRewardedAd(provider, providerTransactionId, proof)
    }
}
