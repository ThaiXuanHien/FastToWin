package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.LoginRequest
import com.hienthai.fastowin.protocol.LogoutRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RefreshTokenRequest
import com.hienthai.fastowin.protocol.RegisterRequest
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.websocket.WebSockets
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

fun Application.gameModule(
    engine: GameEngine = GameEngine(),
    authService: AuthenticationService = AuthenticationService(InMemoryAuthRepository())
) {
    install(ContentNegotiation) {
        json(ProtocolJson)
    }
    install(WebSockets) {
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
            val request = call.receiveOrReject<LoginRequest>() ?: return@post
            call.respondAuthResult(
                authService.login(request.email, request.password, request.devicePlatform)
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

        webSocket("/game") {
            var playerId: String? = null
            try {
                for (frame in incoming) {
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

                        val connected = runCatching {
                            when (message) {
                                is ClientMessage.ConnectGuest ->
                                    engine.connectGuest(message.displayName, message.resumeToken)
                                is ClientMessage.ConnectAccount -> {
                                    val account = authService.authenticateAccessToken(message.accessToken)
                                        ?: throw InvalidAccessTokenException()
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
                        continue
                    }

                    deliver(engine.handle(playerId, message))
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
                "INVALID_CREDENTIALS", "INVALID_REFRESH_TOKEN" -> HttpStatusCode.Unauthorized
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
