package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.SocketSession
import com.hienthai.fastowin.data.network.SocketTransport
import com.hienthai.fastowin.data.network.InMemoryResumeTokenStore
import com.hienthai.fastowin.protocol.GoldExchangeStatus
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class GoldExchangeFeedbackTest {
    @Test
    fun grantedReceiptSurvivesLeavingAndReopeningShopUntilItIsAcknowledged() = runTest {
        val transport = Transport()
        val controller = GameController("ws://test", InMemoryResumeTokenStore(), transport, backgroundScope, "Owner", { "access" })
        try {
            runCurrent()
            transport.server(ServerMessage.SessionReady("owner")); runCurrent()
            controller.openShop(); controller.closeShop(); runCurrent()
            val receipt = ServerMessage.GoldExchangeResult("receipt-1", "gold_bag", receivedGold = 1000, status = GoldExchangeStatus.GRANTED)
            transport.server(receipt); runCurrent()
            assertNull(controller.uiState.value.profileNotice)
            controller.openShop(); runCurrent()
            assertEquals(receipt, controller.uiState.value.goldExchangeResult)
            controller.clearGoldExchangeResult("different-receipt")
            assertEquals(receipt, controller.uiState.value.goldExchangeResult)
            controller.clearGoldExchangeResult(receipt.requestId)
            controller.closeShop(); controller.openShop(); runCurrent()
            assertNull(controller.uiState.value.goldExchangeResult)
        } finally { controller.close() }
    }

    private class Transport : SocketTransport {
        private val incoming = Channel<Frame>(Channel.UNLIMITED)
        override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
            block(object : SocketSession {
                override val incoming get() = this@Transport.incoming
                override suspend fun send(frame: Frame) = Unit
                override suspend fun close() { incoming.close() }
                override suspend fun closeReason(): String? = null
            })
        }
        fun server(message: ServerMessage) {
            check(incoming.trySend(Frame.Text(ProtocolJson.encodeToString<ServerMessage>(message))).isSuccess)
        }
    }
}
