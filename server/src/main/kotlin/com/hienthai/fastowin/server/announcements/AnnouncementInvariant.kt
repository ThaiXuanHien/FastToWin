package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.server.admin.conflict
import com.hienthai.fastowin.server.admin.invalid
import java.util.UUID

internal fun validateTransition(previous: AnnouncementDefinition?, next: AnnouncementDefinition) {
    fun canonical(id: String) = runCatching { UUID.fromString(id).toString() }.getOrNull() == id
    if (!canonical(next.id) || next.targetId?.let(::canonical) == false ||
        next.supersedesAnnouncementId?.let(::canonical) == false ||
        next.selectedUserIds.any { !canonical(it) }) invalid()
    if (next.titleVi.length !in 1..160 || next.titleEn.length !in 1..160 ||
        next.bodyVi.length !in 1..4000 || next.bodyEn.length !in 1..4000 ||
        next.priority !in 0..100 || next.startsAtEpochMillis !in 0..253402300799999L ||
        next.endsAtEpochMillis !in 0..253402300799999L || next.startsAtEpochMillis >= next.endsAtEpochMillis) invalid()
    if ((next.action in setOf(AdminAnnouncementAction.REWARD_EVENT, AdminAnnouncementAction.GIFT_MAILBOX)) != (next.targetId != null)) invalid()
    if (next.audience == AnnouncementAudience.ALL_REGISTERED && next.selectedUserIds.isNotEmpty()) invalid()
    if (next.audience == AnnouncementAudience.USER_IDS && (next.selectedUserIds.size !in 1..10000 || next.selectedUserIds.distinct().size != next.selectedUserIds.size)) invalid()
    if (previous == null) {
        if (next.version != 1 || next.status == AnnouncementStatus.CANCELLED) conflict()
        return
    }
    if (previous.version == Int.MAX_VALUE || next.version != previous.version + 1) conflict()
    if (previous.status == AnnouncementStatus.CANCELLED) conflict()
    if (previous.status == AnnouncementStatus.DRAFT && next.status == AnnouncementStatus.CANCELLED) conflict()
    if (previous.status == AnnouncementStatus.PUBLISHED) {
        if (next.status != AnnouncementStatus.CANCELLED ||
            next.copy(version = previous.version, status = previous.status,
                updatedAtEpochMillis = previous.updatedAtEpochMillis,
                cancelledBy = previous.cancelledBy,
                cancelledAtEpochMillis = previous.cancelledAtEpochMillis,
                cancellationReason = previous.cancellationReason) != previous) conflict()
    }
}
