package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
import com.hienthai.fastowin.localization.TextKey
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation

class GameSocketClientTest {
    @Test
    fun `closed attempt is discarded before retry`() = runTest {
        val transport = RecordingSocketTransport(failAttempts = 1)
        val client = testSocketClient(transport)
        backgroundScope.launch { client.connect("Hiền") }
        advanceUntilIdle()

        assertEquals(2, transport.createdSessionIds.distinct().size)
        assertEquals(null, transport.sentAfterClose)
    }

    @Test
    fun `retry now cancels pending delay and opens immediately`() = runTest {
        val transport = RecordingSocketTransport(failAttempts = Int.MAX_VALUE)
        val sleeper = ControllableRetrySleeper()
        val client = testSocketClient(transport, sleeper)
        backgroundScope.launch { client.connect("Hiền") }
        runCurrent()
        client.retryNow()
        runCurrent()
        assertEquals(2, transport.createdSessionIds.size)
    }
    @Test
    fun `replacement close stops reconnect loop`() {
        assertFalse(shouldReconnectAfterSocketClose(SESSION_REPLACED_CLOSE_REASON))
        assertTrue(shouldReconnectAfterSocketClose("Network failure"))
        assertTrue(shouldReconnectAfterSocketClose(null))
    }

    @Test
    fun `client generated socket errors expose stable localization keys`() {
        assertEquals(
            TextKey.ServerInvalidRequest.name,
            socketClientError("PROTOCOL_DECODE_FAILED", "Legacy fallback").messageKey
        )
        assertEquals(
            TextKey.ServerUnavailable.name,
            socketClientError("CONNECTION_FAILED", "Legacy fallback").messageKey
        )
        assertEquals(
            TextKey.ServerSessionExpired.name,
            socketClientError("SESSION_REPLACED", "Legacy fallback").messageKey
        )
    }
}

private fun testSocketClient(
    transport: RecordingSocketTransport,
    sleeper: RetrySleeper = RetrySleeper { }
) = GameSocketClient(
    serverUrl = "ws://test",
    tokenStore = InMemoryResumeTokenStore(),
    retrySleeper = sleeper,
    retryJitter = RetryJitter { 0L },
    transport = transport
)

private class ControllableRetrySleeper : RetrySleeper {
    override suspend fun sleep(delayMillis: Long) = kotlinx.coroutines.awaitCancellation()
}

private class RecordingSocketTransport(private val failAttempts: Int) : SocketTransport {
    val createdSessionIds = mutableListOf<Int>()
    var sentAfterClose: String? = null
    private var nextId = 1

    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        val id = nextId++
        createdSessionIds += id
        val session = RecordingSession { sentAfterClose = it }
        if (id <= failAttempts) session.failOnSend = true
        try { block(session) } finally { session.close() }
    }
}

private class RecordingSession(private val onStaleSend: (String) -> Unit) : SocketSession {
    override val incoming = kotlinx.coroutines.channels.Channel<io.ktor.websocket.Frame>(0)
    var failOnSend = false
    private var closed = false
    override suspend fun send(frame: io.ktor.websocket.Frame) {
        if (closed) onStaleSend("stale")
        if (failOnSend) error("failed")
    }
    override suspend fun close() { closed = true; incoming.close() }
    override suspend fun closeReason(): String? = null
}
