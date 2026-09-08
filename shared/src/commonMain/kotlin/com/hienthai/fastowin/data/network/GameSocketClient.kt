package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.localization.protocolTextKeyForCode
import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal fun interface RetrySleeper { suspend fun sleep(delayMillis: Long) }
internal fun interface RetryJitter { fun nextMillis(): Long }

internal interface SocketSession {
    val incoming: ReceiveChannel<Frame>
    suspend fun send(frame: Frame)
    suspend fun close()
    suspend fun closeReason(): String?
}

internal interface SocketTransport {
    suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit)
}

private class KtorSocketTransport(private val client: HttpClient) : SocketTransport {
    override suspend fun webSocket(url: String, block: suspend (SocketSession) -> Unit) {
        client.webSocket(url) {
            val session = object : SocketSession {
                override val incoming get() = this@webSocket.incoming
                override suspend fun send(frame: Frame) = this@webSocket.send(frame)
                override suspend fun close() = this@webSocket.close()
                override suspend fun closeReason() = this@webSocket.closeReason.await()?.message
            }
            try {
                block(session)
            } finally {
                withContext(NonCancellable) {
                    try {
                        session.close()
                    } finally {
                        // Ktor's outer response cleanup joins in the caller's
                        // context, which may already be cancelled. Await the
                        // transport jobs here, never the connect owner's job.
                        this@webSocket.coroutineContext[Job]?.cancelAndJoin()
                        this@webSocket.call.coroutineContext[Job]?.cancelAndJoin()
                    }
                }
            }
        }
    }
}

/**
 * Owns one reconnect loop. A transport session never escapes that loop:
 * callers enqueue into the mailbox for the current attempt, and the owner
 * drains that mailbox while it owns the corresponding SocketSession.
 */
internal class GameSocketClient(
    private val serverUrl: String,
    private val tokenStore: ResumeTokenStore,
    private val accessTokenProvider: (suspend (forceRefresh: Boolean) -> String?)? = null,
    private val onAccountSessionExpired: ((code: String, fallback: String) -> Unit)? = null,
    retrySleeper: RetrySleeper = RetrySleeper { delay(it) },
    retryJitter: RetryJitter = RetryJitter { 0L },
    transport: SocketTransport? = null
) {
    private val retrySleeper = retrySleeper
    private val retryJitter = retryJitter
    private val client = HttpClient {
        install(WebSockets)
        install(HttpTimeout) { connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS }
    }
    private val transport = transport ?: KtorSocketTransport(client)
    // Held through transport teardown, including cancellation cleanup. Stop
    // never acquires this mutex: it signals a captured owner and awaits its ack.
    private val connectMutex = Mutex()
    private val currentRun = MutableStateFlow<ConnectRun?>(null)
    private val closed = MutableStateFlow(false)
    private val lifecycleMutex = Mutex()
    private var currentMailbox: AttemptMailbox? = null
    private var resumeToken: String? = tokenStore.load(serverUrl)
    private var forceAccessTokenRefresh = false
    private var hasConnected = false
    private var resumeRejected = false
    private var resumeRetryUsed = false
    private val _messages = Channel<ServerMessage>(Channel.UNLIMITED)
    val messages: Flow<ServerMessage> = _messages.receiveAsFlow()
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private val _connectionState = MutableStateFlow(SocketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    suspend fun connect(displayName: String) = connectMutex.withLock {
        if (closed.value) return@withLock
        val run = ConnectRun()
        currentRun.value = run
        try {
            // close() may have run between the closed check and publication.
            if (closed.value) run.requestStop()
            connectLoop(displayName, run)
        } finally {
            withContext(NonCancellable) {
                _isConnected.value = false
                if (run.machine.state != SocketConnectionState.TERMINAL) {
                    run.machine.reduce(ReconnectEvent.Stop)
                    _connectionState.value = SocketConnectionState.DISCONNECTED
                }
                currentRun.compareAndSet(run, null)
                run.retryRequests.cancel()
                run.stopRequests.cancel()
                run.finished.complete(Unit)
            }
        }
    }

    private suspend fun connectLoop(displayName: String, run: ConnectRun) {
        val machine = run.machine
        machine.reduce(ReconnectEvent.Start)
        while (currentCoroutineContext().isActive && !run.isStopping) {
            _connectionState.value =
                if (hasConnected) SocketConnectionState.RECONNECTING else SocketConnectionState.CONNECTING

            val result = runAttempt(displayName, run)
            if (run.isStopping || !currentCoroutineContext().isActive) break

            when (result) {
                AttemptResult.Stop, AttemptResult.Terminal -> break
                AttemptResult.ManualRetry -> {
                    drainRetryRequests(run)
                    machine.reduce(ReconnectEvent.ManualRetry)
                    _connectionState.value = SocketConnectionState.CONNECTING
                }
                AttemptResult.ImmediateRetry -> {
                    // This is the one auth-only retry after INVALID_RESUME_TOKEN.
                    // Advance state without introducing a backoff.
                    machine.reduce(ReconnectEvent.AttemptFailed)
                    _connectionState.value = SocketConnectionState.CONNECTING
                }
                AttemptResult.Retry -> {
                    val delayMillis =
                        (machine.reduce(ReconnectEvent.AttemptFailed) as? ReconnectDecision.RetryAfter)
                            ?.delayMillis ?: break
                    _connectionState.value = SocketConnectionState.RECONNECTING
                    when (awaitRetry(delayMillis + retryJitter.nextMillis(), run)) {
                        BackoffResult.Elapsed -> Unit
                        BackoffResult.ManualRetry -> {
                            machine.reduce(ReconnectEvent.ManualRetry)
                            _connectionState.value = SocketConnectionState.CONNECTING
                        }
                        BackoffResult.Stop -> break
                    }
                }
            }
        }
    }

    /** Enqueue only a signal; the connect loop performs all session closure. */
    fun retryNow() {
        val run = currentRun.value ?: return
        if (!run.isStopping) {
            run.retryRequests.trySend(Unit)
        }
    }

    suspend fun sendMessage(message: ClientMessage) {
        val mailbox = lifecycleMutex.withLock { currentMailbox }
        if (mailbox == null || mailbox.outgoing.trySend(message).isFailure) {
            _messages.send(socketClientError("CONNECTION_NOT_READY", "The server connection is not ready. Please wait or try again."))
        }
    }

    suspend fun disconnect() {
        val run = currentRun.value ?: return
        run.requestStop()
        // Never wait while holding connectMutex or lifecycleMutex. Only this
        // generation's ack is observed, even if another caller starts a run.
        run.finished.await()
    }

    fun close() {
        closed.value = true
        currentRun.value?.requestStop()
        client.close()
    }

    private suspend fun runAttempt(displayName: String, run: ConnectRun): AttemptResult {
        val mailbox = AttemptMailbox()
        var result = AttemptResult.Retry
        try {
            transport.webSocket(serverUrl) { attempt ->
                lifecycleMutex.withLock { currentMailbox = mailbox }
                run.machine.reduce(ReconnectEvent.TransportOpened)
                _isConnected.value = true
                _connectionState.value = SocketConnectionState.AUTHENTICATING
                result = runSocketAttempt(displayName, mailbox, attempt, run)

                if (result == AttemptResult.Retry && run.isStopping) {
                    result = AttemptResult.Stop
                } else if (result == AttemptResult.Retry &&
                    !shouldReconnectAfterSocketClose(attempt.closeReason())
                ) {
                    terminal(run, "SESSION_REPLACED", "Your account signed in on another device.")
                    _messages.send(socketClientError("SESSION_REPLACED", "Your account signed in on another device."))
                    result = AttemptResult.Terminal
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _messages.send(socketClientError("CONNECTION_FAILED", "Could not connect to $serverUrl: ${error.message}. Retrying…"))
            result = AttemptResult.Retry
        } finally {
            withContext(NonCancellable) {
                detachMailbox(mailbox)
                _isConnected.value = false
            }
        }
        return result
    }

    private suspend fun runSocketAttempt(
        displayName: String,
        mailbox: AttemptMailbox,
        attempt: SocketSession,
        run: ConnectRun
    ): AttemptResult = coroutineScope {
        val auth = authenticate(displayName, mailbox, attempt, run)
        when (auth) {
            AuthResult.Retry -> return@coroutineScope AttemptResult.ManualRetry
            AuthResult.Stop -> return@coroutineScope AttemptResult.Stop
            is AuthResult.Ready -> Unit
        }
        val hello = auth.hello
        if (run.isStopping) return@coroutineScope AttemptResult.Stop

        // The owner sends hello, so detach cannot race this send on a stale
        // SocketSession. retryNow is handled only at the next select point.
        attempt.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(hello)))

        var result: AttemptResult? = null
        while (result == null && !run.isStopping) {
            select<Unit> {
                attempt.incoming.onReceiveCatching { received ->
                    val frame = received.getOrNull()
                    if (frame == null) {
                        result = AttemptResult.Retry
                    } else if (frame is Frame.Text) {
                        result = handleMessage(frame.readText(), mailbox, attempt, run)
                    }
                }
                mailbox.outgoing.onReceiveCatching { outgoing ->
                    val message = outgoing.getOrNull()
                    if (message == null) {
                        result = AttemptResult.Retry
                    } else {
                        attempt.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message)))
                    }
                }
                run.retryRequests.onReceive {
                    drainRetryRequests(run)
                    result = AttemptResult.ManualRetry
                    closeAttempt(mailbox, attempt, run)
                }
                run.stopRequests.onReceive {
                    result = AttemptResult.Stop
                    closeAttempt(mailbox, attempt, run)
                }
            }
        }
        result ?: AttemptResult.Stop
    }

    private suspend fun authenticate(
        displayName: String,
        mailbox: AttemptMailbox,
        attempt: SocketSession,
        run: ConnectRun
    ): AuthResult = coroutineScope {
        if (accessTokenProvider == null) {
            return@coroutineScope AuthResult.Ready(ClientMessage.ConnectGuest(displayName, resumeToken))
        }

        val forceRefresh = forceAccessTokenRefresh
        val access = async { accessTokenProvider.invoke(forceRefresh) }
        val result = select<AuthResult> {
            access.onAwait { token ->
                forceAccessTokenRefresh = false
                if (token == null) {
                    terminal(run, "SESSION_EXPIRED", "Your session has expired. Please sign in again.")
                    AuthResult.Stop
                } else {
                    AuthResult.Ready(ClientMessage.ConnectAccount(token, resumeToken.takeUnless { resumeRejected }))
                }
            }
            run.retryRequests.onReceive {
                access.cancel()
                drainRetryRequests(run)
                closeAttempt(mailbox, attempt, run)
                AuthResult.Retry
            }
            run.stopRequests.onReceive {
                access.cancel()
                closeAttempt(mailbox, attempt, run)
                AuthResult.Stop
            }
        }
        if (result !is AuthResult.Ready) access.cancel()
        result
    }

    private suspend fun handleMessage(
        raw: String,
        mailbox: AttemptMailbox,
        attempt: SocketSession,
        run: ConnectRun
    ): AttemptResult? {
        val message = runCatching { ProtocolJson.decodeFromString<ServerMessage>(raw) }
            .getOrElse { socketClientError("PROTOCOL_DECODE_FAILED", "Could not decode the server response: ${it.message}") }

        var result: AttemptResult? = null
        when (message) {
            is ServerMessage.SessionReady -> {
                message.resumeToken?.let {
                    resumeToken = it
                    tokenStore.save(serverUrl, it)
                }
                resumeRejected = false
                resumeRetryUsed = false
                hasConnected = true
                run.machine.reduce(ReconnectEvent.Authenticated)
                _connectionState.value = SocketConnectionState.CONNECTED
            }
            is ServerMessage.Error -> when (message.code) {
                "INVALID_RESUME_TOKEN" -> {
                    if (resumeRetryUsed) {
                        terminal(run, message.code, message.message)
                        result = AttemptResult.Stop
                    } else {
                        resumeRetryUsed = true
                        resumeToken = null
                        tokenStore.clear(serverUrl)
                        resumeRejected = true
                        result = AttemptResult.ImmediateRetry
                        closeAttempt(mailbox, attempt, run)
                    }
                }
                "INVALID_ACCESS_TOKEN" -> {
                    terminal(run, message.code, message.message)
                    result = AttemptResult.Stop
                }
                "SESSION_EXPIRED" -> {
                    terminal(run, message.code, message.message)
                    result = AttemptResult.Stop
                }
            }
            else -> Unit
        }
        _messages.send(message)
        return result
    }

    private suspend fun closeAttempt(mailbox: AttemptMailbox, attempt: SocketSession, run: ConnectRun) {
        detachMailbox(mailbox)
        try {
            attempt.close()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The transport may already have closed this attempt.
        }
        // Taps received while close was suspended belong to the old attempt;
        // consume them before the loop creates its replacement.
        drainRetryRequests(run)
    }

    private suspend fun detachMailbox(mailbox: AttemptMailbox) {
        lifecycleMutex.withLock {
            if (currentMailbox === mailbox) currentMailbox = null
            mailbox.outgoing.cancel()
        }
    }

    private suspend fun awaitRetry(delayMillis: Long, run: ConnectRun): BackoffResult = coroutineScope {
        val sleeper = async { retrySleeper.sleep(delayMillis) }
        select {
            run.retryRequests.onReceive {
                sleeper.cancel()
                drainRetryRequests(run)
                BackoffResult.ManualRetry
            }
            run.stopRequests.onReceive {
                sleeper.cancel()
                BackoffResult.Stop
            }
            sleeper.onAwait { BackoffResult.Elapsed }
        }
    }

    private fun drainRetryRequests(run: ConnectRun) {
        while (run.retryRequests.tryReceive().isSuccess) { /* Drain this generation only. */ }
    }

    private fun terminal(run: ConnectRun, code: String, fallback: String) {
        run.requestStop()
        run.machine.reduce(ReconnectEvent.SessionExpired)
        _connectionState.value = SocketConnectionState.TERMINAL
        onAccountSessionExpired?.invoke(code, fallback)
    }

    private class AttemptMailbox {
        val outgoing = Channel<ClientMessage>(Channel.UNLIMITED)
    }

    private class ConnectRun {
        val machine = ReconnectStateMachine()
        val retryRequests = Channel<Unit>(Channel.CONFLATED)
        val stopRequests = Channel<Unit>(Channel.CONFLATED)
        val finished = CompletableDeferred<Unit>()
        private val stopping = MutableStateFlow(false)
        val isStopping get() = stopping.value

        fun requestStop() {
            stopping.value = true
            stopRequests.trySend(Unit)
        }
    }

    private enum class AttemptResult {
        Retry,
        ManualRetry,
        ImmediateRetry,
        Stop,
        Terminal
    }

    private sealed interface AuthResult {
        data class Ready(val hello: ClientMessage) : AuthResult
        data object Retry : AuthResult
        data object Stop : AuthResult
    }

    private enum class BackoffResult { Elapsed, ManualRetry, Stop }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 7_000L
    }
}

internal fun shouldReconnectAfterSocketClose(reason: String?): Boolean = reason != SESSION_REPLACED_CLOSE_REASON

internal fun socketClientError(code: String, message: String): ServerMessage.Error =
    ServerMessage.Error(code = code, message = message, messageKey = protocolTextKeyForCode(code)?.name)
