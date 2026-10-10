package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AnnouncementApiClientTest {
    @Test fun `network only bearer inbox refresh retains exact decimal total`() = runTest {
        val seen = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/announcements", request.url.encodedPath)
            assertEquals("2", request.url.parameters["page"])
            assertEquals("25", request.url.parameters["pageSize"])
            assertEquals("no-store", request.headers[HttpHeaders.CacheControl])
            seen += request.headers[HttpHeaders.Authorization]
            if (seen.size == 1) respond("{}", HttpStatusCode.Unauthorized)
            else respond("""{"items":[],"page":2,"pageSize":25,"total":"9223372036854775807","serverNowEpochMillis":1000}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = AnnouncementApiClient("wss://example.invalid/game", { if (it) "new" else "old" }, client = client)
        assertEquals("9223372036854775807", api.inbox(2, 25).total)
        assertEquals(listOf<String?>("Bearer old", "Bearer new"), seen)
        api.close()
    }
    @Test fun `read dismiss hide and preference routes keep their separate server authority`() = runTest {
        val paths = mutableListOf<String>(); var body = ""
        val client = HttpClient(MockEngine { request ->
            paths += "${request.method.value} ${request.url.encodedPath}"
            assertEquals("Bearer registered", request.headers[HttpHeaders.Authorization])
            assertEquals("retained", request.headers["X-Platform"])
            val response = if (request.method == HttpMethod.Put) {
                body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                """{"version":2,"adminEnabled":true,"eventEnabled":false,"serverNowEpochMillis":1000}"""
            } else """{"affected":"1","serverNowEpochMillis":1000}"""
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = AnnouncementApiClient("https://example.invalid", { "registered" }, object : AuthRequestConfigurator {
            override fun configure(request: io.ktor.client.request.HttpRequestBuilder) { request.headers.append("X-Platform", "retained") }
        }, client)
        api.read("id"); api.read(); api.dismiss("id"); api.dismiss(); api.hide("id")
        assertTrue(api.savePreferences(AnnouncementPreferenceRequest(1, true, false)).adminEnabled)
        assertEquals(listOf("POST /api/announcements/id/read", "POST /api/announcements/read-all", "POST /api/announcements/id/dismiss", "POST /api/announcements/dismiss-all", "POST /api/announcements/id/hide-banner", "PUT /api/announcements/push-preferences"), paths)
        assertEquals(AnnouncementPreferenceRequest(1, true, false), ProtocolJson.decodeFromString<AnnouncementPreferenceRequest>(body))
        api.close()
    }
    @Test fun `missing bearer cannot send requests or reveal raw error copy`() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("{}") })
        val api = AnnouncementApiClient("https://example.invalid", { null }, client = client)
        assertEquals("UNAUTHORIZED", assertFailsWith<AnnouncementApiException> { api.banners() }.code)
        assertEquals(0, requests)
        api.close()
    }
}
