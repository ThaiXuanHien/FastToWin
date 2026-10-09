package com.hienthai.fastowin.server.compensation

import kotlinx.serialization.Serializable

@Serializable data class CompensationIncidentRequest(
    val incidentKey: String, val titleVi: String, val titleEn: String,
    val descriptionVi: String, val descriptionEn: String,
    val affectedFromEpochMillis: Long, val affectedUntilEpochMillis: Long,
    val causeCode: String, val evidence: String, val responsibleAdminId: String,
    val requestId: String, val reason: String, val expectedVersion: Int? = null
)
@Serializable data class CompensationIncident(
    val id: String, val incidentKey: String, val version: Int,
    val titleVi: String, val titleEn: String, val descriptionVi: String, val descriptionEn: String,
    val affectedFromEpochMillis: Long, val affectedUntilEpochMillis: Long,
    val causeCode: String, val evidence: String, val responsibleAdminId: String,
    val createdByAdminId: String, val updatedByAdminId: String,
    val createdAtEpochMillis: Long, val updatedAtEpochMillis: Long
)
@Serializable data class CompensationPage<T>(val items: List<T>, val page: Int, val pageSize: Int, val total: String)
@Serializable enum class CompensationAudience { ALL_REGISTERED, USER_IDS, ACTIVITY }
@Serializable data class CompensationCapability(val source: CompensationAudience, val available: Boolean, val explanation: String)
@Serializable data class CompensationCapabilities(val sources: List<CompensationCapability>)
@Serializable data class CompensationRound(
    val campaignId: String, val incidentId: String, val roundNumber: Int,
    val supplementalReason: String?, val receivingReasonVi: String, val receivingReasonEn: String,
    val createdByAdminId: String? = null, val updatedByAdminId: String? = null,
    val createdAtEpochMillis: Long? = null, val updatedAtEpochMillis: Long? = null,
    val publishedByAdminId: String? = null, val cancelledByAdminId: String? = null
)
/** Internal receipt, never returned as an admin or player DTO. */
data class CompensationReplay(val fingerprint: String, val responseType: String, internal val responseJson: String)
@Serializable data class CompensationPreviousClaims(
    val distinctUsers: String, val entitlements: String, val selectedOverlap: String
)
@Serializable data class CompensationRoundRequest(
    val incidentVersion: Int, val receivingReasonVi: String, val receivingReasonEn: String,
    val gold: Int, val xp: Int, val availableAtEpochMillis: Long,
    val requestId: String, val reason: String,
    val audience: CompensationAudience = CompensationAudience.ALL_REGISTERED,
    val userIds: List<String> = emptyList(), val expiresAtEpochMillis: Long? = null,
    val expectedNextRoundNumber: Int? = null, val supplementalReason: String? = null,
    val expectedVersion: Int? = null
)
@Serializable data class CompensationRoundPreviewRequest(val incidentVersion: Int, val expectedVersion: Int, val requestId: String, val reason: String)
@Serializable data class CompensationRoundPublishRequest(val incidentVersion: Int, val expectedVersion: Int, val previewToken: String, val requestId: String, val reason: String)
@Serializable data class CompensationRoundCancelRequest(val incidentVersion: Int, val expectedVersion: Int, val requestId: String, val reason: String)
@Serializable data class CompensationReport(
    val eligible: String, val pending: String, val delivered: String, val claimed: String,
    val expired: String, val cancelled: String, val skipped: String, val claimedGold: String, val claimedXp: String
)
@Serializable data class CompensationRoundDetail(
    val incidentId: String, val incidentKey: String, val incidentVersion: Int,
    val round: CompensationRound, val campaign: com.hienthai.fastowin.server.gifts.GiftCampaign, val report: CompensationReport
)
@Serializable data class CompensationPreview(
    val incidentId: String, val incidentVersion: Int, val campaignId: String, val version: Int, val roundNumber: Int,
    val source: CompensationAudience, val userIds: List<String>, val sampleUserIds: List<String>,
    val invalidUserIds: List<String>, val duplicateUserIds: List<String>, val duplicateCount: String,
    val recipientCount: String, val totalGold: String, val totalXp: String,
    val previousClaimedUserCount: String, val previousClaimedEntitlementCount: String,
    val selectedPreviouslyClaimedUserCount: String, val previewToken: String
)
