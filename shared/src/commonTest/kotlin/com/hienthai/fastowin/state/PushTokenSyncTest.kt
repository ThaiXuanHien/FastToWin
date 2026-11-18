package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.InMemoryResumeTokenStore
import com.hienthai.fastowin.data.network.SocketSession
import com.hienthai.fastowin.data.network.SocketTransport
import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenSyncTest {
    @Test
    fun disablingPushClearsTheServerTokenOnceAndNeverBeforeARealToken() = runTest {
        val transport = Transport()
        val controller = GameController("ws://test", InMemoryResumeTokenStore(), transport, backgroundScope, "Owner", { "access" })
        runCurrent()
        transport.server(ServerMessage.SessionReady("owner")); runCurrent()

        controller.sendFcmToken("")
        controller.sendFcmToken("   ")
        runCurrent()
        assertEquals(emptyList(), transport.fcmTokens())

        controller.sendFcmToken(" device-token ")
        runCurrent()
        controller.sendFcmToken("")
        controller.sendFcmToken("")
        runCurrent()
        assertEquals(listOf("device-token", ""), transport.fcmTokens())
    }

    private class Transport : SocketTransport {
        private val incoming = Channel<Frame>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
            block(object : SocketSession {
                override val incoming get() = this@Transport.incoming
                override suspend fun send(frame: Frame) { if (frame is Frame.Text) sent += frame.readText() }
                override suspend fun close() { incoming.close() }
                override suspend fun closeReason(): String? = null
            })
        }
        fun server(message: ServerMessage) {
            check(incoming.trySend(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(message))).isSuccess)
        }
        fun fcmTokens(): List<String> = sent.mapNotNull {
            (runCatching { ProtocolJson.decodeFromString<ClientMessage>(it) }.getOrNull() as? ClientMessage.UpdateFcmToken)?.token
        }
    }
}
