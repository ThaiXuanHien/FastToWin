package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnedWorkerNettyTest {
    @Test
    fun `signed peer new intent keeps its original generation across STOP_NEW and resume`() = runBlocking {
        withOwnedWorkerFixture { f ->
            val stale = f.activate()
            val creator = f.pilot()
            val host = f.rooms.bindSocket(f.players.keys.first())
            val guest = f.rooms.bindSocket(f.players.keys.last())
            val created = assertIs<OwnedRoomReply.Applied>(creator.onMessage(host,
                ClientMessage.CreateRoom("Generation", "private worker", ProtocolGameMode.ORDER)))
            assertTrue(creator.beginDrain())
            f.stopNew()
            withWorkerServer(f) { _, uri ->
                val authority = PostgresOwnedAdmissionRepository(f.source)
                val current = authority.resume(assertNotNull(authority.observe()).generation, OwnedAdmissionBinding.from(f.config))
                val recovered = assertNotNull(f.rooms.read(created.roomId))
                val peer = OwnerPeerTransport(f.config.peerId, f.config.nodeId, uri, f.config.peerSecret)
                val request = OwnerCommandRequest(created.roomId.toString(), recovered.fence.epoch,
                    guest.playerId.toString(), guest.generation, OwnerOperation.COMMAND,
                    ClientMessage.JoinRoom(created.roomId.toString(), "private worker"), recovered.fence.instanceId.toString(),
                    admissionPolicy = stale)
                assertIs<OwnedRoomReply.Rejected>(peer.forward(request))
                assertNull(f.rooms.lookupRoom(guest.playerId))
                assertIs<OwnedRoomReply.Applied>(peer.forward(request.copy(admissionPolicy = current)))
                assertEquals(created.roomId, f.rooms.lookupRoom(guest.playerId))
            }
        }
    }

    @Test
    fun `real worker recovers owned leases serves signed reconnect and refuses new match admission`() = runBlocking {
        withOwnedWorkerFixture { f ->
            f.activate()
            val creator = f.pilot()
            val socket = f.rooms.bindSocket(f.players.keys.first())
            val created = assertIs<OwnedRoomReply.Applied>(creator.onMessage(socket,
                ClientMessage.CreateRoom("Existing owned room", "private worker", ProtocolGameMode.ORDER)))
            assertTrue(creator.beginDrain())
            f.stopNew()
            withWorkerServer(f) { _, uri ->
                val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                for (path in listOf("/game", "/auth/login", "/api/content-terms", "/admin/api/maintenance", "/gifts", "/status")) {
                    assertEquals(404, http.send(HttpRequest.newBuilder(uri.resolve(path)).GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode(), path)
                }
                val recovered = assertNotNull(f.rooms.read(created.roomId))
                assertEquals(created.epoch + 1, recovered.fence.epoch)
                val peer = OwnerPeerTransport(f.config.peerId, f.config.nodeId, uri, f.config.peerSecret)
                val reconnect = OwnerCommandRequest(created.roomId.toString(), recovered.fence.epoch,
                    socket.playerId.toString(), socket.generation, OwnerOperation.RECONNECT,
                    ownerInstanceId = recovered.fence.instanceId.toString())
                assertIs<OwnedRoomReply.Applied>(peer.forward(reconnect))
                val ready = reconnect.copy(operation = OwnerOperation.COMMAND,
                    command = ClientMessage.SetReady(created.roomId.toString(), true))
                assertEquals("MAINTENANCE", assertIs<OwnedRoomReply.Retryable>(peer.forward(ready)).publicMessage.code)
                assertEquals(OwnedRoomReply.NoRoom, peer.forward(reconnect.copy(operation = OwnerOperation.DISCONNECT)))
                assertEquals(200, http.send(HttpRequest.newBuilder(uri.resolve("/internal/metrics")).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode())
            }
        }
    }

    @Test
    fun `actual worker continues acknowledged gameplay delivers outbox and settles an existing match`() = runBlocking {
        withOwnedWorkerFixture { f ->
            f.activate()
            val creator = f.pilot()
            val host = f.rooms.bindSocket(f.players.keys.first())
            val guest = f.rooms.bindSocket(f.players.keys.last())
            val created = assertIs<OwnedRoomReply.Applied>(creator.onMessage(host,
                ClientMessage.CreateRoom("Active owned room", "private worker", ProtocolGameMode.ORDER)))
            val roomId = created.roomId.toString()
            assertIs<OwnedRoomReply.Applied>(creator.onMessage(guest, ClientMessage.JoinRoom(roomId, "private worker")))
            assertIs<OwnedRoomReply.Applied>(creator.onMessage(host, ClientMessage.SetReady(roomId, true)))
            assertIs<OwnedRoomReply.Applied>(creator.onMessage(guest, ClientMessage.SetReady(roomId, true)))
            assertIs<OwnedRoomReply.Applied>(creator.onMessage(host, ClientMessage.SelectNumber(roomId, 1, "before-handoff")))
            assertTrue(creator.beginDrain())
            f.stopNew()

            withWorkerServer(f) { worker, uri ->
                val recovered = assertNotNull(f.rooms.read(created.roomId))
                assertEquals(created.epoch + 1, recovered.fence.epoch)
                val peer = OwnerPeerTransport(f.config.peerId, f.config.nodeId, uri, f.config.peerSecret)
                fun request(socket: SocketFence, command: ClientMessage? = null) = OwnerCommandRequest(
                    roomId, recovered.fence.epoch, socket.playerId.toString(), socket.generation,
                    if (command == null) OwnerOperation.RECONNECT else OwnerOperation.COMMAND, command,
                    recovered.fence.instanceId.toString()
                )
                val hostReply = assertIs<OwnedRoomReply.Applied>(peer.forward(request(host)))
                assertEquals(RoomPhase.PLAYING, assertNotNull(hostReply.gameSnapshot()).phase)
                assertIs<OwnedRoomReply.Applied>(peer.forward(request(guest)))
                val delivered = Channel<ServerMessage>(Channel.UNLIMITED)
                worker.delivery.register(host, hostReply) { delivered.send(it) }
                for (number in 2..GAME_NUMBER_COUNT) {
                    val selected = assertIs<OwnedRoomReply.Applied>(peer.forward(request(host,
                        ClientMessage.SelectNumber(roomId, number, "after-handoff-$number"))))
                    assertTrue(assertIs<ServerMessage.GameStateUpdated>(selected.publicMessage).selectionAccepted)
                }
                withTimeout(8_000) {
                    while (true) {
                        val message = delivered.receive()
                        if (message is ServerMessage.GameStateUpdated && message.game.phase == RoomPhase.FINISHED) break
                    }
                }
                withTimeout(8_000) {
                    while (f.source.connection.use { c -> c.createStatement().use { s ->
                        s.executeQuery("SELECT count(*) FROM room_ownership_pilot_settlements").use { r -> r.next(); r.getInt(1) }
                    } } != 1) delay(25)
                }
                val finished = assertNotNull(f.rooms.read(created.roomId)).snapshot
                assertEquals(RoomPhase.FINISHED, finished.phase)
                assertEquals(GAME_NUMBER_COUNT, finished.selectionEvents.size)
                assertEquals("before-handoff", finished.selectionEvents.first().requestId)
                assertNull(finished.pendingCompletedMatch)
            }
            assertEquals(0, f.rooms.durableSnapshot().remainingOwnedLeases, "Worker shutdown retained a live owned lease")
            assertEquals(1, f.rooms.durableSnapshot().remainingOpenRooms, "Process drain must preserve existing result rooms")
        }
    }

    private suspend fun withWorkerServer(
        fixture: OwnedWorkerTestFixture,
        block: suspend (RoomOwnershipPilot, URI) -> Unit
    ) {
        OwnedWorkerDatabaseRuntime.open(fixture.databaseSettings).use { database ->
            val worker = database.openRoomOwnershipPilot(fixture.config, candidateIdentity = { OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64)) })
            val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) { ownedWorkerModule(worker) }.start()
            try {
                val port = server.engine.resolvedConnectors().first().port
                withTimeout(8_000) {
                    while (fixture.rooms.durableSnapshot().remainingOwnedLeases == 0) delay(25)
                }
                assertFalse(worker.isReady(), "STOP_NEW cannot advertise admission readiness")
                block(worker, URI("http://127.0.0.1:$port"))
            } finally { server.stop(0, 5_000) }
            assertFalse(worker.canRollback(), "Open owned rooms cannot authorize legacy rollback")
        }
    }
}
