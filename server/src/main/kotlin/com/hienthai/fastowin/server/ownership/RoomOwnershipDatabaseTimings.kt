package com.hienthai.fastowin.server.ownership

internal enum class OwnershipDatabaseStage(val key: String) {
    POOL_ACQUIRE("pool_acquire"), SETUP("transaction_setup"), BODY("transaction_body"),
    EXECUTE("statement_execute"), DECODE("row_decode"), COMMIT("commit"), ROLLBACK("rollback");
}

/** Fixed-size aggregates only: no SQL, identifiers, arguments or exceptions. */
internal class RoomOwnershipDatabaseTimings(private val nanoTime: () -> Long = System::nanoTime) {
    private val series = OwnershipDatabaseStage.entries.associateWith { Series() }

    fun <T> measure(stage: OwnershipDatabaseStage, operation: () -> T): T {
        val started = nanoTime()
        var failed = false
        try { return operation() }
        catch (error: Throwable) { failed = true; throw error }
        finally { series.getValue(stage).record((nanoTime() - started).coerceAtLeast(0), failed) }
    }

    fun render(): String = buildString {
        OwnershipDatabaseStage.entries.forEach { append(series.getValue(it).render(it.key)) }
    }

    private class Series {
        private var count = 0L
        private var failures = 0L
        private var totalNanos = 0L
        private var maxNanos = 0L
        private val buckets = LongArray(boundsMillis.size + 1)

        @Synchronized fun record(nanos: Long, failed: Boolean) {
            count++
            if (failed) failures++
            totalNanos += nanos
            maxNanos = maxOf(maxNanos, nanos)
            val index = boundsMillis.indexOfFirst { nanos <= it * 1_000_000 }.takeIf { it >= 0 } ?: boundsMillis.size
            buckets[index]++
        }

        @Synchronized fun render(stage: String): String = buildString {
            val prefix = "fasttowin_room_ownership_db_${stage}_seconds"
            var cumulative = 0L
            val rank = count - count / 20 // ceil(0.95 * count), without floating-point loss.
            val p95Index = if (count == 0L) -1 else buckets.indices.first { index ->
                cumulative += buckets[index]
                cumulative >= rank
            }
            val p95 = if (p95Index in boundsMillis.indices) boundsMillis[p95Index] / 1_000.0 else Double.NaN
            appendLine("${prefix}_count $count")
            appendLine("${prefix}_sum ${totalNanos / 1_000_000_000.0}")
            appendLine("${prefix}_max ${maxNanos / 1_000_000_000.0}")
            appendLine("${prefix}_p95_upper_bound $p95")
            appendLine("${prefix}_failures_total $failures")
        }
    }

    companion object {
        private val boundsMillis = longArrayOf(1, 5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 30_000)
        val metricNames: Set<String> = OwnershipDatabaseStage.entries.flatMap { stage ->
            listOf("count", "sum", "max", "p95_upper_bound", "failures_total").map { suffix ->
                "fasttowin_room_ownership_db_${stage.key}_seconds_$suffix"
            }
        }.toSet()
    }
}
