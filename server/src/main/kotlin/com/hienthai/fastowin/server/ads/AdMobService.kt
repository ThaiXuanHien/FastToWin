package com.hienthai.fastowin.server.ads

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.PlayQuotaRepository

class AdMobService(
    private val configuration: AdMobConfiguration?,
    private val repository: AdMobRepository,
    private val quota: PlayQuotaRepository,
    private val verifier: AdMobSsvVerifier = AdMobSsvVerifier(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newIntentAllowed: () -> Boolean = { true },
    private val rewardedAdAvailability: RewardedAdAvailability
) {
    suspend fun prepare(userId: String, requestId: String): RewardedAdSessionView {
        val config = configuration ?: throw AdMobUnavailable()
        if (!Regex("[A-Za-z0-9_-]{1,64}").matches(requestId)) throw AdMobInvalidRequest()
        val existing = repository.findRequest(userId, requestId)
        if (existing != null) return view(existing, nowMillis())
        if (!newIntentAllowed()) throw AdMobUnavailable()
        return view(repository.prepare(userId, requestId, config.androidAdUnitId, nowMillis()), nowMillis())
    }
    suspend fun status(userId: String, sessionId: String): RewardedAdSessionView? {
        if (configuration == null) throw AdMobUnavailable()
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(sessionId)) throw AdMobInvalidRequest()
        return repository.find(userId, sessionId)?.let { view(it, nowMillis()) }
    }
    suspend fun callback(rawQuery: String) {
        val config = configuration ?: throw AdMobUnavailable()
        val event = verifier.verify(rawQuery)
        // Console probes may carry canned policy values. Only authenticated, strictly parsed unknown events are acknowledged here.
        if (repository.findByCustomData(event.customData) == null) return
        val now = nowMillis()
        if (!config.acceptsUnit(event.adUnit) || event.rewardAmount != "2" || event.rewardItem != "online_matches" ||
            event.timestampMillis > now + CLOCK_TOLERANCE || event.timestampMillis < now - 86_400_000) throw AdMobInvalidRequest()
        repository.accept(event, now)
    }
    private suspend fun view(session: AdMobSession, now: Long) = RewardedAdSessionView(
        session.id, session.customData, session.adUnitId, session.expiresAt,
        if (session.transactionId != null) RewardedAdSessionStatus.GRANTED else if (now >= session.expiresAt) RewardedAdSessionStatus.EXPIRED else RewardedAdSessionStatus.PENDING,
        session.transactionId,
        if (session.transactionId != null) quota.getSnapshot(session.userId, now).copy(rewardedAdAvailability = rewardedAdAvailability) else null
    )
}
