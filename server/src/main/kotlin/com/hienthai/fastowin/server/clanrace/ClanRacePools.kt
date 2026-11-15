package com.hienthai.fastowin.server.clanrace

import java.math.BigInteger
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate

internal data class ClanRaceStrength(
    val clanId: String,
    val priorScore: BigInteger,
    val activeMembers: Int
)

/** Four complete weeks of contribution are primary; roster and identity provide stable tie breaks. */
internal fun assignRacePools(clans: List<ClanRaceStrength>): Map<String, String> = buildMap {
    val order = compareByDescending<ClanRaceStrength> { it.priorScore }
        .thenByDescending { it.activeMembers }
        .thenBy { it.clanId }
    for ((starter, group) in clans.groupBy { it.priorScore == BigInteger.ZERO }) {
        val poolCount = (group.size + 19) / 20
        val baseSize = group.size / poolCount
        val largerPools = group.size % poolCount
        var index = 0
        val sorted = group.sortedWith(order)
        repeat(poolCount) { pool ->
            repeat(baseSize + if (pool < largerPools) 1 else 0) {
                put(sorted[index++].clanId, "${if (starter) "starter" else "strength"}-${pool + 1}")
            }
        }
    }
}

/** Reconstruct the start boundary from durable history, even if the worker wakes days late. */
internal fun JdbcClanRaceStore.ensureRacePools(week: LocalDate, now: Long) {
    if (published(week)?.poolingVersion != 1 || now < raceWeekMillis(week)) return
    val date = Date.valueOf(week)
    if (rows("SELECT 1 FROM clan_race_pool_weeks WHERE week_start=?", listOf(date)) { true }.isEmpty()) {
        val start = Timestamp(raceWeekMillis(week))
        val strengths = rows("""
          SELECT c.clan_id,
            coalesce((SELECT sum(e.score_units) FROM clan_race_events e WHERE e.clan_id=c.clan_id AND e.week_start>=? AND e.week_start<? AND e.occurred_at<?),0) AS prior_score,
            (SELECT count(*) FROM clan_race_tenures t WHERE t.clan_id=c.clan_id AND t.joined_at<? AND (t.ended_at IS NULL OR t.ended_at>=?)) AS active_members
          FROM clan_race_clans c WHERE c.created_at<? AND (c.deleted_at IS NULL OR c.deleted_at>=?)
        """, listOf(Date.valueOf(week.minusWeeks(4)), date, start, start, start, start, start)) {
            ClanRaceStrength(
                it.getString("clan_id"),
                it.getBigDecimal("prior_score").toBigIntegerExact(),
                it.getInt("active_members")
            )
        }
        update("INSERT INTO clan_race_pool_weeks VALUES(?,?)", date, start)
        val assignments = assignRacePools(strengths)
        for (clan in strengths) {
            update(
                "INSERT INTO clan_race_pool_members VALUES(?,?,?,?,?)",
                date, uuid(clan.clanId), assignments.getValue(clan.clanId),
                clan.priorScore.toBigDecimal(), clan.activeMembers
            )
        }
    }
    // Clans created after the boundary only enter starter pools. Existing assignments never move.
    val newcomers = rows("""SELECT clan_id FROM clan_race_clans c WHERE c.created_at>=? AND c.created_at<=? AND NOT EXISTS
        (SELECT 1 FROM clan_race_pool_members p WHERE p.week_start=? AND p.clan_id=c.clan_id) ORDER BY c.created_at,c.clan_id""",
        listOf(Timestamp(raceWeekMillis(week)), Timestamp(now), date)) { it.getString(1) }
    val sizes = rows(
        "SELECT pool_id,count(*) FROM clan_race_pool_members WHERE week_start=? AND pool_id LIKE 'starter-%' GROUP BY pool_id",
        listOf(date)
    ) {
        it.getString(1).substringAfterLast('-').toInt() to it.getInt(2)
    }.toMap().toMutableMap()
    for (clan in newcomers) {
        val pool = sizes.filterValues { it < 20 }.keys.maxOrNull() ?: ((sizes.keys.maxOrNull() ?: 0) + 1)
        update(
            "INSERT INTO clan_race_pool_members VALUES(?,?,?,?,?)",
            date, uuid(clan), "starter-$pool", BigInteger.ZERO.toBigDecimal(), 0
        )
        sizes[pool] = (sizes[pool] ?: 0) + 1
    }
}

internal fun JdbcClanRaceStore.racePool(week: LocalDate, clan: String?): String? = clan?.let {
    rows(
        "SELECT pool_id FROM clan_race_pool_members WHERE week_start=? AND clan_id=?",
        listOf(Date.valueOf(week), uuid(it))
    ) { row -> row.getString(1) }.singleOrNull()
}
