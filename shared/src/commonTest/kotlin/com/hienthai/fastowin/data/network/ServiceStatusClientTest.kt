package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ServiceStatusClientTest {
    @Test fun `new client bypasses old cached status without adding a CORS preflight header`() = runTest {
        val transport = HttpClient(MockEngine { request ->
            assertEquals("/api/status", request.url.encodedPath)
            assertEquals("https", request.url.protocol.name)
            assertNull(request.headers[HttpHeaders.CacheControl])
            respond("""{"maintenance":true,"state":"MAINTENANCE","messageEn":"Update","pollAfterSeconds":5}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val client = ServiceStatusClient("wss://example.invalid/game", transport)
        assertEquals(MaintenanceState.MAINTENANCE, client.fetchOrNull()?.state)
        client.close()
    }
    @Test fun `unavailable never fabricates ACTIVE`() = runTest {
        val client = ServiceStatusClient("http://localhost", HttpClient(MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) }))
        assertNull(client.fetchOrNull()); client.close()
    }
    @Test fun `cancellation propagates`() = runTest {
        val client = ServiceStatusClient("http://localhost", HttpClient(MockEngine { throw CancellationException("cancel") }))
        assertFailsWith<CancellationException> { client.fetchOrNull() }; client.close()
    }
}
