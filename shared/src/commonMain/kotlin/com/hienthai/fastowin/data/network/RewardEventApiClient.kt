package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

interface RewardEventGateway {
    suspend fun page(page: Int, pageSize: Int): RewardEventPublicPage
    suspend fun detail(id: String): RewardEventPublicDetail
    fun close() = Unit
}

class RewardEventApiException(val code: String) : Exception(code)

class RewardEventApiClient(serverUrl: String, client: HttpClient? = null) : RewardEventGateway {
    private val base = "${serverUrl.toHttpBaseUrl()}/api/events"
    private val client = client ?: HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }
    override suspend fun page(page: Int, pageSize: Int): RewardEventPublicPage = send("?page=$page&pageSize=$pageSize")
    override suspend fun detail(id: String): RewardEventPublicDetail = send("/${id.encodeURLPathPart()}")
    private suspend inline fun <reified T> send(path: String): T {
        // The server's response is no-store and /api is network-only in the worker.
        // Keep a simple public GET: custom cache headers would fail the accepted CORS policy.
        val response = client.get(base + path)
        if (!response.status.isSuccess()) throw RewardEventApiException(
            if (response.status.value == 503) "UNAVAILABLE" else if (response.status.value == 404) "NOT_FOUND" else "NETWORK_ERROR"
        )
        return response.body()
    }
    override fun close() = client.close()
}
