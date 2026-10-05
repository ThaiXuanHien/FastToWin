package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.admin.invalid
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

@Serializable enum class AnalyticsMetric {
    REGISTRATIONS, ITEM_SPEND, ITEM_PURCHASES, ITEM_BUYERS, TOP_ITEMS,
    MONEY_PAID, MONEY_REFUNDED, MONEY_NET, STORE_PURCHASES, STORE_BUYERS;
    val financial: Boolean get() = ordinal >= MONEY_PAID.ordinal
}
@Serializable enum class AnalyticsBucket { DAY, WEEK, MONTH }
@Serializable enum class CoverageStatus { COMPLETE, PARTIAL, UNAVAILABLE }
@Serializable data class AnalyticsRange(val from: Long, val until: Long)
@Serializable data class AnalyticsCoverage(val source: String, val environment: String, val status: CoverageStatus,
    val coverageStart: Long?, val freshness: Long?)
@Serializable data class AnalyticsPoint(val from: Long, val until: Long, val value: String?)
@Serializable data class AnalyticsComparison(val range: AnalyticsRange, val value: String?, val absoluteChange: String?,
    val percentageChange: String?, val coverage: AnalyticsCoverage)
@Serializable data class AnalyticsTopItem(val item: String, val purchases: String, val spend: String, val buyers: String)
@Serializable data class AnalyticsGroup(val source: String, val unit: String, val currency: String?, val currencyExponent: Int?,
    val summary: String?, val series: List<AnalyticsPoint>, val comparison: AnalyticsComparison,
    val top: List<AnalyticsTopItem> = emptyList())
@Serializable data class AnalyticsResult(val metric: AnalyticsMetric, val coverage: List<AnalyticsCoverage>,
    val groups: List<AnalyticsGroup>)
@Serializable data class AnalyticsResponse(val range: AnalyticsRange, val comparisonRange: AnalyticsRange,
    val timezone: String, val bucket: AnalyticsBucket, val snapshotAt: Long, val metrics: List<AnalyticsResult>)

data class AnalyticsQuery(val range: AnalyticsRange, val comparisonRange: AnalyticsRange,
    val bucket: AnalyticsBucket, val metrics: List<AnalyticsMetric>, val store: String?, val currency: String?,
    val item: String?, val product: String?, val environment: String) {
    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Bangkok")
        fun parse(parameters: Map<String, List<String>>, now: Long): AnalyticsQuery {
            val allowed = setOf("from", "until", "bucket", "metric", "preset", "store", "currency", "item", "product", "environment")
            if (parameters.keys.any { it !in allowed } || parameters.any { it.key != "metric" && it.value.size != 1 }) invalid()
            fun value(key: String) = parameters[key]?.singleOrNull()
            fun epoch(key: String) = value(key)?.let { it.toLongOrNull()?.takeIf { n -> n >= 0 } ?: invalid() }
            val preset = value("preset")?.let { it.toIntOrNull()?.takeIf { p -> p in setOf(7, 30, 90) } ?: invalid() } ?: 30
            val requestedUntil = epoch("until") ?: now
            val from = epoch("from") ?: (requestedUntil - preset * 86_400_000L)
            if ((value("from") == null) != (value("until") == null) || value("preset") != null && value("from") != null) invalid()
            val until = minOf(requestedUntil, now)
            if (from < 0 || from >= until || Instant.ofEpochMilli(requestedUntil).atZone(ZONE) > Instant.ofEpochMilli(from).atZone(ZONE).plusMonths(24)) invalid()
            val previousFrom = from - (until - from)
            if (previousFrom < 0) invalid()
            val bucket = value("bucket")?.let { runCatching { AnalyticsBucket.valueOf(it) }.getOrElse { invalid() } } ?: AnalyticsBucket.DAY
            val names = parameters["metric"]?.flatMap { it.split(',') } ?: listOf("REGISTRATIONS")
            if (names.size > AnalyticsMetric.entries.size) invalid()
            val metrics = names.map { runCatching { AnalyticsMetric.valueOf(it) }.getOrElse { invalid() } }.distinct()
            if (metrics.isEmpty()) invalid()
            val store = value("store")?.also { if (it !in setOf("GOOGLE_PLAY", "APP_STORE")) invalid() }
            val currency = value("currency")?.also { if (!it.matches(Regex("[A-Z]{3}")) && it !in setOf("GOLD", "GEMS")) invalid() }
            fun identifier(key: String) = value(key)?.also { if (!it.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) invalid() }
            val item = identifier("item"); val product = identifier("product")
            val environment = value("environment")?.also { if (it !in setOf("LIVE", "SANDBOX")) invalid() } ?: "LIVE"
            if (metrics.any { !it.financial } && (store != null || product != null || environment != "LIVE")) invalid()
            if (metrics.any { it.financial } && (item != null || currency in setOf("GOLD", "GEMS"))) invalid()
            if (metrics.any { !it.financial && it != AnalyticsMetric.REGISTRATIONS } && currency != null && currency !in setOf("GOLD", "GEMS")) invalid()
            if (metrics.contains(AnalyticsMetric.REGISTRATIONS) && (currency != null || item != null)) invalid()
            return AnalyticsQuery(AnalyticsRange(from, until), AnalyticsRange(previousFrom, from), bucket, metrics, store, currency, item, product, environment)
        }
    }
    fun buckets(): List<AnalyticsRange> {
        val first = Instant.ofEpochMilli(range.from).atZone(ZONE).toLocalDate().let { date -> when (bucket) {
            AnalyticsBucket.DAY -> date
            AnalyticsBucket.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            AnalyticsBucket.MONTH -> date.withDayOfMonth(1)
        } }.atStartOfDay(ZONE)
        return buildList {
            var start = first
            while (start.toInstant().toEpochMilli() < range.until) {
                val end = when (bucket) { AnalyticsBucket.DAY -> start.plusDays(1); AnalyticsBucket.WEEK -> start.plusWeeks(1); AnalyticsBucket.MONTH -> start.plusMonths(1) }
                add(AnalyticsRange(maxOf(start.toInstant().toEpochMilli(), range.from), minOf(end.toInstant().toEpochMilli(), range.until)))
                start = end
            }
        }
    }
}
