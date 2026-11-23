package com.hienthai.fastowin.server.avatars

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

/**
 * Uploaded clan logos, keyed by clan ID, with the same intent/commit/cleanup protocol as avatars.
 * Objects share `avatar_cleanup`; the avatar cleaner deletes both kinds once nothing references them.
 */
class PostgresClanLogoRepository(
    private val source: DataSource,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : AvatarRepository, ClanLogoChangeLog {
    override suspend fun current(playerId: String): AvatarSnapshot? = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.prepareStatement("""
                SELECT COALESCE(l.revision, 0) AS revision, l.legacy_data, l.object_key, l.content_type,
                       l.byte_size, l.sha256, l.width, l.height
                FROM clans c LEFT JOIN clan_logos l ON l.clan_id = c.id WHERE c.id = ?
            """.trimIndent()).use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.executeQuery().use { row ->
                    if (!row.next()) null else AvatarSnapshot(row.getLong("revision"), row.getString("legacy_data"), row.metadata())
                }
            }
        }
    }

    override suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String): Boolean = transaction { connection ->
        if (!connection.lockLogo(playerId, expectedRevision)) return@transaction false
        connection.enqueueCurrent(playerId, nowMillis() + PostgresAvatarRepository.OLD_OBJECT_DELAY)
        connection.prepareStatement("""
            UPDATE clan_logos SET legacy_data = ?, object_key = NULL, content_type = NULL, byte_size = NULL,
                sha256 = NULL, width = NULL, height = NULL, revision = revision + 1, updated_at = NOW()
            WHERE clan_id = ?
        """.trimIndent()).use {
            it.setString(1, base64); it.setObject(2, UUID.fromString(playerId)); it.executeUpdate()
        }
        true
    }

    override suspend fun beginIntent(playerId: String, expectedRevision: Long, key: String): Boolean = transaction { connection ->
        if (!connection.lockLogo(playerId, expectedRevision)) return@transaction false
        connection.prepareStatement("INSERT INTO avatar_cleanup(object_key, state, available_at, intent_expires_at) VALUES (?, 'pending', ?, ?)").use {
            it.setString(1, key)
            val expires = Timestamp(nowMillis() + PostgresAvatarRepository.INTENT_LIFETIME)
            it.setTimestamp(2, expires); it.setTimestamp(3, expires); it.executeUpdate()
        }
        true
    }

    override suspend fun commit(playerId: String, expectedRevision: Long, metadata: AvatarMetadata): Boolean = transaction { connection ->
        if (!connection.lockLogo(playerId, expectedRevision)) return@transaction false
        val pending = connection.prepareStatement("SELECT state, intent_expires_at FROM avatar_cleanup WHERE object_key = ? FOR UPDATE").use {
            it.setString(1, metadata.key)
            it.executeQuery().use { row ->
                row.next() && row.getString("state") == "pending" &&
                    (row.getTimestamp("intent_expires_at")?.time ?: 0) > nowMillis()
            }
        }
        if (!pending) return@transaction false
        connection.enqueueCurrent(playerId, nowMillis() + PostgresAvatarRepository.OLD_OBJECT_DELAY)
        connection.prepareStatement("""
            UPDATE clan_logos SET legacy_data = NULL, object_key = ?, content_type = ?, byte_size = ?, sha256 = ?,
                width = ?, height = ?, revision = revision + 1, updated_at = NOW()
            WHERE clan_id = ?
        """.trimIndent()).use {
            it.setString(1, metadata.key); it.setString(2, metadata.contentType); it.setInt(3, metadata.byteSize)
            it.setString(4, metadata.sha256); it.setInt(5, metadata.width); it.setInt(6, metadata.height)
            it.setObject(7, UUID.fromString(playerId))
            it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM avatar_cleanup WHERE object_key = ?").use {
            it.setString(1, metadata.key); it.executeUpdate()
        }
        true
    }

    override suspend fun clear(ownerId: String, expectedRevision: Long): Boolean = transaction { connection ->
        if (!connection.lockLogo(ownerId, expectedRevision)) return@transaction false
        connection.enqueueCurrent(ownerId, nowMillis() + PostgresAvatarRepository.OLD_OBJECT_DELAY)
        connection.prepareStatement("""
            UPDATE clan_logos SET legacy_data = NULL, object_key = NULL, content_type = NULL, byte_size = NULL,
                sha256 = NULL, width = NULL, height = NULL, revision = revision + 1, updated_at = NOW()
            WHERE clan_id = ?
        """.trimIndent()).use {
            it.setObject(1, UUID.fromString(ownerId)); it.executeUpdate()
        }
        true
    }

    // Object cleanup is shared with avatars and runs from the avatar service only.
    override suspend fun claimCleanup(limit: Int) = emptyList<AvatarCleanupItem>()
    override suspend fun completeCleanup(key: String) {}
    override suspend fun retryCleanup(key: String) {}

    /** Changes recorded in the last 24 hours, used for the daily logo change limit. */
    override suspend fun changesSince(clanId: String, sinceMillis: Long): Int = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM clan_logo_changes WHERE clan_id = ? AND changed_at >= ?").use {
                it.setObject(1, UUID.fromString(clanId)); it.setLong(2, sinceMillis)
                it.executeQuery().use { row -> row.next(); row.getInt(1) }
            }
        }
    }

    override suspend fun recordChange(clanId: String, atMillis: Long): Unit = transaction { connection ->
        connection.prepareStatement("INSERT INTO clan_logo_changes(clan_id, changed_at) VALUES (?, ?)").use {
            it.setObject(1, UUID.fromString(clanId)); it.setLong(2, atMillis); it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM clan_logo_changes WHERE clan_id = ? AND changed_at < ?").use {
            it.setObject(1, UUID.fromString(clanId)); it.setLong(2, atMillis - 2 * 86_400_000L); it.executeUpdate()
        }
        Unit
    }

    /** Creates the logo row on first use, then locks it. Inserting never touches the clans row. */
    private fun Connection.lockLogo(clanId: String, expectedRevision: Long): Boolean {
        val id = UUID.fromString(clanId)
        prepareStatement("INSERT INTO clan_logos(clan_id) SELECT id FROM clans WHERE id = ? ON CONFLICT (clan_id) DO NOTHING").use {
            it.setObject(1, id); it.executeUpdate()
        }
        return prepareStatement("SELECT revision FROM clan_logos WHERE clan_id = ? FOR UPDATE").use {
            it.setObject(1, id)
            it.executeQuery().use { row -> row.next() && row.getLong(1) == expectedRevision }
        }
    }

    private fun Connection.enqueueCurrent(clanId: String, deleteAfter: Long) {
        prepareStatement("""
            INSERT INTO avatar_cleanup(object_key, state, available_at)
                SELECT object_key, 'pending', ? FROM clan_logos WHERE clan_id = ? AND object_key IS NOT NULL
            ON CONFLICT (object_key) DO NOTHING
        """.trimIndent()).use {
            it.setTimestamp(1, Timestamp(deleteAfter)); it.setObject(2, UUID.fromString(clanId)); it.executeUpdate()
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
}

/** Memory engines and tests: legacy base64 only, like [ProfileAvatarRepository]. */
class InMemoryClanLogoRepository(private val clanExists: suspend (String) -> Boolean) : AvatarRepository, ClanLogoChangeLog {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val logos = mutableMapOf<String, Pair<Long, String?>>()
    private val changes = mutableListOf<Pair<String, Long>>()
    override suspend fun changesSince(clanId: String, sinceMillis: Long) =
        mutex.withLockValue { changes.count { it.first == clanId && it.second >= sinceMillis } }
    override suspend fun recordChange(clanId: String, atMillis: Long) = mutex.withLockValue { changes += clanId to atMillis; Unit }
    override suspend fun current(playerId: String): AvatarSnapshot? {
        if (!clanExists(playerId)) return null
        return mutex.withLockValue { logos[playerId].let { AvatarSnapshot(it?.first ?: 0, it?.second, null) } }
    }
    override suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String) = swap(playerId, expectedRevision, base64)
    override suspend fun clear(ownerId: String, expectedRevision: Long) = swap(ownerId, expectedRevision, null)
    private suspend fun swap(id: String, expected: Long, value: String?) = mutex.withLockValue {
        if ((logos[id]?.first ?: 0) != expected) false else { logos[id] = (expected + 1) to value; true }
    }
    override suspend fun beginIntent(playerId: String, expectedRevision: Long, key: String) = false
    override suspend fun commit(playerId: String, expectedRevision: Long, metadata: AvatarMetadata) = false
    override suspend fun claimCleanup(limit: Int) = emptyList<AvatarCleanupItem>()
    override suspend fun completeCleanup(key: String) {}
    override suspend fun retryCleanup(key: String) {}
    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockValue(block: () -> T): T {
        lock(); try { return block() } finally { unlock() }
    }
}
