package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class PostgresAnalyticsRepository(private val source: DataSource) : AnalyticsRepository {
    override suspend fun read(admin: AdminService, token: String?, query: AnalyticsQuery): AnalyticsResponse = withContext(Dispatchers.IO) {
        try {
            source.connection.use { connection ->
                // Acquire outside the repeatable-read transaction: a SELECT waiting for an xact
                // lock inside that transaction could freeze authorization before revocation commits.
                connection.autoCommit = true
                val previousIsolation = connection.transactionIsolation
                var locked = false
                try {
                    connection.createStatement().use { it.execute("SELECT pg_advisory_lock(7421092187349)") }
                    locked = true
                    connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                    connection.autoCommit = false
                    val transaction = JdbcAdminTransaction(connection)
                    val actor = admin.authorize(transaction, token)
                    requireMetrics(actor, query) // Every requested metric before any facts/coverage query.
                    val snapshot = rows(connection, "SELECT clock_timestamp()") { it.getTimestamp(1).time }.single()
                    transaction.appendAudit(AuditEntry(UUID.randomUUID().toString(), actor.id, "ANALYTICS_READ", null, null,
                        "from=${query.range.from};until=${query.range.until};metrics=${query.metrics.joinToString(",")}", null, null, snapshot))
                    val reader = Reader(connection, query)
                    val response = AnalyticsResponse(query.range, query.comparisonRange, "Asia/Bangkok", query.bucket,
                        snapshot, query.metrics.map(reader::metric))
                    connection.commit()
                    response
                } catch (error: Throwable) {
                    try { if (!connection.autoCommit) connection.rollback() } catch (rollback: SQLException) { error.addSuppressed(rollback) }
                    throw error
                } finally {
                    if (locked) {
                        try {
                            connection.autoCommit = true
                            if (!rows(connection, "SELECT pg_advisory_unlock(7421092187349)") { it.getBoolean(1) }.single()) throw SQLException("Authorization lock was not held")
                            connection.transactionIsolation = previousIsolation
                        } catch (error: Throwable) {
                            // A pooled connection with a session lock must never be recycled.
                            try { connection.abort { task -> task.run() } } catch (abort: Throwable) { error.addSuppressed(abort) }
                            throw error
                        }
                    }
                }
            }
        } catch (_: SQLException) { unavailable() }
    }

    private class Reader(private val connection: Connection, private val query: AnalyticsQuery) {
        private val coverageCache = mutableMapOf<Pair<String, String>, AnalyticsCoverage>()
        private data class Dimension(val source: String, val currency: String?, val exponent: Int?)
        private data class Aggregate(val value: String, val events: Long)
        private data class Filter(val sql: String, val values: List<Any?>)

        fun metric(metric: AnalyticsMetric): AnalyticsResult {
            val sources = if (metric.financial) query.store?.let(::listOf) ?: listOf("GOOGLE_PLAY", "APP_STORE") else listOf("SERVER")
            val coverage = sources.map { coverage(metric, it, query.range) }
            val groups = sources.flatMap { source ->
                val currentCoverage = coverage.single { it.source == source }
                val priorCoverage = coverage(metric, source, query.comparisonRange)
                val dimensions = when {
                    metric == AnalyticsMetric.REGISTRATIONS -> listOf(Dimension(source, null, null))
                    !metric.financial -> (query.currency?.let(::listOf) ?: listOf("GOLD", "GEMS")).map { Dimension(source, it, 0) }
                    currentCoverage.status == CoverageStatus.UNAVAILABLE && priorCoverage.status == CoverageStatus.UNAVAILABLE -> emptyList()
                    metric in setOf(AnalyticsMetric.STORE_PURCHASES, AnalyticsMetric.STORE_BUYERS) -> listOf(Dimension(source, null, null))
                    else -> {
                        // Unit discovery includes both event kinds, so a complete known currency
                        // can correctly show zero paid/refunded instead of disappearing.
                        val filter = filter(AnalyticsMetric.MONEY_NET, AnalyticsRange(query.comparisonRange.from, query.range.until), Dimension(source, query.currency, null))
                        rows(connection, "SELECT DISTINCT currency,currency_exponent FROM analytics_financial_events WHERE ${filter.sql} ORDER BY currency,currency_exponent", filter.values) {
                            Dimension(source, it.getString(1), it.getInt(2))
                        }
                    }
                }
                dimensions.map { dimension -> group(metric, dimension, currentCoverage, priorCoverage) }
            }
            return AnalyticsResult(metric, coverage, groups)
        }

        private fun coverage(metric: AnalyticsMetric, source: String, range: AnalyticsRange): AnalyticsCoverage {
            val family = when { metric.financial -> "FINANCIAL"; metric == AnalyticsMetric.REGISTRATIONS -> "REGISTRATIONS"; else -> "ITEM_PURCHASES" }
            val raw = coverageCache.getOrPut(family to source) { rows(connection, "SELECT status,coverage_start,freshness FROM analytics_source_coverage WHERE metric=? AND source=? AND environment=?",
                listOf(family, source, query.environment)) {
                AnalyticsCoverage(source, query.environment, CoverageStatus.valueOf(it.getString(1)), it.getTimestamp(2)?.time, it.getTimestamp(3)?.time)
            }.singleOrNull() ?: AnalyticsCoverage(source, query.environment, CoverageStatus.UNAVAILABLE, null, null) }
            return if (raw.status == CoverageStatus.COMPLETE && (raw.coverageStart == null || raw.coverageStart > range.from)) raw.copy(status = CoverageStatus.PARTIAL) else raw
        }

        private fun group(metric: AnalyticsMetric, dimension: Dimension, current: AnalyticsCoverage, prior: AnalyticsCoverage): AnalyticsGroup {
            val summary = value(metric, dimension, query.range, current)
            val previous = value(metric, dimension, query.comparisonRange, prior)
            val difference = if (summary != null && previous != null && current.status == CoverageStatus.COMPLETE && prior.status == CoverageStatus.COMPLETE)
                BigDecimal(summary).subtract(BigDecimal(previous)).toPlainString() else null
            val percent = if (difference != null && BigDecimal(previous).signum() != 0)
                BigDecimal(difference).multiply(BigDecimal(100)).divide(BigDecimal(previous).abs(), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() else null
            val unit = when (metric) {
                AnalyticsMetric.ITEM_SPEND -> dimension.currency!!
                AnalyticsMetric.MONEY_PAID, AnalyticsMetric.MONEY_REFUNDED, AnalyticsMetric.MONEY_NET -> "MONEY_MINOR"
                AnalyticsMetric.ITEM_BUYERS, AnalyticsMetric.STORE_BUYERS -> "BUYERS"
                AnalyticsMetric.REGISTRATIONS -> "REGISTRATIONS"
                else -> "PURCHASES"
            }
            val byBucket = if (current.status == CoverageStatus.UNAVAILABLE) emptyMap() else series(metric, dimension)
            val points = query.buckets().map { range ->
                val raw = byBucket[range.from]
                val bucketCoverage = coverage(metric, dimension.source, range).status
                val value = when {
                    current.status == CoverageStatus.UNAVAILABLE -> null
                    raw != null -> raw
                    bucketCoverage == CoverageStatus.COMPLETE -> "0"
                    else -> null
                }
                AnalyticsPoint(range.from, range.until, value)
            }
            return AnalyticsGroup(dimension.source, unit, dimension.currency, dimension.exponent, summary, points,
                AnalyticsComparison(query.comparisonRange, previous, difference, percent, prior),
                if (metric == AnalyticsMetric.TOP_ITEMS && current.status != CoverageStatus.UNAVAILABLE) top(dimension) else emptyList())
        }

        private fun value(metric: AnalyticsMetric, dimension: Dimension, range: AnalyticsRange, coverage: AnalyticsCoverage): String? {
            if (coverage.status == CoverageStatus.UNAVAILABLE) return null
            val filter = filter(metric, range, dimension)
            val result = rows(connection, "SELECT ${expression(metric)}::text,COUNT(*) FROM ${table(metric)} WHERE ${filter.sql}", filter.values) {
                Aggregate(it.getString(1) ?: "0", it.getLong(2))
            }.single()
            return if (result.events == 0L && coverage.status != CoverageStatus.COMPLETE) null else result.value
        }

        private fun series(metric: AnalyticsMetric, dimension: Dimension): Map<Long, String> {
            val filter = filter(metric, query.range, dimension)
            val bucket = "date_trunc(?, ${timeColumn(metric)} AT TIME ZONE 'Asia/Bangkok') AT TIME ZONE 'Asia/Bangkok'"
            return rows(connection, "SELECT $bucket AS bucket, ${expression(metric)}::text FROM ${table(metric)} WHERE ${filter.sql} GROUP BY bucket ORDER BY bucket",
                listOf(query.bucket.name.lowercase()) + filter.values) {
                maxOf(it.getTimestamp(1).time, query.range.from) to it.getString(2)
            }.toMap()
        }

        private fun top(dimension: Dimension): List<AnalyticsTopItem> {
            val filter = filter(AnalyticsMetric.TOP_ITEMS, query.range, dimension)
            return rows(connection, """SELECT item_id,COUNT(*)::text,SUM(amount)::text,COUNT(DISTINCT subject_key)::text
                FROM analytics_item_purchases WHERE ${filter.sql} GROUP BY item_id
                ORDER BY COUNT(*) DESC,SUM(amount) DESC,item_id ASC LIMIT 10""", filter.values) {
                AnalyticsTopItem(it.getString(1), it.getString(2), it.getString(3), it.getString(4))
            }
        }
        private fun table(metric: AnalyticsMetric) = when { metric.financial -> "analytics_financial_events"; metric == AnalyticsMetric.REGISTRATIONS -> "analytics_registrations"; else -> "analytics_item_purchases" }
        private fun timeColumn(metric: AnalyticsMetric) = when { metric.financial -> "occurred_at"; metric == AnalyticsMetric.REGISTRATIONS -> "registered_at"; else -> "purchased_at" }
        private fun expression(metric: AnalyticsMetric) = when (metric) {
            AnalyticsMetric.ITEM_SPEND -> "SUM(amount)"
            AnalyticsMetric.MONEY_PAID, AnalyticsMetric.MONEY_REFUNDED -> "SUM(amount_minor)"
            AnalyticsMetric.MONEY_NET -> "SUM(CASE WHEN event_type='PAYMENT' THEN amount_minor ELSE -amount_minor END)"
            AnalyticsMetric.ITEM_BUYERS, AnalyticsMetric.STORE_BUYERS -> "COUNT(DISTINCT subject_key)"
            else -> "COUNT(*)"
        }
        private fun filter(metric: AnalyticsMetric, range: AnalyticsRange, dimension: Dimension): Filter {
            val conditions = mutableListOf("${timeColumn(metric)}>=?", "${timeColumn(metric)}<?")
            val values = mutableListOf<Any?>(Timestamp(range.from), Timestamp(range.until))
            fun add(column: String, value: Any?) { if (value != null) { conditions.add("$column=?"); values.add(value) } }
            if (metric.financial) {
                add("source", dimension.source); add("environment", query.environment)
                add("currency", dimension.currency ?: query.currency); add("currency_exponent", dimension.exponent); add("product_id", query.product)
                when (metric) {
                    AnalyticsMetric.MONEY_REFUNDED -> add("event_type", "REFUND")
                    AnalyticsMetric.MONEY_NET -> Unit
                    else -> add("event_type", "PAYMENT")
                }
            } else if (metric != AnalyticsMetric.REGISTRATIONS) { add("currency", dimension.currency); add("item_id", query.item) }
            return Filter(conditions.joinToString(" AND "), values)
        }
    }
}

private fun <T> rows(connection: Connection, sql: String, values: List<Any?> = emptyList(), map: (ResultSet) -> T): List<T> =
    connection.prepareStatement(sql).use { statement ->
        values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { result -> buildList { while (result.next()) add(map(result)) } }
    }
