package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.server.announcements.AnnouncementRepository
import com.hienthai.fastowin.server.announcements.InMemoryAnnouncementRepository
import com.hienthai.fastowin.server.announcements.JdbcNotificationPersistence
import com.hienthai.fastowin.server.announcements.PostgresAnnouncementRepository
import com.hienthai.fastowin.server.announcements.UnavailableAnnouncementRepository
import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

data class StoredRoomInvitation(
    val id: String,
    val inviterId: String,
    val inviteeId: String,
    val roomId: String,
    val inviterDisplayName: String,
    val roomName: String,
    val expiresAtMillis: Long
)

interface NotificationRepository {
    val announcements: AnnouncementRepository get() = UnavailableAnnouncementRepository
    suspend fun loadNotifications(userId: String): List<NotificationSnapshot>
    suspend fun createNotifications(userId: String, notifications: List<NotificationSnapshot>)
    suspend fun markNotificationsRead(userId: String, notificationId: String?, nowMillis: Long)
    suspend fun dismissNotifications(userId: String, notificationId: String?, nowMillis: Long)
    suspend fun loadActiveRoomInvitations(nowMillis: Long): List<StoredRoomInvitation>
    suspend fun saveRoomInvitation(invitation: StoredRoomInvitation)
    suspend fun deleteRoomInvitation(invitationId: String)
    suspend fun deleteRoomInvitationsBetween(firstUserId: String, secondUserId: String)
    suspend fun deleteRoomInvitationsForInvitee(inviteeId: String)
    suspend fun deleteRoomInvitationsForRoom(roomId: String)
    suspend fun deleteExpiredRoomInvitations(nowMillis: Long)
}

object NoOpNotificationRepository : NotificationRepository {
    override suspend fun loadNotifications(userId: String) = emptyList<NotificationSnapshot>()
    override suspend fun createNotifications(userId: String, notifications: List<NotificationSnapshot>) = Unit
    override suspend fun markNotificationsRead(userId: String, notificationId: String?, nowMillis: Long) = Unit
    override suspend fun dismissNotifications(userId: String, notificationId: String?, nowMillis: Long) = Unit
    override suspend fun loadActiveRoomInvitations(nowMillis: Long) = emptyList<StoredRoomInvitation>()
    override suspend fun saveRoomInvitation(invitation: StoredRoomInvitation) = Unit
    override suspend fun deleteRoomInvitation(invitationId: String) = Unit
    override suspend fun deleteRoomInvitationsBetween(firstUserId: String, secondUserId: String) = Unit
    override suspend fun deleteRoomInvitationsForInvitee(inviteeId: String) = Unit
    override suspend fun deleteRoomInvitationsForRoom(roomId: String) = Unit
    override suspend fun deleteExpiredRoomInvitations(nowMillis: Long) = Unit
}

class InMemoryNotificationRepository(adminRepository: InMemoryAdminRepository = InMemoryAdminRepository()) : NotificationRepository {
    private val mutex = Mutex()
    private val notifications = linkedMapOf<Pair<String, String>, StoredNotification>()
    private val invitations = linkedMapOf<String, StoredRoomInvitation>()
    override val announcements = InMemoryAnnouncementRepository(this, adminRepository)

    internal suspend fun <T> withCopiedNotifications(block: suspend (MutableMap<Pair<String, String>, StoredNotification>) -> T): T = mutex.withLock {
        val copy = notifications.toMutableMap()
        val result = block(copy)
        notifications.clear()
        notifications.putAll(copy)
        result
    }

    override suspend fun loadNotifications(userId: String) = mutex.withLock {
        notifications.entries
            .filter { it.key.first == userId && !it.value.dismissed }
            .map { announcements.project(it.value.snapshot) }
            .sortedByDescending(NotificationSnapshot::createdAtEpochMillis)
            .take(100)
    }

    override suspend fun createNotifications(userId: String, notifications: List<NotificationSnapshot>) {
        mutex.withLock {
            notifications.forEach { notification ->
                this.notifications.putIfAbsent(userId to notification.id, StoredNotification(notification))
            }
        }
    }

    override suspend fun markNotificationsRead(userId: String, notificationId: String?, nowMillis: Long) {
        mutex.withLock {
            notifications.entries.filter { (key, value) ->
                key.first == userId && !value.dismissed && (notificationId == null || key.second == notificationId)
            }.forEach { entry -> entry.setValue(entry.value.copy(snapshot = entry.value.snapshot.copy(isRead = true))) }
            announcements.updateReceipt(userId, notificationId, nowMillis, read = true)
        }
    }

    override suspend fun dismissNotifications(userId: String, notificationId: String?, nowMillis: Long) {
        mutex.withLock {
            notifications.entries.filter { (key, value) ->
                key.first == userId && !value.dismissed && (notificationId == null || key.second == notificationId)
            }.forEach { entry -> entry.setValue(entry.value.copy(dismissed = true)) }
            announcements.updateReceipt(userId, notificationId, nowMillis, read = false)
        }
    }

    override suspend fun loadActiveRoomInvitations(nowMillis: Long) = mutex.withLock {
        invitations.values.filter { it.expiresAtMillis > nowMillis }
    }

    override suspend fun saveRoomInvitation(invitation: StoredRoomInvitation) {
        mutex.withLock {
            invitations.entries.removeAll {
                it.value.inviterId == invitation.inviterId && it.value.inviteeId == invitation.inviteeId
            }
            invitations[invitation.id] = invitation
        }
    }

    override suspend fun deleteRoomInvitation(invitationId: String) {
        mutex.withLock { invitations.remove(invitationId) }
    }

    override suspend fun deleteRoomInvitationsBetween(firstUserId: String, secondUserId: String) {
        mutex.withLock {
            invitations.entries.removeAll {
                (it.value.inviterId == firstUserId && it.value.inviteeId == secondUserId) ||
                    (it.value.inviterId == secondUserId && it.value.inviteeId == firstUserId)
            }
        }
    }

    override suspend fun deleteRoomInvitationsForInvitee(inviteeId: String) {
        mutex.withLock { invitations.entries.removeAll { it.value.inviteeId == inviteeId } }
    }

    override suspend fun deleteRoomInvitationsForRoom(roomId: String) {
        mutex.withLock { invitations.entries.removeAll { it.value.roomId == roomId } }
    }

    override suspend fun deleteExpiredRoomInvitations(nowMillis: Long) {
        mutex.withLock { invitations.entries.removeAll { it.value.expiresAtMillis <= nowMillis } }
    }

    internal data class StoredNotification(
        val snapshot: NotificationSnapshot,
        val dismissed: Boolean = false
    )
}

class PostgresNotificationRepository(private val dataSource: DataSource) : NotificationRepository {
    override val announcements = PostgresAnnouncementRepository(dataSource)
    override suspend fun loadNotifications(userId: String): List<NotificationSnapshot> = withContext(Dispatchers.IO) {
        dataSource.connection.use { JdbcNotificationPersistence(it).load(userId) }
    }

    override suspend fun createNotifications(userId: String, notifications: List<NotificationSnapshot>) {
        if (notifications.isEmpty()) return
        withContext(Dispatchers.IO) {
            dataSource.connection.use { JdbcNotificationPersistence(it).create(userId, notifications) }
        }
    }

    override suspend fun markNotificationsRead(userId: String, notificationId: String?, nowMillis: Long) =
        updateNotifications(userId, notificationId, "read_at", nowMillis)

    override suspend fun dismissNotifications(userId: String, notificationId: String?, nowMillis: Long) =
        updateNotifications(userId, notificationId, "dismissed_at", nowMillis)

    private suspend fun updateNotifications(userId: String, notificationId: String?, column: String, nowMillis: Long) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val persistence = JdbcNotificationPersistence(connection)
                    if (column == "read_at") persistence.markRead(userId, notificationId, nowMillis)
                    else persistence.dismiss(userId, notificationId, nowMillis)
                    connection.commit()
                } catch (error: Throwable) { connection.rollback(); throw error }
            }
        }
    }

    override suspend fun loadActiveRoomInvitations(nowMillis: Long): List<StoredRoomInvitation> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT id, inviter_id, invitee_id, room_id, inviter_display_name, room_name, expires_at
                    FROM room_invitations WHERE expires_at > ? ORDER BY expires_at
                    """.trimIndent()
                ).use { statement ->
                    statement.setTimestamp(1, Timestamp(nowMillis))
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) add(StoredRoomInvitation(
                                id = result.getObject("id", UUID::class.java).toString(),
                                inviterId = result.getObject("inviter_id", UUID::class.java).toString(),
                                inviteeId = result.getObject("invitee_id", UUID::class.java).toString(),
                                roomId = result.getObject("room_id", UUID::class.java).toString(),
                                inviterDisplayName = result.getString("inviter_display_name"),
                                roomName = result.getString("room_name"),
                                expiresAtMillis = result.getTimestamp("expires_at").time
                            ))
                        }
                    }
                }
            }
        }

    override suspend fun saveRoomInvitation(invitation: StoredRoomInvitation) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO room_invitations(
                        id, inviter_id, invitee_id, room_id, inviter_display_name, room_name, expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (inviter_id, invitee_id) DO UPDATE SET
                        id = EXCLUDED.id, room_id = EXCLUDED.room_id,
                        inviter_display_name = EXCLUDED.inviter_display_name,
                        room_name = EXCLUDED.room_name, expires_at = EXCLUDED.expires_at,
                        created_at = CURRENT_TIMESTAMP
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(invitation.id))
                    statement.setObject(2, UUID.fromString(invitation.inviterId))
                    statement.setObject(3, UUID.fromString(invitation.inviteeId))
                    statement.setObject(4, UUID.fromString(invitation.roomId))
                    statement.setString(5, invitation.inviterDisplayName)
                    statement.setString(6, invitation.roomName)
                    statement.setTimestamp(7, Timestamp(invitation.expiresAtMillis))
                    statement.executeUpdate()
                }
            }
        }
    }

    override suspend fun deleteRoomInvitation(invitationId: String) = deleteWhere("id = ?", UUID.fromString(invitationId))
    override suspend fun deleteRoomInvitationsBetween(firstUserId: String, secondUserId: String) = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM room_invitations WHERE (inviter_id = ? AND invitee_id = ?) OR (inviter_id = ? AND invitee_id = ?)"
            ).use { statement ->
                val first = UUID.fromString(firstUserId)
                val second = UUID.fromString(secondUserId)
                statement.setObject(1, first); statement.setObject(2, second)
                statement.setObject(3, second); statement.setObject(4, first)
                statement.executeUpdate()
            }
        }
        Unit
    }
    override suspend fun deleteRoomInvitationsForInvitee(inviteeId: String) = deleteWhere("invitee_id = ?", UUID.fromString(inviteeId))
    override suspend fun deleteRoomInvitationsForRoom(roomId: String) = deleteWhere("room_id = ?", UUID.fromString(roomId))
    override suspend fun deleteExpiredRoomInvitations(nowMillis: Long) = deleteWhere("expires_at <= ?", Timestamp(nowMillis))

    private suspend fun deleteWhere(predicate: String, value: Any) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement("DELETE FROM room_invitations WHERE $predicate").use { statement ->
                    statement.setObject(1, value)
                    statement.executeUpdate()
                }
            }
        }
    }
}
