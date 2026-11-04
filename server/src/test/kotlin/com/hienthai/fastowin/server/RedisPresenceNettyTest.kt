package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedisPresenceNettyTest {
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
        val alice = auth.verifiedAccount("alice")
        val bob = auth.verifiedAccount("bob")
        val charlie = auth.verifiedAccount("charlie")
        val clan = ClanSnapshot(
            id = UUID.randomUUID().toString(), name = "Presence clan", description = "", ownerId = alice.userId,
            members = listOf(alice, bob).map { ClanMemberSnapshot(it.userId, "Member", ClanRole.MEMBER, 1_000) },
            trophies = 0
        )
        val clans = object : ClanRepository by NoOpClanRepository {
            override suspend fun getClanById(clanId: String) = clan.takeIf { it.id == clanId }
            override suspend fun getClanByUserId(userId: String) = clan.takeIf { it.members.any { member -> member.userId == userId } }
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

    private suspend fun AuthenticationService.verifiedAccount(name: String): AuthSessionResponse {
        val session = assertIs<AuthResult.Success>(register("$name@example.com", "valid-test-password", name, "android")).session
        val verification = assertIs<AccountActionResult.Success>(requestEmailVerification(session.accessToken))
        assertIs<AccountActionResult.Success>(confirmEmailVerification(session.accessToken, requireNotNull(verification.emailVerificationCode)))
        return session
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
