package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.AccountSessionsRequest
import com.hienthai.fastowin.protocol.AccountSessionsResponse
import com.hienthai.fastowin.protocol.ChangePasswordRequest
import com.hienthai.fastowin.protocol.DeleteAccountRequest
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.LogoutRequest
import com.hienthai.fastowin.protocol.PasswordResetConfirmRequest
import com.hienthai.fastowin.protocol.PasswordResetRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RefreshTokenRequest
import com.hienthai.fastowin.protocol.RevokeAccountSessionRequest
import com.hienthai.fastowin.protocol.RevokeAllAccountSessionsRequest
import com.hienthai.fastowin.protocol.RegisterRequest
import com.hienthai.fastowin.protocol.UpgradeGuestRequest
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondBytes
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Application.gameModule(
    engine: GameEngine = GameEngine(),
    authService: AuthenticationService = AuthenticationService(InMemoryAuthRepository()),
    environment: String = "dev",
    rateLimiter: RateLimiter = InMemoryRateLimiter(),
    rateLimitPolicies: ServerRateLimitPolicies = ServerRateLimitPolicies(),
    seasonLifecycleRepository: SeasonLifecycleRepository = NoOpSeasonLifecycleRepository,
    serviceStatusProvider: () -> ServiceStatusResponse = ::serviceStatusFromEnvironment,
    websocketPingPeriod: Duration = DEFAULT_WEBSOCKET_PING_PERIOD,
    websocketPongTimeout: Duration = DEFAULT_WEBSOCKET_PONG_TIMEOUT
) {
    install(ContentNegotiation) {
        json(ProtocolJson)
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        if (environment == "dev") {
            anyHost()
        } else {
            allowedWebOriginsFromEnvironment().forEach { (host, scheme) ->
                allowHost(host, schemes = listOf(scheme))
            }
        }
    }
    install(WebSockets) {
        pingPeriod = websocketPingPeriod
        timeout = websocketPongTimeout
        maxFrameSize = 64 * 1024
    }

    val connections = ConcurrentHashMap<String, SocketConnection>()

    launch {
        while (isActive) {
            runCatching { seasonLifecycleRepository.maintain() }
                .onFailure { System.err.println("Could not maintain season lifecycle: ${it.message}") }
            delay(SEASON_LIFECYCLE_INTERVAL_MILLIS)
        }
    }

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
        get("/status") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(serviceStatusProvider())
        }

        get("/health") { call.respondText("OK") }

        post("/auth/register") {
            val request = call.receiveOrReject<RegisterRequest>() ?: return@post
            call.respondAuthResult(
                authService.register(
                    request.email,
                    request.password,
                    request.displayName,
                    request.devicePlatform,
                    request.gender
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
            val result = authService.login(request.email, request.password, request.devicePlatform)
            if (result is AuthResult.Success) {
                // A successful login owns the account from this point forward.
                // Closing the previous socket makes the old device return to login immediately.
                val previousConnection = connections[result.session.userId]
                if (previousConnection != null) {
                    try {
                        previousConnection.closeForReplacement()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        connections.remove(result.session.userId, previousConnection)
                    }
                }
            }
            call.respondAuthResult(result)
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

        post("/auth/sessions") {
            val request = call.receiveOrReject<AccountSessionsRequest>() ?: return@post
            call.respondAccountSessions(authService.listSessions(request.accessToken))
        }

        post("/auth/sessions/revoke") {
            val request = call.receiveOrReject<RevokeAccountSessionRequest>() ?: return@post
            call.respondAccountAction(
                authService.revokeSession(request.accessToken, request.sessionId)
            )
        }

        post("/auth/sessions/revoke-all") {
            val request = call.receiveOrReject<RevokeAllAccountSessionsRequest>() ?: return@post
            call.respondAccountAction(authService.revokeAllSessions(request.accessToken))
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

        get("/api/avatar/{playerId}") {
            val playerId = call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val base64Data = engine.getAvatarData(playerId)
            if (base64Data != null) {
                val cleanBase64 = base64Data.substringAfter(",")
                try {
                    val bytes = java.util.Base64.getDecoder().decode(cleanBase64)
                    call.respondBytes(bytes, io.ktor.http.ContentType.Image.JPEG)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }



        webSocket("/game") {
            val serviceStatus = serviceStatusProvider()
            if (serviceStatus.maintenance) {
                close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server maintenance"))
                return@webSocket
            }
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
                            requestId = when (message) {
                                is ClientMessage.SelectNumber -> message.requestId
                                is ClientMessage.VerifyStorePurchase -> message.requestId
                                else -> null
                            }
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

        is ClientMessage.VerifyStorePurchase -> consume(
            RateLimitBuckets.VERIFY_PURCHASE_PLAYER,
            playerKey,
            policies.verifyPurchasePerPlayer
        ).takeUnless(RateLimitResult::allowed) ?: consume(
            RateLimitBuckets.VERIFY_PURCHASE_IP,
            clientKey,
            policies.verifyPurchasePerIp
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

private suspend fun ApplicationCall.respondAccountSessions(result: AccountSessionsResult) {
    when (result) {
        is AccountSessionsResult.Success -> respond(
            HttpStatusCode.OK,
            AccountSessionsResponse(result.sessions)
        )
        is AccountSessionsResult.Failure -> respond(
            HttpStatusCode.Unauthorized,
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
        session.close(CloseReason(CloseReason.Codes.NORMAL, SESSION_REPLACED_CLOSE_REASON))
    }
}

private const val SESSION_CLEANUP_INTERVAL_MILLIS = 5_000L
private const val GAME_TIMER_INTERVAL_MILLIS = 250L
private const val SEASON_LIFECYCLE_INTERVAL_MILLIS = 60_000L
private val DEFAULT_WEBSOCKET_PING_PERIOD = 10.seconds
private val DEFAULT_WEBSOCKET_PONG_TIMEOUT = 8.seconds

internal fun serviceStatusFromEnvironment(): ServiceStatusResponse {
    val maintenance = System.getenv("FASTTOWIN_MAINTENANCE")
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
    val configuredMessage = System.getenv("FASTTOWIN_MAINTENANCE_MESSAGE")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    return ServiceStatusResponse(
        maintenance = maintenance,
        message = configuredMessage.takeIf { maintenance },
        pollAfterSeconds = if (maintenance) 60 else 30
    )
}

private fun allowedWebOriginsFromEnvironment(): List<Pair<String, String>> =
    System.getenv("FASTTOWIN_WEB_ORIGINS")
        ?.split(',')
        .orEmpty()
        .mapNotNull { rawOrigin ->
            runCatching {
                val uri = URI(rawOrigin.trim())
                val scheme = uri.scheme?.lowercase().takeIf { it == "http" || it == "https" }
                    ?: return@runCatching null
                val authority = uri.rawAuthority?.takeIf(String::isNotBlank)
                    ?: return@runCatching null
                authority to scheme
            }.getOrNull()
        }
