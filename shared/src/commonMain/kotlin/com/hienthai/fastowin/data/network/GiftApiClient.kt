package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException

interface GiftGateway {
    suspend fun page(page: Int, pageSize: Int): GiftMailboxPage
    suspend fun claim(id: String): GiftClaimResult
    suspend fun claimAll(ids: List<String>): GiftClaimAllResult
    fun close() = Unit
}

class GiftApiException(val code: String, override val message: String) : Exception(message)

class GiftApiClient(
    serverUrl: String,
    private val accessTokenProvider: suspend (forceRefresh: Boolean) -> String?,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    client: HttpClient? = null
) : GiftGateway {
    private val baseUrl = "${serverUrl.toHttpBaseUrl()}/gifts/mailbox"
    private val client = client ?: HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }

    override suspend fun page(page: Int, pageSize: Int): GiftMailboxPage =
        send("?page=$page&pageSize=$pageSize", HttpMethod.Get)

    override suspend fun claim(id: String): GiftClaimResult =
        send("/$id/claim", HttpMethod.Post)

    override suspend fun claimAll(ids: List<String>): GiftClaimAllResult =
        send("/claim-all", HttpMethod.Post) {
            contentType(ContentType.Application.Json)
            setBody(GiftClaimAllRequest(ids))
        }

    override fun close() = client.close()

    private suspend inline fun <reified T> send(
        path: String,
        method: HttpMethod,
        noinline configure: HttpRequestBuilder.() -> Unit = {}
    ): T {
        val first = client.request(baseUrl + path) {
            this.method = method
            requestConfigurator.configure(this)
            val token = accessTokenProvider(false) ?: throw GiftApiException("UNAUTHORIZED", "")
            headers.append(HttpHeaders.Authorization, "Bearer $token")
            configure()
        }
        val response = if (first.status.value == 401) client.request(baseUrl + path) {
            this.method = method
            requestConfigurator.configure(this)
            val token = accessTokenProvider(true) ?: throw GiftApiException("UNAUTHORIZED", "")
            headers.append(HttpHeaders.Authorization, "Bearer $token")
            configure()
        } else first
        if (!response.status.isSuccess()) {
            val error = try { response.body<GiftError>() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            throw GiftApiException(error?.code ?: "NETWORK_ERROR", error?.message.orEmpty())
        }
        return response.body()
    }
}
