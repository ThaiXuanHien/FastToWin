package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable enum class ClanRaceConfigState { DRAFT, PUBLISHED, INACTIVE }
@Serializable enum class ClanRaceState { INACTIVE, ACTIVE, CLOSED }
@Serializable data class ClanRaceReward(val xp:Int=0,val gold:Int=0,val gems:Int=0)
@Serializable data class ClanRaceConfig(val goldRate:String,val gemRate:String,val rewards:List<ClanRaceReward>)
@Serializable data class ClanRaceDraftRequest(val weekStart:String,val config:ClanRaceConfig,val expectedVersion:Long=0,val reason:String,val requestId:String)
@Serializable data class ClanRaceMutationRequest(val expectedVersion:Long,val reason:String,val requestId:String)
@Serializable data class ClanRaceCloneRequest(val weekStart:String,val expectedVersion:Long,val reason:String,val requestId:String)
@Serializable data class ClanRaceAdminView(val weekStart:String,val version:Long,val state:ClanRaceConfigState,val config:ClanRaceConfig,val publishedRevision:Long?=null)
@Serializable data class ClanRaceBudget(val xp:String,val gold:String,val gems:String)
@Serializable data class ClanRacePreview(val race:ClanRaceAdminView,val startEpochMillis:Long,val endEpochMillis:Long,val gold100Points:String,val gem1Points:String,val maximumRecipientsPerRank:Int=50,val maximumRecipients:Int=150,val maximumBudget:ClanRaceBudget,val serverNowEpochMillis:Long)
@Serializable data class ClanRaceCurrent(val weekStart:String,val startEpochMillis:Long,val endEpochMillis:Long,val state:ClanRaceState,val config:ClanRaceConfig?=null,val revision:Long?=null,val serverNowEpochMillis:Long)
@Serializable data class ClanRaceStanding(val rank:Int,val clanId:String,val name:String,val logoId:String?,val level:Int,val scoreUnits:String,val points:String,val reachedAtEpochMillis:Long)
@Serializable data class ClanRaceStandings(val weekStart:String,val items:List<ClanRaceStanding>,val page:Int,val pageSize:Int,val total:Int,val ownClan:ClanRaceStanding?=null,val serverNowEpochMillis:Long)
@Serializable data class ClanRaceContribution(val userId:String,val displayName:String?,val tenureId:String,val currentMember:Boolean,val eligible:Boolean,val donationUnits:String,val missionUnits:String,val scoreUnits:String,val points:String)
@Serializable data class ClanRaceContributions(val weekStart:String,val clanId:String?,val items:List<ClanRaceContribution>,val serverNowEpochMillis:Long)
@Serializable data class ClanRaceHistory(val items:List<ClanRaceCurrent>,val page:Int,val pageSize:Int,val total:Int,val serverNowEpochMillis:Long)
@Serializable data class ClanRaceApiError(val code:String,val message:String)
