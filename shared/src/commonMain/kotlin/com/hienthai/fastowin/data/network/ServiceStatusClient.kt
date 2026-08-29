package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/**
 * Reads the server-owned service status independently from the game socket.
 * A network error deliberately returns null: loss of connectivity must never
 * be interpreted as planned maintenance.
 */
class ServiceStatusClient(serverUrl: String) {
    private val statusUrl = "${serverUrl.toHttpBaseUrl()}/status"
    private val client = HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
    }

    suspend fun fetchOrNull(): ServiceStatusResponse? = runCatching {
        val response = client.get(statusUrl)
        if (response.status.isSuccess()) response.body<ServiceStatusResponse>() else null
    }.getOrNull()

    fun close() = client.close()

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
    }
}
