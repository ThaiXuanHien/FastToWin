package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.server.admin.AdminTransaction
import com.hienthai.fastowin.server.admin.JdbcAdminTransaction
import com.hienthai.fastowin.server.admin.conflict
import com.hienthai.fastowin.server.admin.unavailable
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PostgresAnnouncementRepository(private val dataSource: DataSource) : AnnouncementRepository {
    override suspend fun <T> transaction(admin: Boolean, block: AnnouncementTransaction.() -> T): T = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    if (admin) connection.prepareStatement("SELECT pg_advisory_xact_lock(7421092187349)").use { it.execute() }
                    val tx = JdbcAnnouncementTransaction(connection)
                    val result = tx.block()
                    currentCoroutineContext().ensureActive()
                    connection.commit()
                    result
                } catch (error: Throwable) { connection.rollback(); throw error }
            }
        } catch (error: SQLException) {
            if (error.sqlState?.startsWith("23") == true) conflict()
            unavailable()
        }
    }
}

internal class JdbcAnnouncementTransaction(private val connection: Connection) : AnnouncementTransaction {
    override val admin: AdminTransaction = JdbcAdminTransaction(connection)
    override val notifications: NotificationPersistence = JdbcNotificationPersistence(connection)
    private val publishingHere = mutableSetOf<String>()

    private fun <T> rows(sql: String, vararg values: Any?, mapper: (ResultSet) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
            statement.executeQuery().use { result -> buildList { while (result.next()) add(mapper(result)) } }
        }

    private fun execute(sql: String, vararg values: Any?): Int = connection.prepareStatement(sql).use { statement ->
        values.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
        statement.executeUpdate()
    }

    override fun definition(id: String, lock: Boolean): AnnouncementDefinition? = rows(
        "SELECT payload FROM admin_announcements WHERE id=? ${if (lock) "FOR UPDATE" else ""}", UUID.fromString(id)
    ) { Json.decodeFromString<AnnouncementDefinition>(it.getString(1)) }.singleOrNull()

    override fun putDefinition(value: AnnouncementDefinition) {
        val existing = rows("SELECT payload FROM admin_announcements WHERE id=? FOR UPDATE", UUID.fromString(value.id)) {
            Json.decodeFromString<AnnouncementDefinition>(it.getString(1))
        }.singleOrNull()
        validateTransition(existing, value)
        if (value.status == AnnouncementStatus.PUBLISHED && existing?.status != AnnouncementStatus.PUBLISHED)
            publishingHere.add(value.id)
        execute(
            """INSERT INTO admin_announcements(id,version,status,starts_at,ends_at,priority,payload)
               VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET version=EXCLUDED.version,
               status=EXCLUDED.status,starts_at=EXCLUDED.starts_at,ends_at=EXCLUDED.ends_at,
               priority=EXCLUDED.priority,payload=EXCLUDED.payload""",
            UUID.fromString(value.id), value.version, value.status.name, Timestamp(value.startsAtEpochMillis),
            Timestamp(value.endsAtEpochMillis), value.priority, Json.encodeToString(value)
        )
    }

    override fun definitions(status: AnnouncementStatus?, limit: Int, offset: Int): List<AnnouncementDefinition> {
        require(limit in 1..10000 && offset >= 0)
        return if (status == null) rows("SELECT payload FROM admin_announcements ORDER BY id LIMIT ? OFFSET ?", limit, offset) {
            Json.decodeFromString(it.getString(1))
        } else rows("SELECT payload FROM admin_announcements WHERE status=? ORDER BY id LIMIT ? OFFSET ?", status.name, limit, offset) {
            Json.decodeFromString(it.getString(1))
        }
    }

    override fun definitionCount(status: AnnouncementStatus?): Long = if (status == null)
        rows("SELECT count(*) FROM admin_announcements") { it.getLong(1) }.single()
    else rows("SELECT count(*) FROM admin_announcements WHERE status=?", status.name) { it.getLong(1) }.single()

    override fun registeredPlayers(): List<AnnouncementPlayer> = rows(
        """SELECT id,notification_language,fcm_token FROM users
           WHERE account_type='REGISTERED' AND status<>'DELETED' ORDER BY id"""
    ) { AnnouncementPlayer(it.getString(1), it.getString(2), it.getString(3)) }

    override fun currentPlayer(userId: String): AnnouncementPlayer? = rows(
        """SELECT id,notification_language,fcm_token FROM users
           WHERE id=? AND account_type='REGISTERED' AND status<>'DELETED' FOR UPDATE""", UUID.fromString(userId)
    ) { AnnouncementPlayer(it.getString(1), it.getString(2), it.getString(3)) }.singleOrNull()

    override fun publishedTarget(action: com.hienthai.fastowin.protocol.AdminAnnouncementAction, targetId: String): Boolean = when (action) {
        com.hienthai.fastowin.protocol.AdminAnnouncementAction.REWARD_EVENT -> rows(
            "SELECT 1 FROM reward_events WHERE id=? AND payload::jsonb->>'publishedAtEpochMillis' IS NOT NULL AND COALESCE((payload::jsonb->>'cancelled')::boolean,false)=false",
            UUID.fromString(targetId)) { it.getInt(1) }.isNotEmpty()
        com.hienthai.fastowin.protocol.AdminAnnouncementAction.GIFT_MAILBOX -> rows(
            "SELECT 1 FROM admin_gift_campaigns WHERE id=? AND state='PUBLISHED'",
            UUID.fromString(targetId)) { it.getInt(1) }.isNotEmpty()
        else -> false
    }

    override fun recipient(announcementId: String, userId: String): AnnouncementRecipient? = rows(
        "SELECT payload FROM admin_announcement_recipients WHERE announcement_id=? AND user_id=?",
        UUID.fromString(announcementId), UUID.fromString(userId)
    ) { Json.decodeFromString<AnnouncementRecipient>(it.getString(1)) }.singleOrNull()

    override fun recipients(announcementId: String, limit: Int, offset: Int): List<AnnouncementRecipient> {
        require(limit in 1..10000 && offset >= 0)
        return rows("SELECT payload FROM admin_announcement_recipients WHERE announcement_id=? ORDER BY user_id LIMIT ? OFFSET ?",
            UUID.fromString(announcementId), limit, offset) { Json.decodeFromString(it.getString(1)) }
    }

    override fun recipientCounts(announcementId: String): AnnouncementRecipientCounts = rows(
        """SELECT count(*) AS frozen,
          count(*) FILTER (WHERE r.payload::jsonb->>'storedAtEpochMillis' IS NOT NULL) AS stored,
          count(*) FILTER (WHERE r.payload::jsonb->>'readAtEpochMillis' IS NOT NULL) AS read_count,
          count(*) FILTER (WHERE r.payload::jsonb->>'dismissedAtEpochMillis' IS NOT NULL) AS dismissed,
          count(*) FILTER (WHERE r.payload::jsonb->>'bannerHiddenAtEpochMillis' IS NOT NULL) AS banner_hidden,
          count(*) FILTER (WHERE r.payload::jsonb->>'skippedReason' IS NOT NULL) AS skipped,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='ACCEPTED') AS push_accepted,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='FAILED') AS push_failed,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='UNKNOWN') AS push_unknown,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='ATTEMPTING') AS push_in_flight,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='DISABLED') AS push_disabled,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='OPTED_OUT') AS push_opted_out,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='NO_TOKEN') AS push_no_token,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='UNAVAILABLE') AS push_unavailable,
          count(*) FILTER (WHERE p.payload::jsonb->>'outcome'='EXPIRED') AS push_expired
          FROM admin_announcement_recipients r LEFT JOIN admin_announcement_push_attempts p
            ON p.announcement_id=r.announcement_id AND p.user_id=r.user_id
          WHERE r.announcement_id=?""", UUID.fromString(announcementId)
    ) {
        AnnouncementRecipientCounts(it.getLong(1), it.getLong(2), it.getLong(3), it.getLong(4), it.getLong(5), it.getLong(6),
            it.getLong(7), it.getLong(8), it.getLong(9), it.getLong(10), it.getLong(11),
            it.getLong(12), it.getLong(13), it.getLong(14), it.getLong(15))
    }.single()

    override fun putRecipient(value: AnnouncementRecipient) {
        lockAnnouncement(value.announcementId)
        val previous = rows(
            "SELECT payload FROM admin_announcement_recipients WHERE announcement_id=? AND user_id=? FOR UPDATE",
            UUID.fromString(value.announcementId), UUID.fromString(value.userId)
        ) { Json.decodeFromString<AnnouncementRecipient>(it.getString(1)) }.singleOrNull()
        if (previous == null && value.announcementId !in publishingHere) conflict()
        val merged = value.copy(
            storedAtEpochMillis = value.storedAtEpochMillis ?: previous?.storedAtEpochMillis,
            readAtEpochMillis = value.readAtEpochMillis ?: previous?.readAtEpochMillis,
            dismissedAtEpochMillis = value.dismissedAtEpochMillis ?: previous?.dismissedAtEpochMillis,
            bannerHiddenAtEpochMillis = value.bannerHiddenAtEpochMillis ?: previous?.bannerHiddenAtEpochMillis
        )
        execute(
            """INSERT INTO admin_announcement_recipients(announcement_id,user_id,payload) VALUES (?,?,?)
               ON CONFLICT(announcement_id,user_id) DO UPDATE SET payload=EXCLUDED.payload""",
            UUID.fromString(value.announcementId), UUID.fromString(value.userId), Json.encodeToString(merged)
        )
    }

    override fun replay(actorId: String, requestId: String): AnnouncementReplay? = rows(
        "SELECT payload FROM admin_announcement_replays WHERE actor_id=? AND request_id=?",
        UUID.fromString(actorId), requestId
    ) { Json.decodeFromString<AnnouncementReplay>(it.getString(1)) }.singleOrNull()

    override fun putReplay(value: AnnouncementReplay) {
        if (execute("INSERT INTO admin_announcement_replays(actor_id,request_id,payload) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                UUID.fromString(value.actorId), value.requestId, Json.encodeToString(value)) != 1) conflict()
    }

    override fun preference(userId: String): AnnouncementPreference = rows(
        "SELECT version,admin_enabled,event_enabled FROM admin_announcement_preferences WHERE user_id=?", UUID.fromString(userId)
    ) { AnnouncementPreference(userId, it.getInt(1), it.getBoolean(2), it.getBoolean(3)) }.singleOrNull()
        ?: AnnouncementPreference(userId)

    override fun putPreference(value: AnnouncementPreference) {
        val existing = rows("SELECT version,admin_enabled,event_enabled FROM admin_announcement_preferences WHERE user_id=? FOR UPDATE", UUID.fromString(value.userId)) {
            AnnouncementPreference(value.userId, it.getInt(1), it.getBoolean(2), it.getBoolean(3))
        }.singleOrNull()
        if (existing == null) {
            if (value.version != 1) conflict()
            execute("INSERT INTO admin_announcement_preferences(user_id,version,admin_enabled,event_enabled) VALUES (?,?,?,?)",
                UUID.fromString(value.userId), value.version, value.adminEnabled, value.eventEnabled)
        } else {
            if (existing == value) return
            if (existing.version == Int.MAX_VALUE || value.version != existing.version + 1) conflict()
            execute("UPDATE admin_announcement_preferences SET version=?,admin_enabled=?,event_enabled=? WHERE user_id=?",
                value.version, value.adminEnabled, value.eventEnabled, UUID.fromString(value.userId))
        }
    }

    override fun pushAttempt(announcementId: String, userId: String): AnnouncementPushAttempt? = rows(
        "SELECT payload FROM admin_announcement_push_attempts WHERE announcement_id=? AND user_id=?",
        UUID.fromString(announcementId), UUID.fromString(userId)
    ) { Json.decodeFromString<AnnouncementPushAttempt>(it.getString(1)) }.singleOrNull()

    override fun putPushAttempt(value: AnnouncementPushAttempt) {
        lockAnnouncement(value.announcementId)
        val recipient = rows(
            "SELECT 1 FROM admin_announcement_recipients WHERE announcement_id=? AND user_id=? FOR UPDATE",
            UUID.fromString(value.announcementId), UUID.fromString(value.userId)
        ) { it.getInt(1) }
        if (recipient.isEmpty()) conflict()
        val existing = rows(
            "SELECT payload FROM admin_announcement_push_attempts WHERE announcement_id=? AND user_id=? FOR UPDATE",
            UUID.fromString(value.announcementId), UUID.fromString(value.userId)
        ) { Json.decodeFromString<AnnouncementPushAttempt>(it.getString(1)) }.singleOrNull()
        if (existing == value) return
        if (existing != null && (existing.attemptId != value.attemptId ||
                existing.outcome != AnnouncementPushOutcome.ATTEMPTING ||
                value.outcome == AnnouncementPushOutcome.ATTEMPTING ||
                existing.claimedAtEpochMillis != value.claimedAtEpochMillis)) conflict()
        execute(
            """INSERT INTO admin_announcement_push_attempts(announcement_id,user_id,payload) VALUES (?,?,?)
               ON CONFLICT(announcement_id,user_id) DO UPDATE SET payload=EXCLUDED.payload""",
            UUID.fromString(value.announcementId), UUID.fromString(value.userId), Json.encodeToString(value)
        )
    }

    private fun lockAnnouncement(id: String) {
        if (rows("SELECT id FROM admin_announcements WHERE id=? FOR UPDATE", UUID.fromString(id)) { it.getString(1) }.isEmpty()) conflict()
    }
}
