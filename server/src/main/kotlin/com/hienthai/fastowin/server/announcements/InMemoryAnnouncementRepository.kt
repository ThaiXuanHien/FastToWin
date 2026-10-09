package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.server.InMemoryNotificationRepository
import com.hienthai.fastowin.server.admin.AdminTransaction
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository

internal interface AnnouncementMemoryPushTargets {
    fun announcementPushTarget(userId: String): Pair<String, String>?
}

/** Lock order: notification mutex, then admin mutex. All three copied states publish under both locks. */
class InMemoryAnnouncementRepository internal constructor(
    private val owner: InMemoryNotificationRepository,
    private val adminRepository: InMemoryAdminRepository
) : AnnouncementRepository {
    private var state = State()

    private data class State(
        val definitions: MutableMap<String, AnnouncementDefinition> = linkedMapOf(),
        val recipients: MutableMap<Pair<String, String>, AnnouncementRecipient> = linkedMapOf(),
        val replays: MutableMap<Pair<String, String>, AnnouncementReplay> = linkedMapOf(),
        val preferences: MutableMap<String, AnnouncementPreference> = linkedMapOf(),
        val attempts: MutableMap<Pair<String, String>, AnnouncementPushAttempt> = linkedMapOf()
    ) {
        fun copyState() = State(definitions.toMutableMap(), recipients.toMutableMap(), replays.toMutableMap(),
            preferences.toMutableMap(), attempts.toMutableMap())
    }

    override suspend fun <T> transaction(admin: Boolean, block: AnnouncementTransaction.() -> T): T =
        owner.withCopiedNotifications { notificationCopy, publishNotifications ->
            val next = state.copyState()
            adminRepository.coordinatedTransaction(publish = {
                state = next
                publishNotifications()
            }) {
                val adminTransaction = this
                val publishingHere = mutableSetOf<String>()
                val persistence = object : NotificationPersistence {
                    override fun load(userId: String, limit: Int, offset: Int, includeDismissed: Boolean, announcementOnly: Boolean, notificationId: String?) = notificationCopy.entries
                        .filter { it.key.first == userId && (includeDismissed || !it.value.dismissed) &&
                            (!announcementOnly || it.value.snapshot.announcement != null) &&
                            (notificationId == null || it.key.second == notificationId) }
                        .map { project(it.value.snapshot, next) }.sortedByDescending(NotificationSnapshot::createdAtEpochMillis).drop(offset).take(limit)
                    override fun count(userId: String, includeDismissed: Boolean, announcementOnly: Boolean) = notificationCopy.entries.count {
                        it.key.first == userId && (includeDismissed || !it.value.dismissed) &&
                            (!announcementOnly || it.value.snapshot.announcement != null)
                    }.toLong()
                    override fun create(userId: String, notifications: List<NotificationSnapshot>) {
                        notifications.forEach { notification ->
                            notificationCopy.putIfAbsent(userId to notification.id, InMemoryNotificationRepository.StoredNotification(notification))
                        }
                    }
                    override fun markRead(userId: String, notificationId: String?, nowMillis: Long) {
                        notificationCopy.entries.filter { it.key.first == userId && !it.value.dismissed && (notificationId == null || it.key.second == notificationId) }
                            .forEach { it.setValue(it.value.copy(snapshot = it.value.snapshot.copy(isRead = true))) }
                        updateReceipt(next, userId, notificationId, nowMillis, true)
                    }
                    override fun dismiss(userId: String, notificationId: String?, nowMillis: Long) {
                        notificationCopy.entries.filter { it.key.first == userId && !it.value.dismissed && (notificationId == null || it.key.second == notificationId) }
                            .forEach { it.setValue(it.value.copy(dismissed = true)) }
                        updateReceipt(next, userId, notificationId, nowMillis, false)
                    }
                }
                object : AnnouncementTransaction {
                    override val admin: AdminTransaction = adminTransaction
                    override val notifications: NotificationPersistence = persistence
                    override fun definition(id: String, lock: Boolean) = next.definitions[id]
                    override fun putDefinition(value: AnnouncementDefinition) {
                        val previous = next.definitions[value.id]
                        validateTransition(previous, value)
                        if (value.status == AnnouncementStatus.PUBLISHED && previous?.status != AnnouncementStatus.PUBLISHED)
                            publishingHere.add(value.id)
                        next.definitions[value.id] = value
                    }
                    override fun definitions(status: AnnouncementStatus?, limit: Int, offset: Int) = next.definitions.values
                        .filter { status == null || it.status == status }.sortedBy { it.id }.drop(offset).take(limit)
                    override fun definitionCount(status: AnnouncementStatus?) = next.definitions.values.count { status == null || it.status == status }.toLong()
                    override fun registeredPlayers(): List<AnnouncementPlayer> {
                        // Memory player fixtures use the existing admin seam; absent storage is unavailable.
                        val all = mutableListOf<com.hienthai.fastowin.server.admin.UserSummary>()
                        var page = 0
                        do {
                            val batch = adminTransaction.users("", false, com.hienthai.fastowin.server.admin.PageRequest(page++, 100)).items
                            all.addAll(batch)
                        } while (batch.size == 100)
                        return all.filter { it.accountType == "REGISTERED" && it.status != "DELETED" }
                            .map { AnnouncementPlayer(it.id, "en") }
                    }
                    override fun currentPlayer(userId: String): AnnouncementPlayer? =
                        adminTransaction.user(userId)?.takeIf { it.accountType == "REGISTERED" && it.status != "DELETED" }
                            ?.let {
                                val target = (adminTransaction as AnnouncementMemoryPushTargets).announcementPushTarget(userId)
                                AnnouncementPlayer(it.id, target?.first ?: "en", target?.second)
                            }
                    override fun publishedTarget(action: com.hienthai.fastowin.protocol.AdminAnnouncementAction, targetId: String): Boolean = false
                    override fun recipient(announcementId: String, userId: String) = next.recipients[announcementId to userId]
                    override fun recipients(announcementId: String, limit: Int, offset: Int) = next.recipients.values
                        .filter { it.announcementId == announcementId }.sortedBy { it.userId }.drop(offset).take(limit)
                    override fun recipientCounts(announcementId: String): AnnouncementRecipientCounts {
                        val values = next.recipients.values.filter { it.announcementId == announcementId }
                        val attempts = next.attempts.values.filter { it.announcementId == announcementId }
                        fun outcomes(outcome: AnnouncementPushOutcome) = attempts.count { it.outcome == outcome }.toLong()
                        return AnnouncementRecipientCounts(values.size.toLong(), values.count { it.storedAtEpochMillis != null }.toLong(),
                            values.count { it.readAtEpochMillis != null }.toLong(), values.count { it.dismissedAtEpochMillis != null }.toLong(),
                            values.count { it.bannerHiddenAtEpochMillis != null }.toLong(), values.count { it.skippedReason != null }.toLong(),
                            outcomes(AnnouncementPushOutcome.ACCEPTED), outcomes(AnnouncementPushOutcome.FAILED),
                            outcomes(AnnouncementPushOutcome.UNKNOWN), outcomes(AnnouncementPushOutcome.ATTEMPTING),
                            outcomes(AnnouncementPushOutcome.DISABLED), outcomes(AnnouncementPushOutcome.OPTED_OUT),
                            outcomes(AnnouncementPushOutcome.NO_TOKEN), outcomes(AnnouncementPushOutcome.UNAVAILABLE),
                            outcomes(AnnouncementPushOutcome.EXPIRED))
                    }
                    override fun putRecipient(value: AnnouncementRecipient) {
                        if (next.definitions[value.announcementId] == null) com.hienthai.fastowin.server.admin.conflict()
                        val previous = next.recipients[value.announcementId to value.userId]
                        if (previous == null && value.announcementId !in publishingHere) com.hienthai.fastowin.server.admin.conflict()
                        next.recipients[value.announcementId to value.userId] = value.copy(
                            storedAtEpochMillis = value.storedAtEpochMillis ?: previous?.storedAtEpochMillis,
                            readAtEpochMillis = value.readAtEpochMillis ?: previous?.readAtEpochMillis,
                            dismissedAtEpochMillis = value.dismissedAtEpochMillis ?: previous?.dismissedAtEpochMillis,
                            bannerHiddenAtEpochMillis = value.bannerHiddenAtEpochMillis ?: previous?.bannerHiddenAtEpochMillis
                        )
                    }
                    override fun replay(actorId: String, requestId: String) = next.replays[actorId to requestId]
                    override fun putReplay(value: AnnouncementReplay) {
                        if (next.replays.containsKey(value.actorId to value.requestId)) com.hienthai.fastowin.server.admin.conflict()
                        next.replays[value.actorId to value.requestId] = value
                    }
                    override fun preference(userId: String) = next.preferences[userId] ?: AnnouncementPreference(userId)
                    override fun putPreference(value: AnnouncementPreference) {
                        val previous = next.preferences[value.userId]
                        if (previous == value) return
                        if ((previous == null && value.version != 1) ||
                            (previous != null && (previous.version == Int.MAX_VALUE || value.version != previous.version + 1)))
                            com.hienthai.fastowin.server.admin.conflict()
                        next.preferences[value.userId] = value
                    }
                    override fun pushAttempt(announcementId: String, userId: String) = next.attempts[announcementId to userId]
                    override fun putPushAttempt(value: AnnouncementPushAttempt) {
                        if (next.recipients[value.announcementId to value.userId] == null) com.hienthai.fastowin.server.admin.conflict()
                        val previous = next.attempts[value.announcementId to value.userId]
                        if (previous == value) return
                        if (previous != null && (previous.attemptId != value.attemptId ||
                                previous.outcome != AnnouncementPushOutcome.ATTEMPTING ||
                                value.outcome == AnnouncementPushOutcome.ATTEMPTING ||
                                previous.claimedAtEpochMillis != value.claimedAtEpochMillis))
                            com.hienthai.fastowin.server.admin.conflict()
                        next.attempts[value.announcementId to value.userId] = value
                    }
                }.block()
            }
        }

    /** Called only while the owner's notification mutex is held. */
    internal fun updateReceipt(userId: String, notificationId: String?, nowMillis: Long, read: Boolean) {
        updateReceipt(state, userId, notificationId, nowMillis, read)
    }

    internal fun project(snapshot: NotificationSnapshot): NotificationSnapshot = project(snapshot, state)
    private fun project(snapshot: NotificationSnapshot, current: State): NotificationSnapshot {
        val definition = snapshot.announcement?.id?.let(current.definitions::get) ?: return snapshot
        return snapshot.copy(announcement = definition.publicContext(System.currentTimeMillis()))
    }

    private fun updateReceipt(target: State, userId: String, notificationId: String?, nowMillis: Long, read: Boolean) {
        target.recipients.entries.filter { (key, value) ->
            key.second == userId && (notificationId == null || notificationId == "admin-announcement:${key.first}") &&
                value.storedAtEpochMillis != null && value.dismissedAtEpochMillis == null
        }.forEach { entry ->
            entry.setValue(if (read) entry.value.copy(readAtEpochMillis = entry.value.readAtEpochMillis ?: nowMillis)
                else entry.value.copy(dismissedAtEpochMillis = nowMillis))
        }
    }
}
