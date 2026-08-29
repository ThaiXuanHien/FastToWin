package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceStatusTest {
    @Test
    fun `status reports normal operation explicitly`() = testApplication {
        application {
            gameModule(
                serviceStatusProvider = { ServiceStatusResponse(maintenance = false) }
            )
        }

        val response = jsonClient().get("/status").body<ServiceStatusResponse>()

        assertFalse(response.maintenance)
        assertEquals(null, response.message)
    }

    @Test
    fun `status exposes planned maintenance message and polling interval`() = testApplication {
        application {
            gameModule(
                serviceStatusProvider = {
                    ServiceStatusResponse(
                        maintenance = true,
                        message = "Nâng cấp dữ liệu mùa giải.",
                        pollAfterSeconds = 60
                    )
                }
            )
        }

        val response = jsonClient().get("/status").body<ServiceStatusResponse>()

        assertTrue(response.maintenance)
        assertEquals("Nâng cấp dữ liệu mùa giải.", response.message)
        assertEquals(60, response.pollAfterSeconds)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(ProtocolJson) }
    }
}
