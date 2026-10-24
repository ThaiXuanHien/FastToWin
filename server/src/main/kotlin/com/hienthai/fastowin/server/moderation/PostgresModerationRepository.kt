package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.announcements.JdbcNotificationPersistence
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

class PostgresModerationRepository(private val source:DataSource):ModerationRepository {
    /** Hot gameplay queries never acquire the admin mutation lock and cannot write to PostgreSQL. */
    override suspend fun <T> read(block:ModerationReadTransaction.()->T):T=withContext(Dispatchers.IO) {
        try { source.connection.use {connection ->
            connection.autoCommit=false
            connection.isReadOnly=true
            try {
                val result=block(JdbcModerationTransaction(connection))
                currentCoroutineContext().ensureActive();connection.commit();result
            } catch(error:Throwable) {connection.rollback();throw error}
        }} catch(_:SQLException) {unavailable()}
    }
    override suspend fun <T> transaction(block:ModerationTransaction.()->T):T=withContext(Dispatchers.IO) {
        try { source.connection.use {connection ->
            connection.autoCommit=false
            try {
                // Shared with access changes: role and session are re-read under this lock in the SAME transaction.
                connection.prepareStatement("SELECT pg_advisory_xact_lock(7421092187349)").use {it.execute()}
                val result=JdbcModerationTransaction(connection).block()
                currentCoroutineContext().ensureActive();connection.commit();result
            } catch(error:Throwable) {connection.rollback();throw error}
        }} catch(error:SQLException) {
            if(error.sqlState?.startsWith("23")==true) conflict()
            unavailable()
        }
    }
}

/** Also used by account creation/deletion so consent and erasure commit with the identity mutation. */
class JdbcModerationTransaction(private val connection:Connection):ModerationTransaction {
    override val admin:AdminTransaction=JdbcAdminTransaction(connection)
    private fun execute(sql:String,vararg values:Any?):Int=connection.prepareStatement(sql).use {statement ->
        values.forEachIndexed {i,value->statement.setObject(i+1,value)};statement.executeUpdate()
    }
    private fun <T> rows(sql:String,vararg values:Any?,mapper:(ResultSet)->T):List<T> = connection.prepareStatement(sql).use {statement ->
        values.forEachIndexed {i,value->statement.setObject(i+1,value)}
        statement.executeQuery().use {result->buildList {while(result.next()) add(mapper(result))}}
    }
    private fun uuid(id:String)=UUID.fromString(id)
    override fun registerPlayer(playerId:String) {if(!playerExists(playerId)) unauthorized()}
    override fun acceptedVersion(playerId:String)=rows("SELECT accepted_version FROM ugc_content_terms WHERE user_id=?",uuid(playerId)){it.getString(1)}.singleOrNull()
    override fun acceptTerms(playerId:String,version:String,nowMillis:Long) {
        if(version!=CONTENT_TERMS_VERSION) moderationError(400,"CONTENT_TERMS_VERSION_INVALID")
        execute("""INSERT INTO ugc_content_terms(user_id,accepted_version,accepted_at) VALUES (?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET accepted_version=EXCLUDED.accepted_version,accepted_at=EXCLUDED.accepted_at
            WHERE ugc_content_terms.accepted_version<>EXCLUDED.accepted_version""",uuid(playerId),version,nowMillis)
    }
    fun requireContentTerms(playerId:String,requiredVersion:String=CONTENT_TERMS_VERSION) {
        if(acceptedVersion(playerId)!=requiredVersion) moderationError(403,"CONTENT_TERMS_REQUIRED")
    }
    override fun playerExists(playerId:String)=rows("SELECT 1 FROM users WHERE id=? AND status<>'DELETED'",uuid(playerId)){true}.isNotEmpty()
    override fun putTarget(target:UgcTarget):Unit=throw UnsupportedOperationException("PostgreSQL targets come from authoritative source rows")
    override fun removeTarget(type:UgcTargetType,id:String) = Unit
    override fun publishedTargets():List<Pair<UgcTargetType,String>> = emptyList()
    override fun target(type:UgcTargetType,id:String):UgcTarget?=when(type) {
        UgcTargetType.PLAYER_PROFILE -> rows("SELECT p.display_name FROM profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=? AND u.status<>'DELETED'",uuid(id)) {
            val text=it.getString(1);UgcTarget(type,id,id,text,contentRevision(text))
        }.singleOrNull()
        UgcTargetType.PLAYER_AVATAR -> rows("""SELECT p.avatar_revision,p.avatar_data,p.avatar_url,a.object_key FROM profiles p
            JOIN users u ON u.id=p.user_id LEFT JOIN avatar_objects a ON a.user_id=p.user_id
            WHERE p.user_id=? AND u.status<>'DELETED' AND (p.avatar_data IS NOT NULL OR p.avatar_url IS NOT NULL OR a.object_key IS NOT NULL)""",uuid(id)) {
            val revision=it.getLong(1).toString()
            // References only: legacy base64 stays in profiles and is NEVER copied into a report.
            UgcTarget(type,id,id,"",revision,it.getString("object_key") ?: "avatar-revision:$revision")
        }.singleOrNull()
        UgcTargetType.CLAN -> rows("SELECT name,description,owner_id FROM clans WHERE id=?",uuid(id)) {
            val text=it.getString(1)+"\n"+it.getString(2).orEmpty()
            UgcTarget(type,id,it.getString(3),text,contentRevision(text))
        }.singleOrNull()
        UgcTargetType.ROOM -> rows("SELECT state_json::text FROM active_room_snapshots WHERE room_id=?",uuid(id)) {
            val json=Json.parseToJsonElement(it.getString(1)).jsonObject
            val text=json["roomName"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val owner=json["host"]?.jsonObject?.get("playerId")?.jsonPrimitive?.contentOrNull
            UgcTarget(type,id,owner,text,contentRevision(text))
        }.singleOrNull()
        UgcTargetType.TOURNAMENT -> rows("SELECT snapshot_json::text FROM tournaments WHERE tournament_id=?",uuid(id)) {
            val json=Json.parseToJsonElement(it.getString(1)).jsonObject
            val text=json["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            UgcTarget(type,id,json["hostPlayerId"]?.jsonPrimitive?.contentOrNull,text,contentRevision(text))
        }.singleOrNull()
    }
    override fun appendAction(value:ModerationActionRecord) {
        execute("""INSERT INTO ugc_moderation_actions(id,report_id,subject_user_id,actor_id,action,reason,created_at,expires_at)
            VALUES (?,?,?,?,?,?,?,?)""",uuid(value.id),value.reportId?.let(::uuid),value.subjectUserId?.let(::uuid),
            value.actorId?.let(::uuid),value.action,value.reason,value.createdAtEpochMillis,value.expiresAtEpochMillis)
    }
    override fun lastActionReason(reportId:String)=rows("SELECT reason FROM ugc_moderation_actions WHERE report_id=? ORDER BY created_at DESC,sequence DESC LIMIT 1",uuid(reportId)){it.getString(1)}.singleOrNull()
    private fun readReport(r:ResultSet)=ModerationReport(r.getString("id"),r.getInt("version"),ModerationStatus.valueOf(r.getString("status")),
        r.getString("reporter_id"),UgcTargetType.valueOf(r.getString("target_type")),r.getString("target_id"),r.getString("target_owner_id"),
        UgcReportReason.valueOf(r.getString("reason")),r.getString("note"),r.getString("evidence_text"),r.getString("target_revision"),
        r.getString("image_reference"),r.getLong("created_at"),(r.getObject("closed_at") as? Number)?.toLong())
    override fun report(id:String)=rows("SELECT * FROM ugc_reports WHERE id=?",uuid(id),mapper=::readReport).singleOrNull()
    override fun openReport(reporterId:String,type:UgcTargetType,targetId:String)=rows("SELECT * FROM ugc_reports WHERE reporter_id=? AND target_type=? AND target_id=? AND status IN ('NEW','REVIEWING')",uuid(reporterId),type.name,uuid(targetId),mapper=::readReport).singleOrNull()
    override fun putReport(value:ModerationReport) {
        execute("""INSERT INTO ugc_reports(id,version,status,reporter_id,target_type,target_id,target_owner_id,reason,note,evidence_text,target_revision,image_reference,created_at,closed_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET version=EXCLUDED.version,status=EXCLUDED.status,
            reporter_id=EXCLUDED.reporter_id,target_id=EXCLUDED.target_id,target_owner_id=EXCLUDED.target_owner_id,
            note=EXCLUDED.note,evidence_text=EXCLUDED.evidence_text,target_revision=EXCLUDED.target_revision,image_reference=EXCLUDED.image_reference,closed_at=EXCLUDED.closed_at""",
            uuid(value.id),value.version,value.status.name,value.reporterId?.let(::uuid),value.targetType.name,value.targetId?.let(::uuid),
            value.targetOwnerId?.let(::uuid),value.reason.name,value.note,value.evidenceText,value.targetRevision,value.imageReference,value.createdAtEpochMillis,value.closedAtEpochMillis)
    }
    override fun reports(status:ModerationStatus?,limit:Int,offset:Int):List<ModerationReport> {
        require(limit in 1..100 && offset>=0)
        return if(status==null) rows("SELECT * FROM ugc_reports ORDER BY created_at DESC,id LIMIT ? OFFSET ?",limit,offset,mapper=::readReport)
        else rows("SELECT * FROM ugc_reports WHERE status=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",status.name,limit,offset,mapper=::readReport)
    }
    override fun reportCount(status:ModerationStatus?)=if(status==null) rows("SELECT COUNT(*) FROM ugc_reports"){it.getLong(1)}.single()
        else rows("SELECT COUNT(*) FROM ugc_reports WHERE status=?",status.name){it.getLong(1)}.single()
    override fun replay(actorId:String,requestId:String)=rows("SELECT * FROM ugc_request_replays WHERE actor_id=? AND request_id=?",uuid(actorId),requestId) {
        ModerationReplay(it.getString("actor_id"),it.getString("request_id"),it.getString("fingerprint"),it.getString("response_type"),it.getString("response_json"),it.getString("report_id"),it.getLong("expires_at"))
    }.singleOrNull()
    override fun putReplay(value:ModerationReplay) {
        val subject=if(value.responseType=="RESTRICTION") Json.decodeFromString<ModerationRestrictionView>(value.responseJson).userId else null
        execute("""INSERT INTO ugc_request_replays(actor_id,request_id,fingerprint,response_type,response_json,report_id,subject_user_id,expires_at)
            VALUES (?,?,?,?,?,?,?,?)""",uuid(value.actorId),value.requestId,value.fingerprint,value.responseType,value.responseJson,value.reportId?.let(::uuid),subject?.let(::uuid),value.expiresAtEpochMillis)
    }
    override fun rateCount(playerId:String,since:Long)=rows("SELECT COUNT(*) FROM ugc_report_rate_events WHERE user_id=? AND created_at>?",uuid(playerId),since){it.getInt(1)}.single()
    override fun addRateEvent(playerId:String,nowMillis:Long) {execute("INSERT INTO ugc_report_rate_events(user_id,created_at) VALUES (?,?)",uuid(playerId),nowMillis)}
    override fun suppression(type:UgcTargetType,targetId:String,revision:String?)=if(revision==null)
        rows("SELECT 1 FROM ugc_content_suppressions WHERE target_type=? AND target_id=? LIMIT 1",type.name,uuid(targetId)){true}.isNotEmpty()
        else rows("SELECT 1 FROM ugc_content_suppressions WHERE target_type=? AND target_id=? AND revision=?",type.name,uuid(targetId),revision){true}.isNotEmpty()
    override fun suppressions()=rows("SELECT * FROM ugc_content_suppressions") {
        ContentSuppression(UgcTargetType.valueOf(it.getString("target_type")),it.getString("target_id"),it.getString("revision"),it.getString("owner_id"),it.getLong("created_at"),it.getString("name_revision"))
    }
    override fun putSuppression(value:ContentSuppression) {
        execute("INSERT INTO ugc_content_suppressions(target_type,target_id,revision,owner_id,created_at,name_revision) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
            value.targetType.name,uuid(value.targetId),value.revision,value.ownerId?.let(::uuid),value.createdAtEpochMillis,value.nameRevision)
        value.ownerId?.takeIf(::playerExists)?.let {owner ->
            JdbcNotificationPersistence(connection).create(owner,listOf(NotificationSnapshot("moderation:${value.targetType}:${value.targetId}:${value.revision}",NotificationKind.COSMETIC,
                "Content removed", "Public content was removed for violating community rules. Contact support for review.",
                value.createdAtEpochMillis,destination=NotificationDestination.PROFILE,
                titleKey="UgcContentRemovedTitle",messageKey="UgcContentRemovedBody")))
        }
    }
    override fun restriction(userId:String):ModerationRestrictionView=rows("SELECT version,blocked FROM ugc_account_restrictions WHERE user_id=?",uuid(userId)) {
        ModerationRestrictionView(userId,it.getInt(1),it.getBoolean(2))
    }.singleOrNull() ?: ModerationRestrictionView(userId,blocked=rows("SELECT status FROM users WHERE id=?",uuid(userId)){it.getString(1)=="BLOCKED"}.singleOrNull()==true)
    override fun putRestriction(value:ModerationRestrictionView,nowMillis:Long) {
        // Parent user row is locked before sessions in every auth mutation.
        if(rows("SELECT id FROM users WHERE id=? AND status<>'DELETED' FOR UPDATE",uuid(value.userId)){it.getString(1)}.isEmpty()) missing()
        execute("UPDATE users SET status=?,updated_at=NOW() WHERE id=?",if(value.blocked) "BLOCKED" else "ACTIVE",uuid(value.userId))
        execute("""INSERT INTO ugc_account_restrictions(user_id,version,blocked,updated_at) VALUES (?,?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET version=EXCLUDED.version,blocked=EXCLUDED.blocked,updated_at=EXCLUDED.updated_at""",
            uuid(value.userId),value.version,value.blocked,nowMillis)
        if(value.blocked) execute("UPDATE sessions SET revoked_at=NOW() WHERE user_id=? AND revoked_at IS NULL",uuid(value.userId))
    }
    override fun erasePlayer(playerId:String) {
        val id=uuid(playerId)
        execute("DELETE FROM ugc_moderation_actions WHERE subject_user_id=? OR report_id IN (SELECT id FROM ugc_reports WHERE reporter_id=? OR target_owner_id=? OR target_id=?)",id,id,id,id)
        execute("DELETE FROM ugc_request_replays WHERE actor_id=? OR subject_user_id=? OR report_id IN (SELECT id FROM ugc_reports WHERE reporter_id=? OR target_owner_id=? OR target_id=?)",id,id,id,id,id)
        execute("""UPDATE ugc_reports SET reporter_id=CASE WHEN reporter_id=? THEN NULL ELSE reporter_id END,
            target_id=CASE WHEN target_owner_id=? OR target_id=? THEN NULL ELSE target_id END,
            target_owner_id=CASE WHEN target_owner_id=? THEN NULL ELSE target_owner_id END,
            note='',evidence_text='',target_revision=NULL,image_reference=NULL
            WHERE reporter_id=? OR target_owner_id=? OR target_id=?""",id,id,id,id,id,id,id)
        execute("DELETE FROM ugc_content_terms WHERE user_id=?",id)
        execute("DELETE FROM ugc_report_rate_events WHERE user_id=?",id)
        execute("DELETE FROM ugc_account_restrictions WHERE user_id=?",id)
        execute("DELETE FROM ugc_content_suppressions WHERE owner_id=? OR target_id=?",id,id)
    }
    override fun cleanup(closedBefore:Long,nowMillis:Long):Int {
        val removed=execute("DELETE FROM ugc_reports WHERE closed_at<?",closedBefore)
        execute("DELETE FROM ugc_request_replays WHERE report_id IS NULL AND expires_at<?",nowMillis)
        execute("DELETE FROM ugc_moderation_actions WHERE expires_at<?",nowMillis)
        execute("DELETE FROM ugc_report_rate_events WHERE created_at<=?",nowMillis-86_400_000)
        return removed
    }
}
