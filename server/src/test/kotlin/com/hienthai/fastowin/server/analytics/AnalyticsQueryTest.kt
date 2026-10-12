package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.admin.AdminException
import java.time.Instant
import kotlin.test.*

class AnalyticsQueryTest {
    private val now = time("2026-10-05T10:00:00Z")
    @Test fun defaultAndPresetsUseElapsedThirtySevenAndNinetyDays() {
        for (days in listOf(7, 30, 90)) {
            val query = AnalyticsQuery.parse(mapOf("preset" to listOf("$days")), now)
            assertEquals(AnalyticsRange(now - days * 86400000L, now), query.range)
            assertEquals(AnalyticsRange(now - days * 172800000L, query.range.from), query.comparisonRange)
        }
        assertEquals(now - 30 * 86400000L, AnalyticsQuery.parse(emptyMap(), now).range.from)
    }
    @Test fun partialCurrentPeriodComparesOnlyElapsedDuration() {
        val q = query("2026-10-01T17:00:00Z", "2026-10-31T17:00:00Z", "DAY")
        assertEquals(now, q.range.until)
        assertEquals(q.range.until - q.range.from, q.comparisonRange.until - q.comparisonRange.from)
    }
    @Test fun bangkokMondayMidnightAndCalendarMonthBoundaries() {
        val week = query("2026-10-04T16:59:59Z", "2026-10-05T17:00:00Z", "WEEK")
        assertEquals(time("2026-10-04T17:00:00Z"), week.buckets()[0].until)
        assertEquals(2, week.buckets().size)
        val day = query("2026-10-03T16:59:59Z", "2026-10-04T17:00:00Z", "DAY")
        assertEquals(time("2026-10-03T17:00:00Z"), day.buckets()[0].until)
        val month = query("2026-09-30T16:59:59Z", "2026-10-01T17:00:00Z", "MONTH")
        assertEquals(time("2026-09-30T17:00:00Z"), month.buckets()[0].until)
    }
    @Test fun maximumIsTwentyFourCalendarMonthsAndFiltersAreBounded() {
        val end = time("2026-10-01T17:00:00Z")
        val start = time("2024-10-01T17:00:00Z")
        assertEquals(start, AnalyticsQuery.parse(mapOf("from" to listOf("$start"), "until" to listOf("$end")), now).range.from)
        for (parameters in listOf(mapOf("from" to listOf("${start - 1}"), "until" to listOf("$end")),
            mapOf("bucket" to listOf("day")), mapOf("preset" to listOf("31")), mapOf("from" to listOf("1")),
            mapOf("metric" to listOf("ITEM_SPEND"), "item" to listOf("a';DROP TABLE users;--")),
            mapOf("metric" to listOf("MONEY_NET"), "store" to listOf("fake")),
            mapOf("metric" to listOf("ITEM_SPEND"), "currency" to listOf("THB")),
            mapOf("metric" to listOf("MONEY_NET"), "currency" to listOf("GOLD")),
            mapOf("until" to listOf("${Long.MAX_VALUE}"), "from" to listOf("1")),
            mapOf("secret" to listOf("x")), mapOf("bucket" to listOf("DAY", "WEEK")))) {
            assertEquals(400, assertFailsWith<AdminException> { AnalyticsQuery.parse(parameters, now) }.status)
        }
    }
    @Test fun activityIsNonFinancialAndRejectsAllDimensions() {
        val query = AnalyticsQuery.parse(mapOf("metric" to listOf("ACTIVE_PLAYERS")), now)
        assertFalse(query.metrics.single().financial)
        for ((key, value) in listOf("currency" to "GOLD", "currency" to "THB", "item" to "hat", "store" to "GOOGLE_PLAY", "product" to "sku", "environment" to "SANDBOX")) {
            assertEquals(400, assertFailsWith<AdminException> { AnalyticsQuery.parse(mapOf("metric" to listOf("ACTIVE_PLAYERS"), key to listOf(value)), now) }.status)
        }
        assertTrue(AnalyticsMetric.valueOf("STORE_BUYERS").financial)
    }
    private fun query(from: String, until: String, bucket: String) = AnalyticsQuery.parse(mapOf(
        "from" to listOf("${time(from)}"), "until" to listOf("${time(until)}"), "bucket" to listOf(bucket)), now)
    companion object { fun time(value: String) = Instant.parse(value).toEpochMilli() }
}
