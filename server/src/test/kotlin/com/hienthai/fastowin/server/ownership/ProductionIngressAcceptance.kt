package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.UUID

private data class RecoveryClock(val now: Long, val deadline: Long?, val closed: Long?)

private fun RoomAcceptanceFixture.recoveryClock(roomId: String) = withFixtureDatabase { c ->
    c.prepareStatement("SELECT clock_timestamp(), recovery_deadline, closed_at FROM room_ownership_pilot_rooms WHERE room_id=?").use { s ->
        s.setObject(1, UUID.fromString(roomId))
        s.executeQuery().use { r ->
            check(r.next())
            RecoveryClock(r.getTimestamp(1).time, r.getTimestamp(2)?.time, r.getTimestamp(3)?.time)
        }
    }
}

internal suspend fun productionIngressAcceptance(fixture: RoomAcceptanceFixture): JsonObject {
    val config = requireNotNull(fixture.settings.topologyStatus)
    val cases = mutableListOf<JsonObject>()
    for (index in 0..1) {
        val inside = index == 0
        val old = config.process(0)
        val survivor = config.process(1)
        val match = fixture.createMatch(3 + index, targetOwner = 0)
        val selected = UUID.randomUUID().toString()
        match.select(match.nextNumber, selected)
        match.verifyEconomy()
        val before = fixture.snapshot(match.roomId)
        check(before.instanceId == old && before.snapshot.selectionEvents.count { it.requestId == selected } == 1)
        val ownerBefore = fixture.ownerFor(before)
        val legacyAccount = fixture.accounts()[26]
        val legacy = if (inside) fixture.connect(legacyAccount, 0) else null
        var legacyRoom: GameSnapshot? = null
        var restoredLegacy = 0
        var killed = false
        var restarted = false
        try {
            if (legacy != null) {
                legacy.send(ClientMessage.CreateRoom("Legacy restore fixture", "", ProtocolGameMode.ORDER))
                legacyRoom = (legacy.await { it is ServerMessage.RoomCreated } as ServerMessage.RoomCreated).game
                val legacyId = UUID.fromString(legacyRoom.roomId)
                check(fixture.number("SELECT count(*) FROM active_room_snapshots WHERE room_id='$legacyId'") == 1L)
                check(fixture.number("SELECT count(*) FROM room_ownership_pilot_rooms WHERE room_id='$legacyId'") == 0L)
            }
            fixture.faults.backend(0, BackendAction.KILL)
            killed = true
            // The supervisor acknowledgement alone does not establish loss of the public route.
            val interrupted = (0..1).count { player ->
                withTimeout(5_000) {
                    val incoming = match.socket(player).session.incoming
                    while (incoming.receiveCatching().isSuccess) {
                        // Drain frames queued before the killed ingress closed the transport.
                    }
                }
                true
            }
            val refused = try {
                withTimeout(3_000) { fixture.client.get(fixture.settings.urls[0] + "/health") }
                false
            } catch (_: IOException) { true }
            check(refused)
            val claimed = fixture.awaitStored(match.roomId) { it.instanceId == survivor && it.epoch > before.epoch }
            check(claimed.snapshot.matchId == match.matchId && claimed.snapshot.numbers == before.snapshot.numbers)
            val observed = fixture.recoveryClock(match.roomId)
            val deadline = requireNotNull(observed.deadline)
            check(observed.now < deadline && observed.closed == null)
            check(config.process(1) == survivor)
            fixture.faults.backend(0, BackendAction.START)
            restarted = true
            fixture.awaitReady(0)
            fixture.refreshOwnerProcesses()
            val replacement = config.process(0)
            check(replacement != old && config.process(1) == survivor)
            val reconnectTime: RecoveryClock
            val returning: Int
            if (inside) {
                match.reconnect()
                reconnectTime = fixture.recoveryClock(match.roomId)
                check(reconnectTime.now < deadline && reconnectTime.deadline == null && reconnectTime.closed == null)
                returning = (0..1).count { match.socket(it).ready.currentGame?.matchId == before.snapshot.matchId }
                check(returning == 2)
                val next = UUID.randomUUID().toString()
                match.select(match.nextNumber, next)
                check(fixture.snapshot(match.roomId).snapshot.selectionEvents.count { it.requestId == next } == 1)
                match.verifyEconomy()
                val expectedLegacy = requireNotNull(legacyRoom)
                val resumedLegacy = fixture.connect(legacyAccount, 0, requireNotNull(legacy).ready.resumeToken)
                try {
                    check(resumedLegacy.ready.currentGame?.roomId == expectedLegacy.roomId)
                    resumedLegacy.leaveRoom(expectedLegacy.roomId)
                    restoredLegacy = 1
                } finally { resumedLegacy.closeGracefully() }
                match.socket(0).send(ClientMessage.LeaveRoom(match.roomId))
                val finishes = (0..1).map { i ->
                    match.socket(i).await { it is ServerMessage.GameFinished && it.game.matchId == match.matchId } as ServerMessage.GameFinished
                }
                check(finishes[0].game == finishes[1].game && finishes[0].game.winnerPlayerId == match.playerId(1))
                match.verifyFinishedEconomy(finishes[0].game)
                for (i in 0..1) match.socket(i).leaveRoom(match.roomId)
                withTimeout(30_000) {
                    while (fixture.number("SELECT count(*) FROM room_ownership_pilot_settlements WHERE match_id='${match.matchId}'") != 1L) delay(50)
                }
            } else {
                withTimeout(40_000) {
                    while (fixture.recoveryClock(match.roomId).let { it.now < deadline || it.closed == null }) delay(100)
                }
                val members = fixture.number("SELECT count(*) FROM room_ownership_pilot_memberships WHERE room_id='${match.roomId}'")
                check(members == 0L)
                val accounts = fixture.accounts()
                val pair = config.targetPair(fixture.settings.accountIds, 3 + index, 0).map(accounts::get)
                val fresh = fixture.connectPair(pair[0], 0, pair[1], 0)
                try {
                    returning = fresh.count { it.ready.currentGame != null }
                    check(returning == 0 && fresh.all { it.backend == 0 })
                } finally { fresh.forEach { it.closeGracefully() } }
                reconnectTime = fixture.recoveryClock(match.roomId)
                check(requireNotNull(reconnectTime.closed) >= deadline)
                val expired = fixture.snapshot(match.roomId).snapshot
                check(expired.pendingCompletedMatch == null && expired.forcedWinnerId == null && !expired.resultQueued)
            }
            val snapshot = fixture.snapshot(match.roomId).snapshot
            val originalSelections = snapshot.selectionEvents.count { it.requestId == selected }
            val quota = fixture.number("SELECT count(*) FROM play_quota_match_consumptions WHERE match_id='${match.matchId}' AND reservation_state='FINALIZED'")
            val settlements = fixture.number("SELECT count(*) FROM room_ownership_pilot_settlements WHERE match_id='${match.matchId}'")
            val matches = fixture.number("SELECT count(*) FROM matches WHERE id='${match.matchId}'")
            check(originalSelections == 1 && quota == 2L && settlements == (if (inside) 1L else 0L) && settlements == matches)
            cases += buildJsonObject {
                put("outcome", if (inside) "inside" else "outside")
                put("ownerBefore", ownerBefore); put("ownerAfter", fixture.ownerFor(claimed))
                put("epochBefore", before.epoch); put("epochAfter", claimed.epoch)
                put("oldProcess", old.toString().replace("-", "")); put("newProcess", replacement.toString().replace("-", ""))
                put("interruptedSockets", interrupted); put("refusedConnections", 1)
                put("claimDbMillis", observed.now); put("recoveryDeadlineMillis", deadline)
                put("reconnectDbMillis", reconnectTime.now)
                put("closedAtMillis", reconnectTime.closed?.let(::JsonPrimitive) ?: JsonNull)
                put("legacyRestoredOnA", restoredLegacy)
                put("returningGames", returning); put("matchRows", matches); put("settlements", settlements)
                put("originalSelections", originalSelections); put("quota", quota)
            }
            fixture.faults.verifyHealthy()
        } finally {
            if (killed && !restarted) withContext(NonCancellable) { fixture.faults.backend(0, BackendAction.START) }
            legacy?.closeGracefully()
            for (i in 0..1) match.socket(i).closeGracefully()
        }
    }
    val workerResponse = fixture.client.get(fixture.settings.urls[1] + "/internal/metrics")
    check(workerResponse.status == HttpStatusCode.OK)
    val workerMetrics = workerResponse.bodyAsText()
    check(workerMetrics.lineSequence().none { it.startsWith("fasttowin_websocket_") })
    return buildJsonObject { put("cases", JsonArray(cases)); put("workerPublicMetrics", 0) }
}
