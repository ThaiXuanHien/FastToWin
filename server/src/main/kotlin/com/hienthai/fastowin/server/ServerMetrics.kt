package com.hienthai.fastowin.server

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal class ServerMetrics(
    private val startedAtMillis: Long = System.currentTimeMillis(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val runtime: Runtime = Runtime.getRuntime()
) {
    private val activeWebSockets = AtomicLong()
    private val webSocketConnections = AtomicLong()
    private val authenticatedSessions = AtomicLong()
    private val webSocketMessages = AtomicLong()
    private val webSocketRateLimits = AtomicLong()
    private val invalidWebSocketMessages = AtomicLong()

    fun webSocketOpened() {
        activeWebSockets.incrementAndGet()
        webSocketConnections.incrementAndGet()
    }

    fun webSocketClosed() {
        activeWebSockets.updateAndGet { current -> (current - 1L).coerceAtLeast(0L) }
    }

    fun sessionAuthenticated() {
        authenticatedSessions.incrementAndGet()
    }

    fun webSocketMessageReceived() {
        webSocketMessages.incrementAndGet()
    }

    fun webSocketRateLimited() {
        webSocketRateLimits.incrementAndGet()
    }

    fun invalidWebSocketMessage() {
        invalidWebSocketMessages.incrementAndGet()
    }

    fun render(): String {
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val uptimeSeconds = ((nowMillis() - startedAtMillis).coerceAtLeast(0L) / 1_000.0)
        return buildString {
            gauge("fasttowin_uptime_seconds", "Server uptime in seconds.", uptimeSeconds)
            gauge("fasttowin_websocket_connections", "Currently open WebSocket connections.", activeWebSockets.get())
            counter("fasttowin_websocket_connections_total", "WebSocket connections opened.", webSocketConnections.get())
            counter("fasttowin_authenticated_sessions_total", "WebSocket sessions authenticated.", authenticatedSessions.get())
            counter("fasttowin_websocket_messages_total", "Text messages received over WebSocket.", webSocketMessages.get())
            counter("fasttowin_websocket_rate_limited_total", "WebSocket requests rejected by rate limiting.", webSocketRateLimits.get())
            counter("fasttowin_websocket_invalid_messages_total", "Invalid WebSocket messages received.", invalidWebSocketMessages.get())
            gauge("fasttowin_jvm_memory_used_bytes", "JVM heap memory currently used.", usedMemory)
            gauge("fasttowin_jvm_memory_max_bytes", "Maximum JVM heap memory.", runtime.maxMemory())
        }
    }

    private fun StringBuilder.counter(name: String, help: String, value: Long) {
        metric(name, help, "counter", value.toString())
    }

    private fun StringBuilder.gauge(name: String, help: String, value: Long) {
        metric(name, help, "gauge", value.toString())
    }

    private fun StringBuilder.gauge(name: String, help: String, value: Double) {
        metric(name, help, "gauge", String.format(Locale.ROOT, "%.3f", value))
    }

    private fun StringBuilder.metric(name: String, help: String, type: String, value: String) {
        append("# HELP ").append(name).append(' ').append(help).append('\n')
        append("# TYPE ").append(name).append(' ').append(type).append('\n')
        append(name).append(' ').append(value).append('\n')
    }
}
