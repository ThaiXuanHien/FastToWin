package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.sql.Connection
import java.util.UUID

/** Exercise the account-lane barrier against the packaged worker and disposable database. */
internal suspend fun productionLaneAcceptance(fixture: RoomAcceptanceFixture): JsonObject = coroutineScope {
    val control = requireNotNull(fixture.settings.topologyStatus)
    val policy = requireNotNull(PostgresOwnedAdmissionRepository(fixture.source).observe())
    check(policy.generation == control.generation && policy.admits(control.binding))
    val accounts = fixture.accounts()
    val cases = mutableListOf<JsonObject>()
    for (index in 0..3) {
        val create = index < 2
        val commit = index % 2 == 0
        val pair = control.targetPair(fixture.settings.accountIds, 9 + index, 1).map(accounts::get)
        val actor = pair[if (create) 0 else 1]
        val player = UUID.fromString(actor.userId)
        val original = fixture.connect(actor, 0)
        val host = if (create) null else fixture.connect(pair[0], 0)
        var replacement: AcceptanceSocket? = null
        var target: UUID? = null
        val password = "LaneFixture123!"
        try {
            val room = if (host == null) UUID.randomUUID() else {
                host.send(ClientMessage.CreateRoom("Worker waiting room", password, ProtocolGameMode.ORDER))
                UUID.fromString((host.await { it is ServerMessage.RoomCreated } as ServerMessage.RoomCreated).game.roomId)
            }
            target = room
            val generation = fixture.number("SELECT generation FROM room_ownership_pilot_sessions WHERE player_id='$player'")
            val request = if (create) OwnedCreateRequest(UUID.randomUUID(), room, SocketFence(player, generation),
                actor.userId, control.nodeId(1), policy,
                ClientMessage.CreateRoom("Held worker create", password, ProtocolGameMode.ORDER)) else null
            val state = if (create) null else fixture.snapshot(room.toString())
            val join = state?.let {
                check(fixture.ownerFor(it) == 1)
                OwnerCommandRequest(room.toString(), it.epoch, actor.userId, generation, OwnerOperation.COMMAND,
                    ClientMessage.JoinRoom(room.toString(), password), it.instanceId.toString(), policy)
            }
            val obsoleteTitle = "Old lane ${UUID.randomUUID()}"
            val next = withLaneWriteHeld(fixture, room, player, create, commit, observe = { gate, discardReply ->
                val worker = fixture.blockedLaneBackend(gate.pid(), if (create) "INSERT INTO room_ownership_pilot_rooms" else "UPDATE room_ownership_pilot_rooms")
                val connection = async { fixture.connect(actor, 0, original.ready.resumeToken) }
                try {
                    fixture.blockedLaneBackend(worker, "INSERT INTO room_ownership_pilot_sessions")
                    check(!connection.isCompleted)
                    original.send(ClientMessage.CreateRoom(obsoleteTitle, "", ProtocolGameMode.ORDER))
                    original.session.flush()
                    discardReply()
                    gate.rollback()
                    connection.await()
                } catch (failure: Throwable) {
                    connection.cancel()
                    throw failure
                }
            }, observeReply = false, send = { peer ->
                if (request != null) peer.forwardCreate(request) else peer.forward(requireNotNull(join))
            })
            replacement = next
            check(next.ready.currentGame?.roomId == room.toString().takeIf { commit })
            check(fixture.number("SELECT generation FROM room_ownership_pilot_sessions WHERE player_id='$player'") == generation + 1)
            withTimeout(5_000) {
                for (frame in original.session.incoming) {
                    if (frame is Frame.Text) {
                        check(ProtocolJson.decodeFromString<ServerMessage>(frame.readText()) !is ServerMessage.RoomCreated)
                    }
                }
                check(original.session.closeReason.await()?.message == SESSION_REPLACED_CLOSE_REASON)
            }
            check(fixture.number("SELECT count(*) FROM active_room_snapshots WHERE state_json->>'roomName'='$obsoleteTitle'") == 0L)
            val held = fixture.laneCounts(room, player)
            check(held.first() == if (commit) 1L else 0L)
            check(held[2] == if (create && !commit) 0L else 1L)
            check(held[3] >= if (commit) 1L else 0L)
            if (!create) check(fixture.snapshot(room.toString()).snapshot.guest?.playerId == actor.userId.takeIf { commit })
            val lateRoom = if (create) UUID.randomUUID() else room
            val before = fixture.laneCounts(lateRoom, player)
            control.workerPeer().use { peer ->
                if (request != null) {
                    check(peer.forwardCreate(request.copy(operationId = UUID.randomUUID(), roomId = lateRoom)) == OwnedCreateResult.AttemptRejected("STALE_SOCKET"))
                } else {
                    val late = peer.forward(requireNotNull(join))
                    check(late is OwnedRoomReply.Retryable && late.publicMessage.code == "ROOM_RECOVERING")
                }
            }
            check(fixture.laneCounts(lateRoom, player) == before)
            next.send(ClientMessage.CreateRoom("Competing legacy intent", "", ProtocolGameMode.ORDER))
            if (commit) {
                try {
                    next.await { it is ServerMessage.RoomCreated }
                    error("Legacy room opened while owned membership existed")
                } catch (failure: AcceptanceRejected) { check(failure.code == "ALREADY_IN_ROOM") }
                next.leaveRoom(room.toString())
            } else {
                val legacy = (next.await { it is ServerMessage.RoomCreated } as ServerMessage.RoomCreated).game
                check(legacy.roomName == "Competing legacy intent")
                check(fixture.laneCounts(room, player).first() == 0L)
                next.leaveRoom(legacy.roomId)
            }
            cases += buildJsonObject {
                put("operation", if (create) "create" else "join")
                put("transaction", if (commit) "commit" else "rollback")
                put("blockedTransactions", 2)
                put("replyWithheld", true)
                put("generationBefore", generation)
                put("generationAfter", generation + 1)
                put("resumedOwnedRoom", commit)
                put("legacyAdmitted", !commit)
                put("obsoleteSocketRejected", true)
                put("lateWrites", 0)
            }
        } finally {
            withContext(NonCancellable) {
                replacement?.closeGracefully()
                if (host != null && target != null) {
                    val members = fixture.number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE room_id='$target' AND player_id='${pair[0].userId}'")
                    if (members != 0L) host.leaveRoom(target.toString())
                }
                host?.closeGracefully()
                original.closeGracefully()
            }
        }
    }
    buildJsonObject { put("cases", JsonArray(cases)) }
}

internal suspend fun <T> withLaneWriteHeld(
    fixture: RoomAcceptanceFixture,
    room: UUID,
    player: UUID,
    create: Boolean,
    commit: Boolean,
    observe: suspend (Connection, () -> Unit) -> T,
    observeReply: Boolean = true,
    send: suspend (OwnerPeerTransport) -> Any?
): T = coroutineScope {
    val event = if (create) "INSERT" else "UPDATE OF snapshot"
    val condition = if (create) "NEW.room_id = '$room'::uuid"
        else "NEW.room_id = '$room'::uuid AND OLD.snapshot->'guest' IS DISTINCT FROM NEW.snapshot->'guest' AND NEW.snapshot->'guest'->>'playerId' = '$player'"
    val outcome = if (commit) "RETURN NEW;" else "RAISE EXCEPTION 'fixture operation rolled back';"
    fixture.sql("""
        CREATE FUNCTION hold_topology_lane() RETURNS trigger LANGUAGE plpgsql AS
        ${'$'}body${'$'} BEGIN IF $condition THEN
            PERFORM pg_advisory_xact_lock(892765); $outcome
        END IF; RETURN NEW; END; ${'$'}body${'$'}
    """.trimIndent())
    try {
        fixture.sql("CREATE TRIGGER hold_topology_lane BEFORE $event ON room_ownership_pilot_rooms FOR EACH ROW EXECUTE FUNCTION hold_topology_lane()")
        fixture.source.connection.use { gate ->
            gate.autoCommit = false
            gate.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(892765)") }
            val sent = async { requireNotNull(fixture.settings.topologyStatus).workerPeer().use { send(it) } }
            try {
                val observed = observe(gate) {
                    check(!observeReply && !sent.isCompleted)
                    sent.cancel()
                }
                if (observeReply) {
                    val reply = sent.await()
                    check(reply is OwnedCreateResult.Created || reply is OwnedCreateResult.Unresolved)
                } else {
                    sent.join()
                    check(sent.isCancelled)
                }
                observed
            } finally {
                gate.rollback()
                sent.cancel()
            }
        }
    } finally {
        withContext(NonCancellable) {
            fixture.sql("DROP TRIGGER IF EXISTS hold_topology_lane ON room_ownership_pilot_rooms")
            fixture.sql("DROP FUNCTION hold_topology_lane()")
        }
    }
}

internal fun Connection.pid(): Int = createStatement().use { s ->
    s.executeQuery("SELECT pg_backend_pid()").use { rows -> check(rows.next()); rows.getInt(1) }
}

internal suspend fun RoomAcceptanceFixture.blockedLaneBackend(blocker: Int, statementText: String): Int = withTimeout(1_500) {
    while (true) {
        val pid = withContext(Dispatchers.IO) {
            withFixtureDatabase { c ->
                c.prepareStatement("SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)) AND strpos(query, ?) > 0").use { s ->
                    s.setInt(1, blocker)
                    s.setString(2, statementText)
                    s.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
                }
            }
        }
        if (pid != null) return@withTimeout pid
        delay(5)
    }
    error("Unreachable")
}

private suspend fun RoomAcceptanceFixture.laneCounts(room: UUID, player: UUID): List<Long> = listOf(
    number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE player_id='$player'"),
    number("SELECT count(*) FROM room_ownership_pilot_rooms WHERE room_id='$room'"),
    number("SELECT count(*) FROM owned_room_create_operations WHERE room_id='$room'"),
    number("SELECT count(*) FROM room_ownership_pilot_events WHERE room_id='$room'")
)