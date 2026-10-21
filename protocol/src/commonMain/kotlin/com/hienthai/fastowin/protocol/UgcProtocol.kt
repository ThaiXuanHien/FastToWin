package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

const val CONTENT_TERMS_VERSION: String = "2026-10-20"
const val MODERATED_AVATAR_ID: String = "avatar_moderated"
@Serializable enum class UgcTargetType { PLAYER_PROFILE, PLAYER_AVATAR, CLAN, ROOM, TOURNAMENT }
@Serializable enum class UgcReportReason { HARASSMENT, HATE, SEXUAL_CONTENT, VIOLENCE, SPAM, PERSONAL_INFORMATION, OTHER }
@Serializable data class ContentReportRequest(
    val targetType: UgcTargetType, val targetId: String, val reason: UgcReportReason,
    val note: String = "", val requestId: String
)
@Serializable data class ContentReportReceipt(val reportId: String, val duplicate: Boolean = false)
@Serializable data class ContentTermsStatus(val requiredVersion: String, val acceptedVersion: String?, val enforced: Boolean)
