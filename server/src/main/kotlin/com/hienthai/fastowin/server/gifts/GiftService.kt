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

    suspend fun exchangePlays(userId: String, request: PlayCreditExchangeRequest): AdminOk = repository.transaction {
        uuid(userId)
        uuid(request.requestId)
        if (!rewardActionPlayer(userId)) unauthorized()
        if (!newIntentAllowed()) unavailable()
        val offer = PLAY_CREDIT_OFFERS.firstOrNull { it.id == request.offerId } ?: invalid()
        exchangePlayCredits(userId, offer, request.requestId, nowMillis())
        AdminOk()
    }

    suspend fun listGiftCodes(context: GiftAdminContext, page: PageRequest): AdminPage<GiftCode> = repository.transaction(true) {
        authorize(context, owner = true)
        listCodes(page)
    }

    suspend fun publishGiftCode(context: GiftAdminContext, request: GiftCodePublishRequest): GiftCode {
        val service = admins ?: unavailable()
        var control: DualControl = DualControl.Allowed
        val published = publishGiftCode(context, request) { actorId, key ->
            val reward = request.reward
            // The version is the code's content without the request id or reason, so a second
            // Owner approves by submitting the same code, reward, expiry and claim limit.
            val version = hashToken("gift-code-approval:" + Json.encodeToString(request.copy(code = key, reason = "", requestId = "")))
            control = admin.dualControl("GIFT_CODE", key, version, actorId, service.publishLimits.exceeded(
                RewardAmounts(reward.gold.toLong(), reward.gems.toLong(), reward.xp.toLong(), reward.plays.toLong()),
                request.maximumClaims?.toLong()), nowMillis())
            control !is DualControl.Pending
        }
        reportDualControl(service, control, "mã quà", request.code.trim().uppercase())
        return published ?: error("unreachable: pending publication was reported")
    }

    private fun reportDualControl(service: AdminService, control: DualControl, kind: String, label: String) {
        when (control) {
            is DualControl.Pending -> {
                if (control.newlyRequested) service.alert("Yêu cầu duyệt công bố $kind vượt trần", "Nội dung" to label, "Vượt trần" to control.reasons.joinToString("; "))
                approvalRequired(control)
            }
            is DualControl.Approved -> service.alert("Đã duyệt và công bố $kind vượt trần", "Nội dung" to label, "Vượt trần" to control.reasons.joinToString("; "))
            DualControl.Allowed -> Unit
        }
    }

    private suspend fun publishGiftCode(
        context: GiftAdminContext, request: GiftCodePublishRequest, approve: GiftTransaction.(actorId: String, key: String) -> Boolean
    ): GiftCode? = repository.transaction(true) {
        val actor = authorize(context, mutation = true, owner = true)
        mutation(request.reason, request.requestId)
        val key = normalizedCode(request.code)
        val reward = request.reward
        val fingerprint = hashToken("gift-code-publish:" + Json.encodeToString(request.copy(code = key)))
        val previous = giftCodeMutation(actor.id, request.requestId)
        if (previous != null) {
            if (previous != fingerprint) conflict()
            return@transaction code(key) ?: missing()
        }
        val amounts = listOf(reward.gold, reward.xp, reward.gems, reward.plays)
        if (amounts.any { it < 0 } || amounts.all { it == 0 } ||
            request.expiresAt <= nowMillis() || request.expiresAt > 253402300799999L ||
            (request.maximumClaims != null && request.maximumClaims <= 0)) invalid()
        if (code(key) != null) conflict()
        if (!approve(actor.id, key)) return@transaction null
        val published = GiftCode(key, reward, request.expiresAt, request.maximumClaims)
        insertCode(published)
        recordGiftCodeMutation(actor.id, request.requestId, fingerprint)
        giftCodeAudit(actor.id, "GIFT_CODE_PUBLISH", key, request.requestId, request.reason)
        published
    }

    suspend fun stopGiftCode(context: GiftAdminContext, input: String, request: GiftCodeStopRequest): AdminOk = repository.transaction(true) {
        val actor = authorize(context, mutation = true, owner = true)
        mutation(request.reason, request.requestId)
        val key = normalizedCode(input)
        val fingerprint = hashToken("gift-code-stop:$key:" + Json.encodeToString(request))
        val previous = giftCodeMutation(actor.id, request.requestId)
        if (previous != null) {
            if (previous != fingerprint) conflict()
            return@transaction AdminOk()
        }
        val current = code(key) ?: missing()
        if (!current.stopped) {
            stopCode(key)
            giftCodeAudit(actor.id, "GIFT_CODE_STOP", key, request.requestId, request.reason)
        }
        recordGiftCodeMutation(actor.id, request.requestId, fingerprint)
        AdminOk()
    }

    suspend fun redeemGiftCode(userId: String, request: GiftCodeRedeemRequest): GiftCodeRedeemResult = repository.transaction {
        uuid(userId)
        val key = normalizedCode(request.code)
        if (!rewardActionPlayer(userId)) unauthorized()
        val current = code(key) ?: throw AdminException(404, "GIFT_CODE_NOT_FOUND", "Code not found")
        if (redeemed(key, userId)) throw AdminException(409, "GIFT_CODE_ALREADY_USED", "Code already used")
        if (current.stopped) throw AdminException(409, "GIFT_CODE_STOPPED", "Code stopped")
        val now = nowMillis()
        if (now >= current.expiresAt) throw AdminException(409, "GIFT_CODE_EXPIRED", "Code expired")
        if (current.maximumClaims != null && current.claims >= current.maximumClaims)
            throw AdminException(409, "GIFT_CODE_EXHAUSTED", "Code exhausted")
        if (!newIntentAllowed()) unavailable()
        redeem(current, userId, now)
        GiftCodeRedeemResult(current.reward)
    }

    private fun normalizedCode(value: String): String = value.trim().uppercase().also {
        if (!it.matches(Regex("[A-Z0-9_-]{4,24}"))) invalid()
    }

    private fun GiftTransaction.giftCodeAudit(actor: String, action: String, code: String, requestId: String, reason: String) {
        // A hash identifies the code in audit output without publishing it to other views.
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), actor, action, null,
            requestId, reason, null, "codeHash=${hashToken(code)}", nowMillis()))
    }

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
    private fun GiftTransaction.detail(c: GiftCampaign) = GiftCampaignOperations.detail(this, c, nowMillis())
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
    private fun GiftTransaction.audience(c: GiftCampaign) = GiftCampaignOperations.audience(this, c)
    private fun preview(c: GiftCampaign, a: GiftCampaignOperations.Audience) = GiftCampaignOperations.preview(c, a)
    suspend fun preview(context: GiftAdminContext, id: String, request: GiftPreviewRequest): GiftPreview = repository.transaction(true) {
        val actor = authorize(context, true); uuid(id); val c = ordinaryCampaign(id, true)
        if (c.state != CampaignState.DRAFT || c.version != request.expectedVersion) conflict()
        val result = preview(c, audience(c)); audit(actor.id, "GIFT_PREVIEW", id); result
    }
    suspend fun publish(context: GiftAdminContext, id: String, request: GiftPublishRequest): GiftCampaignDetail {
        val service = admins ?: unavailable()
        var control: DualControl = DualControl.Allowed
        var title = ""
        val result = repository.transaction(true) {
            val actor = authorize(context, true, true); uuid(id); mutation(request.reason, request.requestId)
            val fp = hashToken("publish:$id:" + Json.encodeToString(request))
            val c = ordinaryCampaign(id, true)
            replay(actor.id, request.requestId, fp)?.let { return@transaction it }
            if (c.state != CampaignState.DRAFT || c.version != request.expectedVersion) conflict()
            val audience = audience(c); val fresh = preview(c, audience)
            if (fresh.invalidUserIds.isNotEmpty()) invalid()
            if (fresh.previewToken != request.previewToken) conflict()
            title = c.draft.titleVi
            control = admin.dualControl("GIFT_CAMPAIGN", id, "v${c.version}", actor.id,
                service.publishLimits.exceeded(c.draft.rewardAmounts(), audience.users.size.toLong()), nowMillis())
            if (control is DualControl.Pending) return@transaction null
            publishApproved(actor, id, request, fp, c, audience)
        }
        reportDualControl(service, control, "quà", title)
        return result ?: error("unreachable: pending publication was reported")
    }
    private fun GiftTransaction.publishApproved(actor: AdminIdentity, id: String, request: GiftPublishRequest, fp: String,
        c: GiftCampaign, audience: GiftCampaignOperations.Audience): GiftCampaignDetail {
        val updated = c.copy(state = CampaignState.PUBLISHED, version = Math.addExact(c.version, 1), publishedAtEpochMillis = nowMillis())
        freeze(id, audience.users); putCampaign(updated); audit(actor.id, "GIFT_PUBLISH", id, request.requestId, request.reason)
        return save(actor.id, request.requestId, fp, detail(updated))
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
    private fun GiftTransaction.ordinaryCampaign(id: String, lock: Boolean = false) = campaign(id, lock)?.takeIf { it.compensation == null && it.clanRace == null } ?: missing()
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
        GiftMailboxPage(rows.map { (c, r) -> gift(c, r, now) }, page.page, page.pageSize, total, mailboxAvailableCount(userId, now))
    }
    suspend fun claim(userId: String, campaignId: String, receivedAt: Long = nowMillis()): GiftClaimResult = repository.transaction {
        uuid(userId); uuid(campaignId)
        // Canonical lock order: campaign -> entitlement -> user -> stats. Cancellation uses campaign lock too.
        val c = campaign(campaignId, true) ?: missing()
        val r = recipient(campaignId, userId, true)?.takeIf { it.delivery == "DELIVERED" } ?: missing()
        if (!activePlayer(userId)) unauthorized()
        if (r.claimedAt != null) {
            val current = balance(userId)
            return@transaction GiftClaimResult(gift(c, r, receivedAt), c.draft.gold, c.draft.xp, current.gold, current.xp, c.draft.gems, current.gems)
        }
        if (!newIntentAllowed()) throw AdminException(503, "MAINTENANCE",
            com.hienthai.fastowin.localization.LocalizationService(com.hienthai.fastowin.localization.AppLanguage.VIETNAMESE)
                .text(com.hienthai.fastowin.localization.TextKey.MaintenanceDefaultMessage))
        if (c.state != CampaignState.PUBLISHED || receivedAt < c.draft.availableAtEpochMillis || receivedAt >= c.draft.expiresAtEpochMillis!!) conflict()
        val current = award(userId, c, receivedAt)
        val claimed = r.copy(claimedAt = receivedAt); putRecipient(claimed)
        GiftClaimResult(gift(c, claimed, receivedAt), c.draft.gold, c.draft.xp, current.gold, current.xp, c.draft.gems, current.gems)
    }
    suspend fun claimAll(userId: String, request: GiftClaimAllRequest, receivedAt: Long = nowMillis()): GiftClaimAllResult {
        if (request.campaignIds.size !in 1..100) invalid()
        return GiftClaimAllResult(request.campaignIds.map { id ->
            try { GiftClaimItem(id, result = claim(userId, id, receivedAt)) }
            catch (error: AdminException) { GiftClaimItem(id, error = GiftError(error.code, error.message)) }
        })
    }
    private fun validated(request: GiftDraftRequest) = GiftCampaignOperations.validate(request)
    private fun mutation(reason: String, request: String) {
        if (reason.isBlank() || reason.length > 500 || reason.any(Char::isISOControl) || !request.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid()
    }
}
internal fun GiftDraftRequest.rewardAmounts() = RewardAmounts(gold.toLong(), gems.toLong(), xp.toLong(), plays.toLong())
internal fun uuid(id: String) { if (runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false).not()) invalid() }
internal fun gift(c: GiftCampaign, r: GiftRecipient, now: Long) = MailboxGift(c.id, c.draft.titleVi, c.draft.titleEn, c.draft.bodyVi, c.draft.bodyEn,
    c.draft.gold, c.draft.xp, c.draft.availableAtEpochMillis, c.draft.expiresAtEpochMillis!!,
    when { r.claimedAt != null -> GiftState.CLAIMED; c.state == CampaignState.CANCELLED -> GiftState.CANCELLED; now >= c.draft.expiresAtEpochMillis -> GiftState.EXPIRED; else -> GiftState.AVAILABLE }, r.claimedAt, c.compensation, c.draft.gems, c.clanRace, c.draft.plays)
