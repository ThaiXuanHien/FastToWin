package com.hienthai.fastowin.server.clanrace

import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate

data class ClanRaceEligibleTenure(val tenureId:String,val userId:String,val clanId:String)
/** Membership immediately before cutoff; a departure exactly at cutoff belongs to the new week. */
fun JdbcClanRaceStore.eligibleAt(week:LocalDate,clan:String,cutoffMillis:Long):List<ClanRaceEligibleTenure> = rows("""
 SELECT t.id,t.user_id,t.clan_id FROM clan_race_tenures t
 WHERE t.clan_id=? AND t.joined_at<? AND (t.ended_at IS NULL OR t.ended_at>=?)
 AND EXISTS(SELECT 1 FROM clan_race_events e WHERE e.tenure_id=t.id AND e.week_start=? AND e.occurred_at<? AND e.score_units>0)
 ORDER BY t.user_id
""",listOf(uuid(clan),Timestamp(cutoffMillis),Timestamp(cutoffMillis),Date.valueOf(week),Timestamp(cutoffMillis))) { ClanRaceEligibleTenure(it.getString(1),it.getString(2),it.getString(3)) }
