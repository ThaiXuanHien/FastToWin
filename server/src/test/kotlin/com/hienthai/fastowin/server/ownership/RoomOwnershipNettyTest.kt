package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
import com.hienthai.fastowin.server.AuthResult
import com.hienthai.fastowin.server.AuthenticationService
import com.hienthai.fastowin.server.GameEngine
import com.hienthai.fastowin.server.InMemoryRateLimiter
import com.hienthai.fastowin.server.PasswordHasher
import com.hienthai.fastowin.server.PostgresAuthRepository
import com.hienthai.fastowin.server.RateLimitBuckets
import com.hienthai.fastowin.server.RateLimitPolicy
import com.hienthai.fastowin.server.RateLimiter
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.gameModule
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RoomOwnershipNettyTest {
    @Test
    fun `authenticated sockets on different backends share one committed game`() = runBlocking {
        fixture { f ->
            val host = f.connect(f.aPort, f.hostToken)
            val guest = f.connect(f.bPort, f.guestToken)
            try {
                val create = ClientMessage.CreateRoom("Pilot room", PASSWORD, ProtocolGameMode.ORDER)
                host.sendMessage(create.copy(password = ""))
                assertEquals("INVALID_PILOT_ROOM", host.receiveMessage<ServerMessage.Error>().code)
                host.sendMessage(create)
                val created = host.receiveMessage<ServerMessage.RoomCreated>().game
                guest.sendMessage(ClientMessage.ListRooms)
                val listing = guest.receiveMessage<ServerMessage.RoomList>().rooms.single()
                assertEquals(created.roomId, listing.id)
                assertEquals(true, listing.requiresPassword)
                val roomId = listing.id
                guest.sendMessage(ClientMessage.JoinRoom(roomId, PASSWORD))
                val hostJoin = host.receiveMessage<ServerMessage.RoomUpdated>().game
                val guestJoin = guest.receiveMessage<ServerMessage.RoomUpdated>().game
                assertEquals(hostJoin, guestJoin)
                val committedJoin = assertNotNull(f.rooms.read(UUID.fromString(roomId)))
                assertEquals(committedJoin.snapshot.sequence, guestJoin.sequence)
                val revisions = assertIs<RoomEventBatch.Contiguous>(
                    f.rooms.readEvents(committedJoin.fence.roomId, committedJoin.revision - 1, 1)
                ).revisions
                assertEquals(committedJoin.revision, revisions.single().revision)
                assertEquals(ServerMessage.RoomUpdated(guestJoin), revisions.single().events.single().message)
                assertEquals(1, f.joinAdmissions.get())

                val replacement = f.connect(f.bPort, f.hostToken, host.ready.resumeToken)
                try {
                    assertEquals(guestJoin, replacement.ready.currentGame)
                    host.sendMessage(ClientMessage.SetReady(roomId, true))
                    assertEquals(SESSION_REPLACED_CLOSE_REASON, withTimeout(5_000) { host.socket.closeReason.await() }?.message)
                    replacement.socket.sendMessage(ClientMessage.SetReady(roomId, true))
                    val ready = guest.receiveMessage<ServerMessage.RoomUpdated>().game
                    assertEquals(1, ready.players.count { it.isReady })
                    replacement.socket.receiveMessage<ServerMessage.RoomUpdated>()
                    guest.sendMessage(ClientMessage.SetReady(roomId, true))
                    val started = guest.receiveMessage<ServerMessage.GameStarted>().game
                    assertEquals(started, replacement.socket.receiveMessage<ServerMessage.GameStarted>().game)
                    val tap = ClientMessage.SelectNumber(roomId, 1, "one")
                    replacement.socket.sendMessage(tap)
                    val updated = guest.receiveMessage<ServerMessage.GameStateUpdated>()
                    assertEquals(listOf(1), updated.game.selectedNumbers)
                    assertEquals(updated, replacement.socket.receiveMessage<ServerMessage.GameStateUpdated>())
                    // The test wake-up sources sent nothing for these writes. Duplicate wake-ups must not redeliver them.
                    repeat(3) { f.wakeups.emit(UUID.fromString(roomId)) }
                    guest.sendMessage(tap.copy(number = 2, requestId = "two"))
                    assertEquals(listOf(1, 2), guest.receiveMessage<ServerMessage.GameStateUpdated>().game.selectedNumbers)
                    assertEquals(listOf(1, 2), replacement.socket.receiveMessage<ServerMessage.GameStateUpdated>().game.selectedNumbers)
                } finally {
                    replacement.socket.close()
                }
            } finally {
                guest.close()
                host.close()
            }
        }
    }

    @Test
    fun `pilot websocket rejects accounts outside its fixture allowlist`() = runBlocking {
        fixture { f ->
            val outside = f.client.webSocketSession("ws://127.0.0.1:${f.aPort}/game")
            try {
                outside.sendMessage(ClientMessage.ConnectAccount(f.otherToken))
                assertEquals("PILOT_ACCOUNT_REQUIRED", outside.receiveMessage<ServerMessage.Error>().code)
            } finally {
                outside.close()
            }
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL")
        if (System.getenv("FASTTOWIN_TEST_ROOM_OWNERSHIP") == "true") requireNotNull(url)
        assumeTrue(url != null, "Isolated PostgreSQL fixture required")
        require(url!!.matches(Regex("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_]+")))
        val schema = "owned_netty_test_" + UUID.randomUUID().toString().replace("-", "")
        fun pool(selectedSchema: String?) = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 5
            if (selectedSchema != null) this.schema = selectedSchema
        })
        pool(null).use { control ->
            control.connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            try {
                Flyway.configure().dataSource(control).schemas(schema).defaultSchema(schema).load().migrate()
                pool(schema).use { a -> pool(schema).use { b -> runServers(a, b, block) } }
            } finally {
                control.connection.use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    private suspend fun runServers(a: HikariDataSource, b: HikariDataSource, block: suspend (Fixture) -> Unit) {
        val authA = AuthenticationService(PostgresAuthRepository(a), passwordHasher = PasswordHasher(1000))
        val authB = AuthenticationService(PostgresAuthRepository(b), passwordHasher = PasswordHasher(1000))
        val accounts = listOf("Host", "Guest", "Other").map { name ->
            assertIs<AuthResult.Success>(authA.register("$name@example.test", "FixturePassword123!", name, "test")).session
        }
        val fixtureId = UUID.randomUUID()
        a.connection.use { c ->
            c.createStatement().use { it.executeUpdate("UPDATE users SET email_verified_at = clock_timestamp()") }
            c.prepareStatement("INSERT INTO room_ownership_pilot_fixture (fixture_id) VALUES (?)").use {
                it.setObject(1, fixtureId)
                it.executeUpdate()
            }
        }
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 1, loopback).use { aPortReservation ->
            ServerSocket(0, 1, loopback).use { bPortReservation ->
                val aPort = aPortReservation.localPort
                val bPort = bPortReservation.localPort
                val aId = UUID.randomUUID()
                val bId = UUID.randomUUID()
                val allowed = accounts.take(2).map { UUID.fromString(it.userId) }.toSet()
                val playerLookup: suspend (UUID) -> StoredActivePlayer? = { id ->
                    accounts.firstOrNull { it.userId == id.toString() }?.let { StoredActivePlayer(it.userId, it.displayName, true) }
                }
                val secret = ByteArray(32) { (it + 1).toByte() }
                val wakeups = MutableSharedFlow<UUID>(extraBufferCapacity = 8)
                val pilotA = RoomOwnershipPilot.open(
                    RoomOwnershipPilotConfig(aId, bId, URI("http://127.0.0.1:$bPort"), secret, fixtureId, allowed),
                    a, playerLookup, wakeups = wakeups
                )
                val pilotB = RoomOwnershipPilot.open(
                    RoomOwnershipPilotConfig(bId, aId, URI("http://127.0.0.1:$aPort"), secret, fixtureId, allowed),
                    b, playerLookup, wakeups = wakeups
                )
                val joinAdmissions = AtomicInteger()
                val delegate = InMemoryRateLimiter()
                val limiter = object : RateLimiter by delegate {
                    override suspend fun consume(bucket: String, key: String, policy: RateLimitPolicy) =
                        delegate.consume(bucket, key, policy).also {
                            if (bucket == RateLimitBuckets.JOIN_ROOM_PLAYER) joinAdmissions.incrementAndGet()
                        }
                }
                val serverA = embeddedServer(Netty, host = "127.0.0.1", port = aPort) {
                    gameModule(engine = GameEngine(), authService = authA, rateLimiter = limiter, roomOwnershipPilot = pilotA)
                }
                val serverB = embeddedServer(Netty, host = "127.0.0.1", port = bPort) {
                    gameModule(engine = GameEngine(), authService = authB, rateLimiter = limiter, roomOwnershipPilot = pilotB)
                }
                try {
                    aPortReservation.close()
                    serverA.start(wait = false)
                    bPortReservation.close()
                    serverB.start(wait = false)
                    HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                        block(Fixture(client, aPort, bPort, accounts[0].accessToken, accounts[1].accessToken,
                            accounts[2].accessToken, wakeups, PostgresRoomOwnershipRepository(a), joinAdmissions))
                    }
                } finally {
                    serverB.stop(0, 2_000)
                    serverA.stop(0, 2_000)
                }
            }
        }
    }

    private data class Fixture(
        val client: HttpClient,
        val aPort: Int,
        val bPort: Int,
        val hostToken: String,
        val guestToken: String,
        val otherToken: String,
        val wakeups: MutableSharedFlow<UUID>,
        val rooms: RoomOwnershipRepository,
        val joinAdmissions: AtomicInteger
    ) {
        suspend fun connect(port: Int, token: String, resumeToken: String? = null): Connection {
            val socket = client.webSocketSession("ws://127.0.0.1:$port/game")
            socket.sendMessage(ClientMessage.ConnectAccount(token, resumeToken = resumeToken))
            val ready = socket.receiveMessage<ServerMessage.SessionReady>()
            socket.receiveMessage<ServerMessage.RoomList>()
            return Connection(socket, ready)
        }
    }

    private data class Connection(val socket: DefaultClientWebSocketSession, val ready: ServerMessage.SessionReady) {
        suspend fun close() = socket.close()
        suspend fun sendMessage(message: ClientMessage) = socket.sendMessage(message)
        suspend inline fun <reified T : ServerMessage> receiveMessage(): T = socket.receiveMessage<T>()
    }

    private companion object {
        const val PASSWORD = "private fixture"

        suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) =
            send(ProtocolJson.encodeToString<ClientMessage>(message))

        suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.receiveMessage(): T =
            withTimeout(5_000) {
                for (frame in incoming) if (frame is Frame.Text) {
                    val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                    if (message is T) return@withTimeout message
                    if (message is ServerMessage.Error) error("Unexpected pilot error ${message.code} before ${T::class.simpleName}")
                }
                error("Socket closed before ${T::class.simpleName}")
            }
    }
}
