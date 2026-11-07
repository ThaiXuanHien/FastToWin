package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.SelectionResult
import com.hienthai.fastowin.server.PostgresMatchResultRepository
import com.hienthai.fastowin.server.PostgresPlayQuotaRepository
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
internal data class RoomOwnershipFaultSuiteReceipt(
    val schemaVersion: Int = 1,
    val requiredCases: Int = 8,
    val accepted: Boolean,
    val cases: List<RoomOwnershipFaultCaseReceipt>
)

@Serializable
internal data class RoomOwnershipFaultCaseReceipt(
    val name: String,
    val passed: Boolean = true,
    val roomId: String? = null,
    val matchId: String? = null,
    val beforeEpoch: Long? = null,
    val afterEpoch: Long? = null,
    val beforeRevision: Long? = null,
    val afterRevision: Long? = null,
    val recoveryMillis: Long? = null,
    val evidence: Map<String, String> = emptyMap()
)

/** Real-process faults only. A failed or unexercised case fails the suite and leaves a partial receipt. */
internal suspend fun runRoomOwnershipFaults(fixture: RoomAcceptanceFixture): RoomOwnershipFaultSuiteReceipt {
    check(fixture.backendCount == 2) { "The fault suite requires two real backends" }
    val results = mutableListOf<RoomOwnershipFaultCaseReceipt>()
    suspend fun persist() {
        val receipt = RoomOwnershipFaultSuiteReceipt(accepted = results.size == 8 && results.all { it.passed }, cases = results.toList())
        Files.writeString(fixture.settings.receipts.resolve("faults.json"), ProtocolJson.encodeToString(receipt))
    }
    suspend fun case(name: String, action: suspend () -> RoomOwnershipFaultCaseReceipt) {
        try {
            fixture.requireBothReady()
            fixture.resetRooms()
            val result = action()
            check(result.name == name && result.passed)
            results += result
            persist()
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            // Never serialize SQL/HTTP exception text, environment values, tokens, or passwords.
            results += RoomOwnershipFaultCaseReceipt(name, passed = false,
                evidence = mapOf("failureType" to failure.javaClass.simpleName))
            persist()
            throw IllegalStateException("Required room ownership fault failed: $name", failure)
        }
    }

    case("kill-after-committed-selection") {
        val match = fixture.createMatch(0)
        try {
            val request = requestId()
            val number = match.nextNumber
            match.select(number, request)
            val before = requireSelection(fixture, match, request, number)
            val elapsed = fixture.faultOwner(match)
            val after = fixture.snapshot(match.roomId)
            requireSameBoard(before, after)
            check(after.epoch > before.epoch && elapsed <= 25_000)
            requireSelection(fixture, match, request, number)
            match.verifyEconomy()
            receipt("kill-after-committed-selection", match, before, after, elapsed,
                "acknowledgedSelectionPreserved" to "true", "quotaDebits" to "2")
        } finally { match.close() }
    }

    case("selective-owner-postgres-partition") {
        val match = fixture.createMatch(0)
        val oldSockets = listOf(match.socket(0), match.socket(1))
        val before = fixture.snapshot(match.roomId)
        val oldOwner = fixture.ownerFor(before)
        val survivor = 1 - oldOwner
        val blockedRequest = requestId()
        val staleRequest = requestId()
        val number = match.nextNumber
        var proxyDisabled = false
        try {
            fixture.proxy(oldOwner, blocked = true)
            proxyDisabled = true
            fixture.intentionallyUnavailableBackend = oldOwner
            match.sendWithoutReading(ClientMessage.SelectNumber(match.roomId, number, blockedRequest))
            noAcceptedSelection(oldSockets[0], number, 2_000)
            check(fixture.snapshot(match.roomId).snapshot.selectionEvents.none { it.requestId == blockedRequest })
            val started = System.nanoTime()
            match.reconnect(preserveOldSockets = true)
            val after = fixture.awaitStored(match.roomId) { it.epoch > before.epoch }
            val recoveryMillis = elapsedMillis(started)
            check(recoveryMillis <= 25_000 && after.instanceId != before.instanceId)
            requireSameBoard(before, after)
            match.ownerBackend = survivor
            fixture.proxy(oldOwner, blocked = false)
            proxyDisabled = false
            fixture.awaitReady(oldOwner)
            fixture.intentionallyUnavailableBackend = null
            val staleTransport = try {
                oldSockets[0].send(ClientMessage.SelectNumber(match.roomId, number, staleRequest))
                "sent-on-superseded-socket"
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                "superseded-transport-closed"
            }
            noAcceptedSelection(oldSockets[0], number, 1_000)
            val restored = fixture.snapshot(match.roomId)
            check(restored.epoch == after.epoch && restored.instanceId == after.instanceId)
            check(restored.snapshot.selectionEvents.none { it.requestId in setOf(blockedRequest, staleRequest) })
            // Exercise the actual PostgreSQL fences with the former owner's captured identity.
            // These calls originate in the controller, independently of the superseded socket above.
            val repository = PostgresRoomOwnershipRepository(fixture.source)
            val oldFence = OwnerFence(UUID.fromString(match.roomId), before.instanceId, before.epoch)
            var staleCallbackEntered = false
            val staleWrite = repository.withFencedRoom(oldFence, restored.revision, socket = null) { _, locked ->
                staleCallbackEntered = true
                RoomMutation.Change(locked.snapshot.copy(currentTarget = 999), close = true)
            }
            check(staleWrite == FencedResult.Stale && !staleCallbackEntered)
            check(!repository.renew(oldFence) && !repository.release(oldFence))
            val economy = OwnedMatchEconomy(repository, PostgresPlayQuotaRepository(fixture.source),
                PostgresMatchResultRepository(fixture.source))
            check(economy.settle(oldFence, restored.revision) == FencedResult.Stale)
            val afterStaleAttempts = fixture.snapshot(match.roomId)
            check(afterStaleAttempts.epoch == restored.epoch && afterStaleAttempts.instanceId == restored.instanceId)
            requireSameBoard(restored, afterStaleAttempts)
            check(fixture.number("SELECT count(*) FROM room_ownership_pilot_rooms WHERE room_id='${uuid(match.roomId)}' AND closed_at IS NOT NULL") == 0L)
            check(fixture.number("SELECT count(*) FROM room_ownership_pilot_settlements WHERE match_id='${uuid(match.matchId)}'") == 0L)
            check(fixture.number("SELECT count(*) FROM matches WHERE id='${uuid(match.matchId)}'") == 0L)
            check(fixture.number("SELECT count(*) FROM wallet_transactions WHERE source_type='MATCH' AND source_id='${uuid(match.matchId)}'") == 0L)
            val retry = requestId()
            match.select(number, retry)
            val advanced = requireSelection(fixture, match, retry, number)
            check(advanced.epoch == after.epoch)
            val invalidEpochs = fixture.number("""
                SELECT count(*) FROM room_ownership_pilot_events
                WHERE room_id='${uuid(match.roomId)}' AND revision>${after.revision} AND owner_epoch<${after.epoch}
            """.trimIndent())
            check(invalidEpochs == 0L)
            match.verifyEconomy()
            receipt("selective-owner-postgres-partition", match, before, advanced, recoveryMillis,
                "postgresFaultScope" to "owner proxy only; controller and peer remain connected",
                "supersededSocketAttempt" to staleTransport,
                "staleFenceProofOrigin" to "controller invoked production PostgreSQL repository with captured old instance and epoch",
                "staleMutationResult" to "Stale; mutation callback not entered",
                "staleRenewAndRelease" to "false", "staleSettlementResult" to "Stale; no settlement or wallet effects",
                "staleRequestPersisted" to "false", "postTakeoverEpochRegressions" to "0")
        } finally {
            if (proxyDisabled) fixture.proxy(oldOwner, blocked = false)
            fixture.intentionallyUnavailableBackend = null
            oldSockets.forEach { it.close() }
            fixture.awaitReady(oldOwner)
            match.close()
        }
    }

    case("kill-before-selection-commit") {
        val match = fixture.createMatch(0)
        val before = fixture.snapshot(match.roomId)
        val owner = fixture.ownerFor(before)
        val request = requestId()
        val number = match.nextNumber
        var blocker: Connection? = null
        var ownerKilled = false
        try {
            val lockedConnection = withContext(Dispatchers.IO) {
                fixture.source.connection.apply {
                    autoCommit = false
                    prepareStatement("SELECT room_id FROM room_ownership_pilot_rooms WHERE room_id=? FOR UPDATE").use {
                        it.setObject(1, UUID.fromString(match.roomId))
                        it.executeQuery().use { rows -> check(rows.next()) }
                    }
                }
            }
            blocker = lockedConnection
            val blockerPid = withContext(Dispatchers.IO) {
                lockedConnection.createStatement().use { it.executeQuery("SELECT pg_backend_pid()").use { rows -> rows.next(); rows.getInt(1) } }
            }
            match.sendWithoutReading(ClientMessage.SelectNumber(match.roomId, number, request))
            val blockedSessions = withTimeout(4_000) {
                var count: Long
                do {
                    count = fixture.number("SELECT count(*) FROM pg_stat_activity WHERE $blockerPid = ANY(pg_blocking_pids(pid))")
                    if (count == 0L) delay(25)
                } while (count == 0L)
                count
            }
            noAcceptedSelection(match.socket(0), number, 500)
            val locked = fixture.snapshot(match.roomId)
            check(locked.snapshot.selectionEvents.none { it.requestId == request })
            requireSameBoard(before, locked)
            fixture.intentionallyUnavailableBackend = owner
            val started = System.nanoTime()
            fixture.docker("kill", nodeName(owner))
            ownerKilled = true
            withContext(Dispatchers.IO) { lockedConnection.rollback(); lockedConnection.close() }
            blocker = null
            match.reconnect()
            match.ownerBackend = 1 - owner
            val recovered = fixture.awaitStored(match.roomId) { it.epoch > before.epoch }
            val recoveryMillis = elapsedMillis(started)
            check(recoveryMillis <= 25_000)
            requireSameBoard(before, recovered)
            check(recovered.snapshot.selectionEvents.none { it.requestId == request })
            match.select(number, request)
            val retried = requireSelection(fixture, match, request, number)
            match.verifyEconomy()
            receipt("kill-before-selection-commit", match, before, retried, recoveryMillis,
                "controllerRowLockHeld" to "true", "databaseBlockedSessions" to blockedSessions.toString(),
                "ackWhileLocked" to "false", "requestAbsentAfterKill" to "true", "requestCountAfterRetry" to "1")
        } finally {
            withContext(Dispatchers.IO) { blocker?.let { it.rollback(); it.close() } }
            if (ownerKilled) { fixture.docker("start", nodeName(owner)); fixture.awaitReady(owner) }
            fixture.intentionallyUnavailableBackend = null
            match.close()
        }
    }

    case("kill-after-commit-before-reading-ack") {
        val match = fixture.createMatch(0)
        try {
            val request = requestId()
            val number = match.nextNumber
            match.sendWithoutReading(ClientMessage.SelectNumber(match.roomId, number, request))
            val before = requireSelection(fixture, match, request, number)
            val elapsed = fixture.faultOwner(match)
            // Retry exactly the uncertain request, not a new command ID. The peer
            // stream is already proven by the durable snapshot in this cut point.
            match.select(number, request, verifyPeer = false)
            val after = requireSelection(fixture, match, request, number)
            requireSameBoard(before, after)
            match.verifyEconomy()
            receipt("kill-after-commit-before-reading-ack", match, before, after, elapsed,
                "callerReadBeforeKill" to "false", "durableCommitObservedBeforeKill" to "true", "requestCountAfterRetry" to "1")
        } finally { match.close() }
    }

    case("start-response-loss") {
        val match = fixture.createWaitingMatch(0)
        try {
            val waiting = fixture.snapshot(match.roomId)
            check(waiting.snapshot.phase == RoomPhase.WAITING)
            check(fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE match_id='${uuid(waiting.snapshot.matchId)}'") == 0L)
            match.sendWithoutReading(ClientMessage.SetReady(match.roomId, true), playerIndex = 1)
            val started = fixture.awaitStored(match.roomId) { it.snapshot.phase == RoomPhase.PLAYING }
            val elapsed = fixture.faultOwner(match)
            // SessionReady reconciles the durable start; no client consumed GameStarted before the kill.
            val after = fixture.snapshot(match.roomId)
            check(match.lastSnapshot.phase == RoomPhase.PLAYING)
            requireSameBoard(started, after)
            check(started.snapshot.matchId == match.matchId)
            match.sendWithoutReading(ClientMessage.SetReady(match.roomId, true), playerIndex = 1)
            match.socket(1).await { it is ServerMessage.GameStateUpdated && it.game.phase == RoomPhase.PLAYING }
            val retried = fixture.snapshot(match.roomId)
            requireSameBoard(started, retried)
            match.verifyEconomy()
            receipt("start-response-loss", match, started, retried, elapsed,
                "startReplyReadBeforeKill" to "false", "sameDurableMatchAndBoard" to "true", "quotaDebitsAfterReadyRetry" to "2")
        } finally { match.close() }
    }

    case("settlement-response-loss") {
        val match = fixture.createMatch(0)
        try {
            while (match.nextNumber < GAME_NUMBER_COUNT) {
                match.select(match.nextNumber, requestId())
                delay(60) // Stay within the actual per-player cap while preparing the final command.
            }
            val number = match.nextNumber
            check(number == GAME_NUMBER_COUNT)
            val request = requestId()
            match.sendWithoutReading(ClientMessage.SelectNumber(match.roomId, number, request))
            val before = fixture.awaitStored(match.roomId) {
                it.snapshot.phase == RoomPhase.FINISHED && it.snapshot.pendingCompletedMatch == null &&
                    it.snapshot.selectionEvents.any { event -> event.requestId == request }
            }
            requireSettlement(fixture, before.snapshot.matchId)
            val elapsed = fixture.faultOwner(match)
            check(match.lastSnapshot.phase == RoomPhase.FINISHED)
            match.sendWithoutReading(ClientMessage.SelectNumber(match.roomId, number, request))
            // A replay can return the cached selection update even though the match is now finished.
            match.socket(0).await {
                it is ServerMessage.GameStateUpdated && it.acceptedNumber == number && it.game.phase == RoomPhase.FINISHED
            }
            val after = requireSelection(fixture, match, request, number)
            requireSameBoard(before, after)
            check(after.snapshot.finishedAtEpochMillis == before.snapshot.finishedAtEpochMillis)
            requireSettlement(fixture, match.matchId)
            match.verifyEconomy()
            receipt("settlement-response-loss", match, before, after, elapsed,
                "finishReplyReadBeforeKill" to "false", "selectionCount" to GAME_NUMBER_COUNT.toString(),
                "quotaDebits" to "2", "matchRows" to "1", "settlementRows" to "1", "walletRows" to "2")
        } finally { match.close() }
    }

    case("redis-stop-fails-closed") {
        val match = fixture.createMatch(0)
        val before = fixture.snapshot(match.roomId)
        val idleAccount = fixture.accounts()[2]
        val idleId = uuid(idleAccount.userId)
        val roomCount = fixture.number("SELECT count(*) FROM room_ownership_pilot_rooms")
        val idleMemberships = fixture.number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE player_id='$idleId'")
        val idleSessions = fixture.number("SELECT count(*) FROM room_ownership_pilot_sessions WHERE player_id='$idleId'")
        val idleQuotaRows = fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE user_id='$idleId'")
        check(idleMemberships == 0L && idleSessions == 0L && idleQuotaRows == 0L)
        val request = requestId()
        val number = match.nextNumber
        var redisStopped = false
        var recoveredSocket: AcceptanceSocket? = null
        try {
            fixture.docker("stop", "redis")
            redisStopped = true
            val rejection = try {
                fixture.connect(idleAccount, preferred = 0).also { it.close() }
                error("A new connection was admitted while its Redis-backed limiter was unavailable")
            } catch (failure: AcceptanceRejected) { failure.code }
            check(rejection == "RATE_LIMIT_UNAVAILABLE")
            check(fixture.number("SELECT count(*) FROM room_ownership_pilot_rooms") == roomCount)
            check(fixture.number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE player_id='$idleId'") == idleMemberships)
            check(fixture.number("SELECT count(*) FROM room_ownership_pilot_sessions WHERE player_id='$idleId'") == idleSessions)
            check(fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE user_id='$idleId'") == idleQuotaRows)
            // Existing match message/select buckets deliberately use RAM, so this is allowed.
            match.select(number, request)
            requireSelection(fixture, match, request, number)
            match.verifyEconomy()
            fixture.docker("start", "redis")
            redisStopped = false
            fixture.awaitReady(0); fixture.awaitReady(1)
            val recovered = connectAfterRedisRecovery(fixture, idleAccount)
            recoveredSocket = recovered
            check(recovered.ready.currentGame == null)
            check(fixture.number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE player_id='$idleId'") == 0L)
            check(fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE user_id='$idleId'") == 0L)
            val after = requireSelection(fixture, match, request, number)
            match.verifyEconomy()
            receipt("redis-stop-fails-closed", match, before, after, null,
                "protectedScope" to "new websocket connection; Redis-backed websocket-connect bucket",
                "outageError" to "RATE_LIMIT_UNAVAILABLE", "newConnectionAdmittedDuringOutage" to "false",
                "newRoomMembershipOrQuotaFromRejectedConnection" to "false",
                "idleAccountConnectedAfterRecovery" to "true",
                "existingMatchSelectionDuringOutage" to "accepted once; message and select buckets intentionally use RAM",
                "existingMatchQuotaDebits" to "2")
        } finally {
            recoveredSocket?.close()
            if (redisStopped) fixture.docker("start", "redis")
            fixture.awaitReady(0); fixture.awaitReady(1)
            match.close()
        }
    }

    case("postgres-stop-no-optimistic-ack") {
        val match = fixture.createMatch(0)
        val before = fixture.snapshot(match.roomId)
        val request = requestId()
        val number = match.nextNumber
        var postgresStopped = false
        try {
            fixture.docker("stop", "postgres")
            postgresStopped = true
            match.sendWithoutReading(ClientMessage.SelectNumber(match.roomId, number, request))
            val observationStarted = System.nanoTime()
            val observation = noAcceptedSelection(match.socket(0), number, 25_000, request)
            val observationMillis = elapsedMillis(observationStarted)
            check(observation.endedBy != "deadline") { "The outage request was still pending when observation ended" }
            fixture.docker("start", "postgres")
            postgresStopped = false
            fixture.awaitReady(0); fixture.awaitReady(1)
            val restored = fixture.snapshot(match.roomId)
            requireSameBoard(before, restored)
            check(restored.snapshot.selectionEvents.none { it.requestId == request })
            match.reconnect()
            match.select(number, request)
            val after = requireSelection(fixture, match, request, number)
            match.verifyEconomy()
            receipt("postgres-stop-no-optimistic-ack", match, before, after, null,
                "outageAcceptedAck" to "false", "outageObservationMillis" to observationMillis.toString(),
                "outageRequestEndedBy" to observation.endedBy,
                "outageErrorCodes" to observation.errorCodes.sorted().joinToString(","),
                "requestAbsentBeforeRecoveryRetry" to "true", "requestCountAfterRecovery" to "1")
        } finally {
            if (postgresStopped) fixture.docker("start", "postgres")
            fixture.awaitReady(0); fixture.awaitReady(1)
            match.close()
        }
    }
    check(results.size == 8 && results.all { it.passed })
    return RoomOwnershipFaultSuiteReceipt(accepted = true, cases = results)
}

private suspend fun requireSelection(
    fixture: RoomAcceptanceFixture, match: AcceptanceMatch, request: String, number: Int
): StoredRoomReceipt {
    val stored = fixture.awaitStored(match.roomId) { room -> room.snapshot.selectionEvents.any { it.requestId == request } }
    val events = stored.snapshot.selectionEvents.filter { it.requestId == request }
    check(events.size == 1 && events.single().number == number && events.single().playerId == match.playerId(0) &&
        events.single().result == SelectionResult.ACCEPTED)
    check(stored.snapshot.selectedNumbers.count { it == number } == 1)
    return stored
}

private suspend fun connectAfterRedisRecovery(
    fixture: RoomAcceptanceFixture, account: AcceptanceAccount
): AcceptanceSocket = withTimeout(25_000) {
    while (true) {
        try {
            return@withTimeout fixture.connect(account, preferred = 0)
        } catch (failure: AcceptanceRejected) {
            // The Redis client's reconnect backoff may outlive PostgreSQL-only readiness.
            if (failure.code != "RATE_LIMIT_UNAVAILABLE") throw failure
            delay(100)
        }
    }
    error("Unreachable")
}

private fun requireSameBoard(before: StoredRoomReceipt, after: StoredRoomReceipt) {
    check(after.snapshot.roomId == before.snapshot.roomId && after.snapshot.matchId == before.snapshot.matchId)
    check(after.snapshot.numbers == before.snapshot.numbers)
    check(after.snapshot.phase == before.snapshot.phase && after.snapshot.targetOrder == before.snapshot.targetOrder)
    check(after.snapshot.selectedNumbers == before.snapshot.selectedNumbers)
    check(after.snapshot.selectedNumbersByPlayer == before.snapshot.selectedNumbersByPlayer)
    check(after.snapshot.targetIndexes == before.snapshot.targetIndexes)
    check(after.snapshot.scores == before.snapshot.scores && after.snapshot.currentTarget == before.snapshot.currentTarget)
    check(after.snapshot.selectionEvents == before.snapshot.selectionEvents)
    check(after.snapshot.startedAtEpochMillis == before.snapshot.startedAtEpochMillis)
    check(after.snapshot.finishedAtEpochMillis == before.snapshot.finishedAtEpochMillis)
}

private suspend fun requireSettlement(fixture: RoomAcceptanceFixture, matchId: String) {
    val id = uuid(matchId)
    withTimeout(12_000) {
        while (fixture.number("SELECT count(*) FROM room_ownership_pilot_settlements WHERE match_id='$id'") != 1L) delay(50)
    }
    check(fixture.number("SELECT count(*) FROM matches WHERE id='$id'") == 1L)
    check(fixture.number("SELECT count(*) FROM wallet_transactions WHERE source_type='MATCH' AND source_id='$id'") == 2L)
    check(fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE match_id='$id' AND reservation_state='FINALIZED'") == 2L)
}

/** Reads every text frame; an optimistic state containing the selection fails even without a formal ACK. */
private data class NoAckObservation(val endedBy: String, val errorCodes: Set<String>)

private suspend fun noAcceptedSelection(
    socket: AcceptanceSocket, number: Int, durationMillis: Long, requestId: String? = null
): NoAckObservation {
    val errors = mutableSetOf<String>()
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMillis)
    while (true) {
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
        if (remaining <= 0) return NoAckObservation("deadline", errors)
        val received = withTimeoutOrNull(remaining.coerceAtLeast(1)) { socket.session.incoming.receiveCatching() }
            ?: return NoAckObservation("deadline", errors)
        val frame = received.getOrNull() ?: return NoAckObservation("socket-closed", errors)
        if (frame is Frame.Text) {
            when (val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())) {
                is ServerMessage.Error -> {
                    errors += message.code
                    if (requestId != null && message.requestId == requestId && message.code == "ROOM_RECOVERING") {
                        return NoAckObservation("request-rejected", errors)
                    }
                }
                is ServerMessage.GameStateUpdated -> {
                    check(!message.selectionAccepted || message.acceptedNumber != number) {
                        "An unavailable dependency produced an accepted selection ACK"
                    }
                    check(number !in message.game.selectedNumbers) { "An unavailable dependency exposed optimistic state" }
                }
                is ServerMessage.GameFinished -> check(number !in message.game.selectedNumbers)
                else -> Unit
            }
        }
    }
}

private fun receipt(
    name: String, match: AcceptanceMatch, before: StoredRoomReceipt, after: StoredRoomReceipt,
    recoveryMillis: Long? = null, vararg evidence: Pair<String, String>
) = RoomOwnershipFaultCaseReceipt(name, roomId = match.roomId, matchId = match.matchId,
    beforeEpoch = before.epoch, afterEpoch = after.epoch, beforeRevision = before.revision, afterRevision = after.revision,
    recoveryMillis = recoveryMillis, evidence = mapOf(*evidence))

private fun requestId() = "fault-${UUID.randomUUID()}"
private fun uuid(value: String) = UUID.fromString(value).toString()
private fun elapsedMillis(started: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
