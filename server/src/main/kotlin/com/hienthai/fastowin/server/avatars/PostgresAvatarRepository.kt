package com.hienthai.fastowin.server.avatars

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class PostgresAvatarRepository(private val source: DataSource, private val nowMillis: () -> Long = System::currentTimeMillis) : AvatarRepository {
    override suspend fun current(playerId: String): AvatarSnapshot? = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.prepareStatement("""
                SELECT p.avatar_revision, p.avatar_data, a.object_key, a.content_type, a.byte_size, a.sha256, a.width, a.height
                FROM users u JOIN profiles p ON p.user_id = u.id
                LEFT JOIN avatar_objects a ON a.user_id = u.id WHERE u.id = ? AND u.status = 'ACTIVE'
            """.trimIndent()).use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.executeQuery().use { row ->
                    if (!row.next()) null else AvatarSnapshot(row.getLong("avatar_revision"), row.getString("avatar_data"), row.metadata())
                }
            }
        }
    }

    override suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String): Boolean = transaction { connection ->
        if (!connection.lockActivePlayer(playerId, expectedRevision)) return@transaction false
        connection.enqueueCurrent(playerId, nowMillis() + OLD_OBJECT_DELAY)
        connection.prepareStatement("DELETE FROM avatar_objects WHERE user_id = ?").use {
            it.setObject(1, UUID.fromString(playerId)); it.executeUpdate()
        }
        connection.prepareStatement("UPDATE profiles SET avatar_data = ?, avatar_revision = avatar_revision + 1, updated_at = NOW() WHERE user_id = ?").use {
            it.setString(1, base64); it.setObject(2, UUID.fromString(playerId)); it.executeUpdate()
        }
        true
    }

    override suspend fun beginIntent(playerId: String, expectedRevision: Long, key: String): Boolean = transaction { connection ->
        if (!connection.lockActivePlayer(playerId, expectedRevision)) return@transaction false
        connection.prepareStatement("INSERT INTO avatar_cleanup(object_key, state, available_at, intent_expires_at) VALUES (?, 'pending', ?, ?)").use {
            it.setString(1, key)
            val expires = Timestamp(nowMillis() + INTENT_LIFETIME)
            it.setTimestamp(2, expires); it.setTimestamp(3, expires); it.executeUpdate()
        }
        true
    }

    override suspend fun commit(playerId: String, expectedRevision: Long, metadata: AvatarMetadata): Boolean = transaction { connection ->
        if (!connection.lockActivePlayer(playerId, expectedRevision)) return@transaction false
        // Lock the intent, rather than merely observing it: a cleaner can never delete then lose to late CAS.
        val pending = connection.prepareStatement("SELECT state, intent_expires_at FROM avatar_cleanup WHERE object_key = ? FOR UPDATE").use {
            it.setString(1, metadata.key)
            it.executeQuery().use { row ->
                row.next() && row.getString("state") == "pending" &&
                    (row.getTimestamp("intent_expires_at")?.time ?: 0) > nowMillis()
            }
        }
        if (!pending) return@transaction false
        connection.enqueueCurrent(playerId, nowMillis() + OLD_OBJECT_DELAY)
        connection.prepareStatement("""
            INSERT INTO avatar_objects(user_id, object_key, content_type, byte_size, sha256, width, height, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) ON CONFLICT(user_id) DO UPDATE SET
                object_key = EXCLUDED.object_key, content_type = EXCLUDED.content_type, byte_size = EXCLUDED.byte_size,
                sha256 = EXCLUDED.sha256, width = EXCLUDED.width, height = EXCLUDED.height, updated_at = NOW()
        """.trimIndent()).use {
            it.setObject(1, UUID.fromString(playerId)); it.setString(2, metadata.key); it.setString(3, metadata.contentType)
            it.setInt(4, metadata.byteSize); it.setString(5, metadata.sha256); it.setInt(6, metadata.width); it.setInt(7, metadata.height)
            it.executeUpdate()
        }
        connection.prepareStatement("UPDATE profiles SET avatar_data = NULL, avatar_revision = avatar_revision + 1, updated_at = NOW() WHERE user_id = ?").use {
            it.setObject(1, UUID.fromString(playerId)); it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM avatar_cleanup WHERE object_key = ?").use {
            it.setString(1, metadata.key); it.executeUpdate()
        }
        true
    }

    override suspend fun claimCleanup(limit: Int): List<AvatarCleanupItem> = transaction { connection ->
        // A crash after the eighth claim must not leave invisible work in a permanent lease.
        connection.prepareStatement("""
            UPDATE avatar_cleanup SET state = 'failed', last_error_code = 'OBJECT_DELETE_RETRY_EXHAUSTED', updated_at = NOW()
            WHERE state = 'deleting' AND attempts >= 8 AND available_at <= ?
        """.trimIndent()).use {
            it.setTimestamp(1, Timestamp(nowMillis())); it.executeUpdate()
        }
        connection.prepareStatement("""
            WITH due AS (
                SELECT q.object_key FROM avatar_cleanup q
                WHERE q.state IN ('pending', 'deleting') AND q.available_at <= ? AND q.attempts < 8
                  AND NOT EXISTS (SELECT 1 FROM avatar_objects a WHERE a.object_key = q.object_key)
                ORDER BY q.available_at LIMIT ? FOR UPDATE OF q SKIP LOCKED
            )
            UPDATE avatar_cleanup q SET state = 'deleting', attempts = q.attempts + 1,
                available_at = ?, updated_at = NOW() FROM due WHERE q.object_key = due.object_key RETURNING q.object_key
        """.trimIndent()).use {
            it.setTimestamp(1, Timestamp(nowMillis())); it.setInt(2, limit.coerceIn(1, 100))
            it.setTimestamp(3, Timestamp(nowMillis() + DELETE_LEASE))
            it.executeQuery().use { row -> buildList { while (row.next()) add(AvatarCleanupItem(row.getString(1))) } }
        }
    }

    override suspend fun completeCleanup(key: String): Unit = transaction { connection ->
        connection.prepareStatement("UPDATE avatar_cleanup SET state = 'deleted', last_error_code = NULL, updated_at = NOW() WHERE object_key = ? AND state = 'deleting'").use {
            it.setString(1, key); it.executeUpdate()
        }
        Unit
    }

    override suspend fun retryCleanup(key: String): Unit = transaction { connection ->
        connection.prepareStatement("""
            UPDATE avatar_cleanup SET state = CASE WHEN attempts >= 8 THEN 'failed' ELSE 'deleting' END,
                available_at = CAST(? AS timestamptz) + (LEAST(3600, 30 * power(2, attempts)) * INTERVAL '1 second'),
                last_error_code = 'OBJECT_DELETE_UNAVAILABLE', updated_at = NOW()
            WHERE object_key = ? AND state = 'deleting'
        """.trimIndent()).use {
            it.setTimestamp(1, Timestamp(nowMillis())); it.setString(2, key); it.executeUpdate()
        }
        Unit
    }

    private fun Connection.lockActivePlayer(playerId: String, expectedRevision: Long): Boolean =
        prepareStatement("""
            SELECT p.avatar_revision FROM users u JOIN profiles p ON p.user_id = u.id
            WHERE u.id = ? AND u.status = 'ACTIVE' FOR UPDATE OF u, p
        """.trimIndent()).use {
            it.setObject(1, UUID.fromString(playerId))
            it.executeQuery().use { row -> row.next() && row.getLong(1) == expectedRevision }
        }

    private fun Connection.enqueueCurrent(playerId: String, deleteAfter: Long) {
        prepareStatement("""
            INSERT INTO avatar_cleanup(object_key, state, available_at)
                SELECT object_key, 'pending', ? FROM avatar_objects WHERE user_id = ?
            ON CONFLICT (object_key) DO NOTHING
        """.trimIndent()).use {
            it.setTimestamp(1, Timestamp(deleteAfter)); it.setObject(2, UUID.fromString(playerId)); it.executeUpdate()
        }
    }

    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private fun ResultSet.metadata(): AvatarMetadata? {
        val key = getString("object_key") ?: return null
        return AvatarMetadata(key, getString("content_type"), getInt("byte_size"), getString("sha256"), getInt("width"), getInt("height"))
    }

    companion object {
        const val INTENT_LIFETIME = 60 * 60_000L
        const val OLD_OBJECT_DELAY = 24 * 60 * 60_000L
        private const val DELETE_LEASE = 60_000L
    }
}
