package com.hienthai.fastowin.server.missions

import com.hienthai.fastowin.protocol.*
import java.time.*
import java.time.temporal.TemporalAdjusters

internal val MISSION_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")
internal fun missionDate(now:Long):LocalDate=Instant.ofEpochMilli(now).atZone(MISSION_ZONE).toLocalDate()
internal fun periodStart(period:MissionCadence,date:LocalDate):LocalDate=if(period==MissionCadence.DAILY) date else date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
internal fun nextPeriod(period:MissionCadence,date:LocalDate):LocalDate=periodStart(period,date).let { if(period==MissionCadence.DAILY) it.plusDays(1) else it.plusWeeks(1) }

/** Facts supplied only by committed server operations, never by a player request. */
data class MissionProgressEvent(
    val sourceId:String, val playerId:String, val occurredAtMillis:Long,
    val matchType:MatchType?=null, val played:Boolean=false, val won:Boolean=false,
    val correct:Int=0, val wrong:Int=0, val perfectWin:Boolean=false, val winStreak:Int=0,
    val checkIn:Boolean=false, val donatedGold:Int=0, val donatedGems:Int=0,
    val intentionalLeave:Boolean=false
)
internal fun MissionConfig.increment(event:MissionProgressEvent):Int {
    if(event.intentionalLeave || (matchType!=null && event.matchType!=matchType)) return 0
    return when(action) {
        MissionAction.PLAY -> if(event.played) 1 else 0
        MissionAction.WIN -> if(event.won) 1 else 0
        MissionAction.CORRECT -> event.correct
        MissionAction.ACCURACY -> if(event.played && event.correct.toLong()+event.wrong>0 && event.correct.toLong()*100 >= (event.correct.toLong()+event.wrong)*(accuracyPercent?:90)) 1 else 0
        MissionAction.PERFECT_WIN -> if(event.perfectWin) 1 else 0
        MissionAction.WIN_STREAK -> if(event.winStreak>=(streakThreshold?:target)) if(scope==MissionScope.CLAN) 1 else target else 0
        MissionAction.CHECK_IN -> if(event.checkIn) 1 else 0
        MissionAction.DONATE_GOLD -> event.donatedGold
        MissionAction.DONATE_GEMS -> event.donatedGems
    }
}
internal fun <T> selectMissions(period:MissionCadence,date:LocalDate,values:List<T>,difficulty:(T)->MissionDifficulty):List<T> {
    val seed=periodStart(period,date).toEpochDay()
    fun rotate(pool:List<T>,count:Int,offset:Long):List<T> {
        if(pool.isEmpty()) return emptyList()
        val start=Math.floorMod(seed+offset,pool.size.toLong()).toInt()
        return List(minOf(count,pool.size)) { pool[(start+it)%pool.size] }
    }
    return if(period==MissionCadence.DAILY)
        rotate(values.filter { difficulty(it)==MissionDifficulty.EASY },1,0)+
            rotate(values.filter { difficulty(it)==MissionDifficulty.NORMAL },2,11)+
            rotate(values.filter { difficulty(it) in setOf(MissionDifficulty.HARD,MissionDifficulty.ELITE) },1,23)
    else rotate(values.filter { difficulty(it)==MissionDifficulty.NORMAL },1,0)+
        rotate(values.filter { difficulty(it)==MissionDifficulty.HARD },2,11)+
        rotate(values.filter { difficulty(it)==MissionDifficulty.ELITE },1,23)
}
