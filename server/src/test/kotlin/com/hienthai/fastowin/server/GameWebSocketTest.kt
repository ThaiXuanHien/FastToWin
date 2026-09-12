package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RewardedAdAvailability
import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.RewardedAdProvider
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
    fun `legacy additive protocol versions remain accepted`() = testApplication {
        application { gameModule() }
        val webSocketClient = createClient { install(WebSockets) }

        for (version in 38..42) {
            val socket = webSocketClient.webSocketSession("/game")
            try {
                socket.sendMessage(ClientMessage.ConnectGuest("Protocol $version", protocolVersion = version))
                val session = withTimeout(2_000) { socket.receiveMessage<ServerMessage.SessionReady>() }
                assertEquals(43, session.protocolVersion)
                withTimeout(2_000) { socket.receiveMessage<ServerMessage.RoomList>() }
            } finally {
                socket.close()
            }
        }
    }

    @Test
    fun `protocol versions outside the additive compatibility window are rejected`() = testApplication {
        application { gameModule() }
        val webSocketClient = createClient { install(WebSockets) }

        for (version in listOf(37, 44)) {
            val socket = webSocketClient.webSocketSession("/game")
            try {
                socket.sendMessage(ClientMessage.ConnectGuest("Protocol $version", protocolVersion = version))
                val error = withTimeout(2_000) { socket.receiveMessage<ServerMessage.Error>() }
                assertEquals("PROTOCOL_MISMATCH", error.code)
            } finally {
                socket.close()
            }
        }
    }

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
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val hostAccount = authService.registerVerified(
            email = "limited-host@example.com",
            password = "strong-password-123",
            displayName = "Limited host"
        )
        val guestAccount = authService.registerVerified(
            email = "limited-guest@example.com",
            password = "strong-password-123",
            displayName = "Limited guest"
        )
        val policies = ServerRateLimitPolicies().copy(
            createRoomPerPlayer = RateLimitPolicy(capacity = 1, refillWindowMillis = 1_000L),
            createRoomPerIp = RateLimitPolicy(capacity = 1, refillWindowMillis = 1_000L),
            joinRoomPerPlayer = RateLimitPolicy(capacity = 2, refillWindowMillis = 1_000L),
            joinRoomPerIpAndRoom = RateLimitPolicy(capacity = 2, refillWindowMillis = 1_000L),
            selectNumberPerPlayer = RateLimitPolicy(capacity = 1, refillWindowMillis = 1_000L)
        )
        application {
            gameModule(
                authService = authService,
                rateLimiter = InMemoryRateLimiter(nowMillis = { now }),
                rateLimitPolicies = policies
            )
        }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")
        try {
            host.sendMessage(ClientMessage.ConnectAccount(hostAccount.accessToken))
            guest.sendMessage(ClientMessage.ConnectAccount(guestAccount.accessToken))
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
        val authSession = authService.registerVerified(
            email = "revoked-socket@example.com",
            password = "strong-password-123",
            displayName = "Revoked player"
        )
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
        val authSession = authService.registerVerified(
            email = "socket@example.com",
            password = "strong-password-123",
            displayName = "Người chơi tài khoản"
        )
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }

        val authenticated = webSocketClient.webSocketSession("/game")
        try {
            authenticated.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken))
            val ready = authenticated.receiveMessage<ServerMessage.SessionReady>()
            assertEquals(authSession.userId, ready.playerId)
            assertNotNull(ready.resumeToken)
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
    fun `play quota websocket consumes one match for both accounts when play starts`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val hostAccount = authService.registerVerified(
            email = "quota-host@example.com",
            password = "strong-password-123",
            displayName = "Quota host"
        )
        val guestAccount = authService.registerVerified(
            email = "quota-guest@example.com",
            password = "strong-password-123",
            displayName = "Quota guest"
        )
        val quotaRepository = InMemoryPlayQuotaRepository()
        val engine = GameEngine(playQuotaRepository = quotaRepository)
        application { gameModule(engine = engine, authService = authService) }
        val client = createClient { install(WebSockets) }
        val host = client.webSocketSession("/game")
        val guest = client.webSocketSession("/game")

        try {
            host.sendMessage(ClientMessage.ConnectAccount(hostAccount.accessToken))
            guest.sendMessage(ClientMessage.ConnectAccount(guestAccount.accessToken))
            host.receiveMessage<ServerMessage.SessionReady>()
            guest.receiveMessage<ServerMessage.SessionReady>()

            host.sendMessage(ClientMessage.CreateRoom("Quota room", PASSWORD, ProtocolGameMode.ORDER))
            val room = host.receiveMessage<ServerMessage.RoomCreated>().game
            guest.sendMessage(ClientMessage.JoinRoom(room.roomId, PASSWORD))
            host.receiveMessage<ServerMessage.RoomUpdated>()
            guest.receiveMessage<ServerMessage.RoomUpdated>()

            host.sendMessage(ClientMessage.SetReady(room.roomId, true))
            host.receiveMessage<ServerMessage.RoomUpdated>()
            guest.receiveMessage<ServerMessage.RoomUpdated>()
            guest.sendMessage(ClientMessage.SetReady(room.roomId, true))
            host.receiveMessage<ServerMessage.GameStarted>()
            guest.receiveMessage<ServerMessage.GameStarted>()

            val hostQuota = host.receiveMessage<ServerMessage.PlayQuotaData>().quota
            val guestQuota = guest.receiveMessage<ServerMessage.PlayQuotaData>().quota
            assertEquals(9, hostQuota.remainingMatches)
            assertEquals(9, guestQuota.remainingMatches)
            assertEquals(1, hostQuota.matchesConsumed)
            assertEquals(1, guestQuota.matchesConsumed)
        } finally {
            host.close()
            guest.close()
        }
    }

    @Test
    fun `play quota websocket blocks guest from online rooms`() = testApplication {
        application { gameModule() }
        val client = createClient { install(WebSockets) }
        val guest = client.webSocketSession("/game")

        try {
            guest.sendMessage(ClientMessage.ConnectGuest("Practice guest"))
            guest.receiveMessage<ServerMessage.SessionReady>()
            guest.sendMessage(ClientMessage.CreateRoom("Guest room", PASSWORD, ProtocolGameMode.ORDER))

            assertEquals("ACCOUNT_REQUIRED", guest.receiveMessage<ServerMessage.Error>().code)
        } finally {
            guest.close()
        }
    }

    @Test
    fun `play quota websocket keeps quota when waiting room is cancelled`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val hostAccount = authService.registerVerified(
            email = "waiting-host@example.com",
            password = "strong-password-123",
            displayName = "Waiting host"
        )
        val guestAccount = authService.registerVerified(
            email = "waiting-guest@example.com",
            password = "strong-password-123",
            displayName = "Waiting guest"
        )
        val engine = GameEngine(playQuotaRepository = InMemoryPlayQuotaRepository())
        application { gameModule(engine = engine, authService = authService) }
        val client = createClient { install(WebSockets) }
        val host = client.webSocketSession("/game")
        val guest = client.webSocketSession("/game")

        try {
            host.sendMessage(ClientMessage.ConnectAccount(hostAccount.accessToken))
            guest.sendMessage(ClientMessage.ConnectAccount(guestAccount.accessToken))
            host.receiveMessage<ServerMessage.SessionReady>()
            guest.receiveMessage<ServerMessage.SessionReady>()

            host.sendMessage(ClientMessage.CreateRoom("Waiting room", PASSWORD, ProtocolGameMode.ORDER))
            val room = host.receiveMessage<ServerMessage.RoomCreated>().game
            guest.sendMessage(ClientMessage.JoinRoom(room.roomId, PASSWORD))
            host.receiveMessage<ServerMessage.RoomUpdated>()
            guest.receiveMessage<ServerMessage.RoomUpdated>()
            guest.sendMessage(ClientMessage.LeaveRoom(room.roomId))

            host.sendMessage(ClientMessage.GetPlayQuota)
            guest.sendMessage(ClientMessage.GetPlayQuota)
            assertEquals(10, host.receiveMessage<ServerMessage.PlayQuotaData>().quota.remainingMatches)
            assertEquals(10, guest.receiveMessage<ServerMessage.PlayQuotaData>().quota.remainingMatches)
        } finally {
            host.close()
            guest.close()
        }
    }

    @Test
    fun `play quota websocket grants a verified rewarded ad only once`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val account = authService.registerVerified(
            email = "rewarded-ad@example.com",
            password = "strong-password-123",
            displayName = "Rewarded ad player"
        )
        val engine = GameEngine(
            playQuotaRepository = InMemoryPlayQuotaRepository(),
            rewardedAdVerifier = DevRewardedAdVerifier(),
            rewardedAdAvailability = RewardedAdAvailability.DEV_SIMULATED
        )
        application { gameModule(engine = engine, authService = authService) }
        val client = createClient { install(WebSockets) }
        val socket = client.webSocketSession("/game")
        val providerTransactionId = "websocket-rewarded-ad-1"
        val command = ClientMessage.ClaimRewardedAdBonus(
            requestId = "rewarded-request-1",
            provider = RewardedAdProvider.DEV_SIMULATED,
            providerTransactionId = providerTransactionId,
            proof = devRewardedAdProof(account.userId, providerTransactionId)
        )

        try {
            socket.sendMessage(ClientMessage.ConnectAccount(account.accessToken))
            socket.receiveMessage<ServerMessage.SessionReady>()
            socket.sendMessage(command)
            val granted = socket.receiveMessage<ServerMessage.RewardedAdBonusResult>()
            assertEquals(RewardedAdBonusStatus.GRANTED, granted.status)
            assertEquals(12, granted.quota.remainingMatches)

            socket.sendMessage(command.copy(requestId = "rewarded-request-2"))
            val duplicate = socket.receiveMessage<ServerMessage.RewardedAdBonusResult>()
            assertEquals(RewardedAdBonusStatus.ALREADY_GRANTED, duplicate.status)
            assertEquals(12, duplicate.quota.remainingMatches)
        } finally {
            socket.close()
        }
    }

    @Test
    fun `valid access token with invalid resume token is rejected without session`() = testApplication {
        val authService = AuthenticationService(InMemoryAuthRepository(), PasswordHasher(iterations = 1_000))
        val registered = authService.registerVerified("invalid-resume@example.com", "strong-password-123", "Invalid resume")
        application { gameModule(authService = authService) }
        val client = createClient { install(WebSockets) }
        val socket = client.webSocketSession("/game")
        try {
            socket.sendMessage(ClientMessage.ConnectAccount(registered.accessToken, resumeToken = "wrong-token"))
            assertEquals("INVALID_RESUME_TOKEN", socket.receiveMessage<ServerMessage.Error>().code)
        } finally { socket.close() }
    }

    @Test
    fun `invalid access token is checked before resume token`() = testApplication {
        application { gameModule() }
        val client = createClient { install(WebSockets) }
        val socket = client.webSocketSession("/game")
        try {
            socket.sendMessage(ClientMessage.ConnectAccount("invalid-access", resumeToken = "wrong-token"))
            assertEquals("INVALID_ACCESS_TOKEN", socket.receiveMessage<ServerMessage.Error>().code)
        } finally { socket.close() }
    }

    @Test
    fun `unverified account cannot authenticate game websocket`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val session = assertIs<AuthResult.Success>(
            authService.register(
                email = "unverified-socket@example.com",
                password = "strong-password-123",
                displayName = "Unverified",
                devicePlatform = "web"
            )
        ).session
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val socket = webSocketClient.webSocketSession("/game")

        try {
            socket.sendMessage(ClientMessage.ConnectAccount(session.accessToken))
            assertEquals("EMAIL_NOT_VERIFIED", socket.receiveMessage<ServerMessage.Error>().code)
        } finally {
            socket.close()
        }
    }

    @Test
    fun `new device connection replaces old account websocket without disconnecting player`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val registered = authService.registerVerified(
            email = "multi-device@example.com",
            password = "strong-password-123",
            displayName = "Multi device"
        )
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val first = webSocketClient.webSocketSession("/game")
        val second = webSocketClient.webSocketSession("/game")

        try {
            first.sendMessage(ClientMessage.ConnectAccount(registered.accessToken))
            val firstReady = first.receiveMessage<ServerMessage.SessionReady>()
            first.receiveMessage<ServerMessage.RoomList>()

            val secondDevice = assertIs<AuthResult.Success>(
                authService.login(
                    email = "multi-device@example.com",
                    password = "strong-password-123",
                    devicePlatform = "ios"
                )
            ).session
            second.sendMessage(ClientMessage.ConnectAccount(secondDevice.accessToken))
            val secondReady = second.receiveMessage<ServerMessage.SessionReady>()
            second.receiveMessage<ServerMessage.RoomList>()
            assertEquals(firstReady.playerId, secondReady.playerId)

            val oldCloseReason = withTimeout(2_000) { first.closeReason.await() }
            assertEquals(SESSION_REPLACED_CLOSE_REASON, oldCloseReason?.message)

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
    fun `HTTP login immediately closes websocket on previous device`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val registered = authService.registerVerified(
            email = "exclusive-login@example.com",
            password = "strong-password-123",
            displayName = "Exclusive login"
        )
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val oldDevice = webSocketClient.webSocketSession("/game")

        try {
            oldDevice.sendMessage(ClientMessage.ConnectAccount(registered.accessToken))
            oldDevice.receiveMessage<ServerMessage.SessionReady>()
            oldDevice.receiveMessage<ServerMessage.RoomList>()

            val loginResponse = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(ProtocolJson.encodeToString(
                    LoginRequest(
                        email = "exclusive-login@example.com",
                        password = "strong-password-123",
                        devicePlatform = "android"
                    )
                ))
            }
            assertEquals(HttpStatusCode.OK, loginResponse.status)
            val closeReason = withTimeout(2_000) { oldDevice.closeReason.await() }
            assertEquals(SESSION_REPLACED_CLOSE_REASON, closeReason?.message)
            assertEquals(null, authService.authenticateAccessToken(registered.accessToken))
            assertIs<AuthResult.Failure>(authService.refresh(registered.refreshToken))
        } finally {
            oldDevice.close()
        }
    }

    @Test
    fun `registered account reconnects to the same active game snapshot`() = testApplication {
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val authSession = authService.registerVerified(
            email = "account-reconnect@example.com",
            password = "strong-password-123",
            displayName = "Chủ phòng tài khoản"
        )
        val guestAuthSession = authService.registerVerified(
            email = "account-reconnect-guest@example.com",
            password = "strong-password-123",
            displayName = "Khách reconnect"
        )
        application { gameModule(authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")

        try {
            host.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken))
            guest.sendMessage(ClientMessage.ConnectAccount(guestAuthSession.accessToken))
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
                val accountResumeToken = assertNotNull(hostReady.resumeToken)
                resumedHost.sendMessage(ClientMessage.ConnectAccount(authSession.accessToken, resumeToken = accountResumeToken))
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
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val (hostAccount, guestAccount) = runBlocking {
            authService.registerVerified(
                email = "restart-host@example.com",
                password = "strong-password-123",
                displayName = "Restart host"
            ) to authService.registerVerified(
                email = "restart-guest@example.com",
                password = "strong-password-123",
                displayName = "Restart guest"
            )
        }
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
                    gameModule(
                        engine = GameEngine(
                            identityRepository = identityRepository,
                            activeRoomRepository = activeRoomRepository
                        ),
                        authService = authService
                    )
                }
                val webSocketClient = createClient { install(WebSockets) }
                val host = webSocketClient.webSocketSession("/game")
                val guest = webSocketClient.webSocketSession("/game")
                try {
                    host.sendMessage(ClientMessage.ConnectAccount(hostAccount.accessToken))
                    guest.sendMessage(ClientMessage.ConnectAccount(guestAccount.accessToken))
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
                    gameModule(
                        engine = GameEngine(
                            identityRepository = identityRepository,
                            activeRoomRepository = activeRoomRepository
                        ),
                        authService = authService
                    )
                }
                val webSocketClient = createClient { install(WebSockets) }
                val host = webSocketClient.webSocketSession("/game")
                val guest = webSocketClient.webSocketSession("/game")
                try {
                    host.sendMessage(ClientMessage.ConnectAccount(hostAccount.accessToken))
                    guest.sendMessage(ClientMessage.ConnectAccount(guestAccount.accessToken))
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
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val hostAccount = authService.registerVerified(
            email = "timer-host@example.com",
            password = "strong-password-123",
            displayName = "Hiền"
        )
        val guestAccount = authService.registerVerified(
            email = "timer-guest@example.com",
            password = "strong-password-123",
            displayName = "Hiếu"
        )
        application {
            gameModule(
                engine = GameEngine(timeAttackMillis = 50L),
                authService = authService
            )
        }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")
        try {
            host.sendMessage(ClientMessage.ConnectAccount(hostAccount.accessToken))
            guest.sendMessage(ClientMessage.ConnectAccount(guestAccount.accessToken))
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
        val authService = AuthenticationService(
            repository = InMemoryAuthRepository(),
            passwordHasher = PasswordHasher(iterations = 1_000)
        )
        val hostAccount = authService.registerVerified(
            email = "e2e-host@example.com",
            password = "strong-password-123",
            displayName = "Hiền"
        )
        val guestAccount = authService.registerVerified(
            email = "e2e-guest@example.com",
            password = "strong-password-123",
            displayName = "Hiếu"
        )
        application { gameModule(engine = GameEngine(), authService = authService) }
        val webSocketClient = createClient { install(WebSockets) }
        val host = webSocketClient.webSocketSession("/game")
        val guest = webSocketClient.webSocketSession("/game")

        try {
            host.sendMessage(ClientMessage.ConnectAccount(hostAccount.accessToken))
            guest.sendMessage(ClientMessage.ConnectAccount(guestAccount.accessToken))
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
                    ClientMessage.ConnectAccount(
                        hostAccount.accessToken,
                        resumeToken = assertNotNull(hostSession.resumeToken)
                    )
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

    private suspend fun AuthenticationService.registerVerified(
        email: String,
        password: String,
        displayName: String
    ): AuthSessionResponse {
        val session = assertIs<AuthResult.Success>(
            register(email, password, displayName, "android")
        ).session
        val verification = assertIs<AccountActionResult.Success>(
            requestEmailVerification(session.accessToken)
        )
        assertIs<AccountActionResult.Success>(
            confirmEmailVerification(
                session.accessToken,
                requireNotNull(verification.emailVerificationCode)
            )
        )
        return session.copy(emailVerified = true)
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
