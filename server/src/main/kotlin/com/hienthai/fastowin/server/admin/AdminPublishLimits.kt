package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.legacyFallback
import java.text.NumberFormat
import java.util.Locale

/** Reward amounts per recipient (or per claim of a gift code). */
data class RewardAmounts(val gold: Long = 0, val gems: Long = 0, val xp: Long = 0, val plays: Long = 0)

/**
 * ADM-12: caps on what one Owner may publish alone. Gifts, compensation rounds and gift
 * codes above a cap need a second, different Owner to publish the same version.
 */
data class AdminPublishLimits(
    val perRecipient: RewardAmounts = RewardAmounts(gold = 50_000, gems = 100, xp = 5_000, plays = 20),
    /** Totals across all recipients; Long.MAX_VALUE means uncapped. */
    val total: RewardAmounts = RewardAmounts(gold = 50_000_000, gems = 50_000, xp = Long.MAX_VALUE, plays = Long.MAX_VALUE)
) {
    /**
     * Human-readable reasons the publication exceeds a cap; empty when one Owner may publish.
     * [recipients] is null for gift codes without a claim limit, whose total is unbounded.
     */
    fun exceeded(amounts: RewardAmounts, recipients: Long?): List<String> = buildList {
        fun check(label: String, value: Long, perCap: Long, totalCap: Long) {
            if (value <= 0) return
            if (value > perCap) add("$label mỗi người ${format(value)} > ${format(perCap)}")
            if (totalCap == Long.MAX_VALUE) return
            val total = recipients?.let { runCatching { Math.multiplyExact(value, it) }.getOrDefault(Long.MAX_VALUE) }
            if (total == null) add("$label không giới hạn số lượt nhận")
            else if (total > totalCap) add("$label tổng ${format(total)} > ${format(totalCap)}")
        }
        check("Vàng", amounts.gold, perRecipient.gold, total.gold)
        check("Gem", amounts.gems, perRecipient.gems, total.gems)
        check("XP", amounts.xp, perRecipient.xp, total.xp)
        check("Lượt chơi", amounts.plays, perRecipient.plays, total.plays)
    }

    companion object {
        fun fromEnvironment(values: Map<String, String> = System.getenv()): AdminPublishLimits {
            val defaults = AdminPublishLimits()
            fun read(name: String, default: Long): Long {
                val raw = values[name]?.trim()?.takeIf(String::isNotEmpty) ?: return default
                val parsed = requireNotNull(raw.toLongOrNull()) { "$name must be a whole number." }
                require(parsed > 0) { "$name must be positive." }
                return parsed
            }
            return AdminPublishLimits(
                RewardAmounts(
                    read("FASTTOWIN_GIFT_CAP_RECIPIENT_GOLD", defaults.perRecipient.gold),
                    read("FASTTOWIN_GIFT_CAP_RECIPIENT_GEMS", defaults.perRecipient.gems),
                    read("FASTTOWIN_GIFT_CAP_RECIPIENT_XP", defaults.perRecipient.xp),
                    read("FASTTOWIN_GIFT_CAP_RECIPIENT_PLAYS", defaults.perRecipient.plays)
                ),
                RewardAmounts(
                    read("FASTTOWIN_GIFT_CAP_TOTAL_GOLD", defaults.total.gold),
                    read("FASTTOWIN_GIFT_CAP_TOTAL_GEMS", defaults.total.gems),
                    read("FASTTOWIN_GIFT_CAP_TOTAL_XP", defaults.total.xp),
                    read("FASTTOWIN_GIFT_CAP_TOTAL_PLAYS", defaults.total.plays)
                )
            )
        }

        private fun format(value: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value)
    }
}

data class PublishApproval(val kind: String, val targetId: String, val versionKey: String, val requestedBy: String, val requestedAtEpochMillis: Long)

/** Result of the second-Owner check for one publication. */
sealed interface DualControl {
    /** Within caps. */
    data object Allowed : DualControl
    /** A different Owner requested this exact version earlier; publishing now completes it. */
    data class Approved(val requestedBy: String, val reasons: List<String>) : DualControl
    /** Recorded (or still waiting for) a second Owner; nothing is published. */
    data class Pending(val newlyRequested: Boolean, val reasons: List<String>) : DualControl
}

/**
 * Applies dual control for one publication version. The caller must only publish on
 * [DualControl.Allowed] or [DualControl.Approved], and must commit a [DualControl.Pending]
 * result (so the request is stored) before reporting [approvalRequired].
 */
internal fun AdminTransaction.dualControl(
    kind: String, targetId: String, versionKey: String, actorId: String, reasons: List<String>, now: Long
): DualControl {
    if (reasons.isEmpty()) return DualControl.Allowed
    val existing = publishApproval(kind, targetId, versionKey)
    if (existing == null) {
        putPublishApproval(PublishApproval(kind, targetId, versionKey, actorId, now))
        appendAudit(AuditEntry(java.util.UUID.randomUUID().toString(), actorId, "PUBLISH_APPROVAL_REQUESTED", targetId.takeIf(::isUuid),
            null, null, null, "kind=$kind;" + reasons.joinToString("; "), now))
        return DualControl.Pending(newlyRequested = true, reasons)
    }
    if (existing.requestedBy == actorId) return DualControl.Pending(newlyRequested = false, reasons)
    deletePublishApprovals(kind, targetId)
    appendAudit(AuditEntry(java.util.UUID.randomUUID().toString(), actorId, "PUBLISH_APPROVAL_GRANTED", targetId.takeIf(::isUuid),
        null, null, "requestedBy=${existing.requestedBy}", "kind=$kind;" + reasons.joinToString("; "), now))
    return DualControl.Approved(existing.requestedBy, reasons)
}

internal fun approvalRequired(pending: DualControl.Pending): Nothing = throw AdminException(409, "SECOND_OWNER_APPROVAL_REQUIRED", legacyFallback(
    (if (pending.newlyRequested) "Đã gửi yêu cầu duyệt. " else "Đang chờ duyệt. ") +
        "Vượt trần (" + pending.reasons.joinToString("; ") + "): cần một Owner khác mở đúng phiên bản này và bấm Công bố."))

private fun isUuid(value: String) = runCatching { java.util.UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
