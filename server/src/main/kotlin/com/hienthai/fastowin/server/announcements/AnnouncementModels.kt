package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.protocol.AdminAnnouncementContext
import com.hienthai.fastowin.protocol.AdminAnnouncementState
import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
import kotlinx.serialization.Serializable

@Serializable enum class AnnouncementStatus { DRAFT, PUBLISHED, CANCELLED }
@Serializable enum class AnnouncementAudience { ALL_REGISTERED, USER_IDS }
@Serializable enum class AnnouncementPushOutcome { ATTEMPTING, ACCEPTED, FAILED, UNKNOWN, DISABLED, OPTED_OUT, NO_TOKEN, UNAVAILABLE, EXPIRED }

/** Internal immutable copy is frozen at publication; never serialize this record directly to a player. */
@Serializable data class AnnouncementDefinition(
    val id: String,
    val version: Int,
    val status: AnnouncementStatus,
    val titleVi: String,
    val titleEn: String,
    val bodyVi: String,
    val bodyEn: String,
    val category: AdminAnnouncementCategory,
    val priority: Int,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val showBanner: Boolean,
    val pushEnabled: Boolean,
    val action: AdminAnnouncementAction,
    val targetId: String? = null,
    val supersedesAnnouncementId: String? = null,
    val audience: AnnouncementAudience,
    val selectedUserIds: List<String> = emptyList(),
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val publishedBy: String? = null,
    val publishedAtEpochMillis: Long? = null,
    val cancelledBy: String? = null,
    val cancelledAtEpochMillis: Long? = null,
    val cancellationReason: String? = null
) {
    fun publicContext(nowMillis: Long): AdminAnnouncementContext = AdminAnnouncementContext(
        id, titleVi, titleEn, bodyVi, bodyEn, category, priority,
        startsAtEpochMillis, endsAtEpochMillis,
        when {
            status == AnnouncementStatus.CANCELLED -> AdminAnnouncementState.CANCELLED
            status == AnnouncementStatus.DRAFT -> AdminAnnouncementState.SCHEDULED
            nowMillis < startsAtEpochMillis -> AdminAnnouncementState.SCHEDULED
            nowMillis >= endsAtEpochMillis -> AdminAnnouncementState.EXPIRED
            else -> AdminAnnouncementState.ACTIVE
        }, action, targetId, supersedesAnnouncementId
    )

    fun notification(language: String, nowMillis: Long): NotificationSnapshot {
        val vietnamese = language.lowercase().startsWith("vi")
        return NotificationSnapshot(
            id = "admin-announcement:$id", kind = NotificationKind.COSMETIC,
            title = if (vietnamese) titleVi else titleEn,
            message = if (vietnamese) bodyVi else bodyEn,
            createdAtEpochMillis = nowMillis, destination = NotificationDestination.PROFILE,
            announcement = publicContext(nowMillis)
        )
    }
}

@Serializable data class AnnouncementRecipient(
    val announcementId: String,
    val userId: String,
    val storedAtEpochMillis: Long? = null,
    val readAtEpochMillis: Long? = null,
    val dismissedAtEpochMillis: Long? = null,
    val bannerHiddenAtEpochMillis: Long? = null,
    val skippedReason: String? = null
)

@Serializable data class AnnouncementReplay(
    val actorId: String, val requestId: String, val fingerprint: String,
    val responseType: String, val responseJson: String
)

@Serializable data class AnnouncementPreference(
    val userId: String, val version: Int = 1,
    val adminEnabled: Boolean = false, val eventEnabled: Boolean = false
)

@Serializable data class AnnouncementPushAttempt(
    val announcementId: String, val userId: String, val attemptId: String,
    val outcome: AnnouncementPushOutcome, val claimedAtEpochMillis: Long,
    val settledAtEpochMillis: Long? = null
)

data class AnnouncementPlayer(val userId: String, val notificationLanguage: String)

data class AnnouncementRecipientCounts(
    val frozen: Long, val stored: Long, val read: Long, val dismissed: Long,
    val bannerHidden: Long, val skipped: Long,
    val pushAccepted: Long, val pushFailed: Long, val pushUnknown: Long,
    val pushInFlight: Long, val pushDisabled: Long, val pushOptedOut: Long,
    val pushNoToken: Long, val pushUnavailable: Long, val pushExpired: Long
)
