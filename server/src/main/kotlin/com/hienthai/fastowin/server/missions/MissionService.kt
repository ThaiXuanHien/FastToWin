package com.hienthai.fastowin.server.missions

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.SQLException
import java.time.LocalDate
import java.util.UUID

data class MissionAdminContext(val token:String?,val csrfToken:String?,val origin:String?)
class MissionService(
    private val repository:PostgresMissionRepository?,private val admins:AdminService?=null,
    private val nowMillis:()->Long=System::currentTimeMillis
) {
    private suspend fun <T> storage(admin:Boolean=false,block:JdbcMissionStore.()->T):T=try {
        (repository?:unavailable()).transaction(admin,block)
    } catch(error:CancellationException) { throw error } catch(error:SQLException) { unavailable() }
    private fun JdbcMissionStore.authorize(context:MissionAdminContext,mutation:Boolean=false,owner:Boolean=false):AdminIdentity {
        val service=admins?:unavailable()
        if(mutation) service.requireOrigin(context.origin) else context.origin?.let(service::requireOrigin)
        return service.authorize(admin,context.token,context.csrfToken,mutation,owner).also { if(it.role==AdminRole.SUPPORT) forbidden() }
    }
    suspend fun list(context:MissionAdminContext,page:PageRequest=PageRequest(),query:String="",scope:MissionScope?=null,state:MissionCatalogState?=null):AdminPage<MissionAdminView> = storage(true) {
        authorize(context)
        if(query.length>128 || query.any(Char::isISOControl)) invalid()
        val all=catalog().filter { (scope==null || it.draft.scope==scope) && (state==null || it.state==state) && it.draft.code.contains(query,true) }
        page.result(all.drop(page.offset).take(page.pageSize),all.size.toLong())
    }
    suspend fun detail(context:MissionAdminContext,id:String):MissionAdminView=storage(true) { authorize(context);validId(id);find(id) }
    suspend fun create(context:MissionAdminContext,request:MissionDraftRequest):MissionAdminView=mutate(context,"CREATE",null,request.expectedVersion,request.reason,request.requestId,Json.encodeToString(request)) { _,actor ->
        if(request.expectedVersion!=0L) conflict()
        validate(request.draft)
        if(catalog().any { it.draft.code==request.draft.code }) conflict()
        if(actor.role!=AdminRole.OWNER && request.draft.reward!=MissionReward()) forbidden()
        MissionAdminView(UUID.randomUUID().toString(),1,MissionCatalogState.DRAFT,request.draft).also(::put)
    }
    suspend fun update(context:MissionAdminContext,id:String,request:MissionDraftRequest):MissionAdminView=mutate(context,"UPDATE",id,request.expectedVersion,request.reason,request.requestId,Json.encodeToString(request)) { before,actor ->
        val old=before?:missing();validate(request.draft)
        if(old.state==MissionCatalogState.ARCHIVED || old.draft.code!=request.draft.code) conflict()
        if(actor.role!=AdminRole.OWNER && old.draft.reward!=request.draft.reward) forbidden()
        old.copy(version=Math.addExact(old.version,1),state=MissionCatalogState.DRAFT,draft=request.draft).also(::put)
    }
    suspend fun preview(context:MissionAdminContext,id:String):MissionPreview=storage(true) {
        authorize(context,true);validId(id)
        val value=find(id);validate(value.draft)
        val from=effective(value.draft)
        val pool=selections(value.draft.scope,value.draft.period,from).filter { it.definitionId!=id }+MissionSelection(id,value.version,value.draft)
        val selected=if(value.draft.scope==MissionScope.CLAN) pool else selectMissions(value.draft.period,from,pool) { it.config.difficulty }
        val warnings=if(value.draft.scope==MissionScope.PERSONAL && selected.size<4) listOf("MISSION_POOL_SHORTAGE") else emptyList()
        audit(authorize(context).id,"PREVIEW",null,value,null,null)
        MissionPreview(value,from.toString(),value.draft.reward,selected.map { it.config.code },warnings,nowMillis())
    }
    suspend fun publish(context:MissionAdminContext,id:String,request:MissionMutationRequest):MissionAdminView=mutate(context,"PUBLISH",id,request.expectedVersion,request.reason,request.requestId,Json.encodeToString(request),true) { before,_ ->
        val old=before?:missing();if(old.state!=MissionCatalogState.DRAFT) conflict();validate(old.draft)
        val version=Math.addExact(old.version,1)
        old.copy(version=version,state=MissionCatalogState.PUBLISHED,publishedRevision=version,effectiveFrom=effective(old.draft).toString()).also(::publish)
    }
    suspend fun archive(context:MissionAdminContext,id:String,request:MissionMutationRequest):MissionAdminView=mutate(context,"ARCHIVE",id,request.expectedVersion,request.reason,request.requestId,Json.encodeToString(request),true) { before,_ ->
        val old=before?:missing();if(old.publishedRevision==null || old.state==MissionCatalogState.ARCHIVED) conflict()
        old.copy(version=Math.addExact(old.version,1),state=MissionCatalogState.ARCHIVED).also { archive(it,archiveCutoff(old.id,missionDate(nowMillis()))) }
    }
    suspend fun deleteDraft(context:MissionAdminContext,id:String,request:MissionMutationRequest):MissionAdminView=mutate(context,"DELETE",id,request.expectedVersion,request.reason,request.requestId,Json.encodeToString(request)) { before,_ ->
        val old=before?:missing();if(old.publishedRevision!=null || old.state!=MissionCatalogState.DRAFT) conflict();delete(old);old
    }
    private suspend fun mutate(context:MissionAdminContext,action:String,id:String?,version:Long,reason:String,request:String,payload:String,owner:Boolean=false,
        change:JdbcMissionStore.(MissionAdminView?,AdminIdentity)->MissionAdminView):MissionAdminView=storage(true) {
        val actor=authorize(context,true,owner)
        if(version<0 || reason.isBlank() || reason.length>500 || reason.any(Char::isISOControl)) invalid()
        validRequest(request);id?.let(::validId)
        val fingerprint=hashToken("$action:$id:$payload")
        replay(actor.id,request)?.let { if(it.first!=fingerprint) conflict();return@storage it.second }
        val before=id?.let(::find);if(before!=null && before.version!=version) throw AdminException(409,"STALE_VERSION","Mission changed; reload before editing.")
        val result=change(before,actor)
        audit(actor.id,action,before,result,request,reason)
        remember(actor.id,request,fingerprint,result);result
    }
    private fun JdbcMissionStore.audit(actor:String,action:String,before:MissionAdminView?,after:MissionAdminView?,request:String?,reason:String?) {
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(),actor,"MISSION_$action",after?.id?:before?.id,request,reason,before?.let { Json.encodeToString(it) },after?.let { Json.encodeToString(it) },nowMillis()))
    }
    suspend fun personal(player:String):MissionListResponse=storage { MissionListResponse(list(player,MissionScope.PERSONAL,nowMillis()),nowMillis()) }
    suspend fun clan(player:String):MissionListResponse=storage { MissionListResponse(list(player,MissionScope.CLAN,nowMillis()),nowMillis()) }
    suspend fun claim(player:String,id:String,request:MissionClaimRequest):MissionClaimResponse {
        validId(id);validRequest(request.requestId)
        return storage { claim(player,id,request.requestId) }
    }
    private fun effective(config:MissionConfig):LocalDate {
        val next=nextPeriod(config.period,missionDate(nowMillis()))
        val from=config.startsOn?.let(LocalDate::parse)?.coerceAtLeast(next)?:next
        val normalized=periodStart(config.period,from).let { if(it<from) nextPeriod(config.period,from) else it }
        if(config.endsOn!=null && normalized>=LocalDate.parse(config.endsOn)) invalid()
        return normalized
    }
    private fun validate(value:MissionConfig) {
        if(!value.code.matches(Regex("[A-Z][A-Z0-9_]{0,31}")) || value.code=="LEGACY_CLAN_QUEST" || value.target !in 1..1_000_000_000) invalid()
        if(listOf(value.reward.xp,value.reward.gold,value.reward.gems,value.reward.contributionPoints).any { it !in 0..1_000_000_000 }) invalid()
        if(value.scope==MissionScope.PERSONAL && value.reward.contributionPoints!=0) invalid()
        if(value.scope==MissionScope.CLAN && ((value.action==MissionAction.DONATE_GOLD && value.reward.gold>0) || (value.action==MissionAction.DONATE_GEMS && value.reward.gems>0)))
            throw AdminException(400,"CLAN_DONATION_REFUND_UNSUPPORTED","Clan donation missions must reward a different asset or XP; same-currency rewards require a personal mission.")
        if(value.action==MissionAction.DONATE_GOLD && value.reward.gold>value.target) invalid()
        if(value.action==MissionAction.DONATE_GEMS && value.reward.gems>value.target) invalid()
        if(value.action==MissionAction.ACCURACY) { if(value.accuracyPercent !in 1..100) invalid() } else if(value.accuracyPercent!=null) invalid()
        if(value.scope==MissionScope.CLAN && value.action==MissionAction.WIN_STREAK) {
            if(value.streakThreshold==null || value.streakThreshold !in 1..1_000_000_000) invalid()
        } else if(value.streakThreshold!=null) invalid()
        // Settlement records one global streak across eligible match types.
        if(value.matchType!=null && value.action in setOf(MissionAction.WIN_STREAK,MissionAction.CHECK_IN,MissionAction.DONATE_GOLD,MissionAction.DONATE_GEMS)) invalid()
        val start=value.startsOn?.let { date(it) };val end=value.endsOn?.let { date(it) }
        if(start!=null && end!=null && start>=end) invalid()
    }
    private fun date(value:String):LocalDate=try { LocalDate.parse(value).also { if(it.toString()!=value || it.year !in 1970..9999) invalid() } } catch(_:java.time.DateTimeException) { invalid() }
    private fun validId(value:String) { if(runCatching { UUID.fromString(value).toString()==value.lowercase() }.getOrDefault(false).not()) invalid() }
    private fun validRequest(value:String) { if(!value.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid() }
}
