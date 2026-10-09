package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable enum class AdminAnnouncementCategory { GENERAL, EVENT, MAINTENANCE, COMPENSATION }
@Serializable enum class AdminAnnouncementState { SCHEDULED, ACTIVE, EXPIRED, CANCELLED }
@Serializable enum class AdminAnnouncementAction { NONE, REWARD_EVENT, GIFT_MAILBOX, APP_UPDATE }

/** Public plain-text context; no admin reason, audience, audit, token or provider facts. */
@Serializable data class AdminAnnouncementContext(
    val id: String,
    val titleVi: String,
    val titleEn: String,
    val bodyVi: String,
    val bodyEn: String,
    val category: AdminAnnouncementCategory,
    val priority: Int,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val state: AdminAnnouncementState,
    val action: AdminAnnouncementAction,
    val targetId: String? = null,
    val supersedesAnnouncementId: String? = null
)
