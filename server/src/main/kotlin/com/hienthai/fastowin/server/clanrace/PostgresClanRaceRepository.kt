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
    internal fun catalog():List<ClanRaceAdminView> = rows("SELECT * FROM clan_race_configs ORDER BY week_start DESC") { ClanRaceAdminView(it.getDate("week_start").toString(),it.getLong("version"),ClanRaceConfigState.valueOf(it.getString("state")),Json.decodeFromString(it.getString("config")),(it.getObject("published_revision") as Number?)?.toLong()) }
    internal fun find(week:String)=catalog().firstOrNull { it.weekStart==week }?:missing()
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
        val value=catalog().firstOrNull { it.weekStart==week.toString() && it.state==ClanRaceConfigState.PUBLISHED }
        return ClanRaceCurrent(week.toString(),raceWeekMillis(week),raceWeekMillis(week.plusWeeks(1)),if(value==null) ClanRaceState.INACTIVE else if(now>=raceWeekMillis(week.plusWeeks(1))) ClanRaceState.CLOSED else ClanRaceState.ACTIVE,value?.config,value?.publishedRevision,now)
    }
    internal fun published(week:LocalDate):ClanRaceConfig?=rows("SELECT r.config FROM clan_race_revisions r JOIN clan_race_configs d USING(week_start) WHERE r.week_start=? AND d.state='PUBLISHED'",listOf(Date.valueOf(week))) { Json.decodeFromString<ClanRaceConfig>(it.getString(1)) }.singleOrNull()
}
