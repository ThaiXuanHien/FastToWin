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

interface MissionGateway {
    suspend fun list(scope: MissionScope): MissionListResponse
    suspend fun claim(instanceId: String, requestId: String): MissionClaimResponse
    fun close() = Unit
}
class MissionApiException(val code: String, override val message: String = "") : Exception(message)

class MissionApiClient(
    serverUrl: String,
    private val accessTokenProvider: suspend (forceRefresh: Boolean) -> String?,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    client: HttpClient? = null
) : MissionGateway {
    private val baseUrl = "${serverUrl.toHttpBaseUrl()}/api/missions"
    private val client = client ?: HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 10_000; socketTimeoutMillis = 10_000 }
    }
    override suspend fun list(scope: MissionScope): MissionListResponse = send("/${scope.name.lowercase()}", HttpMethod.Get)
    override suspend fun claim(instanceId: String, requestId: String): MissionClaimResponse = send("/${instanceId.encodeURLPathPart()}/claim", HttpMethod.Post) {
        contentType(ContentType.Application.Json)
        setBody(MissionClaimRequest(requestId))
    }
    override fun close() = client.close()
    private suspend fun request(path: String, method: HttpMethod, force: Boolean, configure: HttpRequestBuilder.() -> Unit) = client.request(baseUrl + path) {
            this.method = method
            requestConfigurator.configure(this)
            val token = accessTokenProvider(force) ?: throw MissionApiException("UNAUTHORIZED")
            headers.append(HttpHeaders.Authorization, "Bearer $token")
            headers.append(HttpHeaders.CacheControl, "no-store")
            configure()
        }
    private suspend inline fun <reified T> send(path: String, method: HttpMethod, noinline configure: HttpRequestBuilder.() -> Unit = {}): T {
        val first = request(path, method, false, configure)
        val response = if (first.status.value == 401) request(path, method, true, configure) else first
        if (!response.status.isSuccess()) {
            val error = try { response.body<MissionApiError>() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { null }
            throw MissionApiException(error?.code ?: "NETWORK_ERROR", error?.message.orEmpty())
        }
        return response.body()
    }
}
