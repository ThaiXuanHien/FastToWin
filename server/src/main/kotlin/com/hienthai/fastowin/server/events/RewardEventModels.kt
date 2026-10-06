package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.protocol.*
import kotlinx.serialization.Serializable

data class RewardEventAdminContext(val token:String?,val csrfToken:String?=null,val origin:String?=null)
@Serializable data class RewardEventDraft(
    val nameVi:String,val nameEn:String,val contentVi:String,val contentEn:String,val timezone:String,
    val startsAtEpochMillis:Long,val endsAtEpochMillis:Long,val goldMultiplier:Int,val xpMultiplier:Int,
    val matchTypes:Set<MatchType>
)
@Serializable data class RewardEventDraftRequest(val expectedVersion:Long,val requestId:String,val reason:String,val draft:RewardEventDraft)
@Serializable data class RewardEventMutationRequest(val expectedVersion:Long,val requestId:String,val reason:String,val previewToken:String?=null)
@Serializable data class RewardEventAdminView(
    val id:String,val version:Long,val state:RewardEventState,val draft:RewardEventDraft,
    val actorId:String,val reason:String,val changedAtEpochMillis:Long,val publishedAtEpochMillis:Long?=null
)
@Serializable data class RewardEventPreview(
    val event:RewardEventAdminView,val previewToken:String,val overlaps:List<RewardEventPublicView>,
    val policy:String="MAX_PER_ASSET",val serverNowEpochMillis:Long
)
/** Stored lifecycle only; ACTIVE and ENDED are derived, never stale cron-maintained flags. */
@Serializable internal data class RewardEventRecord(
    val id:String,val version:Long,val draft:RewardEventDraft,val actorId:String,val reason:String,
    val changedAtEpochMillis:Long,val publishedAtEpochMillis:Long?=null,val cancelled:Boolean=false
) {
    fun state(now:Long):RewardEventState=when {
        cancelled -> RewardEventState.CANCELLED
        publishedAtEpochMillis==null -> RewardEventState.DRAFT
        now<draft.startsAtEpochMillis -> RewardEventState.SCHEDULED
        now<draft.endsAtEpochMillis -> RewardEventState.ACTIVE
        else -> RewardEventState.ENDED
    }
    fun adminView(now:Long)=RewardEventAdminView(id,version,state(now),draft,actorId,reason,changedAtEpochMillis,publishedAtEpochMillis)
    fun publicView(now:Long)=with(draft) { RewardEventPublicView(id,version,state(now),nameVi,nameEn,contentVi,contentEn,timezone,
        startsAtEpochMillis,endsAtEpochMillis,goldMultiplier,xpMultiplier,matchTypes.toSet()) }
}
internal data class RewardEventRequestRecord(val fingerprint:String,val result:RewardEventAdminView)
