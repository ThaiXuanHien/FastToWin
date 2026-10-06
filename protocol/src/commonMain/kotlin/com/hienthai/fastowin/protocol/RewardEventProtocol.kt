package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable enum class RewardEventState { DRAFT, SCHEDULED, ACTIVE, ENDED, CANCELLED }
@Serializable data class RewardEventSourceSnapshot(
    val id:String,val version:Long,val nameVi:String,val nameEn:String,
    val startsAtEpochMillis:Long,val endsAtEpochMillis:Long,val goldMultiplier:Int,val xpMultiplier:Int
)
@Serializable data class MatchRewardEventSnapshot(
    val capturedAtEpochMillis:Long?=null,val goldMultiplier:Int=1,val xpMultiplier:Int=1,
    val sources:List<RewardEventSourceSnapshot> = emptyList()
)
@Serializable data class MatchRewardBreakdown(
    val baseGold:Int=0,val bonusGold:Int=0,val totalGold:Int=0,
    val baseXp:Int=0,val bonusXp:Int=0,val totalXp:Int=0,
    val sources:List<RewardEventSourceSnapshot> = emptyList()
)
/** Player-safe HTTP contract. Administrative identities, audit reasons and preview tokens stay server-side. */
@Serializable data class RewardEventPublicView(
    val id:String,val version:Long,val state:RewardEventState,
    val nameVi:String,val nameEn:String,val contentVi:String,val contentEn:String,val timezone:String,
    val startsAtEpochMillis:Long,val endsAtEpochMillis:Long,val goldMultiplier:Int,val xpMultiplier:Int,
    val matchTypes:Set<MatchType>
)
@Serializable data class RewardEventPublicPage(
    val items:List<RewardEventPublicView>,val serverNowEpochMillis:Long,val page:Int,val pageSize:Int,val total:Long
)
@Serializable data class RewardEventPublicDetail(val event:RewardEventPublicView,val serverNowEpochMillis:Long)
