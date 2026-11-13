package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.presence.PresenceConfiguration
import com.hienthai.fastowin.server.presence.RedisPresenceStore
import com.hienthai.fastowin.server.ratelimit.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlin.test.*

class RedisNettyAcceptanceTest {
    private fun fixture(): String {
        val url = System.getenv("FASTTOWIN_TEST_REDIS_URL")
        assumeTrue(!url.isNullOrBlank(), "Isolated Redis fixture required")
        return requireNotNull(url)
    }
    private fun settings(url: String, namespace: String) = mapOf(
        "FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to url,
        "FASTTOWIN_RATE_LIMIT_NAMESPACE" to namespace)
    private fun namespace() = "t" + UUID.randomUUID().toString().replace("-", "")
    private suspend fun AuthenticationService.registerVerified(email: String, password: String, name: String): AuthSessionResponse {
        val session = assertIs<AuthResult.Success>(register(email, password, name, "android")).session
        val verification = assertIs<AccountActionResult.Success>(requestEmailVerification(session.accessToken))
        assertIs<AccountActionResult.Success>(confirmEmailVerification(session.accessToken, requireNotNull(verification.emailVerificationCode)))
        return session.copy(emailVerified = true)
    }
    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.receiveMessage(): T = withTimeout(5000) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
            if (message is T) return@withTimeout message
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
    private suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) = send(ProtocolJson.encodeToString(message))
    private fun app(
        runtime: RateLimitRuntime,
        policies: ServerRateLimitPolicies = ServerRateLimitPolicies(),
        auth: AuthenticationService = AuthenticationService(InMemoryAuthRepository()),
        engine: GameEngine = GameEngine()
    ) =
        embeddedServer(Netty, host = "127.0.0.1", port = 0) { gameModule(engine = engine, rateLimiter = runtime, rateLimitPolicies = policies, authService = auth,
            adminService = AdminService(InMemoryAdminRepository(), AdminConfiguration("http://localhost", false), passwordHasher = PasswordHasher(iterations = 1000), rateLimiter = runtime)) }.start()
    private suspend fun port(server: EmbeddedServer<*, *>) = server.engine.resolvedConnectors().single().port
    private fun client() = HttpClient(OkHttp) { install(WebSockets) }

    @Test fun `two Netty applications share login and connection quotas with namespace isolation`() = runBlocking {
        val url = fixture(); val ns = namespace(); val policies = ServerRateLimitPolicies().copy(
            loginPerIp = RateLimitPolicy(1, 60_000), websocketConnectPerIp = RateLimitPolicy(1, 60_000))
        openRateLimitRuntime(settings(url, ns)).use { first -> openRateLimitRuntime(settings(url, ns)).use { second ->
            openRateLimitRuntime(settings(url, namespace())).use { isolated ->
                val a = app(first, policies); val b = app(second, policies)
                val client = client()
                try {
                    suspend fun login(port: Int) = client.post("http://127.0.0.1:$port/auth/login") {
                        contentType(ContentType.Application.Json); setBody("""{"email":"quota@example.com","password":"valid-test-password"}""")
                    }
                    assertEquals(HttpStatusCode.Unauthorized, login(port(a)).status)
                    val denied = login(port(b)); assertEquals(HttpStatusCode.TooManyRequests, denied.status)
                    assertNotNull(denied.headers[HttpHeaders.RetryAfter])
                    assertTrue(first.consume("login-ip", "namespace-isolation", policies.loginPerIp).allowed)
                    assertFalse(first.consume("login-ip", "namespace-isolation", policies.loginPerIp).allowed)
                    assertTrue(isolated.consume("login-ip", "namespace-isolation", policies.loginPerIp).allowed)
                    val x = client.webSocketSession("ws://127.0.0.1:${port(a)}/game")
                    val y = client.webSocketSession("ws://127.0.0.1:${port(b)}/game")
                    try {
                        x.sendMessage(ClientMessage.ConnectGuest("First")); x.receiveMessage<ServerMessage.SessionReady>()
                        y.sendMessage(ClientMessage.ConnectGuest("Second")); assertEquals("RATE_LIMITED", y.receiveMessage<ServerMessage.Error>().code)
                    } finally { x.close(); y.close() }
                    val metrics = client.get("http://127.0.0.1:${port(b)}/internal/metrics").bodyAsText()
                    assertContains(metrics, "fasttowin_rate_limit_shared_decisions_total")
                    assertFalse(metrics.contains("quota@example.com")); assertFalse(metrics.contains(ns)); assertFalse(metrics.contains(url))
                } finally { client.close(); a.stop(); b.stop() }
            }
        } }
    }

    @Test fun `outage fails login and room actions but existing socket remains usable and recovers`() = runBlocking {
        val url = fixture(); val ns = namespace()
        openRateLimitRuntime(settings(url, namespace())).close() // Warm client resources before the bounded proxy startup.
        RedisFaultProxy(url).use { proxy -> openRateLimitRuntime(settings(proxy.proxyUrl, ns)).use { runtime ->
            val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(iterations = 1000))
            val host = auth.registerVerified("outage-host@example.com", "strong-password-123", "Host")
            val guest = auth.registerVerified("outage-guest@example.com", "strong-password-123", "Guest")
            val server = app(runtime, auth = auth); val client = client(); val p = port(server)
            try {
                val socket = client.webSocketSession("ws://127.0.0.1:$p/game")
                val peer = client.webSocketSession("ws://127.0.0.1:$p/game")
                try {
                    socket.sendMessage(ClientMessage.ConnectAccount(host.accessToken)); socket.receiveMessage<ServerMessage.SessionReady>(); socket.receiveMessage<ServerMessage.RoomList>()
                    peer.sendMessage(ClientMessage.ConnectAccount(guest.accessToken)); peer.receiveMessage<ServerMessage.SessionReady>(); peer.receiveMessage<ServerMessage.RoomList>()
                    socket.sendMessage(ClientMessage.CreateRoom("Active", "", ProtocolGameMode.ORDER))
                    val room = socket.receiveMessage<ServerMessage.RoomCreated>().game
                    peer.sendMessage(ClientMessage.JoinRoom(room.roomId, "")); socket.receiveMessage<ServerMessage.RoomUpdated>(); peer.receiveMessage<ServerMessage.RoomUpdated>()
                    socket.sendMessage(ClientMessage.SetReady(room.roomId, true)); socket.receiveMessage<ServerMessage.RoomUpdated>(); peer.receiveMessage<ServerMessage.RoomUpdated>()
                    peer.sendMessage(ClientMessage.SetReady(room.roomId, true)); socket.receiveMessage<ServerMessage.GameStarted>(); peer.receiveMessage<ServerMessage.GameStarted>()
                    proxy.mode = RedisFaultProxy.Mode.HOLD; proxy.disconnect(); delay(100)
                    val response = client.post("http://127.0.0.1:$p/auth/login") {
                        contentType(ContentType.Application.Json); setBody("""{"email":"outage@example.com","password":"valid-test-password"}""")
                    }
                    assertEquals(HttpStatusCode.ServiceUnavailable, response.status); assertContains(response.bodyAsText(), "RATE_LIMIT_UNAVAILABLE")
                    val metrics = client.get("http://127.0.0.1:$p/internal/metrics").bodyAsText()
                    assertContains(metrics, "fasttowin_rate_limit_shared_decisions_total{outcome=\"unavailable\"}")
                    assertFalse(metrics.contains("outage@example.com")); assertFalse(metrics.contains(ns)); assertFalse(metrics.contains(proxy.proxyUrl))
                    val admin = client.post("http://127.0.0.1:$p/admin/api/login") {
                        header(HttpHeaders.Origin, "http://localhost"); contentType(ContentType.Application.Json)
                        setBody("""{"email":"outage-admin@example.com","password":"valid-test-password"}""")
                    }
                    assertEquals(HttpStatusCode.ServiceUnavailable, admin.status); assertContains(admin.bodyAsText(), "RATE_LIMIT_UNAVAILABLE")
                    val newcomer = client.webSocketSession("ws://127.0.0.1:$p/game")
                    try {
                        newcomer.sendMessage(ClientMessage.ConnectGuest("Newcomer"))
                        assertEquals("RATE_LIMIT_UNAVAILABLE", newcomer.receiveMessage<ServerMessage.Error>().code)
                        assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, withTimeout(5000) { newcomer.closeReason.await() }?.code)
                    } finally { newcomer.close() }
                    socket.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "outage-selection"))
                    socket.receiveMessage<ServerMessage.GameStateUpdated>(); peer.receiveMessage<ServerMessage.GameStateUpdated>()
                    socket.sendMessage(ClientMessage.CreateRoom("Room", "", ProtocolGameMode.ORDER))
                    assertEquals("RATE_LIMIT_UNAVAILABLE", socket.receiveMessage<ServerMessage.Error>().code)
                    socket.sendMessage(ClientMessage.JoinRoom("missing", ""))
                    assertEquals("RATE_LIMIT_UNAVAILABLE", socket.receiveMessage<ServerMessage.Error>().code)
                    socket.sendMessage(ClientMessage.ListRooms); socket.receiveMessage<ServerMessage.RoomList>()
                    proxy.release()
                    var recovered = false
                    repeat(40) {
                        if (!recovered) {
                            delay(100)
                            socket.sendMessage(ClientMessage.JoinRoom("missing", ""))
                            recovered = socket.receiveMessage<ServerMessage.Error>().code == "ALREADY_IN_ROOM"
                        }
                    }
                    assertTrue(recovered)
                } finally { socket.close(); peer.close() }
            } finally { client.close(); server.stop() }
        } }
    }

    @Test fun `match finishes and persists one reward while Redis is unavailable`() = runBlocking {
        val rateUrl = fixture()
        val presenceUrl = System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL")
        val databaseUrl = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!presenceUrl.isNullOrBlank() && !databaseUrl.isNullOrBlank(), "Redis presence and PostgreSQL fixtures required")
        require(Regex("""jdbc:postgresql://127\.0\.0\.1:\d+/admin_test""").matches(requireNotNull(databaseUrl)))
        val schemaName = "redis_finish_${UUID.randomUUID().toString().replace("-", "")}"
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = databaseUrl
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            schema = schemaName
        }).use { dataSource ->
            val migrations = Flyway.configure().dataSource(dataSource)
                .schemas(schemaName).defaultSchema(schemaName).cleanDisabled(false).load()
            try {
                migrations.migrate()
                verifyMatchSettlement(rateUrl, requireNotNull(presenceUrl), dataSource)
            } finally {
                migrations.clean()
            }
        }
    }

    private suspend fun verifyMatchSettlement(rateUrl: String, presenceUrl: String, dataSource: HikariDataSource) {
        val auth = AuthenticationService(PostgresAuthRepository(dataSource), PasswordHasher(1000))
        val host = auth.registerVerified("finish-host@example.invalid", "fixture-password-123", "Host")
        val guest = auth.registerVerified("finish-guest@example.invalid", "fixture-password-123", "Guest")
        val profiles = PostgresPlayerProfileRepository(dataSource)
        RedisFaultProxy(rateUrl).use { rateProxy ->
            RedisFaultProxy(presenceUrl).use { presenceProxy ->
                openRateLimitRuntime(settings(rateProxy.proxyUrl, namespace())).use { runtime ->
                    RedisPresenceStore(PresenceConfiguration(presenceProxy.proxyUrl, namespace())).use { presence ->
                        val engine = GameEngine(
                            matchResultRepository = PostgresMatchResultRepository(dataSource),
                            playerProfileRepository = profiles,
                            presenceStore = presence,
                            productionPresenceFailClosed = true
                        )
                        val server = app(runtime, auth = auth, engine = engine)
                        try {
                            client().use { client ->
                                val baseUrl = "http://127.0.0.1:${port(server)}"
                                val gameUrl = "ws://127.0.0.1:${port(server)}/game"
                                client.webSocketSession(gameUrl).useMatchSocket { hostSocket ->
                                    client.webSocketSession(gameUrl).useMatchSocket { guestSocket ->
                                        hostSocket.sendMessage(ClientMessage.ConnectAccount(host.accessToken))
                                        hostSocket.receiveMessage<ServerMessage.SessionReady>()
                                        guestSocket.sendMessage(ClientMessage.ConnectAccount(guest.accessToken))
                                        guestSocket.receiveMessage<ServerMessage.SessionReady>()
                                        hostSocket.sendMessage(ClientMessage.CreateRoom("Redis outage", "", ProtocolGameMode.ORDER))
                                        val room = hostSocket.receiveMessage<ServerMessage.RoomCreated>().game
                                        guestSocket.sendMessage(ClientMessage.JoinRoom(room.roomId, ""))
                                        guestSocket.receiveMessage<ServerMessage.RoomUpdated>()
                                        hostSocket.sendMessage(ClientMessage.SetReady(room.roomId, true))
                                        hostSocket.receiveMessage<ServerMessage.RoomUpdated>()
                                        guestSocket.sendMessage(ClientMessage.SetReady(room.roomId, true))
                                        val started = hostSocket.receiveMessage<ServerMessage.GameStarted>().game
                                        guestSocket.receiveMessage<ServerMessage.GameStarted>()
                                        withTimeout(5_000) {
                                            while (presence.read(listOf(host.userId))?.get(host.userId) != FriendPresence.PLAYING) {
                                                delay(20)
                                            }
                                        }
                                        assertNull(profiles.findMatchDetail(guest.userId, started.matchId))

                                        for (proxy in listOf(rateProxy, presenceProxy)) {
                                            proxy.mode = RedisFaultProxy.Mode.DROP
                                            proxy.disconnect()
                                        }
                                        val login = client.post("$baseUrl/auth/login") {
                                            contentType(ContentType.Application.Json)
                                            setBody("""{"email":"unavailable@example.invalid","password":"fixture-password-123"}""")
                                        }
                                        assertEquals(HttpStatusCode.ServiceUnavailable, login.status)
                                        assertContains(login.bodyAsText(), "RATE_LIMIT_UNAVAILABLE")
                                        assertNull(presence.read(listOf(host.userId)))

                                        hostSocket.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                                        val finished = hostSocket.receiveMessage<ServerMessage.GameFinished>().game
                                        assertEquals(finished, guestSocket.receiveMessage<ServerMessage.GameFinished>().game)
                                        assertEquals(started.matchId, finished.matchId)
                                        assertEquals(RoomPhase.FINISHED, finished.phase)
                                        assertEquals(guest.userId, finished.winnerPlayerId)
                                        assertEquals(MatchForfeitSnapshot(host.userId, MatchForfeitReason.VOLUNTARY_LEAVE), finished.forfeit)

                                        // Repeating LeaveRoom after settlement exits the result screen.
                                        hostSocket.sendMessage(ClientMessage.LeaveRoom(room.roomId))
                                        assertEquals("RESULTS_LEFT", hostSocket.receiveMessage<ServerMessage.RoomClosed>().code)
                                        assertEquals(MatchHistoryOutcome.WIN, profiles.findMatchDetail(guest.userId, finished.matchId)?.summary?.outcome)
                                        assertEquals(MatchHistoryOutcome.LOSS, profiles.findMatchDetail(host.userId, finished.matchId)?.summary?.outcome)
                                        val winnerReward = profiles.loadWalletHistory(guest.userId)
                                            .single { it.sourceType == "MATCH" && it.sourceId == finished.matchId }
                                        assertEquals(80, winnerReward.goldDelta)
                                        assertEquals(24, winnerReward.xpDelta)
                                        assertTrue(profiles.loadWalletHistory(host.userId)
                                            .none { it.sourceType == "MATCH" && it.sourceId == finished.matchId })
                                    }
                                }
                            }
                        } finally {
                            server.stop()
                        }
                    }
                }
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.useMatchSocket(block: suspend (DefaultClientWebSocketSession) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    @Test fun `two applications share admin login and room entry limits`() = runBlocking {
        val url = fixture(); val ns = namespace()
        val policies = ServerRateLimitPolicies().copy(createRoomPerIp = RateLimitPolicy(1, 60_000), joinRoomPerIp = RateLimitPolicy(1, 60_000))
        val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(iterations = 1000))
        val one = auth.registerVerified("netty-one@example.com", "strong-password-123", "One")
        val two = auth.registerVerified("netty-two@example.com", "strong-password-123", "Two")
        openRateLimitRuntime(settings(url, ns)).use { first -> openRateLimitRuntime(settings(url, ns)).use { second ->
            val a = app(first, policies, auth); val b = app(second, policies, auth); val client = client()
            try {
                suspend fun admin(p: Int) = client.post("http://127.0.0.1:$p/admin/api/login") {
                    header(HttpHeaders.Origin, "http://localhost"); contentType(ContentType.Application.Json)
                    setBody("""{"email":"admin-quota@example.com","password":"valid-test-password"}""")
                }
                repeat(8) { assertEquals(HttpStatusCode.Unauthorized, admin(port(if (it % 2 == 0) a else b)).status) }
                assertEquals(HttpStatusCode.TooManyRequests, admin(port(b)).status)
                val x = client.webSocketSession("ws://127.0.0.1:${port(a)}/game")
                val y = client.webSocketSession("ws://127.0.0.1:${port(b)}/game")
                try {
                    x.sendMessage(ClientMessage.ConnectAccount(one.accessToken)); x.receiveMessage<ServerMessage.SessionReady>(); x.receiveMessage<ServerMessage.RoomList>()
                    y.sendMessage(ClientMessage.ConnectAccount(two.accessToken)); y.receiveMessage<ServerMessage.SessionReady>(); y.receiveMessage<ServerMessage.RoomList>()
                    x.sendMessage(ClientMessage.JoinRoom("missing", "")); assertEquals("ROOM_NOT_FOUND", x.receiveMessage<ServerMessage.Error>().code)
                    y.sendMessage(ClientMessage.JoinRoom("missing", "")); assertEquals("RATE_LIMITED", y.receiveMessage<ServerMessage.Error>().code)
                    x.sendMessage(ClientMessage.CreateRoom("First", "", ProtocolGameMode.ORDER)); x.receiveMessage<ServerMessage.RoomCreated>()
                    y.sendMessage(ClientMessage.CreateRoom("Second", "", ProtocolGameMode.ORDER)); assertEquals("RATE_LIMITED", y.receiveMessage<ServerMessage.Error>().code)
                } finally { x.close(); y.close() }
            } finally { client.close(); a.stop(); b.stop() }
        } }
    }

    @Test fun `Ktor startup preserves the Redis logging guard`() = runBlocking {
        val url = fixture()
        openRateLimitRuntime(settings(url, namespace())).use { runtime ->
            val logger = LoggerFactory.getLogger("io.lettuce.test") as Logger
            val oldLevel = logger.level
            val capture = ListAppender<ILoggingEvent>().apply { start() }
            logger.level = Level.DEBUG; logger.addAppender(capture)
            val server = app(runtime)
            try {
                logger.debug("redis-secret-marker")
                runtime.consume("login-ip", "sensitive-identifier", RateLimitPolicy(1, 60_000))
                assertFalse(capture.list.any { it.formattedMessage.contains("redis-secret-marker") })
            } finally { server.stop(); logger.detachAppender(capture); logger.level = oldLevel; capture.stop() }
        }
    }
}
