package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.util.UUID

class RewardEventService(
    val repository:RewardEventRepository,private val admins:AdminService?=null,
    private val nowMillis:()->Long=System::currentTimeMillis
) {
    fun requireOrigin(origin:String?) { (admins?:unavailable()).requireOrigin(origin) }
    private fun RewardEventTransaction.authorize(context:RewardEventAdminContext,mutation:Boolean=false,ownerOnly:Boolean=false):AdminIdentity {
        val service=admins?:unavailable()
        context.origin?.let(service::requireOrigin)
        if(mutation) service.requireOrigin(context.origin)
        return service.authorize(admin,context.token,context.csrfToken,mutation,ownerOnly).also { if(it.role==AdminRole.SUPPORT) forbidden() }
    }
    suspend fun list(context:RewardEventAdminContext,page:PageRequest=PageRequest(),query:String="",state:RewardEventState?=null):AdminPage<RewardEventAdminView> = storage {
        repository.transaction {
            val actor=authorize(context); validateQuery(query); val now=now()
            val all=records().map { it.record().adminView(now) }.filter { (state==null || it.state==state) &&
                (query.isEmpty() || it.draft.nameVi.contains(query,true) || it.draft.nameEn.contains(query,true)) }
                .sortedWith(compareByDescending<RewardEventAdminView> { it.changedAtEpochMillis }.thenBy { it.id })
            audit(actor.id,"EVENT_LIST",now=now)
            page.result(all.drop(page.offset).take(page.pageSize),all.size.toLong())
        }
    }
    suspend fun detail(context:RewardEventAdminContext,id:String):RewardEventAdminView=storage {
        repository.transaction { val actor=authorize(context); val now=now(); val value=find(id).adminView(now); audit(actor.id,"EVENT_DETAIL",after=value,now=now); value }
    }
    suspend fun create(context:RewardEventAdminContext,request:RewardEventDraftRequest):RewardEventAdminView = mutate(
        context,"CREATE",null,request.expectedVersion,request.requestId,request.reason,Json.encodeToString(request)) { _,actor,now ->
        if(request.expectedVersion!=0L) conflict(); validate(request.draft)
        RewardEventRecord(UUID.randomUUID().toString(),1,canonical(request.draft),actor.id,request.reason,now,creatorId=actor.id)
    }
    suspend fun update(context:RewardEventAdminContext,id:String,request:RewardEventDraftRequest):RewardEventAdminView = mutate(
        context,"UPDATE",id,request.expectedVersion,request.requestId,request.reason,Json.encodeToString(request)) { current,actor,now ->
        val before=current?:missing(); if(before.state(now)!=RewardEventState.DRAFT) conflict(); validate(request.draft)
        before.copy(version=nextVersion(before.version),draft=canonical(request.draft),actorId=actor.id,reason=request.reason,changedAtEpochMillis=now)
    }
    suspend fun preview(context:RewardEventAdminContext,id:String):RewardEventPreview=storage {
        repository.transaction {
            val actor=authorize(context,mutation=true); val now=now(); val value=find(id)
            val overlaps=records().map { it.record() }.filter { other -> other.id!=id && other.publishedAtEpochMillis!=null && !other.cancelled &&
                other.draft.startsAtEpochMillis<value.draft.endsAtEpochMillis && other.draft.endsAtEpochMillis>value.draft.startsAtEpochMillis &&
                other.draft.matchTypes.any { it in value.draft.matchTypes } }.sortedBy { it.id }.map { it.publicView(now) }
            audit(actor.id,"EVENT_PREVIEW",after=value.adminView(now),now=now)
            RewardEventPreview(value.adminView(now),previewToken(value),overlaps,serverNowEpochMillis=now)
        }
    }
    suspend fun schedule(context:RewardEventAdminContext,id:String,request:RewardEventMutationRequest):RewardEventAdminView = mutate(
        context,"SCHEDULE",id,request.expectedVersion,request.requestId,request.reason,Json.encodeToString(request),ownerOnly=true) { current,actor,now ->
        val before=current?:missing()
        if(before.state(now)!=RewardEventState.DRAFT || before.draft.endsAtEpochMillis<=now || request.previewToken!=previewToken(before)) conflict()
        before.copy(version=nextVersion(before.version),actorId=actor.id,reason=request.reason,changedAtEpochMillis=now,publishedAtEpochMillis=now,publisherId=actor.id)
    }
    suspend fun cancel(context:RewardEventAdminContext,id:String,request:RewardEventMutationRequest):RewardEventAdminView = mutate(
        context,"CANCEL",id,request.expectedVersion,request.requestId,request.reason,Json.encodeToString(request),ownerOnly=true) { current,actor,now ->
        val before=current?:missing()
        if(before.state(now) in setOf(RewardEventState.ENDED,RewardEventState.CANCELLED)) conflict()
        before.copy(version=nextVersion(before.version),actorId=actor.id,reason=request.reason,changedAtEpochMillis=now,cancelled=true)
    }
    suspend fun publicList(page:PageRequest=PageRequest()):RewardEventPublicPage=storage {
        repository.publicRead {
            val now=now(); val all=records().map { it.record() }.filter { it.publishedAtEpochMillis!=null &&
                it.state(now) in setOf(RewardEventState.SCHEDULED,RewardEventState.ACTIVE) }
                .sortedWith(compareBy<RewardEventRecord> { it.draft.startsAtEpochMillis }.thenBy { it.id })
            RewardEventPublicPage(all.drop(page.offset).take(page.pageSize).map { it.publicView(now) },now,page.page,page.pageSize,all.size.toLong())
        }
    }
    suspend fun publicDetail(id:String):RewardEventPublicDetail=storage {
        repository.publicRead { val value=find(id); if(value.publishedAtEpochMillis==null) missing(); val now=now(); RewardEventPublicDetail(value.publicView(now),now) }
    }
    private suspend fun mutate(context:RewardEventAdminContext,action:String,id:String?,version:Long,requestId:String,reason:String,payload:String,
        ownerOnly:Boolean=false,change:(RewardEventRecord?,AdminIdentity,Long)->RewardEventRecord):RewardEventAdminView=storage {
        repository.transaction {
            // Repository acquires global authorization then exclusive catalog gate; current role/session checked only AFTER both waits.
            val actor=authorize(context,true,ownerOnly)
            validateMutation(version,requestId,reason)
            val fingerprint=hashToken("$action:${id.orEmpty()}:$payload")
            replay(actor.id,requestId)?.let { if(it.first!=fingerprint) conflict(); return@transaction it.second }
            val before=id?.let { find(it) }; if(before!=null && before.version!=version) conflict()
            val now=now(); val result=change(before,actor,now).adminView(now)
            put(result); audit(actor.id,"EVENT_$action",before?.adminView(now),result,requestId,reason,now)
            remember(actor.id,requestId,fingerprint,result); result
        }
    }
    private fun RewardEventRead.find(id:String):RewardEventRecord {
        if(runCatching { UUID.fromString(id).toString()==id.lowercase() }.getOrDefault(false).not()) invalid()
        return records().firstOrNull { it.id==id }?.record()?:missing()
    }
    private fun RewardEventTransaction.audit(actor:String,action:String,before:RewardEventAdminView?=null,after:RewardEventAdminView?=null,
        requestId:String?=null,reason:String?=null,now:Long) {
        fun summary(value:RewardEventAdminView?)=value?.let { "state=${it.state};version=${it.version}" }
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(),actor,action,after?.id,requestId,reason,summary(before),summary(after),now))
    }
    private fun canonical(value:RewardEventDraft)=value.copy(matchTypes=value.matchTypes.sortedBy { it.name }.toSet())
    private fun previewToken(value:RewardEventRecord)=hashToken("reward-event-preview:${value.id}:${value.version}:${Json.encodeToString(canonical(value.draft))}")
    private fun nextVersion(value:Long)=try { Math.addExact(value,1) } catch(_:ArithmeticException) { conflict() }
    private fun now()=nowMillis().also { if(it !in 0..MAX_TIMESTAMP) unavailable() }
    private fun validateQuery(query:String) { if(query.length>128 || query.any(Char::isISOControl)) invalid() }
    private fun validateMutation(version:Long,requestId:String,reason:String) {
        if(version<0 || reason.isBlank() || reason.length>500 || reason.any(Char::isISOControl) || !requestId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid()
    }
    private fun validate(value:RewardEventDraft) {
        fun text(value:String,max:Int) {
            if(value.isBlank() || value.codePointCount(0,value.length)>max || value.any { it.isISOControl() && it !in "\n\r\t" }) invalid()
        }
        text(value.nameVi,200); text(value.nameEn,200); text(value.contentVi,2000); text(value.contentEn,2000)
        if(value.timezone !in ZoneId.getAvailableZoneIds() || value.startsAtEpochMillis !in 0..MAX_TIMESTAMP || value.endsAtEpochMillis !in 0..MAX_TIMESTAMP ||
            value.startsAtEpochMillis>=value.endsAtEpochMillis || value.goldMultiplier !in 1..2 || value.xpMultiplier !in 1..2 || value.matchTypes.isEmpty()) invalid()
    }
    private suspend fun <T> storage(block:suspend ()->T):T=try { block() }
        catch(error:CancellationException) { throw error }
        catch(error:AdminException) { throw error }
        catch(_:Exception) { unavailable() }
    companion object { const val MAX_TIMESTAMP=253402300799999L }
}
