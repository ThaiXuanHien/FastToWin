package com.hienthai.fastowin.server.missions

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.clanrace.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.*
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

/** Each transaction takes the catalog gate before player/clan wallet locks. External settlement callers use lock() first. */
class PostgresMissionRepository(private val source:DataSource) {
    suspend fun <T> transaction(admin:Boolean=false,block:JdbcMissionStore.()->T):T=withContext(Dispatchers.IO) {
        source.connection.use { c ->
            c.autoCommit=false
            try {
                if(admin) c.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
                val store=JdbcMissionStore(c);store.lock();val value=store.block();c.commit();value
            } catch(error:Throwable) { c.rollback();throw error }
        }
    }
}
@Serializable internal data class MissionSelection(val definitionId:String,val revision:Long,val config:MissionConfig)
internal data class MissionInstance(val id:String,val owner:String,val start:LocalDate,val selection:MissionSelection,val progress:Int,val legacySource:String?)

class JdbcMissionStore(private val c:Connection) {
    internal val admin:AdminTransaction=JdbcAdminTransaction(c)
    fun lock() { c.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187355)") } }
    internal fun <T> rows(sql:String,values:List<Any?> = emptyList(),read:(ResultSet)->T):List<T> = c.prepareStatement(sql).use { s ->
        values.forEachIndexed { index,value -> s.setObject(index+1,value) };s.executeQuery().use { r -> buildList { while(r.next()) add(read(r)) } }
    }
    internal fun update(sql:String,vararg values:Any?):Int=c.prepareStatement(sql).use { s -> values.forEachIndexed { index,value -> s.setObject(index+1,value) };s.executeUpdate() }
    private fun id(value:String):UUID=try { UUID.fromString(value) } catch(_:IllegalArgumentException) { invalid() }
    internal fun catalog():List<MissionAdminView> = rows("SELECT * FROM mission_definitions ORDER BY selection_order,id") {
        MissionAdminView(it.getString("id"),it.getLong("version"),MissionCatalogState.valueOf(it.getString("state")),Json.decodeFromString(it.getString("draft")),it.getObject("published_revision")?.let { v -> (v as Number).toLong() },it.getDate("effective_from")?.toString())
    }
    internal fun find(definitionId:String):MissionAdminView=catalog().firstOrNull { it.id==definitionId }?:missing()
    internal fun put(value:MissionAdminView) {
        update("""INSERT INTO mission_definitions(id,code,version,state,draft,published_revision,effective_from) VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET version=excluded.version,state=excluded.state,draft=excluded.draft,published_revision=excluded.published_revision,effective_from=excluded.effective_from""",
            id(value.id),value.draft.code,value.version,value.state.name,Json.encodeToString(value.draft),value.publishedRevision,value.effectiveFrom?.let { Date.valueOf(it) })
    }
    internal fun publish(value:MissionAdminView) {
        val config=value.draft
        update("INSERT INTO mission_revisions(definition_id,revision,config,effective_from,target,reward_xp,reward_gold,reward_gems) VALUES (?,?,?,?,?,?,?,?)",
            id(value.id),value.version,Json.encodeToString(config),Date.valueOf(value.effectiveFrom),config.target,config.reward.xp,config.reward.gold,config.reward.gems)
        put(value)
    }
    internal fun archiveCutoff(definitionId:String,date:LocalDate):LocalDate {
        val published=rows("SELECT revision,effective_from,config FROM mission_revisions WHERE definition_id=? ORDER BY revision",listOf(id(definitionId))) {
            Triple(it.getLong(1),it.getDate(2).toLocalDate(),Json.decodeFromString<MissionConfig>(it.getString(3)))
        }
        // A cadence already in progress can retain its old revision when another cadence starts mid-period.
        val current=MissionCadence.entries.mapNotNull { cadence ->
            published.lastOrNull { it.second<=periodStart(cadence,date) }?.takeIf { it.third.period==cadence }
        }
        // Archive also cancels scheduled revisions; an unpublished draft never determines this boundary.
        val pending=published.filter { it.second>date }
        return (current+pending).minOfOrNull { nextPeriod(it.third.period,date) }?:conflict()
    }
    internal fun archive(value:MissionAdminView,from:LocalDate) { put(value);update("UPDATE mission_definitions SET archived_from=? WHERE id=?",Date.valueOf(from),id(value.id)) }
    internal fun delete(value:MissionAdminView) { update("DELETE FROM mission_definitions WHERE id=?",id(value.id)) }
    internal fun replay(actor:String,request:String):Pair<String,MissionAdminView>?=rows("SELECT fingerprint,result FROM mission_admin_requests WHERE actor_id=? AND request_id=?",listOf(id(actor),request)) { it.getString(1) to Json.decodeFromString<MissionAdminView>(it.getString(2)) }.singleOrNull()
    internal fun remember(actor:String,request:String,fingerprint:String,value:MissionAdminView) { update("INSERT INTO mission_admin_requests(actor_id,request_id,fingerprint,result) VALUES (?,?,?,?)",id(actor),request,fingerprint,Json.encodeToString(value)) }
    internal fun selections(scope:MissionScope,period:MissionCadence,start:LocalDate):List<MissionSelection> = rows("""SELECT r.* FROM mission_revisions r JOIN mission_definitions d ON d.id=r.definition_id
        WHERE r.effective_from<=? AND (d.archived_from IS NULL OR d.archived_from>?)
        AND r.revision=(SELECT max(r2.revision) FROM mission_revisions r2 WHERE r2.definition_id=r.definition_id AND r2.effective_from<=?)
        ORDER BY d.selection_order,d.id""",listOf(Date.valueOf(start),Date.valueOf(start),Date.valueOf(start))) {
        MissionSelection(it.getString("definition_id"),it.getLong("revision"),Json.decodeFromString(it.getString("config")))
    }.filter { it.config.scope==scope && it.config.period==period && it.config.code!="LEGACY_CLAN_QUEST" &&
        (it.config.startsOn==null || LocalDate.parse(it.config.startsOn)<=start) && (it.config.endsOn==null || start<LocalDate.parse(it.config.endsOn)) }
    internal fun plan(scope:MissionScope,period:MissionCadence,start:LocalDate):List<MissionSelection> {
        rows("SELECT selection FROM mission_periods WHERE scope=? AND cadence=? AND period_start=?",listOf(scope.name,period.name,Date.valueOf(start))) { Json.decodeFromString<List<MissionSelection>>(it.getString(1)) }.singleOrNull()?.let { return it }
        val pool=selections(scope,period,start)
        val selected=if(scope==MissionScope.CLAN) pool else selectMissions(period,start,pool) { it.config.difficulty }
        update("INSERT INTO mission_periods(scope,cadence,period_start,selection) VALUES (?,?,?,?)",scope.name,period.name,Date.valueOf(start),Json.encodeToString(selected))
        return selected
    }
    private fun create(owner:String,start:LocalDate,selection:MissionSelection,progress:Int=0,legacy:String?=null):String {
        val instanceId=UUID.nameUUIDFromBytes("$owner:${selection.definitionId}:$start".toByteArray()).toString()
        update("INSERT INTO mission_instances(id,owner_id,scope,definition_id,revision,period_start,config,progress,legacy_source) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(owner_id,definition_id,period_start) DO NOTHING",
            id(instanceId),id(owner),selection.config.scope.name,id(selection.definitionId),selection.revision,Date.valueOf(start),Json.encodeToString(selection.config),progress,legacy)
        return instanceId
    }
    private fun instanceRow(r:ResultSet)=MissionInstance(r.getString("id"),r.getString("owner_id"),r.getDate("period_start").toLocalDate(),MissionSelection(r.getString("definition_id"),r.getLong("revision"),Json.decodeFromString(r.getString("config"))),r.getInt("progress"),r.getString("legacy_source"))
    private fun instances(owner:String)=rows("SELECT * FROM mission_instances WHERE owner_id=? ORDER BY period_start,id",listOf(id(owner)),::instanceRow)
    private fun member(player:String,clan:String):Boolean=rows("SELECT 1 FROM clan_members WHERE clan_id=? AND user_id=? FOR SHARE",listOf(id(clan),id(player))) { true }.isNotEmpty()
    internal fun currentClan(player:String):String?=rows("SELECT clan_id FROM clan_members WHERE user_id=? FOR SHARE",listOf(id(player))) { it.getString(1) }.singleOrNull()
    private fun activePlayer(player:String) { if(rows("SELECT id FROM users WHERE id=? AND status <> 'DELETED' FOR KEY SHARE",listOf(id(player))) { true }.isEmpty()) missing() }
    internal fun ensure(player:String,scope:MissionScope,date:LocalDate):List<MissionInstance> {
        activePlayer(player)
        val owner=if(scope==MissionScope.PERSONAL) player else currentClan(player)?:return emptyList()
        if(scope==MissionScope.PERSONAL) migratePersonal(player) else migrateClan(owner)
        for(period in MissionCadence.entries) {
            val start=periodStart(period,date)
            // Existing legacy rows were the authoritative selections at cutover for that account.
            val selected=plan(scope,period,start)
            selected.forEach { selectedMission ->
                create(owner,start,selectedMission)
            }
        }
                val all=instances(owner)
        val order=MissionCadence.entries.flatMap { cadence -> plan(scope,cadence,periodStart(cadence,date)) }.map { it.definitionId }
        return all.sortedWith(compareBy<MissionInstance> { it.selection.config.period.ordinal }.thenBy { order.indexOf(it.selection.definitionId).let { index -> if(index<0) Int.MAX_VALUE else index } }.thenBy { it.id })
    }
    fun list(player:String,scope:MissionScope,now:Long):List<MissionInstanceView> {
        val date=missionDate(now)
        return ensure(player,scope,date).filter { it.legacySource=="CLAN_QUEST" || it.start==periodStart(it.selection.config.period,date) || (it.progress>=it.selection.config.target && !claimed(it.id,player)) }.map { view(it,player) }
    }
    private fun claimed(instance:String,player:String)=rows("SELECT 1 FROM mission_claims WHERE instance_id=? AND user_id=?",listOf(id(instance),id(player))) { true }.isNotEmpty()
    private fun view(instance:MissionInstance,player:String):MissionInstanceView {
        val config=instance.selection.config
        val contribution=rows("SELECT coalesce(sum(amount),0) FROM mission_progress_events WHERE instance_id=? AND user_id=?",listOf(id(instance.id),id(player))) { it.getLong(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }.single()
        val state=if(claimed(instance.id,player)) MissionClaimState.CLAIMED else if(instance.progress>=config.target) MissionClaimState.CLAIMABLE else MissionClaimState.IN_PROGRESS
        return MissionInstanceView(instance.id,config.code,config.scope,config.action,config.period,config.difficulty,instance.start.toString(),nextPeriod(config.period,instance.start).toString(),config.target,instance.progress,contribution,config.reward,state,if(config.scope==MissionScope.CLAN && config.action==MissionAction.WIN_STREAK) "MissionClanStreak" else "MissionAction${config.action.name}",mapOf("target" to config.target.toString(),"accuracy" to (config.accuracyPercent?:90).toString(),"streak" to (config.streakThreshold?:config.target).toString()),config.matchType,config.accuracyPercent,config.streakThreshold)
    }
    fun record(event:MissionProgressEvent) {
        require(event.sourceId.isNotBlank() && event.sourceId.length<=150 && listOf(event.correct,event.wrong,event.winStreak,event.donatedGold,event.donatedGems).all { it>=0 })
        val date=missionDate(event.occurredAtMillis)
        val all=ensure(event.playerId,MissionScope.PERSONAL,date)+ensure(event.playerId,MissionScope.CLAN,date)
        if(event.donatedGold>0 || event.donatedGems>0) {
            currentClan(event.playerId)?.let { clan ->
                // Keep direct donations distinct; the future top-race module owns any point conversion.
                update("INSERT INTO mission_contribution_events(source_id,user_id,clan_id,points,source_type) VALUES (?,?,?,0,'DONATION') ON CONFLICT DO NOTHING",event.sourceId,id(event.playerId),id(clan))
            }
        }
        for(instance in all) {
            val config=instance.selection.config
            if(instance.legacySource!="CLAN_QUEST" && instance.start!=periodStart(config.period,date)) continue
            val increment=if(instance.legacySource=="CLAN_QUEST") { if(event.won && event.matchType==MatchType.RANKED && !event.intentionalLeave) 1 else 0 } else config.increment(event)
            if(increment<=0) continue
            val source=if(config.scope==MissionScope.CLAN && config.action==MissionAction.WIN_STREAK) "streak:${event.playerId}" else event.sourceId
            applyProgress(instance,event.playerId,source,increment,event.occurredAtMillis)
        }
    }
    private fun applyProgress(instance:MissionInstance,player:String,source:String,increment:Int,now:Long) {
        val config=instance.selection.config
        val tenure=if(config.scope==MissionScope.CLAN && instance.progress<config.target) JdbcClanRaceStore(c).currentTenure(player,instance.owner) else null
        if(update("INSERT INTO mission_progress_events(instance_id,user_id,source_id,amount,occurred_at,race_tenure_id) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",id(instance.id),id(player),source,increment,Timestamp(now),tenure?.let(::id))==0) return
        update("UPDATE mission_instances SET progress=LEAST(?,progress::bigint+?) WHERE id=?",config.target,increment,id(instance.id))
        if(instance.legacySource=="CLAN_QUEST") {
            update("UPDATE clans SET quest_progress=quest_progress+? WHERE id=?",increment,id(instance.owner))
            update("UPDATE clan_members SET quest_contribution=quest_contribution+? WHERE clan_id=? AND user_id=?",increment,id(instance.owner),id(player))
        }
        if(config.scope==MissionScope.PERSONAL) projectPersonal(instance.copy(progress=minOf(config.target.toLong(),instance.progress.toLong()+increment).toInt()),player)
    }
    fun addLegacyQuestProgress(clan:String,player:String,amount:Int):Boolean {
        if(amount<=0 || !member(player,clan)) return false
        migrateClan(clan)
        val instance=instances(clan).firstOrNull { it.legacySource=="CLAN_QUEST" }?:return false
        applyProgress(instance,player,"legacy-command:${UUID.randomUUID()}",amount,System.currentTimeMillis());return true
    }    private fun projectPersonal(instance:MissionInstance,player:String) {
        val config=instance.selection.config
        update("""INSERT INTO user_missions(user_id,mission_code,period_start,progress,target,reward_xp,reward_gold,reward_gems,completed_at)
            VALUES (?,?,?,?,?,?,?,?,CASE WHEN ?>=? THEN CURRENT_TIMESTAMP ELSE NULL END)
            ON CONFLICT(user_id,mission_code,period_start) DO UPDATE SET progress=excluded.progress,completed_at=COALESCE(user_missions.completed_at,excluded.completed_at)""",
            id(player),config.code,Date.valueOf(instance.start),instance.progress,config.target,config.reward.xp,config.reward.gold,config.reward.gems,instance.progress,config.target)
    }
    internal fun claim(player:String,instanceId:String,requestId:String,now:Long=System.currentTimeMillis()):MissionClaimResponse {
        activePlayer(player)
        val instance=rows("SELECT * FROM mission_instances WHERE id=? FOR UPDATE",listOf(id(instanceId)),::instanceRow).singleOrNull()?:missing()
        val config=instance.selection.config
        if(config.scope==MissionScope.PERSONAL && instance.owner!=player) missing()
        if(config.scope==MissionScope.CLAN && !member(player,instance.owner)) forbidden()
        val existingRequest=rows("SELECT instance_id FROM mission_claims WHERE user_id=? AND request_id=?",listOf(id(player),requestId)) { it.getString(1) }.singleOrNull()
        if(existingRequest!=null && existingRequest!=instanceId) conflict()
        if(claimed(instanceId,player)) return MissionClaimResponse(instanceId,MissionClaimState.CLAIMED,config.reward)
        if(instance.progress<config.target) throw AdminException(409,"MISSION_NOT_COMPLETED","Mission is not complete.")
        val reward=config.reward
        update("INSERT INTO player_stats(user_id,updated_at) VALUES (?,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO NOTHING",id(player))
        val before=rows("SELECT gold,gems,experience_points FROM player_stats WHERE user_id=? FOR UPDATE",listOf(id(player))) { Triple(it.getInt(1),it.getInt(2),it.getInt(3)) }.single()
        val balances=try { Triple(Math.addExact(before.first,reward.gold),Math.addExact(before.second,reward.gems),Math.addExact(before.third,reward.xp)) } catch(_:ArithmeticException) { throw AdminException(409,"BALANCE_OVERFLOW","Reward exceeds wallet capacity.") }
        val sourceType=if(instance.legacySource=="CLAN_QUEST") "CLAN_QUEST" else "MISSION"
        val sourceId=if(sourceType=="CLAN_QUEST") instance.owner else instance.legacySource?:instanceId
        // Old ledger keys remain canonical; encountering one means it was already granted before rollout.
        val grant=if(reward.gold!=0 || reward.gems!=0 || reward.xp!=0) update("INSERT INTO wallet_transactions(id,user_id,source_type,source_id,gold_delta,gems_delta,xp_delta) VALUES (?,?,?,?,?,?,?) ON CONFLICT(user_id,source_type,source_id) DO NOTHING",UUID.randomUUID(),id(player),sourceType,sourceId,reward.gold,reward.gems,reward.xp)==1 else true
        if(grant) update("UPDATE player_stats SET gold=?,gems=?,experience_points=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",balances.first,balances.second,balances.third,id(player))
        update("INSERT INTO mission_claims(instance_id,user_id,request_id) VALUES (?,?,?)",id(instanceId),id(player),requestId)
        if(grant && config.scope==MissionScope.CLAN) JdbcClanRaceStore(c).recordMission(player,instance.owner,instanceId,reward.contributionPoints,now)
        if(config.scope==MissionScope.PERSONAL) update("UPDATE user_missions SET claimed_at=CURRENT_TIMESTAMP WHERE user_id=? AND mission_code=? AND period_start=?",id(player),config.code,Date.valueOf(instance.start))
        if(instance.legacySource=="CLAN_QUEST") update("UPDATE clan_members SET quest_reward_claimed=TRUE WHERE clan_id=? AND user_id=?",id(instance.owner),id(player))
        if(config.scope==MissionScope.CLAN && reward.contributionPoints>0 && grant) update("INSERT INTO mission_contribution_events(source_id,user_id,clan_id,points,source_type) VALUES (?,?,?,?,'MISSION') ON CONFLICT DO NOTHING",instanceId,id(player),id(instance.owner),reward.contributionPoints)
        if(grant) grantNewAchievements(c,id(player),completedAchievementCodes(achievementProgress(c,id(player)),setOf("PLAYER_LEVEL_30")),java.time.Instant.now(),null)
        return MissionClaimResponse(instanceId,MissionClaimState.CLAIMED,reward)
    }
    fun claimLegacyPersonal(player:String,code:String,date:LocalDate):MissionRewardClaimResult {
        val instance=ensure(player,MissionScope.PERSONAL,date).firstOrNull { it.selection.config.code==code && it.start==periodStart(it.selection.config.period,date) }?:return MissionRewardClaimResult(MissionRewardClaimStatus.INVALID_MISSION)
        if(claimed(instance.id,player)) return MissionRewardClaimResult(MissionRewardClaimStatus.ALREADY_CLAIMED)
        if(instance.progress<instance.selection.config.target) return MissionRewardClaimResult(MissionRewardClaimStatus.NOT_COMPLETED)
        val reward=claim(player,instance.id,"legacy:${instance.id}").reward
        return MissionRewardClaimResult(MissionRewardClaimStatus.CLAIMED,reward.xp,reward.gold,reward.gems)
    }
    fun claimLegacyClan(clan:String,player:String):Boolean {
        if(!member(player,clan)) return false
        migrateClan(clan)
        val instance=instances(clan).firstOrNull { it.legacySource=="CLAN_QUEST" }?:return false
        if(claimed(instance.id,player) || instance.progress<instance.selection.config.target) return false
        claim(player,instance.id,"legacy:${instance.id}");return true
    }
    internal fun legacyDefinitions(player:String,date:LocalDate):List<com.hienthai.fastowin.server.MissionDefinition> = ensure(player,MissionScope.PERSONAL,date).filter { it.start==periodStart(it.selection.config.period,date) }.map {
        val config=it.selection.config
        val localization=com.hienthai.fastowin.localization.LocalizationService(com.hienthai.fastowin.localization.AppLanguage.VIETNAMESE)
        val specificKey=when {
            config.action==MissionAction.PLAY && config.matchType==MatchType.CASUAL && config.target==2 -> com.hienthai.fastowin.localization.TextKey.MissionCasualTwo
            config.action==MissionAction.PLAY && config.matchType==MatchType.RANKED && config.target==2 -> com.hienthai.fastowin.localization.TextKey.MissionRankedTwo
            config.action==MissionAction.WIN && config.matchType==MatchType.RANKED && config.target==3 -> com.hienthai.fastowin.localization.TextKey.MissionWeeklyRankedWinThree
            else -> null
        }
        val key=specificKey?:com.hienthai.fastowin.localization.TextKey.valueOf("MissionAction${config.action.name}")
        val base=localization.text(key,mapOf("target" to config.target,"accuracy" to (config.accuracyPercent?:90)))
        // Legacy snapshots have no template arguments/matchType field. Their fallback must carry the whole condition.
        val title=if(specificKey==null && config.matchType!=null) {
            val mode=if(config.matchType==MatchType.CASUAL) com.hienthai.fastowin.localization.TextKey.CasualMatch else com.hienthai.fastowin.localization.TextKey.RankedMatch
            "$base (${localization.text(mode)})"
        } else base
        com.hienthai.fastowin.server.MissionDefinition(config.code,title,key,config.target,config.reward.xp,config.reward.gold,config.reward.gems,com.hienthai.fastowin.server.MissionPeriod.valueOf(config.period.name),config.difficulty)
    }
    private fun legacyDefinition(config:MissionConfig):MissionSelection {
        val existing=catalog().firstOrNull { it.draft.code==config.code }
        if(existing!=null) {
            val revision=rows("SELECT revision FROM mission_revisions WHERE definition_id=? ORDER BY revision LIMIT 1",listOf(id(existing.id))) { it.getLong(1) }.single()
            return MissionSelection(existing.id,revision,config)
        }
        val value=MissionAdminView(UUID.randomUUID().toString(),1,MissionCatalogState.PUBLISHED,config,1,"1970-01-01")
        put(value);publish(value)
        if(config.code!="LEGACY_CLAN_QUEST") update("UPDATE mission_definitions SET state='ARCHIVED',archived_from='1970-01-01' WHERE id=?",id(value.id))
        return MissionSelection(value.id,1,config)
    }
    private fun migratePersonal(player:String) {
        val legacy=rows("SELECT u.* FROM user_missions u WHERE u.user_id=? AND NOT EXISTS(SELECT 1 FROM mission_instances i JOIN mission_definitions d ON d.id=i.definition_id WHERE i.owner_id=u.user_id AND i.scope='PERSONAL' AND i.period_start=u.period_start AND d.code=u.mission_code)",listOf(id(player))) {
            val code=it.getString("mission_code")
            val existing=rows("SELECT r.config FROM mission_revisions r JOIN mission_definitions d ON d.id=r.definition_id WHERE d.code=? ORDER BY r.revision LIMIT 1",listOf(code)) { row -> Json.decodeFromString<MissionConfig>(row.getString(1)) }.singleOrNull()
            val config=(existing?:MissionConfig(code,MissionScope.PERSONAL,if(code.contains("PERFECT")) MissionAction.PERFECT_WIN else if(code.contains("CORRECT")) MissionAction.CORRECT else MissionAction.PLAY,if(code.startsWith("WEEKLY")) MissionCadence.WEEKLY else MissionCadence.DAILY,MissionDifficulty.NORMAL,it.getInt("target"),MissionReward())).copy(target=it.getInt("target"),reward=MissionReward(it.getInt("reward_xp"),it.getInt("reward_gold"),it.getInt("reward_gems")))
            Triple(config,it.getDate("period_start").toLocalDate(),it.getInt("progress")) to it.getTimestamp("claimed_at")
        }
        for((row,claimedAt) in legacy) {
            val (config,start,progress)=row
            val instance=create(player,start,legacyDefinition(config),progress,"${config.code}:$start")
            if(claimedAt!=null) update("INSERT INTO mission_claims(instance_id,user_id,request_id,claimed_at) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",id(instance),id(player),"migration:$instance",claimedAt)
        }
    }
    fun migrateClan(clan:String) {
        val value=rows("SELECT m.target,c.quest_progress,m.reward_xp,m.reward_gold,m.reward_gems FROM mission_legacy_clans m JOIN clans c ON c.id=m.clan_id WHERE c.id=?",listOf(id(clan))) { MissionConfig("LEGACY_CLAN_QUEST",MissionScope.CLAN,MissionAction.WIN,MissionCadence.WEEKLY,MissionDifficulty.NORMAL,it.getInt(1),MissionReward(it.getInt(3),it.getInt(4),it.getInt(5))) to it.getInt(2) }.singleOrNull()?:return
        val instance=create(clan,LocalDate.of(1970,1,1),legacyDefinition(value.first),value.second,"CLAN_QUEST")
        update("UPDATE mission_instances SET progress=? WHERE id=?",value.second,id(instance))
        rows("SELECT user_id,max(contribution) AS contribution,bool_or(claimed) AS claimed FROM (SELECT user_id,contribution,claimed FROM mission_legacy_clan_members WHERE clan_id=? UNION ALL SELECT user_id,quest_contribution,quest_reward_claimed FROM clan_members WHERE clan_id=?) m GROUP BY user_id",listOf(id(clan),id(clan))) { Triple(it.getString(1),it.getInt(2),it.getBoolean(3)) }.forEach { (player,progress,wasClaimed) ->
            update("INSERT INTO mission_progress_events(instance_id,user_id,source_id,amount,occurred_at) VALUES (?,?,'legacy-contribution',?,CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING",id(instance),id(player),progress)
            if(wasClaimed) update("INSERT INTO mission_claims(instance_id,user_id,request_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",id(instance),id(player),"migration:$instance")
        }
    }
    /** One-time/idempotent compatibility import, also safe to run after restoring a legacy fixture. */
    fun migrateLegacy() {
        rows("SELECT DISTINCT user_id FROM user_missions") { it.getString(1) }.forEach(::migratePersonal)
        rows("SELECT id FROM clans") { it.getString(1) }.forEach(::migrateClan)
    }
}
