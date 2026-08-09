package com.hienthai.fastowin.data.network

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
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private val _messages = MutableSharedFlow<GameMessage>()
    val messages: Flow<GameMessage> = _messages.asSharedFlow()

    suspend fun connect(url: String = "ws://10.0.2.2:8080/game") {
        client.webSocket(url) {
            session = this
            try {
                while (true) {
                    val message = receiveDeserialized<GameMessage>()
                    _messages.emit(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                session = null
            }
        }
    }

    suspend fun sendMessage(message: GameMessage) {
        session?.sendSerialized(message)
    }

    fun close() {
        client.close()
    }
}
