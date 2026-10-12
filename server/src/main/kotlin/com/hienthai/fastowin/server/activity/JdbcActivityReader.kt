package com.hienthai.fastowin.server.activity

import com.hienthai.fastowin.server.admin.AdminException
import com.hienthai.fastowin.server.analytics.AnalyticsCoverage
import com.hienthai.fastowin.server.analytics.CoverageStatus
import java.sql.Connection
import java.sql.Timestamp

/** Read only; shares the consumer's transaction/snapshot and never commits or opens a connection. */
class JdbcActivityReader(private val connection: Connection) {
    fun coverage(from: Long, until: Long, now: Long): AnalyticsCoverage {
        require(from >= 0 && until > from)
        val cutoff = now - ACTIVITY_RETENTION_MILLIS
        val windows = connection.prepareStatement("SELECT started_at, observed_until FROM activity_coverage_windows WHERE observed_until >= ? ORDER BY started_at, observed_until").use { s ->
            s.setTimestamp(1, Timestamp(cutoff)); s.executeQuery().use { r -> buildList {
                while (r.next()) add(maxOf(r.getTimestamp(1).time, cutoff) to r.getTimestamp(2).time)
            } }
        }
        if (windows.isEmpty()) return AnalyticsCoverage("SERVER", "LIVE", CoverageStatus.UNAVAILABLE, null, null)
        var coveredUntil = from
        for ((start, end) in windows) {
            if (start > coveredUntil) break
            coveredUntil = maxOf(coveredUntil, end)
        }
        val complete = from >= cutoff && until <= now && coveredUntil >= until
        return AnalyticsCoverage("SERVER", "LIVE", if (complete) CoverageStatus.COMPLETE else CoverageStatus.PARTIAL,
            windows.first().first, windows.maxOf { it.second })
    }

    /** Distinct count over the whole requested range; callers gate display on coverage. Never sum bucket counts. */
    fun activeSubjects(from: Long, until: Long, now: Long): Long {
        require(from >= 0 && until > from)
        val effectiveFrom = maxOf(from, now - ACTIVITY_RETENTION_MILLIS)
        val effectiveUntil = minOf(until, now)
        if (effectiveFrom >= effectiveUntil) return 0
        return connection.prepareStatement("SELECT count(DISTINCT a.subject_key) FROM activity_connection_observations a WHERE $overlap").use { s ->
            bindOverlap(s, effectiveFrom, effectiveUntil)
            s.executeQuery().use { r -> check(r.next()); r.getLong(1) }
        }
    }

    /** Coverage and recipients share one statement snapshot, including at READ_COMMITTED. */
    fun registeredRecipients(from: Long, until: Long, now: Long): List<String> {
        require(from >= 0 && until > from)
        return connection.prepareStatement("""
            WITH bounds AS (
                SELECT CAST(? AS TIMESTAMPTZ) AS range_from, CAST(? AS TIMESTAMPTZ) AS range_until,
                       CAST(? AS TIMESTAMPTZ) AS cutoff, CAST(? AS TIMESTAMPTZ) AS snapshot_now
            ), retained AS (
                SELECT GREATEST(w.started_at, b.cutoff) AS started_at, w.observed_until
                FROM activity_coverage_windows w CROSS JOIN bounds b WHERE w.observed_until >= b.cutoff
            ), overlapping AS (
                SELECT GREATEST(w.started_at, b.range_from) AS started_at,
                       LEAST(w.observed_until, b.range_until) AS observed_until
                FROM retained w CROSS JOIN bounds b
                WHERE w.started_at < b.range_until AND w.observed_until > b.range_from
            ), ordered AS (
                SELECT started_at, MAX(observed_until) OVER (
                    ORDER BY started_at, observed_until ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
                ) AS previous_end FROM overlapping
            ), state AS (
                SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM retained) THEN 'UNAVAILABLE'
                    WHEN b.range_from >= b.cutoff AND b.range_until <= b.snapshot_now
                        AND (SELECT MIN(started_at) FROM overlapping) = b.range_from
                        AND (SELECT MAX(observed_until) FROM overlapping) = b.range_until
                        AND NOT EXISTS (SELECT 1 FROM ordered WHERE started_at > previous_end)
                    THEN 'COMPLETE' ELSE 'PARTIAL' END AS status FROM bounds b
            ), selected AS (
                SELECT DISTINCT u.id::text AS user_id FROM activity_connection_observations a
                JOIN analytics_subject_links l ON l.subject_key = a.subject_key JOIN users u ON u.id = l.user_id
                CROSS JOIN bounds b CROSS JOIN state s
                WHERE s.status = 'COMPLETE' AND u.account_type = 'REGISTERED' AND u.status <> 'DELETED' AND (
                    (a.started_at < b.range_until AND a.observed_until > b.range_from) OR
                    (a.started_at = a.observed_until AND a.started_at >= b.range_from AND a.started_at < b.range_until)
                )
            )
            SELECT s.status, r.user_id FROM state s LEFT JOIN selected r ON TRUE ORDER BY r.user_id
        """.trimIndent()).use { s ->
            s.setTimestamp(1, Timestamp(from)); s.setTimestamp(2, Timestamp(until))
            s.setTimestamp(3, Timestamp(now - ACTIVITY_RETENTION_MILLIS)); s.setTimestamp(4, Timestamp(now))
            s.executeQuery().use { r -> buildList {
                while (r.next()) {
                    val status = CoverageStatus.valueOf(r.getString(1))
                    if (status != CoverageStatus.COMPLETE) throw AdminException(400,
                        if (status == CoverageStatus.UNAVAILABLE) "ACTIVITY_SOURCE_UNAVAILABLE" else "ACTIVITY_RANGE_INCOMPLETE",
                        "Connected account activity is unavailable or incomplete for this interval.")
                    r.getString(2)?.let { add(it) }
                }
            } }
        }
    }
    private fun bindOverlap(s: java.sql.PreparedStatement, from: Long, until: Long) {
        s.setTimestamp(1, Timestamp(until)); s.setTimestamp(2, Timestamp(from))
        s.setTimestamp(3, Timestamp(from)); s.setTimestamp(4, Timestamp(until))
    }

    private companion object {
        const val overlap = "(a.started_at < ? AND a.observed_until > ?) OR (a.started_at = a.observed_until AND a.started_at >= ? AND a.started_at < ?)"
    }
}
