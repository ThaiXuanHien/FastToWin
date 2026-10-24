package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable data class RewardedAdSessionRequest(val requestId: String)
@Serializable enum class RewardedAdSessionStatus { PENDING, GRANTED, EXPIRED, FAILED }
@Serializable data class RewardedAdSessionView(
    val sessionId: String,
    val customData: String,
    val adUnitId: String,
    val expiresAtEpochMillis: Long,
    val status: RewardedAdSessionStatus,
    val providerTransactionId: String? = null,
    val quota: PlayQuotaSnapshot? = null
)
