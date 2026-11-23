package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.CLAN_CHAT_MAX_STORED_MESSAGES
import com.hienthai.fastowin.protocol.ClanChatMessageKind
import com.hienthai.fastowin.protocol.ClanChatMessageSnapshot
import com.hienthai.fastowin.protocol.ClanChatSystemEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

interface ClanChatRepository {
    /** Stores a line and trims the clan to its newest [CLAN_CHAT_MAX_STORED_MESSAGES]. */
    suspend fun append(message: ClanChatMessageSnapshot)
    suspend fun find(clanId: String, messageId: String): ClanChatMessageSnapshot?
    suspend fun findById(messageId: String): ClanChatMessageSnapshot?
    /** Newest first, at most [limit], created at or after [sinceMillis] and older than [before]. */
    suspend fun page(clanId: String, before: ClanChatMessageSnapshot?, limit: Int, sinceMillis: Long): List<ClanChatMessageSnapshot>
    suspend fun delete(clanId: String, messageId: String, atMillis: Long): Boolean
    suspend fun mutedUntil(clanId: String, userId: String, nowMillis: Long): Long?
    suspend fun activeMutes(clanId: String, nowMillis: Long): Map<String, Long>
    /** Null [untilMillis] lifts the mute. */
    suspend fun setMute(clanId: String, userId: String, untilMillis: Long?, mutedBy: String)
    suspend fun markRead(clanId: String, userId: String, atMillis: Long)
    /** Other members' lines after the reader's position, capped at 99. */
    suspend fun unreadCount(clanId: String, userId: String, sinceMillis: Long): Int
    /** Members due a push; their throttle moves to [nowMillis] in the same step. */
    suspend fun claimPush(clanId: String, userIds: Collection<String>, nowMillis: Long, intervalMillis: Long): Set<String>
    suspend fun cleanup(beforeMillis: Long): Int
}

internal const val CLAN_CHAT_UNREAD_CAP = 99

class InMemoryClanChatRepository : ClanChatRepository {
    private val mutex = Mutex()
    private val messages = mutableMapOf<String, MutableList<ClanChatMessageSnapshot>>()
    private val mutes = mutableMapOf<Pair<String, String>, Long>()
    private val reads = mutableMapOf<Pair<String, String>, Long>()
    private val pushes = mutableMapOf<Pair<String, String>, Long>()

    private val order = compareBy<ClanChatMessageSnapshot>({ it.createdAtEpochMillis }, { it.id })

    override suspend fun append(message: ClanChatMessageSnapshot) = mutex.withLock {
        val list = messages.getOrPut(message.clanId) { mutableListOf() }
        list += message
        list.sortWith(order)
        while (list.size > CLAN_CHAT_MAX_STORED_MESSAGES) list.removeAt(0)
    }

    override suspend fun find(clanId: String, messageId: String) = mutex.withLock {
        messages[clanId]?.firstOrNull { it.id == messageId }
    }

    override suspend fun findById(messageId: String) = mutex.withLock {
        messages.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == messageId } }
    }

    override suspend fun page(clanId: String, before: ClanChatMessageSnapshot?, limit: Int, sinceMillis: Long) = mutex.withLock {
        messages[clanId].orEmpty().asReversed().asSequence()
            .filter { it.createdAtEpochMillis >= sinceMillis }
            .filter { before == null || order.compare(it, before) < 0 }
            .take(limit).toList()
    }

    override suspend fun delete(clanId: String, messageId: String, atMillis: Long) = mutex.withLock {
        messages[clanId]?.removeAll { it.id == messageId } == true
    }

    override suspend fun mutedUntil(clanId: String, userId: String, nowMillis: Long) = mutex.withLock {
        mutes[clanId to userId]?.takeIf { it > nowMillis }
    }

    override suspend fun activeMutes(clanId: String, nowMillis: Long) = mutex.withLock {
        mutes.filter { it.key.first == clanId && it.value > nowMillis }.mapKeys { it.key.second }
    }

    override suspend fun setMute(clanId: String, userId: String, untilMillis: Long?, mutedBy: String) = mutex.withLock {
        if (untilMillis == null) mutes.remove(clanId to userId) else mutes[clanId to userId] = untilMillis
        Unit
    }

    override suspend fun markRead(clanId: String, userId: String, atMillis: Long) = mutex.withLock {
        reads[clanId to userId] = maxOf(reads[clanId to userId] ?: 0, atMillis)
    }

    override suspend fun unreadCount(clanId: String, userId: String, sinceMillis: Long) = mutex.withLock {
        val read = maxOf(reads[clanId to userId] ?: 0, sinceMillis - 1)
        messages[clanId].orEmpty().count {
            it.kind == ClanChatMessageKind.USER && it.senderId != userId && it.createdAtEpochMillis > read
        }.coerceAtMost(CLAN_CHAT_UNREAD_CAP)
    }

    override suspend fun claimPush(clanId: String, userIds: Collection<String>, nowMillis: Long, intervalMillis: Long) = mutex.withLock {
        userIds.filterTo(mutableSetOf()) { (pushes[clanId to it] ?: 0) <= nowMillis - intervalMillis }
            .onEach { pushes[clanId to it] = nowMillis }
    }

    override suspend fun cleanup(beforeMillis: Long) = mutex.withLock {
        messages.values.sumOf { list -> list.size.also { list.removeAll { it.createdAtEpochMillis < beforeMillis } } - list.size }
    }
}

class PostgresClanChatRepository(private val source: DataSource) : ClanChatRepository {
    override suspend fun append(message: ClanChatMessageSnapshot) = transaction { connection ->
        connection.prepareStatement("""
            INSERT INTO clan_chat_messages(id, clan_id, kind, sender_id, sender_name, body, system_event, system_args, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use {
            it.setObject(1, UUID.fromString(message.id))
            it.setObject(2, UUID.fromString(message.clanId))
            it.setString(3, message.kind.name)
            it.setObject(4, message.senderId?.let(UUID::fromString))
            it.setString(5, message.senderName.take(64))
            it.setString(6, message.text)
            it.setString(7, message.systemEvent?.name)
            it.setString(8, Json.encodeToString(ARGS, message.systemArgs))
            it.setLong(9, message.createdAtEpochMillis)
            it.executeUpdate()
        }
        // Keep only the newest lines; deleted lines count until the 30-day cleanup removes them.
        connection.prepareStatement("""
            DELETE FROM clan_chat_messages WHERE clan_id = ? AND id IN (
                SELECT id FROM clan_chat_messages WHERE clan_id = ?
                ORDER BY created_at DESC, id DESC OFFSET ?
            )
        """.trimIndent()).use {
            val clan = UUID.fromString(message.clanId)
            it.setObject(1, clan); it.setObject(2, clan); it.setInt(3, CLAN_CHAT_MAX_STORED_MESSAGES)
            it.executeUpdate()
        }
        Unit
    }

    override suspend fun find(clanId: String, messageId: String) = read { connection ->
        connection.prepareStatement("SELECT * FROM clan_chat_messages WHERE clan_id = ? AND id = ? AND deleted_at IS NULL").use {
            it.setObject(1, UUID.fromString(clanId)); it.setObject(2, UUID.fromString(messageId))
            it.executeQuery().use { row -> if (row.next()) row.message() else null }
        }
    }

    override suspend fun findById(messageId: String) = read { connection ->
        connection.prepareStatement("SELECT * FROM clan_chat_messages WHERE id = ? AND deleted_at IS NULL").use {
            it.setObject(1, UUID.fromString(messageId))
            it.executeQuery().use { row -> if (row.next()) row.message() else null }
        }
    }

    override suspend fun page(clanId: String, before: ClanChatMessageSnapshot?, limit: Int, sinceMillis: Long) = read { connection ->
        val cursor = if (before == null) "" else "AND (created_at < ? OR (created_at = ? AND id < ?))"
        connection.prepareStatement("""
            SELECT * FROM clan_chat_messages
            WHERE clan_id = ? AND deleted_at IS NULL AND created_at >= ? $cursor
            ORDER BY created_at DESC, id DESC LIMIT ?
        """.trimIndent()).use {
            var index = 1
            it.setObject(index++, UUID.fromString(clanId))
            it.setLong(index++, sinceMillis)
            if (before != null) {
                it.setLong(index++, before.createdAtEpochMillis)
                it.setLong(index++, before.createdAtEpochMillis)
                it.setObject(index++, UUID.fromString(before.id))
            }
            it.setInt(index, limit)
            it.executeQuery().use { row -> buildList { while (row.next()) add(row.message()) } }
        }
    }

    override suspend fun delete(clanId: String, messageId: String, atMillis: Long) = transaction { connection ->
        connection.prepareStatement("UPDATE clan_chat_messages SET deleted_at = ? WHERE clan_id = ? AND id = ? AND deleted_at IS NULL").use {
            it.setLong(1, atMillis); it.setObject(2, UUID.fromString(clanId)); it.setObject(3, UUID.fromString(messageId))
            it.executeUpdate() == 1
        }
    }

    override suspend fun mutedUntil(clanId: String, userId: String, nowMillis: Long) = read { connection ->
        connection.prepareStatement("SELECT muted_until FROM clan_chat_mutes WHERE clan_id = ? AND user_id = ? AND muted_until > ?").use {
            it.setObject(1, UUID.fromString(clanId)); it.setObject(2, UUID.fromString(userId)); it.setLong(3, nowMillis)
            it.executeQuery().use { row -> if (row.next()) row.getLong(1) else null }
        }
    }

    override suspend fun activeMutes(clanId: String, nowMillis: Long) = read { connection ->
        connection.prepareStatement("SELECT user_id, muted_until FROM clan_chat_mutes WHERE clan_id = ? AND muted_until > ?").use {
            it.setObject(1, UUID.fromString(clanId)); it.setLong(2, nowMillis)
            it.executeQuery().use { row -> buildMap { while (row.next()) put(row.getString(1), row.getLong(2)) } }
        }
    }

    override suspend fun setMute(clanId: String, userId: String, untilMillis: Long?, mutedBy: String) = transaction { connection ->
        if (untilMillis == null) {
            connection.prepareStatement("DELETE FROM clan_chat_mutes WHERE clan_id = ? AND user_id = ?").use {
                it.setObject(1, UUID.fromString(clanId)); it.setObject(2, UUID.fromString(userId)); it.executeUpdate()
            }
        } else {
            connection.prepareStatement("""
                INSERT INTO clan_chat_mutes(clan_id, user_id, muted_until, muted_by) VALUES (?, ?, ?, ?)
                ON CONFLICT (clan_id, user_id) DO UPDATE SET muted_until = EXCLUDED.muted_until, muted_by = EXCLUDED.muted_by
            """.trimIndent()).use {
                it.setObject(1, UUID.fromString(clanId)); it.setObject(2, UUID.fromString(userId))
                it.setLong(3, untilMillis); it.setObject(4, UUID.fromString(mutedBy)); it.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun markRead(clanId: String, userId: String, atMillis: Long) = transaction { connection ->
        connection.prepareStatement("""
            INSERT INTO clan_chat_members(clan_id, user_id, last_read_at) VALUES (?, ?, ?)
            ON CONFLICT (clan_id, user_id) DO UPDATE
                SET last_read_at = GREATEST(clan_chat_members.last_read_at, EXCLUDED.last_read_at)
        """.trimIndent()).use {
            it.setObject(1, UUID.fromString(clanId)); it.setObject(2, UUID.fromString(userId)); it.setLong(3, atMillis)
            it.executeUpdate()
        }
        Unit
    }

    override suspend fun unreadCount(clanId: String, userId: String, sinceMillis: Long) = read { connection ->
        connection.prepareStatement("""
            SELECT COUNT(*) FROM (
                SELECT 1 FROM clan_chat_messages m
                WHERE m.clan_id = ? AND m.kind = 'USER' AND m.deleted_at IS NULL AND m.sender_id <> ?
                  AND m.created_at >= ?
                  AND m.created_at > COALESCE((SELECT last_read_at FROM clan_chat_members r WHERE r.clan_id = ? AND r.user_id = ?), 0)
                LIMIT $CLAN_CHAT_UNREAD_CAP
            ) unread
        """.trimIndent()).use {
            val clan = UUID.fromString(clanId); val user = UUID.fromString(userId)
            it.setObject(1, clan); it.setObject(2, user); it.setLong(3, sinceMillis); it.setObject(4, clan); it.setObject(5, user)
            it.executeQuery().use { row -> row.next(); row.getInt(1) }
        }
    }

    override suspend fun claimPush(clanId: String, userIds: Collection<String>, nowMillis: Long, intervalMillis: Long) =
        if (userIds.isEmpty()) emptySet() else transaction { connection ->
            val ids = connection.createArrayOf("uuid", userIds.map(UUID::fromString).toTypedArray())
            try {
                connection.prepareStatement("""
                    INSERT INTO clan_chat_members(clan_id, user_id, last_push_at)
                    SELECT ?, u, ? FROM unnest(?::uuid[]) AS u
                    ON CONFLICT (clan_id, user_id) DO UPDATE SET last_push_at = EXCLUDED.last_push_at
                        WHERE clan_chat_members.last_push_at <= ?
                    RETURNING user_id
                """.trimIndent()).use {
                    it.setObject(1, UUID.fromString(clanId)); it.setLong(2, nowMillis); it.setArray(3, ids)
                    it.setLong(4, nowMillis - intervalMillis)
                    it.executeQuery().use { row -> buildSet { while (row.next()) add(row.getString(1)) } }
                }
            } finally { ids.free() }
        }

    override suspend fun cleanup(beforeMillis: Long) = transaction { connection ->
        connection.prepareStatement("DELETE FROM clan_chat_messages WHERE created_at < ?").use {
            it.setLong(1, beforeMillis); it.executeUpdate()
        }
    }

    private fun ResultSet.message() = ClanChatMessageSnapshot(
        id = getString("id"),
        clanId = getString("clan_id"),
        kind = ClanChatMessageKind.valueOf(getString("kind")),
        createdAtEpochMillis = getLong("created_at"),
        senderId = getString("sender_id"),
        senderName = getString("sender_name"),
        text = getString("body"),
        systemEvent = getString("system_event")?.let { name -> ClanChatSystemEvent.entries.firstOrNull { it.name == name } },
        systemArgs = runCatching { Json.decodeFromString(ARGS, getString("system_args")) }.getOrDefault(emptyMap())
    )

    private suspend fun <T> read(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        source.connection.use(block)
    }

    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.autoCommit = false
            try {
                block(connection).also { connection.commit() }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    private companion object {
        val ARGS = kotlinx.serialization.builtins.MapSerializer(String.serializer(), String.serializer())
    }
}
