package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.ProtocolJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class RewardEventApiClientTest {
    @Test fun `public list uses exact simple network URL no auth and fresh transport on every refresh`() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://example.invalid/api/events?page=2&pageSize=25", request.url.toString())
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers[HttpHeaders.CacheControl]) // Non-simple request header is not allowed by server CORS.
            requests++
            respond("""{"items":[],"serverNowEpochMillis":${1000 + requests},"page":2,"pageSize":25,"total":0}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = RewardEventApiClient("wss://example.invalid/game", client)
        assertEquals(1001, api.page(2,25).serverNowEpochMillis)
        assertEquals(1002, api.page(2,25).serverNowEpochMillis)
        assertEquals(2,requests)
        api.close()
    }
    @Test fun `public detail encodes path segment and distinguishes unavailable`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/events/a%2Fb", request.url.encodedPath)
            respond("{}", HttpStatusCode.ServiceUnavailable)
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = RewardEventApiClient("http://example.invalid", client)
        assertEquals("UNAVAILABLE", assertFailsWith<RewardEventApiException> { api.detail("a/b") }.code)
        api.close()
    }
}
