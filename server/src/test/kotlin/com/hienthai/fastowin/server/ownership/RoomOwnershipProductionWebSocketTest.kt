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

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
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
                        val aId = UUID.randomUUID()
                        val bId = UUID.randomUUID()
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
                        val sa = embeddedServer(Netty, host = "127.0.0.1", port = aPort) {
                            gameModule(engine = GameEngine(playerProfileRepository = PostgresPlayerProfileRepository(a)),
                                authService = authA, roomOwnershipPilot = pa)
                        }
                        val sb = embeddedServer(Netty, host = "127.0.0.1", port = bPort) {
                            ownedWorkerModule(pb)
                        }
                        try {
                            ar.close(); sa.start(wait = false)
                            br.close(); sb.start(wait = false)
                            HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                                block(Fixture(client, aPort, bPort, sessions.map { it.accessToken }, ids, PostgresRoomOwnershipRepository(a)))
                            }
                        } finally { sb.stop(0, 2000); sa.stop(0, 2000) }
                    } }
                } }
            } finally {
                control.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private data class Fixture(val client: HttpClient, val aPort: Int, val bPort: Int, val tokens: List<String>,
        val accounts: List<UUID>, val rooms: RoomOwnershipRepository) {
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
