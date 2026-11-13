package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.maintenance.*
import com.hienthai.fastowin.server.presence.PresenceConfiguration
import com.hienthai.fastowin.server.presence.PresenceStore
import com.hienthai.fastowin.server.presence.RedisPresenceStore
import com.hienthai.fastowin.server.ratelimit.RedisFaultProxy
import kotlinx.coroutines.withTimeoutOrNull
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.UUID
import kotlin.test.*

class RoomOwnershipProductionWebSocketTest {
    @Test
    fun `old frame queued before dispatch cannot reserve a lane after replacement`() = runBlocking {
        val gate = CreateGate()
        fixture(gate) { f ->
            val original = f.connect(f.aPort, f.tokens[0])
            try {
                original.sendMessage(ClientMessage.CreateRoom("Obsolete legacy intent", "", ProtocolGameMode.ORDER))
                withTimeout(8_000) { gate.entered.await() }
                val replacement = f.connect(f.aPort, f.tokens[0], original.ready.resumeToken)
                try {
                    gate.release.complete(Unit)
                    assertEquals(SESSION_REPLACED_CLOSE_REASON, withTimeout(8_000) { original.socket.closeReason.await() }?.message)
                    replacement.sendMessage(ClientMessage.CreateRoom("Current owned intent", "private", ProtocolGameMode.ORDER))
                    val room = replacement.receiveMessage<ServerMessage.RoomCreated>().game
                    assertEquals(UUID.fromString(room.roomId), f.rooms.lookupRoom(f.accounts[0]))
                    assertTrue(f.engine.roomList().rooms.none { it.name == "Obsolete legacy intent" })
                } finally { replacement.close() }
            } finally {
                gate.release.complete(Unit)
                original.close()
            }
        }
    }

    @Test
    fun `fresh ingress waits for the previous generation's held worker create before choosing a lane`() = runBlocking {
        for (commit in listOf(true, false)) fixture { f ->
            val host = f.accounts[0]
            // The preceding ingress lifetime left a durable fence, with no public connection
            // or pending descriptor in this instance's account coordinator.
            val oldSocket = f.rooms.bindSocket(host)
            val oldCreate = assertIs<OwnedCreatePreparation.Ready>(f.pilot.prepareOwnedCreate(
                oldSocket, ClientMessage.CreateRoom("Previous ingress", "private", ProtocolGameMode.ORDER)
            )).request
            assertEquals(f.workerId, oldCreate.destinationNodeId)
            f.source.connection.use { gate ->
                gate.createStatement().use { statement ->
                    val outcome = if (commit) "RETURN NEW;" else "RAISE EXCEPTION ''test rollback'';"
                    statement.execute("""CREATE FUNCTION hold_previous_create() RETURNS trigger LANGUAGE plpgsql AS
                        'BEGIN PERFORM pg_advisory_xact_lock(892765); $outcome END;'""")
                    statement.execute("""CREATE TRIGGER hold_previous_create BEFORE INSERT ON room_ownership_pilot_rooms
                        FOR EACH ROW EXECUTE FUNCTION hold_previous_create()""")
                }
                gate.autoCommit = false
                val blocker = gate.createStatement().use { statement ->
                    statement.execute("SELECT pg_advisory_xact_lock(892765)")
                    statement.executeQuery("SELECT pg_backend_pid()").use { rows -> rows.next(); rows.getInt(1) }
                }
                val create = async(Dispatchers.IO) { f.pilot.createOwned(oldCreate) }
                var replacement: kotlinx.coroutines.Deferred<Connection>? = null
                try {
                    val worker = f.awaitDatabaseWaiter(blocker)
                    val connecting = async { f.connect(f.aPort, f.tokens[0]) }
                    replacement = connecting
                    f.awaitDatabaseWaiter(worker)
                    assertFalse(connecting.isCompleted)
                    gate.rollback()
                    if (commit) assertIs<OwnedCreateResult.Created>(create.await())
                    else assertIs<OwnedCreateResult.Unresolved>(create.await())
                    val socket = connecting.await()
                    try {
                        assertFalse(f.rooms.isCurrentSocket(oldSocket))
                        assertEquals(oldCreate.roomId.takeIf { commit }?.toString(), socket.ready.currentGame?.roomId)
                        socket.sendMessage(ClientMessage.CreateRoom("Competing legacy intent", "", ProtocolGameMode.ORDER))
                        if (commit) assertEquals("ALREADY_IN_ROOM", socket.receiveMessage<ServerMessage.Error>().code)
                        else {
                            val legacy = socket.receiveMessage<ServerMessage.RoomCreated>().game
                            assertEquals("Competing legacy intent", legacy.roomName)
                            assertNull(f.rooms.lookupRoom(host))
                        }
                    } finally { socket.close() }
                } finally {
                    gate.rollback()
                    create.cancel()
                    replacement?.cancel()
                }
            }
        }
    }

    @Test
    fun `legacy account replacement advances the production fence`() = runBlocking {
        fixture { f ->
            val accountId = f.accounts[2]
            val original = f.connect(f.aPort, f.tokens[2])
            try {
                val first = SocketFence(accountId, 1)
                assertTrue(f.rooms.isCurrentSocket(first))
                val replacement = f.connect(f.aPort, f.tokens[2], original.ready.resumeToken)
                try {
                    assertFalse(f.rooms.isCurrentSocket(first))
                    assertTrue(f.rooms.isCurrentSocket(SocketFence(accountId, 2)))
                    replacement.sendMessage(ClientMessage.CreateRoom("Still legacy", "", ProtocolGameMode.ORDER))
                    val created = replacement.receiveMessage<ServerMessage.RoomCreated>()
                    assertNull(f.rooms.read(UUID.fromString(created.game.roomId)))
                } finally { replacement.close() }
            } finally { original.close() }
        }
    }

    @Test
    fun `invalid legacy resume preserves the current socket and fence`() = runBlocking {
        fixture { f ->
            val original = f.connect(f.aPort, f.tokens[0])
            try {
                val invalid = f.client.webSocketSession("ws://127.0.0.1:${f.aPort}/game")
                try {
                    invalid.sendMessage(ClientMessage.ConnectAccount(f.tokens[0], resumeToken = "invalid"))
                    assertEquals("INVALID_RESUME_TOKEN", invalid.receiveMessage<ServerMessage.Error>().code)
                    assertTrue(f.rooms.isCurrentSocket(SocketFence(f.accounts[0], 1)))
                    original.sendMessage(ClientMessage.CreateRoom("Still current", "", ProtocolGameMode.ORDER))
                    assertEquals("Still current", original.receiveMessage<ServerMessage.RoomCreated>().game.roomName)
                } finally { invalid.close() }
            } finally { original.close() }
        }
    }

    @Test
    fun `production serves legacy users and unsupported opted in modes without a marker`() = runBlocking {
        fixture { f ->
            val outside = f.connect(f.aPort, f.tokens[2])
            val opted = f.connect(f.aPort, f.tokens[0])
            val guest = f.client.webSocketSession("ws://127.0.0.1:${f.aPort}/game")
            try {
                guest.sendMessage(ClientMessage.ConnectGuest("Visitor", acceptedContentTermsVersion = CONTENT_TERMS_VERSION))
                guest.receiveMessage<ServerMessage.SessionReady>()
                outside.sendMessage(ClientMessage.CreateRoom("Legacy", "", ProtocolGameMode.ORDER))
                val legacy = outside.receiveMessage<ServerMessage.RoomCreated>().game
                assertNull(f.rooms.read(UUID.fromString(legacy.roomId)))
                opted.sendMessage(ClientMessage.CreateRoom("Random", "private", ProtocolGameMode.RANDOM_TARGET))
                val random = opted.receiveMessage<ServerMessage.RoomCreated>().game
                assertEquals(ProtocolGameMode.RANDOM_TARGET, random.gameMode)
                assertNull(f.rooms.read(UUID.fromString(random.roomId)))
                opted.sendMessage(ClientMessage.CreateRoom("Cannot move", "private", ProtocolGameMode.ORDER))
                assertEquals("ALREADY_IN_ROOM", opted.receiveMessage<ServerMessage.Error>().code)
                assertNull(f.rooms.lookupRoom(f.accounts[0]))
                val reconnected = f.connect(f.aPort, f.tokens[0], opted.ready.resumeToken)
                try { assertEquals(random.roomId, reconnected.ready.currentGame?.roomId) }
                finally { reconnected.close() }
            } finally { guest.close(); opted.close(); outside.close() }
        }
    }

    @Test
    fun `owned zero drain preserves an active public legacy order match`() = runBlocking {
        fixture { f ->
            val host = f.connect(f.aPort, f.tokens[0])
            val guest = f.connect(f.aPort, f.tokens[2])
            try {
                host.sendMessage(ClientMessage.CreateRoom("Legacy public", "", ProtocolGameMode.ORDER))
                val room = host.receiveMessage<ServerMessage.RoomCreated>().game
                guest.sendMessage(ClientMessage.JoinRoom(room.roomId, ""))
                guest.receiveMessage<ServerMessage.RoomUpdated>()
                host.receiveMessage<ServerMessage.RoomUpdated>()
                host.sendMessage(ClientMessage.SetReady(room.roomId, true))
                host.receiveMessage<ServerMessage.RoomUpdated>()
                guest.sendMessage(ClientMessage.SetReady(room.roomId, true))
                host.receiveMessage<ServerMessage.GameStarted>()
                guest.receiveMessage<ServerMessage.GameStarted>()
                val active = assertNotNull(PostgresOwnedAdmissionRepository(f.source).observe())
                val stop = assertNotNull(f.control("stop_new", active.generation))
                val stopped = assertNotNull(stop.policy)
                val done = assertNotNull(f.control("step", stopped.generation))
                assertEquals("completed", done.outcome)
                host.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                host.receiveMessage<ServerMessage.GameFinished>()
                guest.receiveMessage<ServerMessage.GameFinished>()
            } finally { guest.close(); host.close() }
        }
    }

    @Test
    fun `production owns only private casual order with both public sockets on ingress`() = runBlocking {
        fixture { f ->
            val host = f.connect(f.aPort, f.tokens[0])
            val guest = f.connect(f.aPort, f.tokens[1])
            val outside = f.connect(f.aPort, f.tokens[2])
            try {
                host.sendMessage(ClientMessage.CreateRoom("Owned", "private", ProtocolGameMode.ORDER))
                val created = host.receiveMessage<ServerMessage.RoomCreated>().game
                assertNotNull(f.rooms.read(UUID.fromString(created.roomId)))
                assertEquals(f.workerId, f.createDestination(UUID.fromString(created.roomId)))
                outside.sendMessage(ClientMessage.JoinRoom(created.roomId, "private"))
                assertEquals("PILOT_ACCOUNT_REQUIRED", outside.receiveMessage<ServerMessage.Error>().code)
                guest.sendMessage(ClientMessage.JoinRoom(created.roomId, "private"))
                guest.receiveMessage<ServerMessage.RoomUpdated>()
                val joined = host.receiveMessage<ServerMessage.RoomUpdated>().game
                host.sendMessage(ClientMessage.CreateRoom("No simultaneous legacy", "", ProtocolGameMode.RANDOM_TARGET))
                assertEquals("ALREADY_IN_ROOM", host.receiveMessage<ServerMessage.Error>().code)
                val resumed = f.connect(f.aPort, f.tokens[0], host.ready.resumeToken)
                try {
                    assertEquals(joined.roomId, resumed.ready.currentGame?.roomId)
                    resumed.sendMessage(ClientMessage.SetReady(created.roomId, true))
                    guest.receiveMessage<ServerMessage.RoomUpdated>()
                    resumed.receiveMessage<ServerMessage.RoomUpdated>()
                    guest.sendMessage(ClientMessage.SetReady(created.roomId, true))
                    assertEquals(created.roomId, resumed.receiveMessage<ServerMessage.GameStarted>().game.roomId)
                    guest.receiveMessage<ServerMessage.GameStarted>()
                    resumed.sendMessage(ClientMessage.LeaveRoom(created.roomId))
                    resumed.receiveMessage<ServerMessage.GameFinished>()
                    guest.receiveMessage<ServerMessage.GameFinished>()
                } finally { resumed.close() }
            } finally { outside.close(); guest.close(); host.close() }
        }
    }


    @Test
    fun `worker owned presence reaches friend and clan viewers before heartbeat and survives reconnect`() = runBlocking {
        presenceFixture { f, observer, _ ->
            val viewer = f.connect(f.aPort, f.tokens[2])
            val host = f.connect(f.aPort, f.tokens[0])
            val guest = f.connect(f.aPort, f.tokens[1])
            try {
                host.sendMessage(ClientMessage.CreateRoom("Social owned", "private", ProtocolGameMode.ORDER))
                val room = host.receiveMessage<ServerMessage.RoomCreated>().game
                assertEquals(f.workerId, f.createDestination(UUID.fromString(room.roomId)))
                viewer.assertSocialPush(mapOf(f.accounts[0].toString() to FriendPresence.IN_ROOM))
                guest.sendMessage(ClientMessage.JoinRoom(room.roomId, "private"))
                guest.receiveMessage<ServerMessage.RoomUpdated>()
                viewer.assertSocialPush(f.accounts.take(2).associate { it.toString() to FriendPresence.IN_ROOM })
                host.sendMessage(ClientMessage.SetReady(room.roomId, true))
                host.receiveMessage<ServerMessage.RoomUpdated>()
                guest.sendMessage(ClientMessage.SetReady(room.roomId, true))
                host.receiveMessage<ServerMessage.GameStarted>()
                guest.receiveMessage<ServerMessage.GameStarted>()
                val playing = f.accounts.take(2).associate { it.toString() to FriendPresence.PLAYING }
                viewer.assertSocialPush(playing)
                viewer.sendMessage(ClientMessage.GetPresence("owned-playing", f.clanId))
                val explicit = viewer.receiveMessage<ServerMessage.PresenceData>()
                playing.forEach { (id, state) -> assertEquals(state, explicit.states[id]) }
                val replacement = f.connect(f.aPort, f.tokens[0], host.ready.resumeToken)
                try {
                    assertEquals(room.roomId, replacement.ready.currentGame?.roomId)
                    host.close()
                    viewer.sendMessage(ClientMessage.GetFriends)
                    assertTrue(viewer.receiveMessage<ServerMessage.FriendsData> {
                        playing.all { (id, state) -> it.social.friends.any { p -> p.userId == id && p.presence == state } }
                    }.social.friends.isNotEmpty())
                    assertEquals(FriendPresence.PLAYING, observer.read(listOf(f.accounts[0].toString()))?.get(f.accounts[0].toString()))
                    replacement.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                    replacement.receiveMessage<ServerMessage.GameFinished>()
                    guest.receiveMessage<ServerMessage.GameFinished>()
                    viewer.sendMessage(ClientMessage.GetClanInfo(f.clanId))
                    val finished = viewer.receiveMessage<ServerMessage.ClanInfoData> { !it.isPresenceRefresh }
                    playing.forEach { (id, state) -> assertEquals(state, finished.clan.members.single { it.userId == id }.presence) }
                    replacement.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                    replacement.receiveMessage<ServerMessage.RoomClosed>()
                    viewer.assertSocialPush(mapOf(f.accounts[0].toString() to FriendPresence.ONLINE,
                        f.accounts[1].toString() to FriendPresence.PLAYING))
                    guest.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                    guest.receiveMessage<ServerMessage.RoomClosed>()
                    viewer.assertSocialPush(f.accounts.take(2).associate { it.toString() to FriendPresence.ONLINE })
                } finally { replacement.close() }
            } finally { guest.close(); host.close(); viewer.close() }
        }
    }

    @Test
    fun `owned social reads do not fabricate online during Redis outage and recover on next commit`() = runBlocking {
        presenceFixture { f, observer, proxy ->
            val viewer = f.connect(f.aPort, f.tokens[2])
            val host = f.connect(f.aPort, f.tokens[0])
            val guest = f.connect(f.aPort, f.tokens[1])
            try {
                host.sendMessage(ClientMessage.CreateRoom("Outage owned", "private", ProtocolGameMode.ORDER))
                val room = host.receiveMessage<ServerMessage.RoomCreated>().game
                guest.sendMessage(ClientMessage.JoinRoom(room.roomId, "private"))
                guest.receiveMessage<ServerMessage.RoomUpdated>()
                viewer.assertSocialPush(f.accounts.take(2).associate { it.toString() to FriendPresence.IN_ROOM })
                proxy.mode = RedisFaultProxy.Mode.DROP
                try {
                    viewer.sendMessage(ClientMessage.GetPresence("outage-owned", f.clanId))
                    assertNull(withTimeoutOrNull(400) { viewer.receiveMessage<ServerMessage.PresenceData>() })
                    viewer.sendMessage(ClientMessage.GetFriends)
                    assertEquals("FRIENDS_PRESENCE_UNAVAILABLE", viewer.receiveMessage<ServerMessage.Error>().code)
                    viewer.sendMessage(ClientMessage.GetClanInfo(f.clanId))
                    assertEquals("CLAN_PRESENCE_UNAVAILABLE", viewer.receiveMessage<ServerMessage.Error>().code)
                    host.sendMessage(ClientMessage.SetReady(room.roomId, true))
                    host.receiveMessage<ServerMessage.RoomUpdated>()
                    guest.sendMessage(ClientMessage.SetReady(room.roomId, true))
                    host.receiveMessage<ServerMessage.GameStarted>()
                    guest.receiveMessage<ServerMessage.GameStarted>()
                    assertTrue(observer.read(listOf(f.accounts[0].toString()))?.get(f.accounts[0].toString()) in
                        setOf(FriendPresence.IN_ROOM, FriendPresence.PLAYING))
                } finally { proxy.release() }
                host.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "after-redis-repair"))
                host.receiveMessage<ServerMessage.GameStateUpdated>()
                viewer.assertSocialPush(f.accounts.take(2).associate { it.toString() to FriendPresence.PLAYING })
            } finally { guest.close(); host.close(); viewer.close() }
        }
    }

    @Test
    fun `ownership SQL outage retains Redis presence instead of publishing legacy online`() = runBlocking {
        presenceFixture { f, observer, _ ->
            val viewer = f.connect(f.aPort, f.tokens[2])
            val host = f.connect(f.aPort, f.tokens[0])
            try {
                host.sendMessage(ClientMessage.CreateRoom("SQL outage owned", "private", ProtocolGameMode.ORDER))
                val room = host.receiveMessage<ServerMessage.RoomCreated>().game
                viewer.assertSocialPush(mapOf(f.accounts[0].toString() to FriendPresence.IN_ROOM))
                fun rename(from: String, to: String) = f.source.connection.use { c ->
                    c.createStatement().use { it.execute("ALTER TABLE $from RENAME TO $to") }
                }
                rename("room_ownership_pilot_memberships", "unavailable_memberships")
                try {
                    assertEquals(OwnedPresenceRead.Unavailable, f.pilot.presence(SocketFence(f.accounts[0], 1)))
                    repeat(3) {
                        f.pilot.onPresenceChanged(setOf(f.accounts[0]))
                        delay(100)
                    }
                    viewer.sendMessage(ClientMessage.GetFriends)
                    val social = viewer.receiveMessage<ServerMessage.FriendsData>()
                    assertEquals(FriendPresence.IN_ROOM, social.social.friends.single { it.userId == f.accounts[0].toString() }.presence)
                    assertEquals(FriendPresence.IN_ROOM, observer.read(listOf(f.accounts[0].toString()))?.get(f.accounts[0].toString()))
                } finally { rename("unavailable_memberships", "room_ownership_pilot_memberships") }
                host.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                host.receiveMessage<ServerMessage.RoomClosed>()
                viewer.assertSocialPush(mapOf(f.accounts[0].toString() to FriendPresence.ONLINE))
            } finally { host.close(); viewer.close() }
        }
    }


    @Test
    fun `lost start reply and worker takeover preserve owned social presence and acknowledged board`() = runBlocking {
        presenceFixture { f, observer, _ ->
            val viewer = f.connect(f.aPort, f.tokens[2])
            val host = f.connect(f.aPort, f.tokens[0])
            val guest = f.connect(f.aPort, f.tokens[1])
            try {
                host.sendMessage(ClientMessage.CreateRoom("Takeover social", "private", ProtocolGameMode.ORDER))
                val room = host.receiveMessage<ServerMessage.RoomCreated>().game
                val roomId = UUID.fromString(room.roomId)
                guest.sendMessage(ClientMessage.JoinRoom(room.roomId, "private"))
                guest.receiveMessage<ServerMessage.RoomUpdated>()
                viewer.assertSocialPush(f.accounts.take(2).associate { it.toString() to FriendPresence.IN_ROOM })
                host.sendMessage(ClientMessage.SetReady(room.roomId, true))
                host.receiveMessage<ServerMessage.RoomUpdated>()
                // The peer commits, but A's command-response delivery path never sees this reply.
                // Only the durable outbox can notify public sockets and social viewers.
                assertIs<OwnedRoomReply.Applied>(f.pilot.onMessage(SocketFence(f.accounts[1], 1),
                    ClientMessage.SetReady(room.roomId, true)))
                host.receiveMessage<ServerMessage.GameStarted>()
                guest.receiveMessage<ServerMessage.GameStarted>()
                viewer.assertSocialPush(f.accounts.take(2).associate { it.toString() to FriendPresence.PLAYING })
                host.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "before-social-takeover"))
                host.receiveMessage<ServerMessage.GameStateUpdated> { it.game.selectedNumbers == listOf(1) }
                guest.receiveMessage<ServerMessage.GameStateUpdated> { it.game.selectedNumbers == listOf(1) }
                val before = assertNotNull(f.rooms.read(roomId))
                f.stopWorker()
                val recovered = withTimeout(8_000) {
                    var current = assertNotNull(f.rooms.read(roomId))
                    while (current.fence.epoch == before.fence.epoch ||
                        current.snapshot.pilotConnectionEpoch != current.fence.epoch ||
                        current.snapshot.pilotSocketGenerations.keys.size != 2) {
                        delay(25)
                        current = assertNotNull(f.rooms.read(roomId))
                    }
                    current
                }
                assertNotEquals(before.fence.instanceId, recovered.fence.instanceId)
                assertEquals(listOf(1), recovered.snapshot.selectedNumbers)
                host.receiveMessage<ServerMessage.GameStateUpdated> { it.game.selectedNumbers == listOf(1) }
                guest.receiveMessage<ServerMessage.GameStateUpdated> { it.game.selectedNumbers == listOf(1) }
                val ids = f.accounts.take(2).map(UUID::toString)
                assertEquals(ids.associateWith { FriendPresence.PLAYING }, observer.read(ids))
                viewer.sendMessage(ClientMessage.GetFriends)
                val friends = viewer.receiveMessage<ServerMessage.FriendsData>()
                ids.forEach { id -> assertEquals(FriendPresence.PLAYING, friends.social.friends.single { it.userId == id }.presence) }
                viewer.sendMessage(ClientMessage.GetClanInfo(f.clanId))
                val clan = viewer.receiveMessage<ServerMessage.ClanInfoData> { !it.isPresenceRefresh }
                ids.forEach { id -> assertEquals(FriendPresence.PLAYING, clan.clan.members.single { it.userId == id }.presence) }
                host.sendMessage(ClientMessage.SelectNumber(room.roomId, 2, "after-social-takeover"))
                assertEquals(listOf(1, 2), host.receiveMessage<ServerMessage.GameStateUpdated>().game.selectedNumbers)
            } finally { guest.close(); host.close(); viewer.close() }
        }
    }


    @Test
    fun `planned global maintenance waits for worker match and held settlement after legacy is empty`() = runBlocking {
        val maintenance = MaintenanceFixture().also { it.initialize() }
        fixture(maintenanceService = maintenance.service) { f ->
            val host = f.connect(f.aPort, f.tokens[0])
            val guest = f.connect(f.aPort, f.tokens[1])
            try {
                host.sendMessage(ClientMessage.CreateRoom("Maintenance worker", "private", ProtocolGameMode.ORDER))
                val room = host.receiveMessage<ServerMessage.RoomCreated>().game
                guest.sendMessage(ClientMessage.JoinRoom(room.roomId, "private"))
                guest.receiveMessage<ServerMessage.RoomUpdated>()
                host.sendMessage(ClientMessage.SetReady(room.roomId, true))
                host.receiveMessage<ServerMessage.RoomUpdated>()
                guest.sendMessage(ClientMessage.SetReady(room.roomId, true))
                host.receiveMessage<ServerMessage.GameStarted>()
                guest.receiveMessage<ServerMessage.GameStarted>()
                maintenance.service.update(maintenance.owner, maintenance.request().copy(warningMinutes = 0, drainMinutes = 1))
                maintenance.now += 120_000
                f.maintenance.observe()
                assertEquals(0, f.engine.activeMaintenanceMatchCount())
                assertFalse(f.maintenance.publicStatus().maintenance)
                val policy = assertNotNull(PostgresOwnedAdmissionRepository(f.source).observe())
                assertEquals(OwnedAdmissionState.STOP_NEW, policy.state)
                val blocked = assertIs<OwnedRoomReply.Retryable>(f.pilot.onMessage(SocketFence(f.accounts[1], 1),
                    ClientMessage.SetReady(room.roomId, true)))
                assertEquals("MAINTENANCE", blocked.publicMessage.code)
                host.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "during-global-drain"))
                host.receiveMessage<ServerMessage.GameStateUpdated>()
                f.source.connection.use { c -> c.createStatement().use {
                    it.execute("CREATE FUNCTION hold_owned_settlement() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''held test settlement''; END;'")
                    it.execute("CREATE TRIGGER hold_owned_settlement BEFORE INSERT ON room_ownership_pilot_settlements FOR EACH ROW EXECUTE FUNCTION hold_owned_settlement()")
                } }
                try {
                    host.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                    host.receiveMessage<ServerMessage.GameFinished>()
                    guest.receiveMessage<ServerMessage.GameFinished>()
                    host.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                    host.receiveMessage<ServerMessage.RoomClosed>()
                    guest.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                    guest.receiveMessage<ServerMessage.RoomClosed>()
                    assertNotNull(f.rooms.read(UUID.fromString(room.roomId))?.snapshot?.pendingCompletedMatch)
                    val held = assertNotNull(f.control("step", policy.generation))
                    assertEquals("pending", held.outcome)
                    assertEquals(OwnedAdmissionState.STOP_NEW, assertNotNull(PostgresOwnedAdmissionRepository(f.source).observe()).state)
                    f.maintenance.observe()
                    assertFalse(f.maintenance.publicStatus().maintenance)
                    assertTrue(maintenance.service.read(maintenance.owner).runtime.activeMatches!! > 0)
                } finally {
                    f.source.connection.use { c -> c.createStatement().use {
                        it.execute("DROP TRIGGER hold_owned_settlement ON room_ownership_pilot_settlements")
                    } }
                }
                withTimeout(8_000) {
                    while (PostgresRoomOwnershipRepository(f.source).durableSnapshot().let {
                        it.remainingOpenRooms != 0 || it.pendingSettlements != 0
                    }) delay(25)
                }
                val finished = assertNotNull(f.control("step", policy.generation))
                assertEquals("completed", finished.outcome)
                f.maintenance.observe()
                assertTrue(f.maintenance.publicStatus().maintenance)
                assertEquals(1, f.source.connection.use { c -> c.createStatement().use {
                    it.executeQuery("SELECT count(*) FROM room_ownership_pilot_settlements").use { rows -> rows.next(); rows.getInt(1) }
                } })
            } finally { guest.close(); host.close() }
        }
    }


    @Test
    fun `late prepared create and signed worker join are fenced by global maintenance`() = runBlocking {
        val maintenance = MaintenanceFixture().also { it.initialize() }
        fixture(maintenanceService = maintenance.service) { f ->
            val host = f.connect(f.aPort, f.tokens[0])
            val guest = f.connect(f.aPort, f.tokens[1])
            try {
                host.sendMessage(ClientMessage.CreateRoom("Waiting drain", "private", ProtocolGameMode.ORDER))
                val room = host.receiveMessage<ServerMessage.RoomCreated>().game
                val before = assertNotNull(f.rooms.read(UUID.fromString(room.roomId)))
                val pending = assertIs<OwnedCreatePreparation.Ready>(f.pilot.prepareOwnedCreate(SocketFence(f.accounts[1], 1),
                    ClientMessage.CreateRoom("Prepared before drain", "private", ProtocolGameMode.ORDER))).request
                maintenance.service.update(maintenance.owner, maintenance.request().copy(warningMinutes = 0))
                f.maintenance.observe()
                assertFalse(f.maintenance.publicStatus().maintenance, "The open WAITING room still counts as owned work")
                assertIs<OwnedCreateResult.AttemptRejected>(f.pilot.createOwned(pending))
                val join = assertIs<OwnedRoomReply.Rejected>(f.forwardPeer(OwnerCommandRequest(
                    room.roomId, before.fence.epoch, f.accounts[1].toString(), 1, OwnerOperation.COMMAND,
                    ClientMessage.JoinRoom(room.roomId, "private"), before.fence.instanceId.toString(), pending.policy)))
                assertEquals("OWNED_ADMISSION_STOPPED", join.publicMessage.code)
                val ready = assertIs<OwnedRoomReply.Rejected>(f.forwardPeer(OwnerCommandRequest(
                    room.roomId, before.fence.epoch, f.accounts[0].toString(), 1, OwnerOperation.COMMAND,
                    ClientMessage.SetReady(room.roomId, true), before.fence.instanceId.toString(), pending.policy)))
                assertEquals("OWNED_ADMISSION_STOPPED", ready.publicMessage.code)
                assertNull(f.rooms.lookupRoom(f.accounts[1]))
                val retained = assertNotNull(f.rooms.read(UUID.fromString(room.roomId)))
                assertNull(retained.snapshot.guest)
                assertEquals(RoomPhase.WAITING, retained.snapshot.phase)
                assertNull(retained.snapshot.startedAtEpochMillis)
                val version = maintenance.service.read(maintenance.owner).persisted.version
                maintenance.service.update(maintenance.owner, maintenance.request().copy(expectedVersion = version, enabled = false))
                f.maintenance.observe()
                assertTrue(f.engine.newIntentAllowed(), "Global maintenance cancellation reopens legacy service")
                assertEquals(OwnedAdmissionState.STOP_NEW, PostgresOwnedAdmissionRepository(f.source).observe()?.state,
                    "Global maintenance cancellation must not implicitly resume ownership rollout")
            } finally { guest.close(); host.close() }
        }
    }

    private suspend fun presenceFixture(block: suspend (Fixture, PresenceStore, RedisFaultProxy) -> Unit) {
        val redis = System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL")
        assumeTrue(!redis.isNullOrBlank(), "Isolated Redis required")
        val config = PresenceConfiguration(requireNotNull(redis), "t" + UUID.randomUUID().toString().replace("-", ""))
        RedisPresenceStore(config).use { observer ->
            RedisFaultProxy(redis).use { proxy ->
                RedisPresenceStore(PresenceConfiguration(proxy.proxyUrl, config.namespace)).use { publisher ->
                    fixture(presenceStore = publisher) { f -> block(f, observer, proxy) }
                }
            }
        }
    }

    private suspend fun Connection.assertSocialPush(expected: Map<String, FriendPresence>) {
        val friends = receiveMessage<ServerMessage.FriendsData> { data ->
            expected.all { (id, state) ->
                data.social.friends.any { it.userId == id && it.presence == state }
            }
        }
        expected.forEach { (id, state) -> assertEquals(state, friends.social.friends.single { it.userId == id }.presence) }
        val clan = receiveMessage<ServerMessage.ClanInfoData> { data ->
            data.isPresenceRefresh && expected.all { (id, state) ->
                data.clan.members.any { it.userId == id && it.presence == state }
            }
        }
        expected.forEach { (id, state) -> assertEquals(state, clan.clan.members.single { it.userId == id }.presence) }
    }

    private suspend fun fixture(rateLimiter: RateLimiter = InMemoryRateLimiter(), presenceStore: PresenceStore? = null, maintenanceService: MaintenanceService? = null, block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        val schema = "owned_production_ws_" + UUID.randomUUID().toString().replace("-", "")
        fun pool(selectedSchema: String?) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 5
            if (selectedSchema != null) this.schema = selectedSchema
        })
        pool(null).use { control ->
            control.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema).load().migrate()
                pool(schema).use { a -> pool(schema).use { b ->
                    val authA = AuthenticationService(PostgresAuthRepository(a), passwordHasher = PasswordHasher(1000))
                    val sessions = listOf("Host", "Guest", "Outside").map { name ->
                        assertIs<AuthResult.Success>(authA.register("$name@example.test", "Password123!", name, "test")).session
                    }
                    a.connection.use { connection ->
                        connection.createStatement().use { it.executeUpdate("UPDATE users SET email_verified_at=clock_timestamp()") }
                        // Exercise legacy routing with an account that legitimately unlocked this mode.
                        val modeExperience = (1 until ProtocolGameMode.RANDOM_TARGET.unlockLevel)
                            .sumOf { PlayerProgression.experienceForNextLevel(it) }
                        connection.prepareStatement("""
                            INSERT INTO player_stats(user_id, experience_points, updated_at)
                            SELECT id, ?, clock_timestamp() FROM users
                            ON CONFLICT(user_id) DO UPDATE SET experience_points = EXCLUDED.experience_points
                        """.trimIndent()).use { statement ->
                            statement.setInt(1, modeExperience)
                            statement.executeUpdate()
                        }
                    }
                    val ids = sessions.map { UUID.fromString(it.userId) }
                    val loopback = InetAddress.getByName("127.0.0.1")
                    ServerSocket(0, 1, loopback).use { ar -> ServerSocket(0, 1, loopback).use { br ->
                        val firstNode = UUID.randomUUID()
                        val secondNode = UUID.randomUUID()
                        val destination = RoomOwnershipRouting(admissionProductionConfig(
                            accounts = ids.take(2), nodeA = firstNode, nodeB = secondNode
                        )).createNode(ids.first().toString())
                        val aId = if (destination == firstNode) secondNode else firstNode
                        val bId = destination
                        val lookup: suspend (UUID) -> StoredActivePlayer? = { id ->
                            sessions.firstOrNull { it.userId == id.toString() }?.let { StoredActivePlayer(it.userId, it.displayName, true) }
                        }
                        fun config(role: RoomOwnershipRuntimeRole, port: Int): RoomOwnershipRuntimeConfig {
                            val validated = admissionProductionConfig(role, ids.take(2), aId, bId)
                            return object : RoomOwnershipRuntimeConfig by validated {
                            override val peerUri = URI("http://127.0.0.1:$port")
                            }
                        }
                        val ca = config(RoomOwnershipRuntimeRole.INGRESS, br.localPort)
                        val cb = config(RoomOwnershipRuntimeRole.OWNED_WORKER, ar.localPort)
                        val authority = PostgresOwnedAdmissionRepository(a)
                        authority.bootstrap(assertNotNull(authority.observe()).generation, OwnedAdmissionBinding.from(ca))
                        val testIdentity = OwnedCandidateIdentity("a".repeat(64), "b".repeat(64), "c".repeat(64))
                        val pa = RoomOwnershipPilot.open(ca, a, lookup, wakeups = emptyFlow(), candidateIdentity = { testIdentity })
                        val pb = RoomOwnershipPilot.open(cb, b, lookup, wakeups = emptyFlow(), candidateIdentity = { testIdentity })
                        val aPort = ar.localPort
                        val bPort = br.localPort
                        val clan = ClanSnapshot(UUID.randomUUID().toString(), "Owned social", "", sessions[0].userId,
                            sessions.map { ClanMemberSnapshot(it.userId, it.displayName, ClanRole.MEMBER, 1_000) }, trophies = 0)
                        val clans = object : ClanRepository by NoOpClanRepository {
                            override suspend fun getClanById(clanId: String) = clan.takeIf { it.id == clanId }
                            override suspend fun getClanByUserId(userId: String) = clan.takeIf { c -> c.members.any { it.userId == userId } }
                            override suspend fun presenceMemberIds(userId: String, clanId: String) =
                                super<ClanRepository>.presenceMemberIds(userId, clanId)
                        }
                        val friends = object : FriendRepository by NoOpFriendRepository {
                            override suspend fun load(userId: String) = StoredFriends(friends = sessions.filter { it.userId != userId }
                                .map { FriendSnapshot(it.userId, it.displayName, it.displayName.uppercase()) })
                        }
                        val engine = GameEngine(playerProfileRepository = PostgresPlayerProfileRepository(a),
                            friendRepository = friends, clanRepository = clans, presenceStore = presenceStore,
                            productionPresenceFailClosed = true)
                        val maintenanceRuntime = MaintenanceRuntime(engine, maintenanceService, ownedWorkCount = pa::maintenanceWorkCount)
                        val sa = embeddedServer(Netty, host = "127.0.0.1", port = aPort) {
                            gameModule(engine = engine, rateLimiter = rateLimiter, authService = authA, roomOwnershipPilot = pa,
                                maintenanceRuntime = maintenanceRuntime)
                        }
                        val sb = embeddedServer(Netty, host = "127.0.0.1", port = bPort) {
                            ownedWorkerModule(pb)
                        }
                        val workerStopped = java.util.concurrent.atomic.AtomicBoolean()
                        fun stopWorker() { if (workerStopped.compareAndSet(false, true)) sb.stop(0, 2000) }
                        try {
                            ar.close(); sa.start(wait = false)
                            br.close(); sb.start(wait = false)
                            HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                                block(Fixture(client, aPort, sessions.map { it.accessToken }, ids,
                                    PostgresRoomOwnershipRepository(a), a, bId, engine, pa, clan.id, ::stopWorker, maintenanceRuntime,
                                    { request -> OwnerPeerTransport(ca.nodeId, ca.peerId, ca.peerUri, ca.peerSecret).forward(request) },
                                    { operation, generation ->
                                        val transport = OwnerPeerTransport(cb.nodeId, cb.peerId, cb.peerUri, cb.peerSecret)
                                        val topology = OwnedAdmissionBinding.from(ca).topologySha256
                                        val status = transport.forwardControl(OwnedControlRequest(UUID.randomUUID().toString(), "status", generation,
                                            candidateSha256 = testIdentity.candidateSha256, topologySha256 = topology))
                                        transport.forwardControl(OwnedControlRequest(UUID.randomUUID().toString(), operation, generation,
                                            expectedProcessId = assertNotNull(status?.processId), candidateSha256 = testIdentity.candidateSha256, topologySha256 = topology)) }))
                            }
                        } finally { stopWorker(); sa.stop(0, 2000) }
                    } }
                } }
            } finally {
                control.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private data class Fixture(val client: HttpClient, val aPort: Int, val tokens: List<String>,
        val accounts: List<UUID>, val rooms: RoomOwnershipRepository, val source: HikariDataSource, val workerId: UUID,
        val engine: GameEngine, val pilot: RoomOwnershipPilot, val clanId: String, val stopWorker: () -> Unit, val maintenance: MaintenanceRuntime, val forwardPeer: suspend (OwnerCommandRequest) -> OwnedRoomReply?, val control: suspend (String, Long) -> OwnedControlResponse?) {
        suspend fun awaitDatabaseWaiter(blocker: Int): Int = withTimeout(2_000) {
            var waiter: Int? = null
            while (waiter == null) {
                waiter = source.connection.use { connection ->
                    connection.prepareStatement("SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))").use { statement ->
                        statement.setInt(1, blocker)
                        statement.executeQuery().use { if (it.next()) it.getInt(1) else null }
                    }
                }
                if (waiter == null) delay(5)
            }
            checkNotNull(waiter)
        }

        fun createDestination(roomId: UUID): UUID? = source.connection.use { connection ->
            connection.prepareStatement("SELECT destination_node_id FROM owned_room_create_operations WHERE room_id = ?").use { statement ->
                statement.setObject(1, roomId)
                statement.executeQuery().use { if (it.next()) it.getObject(1, UUID::class.java) else null }
            }
        }

        suspend fun connect(port: Int, token: String, resume: String? = null): Connection {
            val socket = client.webSocketSession("ws://127.0.0.1:$port/game")
            socket.sendMessage(ClientMessage.ConnectAccount(token, resumeToken = resume))
            val ready = socket.receiveMessage<ServerMessage.SessionReady>()
            socket.receiveMessage<ServerMessage.RoomList>()
            socket.sendMessage(ClientMessage.AcceptContentTerms(CONTENT_TERMS_VERSION))
            socket.receiveMessage<ServerMessage.ContentTermsData>()
            return Connection(socket, ready)
        }
    }

    private class CreateGate : RateLimiter {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        private val delegate = InMemoryRateLimiter()

        override suspend fun consume(bucket: String, key: String, policy: RateLimitPolicy): RateLimitResult {
            val result = delegate.consume(bucket, key, policy)
            if (bucket == RateLimitBuckets.CREATE_ROOM_IP && entered.complete(Unit)) release.await()
            return result
        }
    }

    private data class Connection(val socket: DefaultClientWebSocketSession, val ready: ServerMessage.SessionReady) {
        suspend fun close() = socket.close()
        suspend fun sendMessage(message: ClientMessage) = socket.sendMessage(message)
        suspend inline fun <reified T : ServerMessage> receiveMessage(crossinline accept: (T) -> Boolean = { true }): T = socket.receiveMessage<T>(accept)
    }
    private companion object {
        suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) = send(ProtocolJson.encodeToString<ClientMessage>(message))
        suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.receiveMessage(crossinline accept: (T) -> Boolean = { true }): T {
            val seen = mutableListOf<String>()
            return withTimeoutOrNull(8_000) {
                for (frame in incoming) if (frame is Frame.Text) {
                    val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                    seen.add(message::class.simpleName.orEmpty())
                    if (message is T && accept(message)) return@withTimeoutOrNull message
                    if (message is ServerMessage.Error) error("Unexpected ${message.code} before ${T::class.simpleName}")
                }
                error("Socket closed before ${T::class.simpleName}")
            } ?: error("Expected ${T::class.simpleName}; received $seen")
        }
    }
}
