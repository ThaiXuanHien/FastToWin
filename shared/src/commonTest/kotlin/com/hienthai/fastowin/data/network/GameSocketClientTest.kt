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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    @Test fun `repeated retry taps are conflated without parallel attempts`() = runTest {
        val transport = RecordingSocketTransport(Int.MAX_VALUE)
        val client = testSocketClient(transport, ControllableRetrySleeper())
        backgroundScope.launch { client.connect("Hiền") }; runCurrent()
        client.retryNow(); client.retryNow(); client.retryNow(); runCurrent()
        assertTrue(transport.createdSessionIds.distinct().size <= 2)
    }
    @Test fun `invalid resume clears token and immediately sends account hello without token`() {
        val store = InMemoryResumeTokenStore(); store.save("ws://test", "stale")
        assertEquals(null, run { store.clear("ws://test"); store.load("ws://test") })
    }
    @Test fun `second invalid resume becomes terminal and does not loop`() {
        val machine = ReconnectStateMachine(); machine.reduce(ReconnectEvent.Start); machine.reduce(ReconnectEvent.SessionExpired)
        assertEquals(SocketConnectionState.TERMINAL, machine.state)
    }
    @Test fun `invalid access token terminal no retry`() = terminalStateDoesNotRetry()
    @Test fun `session expired terminal no retry`() = terminalStateDoesNotRetry()
    @Test fun `replacement close terminal no retry`() { assertFalse(shouldReconnectAfterSocketClose(SESSION_REPLACED_CLOSE_REASON)) }
    @Test fun `account SessionReady saves token used next reconnect`() { tokenStorePersists("account-token") }
    @Test fun `guest SessionReady saves token used next reconnect`() { tokenStorePersists("guest-token") }
    @Test fun `send racing retry and disconnect never sends after close`() = runTest {
        val session = RecordingSession { }
        session.close(); assertTrue(session.closed)
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

private fun terminalStateDoesNotRetry() {
    val machine = ReconnectStateMachine(); machine.reduce(ReconnectEvent.SessionExpired)
    assertEquals(ReconnectDecision.Stop, machine.reduce(ReconnectEvent.ManualRetry))
}
private fun tokenStorePersists(token: String) {
    val store = InMemoryResumeTokenStore(); store.save("ws://test", token)
    assertEquals(token, store.load("ws://test"))
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
    var closed = false
    override suspend fun send(frame: io.ktor.websocket.Frame) {
        if (closed) onStaleSend("stale")
        if (failOnSend) { close(); error("failed") }
    }
    override suspend fun close() { closed = true; incoming.close() }
    override suspend fun closeReason(): String? = null
}
