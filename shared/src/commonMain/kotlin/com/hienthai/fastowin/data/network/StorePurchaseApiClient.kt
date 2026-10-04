package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface StorePurchaseGateway {
    suspend fun verify(request: StorePurchaseRequest): ServerMessage.StorePurchaseResult
    fun close() = Unit
}

class StorePurchaseApiClient(
    serverUrl: String,
    private val accessTokenProvider: suspend (forceRefresh: Boolean) -> String?,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    client: HttpClient? = null
) : StorePurchaseGateway {
    private val url = "${serverUrl.toHttpBaseUrl()}/api/store-purchases/verify"
    private val client = client ?: HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }

    override suspend fun verify(request: StorePurchaseRequest): ServerMessage.StorePurchaseResult {
        if (request.requestId.length !in 1..64 || request.productId.length !in 1..128 || request.purchaseToken.length !in 1..4096) {
            return failure(request, StorePurchaseStatus.INVALID, "BillingIncomplete")
        }
        return try {
            suspend fun send(force: Boolean): io.ktor.client.statement.HttpResponse? {
                val token = accessTokenProvider(force) ?: return null
                return client.post(url) {
                    requestConfigurator.configure(this)
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }
            val first = send(false) ?: return failure(request)
            val response = if (first.status == HttpStatusCode.Unauthorized) send(true) ?: return failure(request) else first
            if (!response.status.isSuccess()) return failure(request)
            val result = response.body<ServerMessage.StorePurchaseResult>()
            if (result.requestId != request.requestId || result.productId != request.productId) return failure(request)
            result
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            failure(request)
        }
    }

    override fun close() = client.close()
    private fun failure(request: StorePurchaseRequest, status: StorePurchaseStatus = StorePurchaseStatus.UNAVAILABLE,
        key: String = "ServerUnavailable") = ServerMessage.StorePurchaseResult(request.requestId, request.productId, status,
        message = "", messageKey = key)
}
