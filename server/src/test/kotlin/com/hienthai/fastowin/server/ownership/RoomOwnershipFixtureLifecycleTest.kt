package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RoomPhase
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.StoredActiveRoom
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.server.routing.routing
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse

class RoomOwnershipFixtureLifecycleTest {
    @Test
    fun `leaving a playing match finishes then explicitly closes the room`() = testApplication {
        val roomId = UUID.randomUUID().toString()
        val finished = ServerMessage.GameFinished(StoredActiveRoom(roomId, roomName = "Fixture",
            host = StoredActivePlayer(UUID.randomUUID().toString(), "Host", true),
            gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.FINISHED).classicSnapshot())
        application {
            install(ServerWebSockets)
            routing { webSocket("/game") {
                incoming.receive()
                send(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(finished)))
                incoming.receive() // Finishing the match does not acknowledge closing its room.
                send(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(ServerMessage.RoomClosed(roomId, "Left"))))
            } }
        }
        val wsClient = createClient { install(ClientWebSockets) }
        val socket = AcceptanceSocket(wsClient.webSocketSession("/game"), 0)
        try { withTimeout(2_000) { socket.leaveRoom(roomId) } }
        finally { socket.closeGracefully() }
    }

    @Test
    fun `recycle waits for current room leave acknowledgement despite an old finished frame`() = testApplication {
        val roomId = UUID.randomUUID().toString()
        val finished = ServerMessage.GameFinished(StoredActiveRoom(roomId, roomName = "Fixture",
            host = StoredActivePlayer(UUID.randomUUID().toString(), "Host", true),
            gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.FINISHED).classicSnapshot())
        val receivedLeave = CompletableDeferred<Unit>()
        val allowAck = CompletableDeferred<Unit>()
        application {
            install(ServerWebSockets)
            routing { webSocket("/game") {
                send(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(finished)))
                incoming.receive()
                receivedLeave.complete(Unit)
                send(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(ServerMessage.RoomClosed("different-room", "Old"))))
                allowAck.await()
                send(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(ServerMessage.RoomClosed(roomId, "Left"))))
            } }
        }
        val wsClient = createClient { install(ClientWebSockets) }
        val socket = AcceptanceSocket(wsClient.webSocketSession("/game"), 0)
        coroutineScope {
            val leaving = async { socket.leaveRoom(roomId) }
            try {
                withTimeout(2_000) { receivedLeave.await() }
                val completedBeforeAck = withTimeoutOrNull(500) { leaving.await(); true } ?: false
                assertFalse(completedBeforeAck, "A stale finished/other-room frame is not a leave ACK")
                allowAck.complete(Unit)
                withTimeout(2_000) { leaving.await() }
            } finally {
                allowAck.complete(Unit)
                leaving.cancelAndJoin()
                socket.closeGracefully()
            }
        }
    }
}
