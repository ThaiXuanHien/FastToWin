package com.hienthai.fastowin.data.network

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class GameSocketClient {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        classDiscriminator = "type"
        encodeDefaults = true
    }
    
    private val TAG = "GameSocketClient"

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private val _messages = MutableSharedFlow<GameMessage>()
    val messages: Flow<GameMessage> = _messages.asSharedFlow()

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    val isConnected: Flow<Boolean> = _isConnected.asSharedFlow()

    suspend fun connect(
        url: String = "wss://free.blr2.piesocket.com/v3/1?api_key=AqHVyHSHRahKcy3ymhsW6ILmEEpU2HHGINdyO9TN",
        onConnected: (suspend () -> Unit)? = null
    ) {
        Log.d(TAG, "Connecting to $url")
        try {
            client.webSocket(url) {
                session = this
                _isConnected.emit(true)
                Log.d(TAG, "Connected successfully")
                onConnected?.invoke()
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            Log.d(TAG, "Received raw: $text")
                            // Skip PieSocket system messages (they don't have our "type" field)
                            if (!text.contains("\"type\"")) {
                                Log.d(TAG, "Skipping non-game message")
                                continue
                            }
                            try {
                                val message = json.decodeFromString<GameMessage>(text)
                                Log.d(TAG, "Decoded message: $message")
                                _messages.emit(message)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to decode message: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in websocket loop: ${e.message}")
                    e.printStackTrace()
                } finally {
                    session = null
                    _isConnected.emit(false)
                    Log.d(TAG, "Disconnected")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            _isConnected.emit(false)
            throw e
        }
    }

    suspend fun sendMessage(message: GameMessage) {
        try {
            val text = json.encodeToString(message)
            Log.d(TAG, "Sending: $text")
            session?.send(Frame.Text(text))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun disconnect() {
        session?.close()
        session = null
    }

    fun close() {
        client.close()
    }
}
