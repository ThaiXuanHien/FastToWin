package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.GiftCodeReward
import kotlinx.serialization.Serializable

@Serializable
data class GiftCode(
    val code: String,
    val reward: GiftCodeReward,
    val expiresAt: Long,
    val maximumClaims: Int? = null,
    val claims: Int = 0,
    val stopped: Boolean = false
)

@Serializable
data class GiftCodePage(
    val items: List<GiftCode>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val serverNowEpochMillis: Long,
)

@Serializable
data class GiftCodePublishRequest(
    val code: String,
    val reward: GiftCodeReward,
    val expiresAt: Long,
    val maximumClaims: Int? = null,
    val reason: String,
    val requestId: String
)

@Serializable
data class GiftCodeStopRequest(val reason: String, val requestId: String)
