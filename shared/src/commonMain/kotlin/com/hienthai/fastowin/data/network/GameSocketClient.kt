package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class GameSocketClient(private val serverUrl: String) {
    private val client = HttpClient { install(WebSockets) }
    private var session: DefaultClientWebSocketSession? = null
    private var resumeToken: String? = null
    private var reconnectEnabled = true

    private val _messages = Channel<ServerMessage>(Channel.UNLIMITED)
    val messages: Flow<ServerMessage> = _messages.receiveAsFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    suspend fun connect(displayName: String) {
        reconnectEnabled = true
        var retryDelayMillis = INITIAL_RETRY_MILLIS

        while (currentCoroutineContext().isActive && reconnectEnabled) {
            try {
                client.webSocket(serverUrl) {
                    session = this
                    _isConnected.value = true
                    retryDelayMillis = INITIAL_RETRY_MILLIS
                    sendMessage(ClientMessage.ConnectGuest(displayName, resumeToken))

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val message = runCatching {
                            ProtocolJson.decodeFromString<ServerMessage>(frame.readText())
                        }.getOrNull() ?: continue
                        if (message is ServerMessage.SessionReady) resumeToken = message.resumeToken
                        _messages.send(message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A reconnect below will restore the server snapshot.
            } finally {
                session = null
                _isConnected.value = false
            }

            if (reconnectEnabled && currentCoroutineContext().isActive) {
                delay(retryDelayMillis)
                retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_MILLIS)
            }
        }
    }

    suspend fun sendMessage(message: ClientMessage) {
        session?.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message)))
    }

    suspend fun disconnect() {
        reconnectEnabled = false
        runCatching { session?.close() }
        session = null
        _isConnected.value = false
    }

    fun close() {
        reconnectEnabled = false
        client.close()
    }

    private companion object {
        const val INITIAL_RETRY_MILLIS = 1_000L
        const val MAX_RETRY_MILLIS = 15_000L
    }
}
