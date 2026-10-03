package com.hienthai.fastowin.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import com.hienthai.fastowin.protocol.ProtocolJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GiftApiClientTest {
    @Test fun `mailbox retries unauthorized once with refreshed bearer on exact route`() = runTest {
        val seen = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/gifts/mailbox", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            assertEquals("2", request.url.parameters["page"])
            assertEquals("25", request.url.parameters["pageSize"])
            seen += request.headers[HttpHeaders.Authorization]
            if (seen.size == 1) respond("{\"code\":\"UNAUTHORIZED\",\"message\":\"expired\"}", HttpStatusCode.Unauthorized)
            else respond("{\"items\":[],\"page\":2,\"pageSize\":25,\"total\":0}", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = GiftApiClient("ws://localhost:8080/game", { force -> if (force) "fresh" else "stale" }, client = client)
        assertEquals(2, api.page(2, 25).page)
        assertEquals(listOf<String?>("Bearer stale", "Bearer fresh"), seen)
        api.close()
    }

    @Test fun `claim all sends exact batch and exposes server error code`() = runTest {
        var body = ""
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/gifts/mailbox/claim-all", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            assertEquals("Bearer player", request.headers[HttpHeaders.Authorization])
            body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond("{\"code\":\"CONFLICT\",\"message\":\"changed\"}", HttpStatusCode.Conflict,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = GiftApiClient("http://localhost:8080", { "player" }, client = client)
        val failure = assertFailsWith<GiftApiException> { api.claimAll(listOf("a", "b")) }
        assertEquals("CONFLICT", failure.code)
        assertTrue(body.contains("\"campaignIds\":[\"a\",\"b\"]"))
        api.close()
    }

    @Test fun `missing bearer fails before any network request`() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("{}") })
        val api = GiftApiClient("http://localhost:8080", { null }, client = client)
        assertEquals("UNAUTHORIZED", assertFailsWith<GiftApiException> { api.claim("a") }.code)
        assertEquals(0, requests)
        api.close()
    }

    @Test fun `single claim uses network only namespace while retaining platform auth configuration`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/gifts/mailbox/campaign/claim", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            assertEquals("Bearer registered", request.headers[HttpHeaders.Authorization])
            assertEquals("1", request.headers["X-FastToWin-Web-Session"])
            assertEquals("1", request.headers["X-FastToWin-CSRF"])
            respond("{\"code\":\"NOT_FOUND\",\"message\":\"missing\"}", HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val configurator = object : AuthRequestConfigurator {
            override fun configure(request: io.ktor.client.request.HttpRequestBuilder) {
                request.headers.append("X-FastToWin-Web-Session", "1")
                request.headers.append("X-FastToWin-CSRF", "1")
            }
        }
        val api = GiftApiClient("wss://example.invalid/game", { "registered" }, configurator, client)
        assertEquals("NOT_FOUND", assertFailsWith<GiftApiException> { api.claim("campaign") }.code)
        api.close()
    }
}
