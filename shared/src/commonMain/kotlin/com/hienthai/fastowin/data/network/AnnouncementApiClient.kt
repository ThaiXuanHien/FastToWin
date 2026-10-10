package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException

// Player DTOs mirror the accepted HTTP contract; admin DTOs are never decoded here.
@Serializable data class AnnouncementPlayerItem(val notification: NotificationSnapshot, val announcement: AdminAnnouncementContext, val dismissed: Boolean, val bannerHidden: Boolean)
@Serializable data class AnnouncementPlayerPage(val items: List<AnnouncementPlayerItem>, val page: Int, val pageSize: Int, val total: String, val serverNowEpochMillis: Long)
@Serializable data class AnnouncementBanners(val items: List<AnnouncementPlayerItem>, val serverNowEpochMillis: Long)
@Serializable data class AnnouncementPreferenceRequest(val expectedVersion: Int, val adminEnabled: Boolean, val eventEnabled: Boolean)
@Serializable data class AnnouncementPreferenceView(val version: Int, val adminEnabled: Boolean, val eventEnabled: Boolean, val serverNowEpochMillis: Long)
@Serializable data class AnnouncementMutationResult(val affected: String, val serverNowEpochMillis: Long)

interface AnnouncementGateway {
    suspend fun inbox(page: Int, pageSize: Int): AnnouncementPlayerPage
    suspend fun detail(id: String): AnnouncementPlayerItem
    suspend fun banners(): AnnouncementBanners
    suspend fun read(id: String? = null): AnnouncementMutationResult
    suspend fun dismiss(id: String? = null): AnnouncementMutationResult
    suspend fun hide(id: String): AnnouncementMutationResult
    suspend fun preferences(): AnnouncementPreferenceView
    suspend fun savePreferences(request: AnnouncementPreferenceRequest): AnnouncementPreferenceView
    fun close() = Unit
}
class AnnouncementApiException(val code: String) : Exception(code)

class AnnouncementApiClient(
    serverUrl: String,
    private val accessTokenProvider: suspend (forceRefresh: Boolean) -> String?,
    private val requestConfigurator: AuthRequestConfigurator = NoOpAuthRequestConfigurator,
    client: HttpClient? = null
) : AnnouncementGateway {
    private val baseUrl = "${serverUrl.toHttpBaseUrl()}/api/announcements"
    private val client = client ?: HttpClient {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 10_000; socketTimeoutMillis = 10_000 }
    }
    override suspend fun inbox(page: Int, pageSize: Int): AnnouncementPlayerPage = send("?page=$page&pageSize=$pageSize", HttpMethod.Get)
    override suspend fun detail(id: String): AnnouncementPlayerItem = send("/${id.encodeURLPathPart()}", HttpMethod.Get)
    override suspend fun banners(): AnnouncementBanners = send("/banners", HttpMethod.Get)
    override suspend fun read(id: String?): AnnouncementMutationResult = send(id?.let { "/${it.encodeURLPathPart()}/read" } ?: "/read-all", HttpMethod.Post)
    override suspend fun dismiss(id: String?): AnnouncementMutationResult = send(id?.let { "/${it.encodeURLPathPart()}/dismiss" } ?: "/dismiss-all", HttpMethod.Post)
    override suspend fun hide(id: String): AnnouncementMutationResult = send("/${id.encodeURLPathPart()}/hide-banner", HttpMethod.Post)
    override suspend fun preferences(): AnnouncementPreferenceView = send("/push-preferences", HttpMethod.Get)
    override suspend fun savePreferences(request: AnnouncementPreferenceRequest): AnnouncementPreferenceView = send("/push-preferences", HttpMethod.Put) {
        contentType(ContentType.Application.Json); setBody(request)
    }
    override fun close() = client.close()
    private suspend fun attempt(path: String, method: HttpMethod, configure: HttpRequestBuilder.() -> Unit, force: Boolean) = client.request(baseUrl + path) {
            this.method = method
            val token = accessTokenProvider(force) ?: throw AnnouncementApiException("UNAUTHORIZED")
            requestConfigurator.configure(this)
            headers.append(HttpHeaders.Authorization, "Bearer $token")
            headers.append(HttpHeaders.CacheControl, "no-store")
            configure()
    }
    private suspend inline fun <reified T> send(path: String, method: HttpMethod, noinline configure: HttpRequestBuilder.() -> Unit = {}): T {
        val first = attempt(path, method, configure, false)
        val response = if (first.status.value == 401) attempt(path, method, configure, true) else first
        if (!response.status.isSuccess()) {
            val error = try { response.body<AnnouncementError>() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            throw AnnouncementApiException(error?.code ?: "NETWORK_ERROR")
        }
        return response.body()
    }
}

@Serializable private data class AnnouncementError(val code: String, val message: String)
