package com.hienthai.fastowin.server.ownership

import java.util.concurrent.atomic.AtomicLong

internal class RoomOwnershipMetrics {
    private val takeovers = AtomicLong()
    private val rejectedFences = AtomicLong()
    private val localRoutes = AtomicLong()
    private val peerRoutes = AtomicLong()
    private val failedRoutes = AtomicLong()
    private val resyncs = AtomicLong()
    private val settlementFailures = AtomicLong()
    private val recoveryBuckets = longArrayOf(250, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 30_000)
    private val bucketCounts = LongArray(recoveryBuckets.size)
    private var recoveryCount = 0L
    private var recoveryTotalMillis = 0L

    fun takeover() { takeovers.incrementAndGet() }
    fun fenceRejected() { rejectedFences.incrementAndGet() }
    fun localRoute() { localRoutes.incrementAndGet() }
    fun peerRoute() { peerRoutes.incrementAndGet() }
    fun routeFailed() { failedRoutes.incrementAndGet() }
    fun resynced() { resyncs.incrementAndGet() }
    fun settlementFailed() { settlementFailures.incrementAndGet() }

    @Synchronized
    fun recoveryCompleted(elapsedMillis: Long) {
        val duration = elapsedMillis.coerceAtLeast(0)
        recoveryCount++
        recoveryTotalMillis += duration
        recoveryBuckets.forEachIndexed { index, upper ->
            if (duration <= upper) bucketCounts[index]++
        }
    }

    fun render(oldestQueuedSeconds: Double): String = buildString {
        counter("takeovers_total", takeovers)
        counter("fence_rejections_total", rejectedFences)
        appendLine("# TYPE fasttowin_room_ownership_routes_total counter")
        appendLine("fasttowin_room_ownership_routes_total{outcome=\"local\"} ${localRoutes.get()}")
        appendLine("fasttowin_room_ownership_routes_total{outcome=\"peer\"} ${peerRoutes.get()}")
        appendLine("fasttowin_room_ownership_routes_total{outcome=\"unavailable\"} ${failedRoutes.get()}")
        counter("resyncs_total", resyncs)
        counter("settlement_failures_total", settlementFailures)
        appendLine("# TYPE fasttowin_room_ownership_oldest_queued_event_seconds gauge")
        appendLine("fasttowin_room_ownership_oldest_queued_event_seconds $oldestQueuedSeconds")
        synchronized(this@RoomOwnershipMetrics) {
            val name = "fasttowin_room_ownership_recovery_seconds"
            appendLine("# TYPE $name histogram")
            recoveryBuckets.forEachIndexed { index, upper ->
                appendLine("${name}_bucket{le=\"${upper / 1000.0}\"} ${bucketCounts[index]}")
            }
            appendLine("${name}_bucket{le=\"+Inf\"} $recoveryCount")
            appendLine("${name}_sum ${recoveryTotalMillis / 1000.0}")
            appendLine("${name}_count $recoveryCount")
        }
    }

    private fun StringBuilder.counter(suffix: String, count: AtomicLong) {
        val name = "fasttowin_room_ownership_$suffix"
        appendLine("# TYPE $name counter")
        appendLine("$name ${count.get()}")
    }
}
