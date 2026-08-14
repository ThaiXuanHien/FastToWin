package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.protocol.SESSION_REPLACED_CLOSE_REASON
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.CloseReason
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
import kotlin.random.Random

enum class SocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING
}

class GameSocketClient(
    private val serverUrl: String,
    private val tokenStore: ResumeTokenStore,
    private val accessTokenProvider: (suspend (forceRefresh: Boolean) -> String?)? = null,
    private val onAccountSessionExpired: (() -> Unit)? = null
) {
    private val client = HttpClient {
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        }
    }
    private var session: DefaultClientWebSocketSession? = null
    private var resumeToken: String? = tokenStore.load(serverUrl)
    private var reconnectEnabled = true
    private var hasConnected = false
    private var forceAccessTokenRefresh = false

    private val _messages = Channel<ServerMessage>(Channel.UNLIMITED)
    val messages: Flow<ServerMessage> = _messages.receiveAsFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow(SocketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    suspend fun connect(displayName: String) {
        reconnectEnabled = true
        var retryDelayMillis = INITIAL_RETRY_MILLIS

        while (currentCoroutineContext().isActive && reconnectEnabled) {
            _connectionState.value = if (hasConnected) {
                SocketConnectionState.RECONNECTING
            } else {
                SocketConnectionState.CONNECTING
            }
            try {
                client.webSocket(serverUrl) {
                    session = this
                    _isConnected.value = true
                    _connectionState.value = SocketConnectionState.AUTHENTICATING
                    retryDelayMillis = INITIAL_RETRY_MILLIS
                    val helloMessage: ClientMessage = if (accessTokenProvider == null) {
                        ClientMessage.ConnectGuest(displayName, resumeToken)
                    } else {
                        val accessToken = accessTokenProvider(forceAccessTokenRefresh)
                        forceAccessTokenRefresh = false
                        if (accessToken == null) {
                            reconnectEnabled = false
                            onAccountSessionExpired?.invoke()
                            return@webSocket
                        }
                        ClientMessage.ConnectAccount(accessToken)
                    }
                    val hello = ProtocolJson.encodeToString<ClientMessage>(helloMessage)
                    send(
                        Frame.Text(hello)
                    )

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val rawMessage = frame.readText()
                        val message = try {
                            ProtocolJson.decodeFromString<ServerMessage>(rawMessage)
                        } catch (error: Exception) {
                            _messages.send(
                                ServerMessage.Error(
                                    code = "PROTOCOL_DECODE_FAILED",
                                    message = "Không đọc được phản hồi máy chủ: ${error.message}"
                                )
                            )
                            continue
                        }
                        if (message is ServerMessage.SessionReady) {
                            message.resumeToken?.let { newResumeToken ->
                                resumeToken = newResumeToken
                                tokenStore.save(serverUrl, newResumeToken)
                            }
                            hasConnected = true
                            forceAccessTokenRefresh = false
                            _connectionState.value = SocketConnectionState.CONNECTED
                        }
                        if (message is ServerMessage.Error && message.code == "INVALID_ACCESS_TOKEN") {
                            if (accessTokenProvider != null) {
                                forceAccessTokenRefresh = true
                                this.close(
                                    CloseReason(
                                        CloseReason.Codes.NORMAL,
                                        "Refreshing account session"
                                    )
                                )
                                break
                            }
                        }
                        _messages.send(message)
                    }
                    val reason = closeReason.await()?.message
                    if (!shouldReconnectAfterSocketClose(reason)) {
                        reconnectEnabled = false
                        _messages.send(
                            ServerMessage.Error(
                                code = "SESSION_REPLACED",
                                message = "Kết nối trò chơi đã chuyển sang thiết bị khác. Đóng ứng dụng ở thiết bị kia rồi mở lại nếu bạn muốn chơi tại đây."
                            )
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _messages.send(
                    ServerMessage.Error(
                        code = "CONNECTION_FAILED",
                        message = "Không thể kết nối $serverUrl: ${error.message}. Đang thử lại..."
                    )
                )
            } finally {
                session = null
                _isConnected.value = false
                _connectionState.value = when {
                    !reconnectEnabled -> SocketConnectionState.DISCONNECTED
                    hasConnected -> SocketConnectionState.RECONNECTING
                    else -> SocketConnectionState.CONNECTING
                }
            }

            if (reconnectEnabled && currentCoroutineContext().isActive) {
                delay(retryDelayMillis + Random.nextLong(RETRY_JITTER_MILLIS + 1))
                retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_MILLIS)
            }
        }
    }

    suspend fun sendMessage(message: ClientMessage) {
        val activeSession = session
        if (activeSession == null) {
            _messages.send(
                ServerMessage.Error(
                    code = "CONNECTION_NOT_READY",
                    message = "Chưa kết nối được máy chủ. Vui lòng đợi hoặc thử lại."
                )
            )
            return
        }
        try {
            activeSession.send(Frame.Text(ProtocolJson.encodeToString<ClientMessage>(message)))
        } catch (error: Exception) {
            _messages.send(
                ServerMessage.Error(
                    code = "SEND_FAILED",
                    message = "Không gửi được dữ liệu: ${error.message}"
                )
            )
        }
    }

    suspend fun disconnect() {
        reconnectEnabled = false
        runCatching { session?.close() }
        session = null
        _isConnected.value = false
        _connectionState.value = SocketConnectionState.DISCONNECTED
    }

    fun close() {
        reconnectEnabled = false
        client.close()
    }

    private companion object {
        const val INITIAL_RETRY_MILLIS = 1_000L
        const val MAX_RETRY_MILLIS = 5_000L
        const val RETRY_JITTER_MILLIS = 750L
        const val CONNECT_TIMEOUT_MILLIS = 7_000L
    }
}

internal fun shouldReconnectAfterSocketClose(reason: String?): Boolean =
    reason != SESSION_REPLACED_CLOSE_REASON
