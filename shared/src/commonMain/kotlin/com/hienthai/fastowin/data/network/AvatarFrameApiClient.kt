package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.AvatarFrameCatalog
import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

internal class AvatarFrameApiClient(serverUrl: String) {
    private val endpoint = "${serverUrl.toHttpBaseUrl()}/api/avatar-frames"
    private val client = HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }

    suspend fun load(): AvatarFrameCatalog? {
        val response = client.get(endpoint)
        return if (response.status.isSuccess()) response.body<AvatarFrameCatalog>() else null
    }

    fun close() = client.close()
}
