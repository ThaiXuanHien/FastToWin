package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.ClanDonationCurrency
import com.hienthai.fastowin.server.admin.AdminException
import java.math.BigInteger
import java.sql.Date
import java.sql.Timestamp

fun JdbcClanRaceStore.currentTenure(player:String,clan:String):String?=rows("SELECT id FROM clan_race_tenures WHERE user_id=? AND clan_id=? AND ended_at IS NULL",listOf(uuid(player),uuid(clan))) { it.getString(1) }.singleOrNull()
fun JdbcClanRaceStore.recordDonation(player:String,clan:String,source:String,currency:ClanDonationCurrency,amount:Long,now:Long) {
    val config=published(raceWeekStart(now))?:return
    val tenure=currentTenure(player,clan)?:return
    val rate=if(currency==ClanDonationCurrency.GOLD) config.goldRate else config.gemRate
    recordScore("DONATION",source,player,clan,tenure,raceDonationUnits(amount,parseRaceRateUnits(rate)),now)
}
fun JdbcClanRaceStore.recordMission(player:String,clan:String,instance:String,points:Int,now:Long) {
    if(points<=0 || published(raceWeekStart(now))==null) return
    val tenure=currentTenure(player,clan)?:return
    val proof=rows("""SELECT 1 FROM mission_progress_events p JOIN clan_race_tenures t ON t.id=p.race_tenure_id
      WHERE p.instance_id=? AND p.user_id=? AND p.race_tenure_id=? AND p.amount>0 AND p.occurred_at>=t.joined_at AND p.occurred_at<=? LIMIT 1""",listOf(uuid(instance),uuid(player),uuid(tenure),Timestamp(now))) { true }.isNotEmpty()
    if(proof) recordScore("MISSION",instance,player,clan,tenure,raceDonationUnits(points.toLong(),RACE_UNITS_PER_POINT),now)
}
private fun JdbcClanRaceStore.recordScore(kind:String,source:String,player:String,clan:String,tenure:String,units:BigInteger,now:Long) {
    if(rows("SELECT 1 FROM clan_race_events WHERE source_kind=? AND source_id=? AND user_id=?",listOf(kind,source,uuid(player))) { true }.isNotEmpty()) return
    val week=raceWeekStart(now)
    try {
        val total=rows("SELECT coalesce(sum(score_units),0) FROM clan_race_events WHERE week_start=? AND clan_id=?",listOf(Date.valueOf(week),uuid(clan))) { it.getBigDecimal(1).toBigIntegerExact() }.single()
        checkedRaceUnits(total+units)
    } catch(_:IllegalArgumentException) { throw AdminException(409,"RACE_SCORE_OVERFLOW","Race score exceeds 38 decimal digits.") }
    update("INSERT INTO clan_race_events(week_start,source_kind,source_id,user_id,clan_id,tenure_id,occurred_at,score_units) VALUES(?,?,?,?,?,?,?,?)",Date.valueOf(week),kind,source,uuid(player),uuid(clan),uuid(tenure),Timestamp(now),units.toBigDecimal())
}
