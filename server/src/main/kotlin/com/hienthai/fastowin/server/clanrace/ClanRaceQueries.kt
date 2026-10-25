package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import java.math.BigInteger
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate

/** Historical identity and existence are read at cutoff, independent of live clan/member rows. */
fun JdbcClanRaceStore.standingsAt(week:LocalDate,cutoffMillis:Long):List<ClanRaceStanding> = rows("""
 WITH totals AS (
   SELECT clan_id,sum(score_units) AS score,max(occurred_at) AS reached FROM clan_race_events
   WHERE week_start=? AND occurred_at<? GROUP BY clan_id
 ), ranked AS (
   SELECT row_number() OVER(ORDER BY t.score DESC,t.reached ASC,t.clan_id ASC) AS rank,t.*,v.name,v.logo_id,v.level
   FROM totals t JOIN clan_race_clans c ON c.clan_id=t.clan_id
   JOIN clan_race_clan_versions v ON v.clan_id=t.clan_id AND v.valid_from<? AND (v.valid_until IS NULL OR v.valid_until>=?)
   WHERE t.score>0 AND c.created_at<? AND (c.deleted_at IS NULL OR c.deleted_at>=?)
 ) SELECT * FROM ranked ORDER BY rank
""",listOf(Date.valueOf(week),Timestamp(cutoffMillis),Timestamp(cutoffMillis),Timestamp(cutoffMillis),Timestamp(cutoffMillis),Timestamp(cutoffMillis))) {
    val units=checkedRaceUnits(it.getBigDecimal("score").toBigIntegerExact())
    ClanRaceStanding(it.getInt("rank"),it.getString("clan_id"),it.getString("name"),it.getString("logo_id"),it.getInt("level"),units.toString(),racePoints(units),it.getTimestamp("reached").time)
}
internal fun JdbcClanRaceStore.ownClan(player:String):String?=rows("SELECT clan_id FROM clan_members WHERE user_id=?",listOf(uuid(player))) { it.getString(1) }.singleOrNull()
internal fun JdbcClanRaceStore.contributions(week:LocalDate,clan:String):List<ClanRaceContribution> = rows("""
 SELECT t.id,t.user_id,t.ended_at,p.display_name,
 coalesce(sum(e.score_units) FILTER(WHERE e.source_kind='DONATION'),0) AS donation,
 coalesce(sum(e.score_units) FILTER(WHERE e.source_kind='MISSION'),0) AS mission
 FROM clan_race_tenures t LEFT JOIN clan_race_events e ON e.tenure_id=t.id AND e.week_start=?
 LEFT JOIN profiles p ON p.user_id=t.user_id
 WHERE t.clan_id=? GROUP BY t.id,p.display_name HAVING t.ended_at IS NULL OR count(e.id)>0 ORDER BY t.joined_at,t.id
""",listOf(Date.valueOf(week),uuid(clan))) {
    val donation=it.getBigDecimal("donation").toBigIntegerExact();val mission=it.getBigDecimal("mission").toBigIntegerExact();val total=checkedRaceUnits(donation+mission)
    val current=it.getTimestamp("ended_at")==null
    ClanRaceContribution(it.getString("user_id"),it.getString("display_name"),it.getString("id"),current,current && total>BigInteger.ZERO,donation.toString(),mission.toString(),total.toString(),racePoints(total))
}
