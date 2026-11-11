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
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal const val ROOM_OWNERSHIP_MAX_EVENT_REVISIONS = 256

internal fun shouldPruneRoomEvents(revision: Long): Boolean = revision > ROOM_OWNERSHIP_MAX_EVENT_REVISIONS

internal class PostgresRoomOwnershipRepository(
    private val dataSource: DataSource,
    private val metrics: RoomOwnershipMetrics? = null
) : RoomOwnershipRepository {
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
    }.also { if (it != null) metrics?.takeover() }

    suspend fun schemaReady(): Boolean = try {
        transaction { ownershipSchemaReady() }
    } catch (failure: SQLException) {
        if (failure.sqlState?.startsWith("42") == true) false else throw failure
    }

    suspend fun fixtureReady(fixtureId: UUID): Boolean = try {
        transaction {
            if (!ownershipSchemaReady()) return@transaction false
            query(
                "SELECT singleton, fixture_id FROM room_ownership_pilot_fixture WHERE FALSE"
            ) { true }
            val keyReady = query(
                """
                SELECT count(*) = 1
                FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = current_schema() AND c.contype = 'p'
                  AND t.relname = 'room_ownership_pilot_fixture'
                  AND pg_get_constraintdef(c.oid) = 'PRIMARY KEY (singleton)'
                """
            ) { rows -> rows.next() && rows.getBoolean(1) }
            keyReady && query("SELECT 1 FROM room_ownership_pilot_fixture WHERE singleton AND fixture_id = ?", fixtureId) { it.next() }
        }
    } catch (failure: SQLException) {
        if (failure.sqlState?.startsWith("42") == true) false else throw failure
    }

    private fun Connection.ownershipSchemaReady(): Boolean {
        // Resolve every runtime column without relying on a fixture table or application rows.
        query(
            """
            SELECT r.room_id, r.instance_id, r.owner_epoch, r.revision, r.lease_until,
                   r.recovery_deadline, r.closed_at, r.snapshot,
                   m.player_id, m.room_id, s.player_id, s.generation,
                   e.room_id, e.revision, e.owner_epoch, e.created_at, e.events,
                   t.match_id, t.room_id, t.completion_sha256
            FROM room_ownership_pilot_rooms r, room_ownership_pilot_memberships m,
                 room_ownership_pilot_sessions s, room_ownership_pilot_events e,
                 room_ownership_pilot_settlements t
            WHERE FALSE
            """
        ) { true }
        return query(
            """
            SELECT count(*) = 5
            FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = current_schema() AND c.contype = 'p'
              AND (t.relname, pg_get_constraintdef(c.oid)) IN (
                  ('room_ownership_pilot_rooms', 'PRIMARY KEY (room_id)'),
                  ('room_ownership_pilot_memberships', 'PRIMARY KEY (player_id)'),
                  ('room_ownership_pilot_sessions', 'PRIMARY KEY (player_id)'),
                  ('room_ownership_pilot_events', 'PRIMARY KEY (room_id, revision)'),
                  ('room_ownership_pilot_settlements', 'PRIMARY KEY (match_id)')
              )
            """
        ) { rows -> rows.next() && rows.getBoolean(1) }
    }

    /** One statement snapshot; pending work is counted even in closed rooms or without a local hint. */
    suspend fun durableSnapshot(instanceId: UUID? = null): RoomOwnershipDurableSnapshot = transaction(singleRead = true) {
        val scope = if (instanceId == null) "" else "WHERE instance_id = ?"
        val args = instanceId?.let { arrayOf<Any>(it) } ?: emptyArray()
        query(
            """
            SELECT count(*) FILTER (WHERE closed_at IS NULL AND lease_until > statement_timestamp()),
                   count(*) FILTER (WHERE closed_at IS NULL),
                   count(*) FILTER (WHERE snapshot->'pendingCompletedMatch' IS NOT NULL
                                    AND snapshot->'pendingCompletedMatch' <> 'null'::jsonb)
            FROM room_ownership_pilot_rooms $scope
            """,
            *args
        ) { rows ->
            check(rows.next())
            RoomOwnershipDurableSnapshot(rows.getInt(1), rows.getInt(2), rows.getInt(3))
        }
    }

    suspend fun ownedFences(instanceId: UUID): List<OwnerFence> = transaction(singleRead = true) {
        query(
            """
            SELECT room_id, owner_epoch FROM room_ownership_pilot_rooms
            WHERE instance_id = ? AND closed_at IS NULL AND lease_until > statement_timestamp()
            ORDER BY room_id
            """,
            instanceId
        ) { rows ->
            buildList {
                while (rows.next()) add(OwnerFence(rows.getObject(1, UUID::class.java), instanceId, rows.getLong(2)))
            }
        }
    }

    suspend fun oldestQueuedEventAgeSeconds(positions: List<RoomQueuePosition>): Double {
        if (positions.isEmpty()) return 0.0
        return transaction(singleRead = true) {
            query(
                """
                SELECT COALESCE(EXTRACT(EPOCH FROM statement_timestamp() - min(e.created_at)), 0)
                FROM jsonb_to_recordset(CAST(? AS jsonb)) AS p(
                    "roomId" uuid, "playerId" uuid, generation bigint, revision bigint, "nextEventIndex" integer
                )
                JOIN room_ownership_pilot_sessions s ON s.player_id = p."playerId" AND s.generation = p.generation
                JOIN room_ownership_pilot_events e ON e.room_id = p."roomId" AND e.revision > p.revision
                WHERE EXISTS (
                    SELECT 1 FROM jsonb_array_elements(e.events) WITH ORDINALITY AS v(item, event_index)
                    WHERE jsonb_exists(v.item->'recipientIds', p."playerId"::text)
                      AND (e.revision > p.revision + 1 OR v.event_index > p."nextEventIndex")
                )
                """,
                ProtocolJson.encodeToString(positions)
            ) { rows -> check(rows.next()); rows.getDouble(1).coerceAtLeast(0.0) }
        }
    }

    override suspend fun expiredRooms(limit: Int): List<UUID> {
        require(limit in 1..MAX_EVENT_REVISIONS)
        return transaction(singleRead = true) {
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

    override suspend fun read(roomId: UUID): OwnedRoomState? = readState(roomId, delivery = false)

    override suspend fun readForDelivery(roomId: UUID): OwnedRoomState? = readState(roomId, delivery = true)

    private suspend fun readState(roomId: UUID, delivery: Boolean): OwnedRoomState? = transaction(singleRead = true) {
        // Retry replies grow with every selection. Public snapshots and delivery
        // authorization use the room fields, while command replay uses read/load.
        val snapshotColumn = if (delivery) "r.snapshot - 'processedRequests'" else "r.snapshot"
        query(
            """
            SELECT r.room_id, r.instance_id, r.owner_epoch, r.revision, r.recovery_deadline, r.closed_at,
                   $snapshotColumn AS snapshot, clock_timestamp() AS observed_at,
                   host_session.player_id::text AS host_session_player_id, host_session.generation AS host_generation,
                   guest_session.player_id::text AS guest_session_player_id, guest_session.generation AS guest_generation
            FROM room_ownership_pilot_rooms r
            LEFT JOIN room_ownership_pilot_sessions host_session
              ON host_session.player_id = CAST(r.snapshot#>>'{host,playerId}' AS uuid)
            LEFT JOIN room_ownership_pilot_sessions guest_session
              ON guest_session.player_id = CAST(r.snapshot#>>'{guest,playerId}' AS uuid)
            WHERE r.room_id = ?
            """,
            roomId
        ) { rows ->
            if (rows.next()) rows.state().copy(sessionGenerations = buildMap {
                rows.getString("host_session_player_id")?.let { put(it, rows.getLong("host_generation")) }
                rows.getString("guest_session_player_id")?.let { put(it, rows.getLong("guest_generation")) }
            }) else null
        }
    }

    suspend fun findOwner(roomId: UUID): OwnerFence? = transaction(singleRead = true) {
        query(
            "SELECT instance_id, owner_epoch FROM room_ownership_pilot_rooms WHERE room_id = ? AND closed_at IS NULL",
            roomId
        ) { rows ->
            if (rows.next()) OwnerFence(roomId, rows.getObject("instance_id", UUID::class.java), rows.getLong("owner_epoch"))
            else null
        }
    }

    override suspend fun lookupRoom(playerId: UUID): UUID? = transaction(singleRead = true) {
        query("SELECT room_id FROM room_ownership_pilot_memberships WHERE player_id = ?", playerId) { rows ->
            if (rows.next()) rows.getObject("room_id", UUID::class.java) else null
        }
    }

    suspend fun listJoinableRooms(): List<RoomSummary> = transaction(singleRead = true) {
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

    override suspend fun isCurrentSocket(socket: SocketFence): Boolean = transaction(singleRead = true) {
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
        val room = if (socket == null) {
            load(fence.roomId, lock = true)
        } else {
            loadForSocket(fence.roomId, socket)
        } ?: return@fencedTransaction FencedResult.Stale
        val observed = observeLocked(room.state)
        if (observed.closed || observed.fence != fence || observed.revision != expectedRevision ||
            room.leaseUntil <= observed.observedAt
        ) return@fencedTransaction FencedResult.Stale
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
        val recoveredAfter = state.recoveryDeadline
            ?.takeIf { mutation.clearRecoveryDeadline && !mutation.close && snapshot.phase == RoomPhase.PLAYING }
            ?.let { state.observedAt.toEpochMilli() - it.minusSeconds(30).toEpochMilli() }
        return FencedResult.Committed(committed, mutation.reply, recoveredAfter)
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
        return transaction(singleRead = true) {
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
        if (shouldPruneRoomEvents(state.revision)) {
            update(
                """
                DELETE FROM room_ownership_pilot_events
                WHERE room_id = ? AND (revision <= ? OR created_at <= clock_timestamp() - interval '2 minutes')
                """,
                state.fence.roomId, state.revision - MAX_EVENT_REVISIONS
            )
        }
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

    private fun Connection.observeLocked(state: OwnedRoomState): OwnedRoomState {
        val host = UUID.fromString(state.snapshot.host.playerId)
        val guest = state.snapshot.guest?.playerId?.let(UUID::fromString) ?: host
        // A separate statement after acquiring the room/socket locks observes
        // current generations and time together, without locking the other session.
        return query(
            """
            SELECT clock_timestamp() AS observed_at,
                   h.generation AS host_generation, g.generation AS guest_generation
            FROM (VALUES (1)) anchor(value)
            LEFT JOIN room_ownership_pilot_sessions h ON h.player_id = ?
            LEFT JOIN room_ownership_pilot_sessions g ON g.player_id = ?
            """,
            host, guest
        ) { rows ->
            check(rows.next())
            val hostGeneration = rows.getLong("host_generation")
            val hostPresent = !rows.wasNull()
            val guestGeneration = rows.getLong("guest_generation")
            val guestPresent = !rows.wasNull()
            state.copy(observedAt = rows.getTimestamp("observed_at").toInstant(), sessionGenerations = buildMap {
                if (hostPresent) put(host.toString(), hostGeneration)
                if (guestPresent) put(guest.toString(), guestGeneration)
            })
        }
    }

    private fun Connection.load(roomId: UUID, lock: Boolean = false): RoomRow? {
        val lockingClause = if (lock) " FOR UPDATE" else ""
        return query("SELECT *, clock_timestamp() AS observed_at FROM room_ownership_pilot_rooms WHERE room_id = ?$lockingClause", roomId) { rows ->
            if (rows.next()) RoomRow(
                rows.state(), rows.getTimestamp("lease_until").toInstant()
            ) else null
        }
    }

    private fun Connection.loadForSocket(roomId: UUID, socket: SocketFence): RoomRow? = query(
        """
        SELECT r.*, clock_timestamp() AS observed_at
        FROM room_ownership_pilot_rooms r
        JOIN room_ownership_pilot_sessions s ON s.player_id = ? AND s.generation = ?
        WHERE r.room_id = ?
        FOR UPDATE OF r, s
        """.trimIndent(),
        socket.playerId, socket.generation, roomId
    ) { rows ->
        if (rows.next()) RoomRow(rows.state(), rows.getTimestamp("lease_until").toInstant()) else null
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
        transaction(commit = { it is FencedResult.Committed }, block = block).also { result ->
            if (result == FencedResult.Stale) metrics?.fenceRejected()
            if (result is FencedResult.Committed) result.recoveryDurationMillis?.let { metrics?.recoveryCompleted(it) }
        }
    } catch (_: SQLException) {
        FencedResult.TemporaryFailure
    }

    private suspend fun <T> transaction(
        commit: (T) -> Boolean = { true },
        singleRead: Boolean = false,
        block: Connection.() -> T
    ): T =
        withContext(Dispatchers.IO) {
            measured(OwnershipDatabaseStage.POOL_ACQUIRE) { dataSource.connection }.use { connection ->
                val previousNetworkTimeout = connection.networkTimeout
                connection.setNetworkTimeout(Runnable::run, 2_500)
                connection.autoCommit = false
                try {
                    val transaction = TransactionConnection(connection, singleRead)
                    val result = measured(OwnershipDatabaseStage.BODY) { transaction.block() }
                    if (singleRead) check(transaction.initialized) // Its read-only batch already committed.
                    else if (commit(result)) measured(OwnershipDatabaseStage.COMMIT) { connection.commit() }
                    else measured(OwnershipDatabaseStage.ROLLBACK) { connection.rollback() }
                    result
                } catch (error: Throwable) {
                    runCatching { measured(OwnershipDatabaseStage.ROLLBACK) { connection.rollback() } }.exceptionOrNull()?.let(error::addSuppressed)
                    throw error
                } finally {
                    if (!connection.isClosed) runCatching { connection.setNetworkTimeout(Runnable::run, previousNetworkTimeout) }
                }
            }
        }

    private class TransactionConnection(connection: Connection, val singleRead: Boolean) : Connection by connection {
        var initialized = false
    }

    private fun Connection.update(sql: String, vararg args: Any): Int = withStatement(sql, args) { statement, first, singleRead ->
        check(!singleRead)
        measured(if (first) OwnershipDatabaseStage.FIRST_STATEMENT else OwnershipDatabaseStage.EXECUTE) {
            if (first) {
                check(!statement.executeFirst(singleRead = false))
                statement.updateCount
            } else statement.executeUpdate()
        }
    }

    private fun <T> Connection.query(sql: String, vararg args: Any, read: (ResultSet) -> T): T =
        withStatement(sql, args) { statement, first, singleRead ->
            val stage = if (singleRead) OwnershipDatabaseStage.READ_TRANSACTION
                else if (first) OwnershipDatabaseStage.FIRST_STATEMENT else OwnershipDatabaseStage.EXECUTE
            measured(stage) {
                if (first) {
                    check(statement.executeFirst(singleRead))
                    checkNotNull(statement.resultSet)
                } else statement.executeQuery()
            }.use { rows ->
                measured(OwnershipDatabaseStage.DECODE) { read(rows) }
            }
        }

    private fun <T> Connection.withStatement(
        sql: String, args: Array<out Any>, operation: (PreparedStatement, Boolean, Boolean) -> T
    ): T {
        val transaction = this as TransactionConnection
        val first = !transaction.initialized
        check(!transaction.singleRead || first) { "Single-statement read cannot execute a second query" }
        transaction.initialized = true
        // pgjdbc sends the fixed setup and first parameterized statement together.
        // Only explicitly selected, single-statement reads also include COMMIT;
        // mutations retain their original commit/rollback decision after decoding/callbacks.
        val prefix = if (first) (if (transaction.singleRead) "SET TRANSACTION READ ONLY; " else "") +
            "SET LOCAL lock_timeout = '1s'; SET LOCAL statement_timeout = '2s'; " else ""
        val suffix = if (transaction.singleRead) "; COMMIT" else ""
        return prepareStatement(prefix + sql + suffix).use { statement ->
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            operation(statement, first, transaction.singleRead)
        }
    }

    private fun PreparedStatement.executeFirst(singleRead: Boolean): Boolean {
        var hasRows = execute()
        repeat(if (singleRead) 3 else 2) {
            check(!hasRows) { "Unexpected setup result set" }
            hasRows = getMoreResults(Statement.CLOSE_CURRENT_RESULT)
        }
        return hasRows
    }

    private fun <T> measured(stage: OwnershipDatabaseStage, operation: () -> T): T =
        if (metrics == null) operation() else metrics.databaseMeasured(stage, operation)

    private fun StoredActiveRoom.isPilotRoom(): Boolean =
        matchType == MatchType.CASUAL && gameMode == ProtocolGameMode.ORDER &&
            passwordSalt?.isNotEmpty() == true && passwordHash?.isNotEmpty() == true &&
            host.isAccount && guest?.isAccount != false && guests.isEmpty() && spectators.isEmpty() &&
            guest?.playerId != host.playerId

    private fun StoredActiveRoom.memberIds(): Set<UUID> =
        (setOfNotNull(host.playerId, guest?.playerId) - departedPlayerIds).map(UUID::fromString).toSet()

    private data class RoomRow(val state: OwnedRoomState, val leaseUntil: Instant)

    private companion object {
        const val MAX_EVENT_REVISIONS = ROOM_OWNERSHIP_MAX_EVENT_REVISIONS
    }
}
