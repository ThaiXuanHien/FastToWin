package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.MaintenanceState
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.hienthai.fastowin.server.maintenance.MaintenancePolicy
import com.hienthai.fastowin.server.ownership.withAdmissionFixture
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyGameIntentTest {
    @Test
    fun `spectator keeps legacy intent until leave is saved`() = runTest {
        val fixture = RoomFixture()
        val roomId = fixture.openRoom(asSpectator = true)
        val memberId = fixture.member.userId.toString()
        assertTrue(fixture.engine.hasActiveGameIntent(memberId))

        val write = fixture.repository.holdNextWrite()
        val leave = async { fixture.engine.handle(memberId, ClientMessage.LeaveRoom(roomId)) }
        write.entered.await()
        assertTrue(fixture.engine.hasActiveGameIntent(memberId))
        write.release.complete(Unit)
        leave.await()

        assertFalse(fixture.engine.hasActiveGameIntent(memberId))
    }

    @Test
    fun `kicked player keeps legacy intent until updated room is saved`() = runTest {
        val fixture = RoomFixture()
        val roomId = fixture.openRoom()
        val memberId = fixture.member.userId.toString()
        val write = fixture.repository.holdNextWrite()

        val kick = async { fixture.kick(roomId) }
        write.entered.await()
        assertTrue(fixture.engine.hasActiveGameIntent(memberId))
        write.release.complete(Unit)
        assertEquals("ROOM_KICKED", kick.await().message<ServerMessage.RoomClosed>().code)

        assertFalse(fixture.engine.hasActiveGameIntent(memberId))
    }

    @Test
    fun `host leave keeps the other player reserved until room deletion`() = runTest {
        val fixture = RoomFixture()
        val roomId = fixture.openRoom()
        val memberId = fixture.member.userId.toString()
        val write = fixture.repository.holdNextWrite()

        val leave = async { fixture.engine.handle(fixture.host.userId.toString(), ClientMessage.LeaveRoom(roomId)) }
        write.entered.await()
        assertTrue(fixture.engine.hasActiveGameIntent(memberId))
        write.release.complete(Unit)
        assertEquals("ROOM_HOST_LEFT", leave.await().message<ServerMessage.RoomClosed>().code)

        assertFalse(fixture.engine.hasActiveGameIntent(memberId))
    }

    @Test
    fun `expired host cleanup keeps the other player reserved until room deletion`() = runTest {
        val fixture = RoomFixture()
        fixture.openRoom()
        val memberId = fixture.member.userId.toString()
        fixture.engine.markDisconnected(fixture.host.userId.toString())
        fixture.now += 30_000
        val write = fixture.repository.holdNextWrite()

        val cleanup = async { fixture.engine.cleanupExpiredSessions() }
        write.entered.await()
        assertTrue(fixture.engine.hasActiveGameIntent(memberId))
        write.release.complete(Unit)
        assertEquals("ROOM_DISCONNECTED_TOO_LONG", cleanup.await().message<ServerMessage.RoomClosed>().code)

        assertFalse(fixture.engine.hasActiveGameIntent(memberId))
    }

    @Test
    fun `failed kick stays reserved in memory and the durable room restores membership`() = runTest {
        val fixture = RoomFixture()
        val roomId = fixture.openRoom()
        val memberId = fixture.member.userId.toString()
        val write = fixture.repository.holdNextWrite()

        val kick = async { assertFailsWith<ReleaseWriteFailure> { fixture.kick(roomId) } }
        write.entered.await()
        write.release.completeExceptionally(ReleaseWriteFailure())
        kick.await()

        assertTrue(fixture.engine.hasActiveGameIntent(memberId))
        val restarted = GameEngine(activeRoomRepository = fixture.repository)
        assertTrue(restarted.hasActiveGameIntent(memberId))
    }

    @Test
    fun `cancelled account reconnect does not leave the engine connected`() = runTest {
        val repository = HoldingRoomRepository(InMemoryActiveRoomRepository())
        val fixture = RoomFixture(repository)
        val connected = fixture.engine.connectAccount(fixture.host)
        fixture.engine.handle(
            connected.playerId,
            ClientMessage.CreateRoom("Reconnect", "", ProtocolGameMode.ORDER)
        )

        val write = repository.holdNextWrite()
        val reconnect = async {
            fixture.engine.connectAccount(fixture.host, connected.resumeToken)
        }
        write.entered.await()
        reconnect.cancelAndJoin()

        assertEquals(FriendPresence.OFFLINE, fixture.engine.localPresence(connected.playerId))
    }
    @Test
    fun `room deletion blocked in postgres keeps the other player reserved`() = runBlocking {
        withAdmissionFixture { database ->
            val stored = PostgresActiveRoomRepository(database.source)
            val fixture = RoomFixture(stored)
            val roomId = fixture.openRoom()
            val memberId = fixture.member.userId.toString()

            database.source.connection.use { gate ->
                gate.autoCommit = false
                gate.prepareStatement("SELECT room_id FROM active_room_snapshots WHERE room_id = ? FOR UPDATE").use { statement ->
                    statement.setObject(1, UUID.fromString(roomId))
                    statement.executeQuery().use { assertTrue(it.next()) }
                }
                val leave = async(Dispatchers.IO) {
                    fixture.engine.handle(fixture.host.userId.toString(), ClientMessage.LeaveRoom(roomId))
                }
                try {
                    database.awaitBlockedBy(database.pid(gate))
                    assertTrue(fixture.engine.hasActiveGameIntent(memberId))
                    assertEquals(memberId, stored.loadAll().single().guest?.playerId)
                } finally {
                    gate.rollback()
                }
                assertEquals("ROOM_HOST_LEFT", leave.await().message<ServerMessage.RoomClosed>().code)
            }
            assertFalse(fixture.engine.hasActiveGameIntent(memberId))
            assertFalse(GameEngine(activeRoomRepository = stored).hasActiveGameIntent(memberId))
        }
    }

    @Test
    fun `maintenance abort retry cannot restore an acknowledged spectator departure`() = runTest {
        val fixture = RoomFixture()
        val roomId = fixture.openRoom(asSpectator = true)
        val memberId = fixture.member.userId.toString()
        val write = fixture.repository.holdNextWrite()
        val abort = async {
            assertFailsWith<ReleaseWriteFailure> {
                fixture.engine.applyMaintenancePolicy(MaintenancePolicy(MaintenanceState.MAINTENANCE))
            }
        }
        write.entered.await()
        write.release.completeExceptionally(ReleaseWriteFailure())
        abort.await()

        fixture.engine.publishMaintenancePolicy(MaintenancePolicy(MaintenanceState.DRAINING))
        fixture.engine.handle(memberId, ClientMessage.LeaveRoom(roomId)).message<ServerMessage.RoomUpdated>()
        assertFalse(fixture.engine.hasActiveGameIntent(memberId))
        fixture.engine.reconcileMaintenancePolicy()

        assertFalse(GameEngine(activeRoomRepository = fixture.repository).hasActiveGameIntent(memberId))
    }

    @Test
    fun `earlier room save cannot acknowledge a later release of the same player`() = runTest {
        val fixture = RoomFixture()
        val roomId = fixture.openRoom()
        val memberId = fixture.member.userId.toString()
        val firstWrite = fixture.repository.holdNextWrite()
        val firstKick = async { fixture.kick(roomId) }
        firstWrite.entered.await()
        fixture.join(roomId)

        val secondWrite = fixture.repository.holdNextWrite()
        val secondKick = async(start = CoroutineStart.UNDISPATCHED) { fixture.kick(roomId) }
        firstWrite.release.complete(Unit)
        secondWrite.entered.await()
        assertTrue(fixture.engine.hasActiveGameIntent(memberId))

        secondWrite.release.complete(Unit)
        firstKick.await()
        secondKick.await()
        assertFalse(fixture.engine.hasActiveGameIntent(memberId))
    }

    @Test
    fun `ordinary host cancellation keeps tournament players reserved until save`() = runTest {
        val write = HeldWrite()
        val repository = object : InMemoryTournamentRepository() {
            override suspend fun save(tournament: TournamentSnapshot) {
                if (tournament.phase == TournamentPhase.CANCELLED) write.awaitRelease()
                super.save(tournament)
            }
        }
        val engine = GameEngine(tournamentRepository = repository)
        val hostId = engine.connectAccount(account("Host")).playerId
        val memberId = engine.connectAccount(account("Member")).playerId
        val tournamentId = engine.handle(hostId, ClientMessage.CreateTournament("Cup", ProtocolGameMode.ORDER))
            .message<ServerMessage.TournamentUpdated>().tournament.tournamentId
        engine.handle(memberId, ClientMessage.JoinPublicTournament(tournamentId))
            .message<ServerMessage.TournamentUpdated>()
        assertTrue(engine.hasActiveGameIntent(memberId))

        val cancel = async { engine.handle(hostId, ClientMessage.LeaveTournament(tournamentId)) }
        write.entered.await()
        assertTrue(engine.hasActiveGameIntent(memberId))
        write.release.complete(Unit)
        assertEquals(TournamentPhase.CANCELLED, cancel.await().message<ServerMessage.TournamentUpdated>().tournament.phase)
        assertFalse(engine.hasActiveGameIntent(memberId))
    }

    private class RoomFixture(stored: ActiveRoomRepository = InMemoryActiveRoomRepository()) {
        val host = account("Host")
        val member = account("Member")
        val repository = HoldingRoomRepository(stored)
        var now = 1_000L
        val engine = GameEngine(activeRoomRepository = repository, nowMillis = { now })

        suspend fun openRoom(asSpectator: Boolean = false): String {
            val connectedHost = engine.connectAccount(host)
            engine.connectAccount(member)
            val roomId = engine.handle(connectedHost.playerId, ClientMessage.CreateRoom("Room", "", ProtocolGameMode.ORDER))
                .message<ServerMessage.RoomCreated>().game.roomId
            join(roomId, asSpectator)
            engine.connectAccount(host, connectedHost.resumeToken)
            return roomId
        }

        suspend fun join(roomId: String, asSpectator: Boolean = false) {
            engine.handle(member.userId.toString(), ClientMessage.JoinRoom(roomId, "", asSpectator))
                .message<ServerMessage.RoomUpdated>()
        }

        suspend fun kick(roomId: String): List<Delivery> =
            engine.handle(host.userId.toString(), ClientMessage.KickPlayer(roomId, member.userId.toString()))
    }

    private class HoldingRoomRepository(private val stored: ActiveRoomRepository) : ActiveRoomRepository by stored {
        private var nextWrite: HeldWrite? = null

        fun holdNextWrite(): HeldWrite = HeldWrite().also {
            check(nextWrite == null)
            nextWrite = it
        }

        override suspend fun save(room: StoredActiveRoom) {
            awaitWriteRelease()
            stored.save(room)
        }

        override suspend fun delete(roomId: String) {
            awaitWriteRelease()
            stored.delete(roomId)
        }

        private suspend fun awaitWriteRelease() {
            val write = nextWrite ?: return
            nextWrite = null
            write.awaitRelease()
        }
    }

    private class HeldWrite {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        suspend fun awaitRelease() {
            entered.complete(Unit)
            release.await()
        }
    }

    private class ReleaseWriteFailure : RuntimeException()

    private companion object {
        fun account(name: String) = AuthenticatedAccount(UUID.randomUUID(), name)

        inline fun <reified T : ServerMessage> List<Delivery>.message(): T =
            map(Delivery::message).filterIsInstance<T>().single()
    }
}
