package com.hienthai.fastowin.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerDrainTest {
    @Test fun `closes are spread evenly across the window`() {
        assertEquals(emptyList(), ServerDrain.schedule(0, 10_000))
        assertEquals(listOf(0L), ServerDrain.schedule(1, 10_000))
        assertEquals(listOf(0L, 2_500L, 5_000L, 7_500L), ServerDrain.schedule(4, 10_000))
    }

    @Test fun `drain closes every connection once within the window`() = runTest {
        val drain = ServerDrain(windowMillis = 4_000)
        val closedAt = mutableListOf<Long>()
        val connections = List<suspend () -> Unit>(4) { { closedAt += currentTime } }

        drain.drain(connections)
        drain.drain(connections)

        assertTrue(drain.draining)
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), closedAt)
    }

    @Test fun `a failing close does not stop the others`() = runTest {
        val closed = mutableListOf<Int>()
        ServerDrain(windowMillis = 0).drain(listOf<suspend () -> Unit>({ error("gone") }, { closed += 2 }))
        assertEquals(listOf(2), closed)
    }

    @Test fun `window comes from the environment`() {
        assertEquals(10_000L, ServerDrain.fromEnvironment(emptyMap()).windowMillis)
        assertEquals(3_000L, ServerDrain.fromEnvironment(mapOf("FASTTOWIN_DRAIN_SECONDS" to "3")).windowMillis)
        assertFailsWith<IllegalArgumentException> { ServerDrain.fromEnvironment(mapOf("FASTTOWIN_DRAIN_SECONDS" to "999")) }
    }

    @Test fun `readiness turns unavailable while liveness stays healthy`() = testApplication {
        val drain = ServerDrain(windowMillis = 0)
        application { gameModule(serverDrain = drain) }

        assertEquals("OK", client.get("/ready").bodyAsText())
        drain.drain(emptyList())
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
        assertEquals("OK", client.get("/health").bodyAsText())
    }
}
