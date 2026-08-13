package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameWebSocketTest {
    @Test
    fun `revoked account session cannot continue using an open websocket`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val authSession = assertIs<AuthResult.Success>(
            authService.register(
                email = "revoked-socket@example.com",
                password = "strong-password-123",
                displayName = "Revoked player",
                devicePlatform = "android"
            )
        ).session
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val socket = webSocketClient.webSocketSession("/game")
        try {
            socket.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken))
            socket.receiveMessage<ServerMessage.SessionReady>()
            socket.receiveMessage<ServerMessage.RoomList>()
            assertIs<AccountActionResult.Success>(
                authService.changePassword(
                    authSession.accessToken,
                    "strong-password-123",
                    "new-strong-password-456"
                )
            )

            socket.sendMessage(ClientMessage.GetProfile)
            assertEquals("INVALID_ACCESS_TOKEN", socket.receiveMessage<ServerMessage.Error>().code)
        } finally {
            socket.close()
        }
    }

    @Test
    fun `registered account authenticates websocket with access token`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val authSession = assertIs<AuthResult.Success>(
            authService.register(
                email = "socket@example.com",
                password = "strong-password-123",
                displayName = "Người chơi tài khoản",
                devicePlatform = "android"
            )
        ).session
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }

        val authenticated = webSocketClient.webSocketSession("/game")
        try {
            authenticated.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken))
            val ready = authenticated.receiveMessage<ServerMessage.SessionReady>()
            assertEquals(authSession.userId, ready.playerId)
            assertEquals(null, ready.resumeToken)
            authenticated.receiveMessage<ServerMessage.RoomList>()
            authenticated.sendMessage(ClientMessage.GetProfile)
            assertEquals(
                "Người chơi tài khoản",
                authenticated.receiveMessage<ServerMessage.ProfileData>().profile.displayName
            )
        } finally {
            authenticated.close()
        }

        authService.logout(authSession.refreshToken)
        val revoked = webSocketClient.webSocketSession("/game")
        try {
            revoked.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken))
            val error = revoked.receiveMessage<ServerMessage.Error>()
            assertEquals("INVALID_ACCESS_TOKEN", error.code)
        } finally {
            revoked.close()
        }
    }

    @Test
    fun `server broadcasts time attack finish without another player action`() = testApplication {
        application { gameModule(GameEngine(timeAttackMillis = 50L)) }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")
        try {
            host.sendMessage(ClientMessage.ConnectGuest("Hiền"))
            guest.sendMessage(ClientMessage.ConnectGuest("Hiếu"))
            host.receiveMessage<ServerMessage.SessionReady>()
            guest.receiveMessage<ServerMessage.SessionReady>()
            host.receiveMessage<ServerMessage.RoomList>()
            guest.receiveMessage<ServerMessage.RoomList>()

            host.sendMessage(ClientMessage.CreateRoom("Phòng timer", PASSWORD, ProtocolGameMode.TIME_ATTACK))
            val room = host.receiveMessage<ServerMessage.RoomCreated>().game
            guest.sendMessage(ClientMessage.JoinRoom(room.roomId, PASSWORD))
            host.receiveMessage<ServerMessage.GameStarted>()
            guest.receiveMessage<ServerMessage.GameStarted>()

            val hostFinished = host.receiveMessage<ServerMessage.GameFinished>().game
            val guestFinished = guest.receiveMessage<ServerMessage.GameFinished>().game
            assertEquals(com.hienthai.fastowin.protocol.RoomPhase.FINISHED, hostFinished.phase)
            assertEquals(hostFinished, guestFinished)
        } finally {
            host.close()
            guest.close()
        }
    }

    @Test
    fun `two websocket clients can play and host can resume snapshot`() = testApplication {
        application { gameModule(GameEngine()) }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")

        try {
            host.sendMessage(ClientMessage.ConnectGuest("Hiền"))
            guest.sendMessage(ClientMessage.ConnectGuest("Hiếu"))
            val hostSession = host.receiveMessage<ServerMessage.SessionReady>()
            val guestSession = guest.receiveMessage<ServerMessage.SessionReady>()
            host.receiveMessage<ServerMessage.RoomList>()
            guest.receiveMessage<ServerMessage.RoomList>()

            host.sendMessage(ClientMessage.GetProfile)
            val profile = host.receiveMessage<ServerMessage.ProfileData>().profile
            assertEquals("Hiền", profile.displayName)
            assertTrue(profile.playerCode.isNotBlank())

            host.sendMessage(ClientMessage.GetLeaderboard)
            val leaderboard = host.receiveMessage<ServerMessage.LeaderboardData>().leaderboard
            assertTrue(leaderboard.topPlayers.isEmpty())

            host.sendMessage(
                ClientMessage.CreateRoom("Phòng E2E", PASSWORD, ProtocolGameMode.ORDER)
            )
            val room = host.receiveMessage<ServerMessage.RoomCreated>().game
            assertEquals("Phòng E2E", room.roomName)

            guest.sendMessage(ClientMessage.JoinRoom(room.roomId, PASSWORD))
            val hostStarted = host.receiveMessage<ServerMessage.GameStarted>().game
            val guestStarted = guest.receiveMessage<ServerMessage.GameStarted>().game
            assertEquals(hostStarted.numbers, guestStarted.numbers)
            assertEquals(50, hostStarted.numbers.size)

            coroutineScope {
                launch {
                    host.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "host-e2e-request"))
                }
                launch {
                    guest.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "guest-e2e-request"))
                }
            }

            val hostUpdate = host.receiveMessage<ServerMessage.GameStateUpdated>().game
            val guestUpdate = guest.receiveMessage<ServerMessage.GameStateUpdated>().game
            assertEquals(2, hostUpdate.currentTarget)
            assertEquals(hostUpdate, guestUpdate)
            assertEquals(10, hostUpdate.players.sumOf { it.score })

            host.close()
            delay(100)

            val resumedHost = webSocketClient.webSocketSession("/game")
            try {
                resumedHost.sendMessage(
                    ClientMessage.ConnectGuest("Hiền", resumeToken = hostSession.resumeToken)
                )
                val resumed = resumedHost.receiveMessage<ServerMessage.SessionReady>()
                assertEquals(hostSession.playerId, resumed.playerId)
                val snapshot = assertNotNull(resumed.currentGame)
                assertEquals(room.roomId, snapshot.roomId)
                assertEquals(2, snapshot.currentTarget)
                assertTrue(snapshot.selectedNumbers.contains(1))
                assertEquals(guestSession.playerId, snapshot.players.first { it.id != resumed.playerId }.id)
            } finally {
                resumedHost.close()
            }
        } finally {
            host.close()
            guest.close()
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) {
        send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message)))
    }

    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.receiveMessage(): T {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
            if (message is T) return message
        }
    }

    private companion object {
        const val PASSWORD = "123456"
    }
}
