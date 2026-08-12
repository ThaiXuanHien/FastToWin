package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
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

fun Application.gameModule(engine: GameEngine = GameEngine()) {
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

    routing {
        get("/health") { call.respondText("OK") }

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
                        if (message !is ClientMessage.ConnectGuest) {
                            send(ProtocolJson.encodeToString<ServerMessage>(
                                ServerMessage.Error("AUTH_REQUIRED", "Hãy khởi tạo phiên chơi trước.")
                            ))
                            continue
                        }
                        if (message.protocolVersion != com.hienthai.fastowin.protocol.PROTOCOL_VERSION) {
                            send(ProtocolJson.encodeToString<ServerMessage>(
                                ServerMessage.Error("PROTOCOL_MISMATCH", "Phiên bản ứng dụng không tương thích.")
                            ))
                            continue
                        }

                        val guest = runCatching {
                            engine.connectGuest(message.displayName, message.resumeToken)
                        }.getOrElse {
                            send(ProtocolJson.encodeToString<ServerMessage>(
                                ServerMessage.Error("INVALID_NAME", it.message ?: "Tên người chơi không hợp lệ.")
                            ))
                            continue
                        }
                        playerId = guest.playerId
                        val connection = SocketConnection(this)
                        connections.put(guest.playerId, connection)?.closeForReplacement()
                        connection.send(
                            ServerMessage.SessionReady(
                                playerId = guest.playerId,
                                resumeToken = guest.resumeToken,
                                currentGame = guest.currentGame
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
