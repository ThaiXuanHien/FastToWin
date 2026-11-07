package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.RoomSummary
import com.hienthai.fastowin.server.StoredActiveRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal class PostgresRoomOwnershipRepository(private val dataSource: DataSource) : RoomOwnershipRepository {
    override suspend fun claim(roomId: UUID, instanceId: UUID): OwnedRoomState? = transaction {
        val room = load(roomId, lock = true) ?: return@transaction null
        if (room.state.closed || room.leaseUntil > databaseTime()) return@transaction null
        val recoveryNeeded = room.state.snapshot.phase == RoomPhase.PLAYING
        query(
            """
            UPDATE room_ownership_pilot_rooms
            SET instance_id = ?, owner_epoch = owner_epoch + 1,
                lease_until = clock_timestamp() + interval '12 seconds',
                recovery_deadline = CASE WHEN ? THEN
                    COALESCE(recovery_deadline, clock_timestamp() + interval '30 seconds')
                    ELSE NULL END
            WHERE room_id = ?
            RETURNING *, clock_timestamp() AS observed_at
            """,
            instanceId, recoveryNeeded, roomId
        ) { rows -> check(rows.next()); rows.state() }
    }

    override suspend fun expiredRooms(limit: Int): List<UUID> {
        require(limit in 1..MAX_EVENT_REVISIONS)
        return transaction {
            query(
                """
                SELECT room_id FROM room_ownership_pilot_rooms
                WHERE closed_at IS NULL AND lease_until <= statement_timestamp()
                ORDER BY lease_until, room_id LIMIT ?
                """,
                limit
            ) { rows -> buildList { while (rows.next()) add(rows.getObject(1, UUID::class.java)) } }
        }
    }

    override suspend fun renew(fence: OwnerFence): Boolean = updateLease(fence, release = false)
    override suspend fun release(fence: OwnerFence): Boolean = updateLease(fence, release = true)

    private suspend fun updateLease(fence: OwnerFence, release: Boolean): Boolean = transaction {
        val room = load(fence.roomId, lock = true) ?: return@transaction false
        if (room.state.closed || room.state.fence != fence) return@transaction false
        val duration = if (release) "0 seconds" else "12 seconds"
        update(
            """
            UPDATE room_ownership_pilot_rooms
            SET lease_until = clock_timestamp() + CAST(? AS interval)
            WHERE room_id = ? AND lease_until > clock_timestamp()
            """,
            duration, fence.roomId
        ) == 1
    }

    override suspend fun read(roomId: UUID): OwnedRoomState? = transaction {
        load(roomId)?.state?.let { it.copy(sessionGenerations = generations(it.snapshot)) }
    }

    override suspend fun lookupRoom(playerId: UUID): UUID? = transaction {
        query("SELECT room_id FROM room_ownership_pilot_memberships WHERE player_id = ?", playerId) { rows ->
            if (rows.next()) rows.getObject("room_id", UUID::class.java) else null
        }
    }

    suspend fun listJoinableRooms(): List<RoomSummary> = transaction {
        query(
            """
            SELECT r.room_id, r.snapshot->>'roomName' AS room_name,
                   r.snapshot#>>'{host,displayName}' AS host_name, s.player_id AS host_id
            FROM room_ownership_pilot_rooms r
            JOIN room_ownership_pilot_sessions s ON s.player_id::text = r.snapshot#>>'{host,playerId}'
            WHERE r.closed_at IS NULL AND r.lease_until > statement_timestamp()
              AND r.snapshot->>'phase' = 'WAITING' AND r.snapshot->>'guest' IS NULL
              AND r.snapshot->>'pilotConnectionEpoch' = r.owner_epoch::text
              AND r.snapshot->'pilotSocketGenerations'->>s.player_id::text = s.generation::text
            ORDER BY lower(r.snapshot->>'roomName'), r.room_id
            LIMIT ?
            """,
            MAX_EVENT_REVISIONS
        ) { rows ->
            buildList {
                while (rows.next()) add(RoomSummary(
                    rows.getString("room_id"), rows.getString("room_name"), rows.getString("host_name"),
                    ProtocolGameMode.ORDER, requiresPassword = true, hostId = rows.getString("host_id")
                ))
            }
        }
    }

    override suspend fun bindSocket(playerId: UUID): SocketFence = transaction {
        query(
            """
            INSERT INTO room_ownership_pilot_sessions (player_id, generation) VALUES (?, 1)
            ON CONFLICT (player_id) DO UPDATE SET generation = room_ownership_pilot_sessions.generation + 1
            RETURNING generation
            """,
            playerId
        ) { rows -> check(rows.next()); SocketFence(playerId, rows.getLong("generation")) }
    }

    override suspend fun isCurrentSocket(socket: SocketFence): Boolean = transaction {
        isCurrent(socket, lock = false)
    }

    override suspend fun createRoom(
        instanceId: UUID,
        socket: SocketFence,
        initialSnapshot: StoredActiveRoom,
        events: List<RoomOwnershipEvent>
    ): FencedResult {
        val roomId = UUID.fromString(initialSnapshot.roomId)
        if (!initialSnapshot.isPilotRoom() || initialSnapshot.phase != RoomPhase.WAITING ||
            initialSnapshot.guest != null || initialSnapshot.host.playerId != socket.playerId.toString()
        ) return FencedResult.Rejected("INVALID_PILOT_ROOM")
        return fencedTransaction {
            if (!isCurrent(socket)) return@fencedTransaction FencedResult.Stale
            val inserted = update(
                """
                INSERT INTO room_ownership_pilot_rooms (room_id, instance_id, owner_epoch, revision, lease_until, snapshot)
                VALUES (?, ?, 1, 1, clock_timestamp() + interval '12 seconds', CAST(? AS jsonb))
                ON CONFLICT (room_id) DO NOTHING
                """,
                roomId, instanceId, ProtocolJson.encodeToString(initialSnapshot)
            )
            if (inserted != 1) return@fencedTransaction FencedResult.Rejected("ROOM_ALREADY_EXISTS")
            if (!addMembership(socket.playerId, roomId)) {
                return@fencedTransaction FencedResult.Rejected("ALREADY_IN_ROOM")
            }
            val state = OwnedRoomState(OwnerFence(roomId, instanceId, 1), 1, initialSnapshot, null, observedAt = databaseTime())
            saveEvents(state, events)
            FencedResult.Committed(state)
        }
    }

    override suspend fun withFencedRoom(
        fence: OwnerFence,
        expectedRevision: Long,
        socket: SocketFence?,
        change: (Connection, OwnedRoomState) -> RoomMutation
    ): FencedResult = fencedTransaction {
        if (socket != null && !isCurrent(socket)) return@fencedTransaction FencedResult.Stale
        val room = load(fence.roomId, lock = true) ?: return@fencedTransaction FencedResult.Stale
        val state = room.state.copy(observedAt = databaseTime())
        if (state.closed || state.fence != fence || state.revision != expectedRevision ||
            room.leaseUntil <= state.observedAt
        ) return@fencedTransaction FencedResult.Stale
        // Observe other generations without locking them after the room row.
        val observed = state.copy(sessionGenerations = generations(state.snapshot))
        when (val mutation = change(this, observed)) {
            is RoomMutation.Reject -> FencedResult.Rejected(mutation.reason)
            is RoomMutation.Replay -> FencedResult.Replay(observed, mutation.message)
            RoomMutation.Unchanged -> FencedResult.Unchanged(observed)
            is RoomMutation.Change -> applyChange(observed, mutation)
        }
    }

    private fun Connection.applyChange(state: OwnedRoomState, mutation: RoomMutation.Change): FencedResult {
        val roomId = state.fence.roomId
        val snapshot = mutation.snapshot
        if (snapshot.roomId != roomId.toString() || !snapshot.isPilotRoom()) {
            return FencedResult.Rejected("INVALID_PILOT_ROOM")
        }
        val previous = state.snapshot.memberIds()
        val current = if (mutation.close) emptySet() else snapshot.memberIds()
        for (player in (previous - current).sorted()) {
            update("DELETE FROM room_ownership_pilot_memberships WHERE player_id = ? AND room_id = ?", player, roomId)
        }
        for (player in (current - previous).sorted()) {
            if (!addMembership(player, roomId)) return FencedResult.Rejected("ALREADY_IN_ROOM")
        }
        // The row is still locked. Recheck expiry after the callback and any membership writes.
        val updated = update(
            """
            UPDATE room_ownership_pilot_rooms
            SET revision = revision + 1, snapshot = CAST(? AS jsonb),
                closed_at = CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                recovery_deadline = CASE WHEN ? THEN NULL ELSE recovery_deadline END
            WHERE room_id = ? AND lease_until > clock_timestamp()
            """,
            ProtocolJson.encodeToString(snapshot), mutation.close,
            mutation.close || mutation.clearRecoveryDeadline, roomId
        )
        if (updated != 1) return FencedResult.Stale
        val committed = state.copy(
            revision = state.revision + 1,
            snapshot = snapshot,
            closed = mutation.close,
            recoveryDeadline = state.recoveryDeadline.takeUnless { mutation.close || mutation.clearRecoveryDeadline }
        )
        saveEvents(committed, mutation.events)
        return FencedResult.Committed(committed, mutation.reply)
    }

    override suspend fun purgeExpiredClosedRooms(limit: Int): Int {
        require(limit in 1..MAX_EVENT_REVISIONS)
        return transaction {
            update(
                """
                DELETE FROM room_ownership_pilot_rooms WHERE room_id IN (
                    SELECT room_id FROM room_ownership_pilot_rooms
                    WHERE closed_at <= statement_timestamp() - interval '2 minutes'
                    ORDER BY closed_at, room_id LIMIT ? FOR UPDATE SKIP LOCKED
                )
                """,
                limit
            )
        }
    }

    override suspend fun readEvents(roomId: UUID, afterRevision: Long, limit: Int): RoomEventBatch {
        require(afterRevision >= 0)
        require(limit in 1..MAX_EVENT_REVISIONS)
        return transaction {
            // The room head and event range use the same statement snapshot.
            query(
                """
                SELECT r.revision AS head, e.revision, e.owner_epoch, e.events
                FROM room_ownership_pilot_rooms r
                LEFT JOIN LATERAL (
                    SELECT revision, owner_epoch, events FROM room_ownership_pilot_events
                    WHERE room_id = r.room_id AND revision > ?
                      AND created_at > statement_timestamp() - interval '2 minutes'
                    ORDER BY revision LIMIT ?
                ) e ON true
                WHERE r.room_id = ?
                ORDER BY e.revision
                """,
                afterRevision, limit, roomId
            ) { rows ->
                if (!rows.next()) return@query RoomEventBatch.Gap
                val head = rows.getLong("head")
                if (afterRevision > head) return@query RoomEventBatch.Gap
                val events = mutableListOf<RoomEventRevision>()
                do {
                    val revision = rows.getLong("revision")
                    if (rows.wasNull()) break
                    if (revision != afterRevision + events.size + 1) return@query RoomEventBatch.Gap
                    events += RoomEventRevision(
                        rows.getLong("owner_epoch"), revision, ProtocolJson.decodeFromString(rows.getString("events"))
                    )
                } while (rows.next())
                if (events.size < limit && afterRevision + events.size != head) RoomEventBatch.Gap
                else RoomEventBatch.Contiguous(events)
            }
        }
    }

    private fun Connection.saveEvents(state: OwnedRoomState, events: List<RoomOwnershipEvent>) {
        update(
            """
            INSERT INTO room_ownership_pilot_events (room_id, revision, owner_epoch, events)
            VALUES (?, ?, ?, CAST(? AS jsonb))
            """,
            state.fence.roomId, state.revision, state.fence.epoch, ProtocolJson.encodeToString(events)
        )
        update(
            """
            DELETE FROM room_ownership_pilot_events
            WHERE room_id = ? AND (revision <= ? OR created_at <= clock_timestamp() - interval '2 minutes')
            """,
            state.fence.roomId, state.revision - MAX_EVENT_REVISIONS
        )
        query(
            "SELECT pg_notify('room_ownership_pilot', ?)",
            "${state.fence.roomId}:${state.revision}"
        ) { rows -> check(rows.next()) }
    }

    private fun Connection.addMembership(playerId: UUID, roomId: UUID): Boolean = update(
        """
        INSERT INTO room_ownership_pilot_memberships (player_id, room_id) VALUES (?, ?)
        ON CONFLICT (player_id) DO NOTHING
        """,
        playerId, roomId
    ) == 1

    private fun Connection.isCurrent(socket: SocketFence, lock: Boolean = true): Boolean = query(
        "SELECT generation FROM room_ownership_pilot_sessions WHERE player_id = ?" + if (lock) " FOR UPDATE" else "",
        socket.playerId
    ) { rows -> rows.next() && rows.getLong("generation") == socket.generation }

    private fun Connection.generations(snapshot: StoredActiveRoom): Map<String, Long> {
        val host = UUID.fromString(snapshot.host.playerId)
        val guest = snapshot.guest?.playerId?.let(UUID::fromString) ?: host
        return query(
            "SELECT player_id, generation FROM room_ownership_pilot_sessions WHERE player_id IN (?, ?)",
            host, guest
        ) { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getLong(2)) } }
    }

    private fun Connection.load(roomId: UUID, lock: Boolean = false): RoomRow? {
        val lockingClause = if (lock) " FOR UPDATE" else ""
        return query("SELECT *, clock_timestamp() AS observed_at FROM room_ownership_pilot_rooms WHERE room_id = ?$lockingClause", roomId) { rows ->
            if (rows.next()) RoomRow(
                rows.state(), rows.getTimestamp("lease_until").toInstant()
            ) else null
        }
    }

    private fun ResultSet.state() = OwnedRoomState(
        fence = OwnerFence(getObject("room_id", UUID::class.java), getObject("instance_id", UUID::class.java), getLong("owner_epoch")),
        revision = getLong("revision"),
        snapshot = ProtocolJson.decodeFromString(getString("snapshot")),
        recoveryDeadline = getTimestamp("recovery_deadline")?.toInstant(),
        closed = getTimestamp("closed_at") != null,
        observedAt = getTimestamp("observed_at").toInstant()
    )

    private fun Connection.databaseTime(): Instant = query("SELECT clock_timestamp()") { rows ->
        check(rows.next())
        rows.getTimestamp(1).toInstant()
    }

    private suspend fun fencedTransaction(block: Connection.() -> FencedResult): FencedResult = try {
        transaction(commit = { it is FencedResult.Committed }, block = block)
    } catch (_: SQLException) {
        FencedResult.TemporaryFailure
    }

    private suspend fun <T> transaction(commit: (T) -> Boolean = { true }, block: Connection.() -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    connection.createStatement().use { statement ->
                        statement.execute("SET LOCAL lock_timeout = '1s'")
                        statement.execute("SET LOCAL statement_timeout = '2s'")
                    }
                    val result = connection.block()
                    if (commit(result)) connection.commit() else connection.rollback()
                    result
                } catch (error: Throwable) {
                    runCatching { connection.rollback() }.exceptionOrNull()?.let(error::addSuppressed)
                    throw error
                }
            }
        }

    private fun Connection.update(sql: String, vararg args: Any): Int = prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }

    private fun <T> Connection.query(sql: String, vararg args: Any, read: (ResultSet) -> T): T =
        prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use(read)
        }

    private fun StoredActiveRoom.isPilotRoom(): Boolean =
        matchType == MatchType.CASUAL && gameMode == ProtocolGameMode.ORDER &&
            passwordSalt?.isNotEmpty() == true && passwordHash?.isNotEmpty() == true &&
            host.isAccount && guest?.isAccount != false && guests.isEmpty() && spectators.isEmpty() &&
            guest?.playerId != host.playerId

    private fun StoredActiveRoom.memberIds(): Set<UUID> =
        (setOfNotNull(host.playerId, guest?.playerId) - departedPlayerIds).map(UUID::fromString).toSet()

    private data class RoomRow(val state: OwnedRoomState, val leaseUntil: Instant)

    private companion object {
        const val MAX_EVENT_REVISIONS = 256
    }
}
