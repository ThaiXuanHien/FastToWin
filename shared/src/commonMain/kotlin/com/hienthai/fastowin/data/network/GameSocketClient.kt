package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.localization.protocolTextKeyForCode
import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.coroutineScope
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
            try { block(session) } finally { session.close() }
        }
    }
}
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
    private val client = HttpClient { install(WebSockets); install(HttpTimeout) { connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS } }
    private val transport = transport ?: KtorSocketTransport(client)
    private val machine = ReconnectStateMachine()
    private val retryRequests = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: SocketSession? = null
    private var resumeToken: String? = tokenStore.load(serverUrl)
    private var reconnectEnabled = true
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

    suspend fun connect(displayName: String) {
        reconnectEnabled = true
        machine.reduce(ReconnectEvent.Start)
        while (currentCoroutineContext().isActive && reconnectEnabled) {
            _connectionState.value = if (hasConnected) SocketConnectionState.RECONNECTING else SocketConnectionState.CONNECTING
            try {
                transport.webSocket(serverUrl) { attempt ->
                    session = attempt
                    machine.reduce(ReconnectEvent.TransportOpened)
                    _isConnected.value = true
                    _connectionState.value = SocketConnectionState.AUTHENTICATING
                    val hello = if (accessTokenProvider == null) ClientMessage.ConnectGuest(displayName, resumeToken)
                    else {
                        val access = accessTokenProvider(forceAccessTokenRefresh)
                        forceAccessTokenRefresh = false
                        if (access == null) { terminal("SESSION_EXPIRED", "Your session has expired. Please sign in again."); return@webSocket }
                        ClientMessage.ConnectAccount(access, resumeToken.takeUnless { resumeRejected })
                    }
                    attempt.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(hello)))
                    for (frame in attempt.incoming) {
                        if (frame !is Frame.Text) continue
                        val message = runCatching { ProtocolJson.decodeFromString<ServerMessage>(frame.readText()) }
                            .getOrElse { socketClientError("PROTOCOL_DECODE_FAILED", "Could not decode the server response: ${it.message}") }
                        when (message) {
                            is ServerMessage.SessionReady -> {
                                message.resumeToken?.let { resumeToken = it; tokenStore.save(serverUrl, it) }
                                resumeRejected = false; resumeRetryUsed = false; hasConnected = true
                                machine.reduce(ReconnectEvent.Authenticated); _connectionState.value = SocketConnectionState.CONNECTED
                            }
                            is ServerMessage.Error -> when (message.code) {
                                "INVALID_RESUME_TOKEN" -> {
                                    if (resumeRetryUsed) terminal(message.code, message.message)
                                    else { resumeRetryUsed = true; resumeToken = null; tokenStore.clear(serverUrl); resumeRejected = true; attempt.close() }
                                }
                                "INVALID_ACCESS_TOKEN" -> terminal(message.code, message.message)
                                "SESSION_EXPIRED" -> terminal(message.code, message.message)
                            }
                            else -> Unit
                        }
                        _messages.send(message)
                        if (!reconnectEnabled) break
                    }
                    if (!shouldReconnectAfterSocketClose(attempt.closeReason())) {
                        terminal("SESSION_REPLACED", "Your account signed in on another device.")
                        _messages.send(socketClientError("SESSION_REPLACED", "Your account signed in on another device."))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _messages.send(socketClientError("CONNECTION_FAILED", "Could not connect to $serverUrl: ${error.message}. Retrying…")) }
            finally { session = null; _isConnected.value = false }
            if (!reconnectEnabled || !currentCoroutineContext().isActive) break
            val delayMillis = (machine.reduce(ReconnectEvent.AttemptFailed) as? ReconnectDecision.RetryAfter)?.delayMillis ?: break
            _connectionState.value = SocketConnectionState.RECONNECTING
            coroutineScope {
                val sleeper = async { retrySleeper.sleep(delayMillis + retryJitter.nextMillis()) }
                select<Unit> {
                    retryRequests.onReceive { sleeper.cancel(); machine.reduce(ReconnectEvent.ManualRetry) }
                    sleeper.onAwait { }
                }
            }
        }
    }
    fun retryNow() {
        if (machine.state != SocketConnectionState.TERMINAL) {
            retryRequests.trySend(Unit)
            val active = session
            session = null
            active?.let { scope.async { try { it.close() } catch (_: Exception) { } } }
        }
    }
    suspend fun sendMessage(message: ClientMessage) {
        val active = session ?: run { _messages.send(socketClientError("CONNECTION_NOT_READY", "The server connection is not ready. Please wait or try again.")); return }
        try {
            active.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message)))
        } catch (error: Exception) {
            _messages.send(socketClientError("SEND_FAILED", "Could not send data: ${error.message}"))
        }
    }
    suspend fun disconnect() { reconnectEnabled = false; session?.close(); session = null; _isConnected.value = false; _connectionState.value = SocketConnectionState.DISCONNECTED; machine.reduce(ReconnectEvent.Stop) }
    fun close() { reconnectEnabled = false; client.close() }
    private fun terminal(code: String, fallback: String) { reconnectEnabled = false; machine.reduce(ReconnectEvent.SessionExpired); _connectionState.value = SocketConnectionState.TERMINAL; onAccountSessionExpired?.invoke(code, fallback) }
    private companion object { const val CONNECT_TIMEOUT_MILLIS = 7_000L }
}
internal fun shouldReconnectAfterSocketClose(reason: String?): Boolean = reason != SESSION_REPLACED_CLOSE_REASON
internal fun socketClientError(code: String, message: String): ServerMessage.Error = ServerMessage.Error(code, message, protocolTextKeyForCode(code)?.name)
