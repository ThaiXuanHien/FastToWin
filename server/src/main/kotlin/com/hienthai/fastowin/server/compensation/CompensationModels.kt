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
    val supplementalReason: String?, val receivingReasonVi: String, val receivingReasonEn: String
)
/** Internal receipt, never returned as an admin or player DTO. */
data class CompensationReplay(val fingerprint: String, val responseType: String, internal val responseJson: String)
@Serializable data class CompensationPreviousClaims(
    val distinctUsers: String, val entitlements: String, val selectedOverlap: String
)
