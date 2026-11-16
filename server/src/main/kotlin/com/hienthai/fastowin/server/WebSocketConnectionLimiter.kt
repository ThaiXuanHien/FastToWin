package com.hienthai.fastowin.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Caps WebSocket connections that are open at the same time on this replica (SEC-09),
 * per client IP and in total. The connect-rate limit alone lets one address hold an
 * unbounded number of idle sockets.
 *
 * The per-IP default is deliberately generous: Vietnamese carriers place many players
 * behind one carrier-grade NAT address.
 */
class WebSocketConnectionLimiter(
    val maxPerClient: Int = DEFAULT_MAX_PER_CLIENT,
    val maxTotal: Int = DEFAULT_MAX_TOTAL
) {
    init {
        require(maxPerClient > 0) { "FASTTOWIN_WS_MAX_PER_IP must be positive." }
        require(maxTotal > 0) { "FASTTOWIN_WS_MAX_TOTAL must be positive." }
    }

    private val total = AtomicInteger()
    private val perClient = ConcurrentHashMap<String, AtomicInteger>()
    private val rejectedPerClient = AtomicLong()
    private val rejectedTotal = AtomicLong()

    /** Returns a lease to release when the socket closes, or null when a cap is reached. */
    fun tryAcquire(clientKey: String): Lease? {
        if (total.incrementAndGet() > maxTotal) {
            total.decrementAndGet()
            rejectedTotal.incrementAndGet()
            return null
        }
        var admitted = false
        perClient.compute(clientKey) { _, current ->
            val counter = current ?: AtomicInteger()
            if (counter.get() < maxPerClient) {
                counter.incrementAndGet()
                admitted = true
            }
            counter.takeIf { it.get() > 0 }
        }
        if (!admitted) {
            total.decrementAndGet()
            rejectedPerClient.incrementAndGet()
            return null
        }
        return Lease(clientKey)
    }

    fun openConnections(): Int = total.get()

    fun openConnections(clientKey: String): Int = perClient[clientKey]?.get() ?: 0

    fun renderMetrics(): String = buildString {
        append("# HELP fasttowin_websocket_connection_limit_rejected_total WebSocket connections refused by open-connection caps.\n")
        append("# TYPE fasttowin_websocket_connection_limit_rejected_total counter\n")
        append("fasttowin_websocket_connection_limit_rejected_total{scope=\"client\"} ").append(rejectedPerClient.get()).append('\n')
        append("fasttowin_websocket_connection_limit_rejected_total{scope=\"total\"} ").append(rejectedTotal.get()).append('\n')
    }

    inner class Lease internal constructor(private val clientKey: String) {
        private var released = false

        fun release() {
            synchronized(this) {
                if (released) return
                released = true
            }
            perClient.computeIfPresent(clientKey) { _, counter ->
                counter.decrementAndGet()
                counter.takeIf { it.get() > 0 }
            }
            total.decrementAndGet()
        }
    }

    companion object {
        const val DEFAULT_MAX_PER_CLIENT = 64
        const val DEFAULT_MAX_TOTAL = 20_000

        fun fromEnvironment(values: Map<String, String> = System.getenv()): WebSocketConnectionLimiter {
            fun read(name: String, default: Int): Int = values[name]?.trim()?.takeIf(String::isNotEmpty)
                ?.let { requireNotNull(it.toIntOrNull()) { "$name must be a whole number." } } ?: default
            return WebSocketConnectionLimiter(
                read("FASTTOWIN_WS_MAX_PER_IP", DEFAULT_MAX_PER_CLIENT),
                read("FASTTOWIN_WS_MAX_TOTAL", DEFAULT_MAX_TOTAL)
            )
        }
    }
}
