package com.hienthai.fastowin.server

import kotlin.test.Test
import kotlin.test.assertContains

class ServerMetricsTest {
    @Test
    fun `renders bounded prometheus counters without player labels`() {
        var now = 10_000L
        val metrics = ServerMetrics(startedAtMillis = 5_000L, nowMillis = { now })

        metrics.webSocketOpened()
        metrics.webSocketOpened()
        metrics.webSocketClosed()
        metrics.sessionAuthenticated()
        metrics.webSocketMessageReceived()
        metrics.webSocketRateLimited()
        metrics.invalidWebSocketMessage()
        now += 1_000L

        val output = metrics.render()
        assertContains(output, "fasttowin_uptime_seconds 6.000")
        assertContains(output, "fasttowin_websocket_connections 1")
        assertContains(output, "fasttowin_websocket_connections_total 2")
        assertContains(output, "fasttowin_authenticated_sessions_total 1")
        assertContains(output, "fasttowin_websocket_messages_total 1")
        assertContains(output, "fasttowin_websocket_rate_limited_total 1")
        assertContains(output, "fasttowin_websocket_invalid_messages_total 1")
    }
}
