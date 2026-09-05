package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.AccountActionResponse
import com.hienthai.fastowin.protocol.AccountSessionsRequest
import com.hienthai.fastowin.protocol.AccountSessionsResponse
import com.hienthai.fastowin.protocol.ChangePasswordRequest
import com.hienthai.fastowin.protocol.DeleteAccountRequest
import com.hienthai.fastowin.protocol.EmailVerificationConfirmRequest
import com.hienthai.fastowin.protocol.EmailVerificationRequest
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
import io.ktor.http.CookieEncoding
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.request.header
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Application.gameModule(
    engine: GameEngine = GameEngine(),
    authService: AuthenticationService = AuthenticationService(InMemoryAuthRepository()),
    environment: String = "dev",
    rateLimiter: RateLimiter = InMemoryRateLimiter(),
    rateLimitPolicies: ServerRateLimitPolicies = ServerRateLimitPolicies(),
    authEmailSender: AuthEmailSender = DisabledAuthEmailSender,
    trustProxyHeaders: Boolean = false,
    seasonLifecycleRepository: SeasonLifecycleRepository = NoOpSeasonLifecycleRepository,
    pushReminderService: PushReminderService = NoOpPushReminderService,
    serviceStatusProvider: () -> ServiceStatusResponse = ::serviceStatusFromEnvironment,
    allowedWebOriginsValue: String? = System.getenv("FASTTOWIN_WEB_ORIGINS"),
    websocketPingPeriod: Duration = DEFAULT_WEBSOCKET_PING_PERIOD,
    websocketPongTimeout: Duration = DEFAULT_WEBSOCKET_PONG_TIMEOUT
) {
    val isProduction = environment != "dev"
    val allowedWebOrigins = buildAllowedWebOrigins(
        environment = environment,
        configuredOrigins = allowedWebOriginsValue
    )
    val webSessionCookies = WebSessionCookieConfiguration(isProduction)
    val metrics = ServerMetrics()
    if (trustProxyHeaders) {
        install(XForwardedHeaders)
    }
    install(ContentNegotiation) {
        json(ProtocolJson)
    }
    install(CallLogging) {
        filter { call -> call.request.path() !in QUIET_LOG_PATHS }
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(WEB_SESSION_HEADER)
        allowHeader(WEB_CSRF_HEADER)
        allowCredentials = true
        allowedWebOrigins.forEach { origin ->
            allowHost(origin.authority, schemes = listOf(origin.scheme))
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

    launch {
        while (isActive) {
            runCatching { pushReminderService.sendDueReminders() }
                .onSuccess { delivered ->
                    if (delivered > 0) println("Sent $delivered daily push reminder(s).")
                }
                .onFailure { error ->
                    System.err.println("Could not send daily push reminders: ${error.message}")
                }
            delay(PUSH_REMINDER_INTERVAL_MILLIS)
        }
    }

    routing {
        get("/status") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(serviceStatusProvider())
        }

        get("/health") { call.respondText("OK") }

        get("/internal/metrics") {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondText(
                metrics.render(),
                ContentType.parse("text/plain; version=0.0.4; charset=utf-8")
            )
        }

        post("/auth/register") {
            val request = call.receiveOrReject<RegisterRequest>() ?: return@post
            val webSession = call.resolveWebSessionRequest(request.devicePlatform, allowedWebOrigins)
                ?: return@post
            call.respondAuthResult(
                authService.register(
                    request.email,
                    request.password,
                    request.displayName,
                    request.devicePlatform,
                    request.gender
                ),
                successStatus = HttpStatusCode.Created,
                webSession = webSession,
                cookieConfiguration = webSessionCookies
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
            val webSession = call.resolveWebSessionRequest(request.devicePlatform, allowedWebOrigins)
                ?: return@post
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
            call.respondAuthResult(result, webSession = webSession, cookieConfiguration = webSessionCookies)
        }

        post("/auth/upgrade-guest") {
            val request = call.receiveOrReject<UpgradeGuestRequest>() ?: return@post
            val webSession = call.resolveWebSessionRequest(request.devicePlatform, allowedWebOrigins)
                ?: return@post
            call.respondAuthResult(
                authService.upgradeGuest(
                    request.resumeToken,
                    request.email,
                    request.password,
                    request.devicePlatform
                ),
                webSession = webSession,
                cookieConfiguration = webSessionCookies
            )
        }

        post("/auth/refresh") {
            val request = call.receiveOrReject<RefreshTokenRequest>() ?: return@post
            val webSession = call.resolveWebSessionRequest(null, allowedWebOrigins) ?: return@post
            val refreshToken = if (webSession) {
                call.request.cookies[webSessionCookies.name].orEmpty()
            } else {
                request.refreshToken
            }
            val result = authService.refresh(refreshToken)
            call.respondAuthResult(
                result,
                webSession = webSession,
                cookieConfiguration = webSessionCookies,
                clearCookieOnFailure = webSession
            )
        }

        post("/auth/logout") {
            val request = call.receiveOrReject<LogoutRequest>() ?: return@post
            val webSession = call.resolveWebSessionRequest(null, allowedWebOrigins) ?: return@post
            val refreshToken = if (webSession) {
                call.request.cookies[webSessionCookies.name].orEmpty()
            } else {
                request.refreshToken
            }
            authService.logout(refreshToken)
            if (webSession) call.clearWebSessionCookie(webSessionCookies)
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
            if (environment != "dev" && !authEmailSender.isConfigured) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    AuthErrorResponse(
                        "PASSWORD_RESET_DELIVERY_UNAVAILABLE",
                        "Dịch vụ email chưa được cấu hình."
                    )
                )
                return@post
            }
            val ipAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.PASSWORD_RESET_IP,
                call.clientRateLimitKey(),
                rateLimitPolicies.passwordResetPerIp
            )
            if (!ipAllowed) return@post
            val request = call.receiveOrReject<PasswordResetRequest>() ?: return@post
            val accountAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.PASSWORD_RESET_ACCOUNT,
                stableRateLimitKey(request.email.trim().lowercase()),
                rateLimitPolicies.passwordResetPerAccount
            )
            if (!accountAllowed) return@post
            val result = authService.requestPasswordReset(request.email)
            if (result is AccountActionResult.Success && result.resetToken != null && authEmailSender.isConfigured) {
                runCatching {
                    authEmailSender.sendPasswordReset(request.email.trim().lowercase(), result.resetToken)
                }.onFailure { error ->
                    // Keep the response generic so delivery errors cannot reveal registered accounts.
                    System.err.println("Could not send password reset email: ${error.message}")
                }
            }
            call.respondAccountAction(result, exposeResetToken = environment == "dev")
        }

        post("/auth/password-reset/confirm") {
            val ipAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.PASSWORD_RESET_CONFIRM_IP,
                call.clientRateLimitKey(),
                rateLimitPolicies.passwordResetConfirmPerIp
            )
            if (!ipAllowed) return@post
            val request = call.receiveOrReject<PasswordResetConfirmRequest>() ?: return@post
            val accountAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.PASSWORD_RESET_CONFIRM_ACCOUNT,
                stableRateLimitKey(request.email.trim().lowercase()),
                rateLimitPolicies.passwordResetConfirmPerAccount
            )
            if (!accountAllowed) return@post
            call.respondAccountAction(
                authService.resetPassword(request.email, request.resetToken, request.newPassword)
            )
        }

        post("/auth/email-verification/request") {
            if (environment != "dev" && !authEmailSender.isConfigured) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    AuthErrorResponse("EMAIL_DELIVERY_UNAVAILABLE", "Dịch vụ email chưa được cấu hình.")
                )
                return@post
            }
            val ipAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.EMAIL_VERIFICATION_REQUEST_IP,
                call.clientRateLimitKey(),
                rateLimitPolicies.emailVerificationRequestPerIp
            )
            if (!ipAllowed) return@post
            val request = call.receiveOrReject<EmailVerificationRequest>() ?: return@post
            val accountAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.EMAIL_VERIFICATION_REQUEST_ACCOUNT,
                stableRateLimitKey(request.accessToken),
                rateLimitPolicies.emailVerificationRequestPerAccount
            )
            if (!accountAllowed) return@post
            val result = authService.requestEmailVerification(request.accessToken)
            if (result is AccountActionResult.Success &&
                result.emailVerificationCode != null && result.emailRecipient != null &&
                authEmailSender.isConfigured
            ) {
                try {
                    authEmailSender.sendEmailVerification(
                        result.emailRecipient,
                        result.emailVerificationCode
                    )
                } catch (error: Exception) {
                    System.err.println("Could not send email verification: ${error.message}")
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        AuthErrorResponse("EMAIL_DELIVERY_FAILED", "Chưa thể gửi email. Vui lòng thử lại sau.")
                    )
                    return@post
                }
            }
            call.respondAccountAction(
                result,
                exposeEmailVerificationCode = environment == "dev"
            )
        }

        post("/auth/email-verification/confirm") {
            val ipAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.EMAIL_VERIFICATION_CONFIRM_IP,
                call.clientRateLimitKey(),
                rateLimitPolicies.emailVerificationConfirmPerIp
            )
            if (!ipAllowed) return@post
            val request = call.receiveOrReject<EmailVerificationConfirmRequest>() ?: return@post
            val accountAllowed = call.consumeHttpRateLimit(
                rateLimiter,
                RateLimitBuckets.EMAIL_VERIFICATION_CONFIRM_ACCOUNT,
                stableRateLimitKey(request.accessToken),
                rateLimitPolicies.emailVerificationConfirmPerAccount
            )
            if (!accountAllowed) return@post
            call.respondAccountAction(
                authService.confirmEmailVerification(request.accessToken, request.verificationCode)
            )
        }

        post("/auth/delete-account") {
            val request = call.receiveOrReject<DeleteAccountRequest>() ?: return@post
            call.respondAccountAction(authService.deleteAccount(request.accessToken, request.password))
        }

        get("/api/avatar/{playerId}") {
            val playerId = call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.response.header(HttpHeaders.CacheControl, "no-store, max-age=0")
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
            metrics.webSocketOpened()
            val serviceStatus = serviceStatusProvider()
            if (serviceStatus.maintenance) {
                close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server maintenance"))
                metrics.webSocketClosed()
                return@webSocket
            }
            val clientRateLimitKey = stableRateLimitKey(call.request.origin.remoteHost)
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
                        metrics.webSocketRateLimited()
                        sendRateLimited(ipMessageLimit)
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "WebSocket rate limit exceeded"))
                        break
                    }
                    if (frame !is Frame.Text) continue
                    metrics.webSocketMessageReceived()
                    val message = runCatching {
                        ProtocolJson.decodeFromString<ClientMessage>(frame.readText())
                    }.getOrElse {
                        metrics.invalidWebSocketMessage()
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
                                    if (!account.emailVerified) throw UnverifiedEmailException()
                                    accountAccessToken = message.accessToken
                                    engine.connectAccount(account)
                                }
                            }
                        }.getOrElse { error ->
                            send(ProtocolJson.encodeToString<ServerMessage>(
                                when (error) {
                                    is InvalidAccessTokenException -> ServerMessage.Error(
                                        "INVALID_ACCESS_TOKEN",
                                        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                                    )
                                    is UnverifiedEmailException -> ServerMessage.Error(
                                        "EMAIL_NOT_VERIFIED",
                                        "Vui lòng xác minh email trước khi vào game."
                                    )
                                    else -> ServerMessage.Error(
                                        "INVALID_NAME",
                                        error.message ?: "Tên người chơi không hợp lệ."
                                    )
                                }
                            ))
                            continue
                        }
                        playerId = connected.playerId
                        metrics.sessionAuthenticated()
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
                        metrics.webSocketRateLimited()
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
                        metrics.webSocketRateLimited()
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
                metrics.webSocketClosed()
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
    stableRateLimitKey(request.origin.remoteHost)

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
private class UnverifiedEmailException : RuntimeException()

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
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    webSession: Boolean = false,
    cookieConfiguration: WebSessionCookieConfiguration? = null,
    clearCookieOnFailure: Boolean = false
) {
    when (result) {
        is AuthResult.Success -> {
            response.header(HttpHeaders.CacheControl, "no-store")
            if (webSession) {
                val configuration = requireNotNull(cookieConfiguration)
                setWebSessionCookie(configuration, result.session)
                respond(successStatus, result.session.copy(refreshToken = ""))
            } else {
                respond(successStatus, result.session)
            }
        }
        is AuthResult.Failure -> {
            if (clearCookieOnFailure && cookieConfiguration != null) {
                clearWebSessionCookie(cookieConfiguration)
            }
            respond(
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
}

private data class WebSessionCookieConfiguration(val production: Boolean) {
    val name: String = if (production) "__Host-fasttowin_refresh" else "fasttowin_refresh_dev"
}

private fun buildAllowedWebOrigins(
    environment: String,
    configuredOrigins: String?
): List<AllowedWebOrigin> {
    val configured = parseAllowedWebOrigins(configuredOrigins, requireHttps = environment != "dev")
    if (environment != "dev") return configured
    return (configured + DEV_WEB_ORIGINS).distinct()
}

private suspend fun ApplicationCall.resolveWebSessionRequest(
    devicePlatform: String?,
    allowedOrigins: List<AllowedWebOrigin>
): Boolean? {
    val hasWebHeader = request.header(WEB_SESSION_HEADER) == WEB_SESSION_HEADER_VALUE
    val isWebPlatform = devicePlatform?.trim()?.equals("web", ignoreCase = true) == true
    if (isWebPlatform && !hasWebHeader) {
        rejectWebSessionRequest()
        return null
    }
    if (!hasWebHeader) return false

    val origin = request.header(HttpHeaders.Origin)
    val csrfHeader = request.header(WEB_CSRF_HEADER)
    val allowed = origin != null &&
        csrfHeader == WEB_CSRF_HEADER_VALUE &&
        allowedOrigins.any { "${it.scheme}://${it.authority}".equals(origin, ignoreCase = true) }
    if (!allowed) {
        rejectWebSessionRequest()
        return null
    }
    return true
}

private suspend fun ApplicationCall.rejectWebSessionRequest() {
    respond(
        HttpStatusCode.Forbidden,
        AuthErrorResponse(
            code = "INVALID_WEB_SESSION_REQUEST",
            message = "Yêu cầu phiên Web không hợp lệ. Vui lòng tải lại trang."
        )
    )
}

private fun ApplicationCall.setWebSessionCookie(
    configuration: WebSessionCookieConfiguration,
    session: com.hienthai.fastowin.protocol.AuthSessionResponse
) {
    val maxAgeSeconds = ((session.refreshExpiresAtEpochMillis - System.currentTimeMillis()) / 1_000L)
        .coerceAtLeast(1L)
    response.cookies.append(
        name = configuration.name,
        value = session.refreshToken,
        encoding = CookieEncoding.RAW,
        maxAge = maxAgeSeconds,
        path = "/",
        secure = configuration.production,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict")
    )
}

private fun ApplicationCall.clearWebSessionCookie(configuration: WebSessionCookieConfiguration) {
    response.cookies.append(
        name = configuration.name,
        value = "",
        encoding = CookieEncoding.RAW,
        maxAge = 0,
        path = "/",
        secure = configuration.production,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Strict")
    )
}

private const val WEB_SESSION_HEADER = "X-FastToWin-Web-Session"
private const val WEB_SESSION_HEADER_VALUE = "1"
private const val WEB_CSRF_HEADER = "X-FastToWin-CSRF"
private const val WEB_CSRF_HEADER_VALUE = "1"
private val DEV_WEB_ORIGINS = listOf(
    AllowedWebOrigin("localhost:8081", "http"),
    AllowedWebOrigin("localhost:8082", "http"),
    AllowedWebOrigin("127.0.0.1:8081", "http"),
    AllowedWebOrigin("127.0.0.1:8082", "http")
)
private val QUIET_LOG_PATHS = setOf("/health", "/internal/metrics")

private suspend fun ApplicationCall.respondAccountAction(
    result: AccountActionResult,
    exposeResetToken: Boolean = false,
    exposeEmailVerificationCode: Boolean = false
) {
    when (result) {
        is AccountActionResult.Success -> respond(
            HttpStatusCode.OK,
            AccountActionResponse(
                message = result.message,
                devResetToken = result.resetToken.takeIf { exposeResetToken },
                devEmailVerificationCode = result.emailVerificationCode
                    .takeIf { exposeEmailVerificationCode },
                emailVerified = result.emailVerified
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
private const val PUSH_REMINDER_INTERVAL_MILLIS = 15L * 60L * 1_000L
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
