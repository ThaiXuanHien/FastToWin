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
import kotlinx.coroutines.withTimeoutOrNull

/** One instance belongs to one account. Tokens are read at request time, never captured or logged. */
interface RewardedAdSessionGateway {
    val ownerId: String
    suspend fun prepare(requestId: String): RewardedAdSessionView
    suspend fun status(sessionId: String): RewardedAdSessionView
    fun close()
}
class RewardedAdApiException : Exception()
class RewardedAdApiClient(
    serverUrl: String,
    override val ownerId: String,
    private val accessTokenProvider: suspend (Boolean) -> String?,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    client: HttpClient? = null,
    private val timeoutMillis: Long = 10_000
) : RewardedAdSessionGateway {
    private val baseUrl = "${serverUrl.toHttpBaseUrl()}/api/rewarded-ads/sessions"
    private val http = client ?: HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }
    private var closed = false
    override suspend fun prepare(requestId: String): RewardedAdSessionView {
        if (!safeId(requestId)) throw RewardedAdApiException()
        return request(null, requestId)
    }
    override suspend fun status(sessionId: String): RewardedAdSessionView {
        if (!safeId(sessionId)) throw RewardedAdApiException()
        return request(sessionId, null)
    }
    private suspend fun request(sessionId: String?, requestId: String?): RewardedAdSessionView {
        if (closed || ownerId.isBlank()) throw RewardedAdApiException()
        try {
            return withTimeoutOrNull(timeoutMillis) {
                suspend fun send(refresh: Boolean): io.ktor.client.statement.HttpResponse {
                    if (closed) throw RewardedAdApiException()
                    val token = accessTokenProvider(refresh)?.takeIf { it.isNotBlank() } ?: throw RewardedAdApiException()
                    return http.request(if (sessionId == null) baseUrl else "$baseUrl/$sessionId") {
                        requestConfigurator.configure(this)
                        bearerAuth(token)
                        method = if (sessionId == null) HttpMethod.Post else HttpMethod.Get
                        if (requestId != null) {
                            contentType(ContentType.Application.Json)
                            setBody(RewardedAdSessionRequest(requestId))
                        }
                    }
                }
                val first = send(false)
                val response = if (first.status == HttpStatusCode.Unauthorized) send(true) else first
                if (!response.status.isSuccess()) throw RewardedAdApiException()
                val result = response.body<RewardedAdSessionView>()
                if (closed || !safeId(result.sessionId) || result.customData.isBlank() || result.customData.length > 1024 ||
                    result.adUnitId.isBlank() || result.adUnitId.length > 128 ||
                    (sessionId != null && result.sessionId != sessionId) ||
                    (result.status == RewardedAdSessionStatus.GRANTED && (result.providerTransactionId.isNullOrBlank() || result.quota == null))) {
                    throw RewardedAdApiException()
                }
                result
            } ?: throw RewardedAdApiException()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            throw RewardedAdApiException()
        }
    }
    override fun close() { closed = true; http.close() }
    private fun safeId(id: String) = id.length in 1..64 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
}
