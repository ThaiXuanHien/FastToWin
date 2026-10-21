package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*

/** Copies publish under the same mutex as admin authority, so a demotion cannot race an action. */
class InMemoryModerationRepository(
    private val admins: InMemoryAdminRepository = InMemoryAdminRepository(),
    private val targetLookup: ((UgcTargetType,String)->UgcTarget?)? = null
) : ModerationRepository {
    private data class State(
        val identities: MutableSet<String> = linkedSetOf(),
        val actions: MutableList<ModerationActionRecord> = mutableListOf(),
        val terms: MutableMap<String,String> = linkedMapOf(),
        val targets: MutableMap<Pair<UgcTargetType,String>,UgcTarget> = linkedMapOf(),
        val reports: MutableMap<String,ModerationReport> = linkedMapOf(),
        val replays: MutableMap<Pair<String,String>,ModerationReplay> = linkedMapOf(),
        val rates: MutableList<Pair<String,Long>> = mutableListOf(),
        val suppressions: MutableMap<Triple<UgcTargetType,String,String>,ContentSuppression> = linkedMapOf(),
        val restrictions: MutableMap<String,ModerationRestrictionView> = linkedMapOf()
    ) {
        fun snapshot()=State(identities.toMutableSet(),actions.toMutableList(),terms.toMutableMap(),targets.toMutableMap(),reports.toMutableMap(),replays.toMutableMap(),
            rates.toMutableList(),suppressions.toMutableMap(),restrictions.toMutableMap())
    }
    private var state=State()
    override suspend fun <T> transaction(block: ModerationTransaction.()->T): T {
        var published: State? = null
        return admins.coordinatedTransaction(publish={ state=published!! }) {
            val next=state.snapshot()
            val authority=this
            val result=object:ModerationTransaction {
                override val admin:AdminTransaction=authority
                override fun registerPlayer(playerId:String) {next.identities.add(playerId)}
                override fun acceptedVersion(playerId:String)=next.terms[playerId]
                override fun acceptTerms(playerId:String,version:String,nowMillis:Long) {next.terms[playerId]=version}
                override fun playerExists(playerId:String)=playerId in next.identities || runCatching {admin.user(playerId)?.status?.let {it!="DELETED"}==true}.getOrDefault(false)
                override fun target(type:UgcTargetType,id:String)=if(targetLookup!=null) targetLookup.invoke(type,id) else next.targets[type to id]
                override fun putTarget(target:UgcTarget) {next.targets[target.type to target.id]=target}
                override fun removeTarget(type:UgcTargetType,id:String) {next.targets.remove(type to id)}
                override fun publishedTargets()=next.targets.keys.toList()
                override fun appendAction(value:ModerationActionRecord) {next.actions.add(value)}
                override fun lastActionReason(reportId:String)=next.actions.lastOrNull {it.reportId==reportId}?.reason
                override fun report(id:String)=next.reports[id]
                override fun openReport(reporterId:String,type:UgcTargetType,targetId:String)=next.reports.values.firstOrNull {
                    it.reporterId==reporterId && it.targetType==type && it.targetId==targetId && !it.status.closed()
                }
                override fun putReport(value:ModerationReport) {next.reports[value.id]=value}
                override fun reports(status:ModerationStatus?,limit:Int,offset:Int)=next.reports.values.filter {status==null || it.status==status}
                    .sortedWith(compareByDescending<ModerationReport>{it.createdAtEpochMillis}.thenBy{it.id}).drop(offset).take(limit)
                override fun reportCount(status:ModerationStatus?)=next.reports.values.count {status==null || it.status==status}.toLong()
                override fun replay(actorId:String,requestId:String)=next.replays[actorId to requestId]
                override fun putReplay(value:ModerationReplay) {next.replays[value.actorId to value.requestId]=value}
                override fun rateCount(playerId:String,since:Long)=next.rates.count {it.first==playerId && it.second>since}
                override fun addRateEvent(playerId:String,nowMillis:Long) {next.rates.add(playerId to nowMillis)}
                override fun suppression(type:UgcTargetType,targetId:String,revision:String?)=next.suppressions.values.any {
                    it.targetType==type && it.targetId==targetId && (revision==null || it.revision==revision)
                }
                override fun suppressions()=next.suppressions.values.toList()
                override fun putSuppression(value:ContentSuppression) {next.suppressions[Triple(value.targetType,value.targetId,value.revision)]=value}
                override fun restriction(userId:String)=next.restrictions[userId] ?: ModerationRestrictionView(userId,blocked=runCatching {admin.user(userId)?.status=="BLOCKED"}.getOrDefault(false))
                override fun putRestriction(value:ModerationRestrictionView,nowMillis:Long) {next.restrictions[value.userId]=value}
                override fun erasePlayer(playerId:String) {
                    next.identities.remove(playerId);next.terms.remove(playerId);next.rates.removeIf {it.first==playerId};next.restrictions.remove(playerId)
                    val affected=next.reports.values.filter {it.reporterId==playerId || it.targetOwnerId==playerId || it.targetId==playerId}.map {it.id}.toSet()
                    next.actions.removeIf {it.reportId in affected || it.subjectUserId==playerId}
                    next.reports.replaceAll {_,value -> if(value.id !in affected) value else value.copy(
                        reporterId=if(value.reporterId==playerId) null else value.reporterId,
                        targetId=if(value.targetOwnerId==playerId || value.targetId==playerId) null else value.targetId,
                        targetOwnerId=if(value.targetOwnerId==playerId) null else value.targetOwnerId,
                        note="",evidenceText="",targetRevision=null,imageReference=null)
                    }
                    next.replays.entries.removeIf {it.key.first==playerId || it.value.reportId in affected || it.value.responseJson.contains(playerId)}
                    next.suppressions.entries.removeIf {it.value.ownerId==playerId || it.value.targetId==playerId}
                    next.targets.entries.removeIf {it.value.ownerId==playerId || it.value.id==playerId}
                }
                override fun cleanup(closedBefore:Long,nowMillis:Long):Int {
                    val expired=next.reports.values.filter {it.closedAtEpochMillis?.let {closed->closed<closedBefore}==true}.map {it.id}.toSet()
                    next.actions.removeIf {it.reportId in expired || it.expiresAtEpochMillis?.let {expiry->expiry<nowMillis}==true}
                    next.reports.keys.removeAll(expired)
                    next.replays.entries.removeIf {(it.value.reportId==null && it.value.expiresAtEpochMillis<nowMillis) || it.value.reportId in expired}
                    next.rates.removeIf {it.second<=nowMillis-86_400_000}
                    return expired.size
                }
            }.block()
            // No suspension or fallible work follows this publication; outer coordinatedTransaction publishes admin copy next.
            published=next
            result
        }
    }
}
