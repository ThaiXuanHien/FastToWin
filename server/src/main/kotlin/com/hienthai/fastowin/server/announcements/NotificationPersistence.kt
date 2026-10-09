package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.NotificationSnapshot

/** Synchronous writer bound to the enclosing announcement transaction's connection or copied memory state. */
interface NotificationPersistence {
    fun load(
        userId: String, limit: Int = 100, offset: Int = 0, includeDismissed: Boolean = false,
        announcementOnly: Boolean = false, notificationId: String? = null
    ): List<NotificationSnapshot>
    fun count(userId: String, includeDismissed: Boolean = false, announcementOnly: Boolean = false): Long
    fun create(userId: String, notifications: List<NotificationSnapshot>)
    fun markRead(userId: String, notificationId: String?, nowMillis: Long)
    fun dismiss(userId: String, notificationId: String?, nowMillis: Long)
}
