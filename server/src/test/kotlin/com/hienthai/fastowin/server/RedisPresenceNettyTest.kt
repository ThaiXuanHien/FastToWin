package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.presence.PresenceConfiguration
import com.hienthai.fastowin.server.presence.PresenceStore
import com.hienthai.fastowin.server.presence.RedisPresenceStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class RedisPresenceNettyTest {
    @Test
    fun `a stalled presence write cannot delay GameStarted`(): Unit = runBlocking {
        val writes = MemoryPresenceStore()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = object : PresenceStore by writes {
            override suspend fun renew(playerId: String, connectionId: UUID, presence: FriendPresence): Boolean {
                if (presence == FriendPresence.PLAYING) {
                    started.complete(Unit)
                    release.await()
                }
                return writes.renew(playerId, connectionId, presence)
            }
        }
        val auth = AuthenticationService(InMemoryAuthRepository())
        val host = auth.verifiedPresenceAccount("timed-host")
        val guest = auth.verifiedPresenceAccount("timed-guest")
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            gameModule(engine = GameEngine(presenceStore = store), authService = auth)
        }.start()
        try {
            HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                val url = "ws://127.0.0.1:${server.engine.resolvedConnectors().single().port}/game"
                val first = client.webSocketSession(url)
                val second = client.webSocketSession(url)
                try {
                    first.sendMessage(ClientMessage.ConnectAccount(host.accessToken))
                    first.receiveMessage<ServerMessage.SessionReady>()
                    second.sendMessage(ClientMessage.ConnectAccount(guest.accessToken))
                    second.receiveMessage<ServerMessage.SessionReady>()
                    first.sendMessage(ClientMessage.CreateRoom("Timed room", "", ProtocolGameMode.ORDER))
                    val roomId = first.receiveMessage<ServerMessage.RoomCreated>().game.roomId
                    second.sendMessage(ClientMessage.JoinRoom(roomId, ""))
                    second.receiveMessage<ServerMessage.RoomUpdated>()
                    first.sendMessage(ClientMessage.SetReady(roomId, true))
                    second.sendMessage(ClientMessage.SetReady(roomId, true))
                    withTimeout(5_000) { started.await() }
                    first.receiveMessage<ServerMessage.GameStarted>()
                    second.receiveMessage<ServerMessage.GameStarted>()
                } finally {
                    release.complete(Unit)
                    first.close()
                    second.close()
                }
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `idle presence survives token rotation and is revoked after logout`() = runBlocking {
        val store = MemoryPresenceStore()
        val auth = AuthenticationService(InMemoryAuthRepository())
        val account = auth.verifiedPresenceAccount("idle-account")
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            gameModule(engine = GameEngine(presenceStore = store), authService = auth)
        }.start()
        try {
            HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                val socket = client.webSocketSession("ws://127.0.0.1:${server.engine.resolvedConnectors().single().port}/game")
                try {
                    socket.sendMessage(ClientMessage.ConnectAccount(account.accessToken))
                    socket.receiveMessage<ServerMessage.SessionReady>()
                    val rotated = assertIs<AuthResult.Success>(auth.refresh(account.refreshToken)).session
                    assertNull(withTimeoutOrNull(21_000) { socket.closeReason.await() })
                    assertEquals(FriendPresence.ONLINE, store.read(listOf(account.userId))[account.userId])
                    auth.logout(rotated.refreshToken)
                    assertEquals("Account session revoked", withTimeout(22_000) { socket.closeReason.await() }?.message)
                    withTimeout(5_000) {
                        while (store.read(listOf(account.userId))[account.userId] != FriendPresence.OFFLINE) delay(20)
                    }
                } finally {
                    socket.close()
                }
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `surviving backend updates clan presence after force killing a peer process`() = runBlocking {
        assumeTrue(System.getenv("FASTTOWIN_TEST_PRESENCE_FULL_TTL") == "true", "Opt-in crash fixture required")
        val classpathFile = Path.of(checkNotNull(System.getenv("FASTTOWIN_TEST_PRESENCE_CLASSPATH_FILE")))
        val namespace = "t" + UUID.randomUUID().toString().replace("-", "")
        val config = PresenceConfiguration(checkNotNull(System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL")), namespace)
        val launcher = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val args = Files.createTempFile("ftw-presence-crash-", ".args")
        try {
            val classpath = Files.readString(classpathFile).replace('\\', '/')
            Files.writeString(args, "-cp\n\"$classpath\"\n${RedisPresenceCrashFixture::class.java.name}\n$namespace\n")
            val child = ProcessBuilder(launcher.toString(), "@$args").redirectErrorStream(true).start()
            try {
                val output = child.inputStream.bufferedReader()
                val port = withTimeout(15_000) {
                    var readyPort: Int? = null
                    while (readyPort == null) {
                        check(child.isAlive) { "Crash fixture exited before becoming ready" }
                        if (output.ready()) readyPort = output.readLine()?.takeIf { it.startsWith("PRESENCE_CRASH_PORT=") }
                            ?.substringAfter('=')?.toIntOrNull()
                        else delay(25)
                    }
                    readyPort
                }
                RedisPresenceStore(config).use { survivor -> verifyHardStop(child, port, survivor) }
            } finally {
                child.destroyForcibly()
                check(child.waitFor(5, TimeUnit.SECONDS)) { "Crash fixture process cleanup timed out" }
                child.inputStream.close()
            }
        } finally {
            Files.deleteIfExists(args)
        }
        Unit
    }

    private suspend fun verifyHardStop(child: Process, port: Int, survivor: PresenceStore) {
        HttpClient(OkHttp) { install(WebSockets) }.use { client ->
            val login = client.post("http://127.0.0.1:$port/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(ProtocolJson.encodeToString(LoginRequest("crash-peer@example.com", "valid-test-password", "android")))
            }
            assertEquals(HttpStatusCode.OK, login.status)
            val bob = ProtocolJson.decodeFromString<AuthSessionResponse>(login.bodyAsText())
            val auth = AuthenticationService(InMemoryAuthRepository())
            val alice = auth.verifiedPresenceAccount("survivor")
            val clan = ClanSnapshot(
                id = UUID.randomUUID().toString(), name = "Crash fixture", description = "", ownerId = alice.userId,
                members = listOf(alice, bob).map { ClanMemberSnapshot(it.userId, "Member", ClanRole.MEMBER, 1_000) },
                trophies = 0
            )
            val clans = object : ClanRepository by NoOpClanRepository {
                override suspend fun getClanByUserId(userId: String) = clan.takeIf { userId == alice.userId }
                override suspend fun presenceMemberIds(userId: String, clanId: String) =
                    super<ClanRepository>.presenceMemberIds(userId, clanId)
            }
            val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                gameModule(engine = GameEngine(clanRepository = clans, presenceStore = survivor), authService = auth)
            }.start()
            try {
                val observing = client.webSocketSession("ws://127.0.0.1:${server.engine.resolvedConnectors().single().port}/game")
                val crashing = client.webSocketSession("ws://127.0.0.1:$port/game")
                try {
                    observing.sendMessage(ClientMessage.ConnectAccount(alice.accessToken))
                    observing.receiveMessage<ServerMessage.SessionReady>()
                    crashing.sendMessage(ClientMessage.ConnectAccount(bob.accessToken))
                    crashing.receiveMessage<ServerMessage.SessionReady>()
                    child.destroyForcibly()
                    check(child.waitFor(5, TimeUnit.SECONDS)) { "Crash fixture did not terminate" }
                    val killedAt = TimeSource.Monotonic.markNow()
                    var firstOffline: Long? = null
                    for (sample in 0..5) {
                        delay((sample * 15_000L - killedAt.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
                        val request = ClientMessage.GetPresence("crash-$sample", clan.id)
                        observing.sendMessage(request)
                        val states = observing.receiveMessage<ServerMessage.PresenceData> { it.requestId == request.requestId }.states
                        assertEquals(FriendPresence.ONLINE, states[alice.userId])
                        if (states[bob.userId] == FriendPresence.OFFLINE) {
                            firstOffline = killedAt.elapsedNow().inWholeMilliseconds
                            break
                        }
                        assertEquals(FriendPresence.ONLINE, states[bob.userId])
                    }
                    checkNotNull(firstOffline) { "Peer did not go offline after the lease expired" }
                    assertTrue(firstOffline in 55_000..77_000, "Unexpected WebSocket expiry window: $firstOffline ms")
                    println("PRESENCE_CRASH offlineAfterMs=$firstOffline survivor=ONLINE refreshMs=15000")
                } finally {
                    crashing.close()
                    observing.close()
                }
            } finally {
                server.stop()
            }
        }
    }

    @Test
    fun `socket lifecycle publishes and revokes across two servers`() = runBlocking {
        val store = MemoryPresenceStore()
        verifyTwoServers(store, store)
    }

    @Test
    fun `two servers with independent Redis clients see the same socket lifecycle`() = runBlocking {
        val url = System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL")
        assumeTrue(!url.isNullOrBlank(), "Isolated Redis presence fixture required")
        val config = PresenceConfiguration(requireNotNull(url), "t" + UUID.randomUUID().toString().replace("-", ""))
        RedisPresenceStore(config).use { first ->
            RedisPresenceStore(config).use { second -> verifyTwoServers(first, second) }
        }
    }

    private suspend fun verifyTwoServers(first: PresenceStore, second: PresenceStore) {
        val auth = AuthenticationService(InMemoryAuthRepository())
        val alice = auth.verifiedPresenceAccount("alice")
        val bob = auth.verifiedPresenceAccount("bob")
        val charlie = auth.verifiedPresenceAccount("charlie")
        val clan = ClanSnapshot(
            id = UUID.randomUUID().toString(), name = "Presence clan", description = "", ownerId = alice.userId,
            members = listOf(alice, bob).map { ClanMemberSnapshot(it.userId, "Member", ClanRole.MEMBER, 1_000) },
            trophies = 0
        )
        val clans = object : ClanRepository by NoOpClanRepository {
            override suspend fun getClanById(clanId: String) = clan.takeIf { it.id == clanId }
            override suspend fun getClanByUserId(userId: String) = clan.takeIf { it.members.any { member -> member.userId == userId } }
            override suspend fun presenceMemberIds(userId: String, clanId: String) =
                super<ClanRepository>.presenceMemberIds(userId, clanId)
        }
        val friends = object : FriendRepository by NoOpFriendRepository {
            override suspend fun load(userId: String) = StoredFriends(friends = listOf(
                if (userId == alice.userId) FriendSnapshot(bob.userId, "Bob", "BOB")
                else FriendSnapshot(alice.userId, "Alice", "ALICE")
            ))
        }
        fun app(store: PresenceStore) = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            gameModule(engine = GameEngine(friendRepository = friends, clanRepository = clans, presenceStore = store), authService = auth)
        }.start()

        val a = app(first)
        val b = app(second)
        val client = HttpClient(OkHttp) { install(WebSockets) }
        try {
            val bobSocket = client.webSocketSession("ws://127.0.0.1:${a.engine.resolvedConnectors().single().port}/game")
            val aliceSocket = client.webSocketSession("ws://127.0.0.1:${b.engine.resolvedConnectors().single().port}/game")
            val charlieSocket = client.webSocketSession("ws://127.0.0.1:${a.engine.resolvedConnectors().single().port}/game")
            try {
                bobSocket.sendMessage(ClientMessage.ConnectAccount(bob.accessToken))
                assertTrue(bobSocket.receiveMessage<ServerMessage.SessionReady>().presenceRefreshSupported)
                aliceSocket.sendMessage(ClientMessage.ConnectAccount(alice.accessToken))
                assertTrue(aliceSocket.receiveMessage<ServerMessage.SessionReady>().presenceRefreshSupported)
                aliceSocket.sendMessage(ClientMessage.GetPresence("friends-1"))
                val initial = aliceSocket.receiveMessage<ServerMessage.PresenceData>()
                assertEquals(mapOf(bob.userId to FriendPresence.ONLINE), initial.states)
                aliceSocket.sendMessage(ClientMessage.GetPresence("too-soon"))
                assertNull(withTimeoutOrNull(300) { aliceSocket.receiveMessage<ServerMessage.PresenceData>() })

                suspend fun assertVisible(expected: FriendPresence) {
                    withTimeout(5_000) {
                        while (second.read(listOf(bob.userId))?.get(bob.userId) != expected) delay(20)
                    }
                    aliceSocket.sendMessage(ClientMessage.GetFriends)
                    val snapshot = aliceSocket.receiveMessage<ServerMessage.FriendsData> {
                        it.social.friends.single().presence == expected
                    }
                    assertEquals(expected, snapshot.social.friends.single().presence)
                    aliceSocket.sendMessage(ClientMessage.GetClanInfo(clan.id))
                    val roster = aliceSocket.receiveMessage<ServerMessage.ClanInfoData> { !it.isPresenceRefresh }
                    assertEquals(expected, roster.clan.members.single { it.userId == bob.userId }.presence)
                }

                bobSocket.sendMessage(ClientMessage.CreateRoom("Presence room", "", ProtocolGameMode.ORDER))
                val roomId = bobSocket.receiveMessage<ServerMessage.RoomCreated>().game.roomId
                assertVisible(FriendPresence.IN_ROOM)
                charlieSocket.sendMessage(ClientMessage.ConnectAccount(charlie.accessToken))
                charlieSocket.receiveMessage<ServerMessage.SessionReady>()
                charlieSocket.sendMessage(ClientMessage.JoinRoom(roomId, ""))
                charlieSocket.receiveMessage<ServerMessage.RoomUpdated>()
                bobSocket.sendMessage(ClientMessage.SetReady(roomId, true))
                charlieSocket.sendMessage(ClientMessage.SetReady(roomId, true))
                assertVisible(FriendPresence.PLAYING)
                val bobReplacement = client.webSocketSession("ws://127.0.0.1:${b.engine.resolvedConnectors().single().port}/game")
                try {
                    bobReplacement.sendMessage(ClientMessage.ConnectAccount(bob.accessToken))
                    bobReplacement.receiveMessage<ServerMessage.SessionReady>()
                    bobSocket.close()
                    assertVisible(FriendPresence.ONLINE)
                } finally {
                    bobReplacement.close()
                }
                assertVisible(FriendPresence.OFFLINE)
            } finally {
                bobSocket.close()
                aliceSocket.close()
                charlieSocket.close()
            }
        } finally {
            client.close()
            a.stop()
            b.stop()
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) =
        send(Frame.Text(ProtocolJson.encodeToString(message)))

    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.receiveMessage(
        crossinline accept: (T) -> Boolean = { true }
    ): T = withTimeout(5_000) {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
            if (message is T && accept(message)) return@withTimeout message
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private class MemoryPresenceStore : PresenceStore {
        private val sockets = ConcurrentHashMap<Pair<String, UUID>, FriendPresence>()
        override suspend fun renew(playerId: String, connectionId: UUID, presence: FriendPresence): Boolean {
            sockets[playerId to connectionId] = presence
            return true
        }
        override suspend fun revoke(playerId: String, connectionId: UUID): Boolean {
            sockets.remove(playerId to connectionId)
            return true
        }
        override suspend fun read(playerIds: Collection<String>) = playerIds.associateWith { id ->
            sockets.entries.filter { it.key.first == id }.maxOfOrNull { it.value } ?: FriendPresence.OFFLINE
        }
        override fun close() = Unit
    }
}

private suspend fun AuthenticationService.verifiedPresenceAccount(name: String): AuthSessionResponse {
    val session = assertIs<AuthResult.Success>(register("$name@example.com", "valid-test-password", name, "android")).session
    val verification = assertIs<AccountActionResult.Success>(requestEmailVerification(session.accessToken))
    assertIs<AccountActionResult.Success>(confirmEmailVerification(session.accessToken, requireNotNull(verification.emailVerificationCode)))
    return session
}

object RedisPresenceCrashFixture {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val namespace = args.single()
        val url = checkNotNull(System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL"))
        val auth = AuthenticationService(InMemoryAuthRepository())
        auth.verifiedPresenceAccount("crash-peer")
        RedisPresenceStore(PresenceConfiguration(url, namespace)).use { store ->
            val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                gameModule(engine = GameEngine(presenceStore = store), authService = auth)
            }.start()
            try {
                println("PRESENCE_CRASH_PORT=${server.engine.resolvedConnectors().single().port}")
                CountDownLatch(1).await()
            } finally {
                server.stop()
            }
        }
    }
}
