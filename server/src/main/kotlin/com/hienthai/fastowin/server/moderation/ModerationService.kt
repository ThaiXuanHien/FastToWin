package com.hienthai.fastowin.server.moderation

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ModerationService(
    val repository: ModerationRepository,
    private val admins: AdminService? = null,
    private val enforcementEnabled: () -> Boolean = { false },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /**
     * SCL-01: game sockets read suppressions and block state on every message and delivery. With a
     * positive value those hot reads are cached this long; local admin changes invalidate at once,
     * so the value only bounds staleness for changes made elsewhere (direct SQL, another backend).
     */
    val hotReadCacheMillis: Long = 0
) {
    private class Cached<T>(val atMillis: Long, val value: T)
    @Volatile private var cachedSuppressions: Cached<List<ContentSuppression>>? = null
    private val cachedBlocks = java.util.concurrent.ConcurrentHashMap<String, Cached<Boolean>>()
    private fun fresh(cached: Cached<*>?) = cached != null && hotReadCacheMillis > 0 &&
        nowMillis() - cached.atMillis in 0 until hotReadCacheMillis
    fun invalidateHotReads() { cachedSuppressions = null; cachedBlocks.clear() }
    /** Memory notification delivery runs after commit and uses reportId as a stable deduplication key. */
    var onContentRemoved: suspend (ownerId: String, reportId: String) -> Unit = { _, _ -> }
    var onSuppressionChanged: suspend (type: UgcTargetType, targetId: String) -> Unit = { _, _ -> }
    /** Applies an admin chat mute decided while resolving a clan chat report. */
    var onChatMuteRequested: suspend (messageId: String, untilMillis: Long) -> Unit = { _, _ -> }
    fun validateAcceptedVersion(version: String?) {
        if (version != null && version != CONTENT_TERMS_VERSION) moderationError(400,"CONTENT_TERMS_VERSION_INVALID")
        if (version == null && enforcementEnabled()) moderationError(403,"CONTENT_TERMS_REQUIRED")
    }
    suspend fun registerIdentity(playerId: String) = repository.transaction { registerPlayer(playerId) }
    suspend fun terms(playerId: String): ContentTermsStatus = repository.read {
        if (!playerExists(playerId)) unauthorized()
        ContentTermsStatus(CONTENT_TERMS_VERSION,acceptedVersion(playerId),enforcementEnabled())
    }
    fun publicTerms() = ContentTermsStatus(CONTENT_TERMS_VERSION,null,enforcementEnabled())
    suspend fun acceptTerms(playerId: String, version: String): ContentTermsStatus = repository.transaction {
        if (version != CONTENT_TERMS_VERSION) moderationError(400,"CONTENT_TERMS_VERSION_INVALID")
        if (!playerExists(playerId)) unauthorized()
        acceptTerms(playerId,version,nowMillis())
        ContentTermsStatus(CONTENT_TERMS_VERSION,version,enforcementEnabled())
    }
    suspend fun requireContentTerms(playerId: String) {
        if (enforcementEnabled()) repository.transaction {
            if (!playerExists(playerId)) unauthorized()
            if (acceptedVersion(playerId) != CONTENT_TERMS_VERSION) moderationError(403,"CONTENT_TERMS_REQUIRED")
        }
    }
    private fun canonical(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    private fun validateRequest(id: String) { if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid() }
    private fun validateReason(reason: String) {
        if(reason.isBlank() || reason.length>500 || reason.any(Char::isISOControl)) invalid()
    }
    suspend fun report(playerId: String, request: ContentReportRequest): ContentReportReceipt = repository.transaction {
        if(!canonical(playerId) || !playerExists(playerId)) unauthorized()
        if(restriction(playerId).blocked) forbidden()
        validateRequest(request.requestId)
        if(!canonical(request.targetId) || request.note.length>500 || request.note.any { it.isISOControl() && it != '\n' && it != '\t' }) invalid()
        val fingerprint=hashToken("report:${Json.encodeToString(request)}")
        replay(playerId,request.requestId)?.let {
            if(it.fingerprint!=fingerprint || it.responseType!="REPORT") conflict()
            return@transaction Json.decodeFromString<ContentReportReceipt>(it.responseJson)
        }
        val target=target(request.targetType,request.targetId) ?: missing()
        val existing=openReport(playerId,request.targetType,request.targetId)
        val now=nowMillis()
        // An exact request replay returned above is free; every NEW request ID is rate bounded,
        // including duplicates, so callers cannot grow the receipt cache without limit.
        if(rateCount(playerId,now-600_000)>=5 || rateCount(playerId,now-86_400_000)>=20)
            moderationError(429,"REPORT_RATE_LIMITED")
        val receipt=if(existing!=null) ContentReportReceipt(existing.id,true) else {
            val value=ModerationReport(UUID.randomUUID().toString(),1,ModerationStatus.NEW,playerId,
                target.type,target.id,target.ownerId,request.reason,request.note,target.text.take(8_000),target.revision,
                target.imageReference,now)
            putReport(value)
            ContentReportReceipt(value.id)
        }
        addRateEvent(playerId,now)
        putReplay(ModerationReplay(playerId,request.requestId,fingerprint,"REPORT",Json.encodeToString(receipt),receipt.reportId,now+91L*86_400_000))
        receipt
    }
    suspend fun isBlocked(playerId: String): Boolean {
        cachedBlocks[playerId]?.takeIf(::fresh)?.let { return it.value }
        val loadedAt = nowMillis()
        return repository.read { restriction(playerId).blocked }.also { remember(playerId, it, loadedAt) }
    }
    /** Block state for many sockets in one read; refreshes the hot cache for each of them. */
    suspend fun blockedAmong(playerIds: Collection<String>): Set<String> {
        if (playerIds.isEmpty()) return emptySet()
        val loadedAt = nowMillis()
        val blocked = repository.read { blockedUserIds(playerIds) }
        playerIds.forEach { remember(it, it in blocked, loadedAt) }
        return blocked
    }
    private fun remember(playerId: String, blocked: Boolean, loadedAt: Long) {
        if (hotReadCacheMillis <= 0) return
        if (cachedBlocks.size >= MAX_CACHED_BLOCKS) cachedBlocks.clear()
        cachedBlocks[playerId] = Cached(loadedAt, blocked)
    }
    suspend fun isSuppressed(type: UgcTargetType, targetId: String, revision: String? = null) = repository.read { suppression(type,targetId,revision) }
    suspend fun suppressions(): List<ContentSuppression> {
        cachedSuppressions?.takeIf(::fresh)?.let { return it.value }
        val loadedAt = nowMillis()
        return repository.read { suppressions() }.also { if (hotReadCacheMillis > 0) cachedSuppressions = Cached(loadedAt, it) }
    }
    suspend fun erasePlayer(playerId: String) = try { repository.transaction { erasePlayer(playerId) } } finally { invalidateHotReads() }
    suspend fun cleanup(): Int = repository.transaction { cleanup(nowMillis()-90L*86_400_000,nowMillis()) }

    private fun requireAdmins() = admins ?: missing()
    private fun ModerationTransaction.view(value: ModerationReport): ModerationAdminView {
        val current=value.targetId?.let { target(value.targetType,it) }
        return ModerationAdminView(ModerationReportView(value.id,value.version,value.status,value.targetType,value.targetId,
            value.reason,value.note,value.evidenceText,value.targetRevision,value.createdAtEpochMillis,value.closedAtEpochMillis),
            current!=null && current.revision!=value.targetRevision,current==null,
            current?.ownerId,current?.ownerId?.let { restriction(it) },lastActionReason(value.id))
    }
    suspend fun list(context: ModerationAdminContext,page: PageRequest=PageRequest(),status: ModerationStatus?=null): ModerationAdminPage = repository.transaction {
        requireAdmins().authorize(admin,context.token)
        ModerationAdminPage(reports(status,page.pageSize,page.offset).map { view(it) },page.page,page.pageSize,reportCount(status).toString())
    }
    suspend fun detail(context: ModerationAdminContext,id: String): ModerationAdminView = repository.transaction {
        requireAdmins().authorize(admin,context.token)
        if(!canonical(id)) invalid()
        view(report(id) ?: missing())
    }
    private fun ModerationTransaction.audit(actorId: String, action: String) {
        // Long-lived audit keeps only admin actor and action; no player ID, notes, evidence, request or reason.
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(),actorId,action,null,null,null,null,null,nowMillis()))
    }
    suspend fun resolve(context: ModerationAdminContext,id: String,request: ModerationResolveRequest): ModerationAdminView {
        val result = repository.transaction {
            val service=requireAdmins();service.requireOrigin(context.origin)
            val actor=service.authorize(admin,context.token,context.csrfToken,mutation=true)
            validateRequest(request.requestId);validateReason(request.reason)
            if(!canonical(id) || request.expectedVersion<=0 || request.status==ModerationStatus.NEW ||
                (request.removeContent && request.status!=ModerationStatus.RESOLVED)) invalid()
            if(actor.role==AdminRole.SUPPORT && (request.status!=ModerationStatus.REVIEWING || request.removeContent || request.chatMuteHours!=0)) forbidden()
            if(request.chatMuteHours !in ADMIN_CHAT_MUTE_HOURS) invalid()
            val fingerprint=hashToken("resolve:$id:${Json.encodeToString(request)}")
            replay(actor.id,request.requestId)?.let {
                if(it.fingerprint!=fingerprint || it.responseType!="ADMIN_REPORT") conflict()
                // Re-project the current report instead of persisting raw evidence in a response cache.
                return@transaction view(report(it.reportId ?: conflict()) ?: missing())
            }
            val current=report(id) ?: missing()
            if(current.version!=request.expectedVersion || current.status.closed() || current.version==Int.MAX_VALUE) conflict()
            if(request.chatMuteHours>0 && (current.targetType!=UgcTargetType.CLAN_MESSAGE || request.status!=ModerationStatus.RESOLVED ||
                current.targetId==null)) invalid()
            if(request.removeContent) {
                val target=current.targetId?.let { target(current.targetType,it) } ?: conflict()
                if(target.revision!=current.targetRevision) conflict()
                putSuppression(ContentSuppression(target.type,target.id,target.revision,target.ownerId,nowMillis(),
                    if(target.type==UgcTargetType.PLAYER_AVATAR || target.type==UgcTargetType.CLAN_LOGO) null else contentRevision(target.text.substringBefore('\n'))))
            }
            val next=current.copy(version=current.version+1,status=request.status,
                closedAtEpochMillis=if(request.status.closed()) nowMillis() else null)
            putReport(next)
            val action=if(request.removeContent) "MODERATION_REMOVE" else "MODERATION_${request.status}"
            appendAction(ModerationActionRecord(UUID.randomUUID().toString(),id,current.targetOwnerId,actor.id,action,request.reason,nowMillis()))
            audit(actor.id,action)
            if(request.chatMuteHours>0) {
                appendAction(ModerationActionRecord(UUID.randomUUID().toString(),id,current.targetOwnerId,actor.id,"MODERATION_CHAT_MUTE",request.reason,nowMillis()))
                audit(actor.id,"MODERATION_CHAT_MUTE")
            }
            putReplay(ModerationReplay(actor.id,request.requestId,fingerprint,"ADMIN_REPORT","",id,nowMillis()+91L*86_400_000))
            view(next)
        }
        invalidateHotReads()
        if(request.removeContent && repository is InMemoryModerationRepository) result.targetOwnerId?.let {
            try { onContentRemoved(it,id) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { /* Committed moderation must still invalidate public content. */ }
        }
        if(request.chatMuteHours>0) result.report.targetId?.let {
            try { onChatMuteRequested(it,nowMillis()+request.chatMuteHours*3_600_000L) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { System.err.println("Clan chat mute from moderation failed") }
        }
        if(request.removeContent) result.report.targetId?.let {
            try { onSuppressionChanged(result.report.targetType,it) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { /* A delivery failure cannot undo the committed result. */ }
        }
        return result
    }
    suspend fun userRestriction(context: ModerationAdminContext,userId: String): ModerationRestrictionView = repository.transaction {
        requireAdmins().authorize(admin,context.token)
        if(!canonical(userId)) invalid()
        if(!playerExists(userId)) missing()
        restriction(userId)
    }
    suspend fun restrict(context: ModerationAdminContext,userId: String,request: ModerationRestrictionRequest): ModerationRestrictionView =
        try { restrictCommitted(context,userId,request) } finally { invalidateHotReads() }
    private suspend fun restrictCommitted(context: ModerationAdminContext,userId: String,request: ModerationRestrictionRequest): ModerationRestrictionView = repository.transaction {
        val service=requireAdmins();service.requireOrigin(context.origin)
        val actor=service.authorize(admin,context.token,context.csrfToken,mutation=true)
        if(actor.role==AdminRole.SUPPORT) forbidden()
        validateRequest(request.requestId);validateReason(request.reason)
        if(!canonical(userId) || request.expectedVersion<0) invalid()
        val fingerprint=hashToken("restrict:$userId:${Json.encodeToString(request)}")
        replay(actor.id,request.requestId)?.let {
            if(it.fingerprint!=fingerprint || it.responseType!="RESTRICTION") conflict()
            return@transaction Json.decodeFromString<ModerationRestrictionView>(it.responseJson)
        }
        if(!playerExists(userId)) missing()
        val current=restriction(userId)
        if(current.version!=request.expectedVersion || current.version==Int.MAX_VALUE) conflict()
        val next=current.copy(version=current.version+1,blocked=request.blocked)
        putRestriction(next,nowMillis())
        val action=if(next.blocked) "MODERATION_BLOCK" else "MODERATION_UNBLOCK"
        appendAction(ModerationActionRecord(UUID.randomUUID().toString(),null,userId,actor.id,action,request.reason,nowMillis(),nowMillis()+90L*86_400_000))
        audit(actor.id,action)
        putReplay(ModerationReplay(actor.id,request.requestId,fingerprint,"RESTRICTION",Json.encodeToString(next),null,nowMillis()+86_400_000))
        next
    }
    private companion object { const val MAX_CACHED_BLOCKS = 100_000 }
}
