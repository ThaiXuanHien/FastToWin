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

/** Player reads only. Identity, points and reward grants are always server-owned. */
interface ClanRaceGateway {
    suspend fun current(): ClanRaceCurrent
    suspend fun standings(page: Int = 0): ClanRaceStandings
    suspend fun contributions(): ClanRaceContributions
    suspend fun history(page: Int = 0): ClanRaceHistory
    fun close() = Unit
}

class ClanRaceApiException(val code: String) : Exception(code)

class ClanRaceApiClient(
    serverUrl: String,
    private val accessTokenProvider: suspend (forceRefresh: Boolean) -> String?,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    client: HttpClient? = null
) : ClanRaceGateway {
    private val baseUrl = "${serverUrl.toHttpBaseUrl()}/api/clan-race"
    private val client = client ?: HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 10_000; socketTimeoutMillis = 10_000 }
    }
    override suspend fun current(): ClanRaceCurrent = read("")
    override suspend fun standings(page: Int): ClanRaceStandings = read("/standings?page=$page&pageSize=25")
    override suspend fun contributions(): ClanRaceContributions = read("/contributions")
    override suspend fun history(page: Int): ClanRaceHistory = read("/history?page=$page&pageSize=25")
    override fun close() = client.close()

    private suspend fun request(path: String, force: Boolean) = client.get(baseUrl + path) {
        requestConfigurator.configure(this)
        val token = accessTokenProvider(force) ?: throw ClanRaceApiException("UNAUTHORIZED")
        headers.append(HttpHeaders.Authorization, "Bearer $token")
        headers.append(HttpHeaders.CacheControl, "no-store")
    }
    private suspend inline fun <reified T> read(path: String): T {
        val first = request(path, false)
        val response = if (first.status.value == 401) request(path, true) else first
        if (!response.status.isSuccess()) {
            val error = try { response.body<ClanRaceApiError>() }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            // A proxy/unknown error body must not erase authentication or outage semantics.
            val code = when (response.status.value) {
                401 -> "UNAUTHORIZED"
                403 -> "FORBIDDEN"
                503 -> "STORAGE_UNAVAILABLE"
                else -> error?.code ?: "NETWORK_ERROR"
            }
            throw ClanRaceApiException(code)
        }
        return response.body()
    }
}
