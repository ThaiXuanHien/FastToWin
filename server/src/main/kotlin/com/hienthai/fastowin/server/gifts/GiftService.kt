package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.hashToken
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class GiftService(val repository: GiftRepository, private val admins: AdminService? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newIntentAllowed: () -> Boolean = { true }) {
    fun receivedAt() = nowMillis()
    fun requireOrigin(origin: String?) { (admins ?: unavailable()).requireOrigin(origin) }
    private fun GiftTransaction.authorize(context: GiftAdminContext, mutation: Boolean = false, owner: Boolean = false): AdminIdentity {
        val service = admins ?: unavailable()
        context.origin?.let(service::requireOrigin)
        if (mutation) service.requireOrigin(context.origin)
        val actor = service.authorize(admin, context.token, context.csrfToken, mutation, owner)
        if (mutation && actor.role == AdminRole.SUPPORT) forbidden()
        return actor
    }
    private fun GiftTransaction.audit(actor: String, action: String, id: String? = null, request: String? = null, reason: String? = null) =
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), actor, action, id, request, reason, null, null, nowMillis()))
    private fun GiftTransaction.detail(c: GiftCampaign) = GiftCampaignDetail(c, counters(c, nowMillis()))
    suspend fun list(context: GiftAdminContext, page: PageRequest): AdminPage<GiftCampaignDetail> = repository.transaction(true) {
        val actor = authorize(context); val result = campaigns(page)
        audit(actor.id, "GIFT_LIST")
        AdminPage(result.items.map { detail(it) }, result.page, result.pageSize, result.total)
    }
    suspend fun detail(context: GiftAdminContext, id: String): GiftCampaignDetail = repository.transaction(true) {
        val actor = authorize(context); uuid(id); val c = ordinaryCampaign(id); audit(actor.id, "GIFT_DETAIL", id); detail(c)
    }
    suspend fun create(context: GiftAdminContext, request: GiftDraftRequest): GiftCampaignDetail = repository.transaction(true) {
        val actor = authorize(context, true); mutation(request.reason, request.requestId)
        val fp = hashToken("create:" + Json.encodeToString(request))
        replay(actor.id, request.requestId, fp)?.let { return@transaction it }
        if (request.expectedVersion != null) invalid()
        val draft = validated(request)
        val c = GiftCampaign(UUID.randomUUID().toString(), 1, CampaignState.DRAFT, draft, nowMillis())
        putCampaign(c); audit(actor.id, "GIFT_CREATE", c.id, request.requestId, request.reason)
        save(actor.id, request.requestId, fp, detail(c))
    }
    suspend fun update(context: GiftAdminContext, id: String, request: GiftDraftRequest): GiftCampaignDetail = repository.transaction(true) {
        val actor = authorize(context, true); uuid(id); mutation(request.reason, request.requestId)
        val fp = hashToken("update:$id:" + Json.encodeToString(request))
        val c = ordinaryCampaign(id, true)
        replay(actor.id, request.requestId, fp)?.let { return@transaction it }
        if (c.state != CampaignState.DRAFT || c.version != request.expectedVersion) conflict()
        val updated = c.copy(version = Math.addExact(c.version, 1), draft = validated(request))
        putCampaign(updated); audit(actor.id, "GIFT_UPDATE", id, request.requestId, request.reason)
        save(actor.id, request.requestId, fp, detail(updated))
    }
    private data class Audience(val users: List<String>, val invalid: List<String>, val duplicates: Int)
    private fun GiftTransaction.audience(c: GiftCampaign): Audience {
        val draft = c.draft
        val registered = registeredPlayers()
        val ids = registered.map { it.id }.toSet()
        val requested = draft.userIds.distinct()
        val selected = when (draft.audience) {
            GiftAudience.ALL_REGISTERED -> ids.toList()
            GiftAudience.USER_IDS -> requested.filter { it in ids }
            GiftAudience.REGISTERED_BETWEEN -> registered.filter { it.registeredAt >= draft.registeredFromEpochMillis!! && it.registeredAt < draft.registeredUntilEpochMillis!! }.map { it.id }
        }.sorted()
        return Audience(selected, if (draft.audience == GiftAudience.USER_IDS) requested.filter { it !in ids }.sorted() else emptyList(), draft.userIds.size - requested.size)
    }
    private fun preview(c: GiftCampaign, a: Audience): GiftPreview {
        val token = hashToken(Json.encodeToString(c) + "|" + a.users.joinToString(",") + "|" + a.invalid.joinToString(","))
        return GiftPreview(c.id, c.version, a.users.size.toLong(), a.duplicates, a.invalid,
            Math.multiplyExact(c.draft.gold.toLong(), a.users.size.toLong()), Math.multiplyExact(c.draft.xp.toLong(), a.users.size.toLong()), token)
    }
    suspend fun preview(context: GiftAdminContext, id: String, request: GiftPreviewRequest): GiftPreview = repository.transaction(true) {
        val actor = authorize(context, true); uuid(id); val c = ordinaryCampaign(id, true)
        if (c.state != CampaignState.DRAFT || c.version != request.expectedVersion) conflict()
        val result = preview(c, audience(c)); audit(actor.id, "GIFT_PREVIEW", id); result
    }
    suspend fun publish(context: GiftAdminContext, id: String, request: GiftPublishRequest): GiftCampaignDetail = repository.transaction(true) {
        val actor = authorize(context, true, true); uuid(id); mutation(request.reason, request.requestId)
        val fp = hashToken("publish:$id:" + Json.encodeToString(request))
        val c = ordinaryCampaign(id, true)
        replay(actor.id, request.requestId, fp)?.let { return@transaction it }
        if (c.state != CampaignState.DRAFT || c.version != request.expectedVersion) conflict()
        val audience = audience(c); val fresh = preview(c, audience)
        if (fresh.invalidUserIds.isNotEmpty()) invalid()
        if (fresh.previewToken != request.previewToken) conflict()
        val updated = c.copy(state = CampaignState.PUBLISHED, version = Math.addExact(c.version, 1), publishedAtEpochMillis = nowMillis())
        freeze(id, audience.users); putCampaign(updated); audit(actor.id, "GIFT_PUBLISH", id, request.requestId, request.reason)
        save(actor.id, request.requestId, fp, detail(updated))
    }
    suspend fun cancel(context: GiftAdminContext, id: String, request: GiftCancelRequest): GiftCampaignDetail = repository.transaction(true) {
        val actor = authorize(context, true, true); uuid(id); mutation(request.reason, request.requestId)
        val fp = hashToken("cancel:$id:" + Json.encodeToString(request))
        val c = ordinaryCampaign(id, true)
        replay(actor.id, request.requestId, fp)?.let { return@transaction it }
        if (c.state == CampaignState.CANCELLED || c.version != request.expectedVersion) conflict()
        val updated = c.copy(state = CampaignState.CANCELLED, version = Math.addExact(c.version, 1), cancelledAtEpochMillis = nowMillis(), cancellationReason = request.reason)
        putCampaign(updated); audit(actor.id, "GIFT_CANCEL", id, request.requestId, request.reason)
        save(actor.id, request.requestId, fp, detail(updated))
    }
    private fun GiftTransaction.ordinaryCampaign(id: String, lock: Boolean = false) = campaign(id, lock)?.takeIf { it.compensation == null } ?: missing()
    private fun GiftTransaction.replay(actor: String, requestId: String, fp: String): GiftCampaignDetail? {
        val record = request(actor, requestId) ?: return null
        if (record.fingerprint != fp) conflict()
        return record.result
    }
    private fun GiftTransaction.save(actor: String, requestId: String, fp: String, result: GiftCampaignDetail): GiftCampaignDetail {
        putRequest(actor, requestId, GiftRequestRecord(fp, result)); return result
    }
    suspend fun deliverDueBatch(limit: Int = 100): Int {
        if (limit !in 1..1000) invalid()
        val now = nowMillis()
        return repository.transaction {
            var processed = 0
            for (id in pendingCampaigns(now, limit)) {
                val c = campaign(id, true) ?: continue
                if (c.state != CampaignState.PUBLISHED || c.draft.availableAtEpochMillis > now) continue
                for (r in pendingRecipients(id, limit - processed)) {
                    putRecipient(r.copy(delivery = if (activePlayer(r.userId)) "DELIVERED" else "SKIPPED")); processed++
                }
                if (processed == limit) break
            }
            processed
        }
    }
    suspend fun mailbox(userId: String, page: PageRequest): GiftMailboxPage = repository.transaction {
        uuid(userId); if (!activePlayer(userId)) unauthorized()
        val now = nowMillis(); val (rows, total) = mailbox(userId, now, page)
        GiftMailboxPage(rows.map { (c, r) -> gift(c, r, now) }, page.page, page.pageSize, total)
    }
    suspend fun claim(userId: String, campaignId: String, receivedAt: Long = nowMillis()): GiftClaimResult = repository.transaction {
        uuid(userId); uuid(campaignId)
        // Canonical lock order: campaign -> entitlement -> user -> stats. Cancellation uses campaign lock too.
        val c = campaign(campaignId, true) ?: missing()
        val r = recipient(campaignId, userId, true)?.takeIf { it.delivery == "DELIVERED" } ?: missing()
        if (!activePlayer(userId)) unauthorized()
        if (r.claimedAt != null) {
            val current = balance(userId)
            return@transaction GiftClaimResult(gift(c, r, receivedAt), c.draft.gold, c.draft.xp, current.gold, current.xp)
        }
        if (!newIntentAllowed()) throw AdminException(503, "MAINTENANCE",
            com.hienthai.fastowin.localization.LocalizationService(com.hienthai.fastowin.localization.AppLanguage.VIETNAMESE)
                .text(com.hienthai.fastowin.localization.TextKey.MaintenanceDefaultMessage))
        if (c.state != CampaignState.PUBLISHED || receivedAt < c.draft.availableAtEpochMillis || receivedAt >= c.draft.expiresAtEpochMillis!!) conflict()
        val current = award(userId, c, receivedAt)
        val claimed = r.copy(claimedAt = receivedAt); putRecipient(claimed)
        GiftClaimResult(gift(c, claimed, receivedAt), c.draft.gold, c.draft.xp, current.gold, current.xp)
    }
    suspend fun claimAll(userId: String, request: GiftClaimAllRequest, receivedAt: Long = nowMillis()): GiftClaimAllResult {
        if (request.campaignIds.size !in 1..100) invalid()
        return GiftClaimAllResult(request.campaignIds.map { id ->
            try { GiftClaimItem(id, result = claim(userId, id, receivedAt)) }
            catch (error: AdminException) { GiftClaimItem(id, error = GiftError(error.code, error.message)) }
        })
    }
    private fun validated(request: GiftDraftRequest): GiftDraftRequest {
        if (request.titleVi.isBlank() || request.titleEn.isBlank() || request.titleVi.length > 160 || request.titleEn.length > 160 ||
            request.bodyVi.isBlank() || request.bodyEn.isBlank() || request.bodyVi.length > 4000 || request.bodyEn.length > 4000 ||
            request.gold < 0 || request.xp < 0 || (request.gold == 0 && request.xp == 0) || request.userIds.size > 10000) invalid()
        val expiry = try { request.expiresAtEpochMillis ?: Math.addExact(request.availableAtEpochMillis, 30L * 24 * 60 * 60 * 1000) } catch (_: ArithmeticException) { invalid() }
        if (request.availableAtEpochMillis < 0 || expiry <= request.availableAtEpochMillis || expiry > 253402300799999L) invalid()
        if (request.audience == GiftAudience.USER_IDS) {
            if (request.userIds.isEmpty()) invalid()
            request.userIds.forEach(::uuid)
        } else if (request.userIds.isNotEmpty()) invalid()
        if (request.audience == GiftAudience.REGISTERED_BETWEEN) {
            val from = request.registeredFromEpochMillis ?: invalid(); val until = request.registeredUntilEpochMillis ?: invalid()
            if (from < 0 || until <= from) invalid()
        } else if (request.registeredFromEpochMillis != null || request.registeredUntilEpochMillis != null) invalid()
        return request.copy(expiresAtEpochMillis = expiry, userIds = request.userIds.map { it.lowercase() })
    }
    private fun mutation(reason: String, request: String) {
        if (reason.isBlank() || reason.length > 500 || reason.any(Char::isISOControl) || !request.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid()
    }
}
internal fun uuid(id: String) { if (runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false).not()) invalid() }
internal fun gift(c: GiftCampaign, r: GiftRecipient, now: Long) = MailboxGift(c.id, c.draft.titleVi, c.draft.titleEn, c.draft.bodyVi, c.draft.bodyEn,
    c.draft.gold, c.draft.xp, c.draft.availableAtEpochMillis, c.draft.expiresAtEpochMillis!!,
    when { r.claimedAt != null -> GiftState.CLAIMED; c.state == CampaignState.CANCELLED -> GiftState.CANCELLED; now >= c.draft.expiresAtEpochMillis -> GiftState.EXPIRED; else -> GiftState.AVAILABLE }, r.claimedAt, c.compensation)
