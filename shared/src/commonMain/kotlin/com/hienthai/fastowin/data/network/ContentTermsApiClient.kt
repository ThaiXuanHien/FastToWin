package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ContentTermsStatus
import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/** Public metadata only: a failed lookup never implies that publishing is allowed. */
class ContentTermsApiClient(serverUrl: String, private val client: HttpClient = HttpClient {
    install(ContentNegotiation) { json(ProtocolJson) }
    install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 10_000 }
}) {
    private val url = "${serverUrl.toHttpBaseUrl()}/api/content-terms"
    suspend fun fetch(): ContentTermsStatus {
        val response = client.get(url)
        check(response.status.isSuccess()) { "CONTENT_TERMS_UNAVAILABLE" }
        return response.body<ContentTermsStatus>().also { check(it.requiredVersion.isNotBlank()) }
    }
    fun close() = client.close()
}
