package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RewardedAdApiClientTest {
    private val view = RewardedAdSessionView("session_1", "nonce", "unit", 999999, RewardedAdSessionStatus.PENDING)
    private fun kotlinx.coroutines.test.TestScope.http(handler: MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(MockEngineConfig().apply { dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler); addHandler { handler(it) } })) { install(ContentNegotiation) { json(ProtocolJson) } }

    @Test fun `prepare uses exact owner authenticated route body and one forced refresh`() = runTest {
        val tokens = mutableListOf<String?>()
        val refresh = mutableListOf<Boolean>()
        val client = http { request ->
            assertEquals("/api/rewarded-ads/sessions", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("1", request.headers["X-FastToWin-CSRF"])
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertEquals(RewardedAdSessionRequest("request_1"), ProtocolJson.decodeFromString<RewardedAdSessionRequest>(body))
            assertFalse(body.contains("owner"))
            tokens += request.headers[HttpHeaders.Authorization]
            if (tokens.size == 1) respond("{}", HttpStatusCode.Unauthorized)
            else respond(ProtocolJson.encodeToString(view), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val config = object : AuthRequestConfigurator {
            override fun configure(request: HttpRequestBuilder) { request.headers.append("X-FastToWin-CSRF", "1") }
        }
        val api = RewardedAdApiClient("wss://example.invalid/game", "owner", { forced -> refresh += forced; if (forced) "fresh" else "old" }, config, client)
        assertEquals<RewardedAdSessionView?>(view, runCatching { api.prepare("request_1") }.getOrNull())
        assertEquals(listOf(false, true), refresh)
        assertEquals(listOf<String?>("Bearer old", "Bearer fresh"), tokens)
        assertEquals("owner", api.ownerId)
        api.close()
    }
    @Test fun `status is owner authenticated and rejects a mismatched session`() = runTest {
        val client = http { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/rewarded-ads/sessions/session_1", request.url.encodedPath)
            assertEquals("Bearer current", request.headers[HttpHeaders.Authorization])
            respond(ProtocolJson.encodeToString(view.copy(sessionId = "other")), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = RewardedAdApiClient("https://example.invalid", "owner", { "current" }, client = client)
        assertFailsWith<RewardedAdApiException> { api.status("session_1") }
        api.close()
    }
    @Test fun `invalid identifiers missing token repeated unauthorized and close fail closed`() = runTest {
        var sends = 0
        var refreshes = 0
        val api = RewardedAdApiClient("https://example.invalid", "owner", { refreshes++; "token" }, client = http { sends++; respond("{}", HttpStatusCode.Unauthorized) })
        assertFailsWith<RewardedAdApiException> { api.prepare("../bad") }
        assertEquals(0, sends)
        assertFailsWith<RewardedAdApiException> { api.prepare("ok") }
        assertEquals(2, sends)
        assertEquals(2, refreshes)
        api.close()
        assertFailsWith<RewardedAdApiException> { api.prepare("after_close") }
        assertEquals(2, sends)
        val missing = RewardedAdApiClient("https://example.invalid", "owner", { null }, client = http { fail("must not send") })
        assertFailsWith<RewardedAdApiException> { missing.prepare("ok") }
        missing.close()
    }
    @Test fun `status reads current token every time and non-success never exposes server details`() = runTest {
        var token = "first"
        val seen = mutableListOf<String?>()
        var unavailable = false
        val api = RewardedAdApiClient("https://example.invalid", "owner", { token }, client = http { request ->
            seen += request.headers[HttpHeaders.Authorization]
            if (unavailable) respond("private-provider-error", HttpStatusCode.ServiceUnavailable)
            else respond(ProtocolJson.encodeToString(view), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        assertEquals(view, api.status("session_1")); token = "second"
        assertEquals(view, api.status("session_1")); unavailable = true
        val failure = assertFailsWith<RewardedAdApiException> { api.status("session_1") }
        assertNull(failure.message)
        assertEquals(listOf<String?>("Bearer first", "Bearer second", "Bearer second"), seen)
        api.close()
    }
    @Test fun `parent cancellation propagates`() = runTest {
        val api = RewardedAdApiClient("https://example.invalid", "owner", { throw CancellationException("cancel") })
        assertFailsWith<CancellationException> { api.prepare("ok") }
        api.close()
    }
    @Test fun `HTTP transport cancellation is propagated unchanged`() = runTest {
        val api = RewardedAdApiClient("https://example.invalid", "owner", { "token" },
            client = http { throw CancellationException("transport cancelled") })
        assertFailsWith<CancellationException> { api.prepare("ok") }
        api.close()
    }
    @Test fun `finite whole request timeout yields safe failure`() = runTest {
        val api = RewardedAdApiClient("https://example.invalid", "owner", { delay(1000); "token" }, timeoutMillis = 25)
        assertFailsWith<RewardedAdApiException> { api.prepare("ok") }
        assertEquals(25, testScheduler.currentTime)
        api.close()
    }
}
