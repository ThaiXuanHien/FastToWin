package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

/** One transaction freezes the complete result, recipient rights and delivery outbox. */
class ClanRaceSettlement(private val repository:PostgresClanRaceRepository,private val nowMillis:()->Long=System::currentTimeMillis) {
    suspend fun settleDueWeeks(limit:Int=10):Int {
        require(limit in 1..100)
        val weeks=repository.transaction {
            val week=raceWeekStart(nowMillis())
            rows("""SELECT r.week_start FROM clan_race_revisions r JOIN clan_race_configs c USING(week_start)
                LEFT JOIN clan_race_settlements s USING(week_start)
                WHERE c.state='PUBLISHED' AND r.week_start<? AND s.week_start IS NULL ORDER BY r.week_start LIMIT ?""",
                listOf(Date.valueOf(week),limit)) { it.getDate(1).toLocalDate() }
        }
        var settled=0
        for(week in weeks) {
            try {
                if(repository.transaction { settle(week,nowMillis()) }) settled++
            } catch(error:CancellationException) { throw error }
            catch(_:Exception) {
                repository.transaction {
                    if(settlementResult(week)==null) settlementCheckpoint(week,"FAILED","SETTLEMENT_FAILED",nowMillis())
                }
            }
        }
        return settled
    }
}

private fun JdbcClanRaceStore.settle(week:LocalDate,now:Long):Boolean {
    val cutoff=raceWeekMillis(week.plusWeeks(1))
    if(now<cutoff || settlementResult(week)!=null) return false
    val config=published(week)?:return false
    setTime(now)
    val standings=standingsAt(week,cutoff)
    val winners=standings.take(3).map { standing ->
        ClanRaceWinner(standing,config.rewards[standing.rank-1],eligibleAt(week,standing.clanId,cutoff).size)
    }
    val revision=rows("SELECT revision FROM clan_race_revisions WHERE week_start=?",listOf(Date.valueOf(week))) { it.getLong(1) }.single()
    val result=ClanRaceSettlementResult(week.toString(),revision,cutoff,now,standings,winners)
    update("INSERT INTO clan_race_settlements VALUES(?,?,?)",Date.valueOf(week),Timestamp(now),Json.encodeToString(result))
    for(winner in winners) for(recipient in eligibleAt(week,winner.standing.clanId,cutoff)) {
        val id=UUID.nameUUIDFromBytes("clan-weekly-race:$week:${recipient.clanId}:${recipient.userId}".toByteArray()).toString()
        val entitlement=ClanRaceEntitlement(id,recipient.userId,recipient.tenureId,week.toString(),cutoff,winner.standing,winner.reward)
        update("INSERT INTO clan_race_entitlements VALUES(?,?,?,?,?,?)",uuid(id),Date.valueOf(week),uuid(recipient.clanId),uuid(recipient.userId),uuid(recipient.tenureId),Json.encodeToString(entitlement))
        update("INSERT INTO clan_race_outbox(entitlement_id) VALUES(?)",uuid(id))
    }
    settlementCheckpoint(week,"SETTLED",null,now)
    return true
}

private fun JdbcClanRaceStore.settlementCheckpoint(week:LocalDate,state:String,error:String?,now:Long) {
    update("""INSERT INTO clan_race_settlement_work VALUES(?,?,1,?,?) ON CONFLICT(week_start)
        DO UPDATE SET state=excluded.state,attempts=clan_race_settlement_work.attempts+1,last_error_code=excluded.last_error_code,updated_at=excluded.updated_at""",
        Date.valueOf(week),state,error,Timestamp(now))
}
