package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.ratelimit.*
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
    private fun app(runtime: RateLimitRuntime, policies: ServerRateLimitPolicies = ServerRateLimitPolicies(), auth: AuthenticationService = AuthenticationService(InMemoryAuthRepository())) =
        embeddedServer(Netty, host = "127.0.0.1", port = 0) { gameModule(rateLimiter = runtime, rateLimitPolicies = policies, authService = auth,
            adminService = AdminService(InMemoryAdminRepository(), AdminConfiguration("http://localhost", false), passwordHasher = PasswordHasher(iterations = 1000), rateLimiter = runtime)) }.start()
    private suspend fun port(server: EmbeddedServer<*, *>) = server.engine.resolvedConnectors().single().port
    private fun client() = HttpClient(OkHttp) { install(WebSockets) }

    @Test fun `two Netty applications share login and connection quotas with namespace isolation`() = runBlocking {
        val url = fixture(); val ns = namespace(); val policies = ServerRateLimitPolicies().copy(
            loginPerIp = RateLimitPolicy(1, 60_000), websocketConnectPerIp = RateLimitPolicy(1, 60_000))
        openRateLimitRuntime(settings(url, ns)).use { first -> openRateLimitRuntime(settings(url, ns)).use { second ->
            openRateLimitRuntime(settings(url, namespace())).use { isolated ->
                val a = app(first, policies); val b = app(second, policies); val c = app(isolated, policies)
                val client = client()
                try {
                    suspend fun login(port: Int) = client.post("http://127.0.0.1:$port/auth/login") {
                        contentType(ContentType.Application.Json); setBody("""{"email":"quota@example.com","password":"valid-test-password"}""")
                    }
                    assertEquals(HttpStatusCode.Unauthorized, login(port(a)).status)
                    val denied = login(port(b)); assertEquals(HttpStatusCode.TooManyRequests, denied.status)
                    assertNotNull(denied.headers[HttpHeaders.RetryAfter])
                    assertEquals(HttpStatusCode.Unauthorized, login(port(c)).status)
                    val x = client.webSocketSession("ws://127.0.0.1:${port(a)}/game")
                    val y = client.webSocketSession("ws://127.0.0.1:${port(b)}/game")
                    try {
                        x.sendMessage(ClientMessage.ConnectGuest("First")); x.receiveMessage<ServerMessage.SessionReady>()
                        y.sendMessage(ClientMessage.ConnectGuest("Second")); assertEquals("RATE_LIMITED", y.receiveMessage<ServerMessage.Error>().code)
                    } finally { x.close(); y.close() }
                    val metrics = client.get("http://127.0.0.1:${port(b)}/internal/metrics").bodyAsText()
                    assertContains(metrics, "fasttowin_rate_limit_shared_decisions_total")
                    assertFalse(metrics.contains("quota@example.com")); assertFalse(metrics.contains(ns)); assertFalse(metrics.contains(url))
                } finally { client.close(); a.stop(); b.stop(); c.stop() }
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
