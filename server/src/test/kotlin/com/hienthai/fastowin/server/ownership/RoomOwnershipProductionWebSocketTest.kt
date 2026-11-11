package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
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

    private suspend fun fixture(rateLimiter: RateLimiter = InMemoryRateLimiter(), block: suspend (Fixture) -> Unit) {
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
                        val pa = RoomOwnershipPilot.open(ca, a, lookup, wakeups = emptyFlow())
                        val pb = RoomOwnershipPilot.open(cb, b, lookup, wakeups = emptyFlow())
                        val aPort = ar.localPort
                        val bPort = br.localPort
                        val engine = GameEngine(playerProfileRepository = PostgresPlayerProfileRepository(a))
                        val sa = embeddedServer(Netty, host = "127.0.0.1", port = aPort) {
                            gameModule(engine = engine, rateLimiter = rateLimiter, authService = authA, roomOwnershipPilot = pa)
                        }
                        val sb = embeddedServer(Netty, host = "127.0.0.1", port = bPort) {
                            ownedWorkerModule(pb)
                        }
                        try {
                            ar.close(); sa.start(wait = false)
                            br.close(); sb.start(wait = false)
                            HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                                block(Fixture(client, aPort, sessions.map { it.accessToken }, ids,
                                    PostgresRoomOwnershipRepository(a), a, bId, engine, pa))
                            }
                        } finally { sb.stop(0, 2000); sa.stop(0, 2000) }
                    } }
                } }
            } finally {
                control.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private data class Fixture(val client: HttpClient, val aPort: Int, val tokens: List<String>,
        val accounts: List<UUID>, val rooms: RoomOwnershipRepository, val source: HikariDataSource, val workerId: UUID,
        val engine: GameEngine, val pilot: RoomOwnershipPilot) {
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
        suspend inline fun <reified T : ServerMessage> receiveMessage(): T = socket.receiveMessage<T>()
    }
    private companion object {
        suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) = send(ProtocolJson.encodeToString<ClientMessage>(message))
        suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.receiveMessage(): T = withTimeout(8_000) {
            for (frame in incoming) if (frame is Frame.Text) {
                val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                if (message is T) return@withTimeout message
                if (message is ServerMessage.Error) error("Unexpected ${message.code} before ${T::class.simpleName}")
            }
            error("Socket closed before ${T::class.simpleName}")
        }
    }
}
