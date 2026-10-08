package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable enum class GiftState { AVAILABLE, CLAIMED, EXPIRED, CANCELLED }
@Serializable data class CompensationMailboxContext(
    val incidentId: String, val incidentKey: String, val roundNumber: Int,
    val affectedFromEpochMillis: Long, val affectedUntilEpochMillis: Long,
    val receivingReasonVi: String, val receivingReasonEn: String
)
@Serializable data class MailboxGift(
    val campaignId: String,
    val titleVi: String, val titleEn: String, val bodyVi: String, val bodyEn: String,
    val gold: Int, val xp: Int,
    val availableAtEpochMillis: Long, val expiresAtEpochMillis: Long,
    val state: GiftState, val claimedAtEpochMillis: Long? = null,
    val compensation: CompensationMailboxContext? = null
)
@Serializable data class GiftMailboxPage(val items: List<MailboxGift>, val page: Int, val pageSize: Int, val total: Long)
@Serializable data class GiftClaimResult(val gift: MailboxGift, val awardedGold: Int, val awardedXp: Int, val gold: Int, val xp: Int)
@Serializable data class GiftClaimAllRequest(val campaignIds: List<String>)
@Serializable data class GiftError(val code: String, val message: String)
@Serializable data class GiftClaimItem(val campaignId: String, val result: GiftClaimResult? = null, val error: GiftError? = null)
@Serializable data class GiftClaimAllResult(val items: List<GiftClaimItem>)
