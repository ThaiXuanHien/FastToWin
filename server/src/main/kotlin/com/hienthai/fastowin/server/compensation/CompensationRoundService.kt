package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*
import com.hienthai.fastowin.server.hashToken
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** All authority and replay writes run on one transaction, after current admin authorization. */
class CompensationRoundService(val repository: GiftRepository, private val admins: AdminService,
    private val nowMillis: () -> Long = System::currentTimeMillis) {
    fun requireOrigin(origin: String?) = admins.requireOrigin(origin)
    private fun GiftTransaction.authorize(context: GiftAdminContext, mutation: Boolean = false, owner: Boolean = false): AdminIdentity {
        context.origin?.let(admins::requireOrigin)
        if (mutation) admins.requireOrigin(context.origin)
        val actor = admins.authorize(admin, context.token, context.csrfToken, mutation, owner)
        if (mutation && actor.role == AdminRole.SUPPORT) forbidden()
        return actor
    }
    private fun canonical(id: String): String { uuid(id); return id.lowercase(java.util.Locale.ROOT) }
    private fun guards(incidentVersion: Int, version: Int?, requestId: String, reason: String) {
        if (incidentVersion <= 0 || version?.let { it <= 0 } == true || reason.isBlank() || reason.length > 500 ||
            reason.any(Char::isISOControl) || !requestId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid()
    }
    private fun supplemental(number: Int, reason: String?) {
        if (number <= 0 || (number == 1 && reason != null) || (number > 1 &&
            (reason == null || reason.isBlank() || reason.length > 500 || reason.any(Char::isISOControl)))) invalid()
    }
    private fun nextVersion(version: Int): Int { if (version == Int.MAX_VALUE) conflict(); return version + 1 }
    private fun GiftTransaction.audit(actor: AdminIdentity, action: String, id: String?, requestId: String? = null,
        reason: String? = null, before: Int? = null, after: Int? = null) {
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), actor.id, action, id, requestId, reason,
            before?.let { "version=$it" }, after?.let { "version=$it" }, nowMillis()))
    }
    private inline fun <reified T> GiftTransaction.replay(actor: String, request: String, fingerprint: String, type: String): T? {
        val receipt = compensation.replay(actor, request) ?: return null
        if (receipt.fingerprint != fingerprint || receipt.responseType != type) conflict()
        return Json.decodeFromString<T>(receipt.responseJson)
    }
    private inline fun <reified T> GiftTransaction.save(actor: String, request: String, fingerprint: String, type: String, result: T): T {
        compensation.putReplay(actor, request, CompensationReplay(fingerprint, type, Json.encodeToString(result))); return result
    }
    private fun GiftTransaction.incident(id: String, version: Int? = null, lock: Boolean = false): CompensationIncident {
        val i = compensation.incident(id, lock) ?: missing()
        if (version != null && i.version != version) conflict()
        return i
    }
    private fun GiftTransaction.mapped(i: CompensationIncident, id: String, lock: Boolean = false): Pair<CompensationRound, GiftCampaign> {
        val r = compensation.round(id)?.takeIf { it.incidentId == i.id } ?: missing()
        val c = campaign(id, lock)?.takeIf { it.compensation?.incidentId == i.id && it.compensation.roundNumber == r.roundNumber } ?: missing()
        return r to c
    }
    private fun audience(r: CompensationRound, c: GiftCampaign): CompensationAudience = r.audience ?:
        if (c.draft.audience == GiftAudience.USER_IDS) CompensationAudience.USER_IDS else CompensationAudience.ALL_REGISTERED
    private fun GiftTransaction.verifyActivity(i: CompensationIncident, source: CompensationAudience) {
        if (source == CompensationAudience.ACTIVITY)
            activityRecipients(i.affectedFromEpochMillis, i.affectedUntilEpochMillis, nowMillis())
    }
    // Incident edits before first publication are reflected in draft projections and the frozen publish snapshot.
    private fun content(i: CompensationIncident, r: CompensationRound, c: GiftCampaign): GiftCampaign {
        if (c.state != CampaignState.DRAFT) return c
        val d = c.draft
        val request = CompensationRoundRequest(i.version, r.receivingReasonVi, r.receivingReasonEn, d.gold, d.xp,
            d.availableAtEpochMillis, d.requestId, d.reason, audience(r, c),
            d.userIds, d.expiresAtEpochMillis, supplementalReason = r.supplementalReason, expectedVersion = d.expectedVersion)
        return c.copy(draft = CompensationGiftContent.draft(i, request), compensation = CompensationGiftContent.context(i, r))
    }
    private fun GiftTransaction.result(i: CompensationIncident, r: CompensationRound, c: GiftCampaign): CompensationRoundDetail {
        val counts = GiftCampaignOperations.detail(this, c, nowMillis()).counters
        return CompensationRoundDetail(i.id, i.incidentKey, i.version, r.copy(audience = audience(r, c)), content(i, r, c), CompensationReport(
            counts.eligible.toString(), counts.pending.toString(), counts.delivered.toString(), counts.claimed.toString(), counts.expired.toString(),
            counts.cancelled.toString(), counts.skipped.toString(), counts.claimedGold.toString(), counts.claimedXp.toString()))
    }
    suspend fun list(context: GiftAdminContext, incidentId: String, page: PageRequest): CompensationPage<CompensationRoundDetail> = repository.transaction(true) {
        val actor = authorize(context); val i = incident(canonical(incidentId)); val rows = compensation.rounds(i.id, page)
        val items = rows.items.map { r -> result(i, r, mapped(i, r.campaignId).second) }
        audit(actor, "COMPENSATION_ROUND_LIST", i.id); CompensationPage(items, rows.page, rows.pageSize, rows.total)
    }
    suspend fun detail(context: GiftAdminContext, incidentId: String, id: String): CompensationRoundDetail = repository.transaction(true) {
        val actor = authorize(context); val i = incident(canonical(incidentId)); val (r, c) = mapped(i, canonical(id))
        val result = result(i, r, c); audit(actor, "COMPENSATION_ROUND_DETAIL", c.id); result
    }
    suspend fun create(context: GiftAdminContext, incidentId: String, request: CompensationRoundRequest): CompensationRoundDetail = repository.transaction(true) {
        val actor = authorize(context, true); val target = canonical(incidentId)
        guards(request.incidentVersion, request.expectedVersion, request.requestId, request.reason)
        if (request.expectedVersion != null) invalid()
        val number = request.expectedNextRoundNumber ?: invalid(); supplemental(number, request.supplementalReason)
        val i = incident(target, lock = true); val draft = CompensationGiftContent.draft(i, request)
        val fp = hashToken("round-create:$target:" + Json.encodeToString(request))
        replay<CompensationRoundDetail>(actor.id, request.requestId, fp, "ROUND_DETAIL")?.let { return@transaction it }
        if (i.version != request.incidentVersion) conflict()
        val maximum = compensation.maximumRoundNumber(i.id)
        if (maximum == Int.MAX_VALUE || number != maximum + 1 || compensation.hasDraftRound(i.id)) conflict()
        verifyActivity(i, request.audience)
        val now = nowMillis()
        val r = CompensationRound(UUID.randomUUID().toString(), i.id, number, request.supplementalReason, request.receivingReasonVi, request.receivingReasonEn,
            actor.id, actor.id, now, now, audience = request.audience)
        val c = GiftCampaign(r.campaignId, 1, CampaignState.DRAFT, draft, nowMillis(), compensation = CompensationGiftContent.context(i, r))
        putCampaign(c); compensation.putRound(r); audit(actor, "COMPENSATION_ROUND_CREATE", c.id, request.requestId, request.reason, after = 1)
        save(actor.id, request.requestId, fp, "ROUND_DETAIL", result(i, r, c))
    }
    suspend fun update(context: GiftAdminContext, incidentId: String, id: String, request: CompensationRoundRequest): CompensationRoundDetail = repository.transaction(true) {
        val actor = authorize(context, true); val target = canonical(incidentId); val roundId = canonical(id)
        guards(request.incidentVersion, request.expectedVersion, request.requestId, request.reason)
        if (request.expectedVersion == null || request.expectedNextRoundNumber != null) invalid()
        val i = incident(target, lock = true); val draft = CompensationGiftContent.draft(i, request)
        val fp = hashToken("round-update:$target:$roundId:" + Json.encodeToString(request))
        replay<CompensationRoundDetail>(actor.id, request.requestId, fp, "ROUND_DETAIL")?.let { return@transaction it }
        if (i.version != request.incidentVersion) conflict()
        val (old, c) = mapped(i, roundId, true); supplemental(old.roundNumber, request.supplementalReason)
        if (c.state != CampaignState.DRAFT || c.version != request.expectedVersion) conflict()
        verifyActivity(i, request.audience)
        val r = old.copy(audience = request.audience, supplementalReason = request.supplementalReason, receivingReasonVi = request.receivingReasonVi, receivingReasonEn = request.receivingReasonEn,
            updatedByAdminId = actor.id, updatedAtEpochMillis = nowMillis())
        val updated = c.copy(version = nextVersion(c.version), draft = draft, compensation = CompensationGiftContent.context(i, r))
        putCampaign(updated); compensation.putRound(r); audit(actor, "COMPENSATION_ROUND_UPDATE", roundId, request.requestId, request.reason, c.version, updated.version)
        save(actor.id, request.requestId, fp, "ROUND_DETAIL", result(i, r, updated))
    }
    private fun GiftTransaction.preview(i: CompensationIncident, r: CompensationRound, original: GiftCampaign): Pair<CompensationPreview, GiftCampaignOperations.Audience> {
        val c = content(i, r, original)
        val source = audience(r, c)
        val a = if (source == CompensationAudience.ACTIVITY) GiftCampaignOperations.Audience(
            activityRecipients(i.affectedFromEpochMillis, i.affectedUntilEpochMillis, nowMillis()), emptyList(), 0)
        else GiftCampaignOperations.audience(this, c)
        val prior = compensation.previousClaims(i.id, r.roundNumber, a.users)
        val duplicates = c.draft.userIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        // Bind the interpretation as well as saved source, interval, versions and selected identities.
        val definition = "connected-registered-game-v1;retention=90d;checkpoint=30s;max-gap=60s"
        val token = hashToken("$source|$definition|" + Json.encodeToString(i) + "|" + Json.encodeToString(r) + "|" + Json.encodeToString(c) + "|" +
            a.users.joinToString(",") + "|" + a.invalid.joinToString(",") + "|" + Json.encodeToString(prior))
        return CompensationPreview(i.id, i.version, c.id, c.version, r.roundNumber,
            source,
            c.draft.userIds, a.users.take(10), a.invalid, duplicates, a.duplicates.toString(), a.users.size.toString(),
            Math.multiplyExact(c.draft.gold.toLong(), a.users.size.toLong()).toString(), Math.multiplyExact(c.draft.xp.toLong(), a.users.size.toLong()).toString(),
            prior.distinctUsers, prior.entitlements, prior.selectedOverlap, token) to a
    }
    suspend fun preview(context: GiftAdminContext, incidentId: String, id: String, request: CompensationRoundPreviewRequest): CompensationPreview = repository.transaction(true) {
        val actor = authorize(context, true); val target = canonical(incidentId); val roundId = canonical(id)
        guards(request.incidentVersion, request.expectedVersion, request.requestId, request.reason)
        val fp = hashToken("round-preview:$target:$roundId:" + Json.encodeToString(request))
        replay<CompensationPreview>(actor.id, request.requestId, fp, "ROUND_PREVIEW")?.let { return@transaction it }
        val i = incident(target, request.incidentVersion, true); val (r, c) = mapped(i, roundId, true)
        if (c.state != CampaignState.DRAFT || c.version != request.expectedVersion) conflict()
        val result = preview(i, r, c).first; audit(actor, "COMPENSATION_ROUND_PREVIEW", roundId, request.requestId, request.reason, c.version, c.version)
        save(actor.id, request.requestId, fp, "ROUND_PREVIEW", result)
    }
    suspend fun publish(context: GiftAdminContext, incidentId: String, id: String, request: CompensationRoundPublishRequest): CompensationRoundDetail = repository.transaction(true) {
        val actor = authorize(context, true, true); val target = canonical(incidentId); val roundId = canonical(id)
        guards(request.incidentVersion, request.expectedVersion, request.requestId, request.reason)
        if (!request.previewToken.matches(Regex("[a-f0-9]{64}"))) invalid()
        val fp = hashToken("round-publish:$target:$roundId:" + Json.encodeToString(request))
        replay<CompensationRoundDetail>(actor.id, request.requestId, fp, "ROUND_DETAIL")?.let { return@transaction it }
        val i = incident(target, request.incidentVersion, true); val (mapping, old) = mapped(i, roundId, true)
        if (old.state != CampaignState.DRAFT || old.version != request.expectedVersion) conflict()
        val (fresh, audience) = preview(i, mapping, old)
        if (fresh.invalidUserIds.isNotEmpty()) invalid()
        if (fresh.previewToken != request.previewToken) conflict()
        val r = mapping.copy(updatedByAdminId = actor.id, updatedAtEpochMillis = nowMillis(), publishedByAdminId = actor.id)
        val c = content(i, r, old).copy(state = CampaignState.PUBLISHED, version = nextVersion(old.version), publishedAtEpochMillis = nowMillis())
        freeze(c.id, audience.users); putCampaign(c); compensation.putRound(r); audit(actor, "COMPENSATION_ROUND_PUBLISH", roundId, request.requestId, request.reason, old.version, c.version)
        save(actor.id, request.requestId, fp, "ROUND_DETAIL", result(i, r, c))
    }
    suspend fun cancel(context: GiftAdminContext, incidentId: String, id: String, request: CompensationRoundCancelRequest): CompensationRoundDetail = repository.transaction(true) {
        val actor = authorize(context, true, true); val target = canonical(incidentId); val roundId = canonical(id)
        guards(request.incidentVersion, request.expectedVersion, request.requestId, request.reason)
        val fp = hashToken("round-cancel:$target:$roundId:" + Json.encodeToString(request))
        replay<CompensationRoundDetail>(actor.id, request.requestId, fp, "ROUND_DETAIL")?.let { return@transaction it }
        val i = incident(target, request.incidentVersion, true); val (mapping, old) = mapped(i, roundId, true)
        if (old.state == CampaignState.CANCELLED || old.version != request.expectedVersion) conflict()
        val r = mapping.copy(updatedByAdminId = actor.id, updatedAtEpochMillis = nowMillis(), cancelledByAdminId = actor.id)
        val c = content(i, r, old).copy(state = CampaignState.CANCELLED, version = nextVersion(old.version), cancelledAtEpochMillis = nowMillis(), cancellationReason = request.reason)
        putCampaign(c); compensation.putRound(r); audit(actor, "COMPENSATION_ROUND_CANCEL", roundId, request.requestId, request.reason, old.version, c.version)
        save(actor.id, request.requestId, fp, "ROUND_DETAIL", result(i, r, c))
    }
}
