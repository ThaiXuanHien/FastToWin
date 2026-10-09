package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.server.AnnouncementPushStatus
import com.hienthai.fastowin.server.PushNotificationService
import com.hienthai.fastowin.server.admin.AuditEntry
import com.hienthai.fastowin.server.admin.invalid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class AnnouncementPushDelivery(
    private val repository: AnnouncementRepository,
    private val push: PushNotificationService,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Claim(val definition: AnnouncementDefinition, val player: AnnouncementPlayer, val attempt: AnnouncementPushAttempt)

    suspend fun deliverDueBatch(limit: Int = 100): Int {
        if (limit !in 1..1000) invalid()
        val claims = repository.transaction {
            val result = mutableListOf<Claim>()
            var definitionOffset = 0
            while (result.size < limit) {
                val ids = definitions(AnnouncementStatus.PUBLISHED, 100, definitionOffset).map { it.id }
                if (ids.isEmpty()) break
                for (id in ids) {
                    val value = definition(id, lock = true) ?: continue
                    val now = nowMillis()
                    if (value.status != AnnouncementStatus.PUBLISHED) continue
                    var recipientOffset = 0
                    while (result.size < limit) {
                        val page = recipients(id, 500, recipientOffset)
                        if (page.isEmpty()) break
                        for (recipient in page) {
                            if (result.size >= limit) break
                            if (recipient.storedAtEpochMillis == null || pushAttempt(id, recipient.userId) != null) continue
                            val player = currentPlayer(recipient.userId)
                            val preference = player?.let { preference(it.userId) }
                            val outcome = when {
                                !value.pushEnabled -> AnnouncementPushOutcome.DISABLED
                                now !in value.startsAtEpochMillis until value.endsAtEpochMillis -> AnnouncementPushOutcome.EXPIRED
                                player == null -> AnnouncementPushOutcome.EXPIRED
                                preference == null || !(if (value.category == AdminAnnouncementCategory.EVENT) preference.eventEnabled else preference.adminEnabled) -> AnnouncementPushOutcome.OPTED_OUT
                                player.fcmToken.isNullOrBlank() -> AnnouncementPushOutcome.NO_TOKEN
                                !push.announcementConfigured -> AnnouncementPushOutcome.UNAVAILABLE
                                else -> AnnouncementPushOutcome.ATTEMPTING
                            }
                            val attempt = AnnouncementPushAttempt(id, recipient.userId, UUID.randomUUID().toString(), outcome,
                                now, if (outcome == AnnouncementPushOutcome.ATTEMPTING) null else now)
                            putPushAttempt(attempt)
                            if (outcome == AnnouncementPushOutcome.ATTEMPTING && player != null) result.add(Claim(value, player, attempt))
                        }
                        recipientOffset += page.size
                    }
                }
                definitionOffset += ids.size
            }
            result.toList()
        }
        for (claim in claims) {
            val value = claim.definition
            val status = try {
                withTimeoutOrNull(20_000) {
                    push.sendAnnouncement(claim.player.fcmToken!!, claim.player.notificationLanguage,
                        value.titleVi, value.titleEn,
                        takeCodePointSafeUtf16(value.bodyVi, 240), takeCodePointSafeUtf16(value.bodyEn, 240),
                        "/notifications")
                } ?: AnnouncementPushStatus.UNKNOWN
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { settle(claim.attempt, AnnouncementPushOutcome.UNKNOWN) }
                throw cancelled
            } catch (_: Exception) { AnnouncementPushStatus.UNKNOWN }
            val outcome = when (status) {
                AnnouncementPushStatus.ACCEPTED -> AnnouncementPushOutcome.ACCEPTED
                AnnouncementPushStatus.FAILED -> AnnouncementPushOutcome.FAILED
                AnnouncementPushStatus.UNKNOWN -> AnnouncementPushOutcome.UNKNOWN
            }
            settle(claim.attempt, outcome)
        }
        return claims.size
    }

    suspend fun recoverStale(limit: Int = 100): Int {
        if (limit !in 1..1000) invalid()
        return repository.transaction {
            val now = nowMillis()
            var recovered = 0
            var definitionOffset = 0
            while (recovered < limit) {
                val ids = definitions(null, 100, definitionOffset).map { it.id }
                if (ids.isEmpty()) break
                for (id in ids) {
                    definition(id, lock = true) ?: continue
                    var recipientOffset = 0
                    while (recovered < limit) {
                        val page = recipients(id, 500, recipientOffset)
                        if (page.isEmpty()) break
                        for (recipient in page) {
                            val attempt = pushAttempt(id, recipient.userId) ?: continue
                            if (attempt.outcome != AnnouncementPushOutcome.ATTEMPTING || now - attempt.claimedAtEpochMillis < 120_000) continue
                            putPushAttempt(attempt.copy(outcome = AnnouncementPushOutcome.UNKNOWN, settledAtEpochMillis = now))
                            auditUnknown(attempt, now)
                            recovered++
                            if (recovered >= limit) break
                        }
                        recipientOffset += page.size
                    }
                }
                definitionOffset += ids.size
            }
            recovered
        }
    }

    private suspend fun settle(attempt: AnnouncementPushAttempt, outcome: AnnouncementPushOutcome) = repository.transaction {
        definition(attempt.announcementId, lock = true) ?: return@transaction
        val latest = pushAttempt(attempt.announcementId, attempt.userId) ?: return@transaction
        if (latest.attemptId != attempt.attemptId || latest.outcome != AnnouncementPushOutcome.ATTEMPTING) return@transaction
        val now = nowMillis()
        putPushAttempt(latest.copy(outcome = outcome, settledAtEpochMillis = now))
        if (outcome == AnnouncementPushOutcome.UNKNOWN) auditUnknown(attempt, now)
    }

    private fun AnnouncementTransaction.auditUnknown(attempt: AnnouncementPushAttempt, now: Long) {
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), null, "ANNOUNCEMENT_PUSH_UNKNOWN",
            attempt.announcementId, null, "AMBIGUOUS_ATTEMPT:${attempt.attemptId}", null, null, now))
    }
}

internal fun takeCodePointSafeUtf16(value: String, limit: Int): String {
    require(limit >= 0)
    var end = 0
    while (end < value.length) {
        val step = Character.charCount(value.codePointAt(end))
        if (end + step > limit) break
        end += step
    }
    return value.substring(0, end)
}
