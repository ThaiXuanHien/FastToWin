package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable
private data class DrainProcess(val nodeId: String, val processId: String, val generation: Long)

@Serializable
private data class DrainProof(
    val serverJarSha256: String,
    val classpathSha256: String,
    val candidateSha256: String,
    val topologySha256: String,
    val mode: OwnedProofMode,
    val acknowledgedPolicy: OwnedAdmissionPolicy,
    val disabledPolicy: OwnedAdmissionPolicy,
    val nodes: List<DrainProcess>,
    val openRooms: Int,
    val pendingSettlements: Int,
    val liveLeases: Int
)

internal suspend fun productionDrainAcceptance(fixture: RoomAcceptanceFixture): JsonObject {
    val control = requireNotNull(fixture.settings.topologyStatus)
    val identity = control.candidate()
    val authority = PostgresOwnedAdmissionRepository(fixture.source)
    val rooms = PostgresRoomOwnershipRepository(fixture.source)
    val active = requireNotNull(authority.observe())
    check(active.generation == control.generation && active.admits(control.binding))
    val accounts = fixture.accounts()
    val playing = fixture.createMatch(5, targetOwner = 1)
    val waiting = fixture.createWaitingMatch(6, targetOwner = 1)
    val idleAccount = accounts[control.targetPair(fixture.settings.accountIds, 8, 1).first()]
    val idle = fixture.connect(idleAccount, 0)
    val joinHost = fixture.connect(accounts[control.targetPair(fixture.settings.accountIds, 14, 1).first()], 0)
    val legacy = fixture.connect(accounts[16], 0)
    val legacyGuest = fixture.connect(accounts[17], 0)
    try {
        legacy.send(ClientMessage.CreateRoom("Legacy drain check", "", ProtocolGameMode.ORDER))
        val legacyRoom = (legacy.await { it is ServerMessage.RoomCreated } as ServerMessage.RoomCreated).game.roomId
        legacyGuest.send(ClientMessage.JoinRoom(legacyRoom, ""))
        for (socket in listOf(legacy, legacyGuest)) socket.await { it is ServerMessage.RoomUpdated && it.game.players.size == 2 }
        legacy.send(ClientMessage.SetReady(legacyRoom, true))
        for (socket in listOf(legacy, legacyGuest)) socket.await { it is ServerMessage.RoomUpdated }
        legacyGuest.send(ClientMessage.SetReady(legacyRoom, true))
        val start = legacy.await { it is ServerMessage.GameStarted } as ServerMessage.GameStarted
        legacyGuest.await { it is ServerMessage.GameStarted && it.game.matchId == start.game.matchId }
        var legacyTarget = start.game.currentTarget
        suspend fun checkLegacy() {
            val number = legacyTarget
            legacy.send(ClientMessage.SelectNumber(legacyRoom, number, UUID.randomUUID().toString()))
            val update = legacy.await { it is ServerMessage.GameStateUpdated && it.acceptedNumber == number } as ServerMessage.GameStateUpdated
            check(update.game.matchId == start.game.matchId)
            legacyTarget = update.game.currentTarget
            legacyGuest.await { it is ServerMessage.GameStateUpdated && it.acceptedNumber == number }
        }
        val joinPassword = "DrainJoin123!"
        joinHost.send(ClientMessage.CreateRoom("Waiting for late join", joinPassword, ProtocolGameMode.ORDER))
        val joinRoom = (joinHost.await { it is ServerMessage.RoomCreated } as ServerMessage.RoomCreated).game.roomId
        val waitingState = fixture.snapshot(waiting.roomId)
        val joinState = fixture.snapshot(joinRoom)
        check(fixture.ownerFor(waitingState) == 1 && fixture.ownerFor(joinState) == 1)
        suspend fun fence(user: String): SocketFence {
            val player = UUID.fromString(user)
            return SocketFence(player, fixture.number("SELECT generation FROM room_ownership_pilot_sessions WHERE player_id='$player'"))
        }
        val idleFence = fence(idleAccount.userId)
        val readyFence = fence(waiting.playerId(1))
        val lateCreate = OwnedCreateRequest(UUID.randomUUID(), UUID.randomUUID(), idleFence,
            idleAccount.userId, control.nodeId(1), active,
            ClientMessage.CreateRoom("Refused old admission", "DrainFixture123!", ProtocolGameMode.ORDER))
        val lateJoin = OwnerCommandRequest(joinRoom, joinState.epoch, idleAccount.userId, idleFence.generation,
            OwnerOperation.COMMAND, ClientMessage.JoinRoom(joinRoom, joinPassword), joinState.instanceId.toString(), active)
        val lateStart = OwnerCommandRequest(waiting.roomId, waitingState.epoch, readyFence.playerId.toString(), readyFence.generation,
            OwnerOperation.COMMAND, ClientMessage.SetReady(waiting.roomId, true), waitingState.instanceId.toString(), active)
        val racingAccount = accounts[control.targetPair(fixture.settings.accountIds, 7, 1).first()]
        val racingPlayer = UUID.fromString(racingAccount.userId)
        val racingSocket = fixture.connect(racingAccount, 0)
        val racingRoom = UUID.randomUUID()
        val oldGeneration = fixture.number("SELECT generation FROM room_ownership_pilot_sessions WHERE player_id='$racingPlayer'")
        val prepared = OwnedCreateRequest(UUID.randomUUID(), racingRoom, SocketFence(racingPlayer, oldGeneration),
            racingAccount.userId, control.nodeId(1), active,
            ClientMessage.CreateRoom("Admission before stop", "DrainFixture123!", ProtocolGameMode.ORDER))
        val concurrentSelection = UUID.randomUUID().toString()
        val stoppedReply = try {
            coroutineScope {
                withLaneWriteHeld(fixture, racingRoom, racingPlayer, create = true, commit = true,
                    observe = { gate, _ ->
                        val worker = fixture.blockedLaneBackend(gate.pid(), "INSERT INTO room_ownership_pilot_rooms")
                        val stop = async { control.mutate("stop_new", active) }
                        fixture.blockedLaneBackend(worker, "FROM owned_admission_policy WHERE singleton FOR UPDATE")
                        check(!stop.isCompleted)
                        val play = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { playing.select(playing.nextNumber, concurrentSelection) }
                        gate.rollback()
                        stop.await().also { play.await() }
                    },
                    send = { it.forwardCreate(prepared) })
            }
        } finally {
            racingSocket.closeGracefully()
        }
        val stopped = requireNotNull(stoppedReply.policy)
        check(stoppedReply.outcome == "ok" && stopped.state == OwnedAdmissionState.STOP_NEW &&
            stopped.generation > active.generation && stopped == authority.observe())
        check(fixture.number("SELECT count(*) FROM owned_room_create_operations WHERE room_id='$racingRoom'") == 1L)
        check(fixture.snapshot(playing.roomId).snapshot.selectionEvents.count { it.requestId == concurrentSelection } == 1)
        val affectedRooms = listOf(lateCreate.roomId, UUID.fromString(joinRoom), UUID.fromString(waiting.roomId))
            .joinToString(",") { "'$it'" }
        val affectedMatches = listOf(UUID.fromString(joinState.snapshot.matchId), UUID.fromString(waiting.matchId))
            .joinToString(",") { "'$it'" }
        suspend fun admissionCounts() = listOf(
            fixture.number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE room_id IN ($affectedRooms)"),
            fixture.number("SELECT count(*) FROM owned_room_create_operations WHERE room_id IN ($affectedRooms)"),
            fixture.number("SELECT count(*) FROM room_ownership_pilot_events WHERE room_id IN ($affectedRooms)"),
            fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE match_id IN ($affectedMatches)")
        )
        val beforeLate = admissionCounts()
        control.workerPeer().use { peer ->
            check(peer.forwardCreate(lateCreate) == OwnedCreateResult.AttemptRejected("OWNED_ADMISSION_STOPPED"))
            for (request in listOf(lateJoin, lateStart)) {
                val result = peer.forward(request)
                val reason = when (result) {
                    is OwnedRoomReply.Rejected -> result.publicMessage.code
                    is OwnedRoomReply.Retryable -> result.publicMessage.code
                    else -> null
                }
                check(reason == "OWNED_ADMISSION_STOPPED" || reason == "MAINTENANCE")
            }
        }
        check(admissionCounts() == beforeLate)
        check(rooms.read(lateCreate.roomId) == null)
        check(fixture.snapshot(joinRoom).revision == joinState.revision)
        check(fixture.snapshot(waiting.roomId).revision == waitingState.revision)
        val initialA = control.observed(0, stopped)
        val initialB = control.observed(1, stopped)
        check(initialA.accepting == true && initialB.accepting == true)
        idle.send(ClientMessage.CreateRoom("Refused during drain", "DrainFixture123!", ProtocolGameMode.ORDER))
        try {
            idle.await { it is ServerMessage.RoomCreated }
            error("STOP_NEW accepted an owned room")
        } catch (failure: AcceptanceRejected) {
            check(failure.code == "MAINTENANCE")
        }
        val request = UUID.randomUUID().toString()
        playing.select(playing.nextNumber, request)
        check(fixture.snapshot(playing.roomId).snapshot.selectionEvents.count { it.requestId == request } == 1)
        fun zero(open: Int, pending: Int, leases: Int) = RoomOwnershipDurableSnapshot(leases, open, pending)
        suspend fun pendingStep() {
            val step = control.mutate("step", stopped)
            check(step.outcome == "pending" && step.policy == stopped && step.proof == null && authority.observe() == stopped)
        }
        pendingStep()
        withTimeout(15_000) {
            while (rooms.read(UUID.fromString(waiting.roomId))?.closed != true ||
                rooms.read(racingRoom)?.closed != true || rooms.read(UUID.fromString(joinRoom))?.closed != true) delay(50)
        }
        checkLegacy()

        val roomId = UUID.fromString(playing.roomId)
        val matchId = UUID.fromString(playing.matchId)
        fixture.sql("""
            CREATE FUNCTION hold_topology_settlement() RETURNS trigger LANGUAGE plpgsql AS
            'BEGIN IF NEW.room_id = ''$roomId''::uuid THEN RAISE EXCEPTION ''fixture settlement held''; END IF; RETURN NEW; END;'
        """.trimIndent())
        try {
            fixture.sql("""
                CREATE TRIGGER hold_topology_settlement BEFORE INSERT ON room_ownership_pilot_settlements
                FOR EACH ROW EXECUTE FUNCTION hold_topology_settlement()
            """.trimIndent())
            playing.socket(0).send(ClientMessage.LeaveRoom(playing.roomId))
            for (index in 0..1) playing.socket(index).await {
                it is ServerMessage.GameFinished && it.game.matchId == playing.matchId
            }
            for (index in 0..1) playing.socket(index).leaveRoom(playing.roomId)
            val held = requireNotNull(rooms.read(roomId))
            check(!held.closed && held.snapshot.pendingCompletedMatch?.matchId == matchId.toString() &&
                held.snapshot.activePlayers().isEmpty())
            val pendingOpen = rooms.durableSnapshot()
            check(pendingOpen.remainingOpenRooms == 1 && pendingOpen.pendingSettlements == 1)
            pendingStep()

            withClosedPendingRoom(fixture, roomId) {
                check(rooms.durableSnapshot() == zero(open = 0, pending = 1, leases = 0))
                pendingStep()
                val deadline = awaitClusterDrain(UUID.randomUUID(), stopped.generation, 1_500,
                    control.nodeId(0), identity, control.binding) { control.send(0, it) }
                check(deadline.outcome == "deadline" && deadline.completion == null && authority.observe() == stopped)
                checkLegacy()
            }
        } finally {
            withContext(NonCancellable) {
                fixture.sql("DROP TRIGGER IF EXISTS hold_topology_settlement ON room_ownership_pilot_settlements")
                fixture.sql("DROP FUNCTION hold_topology_settlement()")
            }
        }
        withTimeout(20_000) {
            while (rooms.durableSnapshot() != zero(open = 0, pending = 0, leases = 0)) delay(50)
        }
        check(fixture.number("SELECT count(*) FROM room_ownership_pilot_settlements WHERE match_id='$matchId'") == 1L)

        fixture.faults.backend(1, BackendAction.KILL)
        try {
            val unavailable = control.mutate("step", stopped)
            check(unavailable.outcome == "unavailable" && unavailable.proof == null && authority.observe() == stopped)
            checkLegacy()
        } finally {
            withContext(NonCancellable) { fixture.faults.backend(1, BackendAction.START) }
        }
        withTimeout(30_000) {
            while (runCatching { fixture.client.get(fixture.settings.urls[1] + "/health").status }.getOrNull() != HttpStatusCode.OK) delay(100)
        }
        val restarted = control.observed(1, stopped)
        check(restarted.processId != initialB.processId)
        check(fixture.client.get(fixture.settings.urls[1] + "/ready").status == HttpStatusCode.ServiceUnavailable)
        checkLegacy()

        val completed = awaitClusterDrain(UUID.randomUUID(), stopped.generation, 25_000,
            control.nodeId(0), identity, control.binding) { control.send(0, it) }
        check(completed.outcome == "completed")
        val response = requireNotNull(completed.completion)
        val proof = Json.decodeFromString<DrainProof>(requireNotNull(response.proof))
        val acknowledged = proof.acknowledgedPolicy
        val disabled = proof.disabledPolicy
        check(proof.mode == OwnedProofMode.INITIAL_DRAIN && acknowledged.state == OwnedAdmissionState.QUIESCING &&
            acknowledged.generation > stopped.generation && disabled.state == OwnedAdmissionState.DISABLED &&
            disabled.generation > acknowledged.generation && disabled == response.policy && disabled == authority.observe())
        check(proof.serverJarSha256 == identity.serverJarSha256 && proof.classpathSha256 == identity.classpathSha256 &&
            proof.candidateSha256 == identity.candidateSha256 && proof.topologySha256 == control.binding.topologySha256 &&
            acknowledged.topologySha256 == disabled.topologySha256 && acknowledged.policySha256 == disabled.policySha256)
        val statuses = (0..1).map { control.observed(it, disabled, acknowledged.generation) }
        check(statuses.map { it.processId } == listOf(initialA.processId, restarted.processId))
        check(statuses.all { it.quiesced == true && it.accepting == false &&
            it.openRooms == 0 && it.liveLeases == 0 && it.pendingSettlements == 0 })
        check(proof.nodes.toSet() == statuses.map {
            DrainProcess(requireNotNull(it.nodeId), requireNotNull(it.processId), it.generation)
        }.toSet())
        val final = rooms.durableSnapshot()
        check(final == zero(open = proof.openRooms, pending = proof.pendingSettlements, leases = proof.liveLeases) &&
            final == zero(open = 0, pending = 0, leases = 0))
        fixture.faults.verifyHealthy()
        return buildJsonObject {
            put("active", active.generation)
            put("stopped", stopped.generation)
            put("acknowledged", acknowledged.generation)
            put("disabled", disabled.generation)
            put("playingDuringStop", 1)
            put("waitingClosed", 3)
            put("concurrentSelections", 1)
            put("staleAdmissionsRejected", 3)
            put("stopWaitedForAdmission", true)
            put("lateAdmissions", 0)
            put("pendingOpen", 1)
            put("pendingClosed", 1)
            put("closedOpenRooms", 0)
            put("closedLiveLeases", 0)
            put("settled", 1)
            put("restartChanged", true)
            put("dependency", "unavailable")
            put("timedAwait", "deadline")
            put("legacySelections", 4)
            put("mode", proof.mode.name)
            put("candidate", proof.candidateSha256)
            put("serverJar", proof.serverJarSha256)
            put("classpath", proof.classpathSha256)
            put("topology", proof.topologySha256)
            put("policy", requireNotNull(disabled.policySha256))
            put("nodes", JsonArray(statuses.map { status ->
                buildJsonObject {
                    put("node", requireNotNull(status.nodeId).replace("-", ""))
                    put("process", requireNotNull(status.processId).replace("-", ""))
                    put("generation", status.generation)
                    put("quiesced", status.quiesced == true)
                }
            }))
            put("openRooms", final.remainingOpenRooms)
            put("liveLeases", final.remainingOwnedLeases)
            put("pendingSettlements", final.pendingSettlements)
        }
    } finally {
        withContext(NonCancellable) {
            for (index in 0..1) {
                playing.socket(index).closeGracefully()
                waiting.socket(index).closeGracefully()
            }
            joinHost.closeGracefully()
            idle.closeGracefully()
            legacyGuest.closeGracefully()
            legacy.closeGracefully()
        }
    }
}

/** Preserve the actual pending payload while simulating a closed row in the disposable fixture. */
private suspend fun withClosedPendingRoom(fixture: RoomAcceptanceFixture, room: UUID, block: suspend () -> Unit) {
    val marker = withContext(Dispatchers.IO) {
        fixture.withFixtureDatabase { connection ->
            connection.prepareStatement("""
                UPDATE room_ownership_pilot_rooms SET closed_at=clock_timestamp()
                WHERE room_id=? AND closed_at IS NULL
                  AND jsonb_typeof(snapshot->'pendingCompletedMatch')='object'
                RETURNING closed_at, revision, (snapshot->'pendingCompletedMatch')::text
            """.trimIndent()).use { statement ->
                statement.setObject(1, room)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    Triple(rows.getTimestamp(1), rows.getLong(2), rows.getString(3)).also { check(!rows.next()) }
                }
            }
        }
    }
    try {
        block()
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            fixture.withFixtureDatabase { connection ->
                connection.prepareStatement("""
                    UPDATE room_ownership_pilot_rooms SET closed_at=NULL
                    WHERE room_id=? AND closed_at=? AND revision=?
                      AND snapshot->'pendingCompletedMatch'=CAST(? AS jsonb)
                """.trimIndent()).use { statement ->
                    statement.setObject(1, room)
                    statement.setTimestamp(2, marker.first)
                    statement.setLong(3, marker.second)
                    statement.setString(4, marker.third)
                    check(statement.executeUpdate() == 1)
                }
            }
        }
    }
}