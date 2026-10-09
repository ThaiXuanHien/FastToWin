package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.server.admin.AdminTransaction
import com.hienthai.fastowin.server.admin.unavailable

interface AnnouncementRepository {
    suspend fun <T> transaction(admin: Boolean = false, block: AnnouncementTransaction.() -> T): T
}

interface AnnouncementTransaction {
    val admin: AdminTransaction
    val notifications: NotificationPersistence
    fun definition(id: String): AnnouncementDefinition?
    fun putDefinition(value: AnnouncementDefinition)
    fun definitions(status: AnnouncementStatus?, limit: Int, offset: Int): List<AnnouncementDefinition>
    fun definitionCount(status: AnnouncementStatus?): Long
    fun registeredPlayers(): List<AnnouncementPlayer>
    fun recipient(announcementId: String, userId: String): AnnouncementRecipient?
    fun recipients(announcementId: String, limit: Int, offset: Int): List<AnnouncementRecipient>
    fun recipientCounts(announcementId: String): AnnouncementRecipientCounts
    fun putRecipient(value: AnnouncementRecipient)
    fun replay(actorId: String, requestId: String): AnnouncementReplay?
    fun putReplay(value: AnnouncementReplay)
    fun preference(userId: String): AnnouncementPreference
    fun putPreference(value: AnnouncementPreference)
    fun pushAttempt(announcementId: String, userId: String): AnnouncementPushAttempt?
    fun putPushAttempt(value: AnnouncementPushAttempt)
}

object UnavailableAnnouncementRepository : AnnouncementRepository {
    override suspend fun <T> transaction(admin: Boolean, block: AnnouncementTransaction.() -> T): T = unavailable()
}
