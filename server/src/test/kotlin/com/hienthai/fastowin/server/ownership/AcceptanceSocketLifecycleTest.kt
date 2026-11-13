package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AcceptanceSocketLifecycleTest {
    @Test fun `results exit retries a temporary ownership revision race`() = runBlocking {
        withSocket("ROOM_RECOVERING") { socket, calls ->
            withTimeout(5_000) { socket.leaveRoom("fixture-room") }
            assertEquals(2, calls.get())
        }
    }

    @Test fun `results exit does not hide a permanent protocol rejection`() = runBlocking {
        withSocket("NOT_IN_ROOM") { socket, calls ->
            assertFailsWith<AcceptanceRejected> { socket.leaveRoom("fixture-room") }
            assertEquals(1, calls.get())
        }
    }

    @Test fun `recycled create accepts the authoritative waiting snapshot for the requested room`() = runBlocking {
        val host = "fixture-host"
        val game = GameSnapshot("new-room", roomName = "Attempt 12-2", hostId = host,
            gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.WAITING,
            players = listOf(PlayerSnapshot(host, "Host", 0)))
        assertEquals(game, matchCreatedRoom(ServerMessage.RoomCreated(game), game.roomName, host, emptySet()))
        assertEquals(game, matchCreatedRoom(ServerMessage.RoomUpdated(game), game.roomName, host, emptySet()))
        assertEquals(null, matchCreatedRoom(ServerMessage.RoomUpdated(game.copy(roomName = "Attempt 12-1")),
            game.roomName, host, emptySet()))
        assertEquals(null, matchCreatedRoom(ServerMessage.RoomUpdated(game), game.roomName, host, setOf(game.roomId)))
        assertEquals(null, matchCreatedRoom(ServerMessage.RoomUpdated(game.copy(hostId = "other")),
            game.roomName, host, emptySet()))
        assertEquals(null, matchCreatedRoom(ServerMessage.RoomUpdated(game.copy(phase = RoomPhase.PLAYING)),
            game.roomName, host, emptySet()))
    }

    @Test fun `recycle leaves the room carried by SessionReady before creating a new one`() = runBlocking {
        val old = GameSnapshot("previous-room", roomName = "Previous", hostId = "fixture-host",
            gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.FINISHED,
            players = listOf(PlayerSnapshot("fixture-host", "Host", 0)))
        withSocket("ROOM_RECOVERING") { socket, calls ->
            withTimeout(5_000) { socket.releasePreviousRoom(old.roomId) }
            assertEquals(2, calls.get())
        }
    }

    private suspend fun withSocket(code: String, block: suspend (AcceptanceSocket, AtomicInteger) -> Unit) {
        val calls = AtomicInteger()
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            install(io.ktor.server.websocket.WebSockets)
            routing { webSocket("/game") {
                for (frame in incoming) if (frame is Frame.Text) {
                    val message = ProtocolJson.decodeFromString<ClientMessage>(frame.readText())
                    check(message is ClientMessage.LeaveRoom)
                    val response: ServerMessage = if (calls.incrementAndGet() == 1)
                        ServerMessage.Error(code, "Fixed fixture error")
                    else ServerMessage.RoomClosed(message.roomId, "Left results")
                    send(ProtocolJson.encodeToString(response))
                }
            } }
        }.start()
        try {
            HttpClient(OkHttp) { install(WebSockets) }.use { client ->
                val port = server.engine.resolvedConnectors().single().port
                val socket = AcceptanceSocket(client.webSocketSession("ws://127.0.0.1:$port/game"), 0)
                try { block(socket, calls) } finally { socket.close() }
            }
        } finally { server.stop(0, 1_000) }
    }
}