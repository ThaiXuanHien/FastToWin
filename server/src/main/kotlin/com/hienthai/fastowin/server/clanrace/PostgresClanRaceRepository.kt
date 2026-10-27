package com.hienthai.fastowin.server.clanrace

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.*
import java.time.*
import java.util.UUID
import javax.sql.DataSource

class PostgresClanRaceRepository(private val source:DataSource) {
    suspend fun <T> transaction(admin:Boolean=false,block:JdbcClanRaceStore.()->T):T=withContext(Dispatchers.IO) {
        source.connection.use { c ->
            c.autoCommit=false
            try {
                if(admin) c.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
                val store=JdbcClanRaceStore(c);store.lock();val value=store.block();c.commit();value
            } catch(error:Throwable) { c.rollback();throw error }
        }
    }
}
class JdbcClanRaceStore(internal val c:Connection) {
    internal val admin:AdminTransaction=JdbcAdminTransaction(c)
    fun lock() { c.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187355)") } }
    /** Only server callers supply time, after acquiring the shared gate. Transaction-local, never an HTTP input. */
    fun setTime(now:Long) { rows("SELECT set_config('fasttowin.race_now',?,true)",listOf(Instant.ofEpochMilli(now).toString())) { it.getString(1) } }
    internal fun <T> rows(sql:String,values:List<Any?> = emptyList(),read:(ResultSet)->T):List<T> = c.prepareStatement(sql).use { s ->
        values.forEachIndexed { i,v -> s.setObject(i+1,v) };s.executeQuery().use { r -> buildList { while(r.next()) add(read(r)) } }
    }
    internal fun update(sql:String,vararg values:Any?):Int=c.prepareStatement(sql).use { s -> values.forEachIndexed { i,v -> s.setObject(i+1,v) };s.executeUpdate() }
    internal fun uuid(id:String)=UUID.fromString(id)
    private fun metadata(row:ResultSet)=ClanRaceAdminView(row.getDate("week_start").toString(),row.getLong("version"),ClanRaceConfigState.valueOf(row.getString("state")),Json.decodeFromString<ClanRaceConfig>(row.getString("config")),(row.getObject("published_revision") as Number?)?.toLong())
    internal fun config(week:String):ClanRaceAdminView?=rows("SELECT * FROM clan_race_configs WHERE week_start=?",listOf(Date.valueOf(week)),::metadata).singleOrNull()
    internal fun withSettlement(value:ClanRaceAdminView)=value.copy(settlement=settlementStatus(LocalDate.parse(value.weekStart)))
    internal fun find(week:String)=withSettlement(config(week)?:missing())
    /** Page metadata in SQL before reading any immutable result or delivery aggregates. */
    internal fun configPage(page:PageRequest,before:LocalDate?=null):Pair<List<ClanRaceAdminView>,Long> {
        val filter=if(before==null) "" else " WHERE state='PUBLISHED' AND week_start<?"
        val values=if(before==null) emptyList() else listOf(Date.valueOf(before))
        val total=rows("SELECT count(*) FROM clan_race_configs$filter",values) { it.getLong(1) }.single()
        val items=rows("SELECT * FROM clan_race_configs$filter ORDER BY week_start DESC LIMIT ? OFFSET ?",values+listOf(page.pageSize,page.offset),::metadata)
        return items to total
    }
    internal fun put(value:ClanRaceAdminView) {
        update("""INSERT INTO clan_race_configs(week_start,version,state,config,published_revision) VALUES(?,?,?,?,?)
          ON CONFLICT(week_start) DO UPDATE SET version=excluded.version,state=excluded.state,config=excluded.config,published_revision=excluded.published_revision""",Date.valueOf(value.weekStart),value.version,value.state.name,Json.encodeToString(value.config),value.publishedRevision)
    }
    internal fun publish(value:ClanRaceAdminView) {
        put(value);update("INSERT INTO clan_race_revisions VALUES(?,?,?)",Date.valueOf(value.weekStart),value.publishedRevision,Json.encodeToString(value.config))
    }
    internal fun replay(actor:String,request:String)=rows("SELECT fingerprint,result FROM clan_race_admin_requests WHERE actor_id=? AND request_id=?",listOf(uuid(actor),request)) { it.getString(1) to Json.decodeFromString<ClanRaceAdminView>(it.getString(2)) }.singleOrNull()
    internal fun remember(actor:String,request:String,fingerprint:String,value:ClanRaceAdminView) { update("INSERT INTO clan_race_admin_requests VALUES(?,?,?,?)",uuid(actor),request,fingerprint,Json.encodeToString(value)) }
    fun current(week:LocalDate,now:Long):ClanRaceCurrent {
        return current(week,now,config(week.toString()))
    }
    internal fun current(week:LocalDate,now:Long,metadata:ClanRaceAdminView?):ClanRaceCurrent {
        val value=metadata?.takeIf { it.state==ClanRaceConfigState.PUBLISHED }?.let(::withSettlement)
        return ClanRaceCurrent(week.toString(),raceWeekMillis(week),raceWeekMillis(week.plusWeeks(1)),if(value==null) ClanRaceState.INACTIVE else if(value.settlement?.state==ClanRaceSettlementState.SETTLED) ClanRaceState.SETTLED else if(now>=raceWeekMillis(week.plusWeeks(1))) ClanRaceState.CLOSED else ClanRaceState.ACTIVE,value?.config,value?.publishedRevision,now,value?.settlement?:if(value!=null && now>=raceWeekMillis(week.plusWeeks(1))) ClanRaceSettlementStatus(ClanRaceSettlementState.PENDING) else null)
    }
    internal fun published(week:LocalDate):ClanRaceConfig?=rows("SELECT r.config FROM clan_race_revisions r JOIN clan_race_configs d USING(week_start) WHERE r.week_start=? AND d.state='PUBLISHED'",listOf(Date.valueOf(week))) { Json.decodeFromString<ClanRaceConfig>(it.getString(1)) }.singleOrNull()
}
