package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.ChangePasswordRequest
import com.hienthai.fastowin.protocol.DeleteAccountRequest
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.LogoutRequest
import com.hienthai.fastowin.protocol.PasswordResetConfirmRequest
import com.hienthai.fastowin.protocol.PasswordResetRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RefreshTokenRequest
import com.hienthai.fastowin.protocol.RegisterRequest
import com.hienthai.fastowin.protocol.UpgradeGuestRequest
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Application.gameModule(
    engine: GameEngine = GameEngine(),
    authService: AuthenticationService = AuthenticationService(InMemoryAuthRepository()),
    environment: String = "dev",
    rateLimiter: RateLimiter = InMemoryRateLimiter(),
    rateLimitPolicies: ServerRateLimitPolicies = ServerRateLimitPolicies(),
    websocketPingPeriod: Duration = DEFAULT_WEBSOCKET_PING_PERIOD,
    websocketPongTimeout: Duration = DEFAULT_WEBSOCKET_PONG_TIMEOUT
) {
    install(ContentNegotiation) {
        json(ProtocolJson)
    }
    install(WebSockets) {
        pingPeriod = websocketPingPeriod
        timeout = websocketPongTimeout
        maxFrameSize = 64 * 1024
    }

    val connections = ConcurrentHashMap<String, SocketConnection>()

    suspend fun deliver(deliveries: List<Delivery>) {
        deliveries.forEach { delivery ->
            val targets = delivery.recipients?.mapNotNull(connections::get) ?: connections.values.toList()
            targets.forEach { connection ->
                try {
                    connection.send(delivery.message)
                } catch (_: Exception) {
                    // The connection cleanup path will remove stale sockets.
                }
            }
        }
    }

    launch {
        while (isActive) {
            delay(SESSION_CLEANUP_INTERVAL_MILLIS)
            deliver(engine.cleanupExpiredSessions())
        }
    }

    launch {
        while (isActive) {
            delay(GAME_TIMER_INTERVAL_MILLIS)
            deliver(engine.advanceTimedGames())
        }
    }

    routing {
        get("/health") { call.respondText("OK") }

        post("/auth/register") {
            val request = call.receiveOrReject<RegisterRequest>() ?: return@post
            call.respondAuthResult(
                authService.register(
                    request.email,
                    request.password,
                    request.displayName,
                    request.devicePlatform
                ),
                successStatus = HttpStatusCode.Created
            )
        }

        post("/auth/login") {
            val ipAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.LOGIN_IP,
                call.clientRateLimitKey(),
                rateLimitPolicies.loginPerIp
            )
            if (!ipAllowed) return@post
            val request = call.receiveOrReject<LoginRequest>() ?: return@post
            val accountAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.LOGIN_ACCOUNT,
                stableRateLimitKey(request.email.trim().lowercase()),
                rateLimitPolicies.loginPerAccount
            )
            if (!accountAllowed) return@post
            call.respondAuthResult(
                authService.login(request.email, request.password, request.devicePlatform)
            )
        }

        post("/auth/upgrade-guest") {
            val request = call.receiveOrReject<UpgradeGuestRequest>() ?: return@post
            call.respondAuthResult(
                authService.upgradeGuest(
                    request.resumeToken,
                    request.email,
                    request.password,
                    request.devicePlatform
                )
            )
        }

        post("/auth/refresh") {
            val request = call.receiveOrReject<RefreshTokenRequest>() ?: return@post
            call.respondAuthResult(authService.refresh(request.refreshToken))
        }

        post("/auth/logout") {
            val request = call.receiveOrReject<LogoutRequest>() ?: return@post
            authService.logout(request.refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/auth/change-password") {
            val request = call.receiveOrReject<ChangePasswordRequest>() ?: return@post
            call.respondAccountAction(
                authService.changePassword(
                    request.accessToken,
                    request.currentPassword,
                    request.newPassword
                )
            )
        }

        post("/auth/password-reset/request") {
            if (environment != "dev") {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    AuthErrorResponse(
                        "PASSWORD_RESET_DELIVERY_UNAVAILABLE",
                        "Dịch vụ gửi email khôi phục mật khẩu chưa được cấu hình."
                    )
                )
                return@post
            }
            val request = call.receiveOrReject<PasswordResetRequest>() ?: return@post
            call.respondAccountAction(authService.requestPasswordReset(request.email), exposeResetToken = true)
        }

        post("/auth/password-reset/confirm") {
            val request = call.receiveOrReject<PasswordResetConfirmRequest>() ?: return@post
            call.respondAccountAction(
                authService.resetPassword(request.email, request.resetToken, request.newPassword)
            )
        }

        post("/auth/delete-account") {
            val request = call.receiveOrReject<DeleteAccountRequest>() ?: return@post
            call.respondAccountAction(authService.deleteAccount(request.accessToken, request.password))
        }

        webSocket("/game") {
            val clientRateLimitKey = stableRateLimitKey(call.request.local.remoteHost)
            var playerId: String? = null
            var playerRateLimitKey: String? = null
            var accountAccessToken: String? = null
            try {
                for (frame in incoming) {
                    val ipMessageLimit = rateLimiter.consume(
                        RateLimitBuckets.WEBSOCKET_MESSAGE_IP,
                        clientRateLimitKey,
                        rateLimitPolicies.websocketMessagesPerIp
                    )
                    if (!ipMessageLimit.allowed) {
                        sendRateLimited(ipMessageLimit)
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "WebSocket rate limit exceeded"))
                        break
                    }
                    if (frame !is Frame.Text) continue
                    val message = runCatching {
                        ProtocolJson.decodeFromString<ClientMessage>(frame.readText())
                    }.getOrElse {
                        send(ProtocolJson.encodeToString<ServerMessage>(
                            ServerMessage.Error("INVALID_MESSAGE", "Dữ liệu gửi lên không hợp lệ.")
                        ))
                        continue
                    }

                    if (playerId == null) {
                        if (message !is ClientMessage.ConnectGuest && message !is ClientMessage.ConnectAccount) {
                            send(ProtocolJson.encodeToString<ServerMessage>(
                                ServerMessage.Error("AUTH_REQUIRED", "Hãy khởi tạo phiên chơi trước.")
                            ))
                            continue
                        }
                        val protocolVersion = when (message) {
                            is ClientMessage.ConnectGuest -> message.protocolVersion
                            is ClientMessage.ConnectAccount -> message.protocolVersion
                        }
                        if (protocolVersion != com.hienthai.fastowin.protocol.PROTOCOL_VERSION) {
                            send(ProtocolJson.encodeToString<ServerMessage>(
                                ServerMessage.Error("PROTOCOL_MISMATCH", "Phiên bản ứng dụng không tương thích.")
                            ))
                            continue
                        }

                        val connectLimit = rateLimiter.consume(
                            RateLimitBuckets.WEBSOCKET_CONNECT_IP,
                            clientRateLimitKey,
                            rateLimitPolicies.websocketConnectPerIp
                        )
                        if (!connectLimit.allowed) {
                            sendRateLimited(connectLimit)
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Connection rate limit exceeded"))
                            break
                        }

                        val connected = runCatching {
                            when (message) {
                                is ClientMessage.ConnectGuest ->
                                    engine.connectGuest(message.displayName, message.resumeToken)
                                is ClientMessage.ConnectAccount -> {
                                    val account = authService.authenticateAccessToken(message.accessToken)
                                        ?: throw InvalidAccessTokenException()
                                    accountAccessToken = message.accessToken
                                    engine.connectAccount(account)
                                }
                            }
                        }.getOrElse { error ->
                            send(ProtocolJson.encodeToString<ServerMessage>(
                                if (error is InvalidAccessTokenException) {
                                    ServerMessage.Error(
                                        "INVALID_ACCESS_TOKEN",
                                        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                                    )
                                } else {
                                    ServerMessage.Error(
                                        "INVALID_NAME",
                                        error.message ?: "Tên người chơi không hợp lệ."
                                    )
                                }
                            ))
                            continue
                        }
                        playerId = connected.playerId
                        playerRateLimitKey = stableRateLimitKey(connected.playerId)
                        val connection = SocketConnection(this)
                        connections.put(connected.playerId, connection)?.closeForReplacement()
                        connection.send(
                            ServerMessage.SessionReady(
                                playerId = connected.playerId,
                                resumeToken = connected.resumeToken,
                                currentGame = connected.currentGame
                            )
                        )
                        deliver(listOf(Delivery(engine.roomList())))
                        deliver(engine.presenceUpdates(connected.playerId))
                        continue
                    }

                    val activePlayerId = playerId
                    val activePlayerRateLimitKey = checkNotNull(playerRateLimitKey)
                    val playerMessageLimit = rateLimiter.consume(
                        RateLimitBuckets.WEBSOCKET_MESSAGE_PLAYER,
                        activePlayerRateLimitKey,
                        rateLimitPolicies.websocketMessagesPerPlayer
                    )
                    if (!playerMessageLimit.allowed) {
                        sendRateLimited(playerMessageLimit)
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Player rate limit exceeded"))
                        break
                    }
                    val actionLimit = rateLimiter.consumeActionLimit(
                        clientRateLimitKey,
                        activePlayerRateLimitKey,
                        message,
                        rateLimitPolicies
                    )
                    if (actionLimit != null) {
                        sendRateLimited(
                            actionLimit,
                            requestId = (message as? ClientMessage.SelectNumber)?.requestId
                        )
                        continue
                    }

                    val currentAccountToken = accountAccessToken
                    if (currentAccountToken != null &&
                        authService.authenticateAccessToken(currentAccountToken) == null
                    ) {
                        send(ProtocolJson.encodeToString<ServerMessage>(
                            ServerMessage.Error(
                                "INVALID_ACCESS_TOKEN",
                                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                            )
                        ))
                        close(CloseReason(CloseReason.Codes.NORMAL, "Account session revoked"))
                        break
                    }
                    deliver(engine.handle(activePlayerId, message))
                }
            } finally {
                playerId?.let { id ->
                    val connection = connections[id]
                    if (connection?.session === this && connections.remove(id, connection)) {
                        deliver(engine.markDisconnected(id))
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.clientRateLimitKey(): String =
    stableRateLimitKey(request.local.remoteHost)

private suspend fun ApplicationCall.consumeHttpRateLimit(
    rateLimiter: RateLimiter,
    bucket: String,
    key: String,
    policy: RateLimitPolicy
): Boolean {
    val result = rateLimiter.consume(bucket, key, policy)
    if (result.allowed) return true
    val retryAfterSeconds = result.retryAfterSeconds()
    response.header(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
    respond(
        HttpStatusCode.TooManyRequests,
        AuthErrorResponse(
            code = "RATE_LIMITED",
            message = "Bạn thao tác quá nhanh. Vui lòng thử lại sau $retryAfterSeconds giây."
        )
    )
    return false
}

private suspend fun RateLimiter.consumeActionLimit(
    clientKey: String,
    playerKey: String,
    message: ClientMessage,
    policies: ServerRateLimitPolicies
): RateLimitResult? {
    return when (message) {
        is ClientMessage.CreateRoom -> consume(
            RateLimitBuckets.CREATE_ROOM_PLAYER,
            playerKey,
            policies.createRoomPerPlayer
        ).takeUnless(RateLimitResult::allowed) ?: consume(
            RateLimitBuckets.CREATE_ROOM_IP,
            clientKey,
            policies.createRoomPerIp
        ).takeUnless(RateLimitResult::allowed)

        is ClientMessage.JoinRoom -> {
            consume(
                RateLimitBuckets.JOIN_ROOM_PLAYER,
                playerKey,
                policies.joinRoomPerPlayer
            ).takeUnless(RateLimitResult::allowed) ?: consume(
                RateLimitBuckets.JOIN_ROOM_IP,
                clientKey,
                policies.joinRoomPerIp
            ).takeUnless(RateLimitResult::allowed) ?: consume(
                RateLimitBuckets.JOIN_ROOM_IP_AND_ROOM,
                stableRateLimitKey("$clientKey:${message.roomId}"),
                policies.joinRoomPerIpAndRoom
            ).takeUnless(RateLimitResult::allowed)
        }

        is ClientMessage.SelectNumber -> consume(
            RateLimitBuckets.SELECT_NUMBER_PLAYER,
            playerKey,
            policies.selectNumberPerPlayer
        ).takeUnless(RateLimitResult::allowed) ?: consume(
            RateLimitBuckets.SELECT_NUMBER_IP,
            clientKey,
            policies.selectNumberPerIp
        ).takeUnless(RateLimitResult::allowed)

        else -> null
    }
}

private suspend fun DefaultWebSocketServerSession.sendRateLimited(
    result: RateLimitResult,
    requestId: String? = null
) {
    val retryAfterSeconds = result.retryAfterSeconds()
    send(ProtocolJson.encodeToString<ServerMessage>(
        ServerMessage.Error(
            code = "RATE_LIMITED",
            message = "Bạn thao tác quá nhanh. Vui lòng thử lại sau $retryAfterSeconds giây.",
            requestId = requestId
        )
    ))
}

private fun RateLimitResult.retryAfterSeconds(): Long =
    ((retryAfterMillis + 999L) / 1_000L).coerceAtLeast(1L)

private class InvalidAccessTokenException : RuntimeException()

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrReject(): T? =
    runCatching { receive<T>() }.getOrElse {
        respond(
            HttpStatusCode.BadRequest,
            AuthErrorResponse("INVALID_REQUEST", "Dữ liệu gửi lên không hợp lệ.")
        )
        null
    }

private suspend fun ApplicationCall.respondAuthResult(
    result: AuthResult,
    successStatus: HttpStatusCode = HttpStatusCode.OK
) {
    when (result) {
        is AuthResult.Success -> respond(successStatus, result.session)
        is AuthResult.Failure -> respond(
            when (result.code) {
                "EMAIL_ALREADY_EXISTS" -> HttpStatusCode.Conflict
                "INVALID_CREDENTIALS", "INVALID_REFRESH_TOKEN", "INVALID_GUEST_SESSION" ->
                    HttpStatusCode.Unauthorized
                "DATABASE_REQUIRED" -> HttpStatusCode.ServiceUnavailable
                else -> HttpStatusCode.BadRequest
            },
            AuthErrorResponse(result.code, result.message)
        )
    }
}

private suspend fun ApplicationCall.respondAccountAction(
    result: AccountActionResult,
    exposeResetToken: Boolean = false
) {
    when (result) {
        is AccountActionResult.Success -> respond(
            HttpStatusCode.OK,
            AccountActionResponse(
                message = result.message,
                devResetToken = result.resetToken.takeIf { exposeResetToken }
            )
        )
        is AccountActionResult.Failure -> respond(
            when (result.code) {
                "INVALID_ACCESS_TOKEN" -> HttpStatusCode.Unauthorized
                else -> HttpStatusCode.BadRequest
            },
            AuthErrorResponse(result.code, result.message)
        )
    }
}

private class SocketConnection(val session: io.ktor.server.websocket.DefaultWebSocketServerSession) {
    private val sendMutex = Mutex()

    suspend fun send(message: ServerMessage) = sendMutex.withLock {
        session.send(ProtocolJson.encodeToString<ServerMessage>(message))
    }

    suspend fun closeForReplacement() {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Session resumed elsewhere"))
    }
}

private const val SESSION_CLEANUP_INTERVAL_MILLIS = 5_000L
private const val GAME_TIMER_INTERVAL_MILLIS = 250L
private val DEFAULT_WEBSOCKET_PING_PERIOD = 10.seconds
private val DEFAULT_WEBSOCKET_PONG_TIMEOUT = 8.seconds
