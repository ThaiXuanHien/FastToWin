package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.gifts.*
import com.hienthai.fastowin.server.legacyFallback
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable internal data class ClanRaceEntitlement(
    val id:String,val userId:String,val tenureId:String,val weekStart:String,val cutoffEpochMillis:Long,
    val standing:ClanRaceStanding,val reward:ClanRaceReward
)

/** Each recipient has a stable system campaign, so its lifetime starts at its own delivery commit. */
internal fun ClanRaceEntitlement.gift(available:Long):GiftCampaign {
    val expires=Math.addExact(available,7L*24*60*60*1000)
    val context=ClanRaceMailboxContext(id,weekStart,raceWeekMillis(LocalDate.parse(weekStart)),cutoffEpochMillis,
        standing.clanId,standing.name,standing.rank,standing.scoreUnits,standing.points,true,cutoffEpochMillis,
        available,expires,reward,"CLAN_WEEKLY_RACE")
    val draft=GiftDraftRequest(
        titleVi=legacyFallback("Quà đua top bang tuần $weekStart"),titleEn=legacyFallback("Weekly clan race reward: $weekStart"),
        bodyVi=legacyFallback("Bang ${standing.name} đạt hạng ${standing.rank}. Bạn đã đóng góp trong lần gia nhập còn hiệu lực tại thời điểm chốt tuần."),
        bodyEn=legacyFallback("${standing.name} finished rank ${standing.rank}. You contributed during your membership active at the weekly cutoff."),
        reason="CLAN_WEEKLY_RACE",gold=reward.gold,xp=reward.xp,gems=reward.gems,
        audience=GiftAudience.USER_IDS,userIds=listOf(userId),availableAtEpochMillis=available,expiresAtEpochMillis=expires,requestId=id)
    return GiftCampaign(id,1,CampaignState.PUBLISHED,draft,available,publishedAtEpochMillis=available,clanRace=context)
}
