package com.hienthai.fastowin.data.network

import com.hienthai.fastowin.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UgcTermsApiTest {
    @Test fun `unauthenticated terms lookup returns server version without publishing a nickname`() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            respond("""{"requiredVersion":"server-v9","acceptedVersion":null,"enforced":true}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(ProtocolJson) } }
        val api = ContentTermsApiClient("wss://example.test", client)
        val status = api.fetch()
        assertEquals("server-v9", status.requiredVersion)
        assertTrue(status.enforced)
        assertEquals(listOf("GET /api/content-terms"), requests)
        api.close()
    }
}
