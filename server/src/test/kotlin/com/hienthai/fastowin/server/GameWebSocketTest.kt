package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.flywaydb.core.Flyway
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameWebSocketTest {
    @Test
    fun `websocket closes after player message burst exceeds limit`() = testApplication {
        val policies = ServerRateLimitPolicies().copy(
            websocketMessagesPerPlayer = RateLimitPolicy(capacity = 2, refillWindowMillis = 1_000L)
        )
        application {
            gameModule(
                rateLimiter = InMemoryRateLimiter(nowMillis = { 1_000L }),
                rateLimitPolicies = policies
            )
        }
        val webSocketClient = createClient { install(WebSockets) }
        val socket = webSocketClient.webSocketSession("/game")
        try {
            socket.sendMessage(ClientMessage.ConnectGuest("Burst player"))
            socket.receiveMessage<ServerMessage.SessionReady>()
            socket.receiveMessage<ServerMessage.RoomList>()

            repeat(2) {
                socket.sendMessage(ClientMessage.ListRooms)
                socket.receiveMessage<ServerMessage.RoomList>()
            }
            socket.sendMessage(ClientMessage.ListRooms)
            val limited = socket.receiveMessage<ServerMessage.Error>()
            assertEquals("RATE_LIMITED", limited.code)
            assertTrue(limited.message.contains("1 giây"))
        } finally {
            socket.close()
        }
    }

    @Test
    fun `room and selection actions are rate limited before game engine work`() = testApplication {
        var now = 1_000L
        val policies = ServerRateLimitPolicies().copy(
            createRoomPerPlayer = RateLimitPolicy(capacity = 1, refillWindowMillis = 1_000L),
            createRoomPerIp = RateLimitPolicy(capacity = 1, refillWindowMillis = 1_000L),
            joinRoomPerPlayer = RateLimitPolicy(capacity = 2, refillWindowMillis = 1_000L),
            joinRoomPerIpAndRoom = RateLimitPolicy(capacity = 2, refillWindowMillis = 1_000L),
            selectNumberPerPlayer = RateLimitPolicy(capacity = 1, refillWindowMillis = 1_000L)
        )
        application {
            gameModule(
                rateLimiter = InMemoryRateLimiter(nowMillis = { now }),
                rateLimitPolicies = policies
            )
        }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")
        try {
            host.sendMessage(ClientMessage.ConnectGuest("Limited host"))
            guest.sendMessage(ClientMessage.ConnectGuest("Limited guest"))
            host.receiveMessage<ServerMessage.SessionReady>()
            guest.receiveMessage<ServerMessage.SessionReady>()
            host.receiveMessage<ServerMessage.RoomList>()
            guest.receiveMessage<ServerMessage.RoomList>()

            host.sendMessage(ClientMessage.CreateRoom("Limited room", PASSWORD, ProtocolGameMode.ORDER))
            val room = host.receiveMessage<ServerMessage.RoomCreated>().game
            guest.sendMessage(ClientMessage.CreateRoom("Bypass room", PASSWORD, ProtocolGameMode.ORDER))
            assertEquals("RATE_LIMITED", guest.receiveMessage<ServerMessage.Error>().code)

            repeat(2) {
                guest.sendMessage(ClientMessage.JoinRoom(room.roomId, "wrong-password"))
                assertEquals("WRONG_PASSWORD", guest.receiveMessage<ServerMessage.Error>().code)
            }
            guest.sendMessage(ClientMessage.JoinRoom(room.roomId, "wrong-password"))
            assertEquals("RATE_LIMITED", guest.receiveMessage<ServerMessage.Error>().code)

            now += 500L
            guest.sendMessage(ClientMessage.JoinRoom(room.roomId, PASSWORD))
            host.receiveMessage<ServerMessage.RoomUpdated>()
            guest.receiveMessage<ServerMessage.RoomUpdated>()
            readyRoom(host, guest, room.roomId)

            host.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "limited-select-1"))
            host.receiveMessage<ServerMessage.GameStateUpdated>()
            guest.receiveMessage<ServerMessage.GameStateUpdated>()
            host.sendMessage(ClientMessage.SelectNumber(room.roomId, 2, "limited-select-2"))
            val selectionLimited = host.receiveMessage<ServerMessage.Error>()
            assertEquals("RATE_LIMITED", selectionLimited.code)
            assertEquals("limited-select-2", selectionLimited.requestId)

            now += 1_000L
            host.sendMessage(ClientMessage.SelectNumber(room.roomId, 2, "limited-select-2"))
            val hostContinued = host.receiveMessage<ServerMessage.GameStateUpdated>().game
            val guestContinued = guest.receiveMessage<ServerMessage.GameStateUpdated>().game
            assertEquals(3, hostContinued.currentTarget)
            assertEquals(hostContinued, guestContinued)
        } finally {
            host.close()
            guest.close()
        }
    }

    @Test
    fun `heartbeat keeps an idle responsive websocket alive`() = testApplication {
        application {
            gameModule(
                websocketPingPeriod = 100.milliseconds,
                websocketPongTimeout = 500.milliseconds
            )
        }
        val webSocketClient = createClient { install(WebSockets) }
        val socket = webSocketClient.webSocketSession("/game")
        try {
            socket.sendMessage(ClientMessage.ConnectGuest("Heartbeat player"))
            socket.receiveMessage<ServerMessage.SessionReady>()
            socket.receiveMessage<ServerMessage.RoomList>()

            delay(1_000)

            socket.sendMessage(ClientMessage.ListRooms)
            socket.receiveMessage<ServerMessage.RoomList>()
        } finally {
            socket.close()
        }
    }

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
    fun `new device connection replaces old account websocket without disconnecting player`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val registered = assertIs<AuthResult.Success>(
            authService.register(
                email = "multi-device@example.com",
                password = "strong-password-123",
                displayName = "Multi device",
                devicePlatform = "android"
            )
        ).session
        val secondDevice = assertIs<AuthResult.Success>(
            authService.login(
                email = "multi-device@example.com",
                password = "strong-password-123",
                devicePlatform = "ios"
            )
        ).session
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val first = webSocketClient.webSocketSession("/game")
        val second = webSocketClient.webSocketSession("/game")

        try {
            first.sendMessage(ClientMessage.ConnectAccount(registered.accessToken))
            val firstReady = first.receiveMessage<ServerMessage.SessionReady>()
            first.receiveMessage<ServerMessage.RoomList>()

            second.sendMessage(ClientMessage.ConnectAccount(secondDevice.accessToken))
            val secondReady = second.receiveMessage<ServerMessage.SessionReady>()
            second.receiveMessage<ServerMessage.RoomList>()
            assertEquals(firstReady.playerId, secondReady.playerId)

            val oldCloseReason = withTimeout(2_000) { first.closeReason.await() }
            assertEquals("Session resumed elsewhere", oldCloseReason?.message)

            second.sendMessage(ClientMessage.GetProfile)
            assertEquals(
                "Multi device",
                second.receiveMessage<ServerMessage.ProfileData>().profile.displayName
            )
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `registered account reconnects to the same active game snapshot`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val authSession = assertIs<AuthResult.Success>(
            authService.register(
                email = "account-reconnect@example.com",
                password = "strong-password-123",
                displayName = "Chủ phòng tài khoản",
                devicePlatform = "android"
            )
        ).session
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")

        try {
            host.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken))
            guest.sendMessage(ClientMessage.ConnectGuest("Khách reconnect"))
            val hostReady = host.receiveMessage<ServerMessage.SessionReady>()
            val guestReady = guest.receiveMessage<ServerMessage.SessionReady>()
            host.receiveMessage<ServerMessage.RoomList>()
            guest.receiveMessage<ServerMessage.RoomList>()

            host.sendMessage(
                ClientMessage.CreateRoom("Phòng account reconnect", PASSWORD, ProtocolGameMode.ORDER)
            )
            val room = host.receiveMessage<ServerMessage.RoomCreated>().game
            joinAndReadyRoom(host, guest, room.roomId)

            host.sendMessage(ClientMessage.SelectNumber(room.roomId, 1, "account-reconnect-select"))
            host.receiveMessage<ServerMessage.GameStateUpdated>()
            guest.receiveMessage<ServerMessage.GameStateUpdated>()
            host.close()
            delay(100)

            guest.sendMessage(ClientMessage.SelectNumber(room.roomId, 2, "guest-while-account-offline"))
            val offlineUpdate = guest.receiveMessage<ServerMessage.GameStateUpdated>().game
            assertEquals(3, offlineUpdate.currentTarget)

            val resumedHost = webSocketClient.webSocketSession("/game")
            try {
                resumedHost.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken))
                val resumed = resumedHost.receiveMessage<ServerMessage.SessionReady>()
                assertEquals(hostReady.playerId, resumed.playerId)
                val snapshot = assertNotNull(resumed.currentGame)
                assertEquals(room.roomId, snapshot.roomId)
                assertEquals(3, snapshot.currentTarget)
                assertEquals(listOf(1, 2), snapshot.selectedNumbers)
                assertEquals(guestReady.playerId, snapshot.players.first { it.id != resumed.playerId }.id)
            } finally {
                resumedHost.close()
            }
        } finally {
            host.close()
            guest.close()
        }
    }

    @Test
    fun `two websocket clients continue their match after server application restart`() {
        val identityRepository = InMemoryGuestIdentityRepository()
        val dataSource = postgresTestDataSource()
        val activeRoomRepository = dataSource
            ?.let(::PostgresActiveRoomRepository)
            ?: InMemoryActiveRoomRepository()
        lateinit var hostSession: ServerMessage.SessionReady
        lateinit var guestSession: ServerMessage.SessionReady
        var roomId = ""
        var boardBeforeRestart = emptyList<Int>()

        try {
            testApplication {
                application {
                    gameModule(GameEngine(
                        identityRepository = identityRepository,
                        activeRoomRepository = activeRoomRepository
                    ))
                }
                val webSocketClient = createClient { install(WebSockets) }
                val host = webSocketClient.webSocketSession("/game")
                val guest = webSocketClient.webSocketSession("/game")
                try {
                    host.sendMessage(ClientMessage.ConnectGuest("Restart host"))
                    guest.sendMessage(ClientMessage.ConnectGuest("Restart guest"))
                    hostSession = host.receiveMessage()
                    guestSession = guest.receiveMessage()
                    host.receiveMessage<ServerMessage.RoomList>()
                    guest.receiveMessage<ServerMessage.RoomList>()

                    host.sendMessage(
                        ClientMessage.CreateRoom("Restart E2E", PASSWORD, ProtocolGameMode.ORDER)
                    )
                    roomId = host.receiveMessage<ServerMessage.RoomCreated>().game.roomId
                    val (hostStarted, guestStarted) = joinAndReadyRoom(host, guest, roomId)
                    assertEquals(hostStarted, guestStarted)
                    boardBeforeRestart = hostStarted.numbers

                    host.sendMessage(ClientMessage.SelectNumber(roomId, 1, "persisted-websocket-request"))
                    val hostUpdate = host.receiveMessage<ServerMessage.GameStateUpdated>().game
                    val guestUpdate = guest.receiveMessage<ServerMessage.GameStateUpdated>().game
                    assertEquals(hostUpdate, guestUpdate)
                    assertEquals(2, hostUpdate.currentTarget)
                } finally {
                    host.close()
                    guest.close()
                    delay(100)
                }
            }

            testApplication {
                application {
                    gameModule(GameEngine(
                        identityRepository = identityRepository,
                        activeRoomRepository = activeRoomRepository
                    ))
                }
                val webSocketClient = createClient { install(WebSockets) }
                val host = webSocketClient.webSocketSession("/game")
                val guest = webSocketClient.webSocketSession("/game")
                try {
                    host.sendMessage(
                        ClientMessage.ConnectGuest("Restart host", resumeToken = hostSession.resumeToken)
                    )
                    guest.sendMessage(
                        ClientMessage.ConnectGuest("Restart guest", resumeToken = guestSession.resumeToken)
                    )
                    val restoredHost = host.receiveMessage<ServerMessage.SessionReady>()
                    val restoredGuest = guest.receiveMessage<ServerMessage.SessionReady>()
                    assertEquals(hostSession.playerId, restoredHost.playerId)
                    assertEquals(guestSession.playerId, restoredGuest.playerId)

                    val hostGame = assertNotNull(restoredHost.currentGame)
                    val guestGame = assertNotNull(restoredGuest.currentGame)
                    assertEquals(roomId, hostGame.roomId)
                    assertEquals(hostGame, guestGame)
                    assertEquals(boardBeforeRestart, hostGame.numbers)
                    assertEquals(listOf(1), hostGame.selectedNumbers)
                    assertEquals(2, hostGame.currentTarget)
                    assertEquals(10, hostGame.players.sumOf { it.score })

                    host.sendMessage(ClientMessage.SelectNumber(roomId, 1, "persisted-websocket-request"))
                    val duplicate = host.receiveMessage<ServerMessage.GameStateUpdated>().game
                    assertEquals(2, duplicate.currentTarget)
                    assertEquals(10, duplicate.players.sumOf { it.score })

                    guest.sendMessage(ClientMessage.SelectNumber(roomId, 2, "after-server-restart"))
                    val hostContinued = host.receiveMessage<ServerMessage.GameStateUpdated>().game
                    val guestContinued = guest.receiveMessage<ServerMessage.GameStateUpdated>().game
                    assertEquals(hostContinued, guestContinued)
                    assertEquals(3, hostContinued.currentTarget)
                    assertEquals(listOf(1, 2), hostContinued.selectedNumbers)
                    assertEquals(20, hostContinued.players.sumOf { it.score })
                } finally {
                    host.close()
                    guest.close()
                }
            }
        } finally {
            if (roomId.isNotBlank()) runBlocking { activeRoomRepository.delete(roomId) }
            dataSource?.close()
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
            joinAndReadyRoom(host, guest, room.roomId)

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

            val (hostStarted, guestStarted) = joinAndReadyRoom(host, guest, room.roomId)
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

    private suspend fun joinAndReadyRoom(
        host: DefaultClientWebSocketSession,
        guest: DefaultClientWebSocketSession,
        roomId: String,
        password: String = PASSWORD
    ): Pair<com.hienthai.fastowin.protocol.GameSnapshot, com.hienthai.fastowin.protocol.GameSnapshot> {
        guest.sendMessage(ClientMessage.JoinRoom(roomId, password))
        host.receiveMessage<ServerMessage.RoomUpdated>()
        guest.receiveMessage<ServerMessage.RoomUpdated>()
        return readyRoom(host, guest, roomId)
    }

    private suspend fun readyRoom(
        host: DefaultClientWebSocketSession,
        guest: DefaultClientWebSocketSession,
        roomId: String
    ): Pair<com.hienthai.fastowin.protocol.GameSnapshot, com.hienthai.fastowin.protocol.GameSnapshot> {
        host.sendMessage(ClientMessage.SetReady(roomId, true))
        host.receiveMessage<ServerMessage.RoomUpdated>()
        guest.receiveMessage<ServerMessage.RoomUpdated>()
        guest.sendMessage(ClientMessage.SetReady(roomId, true))
        val hostStarted = host.receiveMessage<ServerMessage.GameStarted>().game
        val guestStarted = guest.receiveMessage<ServerMessage.GameStarted>().game
        return hostStarted to guestStarted
    }

    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.receiveMessage(): T {
        while (true) {
            val frame = incoming.receive() as? Frame.Text ?: continue
            val message = ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
            if (message is T) return message
        }
    }

    private fun postgresTestDataSource(): HikariDataSource? {
        val url = System.getenv("TEST_DATABASE_URL") ?: return null
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        })
        return try {
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            dataSource
        } catch (error: Throwable) {
            dataSource.close()
            throw error
        }
    }

    private companion object {
        const val PASSWORD = "123456"
    }
}
