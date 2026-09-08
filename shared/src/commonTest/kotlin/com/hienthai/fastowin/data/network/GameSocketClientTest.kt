package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.*
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameSocketClientTest {
    @Test fun `retry and disconnect progress when outer transport teardown stalls`() = runTest {
        val transport = OuterTeardownStallingTransport()
        val client = GameSocketClient("ws://test", InMemoryResumeTokenStore(), transport = transport)
        val connection = backgroundScope.launch { client.connect("Hiền") }
        transport.firstSessionStarted.await()

        client.retryNow()
        transport.firstOuterTeardownStarted.await()
        advanceTimeBy(SHUTDOWN_BOUND_MILLIS + 1)
        runCurrent()
        val replacementStartedWithinBound = transport.replacementStarted.isCompleted
        if (!replacementStartedWithinBound) connection.cancelAndJoin()

        if (replacementStartedWithinBound) {
            client.sendMessage(ClientMessage.GetProfile)
            runCurrent()
            val disconnecting = launch { client.disconnect() }
            advanceTimeBy(SHUTDOWN_BOUND_MILLIS + 1)
            runCurrent()
            disconnecting.join()
            assertTrue(disconnecting.isCompleted)
        }

        assertTrue(replacementStartedWithinBound)
        assertEquals(
            listOf<ClientMessage>(ClientMessage.ConnectGuest("Hiền", null), ClientMessage.GetProfile),
            transport.replacement.sent
        )
        assertEquals(SocketConnectionState.DISCONNECTED, client.connectionState.value)
        transport.releaseFirstOuterTeardown.complete(Unit)
        runCurrent()
    }

    @Test fun `late ignored transport callback cannot mutate replacement`() = runTest {
        val transport = LateCallbackTransport()
        val messages = mutableListOf<ServerMessage>()
        val client = GameSocketClient("ws://test", InMemoryResumeTokenStore(), transport = transport)
        backgroundScope.launch { client.messages.collect { messages += it } }
        val oldConnection = backgroundScope.launch { client.connect("Hiền") }
        transport.firstTransportStarted.await()

        val disconnecting = launch { client.disconnect() }
        runCurrent()
        advanceTimeBy(SHUTDOWN_BOUND_MILLIS + 1)
        runCurrent()
        disconnecting.join()
        oldConnection.join()
        val replacement = backgroundScope.launch { client.connect("New player") }
        transport.replacementStarted.await()
        client.sendMessage(ClientMessage.GetProfile)
        runCurrent()

        transport.allowLateCallback.complete(Unit)
        transport.lateCallbackStarted.await()
        runCurrent()
        val oldCloseWasStarted = transport.old.closeStarted.isCompleted
        client.sendMessage(ClientMessage.ListRooms)
        runCurrent()

        assertTrue(oldCloseWasStarted)
        assertEquals(SocketConnectionState.AUTHENTICATING, client.connectionState.value)
        assertEquals(emptyList(), messages)
        assertEquals(0, transport.old.sendCount)
        assertEquals(1, transport.maxOwnedSessions)
        assertEquals(
            listOf<ClientMessage>(
                ClientMessage.ConnectGuest("New player", null),
                ClientMessage.GetProfile,
                ClientMessage.ListRooms
            ),
            transport.replacement.sent
        )
        assertTrue(replacement.isActive)
        assertFalse(transport.replacement.closeStarted.isCompleted)
        replacement.cancelAndJoin()
    }

    @Test fun `disconnect completes when transport never enters websocket block`() = runTest {
        val transport = HandshakeStallingTransport()
        val client = GameSocketClient("ws://test", InMemoryResumeTokenStore(), transport = transport)
        val connection = backgroundScope.launch { client.connect("Hiền") }
        transport.handshakeStarted.await()

        val disconnecting = launch { client.disconnect() }
        runCurrent()
        advanceTimeBy(SHUTDOWN_BOUND_MILLIS + 1)
        runCurrent()
        val completedWithinBound = disconnecting.isCompleted
        if (!completedWithinBound) connection.cancelAndJoin()
        disconnecting.join()

        assertTrue(completedWithinBound)
        assertEquals(SocketConnectionState.DISCONNECTED, client.connectionState.value)
        assertEquals(1, transport.handshakeCount)
        assertFalse(connection.isActive)
    }

    @Test fun `disconnect completes when session close never returns`() = runTest {
        val transport = CloseStallingTransport()
        val errors = mutableListOf<ServerMessage.Error>()
        val client = GameSocketClient("ws://test", InMemoryResumeTokenStore(), transport = transport)
        backgroundScope.launch { client.messages.collect { if (it is ServerMessage.Error) errors += it } }
        val connection = backgroundScope.launch { client.connect("Hiền") }
        transport.sessionStarted.await()

        val disconnecting = launch { client.disconnect() }
        transport.session.closeStarted.await()
        advanceTimeBy(SHUTDOWN_BOUND_MILLIS + 1)
        runCurrent()
        val completedWithinBound = disconnecting.isCompleted
        if (!completedWithinBound) connection.cancelAndJoin()
        disconnecting.join()
        client.sendMessage(ClientMessage.GetProfile)
        runCurrent()

        assertTrue(completedWithinBound)
        assertFalse(connection.isActive)
        assertEquals(SocketConnectionState.DISCONNECTED, client.connectionState.value)
        assertEquals(listOf<ClientMessage>(ClientMessage.ConnectGuest("Hiền", null)), transport.session.sent)
        assertEquals(0, transport.staleSends)
        assertEquals("CONNECTION_NOT_READY", errors.single().code)
    }

    @Test fun `reset can reconnect after stalled transport teardown`() = runTest {
        val transport = FirstHandshakeStallsThenSessionTransport()
        val client = GameSocketClient("ws://test", InMemoryResumeTokenStore(), transport = transport)
        val oldConnection = backgroundScope.launch { client.connect("Hiền") }
        transport.firstHandshakeStarted.await()

        val reset = launch {
            client.disconnect()
            oldConnection.cancelAndJoin()
            backgroundScope.launch { client.connect("New player") }
        }
        runCurrent()
        advanceTimeBy(SHUTDOWN_BOUND_MILLIS + 1)
        val completedWithinBound = reset.isCompleted
        if (!completedWithinBound) oldConnection.cancelAndJoin()
        reset.join()
        transport.replacementStarted.await()
        client.sendMessage(ClientMessage.GetProfile)
        runCurrent()

        assertTrue(completedWithinBound)
        assertEquals(2, transport.connectionCount)
        assertEquals(SocketConnectionState.AUTHENTICATING, client.connectionState.value)
        assertEquals(
            listOf(ClientMessage.ConnectGuest("New player", null), ClientMessage.GetProfile),
            transport.replacement.sent
        )
        assertEquals(0, transport.staleSends)
    }

    @Test fun `disconnect acknowledges cleanup and next connect does not inherit stop`() = runTest {
        val f = Fixture(this)
        f.start()
        val old = f.transport.sessions.single()
        old.closeGate = CompletableDeferred()
        val stopping = launch(start = CoroutineStart.UNDISPATCHED) { f.client.disconnect() }
        runCurrent()
        val returnedBeforeClose = stopping.isCompleted
        old.closeGate!!.complete(Unit)
        stopping.join()
        val replacement = backgroundScope.launch { f.client.connect("New player") }
        runCurrent()
        assertFalse(returnedBeforeClose)
        assertTrue(old.closed)
        assertEquals(2, f.transport.sessions.size)
        assertFalse(f.transport.sessions.last().closed)
        assertTrue(replacement.isActive)
        assertEquals(ClientMessage.ConnectGuest("New player", null), f.transport.sessions.last().sent.single())
    }

    @Test fun `cancellation restart cannot overlap old transport or inherit unconsumed stop`() = runTest {
        val f = Fixture(this)
        f.start()
        val old = f.transport.sessions.single()
        old.closeGate = CompletableDeferred()
        // Match the old resetGame race: request stop, cancel before its select
        // consumes the signal, then start another connect without joining.
        val stopping = launch(start = CoroutineStart.UNDISPATCHED) { f.client.disconnect() }
        f.connection.cancel()
        val replacement = backgroundScope.launch { f.client.connect("New player") }
        runCurrent()
        val attemptsDuringCleanup = f.transport.sessions.size
        old.closeGate!!.complete(Unit)
        runCurrent()
        stopping.join()
        f.connection.join()
        assertEquals(1, attemptsDuringCleanup)
        assertEquals(1, f.transport.maxConcurrentLiveSessions)
        assertEquals(2, f.transport.sessions.size)
        assertTrue(replacement.isActive)
        assertFalse(f.transport.sessions.last().closed)
        f.client.sendMessage(ClientMessage.GetProfile)
        runCurrent()
        assertEquals(ClientMessage.GetProfile, f.transport.sessions.last().sent.last())
        assertEquals(emptyList(), f.transport.staleSends)
    }

    @Test fun `cancelled owner cleanup cannot detach or close replacement mailbox`() = runTest {
        val f = Fixture(this)
        f.start()
        val old = f.transport.sessions.single()
        old.closeGate = CompletableDeferred()
        f.connection.cancel()
        val replacement = backgroundScope.launch { f.client.connect("New player") }
        runCurrent()
        val attemptsDuringCleanup = f.transport.sessions.size
        old.closeGate!!.complete(Unit)
        runCurrent()
        f.connection.join()
        f.client.sendMessage(ClientMessage.GetProfile)
        runCurrent()
        assertEquals(1, attemptsDuringCleanup)
        assertEquals(1, f.transport.maxConcurrentLiveSessions)
        assertTrue(replacement.isActive)
        assertFalse(f.transport.sessions.last().closed)
        assertEquals(SocketConnectionState.AUTHENTICATING, f.client.connectionState.value)
        assertTrue(f.client.isConnected.value)
        assertEquals(ClientMessage.GetProfile, f.transport.sessions.last().sent.last())
        assertEquals(emptyList(), f.errors)
        assertEquals(emptyList(), f.transport.staleSends)
    }

    @Test fun `reset style disconnect join restart leaves only replacement alive`() = runTest {
        val f = Fixture(this)
        f.start()
        val old = f.transport.sessions.single()
        old.closeGate = CompletableDeferred()
        val reset = launch {
            f.client.disconnect()
            f.connection.cancelAndJoin()
            backgroundScope.launch { f.client.connect("New player") }
        }
        runCurrent()
        val returnedBeforeClose = reset.isCompleted
        old.closeGate!!.complete(Unit)
        reset.join()
        runCurrent()
        assertFalse(returnedBeforeClose)
        assertEquals(1, f.transport.maxConcurrentLiveSessions)
        assertEquals(2, f.transport.sessions.size)
        assertTrue(f.connection.isCompleted)
        assertFalse(f.transport.sessions.last().closed)
        f.client.sendMessage(ClientMessage.GetProfile)
        runCurrent()
        assertEquals(ClientMessage.GetProfile, f.transport.sessions.last().sent.last())
    }

    @Test fun `closed attempt is discarded before retry`() = runTest {
        val f = Fixture(this)
        f.start()
        f.transport.sessions[0].remoteClose()
        runCurrent()
        f.client.sendMessage(ClientMessage.ListRooms)
        runCurrent()
        assertEquals("CONNECTION_NOT_READY", f.errors.last().code)
        f.sleeper.release.send(Unit)
        runCurrent()
        val send = launch { f.client.sendMessage(ClientMessage.GetProfile) }
        runCurrent()
        send.join()
        assertEquals(listOf(1, 2), f.transport.sessions.map { it.id })
        assertEquals(listOf<ClientMessage>(ClientMessage.ConnectGuest("Hiền", null)), f.transport.sessions[0].sent)
        assertEquals(ClientMessage.GetProfile, f.transport.sessions[1].sent.last())
        assertEquals(emptyList(), f.transport.staleSends)
        assertEquals(1, f.transport.maxConcurrentLiveSessions)
    }

    @Test fun `retry now cancels pending delay and opens immediately`() = runTest {
        val f = Fixture(this)
        f.start()
        f.transport.sessions[0].remoteClose()
        runCurrent()
        assertEquals(listOf(1_000L), f.sleeper.delays)
        assertEquals(SocketConnectionState.RECONNECTING, f.client.connectionState.value)
        f.client.retryNow()
        runCurrent()
        assertEquals(2, f.transport.sessions.size)
        assertEquals(1, f.sleeper.cancelled)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun `repeated retry taps are conflated without parallel attempts`() = runTest {
        val f = Fixture(this)
        f.start()
        val old = f.transport.sessions.single()
        old.closeGate = CompletableDeferred()
        repeat(3) { f.client.retryNow() }
        runCurrent()
        val closeWasStarted = old.closeStarted.isCompleted
        repeat(3) { f.client.retryNow() }
        runCurrent()
        val attemptsDuringClose = f.transport.sessions.size
        old.closeGate!!.complete(Unit)
        runCurrent()
        assertTrue(closeWasStarted)
        assertEquals(1, attemptsDuringClose)
        assertEquals(2, f.transport.sessions.size)
        assertFalse(f.transport.sessions[1].closed)
        assertEquals(1, f.transport.maxConcurrentLiveSessions)
        runCurrent()
        assertEquals(2, f.transport.sessions.size)
        assertFalse(f.transport.sessions[1].closeStarted.isCompleted)
    }

    @Test fun `invalid resume clears token and immediately sends account hello without token`() = runTest {
        val f = Fixture(this, account = true, savedToken = "stale")
        f.start()
        assertEquals(ClientMessage.ConnectAccount("access", "stale"), f.transport.sessions[0].sent.single())
        f.transport.sessions[0].server(ServerMessage.Error("INVALID_RESUME_TOKEN", "Invalid resume"))
        runCurrent()
        assertNull(f.store.load("ws://test"))
        assertEquals(2, f.transport.sessions.size)
        assertTrue(f.transport.sessions[0].closed)
        assertEquals(ClientMessage.ConnectAccount("access", null), f.transport.sessions[1].sent.single())
        assertEquals(emptyList(), f.sleeper.delays)
        assertEquals(emptyList(), f.expired)
        f.transport.sessions[1].server(ServerMessage.SessionReady("p1", "fresh"))
        runCurrent()
        assertEquals(SocketConnectionState.CONNECTED, f.client.connectionState.value)
        assertEquals("fresh", f.store.load("ws://test"))
    }

    @Test fun `second invalid resume becomes terminal and does not loop`() = runTest {
        val f = Fixture(this, account = true, savedToken = "stale")
        f.start()
        repeat(2) {
            f.transport.sessions.last().server(ServerMessage.Error("INVALID_RESUME_TOKEN", "Invalid resume"))
            runCurrent()
        }
        f.client.retryNow()
        runCurrent()
        assertEquals(2, f.transport.sessions.size)
        assertEquals(SocketConnectionState.TERMINAL, f.client.connectionState.value)
        assertEquals(listOf("INVALID_RESUME_TOKEN"), f.expired)
        assertTrue(f.transport.sessions.all { it.closed })
        assertEquals(emptyList(), f.sleeper.delays)
    }

    @Test fun `invalid access token terminal no retry`() = runTest { terminalError("INVALID_ACCESS_TOKEN") }
    @Test fun `session expired terminal no retry`() = runTest { terminalError("SESSION_EXPIRED") }

    private suspend fun TestScope.terminalError(code: String) {
        val f = Fixture(this, account = true)
        f.start()
        f.transport.sessions.single().server(ServerMessage.Error(code, "Rejected"))
        runCurrent()
        repeat(3) { f.client.retryNow() }
        runCurrent()
        assertEquals(SocketConnectionState.TERMINAL, f.client.connectionState.value)
        assertEquals(1, f.transport.sessions.size)
        assertEquals(listOf(code), f.expired)
        assertEquals(code, f.errors.single().code)
        assertEquals(emptyList(), f.sleeper.delays)
        assertTrue(f.transport.sessions.single().closed)
    }

    @Test fun `replacement close terminal no retry`() = runTest {
        val f = Fixture(this, account = true)
        f.start()
        f.transport.sessions.single().remoteClose(SESSION_REPLACED_CLOSE_REASON)
        runCurrent()
        f.client.retryNow()
        runCurrent()
        assertEquals(SocketConnectionState.TERMINAL, f.client.connectionState.value)
        assertEquals(1, f.transport.sessions.size)
        assertEquals(listOf("SESSION_REPLACED"), f.expired)
        assertEquals(TextKey.ServerSessionExpired.name, f.errors.single().messageKey)
        assertEquals(emptyList(), f.sleeper.delays)
    }

    @Test fun `account SessionReady saves token used next reconnect`() = runTest { persistedToken(true) }
    @Test fun `guest SessionReady saves token used next reconnect`() = runTest { persistedToken(false) }

    private suspend fun TestScope.persistedToken(account: Boolean) {
        val f = Fixture(this, account = account)
        f.start()
        f.transport.sessions.single().server(ServerMessage.SessionReady("p1", "saved"))
        runCurrent()
        assertEquals("saved", f.store.load("ws://test"))
        assertEquals(SocketConnectionState.CONNECTED, f.client.connectionState.value)
        f.client.retryNow()
        runCurrent()
        assertEquals(2, f.transport.sessions.size)
        val expected = if (account) ClientMessage.ConnectAccount("access", "saved")
            else ClientMessage.ConnectGuest("Hiền", "saved")
        assertEquals(expected, f.transport.sessions[1].sent.single())
        assertEquals(listOf<ServerMessage>(ServerMessage.SessionReady("p1", "saved")), f.messages)
    }

    @Test fun `hello cannot race a retry close while access token is loading`() = runTest {
        val access = CompletableDeferred<String?>()
        val f = Fixture(this, provider = { access.await() })
        f.start()
        repeat(3) { f.client.retryNow() }
        runCurrent()
        access.complete("access")
        runCurrent()
        assertEquals(2, f.transport.sessions.size)
        assertEquals(emptyList(), f.transport.staleSends)
        assertFalse(f.transport.sessions[1].closed)
        assertEquals(1, f.transport.maxConcurrentLiveSessions)
    }

    @Test fun `send racing retry and disconnect never sends after close`() = runTest {
        val f = Fixture(this)
        f.start()
        val old = f.transport.sessions.single()
        old.sendGate = CompletableDeferred()
        val sending = launch { f.client.sendMessage(ClientMessage.ListRooms) }
        runCurrent()
        val sendWasStarted = old.sendStarted.isCompleted
        f.client.retryNow()
        val disconnecting = launch { f.client.disconnect() }
        runCurrent()
        val closedDuringSend = old.closed
        old.sendGate!!.complete(Unit)
        runCurrent()
        assertTrue(sendWasStarted)
        assertFalse(closedDuringSend)
        sending.join()
        disconnecting.join()
        f.client.sendMessage(ClientMessage.GetProfile)
        runCurrent()
        assertEquals(listOf(ClientMessage.ConnectGuest("Hiền", null), ClientMessage.ListRooms), old.sent)
        assertTrue(old.closed)
        assertEquals(emptyList(), f.transport.staleSends)
        assertEquals(1, f.transport.sessions.size)
        assertEquals(SocketConnectionState.DISCONNECTED, f.client.connectionState.value)
        assertEquals("CONNECTION_NOT_READY", f.errors.last().code)
    }

    @Test fun `send queued for old attempt is not replayed into replacement`() = runTest {
        val f = Fixture(this)
        f.start()
        val old = f.transport.sessions.single()
        old.closeGate = CompletableDeferred()
        f.client.retryNow()
        runCurrent()
        val sending = launch { f.client.sendMessage(ClientMessage.GetProfile) }
        runCurrent()
        old.closeGate!!.complete(Unit)
        runCurrent()
        sending.join()
        assertEquals(2, f.transport.sessions.size)
        assertEquals(listOf<ClientMessage>(ClientMessage.ConnectGuest("Hiền", null)), f.transport.sessions[1].sent)
        assertEquals(emptyList(), f.transport.staleSends)
        assertEquals("CONNECTION_NOT_READY", f.errors.last().code)
    }

    @Test fun `disconnect wakes pending backoff without opening replacement`() = runTest {
        val f = Fixture(this)
        f.start()
        f.transport.sessions.single().remoteClose()
        runCurrent()
        val stopping = launch { f.client.disconnect() }
        runCurrent()
        assertTrue(stopping.isCompleted)
        f.client.retryNow()
        runCurrent()
        assertEquals(1, f.transport.sessions.size)
        assertEquals(1, f.sleeper.cancelled)
        assertEquals(SocketConnectionState.DISCONNECTED, f.client.connectionState.value)
    }

    @Test fun `malformed frame emits localized error and next message still arrives`() = runTest {
        val f = Fixture(this)
        f.start()
        f.transport.sessions.single().incoming.send(Frame.Text("not-json"))
        f.transport.sessions.single().server(ServerMessage.RoomList(emptyList()))
        runCurrent()
        assertEquals(TextKey.ServerInvalidRequest.name, f.errors.single().messageKey)
        assertEquals(ServerMessage.RoomList(emptyList()), f.messages.last())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class Fixture(
    private val scope: TestScope,
    account: Boolean = false,
    savedToken: String? = null,
    provider: (suspend (Boolean) -> String?)? = if (account) ({ "access" }) else null
) {
    val transport = ScriptedTransport()
    val sleeper = ScriptedSleeper()
    val store = InMemoryResumeTokenStore().apply { savedToken?.let { save("ws://test", it) } }
    val expired = mutableListOf<String>()
    val messages = mutableListOf<ServerMessage>()
    val errors get() = messages.filterIsInstance<ServerMessage.Error>()
    val client = GameSocketClient("ws://test", store, provider, { code, _ -> expired += code }, sleeper, RetryJitter { 0L }, transport)
    lateinit var connection: Job
        private set
    fun start() {
        scope.backgroundScope.launch { client.messages.collect { messages += it } }
        connection = scope.backgroundScope.launch { client.connect("Hiền") }
        scope.runCurrent()
    }
}

private class ScriptedSleeper : RetrySleeper {
    val delays = mutableListOf<Long>()
    val release = Channel<Unit>(Channel.UNLIMITED)
    var cancelled = 0
    override suspend fun sleep(delayMillis: Long) {
        delays += delayMillis
        try { release.receive() } catch (e: kotlinx.coroutines.CancellationException) { cancelled++; throw e }
    }
}

private class ScriptedTransport : SocketTransport {
    val sessions = mutableListOf<ScriptedSession>()
    val staleSends = mutableListOf<Int>()
    var maxConcurrentLiveSessions = 0
    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        assertEquals("ws://test", url)
        val session = ScriptedSession(sessions.size + 1) { staleSends += it }
        sessions += session
        maxConcurrentLiveSessions = maxOf(maxConcurrentLiveSessions, sessions.count { !it.closed })
        try { block(session) } finally { withContext(NonCancellable) { session.close() } }
    }
}

private class ScriptedSession(val id: Int, private val stale: (Int) -> Unit) : SocketSession {
    override val incoming = Channel<Frame>(Channel.UNLIMITED)
    val sent = mutableListOf<ClientMessage>()
    val sendStarted = CompletableDeferred<Unit>()
    val closeStarted = CompletableDeferred<Unit>()
    var sendGate: CompletableDeferred<Unit>? = null
    var closeGate: CompletableDeferred<Unit>? = null
    var closed = false
        private set
    private var reason: String? = null
    override suspend fun send(frame: Frame) {
        sendGate?.let { sendStarted.complete(Unit); it.await() }
        if (closed) { stale(id); error("send after close: $id") }
        sent += ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText())
    }
    override suspend fun close() {
        if (closed) return
        closeStarted.complete(Unit)
        closeGate?.await()
        closed = true
        incoming.close()
    }
    override suspend fun closeReason(): String? = reason
    suspend fun server(message: ServerMessage) { incoming.send(Frame.Text(ProtocolJson.encodeToString(message))) }
    fun remoteClose(reason: String? = null) { this.reason = reason; closed = true; incoming.close() }
}

private class HandshakeStallingTransport : SocketTransport {
    val handshakeStarted = CompletableDeferred<Unit>()
    var handshakeCount = 0
        private set

    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        handshakeCount++
        handshakeStarted.complete(Unit)
        awaitCancellation()
    }
}

private class CloseStallingTransport : SocketTransport {
    val session = CloseStallingSession()
    val sessionStarted = CompletableDeferred<Unit>()
    var staleSends = 0
        private set

    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        sessionStarted.complete(Unit)
        block(session)
    }

    inner class CloseStallingSession : SocketSession {
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        val sent = mutableListOf<ClientMessage>()
        val closeStarted = CompletableDeferred<Unit>()

        override suspend fun send(frame: Frame) {
            if (closeStarted.isCompleted) staleSends++
            sent += ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText())
        }

        override suspend fun close() {
            closeStarted.complete(Unit)
            awaitCancellation()
        }

        override suspend fun closeReason(): String? = null
    }
}

private class FirstHandshakeStallsThenSessionTransport : SocketTransport {
    val firstHandshakeStarted = CompletableDeferred<Unit>()
    val replacementStarted = CompletableDeferred<Unit>()
    val replacement = ReplacementSession()
    var connectionCount = 0
        private set
    var staleSends = 0
        private set

    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        connectionCount++
        if (connectionCount == 1) {
            firstHandshakeStarted.complete(Unit)
            awaitCancellation()
        } else {
            replacementStarted.complete(Unit)
            block(replacement)
        }
    }

    inner class ReplacementSession : SocketSession {
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        val sent = mutableListOf<ClientMessage>()
        private var closed = false

        override suspend fun send(frame: Frame) {
            if (closed) staleSends++
            sent += ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText())
        }

        override suspend fun close() {
            closed = true
            incoming.close()
        }

        override suspend fun closeReason(): String? = null
    }
}

private class OuterTeardownStallingTransport : SocketTransport {
    val firstSessionStarted = CompletableDeferred<Unit>()
    val firstOuterTeardownStarted = CompletableDeferred<Unit>()
    val replacementStarted = CompletableDeferred<Unit>()
    val first = RecordingSession()
    val replacement = RecordingSession()
    val releaseFirstOuterTeardown = CompletableDeferred<Unit>()
    private var calls = 0

    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        calls++
        if (calls == 1) {
            firstSessionStarted.complete(Unit)
            block(first)
            firstOuterTeardownStarted.complete(Unit)
            withContext(NonCancellable) { releaseFirstOuterTeardown.await() }
        } else {
            replacementStarted.complete(Unit)
            block(replacement)
        }
    }
}

private class LateCallbackTransport : SocketTransport {
    val firstTransportStarted = CompletableDeferred<Unit>()
    val allowLateCallback = CompletableDeferred<Unit>()
    val lateCallbackStarted = CompletableDeferred<Unit>()
    val replacementStarted = CompletableDeferred<Unit>()
    val old = OwnedRecordingSession { ownedSessions++ }
    val replacement = OwnedRecordingSession { ownedSessions++ }
    private var calls = 0
    private var ownedSessions = 0
    var maxOwnedSessions = 0
        private set

    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        calls++
        if (calls == 1) {
            firstTransportStarted.complete(Unit)
            try {
                awaitCancellation()
            } catch (_: kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable) {
                    allowLateCallback.await()
                    lateCallbackStarted.complete(Unit)
                    block(old)
                }
            }
        } else {
            replacementStarted.complete(Unit)
            block(replacement)
        }
    }

    inner class OwnedRecordingSession(private val onOwned: () -> Unit) : SocketSession {
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        val sent = mutableListOf<ClientMessage>()
        val closeStarted = CompletableDeferred<Unit>()
        var sendCount = 0
            private set

        override suspend fun send(frame: Frame) {
            sendCount++
            if (sendCount == 1) {
                onOwned()
                maxOwnedSessions = maxOf(maxOwnedSessions, ownedSessions)
            }
            sent += ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText())
        }

        override suspend fun close() {
            closeStarted.complete(Unit)
            incoming.close()
        }

        override suspend fun closeReason(): String? = null
    }
}

private class RecordingSession : SocketSession {
    override val incoming = Channel<Frame>(Channel.UNLIMITED)
    val sent = mutableListOf<ClientMessage>()

    override suspend fun send(frame: Frame) {
        sent += ProtocolJson.decodeFromString<ClientMessage>((frame as Frame.Text).readText())
    }

    override suspend fun close() {
        incoming.close()
    }

    override suspend fun closeReason(): String? = null
}

private const val SHUTDOWN_BOUND_MILLIS = 1_000L
