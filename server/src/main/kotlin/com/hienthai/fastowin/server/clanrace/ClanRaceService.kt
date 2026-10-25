package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.sql.SQLException
import java.time.*
import java.util.UUID

data class ClanRaceAdminContext(val token:String?,val csrfToken:String?,val origin:String?)
class ClanRaceService(private val repository:PostgresClanRaceRepository?,private val admins:AdminService?=null,private val nowMillis:()->Long=System::currentTimeMillis) {
    private suspend fun <T> storage(admin:Boolean=false,block:JdbcClanRaceStore.()->T):T=try { (repository?:unavailable()).transaction(admin,block) } catch(e:CancellationException) { throw e } catch(_:SQLException) { unavailable() }
    private fun JdbcClanRaceStore.authorize(ctx:ClanRaceAdminContext,mutation:Boolean=false,owner:Boolean=false):AdminIdentity {
        val service=admins?:unavailable()
        if(mutation) service.requireOrigin(ctx.origin) else ctx.origin?.let(service::requireOrigin)
        return service.authorize(admin,ctx.token,ctx.csrfToken,mutation,owner).also { if(it.role==AdminRole.SUPPORT) forbidden() }
    }
    suspend fun list(ctx:ClanRaceAdminContext,page:PageRequest=PageRequest()):AdminPage<ClanRaceAdminView> = storage(true) { authorize(ctx);val all=catalog();page.result(all.drop(page.offset).take(page.pageSize),all.size.toLong()) }
    suspend fun detail(ctx:ClanRaceAdminContext,week:String):ClanRaceAdminView=storage(true) { authorize(ctx);date(week);find(week) }
    suspend fun create(ctx:ClanRaceAdminContext,req:ClanRaceDraftRequest):ClanRaceAdminView=mutate(ctx,"CREATE",req.weekStart,req.expectedVersion,req.reason,req.requestId,Json.encodeToString(req),true,false) { old,_ ->
        if(old!=null || req.expectedVersion!=0L) conflict();future(req.weekStart);ClanRaceAdminView(req.weekStart,1,ClanRaceConfigState.DRAFT,validate(req.config)).also(::put)
    }
    suspend fun update(ctx:ClanRaceAdminContext,week:String,req:ClanRaceDraftRequest):ClanRaceAdminView=mutate(ctx,"UPDATE",week,req.expectedVersion,req.reason,req.requestId,Json.encodeToString(req)) { old,actor ->
        val before=old?:missing();if(req.weekStart!=week || before.state!=ClanRaceConfigState.DRAFT) conflict();future(week)
        val config=validate(req.config);if(actor.role!=AdminRole.OWNER && config!=before.config) forbidden()
        before.copy(version=Math.addExact(before.version,1),config=config).also(::put)
    }
    suspend fun clone(ctx:ClanRaceAdminContext,from:String,req:ClanRaceCloneRequest):ClanRaceAdminView=mutate(ctx,"CLONE",from,req.expectedVersion,req.reason,req.requestId,Json.encodeToString(req)) { old,_ ->
        val before=old?:missing();future(req.weekStart);if(catalog().any { it.weekStart==req.weekStart }) conflict()
        ClanRaceAdminView(req.weekStart,1,ClanRaceConfigState.DRAFT,before.config).also(::put)
    }
    suspend fun publish(ctx:ClanRaceAdminContext,week:String,req:ClanRaceMutationRequest):ClanRaceAdminView=mutate(ctx,"PUBLISH",week,req.expectedVersion,req.reason,req.requestId,Json.encodeToString(req),true) { old,_ ->
        val before=old?:missing();if(before.state!=ClanRaceConfigState.DRAFT) conflict();future(week);validate(before.config)
        before.copy(version=Math.addExact(before.version,1),state=ClanRaceConfigState.PUBLISHED,publishedRevision=Math.addExact(before.version,1)).also(::publish)
    }
    suspend fun archive(ctx:ClanRaceAdminContext,week:String,req:ClanRaceMutationRequest):ClanRaceAdminView=mutate(ctx,"ARCHIVE",week,req.expectedVersion,req.reason,req.requestId,Json.encodeToString(req),true) { old,_ ->
        val before=old?:missing();future(week);if(before.state==ClanRaceConfigState.INACTIVE) conflict();before.copy(version=Math.addExact(before.version,1),state=ClanRaceConfigState.INACTIVE).also(::put)
    }
    suspend fun preview(ctx:ClanRaceAdminContext,week:String):ClanRacePreview=storage(true) {
        val actor=authorize(ctx,true);date(week);val value=find(week);val config=validate(value.config)
        val totals=(0..2).map { index -> config.rewards.fold(BigInteger.ZERO) { sum,r -> sum+BigInteger.valueOf(listOf(r.xp,r.gold,r.gems)[index].toLong())*BigInteger.valueOf(50) }.toString() }
        audit(actor.id,"PREVIEW",value,value,null,null)
        ClanRacePreview(value,raceWeekMillis(date(week)),raceWeekMillis(date(week).plusWeeks(1)),racePoints(raceDonationUnits(100,parseRaceRateUnits(config.goldRate))),config.gemRate,maximumBudget=ClanRaceBudget(totals[0],totals[1],totals[2]),serverNowEpochMillis=nowMillis())
    }
    private suspend fun mutate(ctx:ClanRaceAdminContext,action:String,week:String,version:Long,reason:String,request:String,payload:String,owner:Boolean=false,existing:Boolean=true,change:JdbcClanRaceStore.(ClanRaceAdminView?,AdminIdentity)->ClanRaceAdminView):ClanRaceAdminView=storage(true) {
        val actor=authorize(ctx,true,owner);date(week)
        if(version<0 || reason.isBlank() || reason.length>500 || reason.any(Char::isISOControl) || !request.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid()
        val fingerprint=hashToken("$action:$week:$payload")
        replay(actor.id,request)?.let { if(it.first!=fingerprint) conflict();return@storage it.second }
        val before=catalog().firstOrNull { it.weekStart==week };if(existing && before==null) missing()
        if(before!=null && before.version!=version) throw AdminException(409,"STALE_VERSION","Race config changed; reload before editing.")
        val result=change(before,actor);audit(actor.id,action,before,result,request,reason);remember(actor.id,request,fingerprint,result);result
    }
    private fun JdbcClanRaceStore.audit(actor:String,action:String,before:ClanRaceAdminView?,after:ClanRaceAdminView,request:String?,reason:String?) {
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(),actor,"CLAN_RACE_$action",UUID.nameUUIDFromBytes("clan-race:${after.weekStart}".toByteArray()).toString(),request,reason,before?.let { Json.encodeToString(it) },Json.encodeToString(after),nowMillis()))
    }
    private fun date(week:String):LocalDate=try { LocalDate.parse(week).also { if(it.toString()!=week || it.year !in 1970..9999 || it.dayOfWeek!=DayOfWeek.MONDAY) invalid() } } catch(_:DateTimeException) { invalid() }
    private fun future(week:String) { if(date(week)<raceWeekStart(nowMillis()).plusWeeks(1)) conflict() }
    private fun validate(config:ClanRaceConfig):ClanRaceConfig=try {
        val gold=parseRaceRateUnits(config.goldRate);val gem=parseRaceRateUnits(config.gemRate)
        // Preview examples must fit the same checked score domain as real donations.
        raceDonationUnits(100,gold);raceDonationUnits(1,gem)
        if(config.rewards.size!=3) invalid()
        config.rewards.forEach { if(listOf(it.xp,it.gold,it.gems).any { n -> n<0 } || listOf(it.xp,it.gold,it.gems).all { n -> n==0 }) invalid() }
        config.rewards.zipWithNext().forEach { (a,b) -> if(a.xp<b.xp || a.gold<b.gold || a.gems<b.gems || a==b) invalid() }
        config.copy(goldRate=racePoints(gold),gemRate=racePoints(gem))
    } catch(_:IllegalArgumentException) { invalid() }
    suspend fun current(player:String):ClanRaceCurrent=storage { val now=nowMillis();current(raceWeekStart(now),now) }
    suspend fun standings(player:String,page:PageRequest=PageRequest()):ClanRaceStandings=storage {
        val now=nowMillis();val week=raceWeekStart(now);val all=standingsAt(week,Math.addExact(now,1));val own=ownClan(player)
        ClanRaceStandings(week.toString(),all.drop(page.offset).take(page.pageSize),page.page,page.pageSize,all.size,all.firstOrNull { it.clanId==own },now)
    }
    suspend fun contributions(player:String):ClanRaceContributions=storage {
        val now=nowMillis();val week=raceWeekStart(now);val clan=ownClan(player)
        ClanRaceContributions(week.toString(),clan,clan?.let { contributions(week,it) }.orEmpty(),now)
    }
    suspend fun history(player:String,page:PageRequest=PageRequest()):ClanRaceHistory=storage {
        val now=nowMillis();val week=raceWeekStart(now)
        val all=catalog().filter { it.state==ClanRaceConfigState.PUBLISHED && date(it.weekStart)<week }.map { current(date(it.weekStart),now) }
        ClanRaceHistory(all.drop(page.offset).take(page.pageSize),page.page,page.pageSize,all.size,now)
    }
}
