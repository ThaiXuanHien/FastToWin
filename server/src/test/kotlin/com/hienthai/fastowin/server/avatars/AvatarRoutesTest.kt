package com.hienthai.fastowin.server.avatars

import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.protocol.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlin.test.*

class AvatarRoutesTest {
    @Test fun `avatar route validates UUID and returns cache and nosniff for every outcome`() = testApplication {
        avatarDatabase { source, player ->
            val repository = PostgresAvatarRepository(source)
            val objects = MemoryAvatarObjects()
            val service = AvatarService(repository, objects, objectWrites = true)
            assertTrue(service.upload(player, imageBase64()))
            application { gameModule(GameEngine(avatarService = service)) }
            for ((path, expected) in listOf("invalid" to HttpStatusCode.BadRequest, java.util.UUID.randomUUID().toString() to HttpStatusCode.NotFound,
                player to HttpStatusCode.OK)) {
                val response = client.get("/api/avatar/$path")
                assertEquals(expected, response.status)
                assertEquals("no-store, max-age=0", response.headers[HttpHeaders.CacheControl])
                assertEquals("nosniff", response.headers["X-Content-Type-Options"])
                if (expected == HttpStatusCode.OK) {
                    assertEquals("image/jpeg", response.headers[HttpHeaders.ContentType])
                    assertTrue(response.bodyAsBytes().size <= 45 * 1024)
                }
            }
            objects.failReads = true
            val unavailable = client.get("/api/avatar/$player")
            assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
            assertEquals("nosniff", unavailable.headers["X-Content-Type-Options"])
            assertFalse(unavailable.bodyAsText().contains("provider"))
        }
    }

    @Test fun `avatar websocket requires a session uses its player and rate limits before storage`() = testApplication {
        val auth = AuthenticationService(InMemoryAuthRepository(), passwordHasher = PasswordHasher(1000))
        val owner = assertIs<AuthResult.Success>(auth.register("avatar-owner@example.com", "Strong-password-123", "Owner", "test")).session
        val verification = assertIs<AccountActionResult.Success>(auth.requestEmailVerification(owner.accessToken))
        assertIs<AccountActionResult.Success>(auth.confirmEmailVerification(owner.accessToken, assertNotNull(verification.emailVerificationCode)))
        val writes = mutableMapOf<String, String>()
        val repository = object : AvatarRepository by ProfileAvatarRepository(NoOpPlayerProfileRepository) {
            override suspend fun current(playerId: String) = if (playerId == owner.userId) AvatarSnapshot(0, writes[playerId], null) else null
            override suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String): Boolean {
                writes[playerId] = base64; return true
            }
        }
        application { gameModule(GameEngine(avatarService = AvatarService(repository)), auth,
            rateLimiter = InMemoryRateLimiter(nowMillis = { 1000 }),
            rateLimitPolicies = ServerRateLimitPolicies(avatarPerPlayer = RateLimitPolicy(1, 600_000), avatarPerIp = RateLimitPolicy(10, 600_000))) }
        val socket = createClient { install(WebSockets) }.webSocketSession("/game")
        suspend fun send(message: ClientMessage) { socket.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message))) }
        suspend fun next(): ServerMessage = withTimeout(3000) {
            while (true) {
                val frame = socket.incoming.receive()
                if (frame is Frame.Text) return@withTimeout ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
            }
            error("unreachable")
        }
        try {
            send(ClientMessage.UpdateAvatar(imageBase64()))
            assertEquals("AUTH_REQUIRED", assertIs<ServerMessage.Error>(next()).code)
            send(ClientMessage.ConnectAccount(owner.accessToken))
            while (next() !is ServerMessage.RoomList) { }
            send(ClientMessage.UpdateAvatar(imageBase64()))
            send(ClientMessage.ListRooms)
            var avatarAcknowledgement: ServerMessage.ProfileData? = null
            var response = next()
            while (response !is ServerMessage.RoomList) {
                if (response is ServerMessage.ProfileData) avatarAcknowledgement = response
                response = next()
            }
            assertFalse(assertNotNull(avatarAcknowledgement).isBackgroundRefresh)
            assertEquals(setOf(owner.userId), writes.keys)
            val previous = writes[owner.userId]
            send(ClientMessage.UpdateAvatar(imageBase64(50)))
            assertEquals("RATE_LIMITED", assertIs<ServerMessage.Error>(next()).code)
            assertEquals(previous, writes[owner.userId])
            send(ClientMessage.ListRooms)
            assertIs<ServerMessage.RoomList>(next())
        } finally { socket.close() }
    }

    @Test fun `avatar IP guard applies across sessions before image normalization`() = testApplication {
        var writes = 0
        val repository = object : AvatarRepository by ProfileAvatarRepository(NoOpPlayerProfileRepository) {
            override suspend fun current(playerId: String) = AvatarSnapshot(0, null, null)
            override suspend fun writeLegacy(playerId: String, expectedRevision: Long, base64: String): Boolean { writes++; return true }
        }
        application { gameModule(GameEngine(avatarService = AvatarService(repository)),
            rateLimiter = InMemoryRateLimiter(nowMillis = { 1000 }),
            rateLimitPolicies = ServerRateLimitPolicies(avatarPerPlayer = RateLimitPolicy(3, 600_000), avatarPerIp = RateLimitPolicy(1, 600_000))) }
        val web = createClient { install(WebSockets) }
        suspend fun consume(socket: DefaultClientWebSocketSession, type: String): ServerMessage = withTimeout(3000) {
            while (true) {
                val frame = socket.incoming.receive() as? Frame.Text ?: continue
                val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                if ((type == "room" && message is ServerMessage.RoomList) || message is ServerMessage.Error) return@withTimeout message
            }
            error("unreachable")
        }
        val sockets = listOf(web.webSocketSession("/game"), web.webSocketSession("/game"))
        try {
            for ((index, socket) in sockets.withIndex()) {
                socket.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.ConnectGuest("Avatar $index"))))
                consume(socket, "room")
                socket.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.UpdateAvatar(imageBase64()))))
                if (index == 0) {
                    socket.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(ClientMessage.ListRooms)))
                    assertIs<ServerMessage.RoomList>(consume(socket, "room"))
                } else assertEquals("RATE_LIMITED", assertIs<ServerMessage.Error>(consume(socket, "error")).code)
            }
            assertEquals(1, writes)
        } finally { sockets.forEach { it.close() } }
    }
}
