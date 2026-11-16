package com.hienthai.fastowin.server

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebSocketConnectionLimiterTest {
    @Test
    fun `caps open connections per client and frees them on release`() {
        val limiter = WebSocketConnectionLimiter(maxPerClient = 2, maxTotal = 10)

        val first = assertNotNull(limiter.tryAcquire("a"))
        assertNotNull(limiter.tryAcquire("a"))
        assertNull(limiter.tryAcquire("a"))
        assertNotNull(limiter.tryAcquire("b"))

        first.release()
        first.release()
        assertEquals(1, limiter.openConnections("a"))
        assertNotNull(limiter.tryAcquire("a"))
        assertEquals(3, limiter.openConnections())
        assertContains(limiter.renderMetrics(), "{scope=\"client\"} 1")
    }

    @Test
    fun `caps total open connections`() {
        val limiter = WebSocketConnectionLimiter(maxPerClient = 5, maxTotal = 2)

        val lease = assertNotNull(limiter.tryAcquire("a"))
        assertNotNull(limiter.tryAcquire("b"))
        assertNull(limiter.tryAcquire("c"))
        assertEquals(0, limiter.openConnections("c"))

        lease.release()
        assertNotNull(limiter.tryAcquire("c"))
        assertContains(limiter.renderMetrics(), "{scope=\"total\"} 1")
    }

    @Test
    fun `reads limits from the environment`() {
        val limiter = WebSocketConnectionLimiter.fromEnvironment(
            mapOf("FASTTOWIN_WS_MAX_PER_IP" to "8", "FASTTOWIN_WS_MAX_TOTAL" to "500")
        )
        assertEquals(8, limiter.maxPerClient)
        assertEquals(500, limiter.maxTotal)
        assertEquals(64, WebSocketConnectionLimiter.fromEnvironment(emptyMap()).maxPerClient)
        assertFailsWith<IllegalArgumentException> { WebSocketConnectionLimiter.fromEnvironment(mapOf("FASTTOWIN_WS_MAX_TOTAL" to "0")) }
    }
}
