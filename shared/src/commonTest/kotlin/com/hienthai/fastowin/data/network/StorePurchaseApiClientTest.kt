package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.*

class StorePurchaseApiClientTest {
    private val receipt = StorePurchaseRequest(StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "private-token", "paid-1")
    @Test fun `exact HTTP body route and platform auth configuration survive one forced refresh`() = runTest {
        val seen = mutableListOf<String?>()
        val refreshes = mutableListOf<Boolean>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("https", request.url.protocol.name)
            assertEquals("/api/store-purchases/verify", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("1", request.headers["X-FastToWin-Web-Session"])
            assertEquals("1", request.headers["X-FastToWin-CSRF"])
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertEquals(receipt, ProtocolJson.decodeFromString<StorePurchaseRequest>(body))
            seen += request.headers[HttpHeaders.Authorization]
            if (seen.size == 1) respond("{}", HttpStatusCode.Unauthorized)
            else respond(ProtocolJson.encodeToString(ServerMessage.StorePurchaseResult("paid-1", "fasttowin_gems_80", StorePurchaseStatus.GRANTED, 80, "", "BillingGemsAdded")),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val config = object : AuthRequestConfigurator {
            override fun configure(request: HttpRequestBuilder) {
                request.headers.append("X-FastToWin-Web-Session", "1")
                request.headers.append("X-FastToWin-CSRF", "1")
            }
        }
        val api = StorePurchaseApiClient("wss://example.invalid/game", { force -> refreshes += force; if (force) "fresh" else "old" }, config, client)
        assertEquals(StorePurchaseStatus.GRANTED, api.verify(receipt).status)
        assertEquals(listOf<String?>("Bearer old", "Bearer fresh"), seen)
        assertEquals(listOf(false, true), refreshes)
        api.close()
    }
    @Test fun `server unavailable retains safe typed status and never returns server exception details`() = runTest {
        val client = HttpClient(MockEngine { respond("{\"code\":\"STORAGE_UNAVAILABLE\",\"message\":\"private-token secret provider URL\"}", HttpStatusCode.ServiceUnavailable) }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = StorePurchaseApiClient("http://localhost", { "token" }, client = client)
        val result = api.verify(receipt)
        assertEquals(StorePurchaseStatus.UNAVAILABLE, result.status)
        assertEquals("ServerUnavailable", result.messageKey)
        assertEquals("", result.message)
        api.close()
    }
    @Test fun `bounds and missing bearer prevent network calls`() = runTest {
        var count = 0
        val client = HttpClient(MockEngine { count++; respond("{}") })
        val api = StorePurchaseApiClient("http://localhost", { null }, client = client)
        assertEquals(StorePurchaseStatus.INVALID, api.verify(receipt.copy(purchaseToken = "x".repeat(4097))).status)
        assertEquals(StorePurchaseStatus.UNAVAILABLE, api.verify(receipt).status)
        assertEquals(0, count)
        api.close()
    }
    @Test fun `cancellation propagates instead of converting into receipt failure`() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("cancelled") }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = StorePurchaseApiClient("http://localhost", { "token" }, client = client)
        assertFailsWith<CancellationException> { api.verify(receipt) }
        api.close()
    }
}
