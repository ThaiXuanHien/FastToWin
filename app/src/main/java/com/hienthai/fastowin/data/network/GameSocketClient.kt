package com.hienthai.fastowin.data.network

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class GameSocketClient {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    private val TAG = "GameSocketClient"

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private var session: DefaultClientWebSocketSession? = null

    // Channel with buffer so fast messages are never dropped
    private val _messages = Channel<GameMessage>(capacity = Channel.UNLIMITED)
    val messages: Flow<GameMessage> = _messages.receiveAsFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Opens the WebSocket connection and suspends until it closes.
     * Call this from a dedicated coroutine; cancel that coroutine to disconnect.
     */
    suspend fun connect(
        url: String = "wss://free.blr2.piesocket.com/v3/1?api_key=AqHVyHSHRahKcy3ymhsW6ILmEEpU2HHGINdyO9TN"
    ) {
        Log.d(TAG, "Connecting to $url")
        try {
            client.webSocket(url) {
                session = this
                _isConnected.value = true
                Log.d(TAG, "Connected")

                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        Log.d(TAG, "Raw: $text")

                        // PieSocket sends system events like {"event":"..."}; skip them
                        if (!text.contains("\"type\"")) {
                            Log.d(TAG, "Skipping system message")
                            continue
                        }

                        try {
                            val msg = json.decodeFromString<GameMessage>(text)
                            Log.d(TAG, "Decoded: $msg")
                            _messages.send(msg)
                        } catch (e: Exception) {
                            Log.e(TAG, "Decode error: ${e.message}")
                        }
                    }
                } finally {
                    session = null
                    _isConnected.value = false
                    Log.d(TAG, "Session ended")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            session = null
            _isConnected.value = false
            throw e
        }
    }

    suspend fun sendMessage(message: GameMessage) {
        val s = session
        if (s == null) {
            Log.w(TAG, "sendMessage called but no active session")
            return
        }
        try {
            val text = json.encodeToString(message)
            Log.d(TAG, "Sending: $text")
            s.send(Frame.Text(text))
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
        }
    }

    suspend fun disconnect() {
        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
        _isConnected.value = false
    }

    fun close() {
        client.close()
    }
}
