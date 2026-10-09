package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.NotificationDestination
import com.hienthai.fastowin.protocol.NotificationKind
import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.protocol.ProtocolJson
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Uses only the caller's JDBC connection. The caller owns commit and rollback. */
internal class JdbcNotificationPersistence(private val connection: Connection) : NotificationPersistence {
    override fun load(userId: String, limit: Int, offset: Int, includeDismissed: Boolean,
                      announcementOnly: Boolean, notificationId: String?): List<NotificationSnapshot> {
        require(limit in 1..10000 && offset >= 0)
        connection.prepareStatement(
            """SELECT notification_id,kind,title,message,destination,created_at,read_at,action_data,
                      title_key,title_args,message_key,message_args,announcement_context,
                      a.payload AS announcement_payload
               FROM user_notifications n LEFT JOIN admin_announcements a
                 ON n.source='ADMIN_ANNOUNCEMENT' AND n.notification_id=('admin-announcement:' || a.id::text)
               WHERE n.user_id=? ${if (includeDismissed) "" else "AND n.dismissed_at IS NULL"}
                 ${if (announcementOnly) "AND n.source='ADMIN_ANNOUNCEMENT'" else ""}
                 ${if (notificationId == null) "" else "AND n.notification_id=?"}
               ORDER BY n.created_at DESC,n.notification_id LIMIT ? OFFSET ?"""
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            var parameter = 2
            if (notificationId != null) statement.setString(parameter++, notificationId)
            statement.setInt(parameter++, limit)
            statement.setInt(parameter, offset)
            statement.executeQuery().use { result ->
                return buildList {
                    while (result.next()) add(NotificationSnapshot(
                        id = result.getString("notification_id"),
                        kind = NotificationKind.valueOf(result.getString("kind")),
                        title = result.getString("title"), message = result.getString("message"),
                        createdAtEpochMillis = result.getTimestamp("created_at").time,
                        isRead = result.getTimestamp("read_at") != null,
                        destination = NotificationDestination.valueOf(result.getString("destination")),
                        actionData = result.getString("action_data"),
                        titleKey = result.getString("title_key"),
                        titleArgs = ProtocolJson.decodeFromString(result.getString("title_args")),
                        messageKey = result.getString("message_key"),
                        messageArgs = ProtocolJson.decodeFromString(result.getString("message_args")),
                        announcement = result.getString("announcement_payload")?.let {
                            Json.decodeFromString<AnnouncementDefinition>(it).publicContext(System.currentTimeMillis())
                        } ?: result.getString("announcement_context")?.let {
                            ProtocolJson.decodeFromString(it)
                        }
                    ))
                }
            }
        }
    }

    override fun count(userId: String, includeDismissed: Boolean, announcementOnly: Boolean): Long =
        connection.prepareStatement(
            """SELECT count(*) FROM user_notifications WHERE user_id=?
               ${if (includeDismissed) "" else "AND dismissed_at IS NULL"}
               ${if (announcementOnly) "AND source='ADMIN_ANNOUNCEMENT'" else ""}"""
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

    override fun create(userId: String, notifications: List<NotificationSnapshot>) {
        if (notifications.isEmpty()) return
        connection.prepareStatement(
            """INSERT INTO user_notifications(
                user_id,notification_id,kind,title,message,destination,created_at,action_data,
                title_key,title_args,message_key,message_args,source,announcement_context
            ) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?::jsonb,?,?::jsonb)
            ON CONFLICT (user_id,notification_id) DO NOTHING"""
        ).use { statement ->
            notifications.forEach { notification ->
                statement.setObject(1, UUID.fromString(userId))
                statement.setString(2, notification.id)
                statement.setString(3, notification.kind.name)
                statement.setString(4, notification.title)
                statement.setString(5, notification.message)
                statement.setString(6, notification.destination.name)
                statement.setTimestamp(7, Timestamp(notification.createdAtEpochMillis))
                statement.setString(8, notification.actionData)
                statement.setString(9, notification.titleKey)
                statement.setString(10, ProtocolJson.encodeToString(notification.titleArgs))
                statement.setString(11, notification.messageKey)
                statement.setString(12, ProtocolJson.encodeToString(notification.messageArgs))
                statement.setString(13, if (notification.announcement == null) "LEGACY" else "ADMIN_ANNOUNCEMENT")
                statement.setString(14, notification.announcement?.let(ProtocolJson::encodeToString))
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    override fun markRead(userId: String, notificationId: String?, nowMillis: Long) = update(userId, notificationId, nowMillis, true)
    override fun dismiss(userId: String, notificationId: String?, nowMillis: Long) = update(userId, notificationId, nowMillis, false)

    private fun update(userId: String, notificationId: String?, nowMillis: Long, read: Boolean) {
        // Recipient first, notification second. Delivery takes announcement then recipient then notification.
        val receiptColumn = if (read) "read_at" else "dismissed_at"
        val receiptValue = if (read) "COALESCE(read_at, ?)" else "COALESCE(dismissed_at, ?)"
        connection.prepareStatement(
            """SELECT announcement_id FROM admin_announcement_recipients
               WHERE user_id=? ${if (notificationId == null) "" else "AND announcement_id=?"}
               ORDER BY announcement_id FOR UPDATE"""
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            if (notificationId != null) {
                val id = notificationId.removePrefix("admin-announcement:")
                statement.setObject(2, runCatching { UUID.fromString(id) }.getOrNull())
            }
            statement.executeQuery().use { result ->
                val ids = buildList { while (result.next()) add(result.getObject(1, UUID::class.java)) }
                for (id in ids) {
                    connection.prepareStatement(
                        """UPDATE admin_announcement_recipients SET payload = jsonb_set(
                            payload::jsonb,'{${if (read) "readAtEpochMillis" else "dismissedAtEpochMillis"}}',to_jsonb(?::bigint)
                        )::text WHERE announcement_id=? AND user_id=?
                        AND (payload::jsonb->>'storedAtEpochMillis') IS NOT NULL
                        AND (payload::jsonb->>'dismissedAtEpochMillis') IS NULL
                        ${if (read) "AND (payload::jsonb->>'readAtEpochMillis') IS NULL" else ""}"""
                    ).use { update ->
                        update.setLong(1, nowMillis); update.setObject(2, id); update.setObject(3, UUID.fromString(userId)); update.executeUpdate()
                    }
                }
            }
        }
        val sql = "UPDATE user_notifications SET $receiptColumn=$receiptValue WHERE user_id=?" +
            if (notificationId == null) " AND dismissed_at IS NULL" else " AND notification_id=?"
        connection.prepareStatement(sql).use { statement ->
            statement.setTimestamp(1, Timestamp(nowMillis))
            statement.setObject(2, UUID.fromString(userId))
            if (notificationId != null) statement.setString(3, notificationId)
            statement.executeUpdate()
        }
    }
}
