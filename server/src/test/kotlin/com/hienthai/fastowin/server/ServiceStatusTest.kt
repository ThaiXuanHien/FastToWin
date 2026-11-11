package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.server.presence.PresenceStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceStatusTest {
    @Test
    fun `internal metrics use prometheus text format`() = testApplication {
        application { gameModule() }

        val response = client.get("/internal/metrics")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"].orEmpty().startsWith("text/plain"))
        assertTrue(body.contains("fasttowin_websocket_connections"))
        assertTrue(body.contains("fasttowin_jvm_memory_used_bytes"))
    }

    @Test
    fun `internal metrics expose presence failure counters when presence is enabled`() = testApplication {
        val store = object : PresenceStore {
            override suspend fun renew(playerId: String, connectionId: UUID, presence: FriendPresence) = true
            override suspend fun revoke(playerId: String, connectionId: UUID) = true
            override suspend fun read(playerIds: Collection<String>) = emptyMap<String, FriendPresence>()
            override fun renderMetrics() = """
                # TYPE fasttowin_presence_redis_failures_total counter
                fasttowin_presence_redis_failures_total{operation="read"} 0
                fasttowin_presence_redis_failures_total{operation="write"} 0
            """.trimIndent()
            override fun close() = Unit
        }
        application { gameModule(engine = GameEngine(presenceStore = store)) }

        val body = client.get("/internal/metrics").bodyAsText()

        assertTrue(body.contains("fasttowin_presence_redis_failures_total{operation=\"read\"} 0"))
        assertTrue(body.contains("fasttowin_presence_redis_failures_total{operation=\"write\"} 0"))
    }

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
