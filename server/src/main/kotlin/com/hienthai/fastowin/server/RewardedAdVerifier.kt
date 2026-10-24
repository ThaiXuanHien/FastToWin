package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.protocol.RewardedAdProvider

data class VerifiedRewardedAd(
    val provider: RewardedAdProvider,
    val providerTransactionId: String,
    val proof: String
)

fun interface RewardedAdVerifier {
    suspend fun verify(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        proof: String
    ): VerifiedRewardedAd?
}

class DevRewardedAdVerifier : RewardedAdVerifier {
    override suspend fun verify(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        proof: String
    ): VerifiedRewardedAd? {
        if (provider != RewardedAdProvider.DEV_SIMULATED) return null
        if (userId.isBlank() || providerTransactionId.isBlank()) return null
        val expectedProof = devRewardedAdProof(userId, providerTransactionId)
        if (proof != expectedProof) return null
        return VerifiedRewardedAd(provider, providerTransactionId, proof)
    }
}

data object RejectingRewardedAdVerifier : RewardedAdVerifier {
    override suspend fun verify(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        proof: String
    ): VerifiedRewardedAd? = null
}

fun devRewardedAdProof(userId: String, providerTransactionId: String): String =
    "FASTTOWIN_DEV_REWARDED_V1:$userId:$providerTransactionId"

fun configuredRewardedAdVerifier(environment: String, adMob: RewardedAdVerifier? = null): RewardedAdVerifier {
    val fallback = if (environment == "dev") DevRewardedAdVerifier() else RejectingRewardedAdVerifier
    if (adMob == null) return fallback
    return RewardedAdVerifier { userId, provider, transactionId, proof ->
        if (provider == RewardedAdProvider.ADMOB_ANDROID) adMob.verify(userId, provider, transactionId, proof)
        else fallback.verify(userId, provider, transactionId, proof)
    }
}
fun configuredRewardedAdAvailability(environment: String, adMobEnabled: Boolean = false): RewardedAdAvailability =
    when {
        environment == "dev" -> RewardedAdAvailability.DEV_SIMULATED
        adMobEnabled -> RewardedAdAvailability.MOBILE_PRODUCTION
        else -> RewardedAdAvailability.UNAVAILABLE
    }
