package com.hienthai.fastowin.server.announcements

import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.protocol.AdminAnnouncementContext
import com.hienthai.fastowin.protocol.NotificationSnapshot
import com.hienthai.fastowin.server.admin.AdminService
import com.hienthai.fastowin.server.admin.AdminRole
import com.hienthai.fastowin.server.admin.AuditEntry
import com.hienthai.fastowin.server.admin.PageRequest
import com.hienthai.fastowin.server.admin.forbidden
import com.hienthai.fastowin.server.admin.invalid
import com.hienthai.fastowin.server.admin.missing
import com.hienthai.fastowin.server.admin.conflict
import com.hienthai.fastowin.server.admin.unauthorized
import com.hienthai.fastowin.server.hashToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

data class AnnouncementAdminContext(val token: String?, val csrfToken: String? = null, val origin: String? = null)

@Serializable data class AnnouncementDraftRequest(
    val titleVi: String, val titleEn: String, val bodyVi: String, val bodyEn: String,
    val category: AdminAnnouncementCategory, val priority: Int = 0,
    val startsAtEpochMillis: Long, val endsAtEpochMillis: Long,
    val showBanner: Boolean = false, val pushEnabled: Boolean = false,
    val action: AdminAnnouncementAction = AdminAnnouncementAction.NONE,
    val targetId: String? = null, val supersedesAnnouncementId: String? = null,
    val audience: AnnouncementAudience = AnnouncementAudience.ALL_REGISTERED,
    val selectedUserIds: List<String> = emptyList(),
    val expectedVersion: Int? = null, val requestId: String, val reason: String
)

@Serializable data class AnnouncementMutationRequest(
    val expectedVersion: Int, val requestId: String, val reason: String,
    val previewToken: String? = null
)

@Serializable data class AnnouncementCounts(
    val frozen: String, val stored: String, val read: String, val dismissed: String,
    val bannerHidden: String, val skipped: String,
    val pushAccepted: String, val pushFailed: String, val pushUnknown: String,
    val pushInFlight: String, val pushDisabled: String, val pushOptedOut: String,
    val pushNoToken: String, val pushUnavailable: String, val pushExpired: String,
    val eligible: String = "0", val pending: String = "0"
)

@Serializable data class AnnouncementAdminView(
    val announcement: AnnouncementDefinition, val counts: AnnouncementCounts,
    val serverNowEpochMillis: Long
)

@Serializable data class AnnouncementAdminPage(
    val items: List<AnnouncementAdminView>, val page: Int, val pageSize: Int,
    val total: String, val serverNowEpochMillis: Long
)

@Serializable data class AnnouncementPreview(
    val announcement: AnnouncementDefinition, val recipientCount: String,
    val duplicateCount: String, val invalidUserIds: List<String>, val previewToken: String,
    val pushConfigured: Boolean, val pushReminderBodyLimit: Int = 240,
    val serverNowEpochMillis: Long
)

@Serializable data class AnnouncementPlayerItem(
    val notification: NotificationSnapshot, val announcement: AdminAnnouncementContext,
    val dismissed: Boolean, val bannerHidden: Boolean
)

@Serializable data class AnnouncementPlayerPage(
    val items: List<AnnouncementPlayerItem>, val page: Int, val pageSize: Int,
    val total: String, val serverNowEpochMillis: Long
)

@Serializable data class AnnouncementBanners(val items: List<AnnouncementPlayerItem>, val serverNowEpochMillis: Long)
@Serializable data class AnnouncementPreferenceRequest(val expectedVersion: Int, val adminEnabled: Boolean, val eventEnabled: Boolean)
@Serializable data class AnnouncementPreferenceView(val version: Int, val adminEnabled: Boolean, val eventEnabled: Boolean, val serverNowEpochMillis: Long)
@Serializable data class AnnouncementMutationResult(val affected: String, val serverNowEpochMillis: Long)
@Serializable data class AnnouncementCapabilities(val pushConfigured: Boolean, val serverNowEpochMillis: Long)

class AnnouncementService(
    val repository: AnnouncementRepository,
    private val admins: AdminService?,
    private val pushConfigured: () -> Boolean = { false },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private fun AnnouncementRecipientCounts.public(status: AnnouncementStatus) = AnnouncementCounts(
        frozen.toString(), stored.toString(), read.toString(), dismissed.toString(), bannerHidden.toString(), skipped.toString(),
        pushAccepted.toString(), pushFailed.toString(), pushUnknown.toString(), pushInFlight.toString(),
        pushDisabled.toString(), pushOptedOut.toString(), pushNoToken.toString(), pushUnavailable.toString(), pushExpired.toString(),
        (frozen - skipped).toString(),
        (if (status == AnnouncementStatus.PUBLISHED) frozen - stored - skipped else 0L).toString()
    )
    private fun AnnouncementTransaction.view(value: AnnouncementDefinition) =
        AnnouncementAdminView(value, recipientCounts(value.id).public(value.status), nowMillis())

    private fun requireAdmins() = admins ?: missing()
    private fun validateMutation(requestId: String, reason: String) {
        if (!requestId.matches(Regex("[A-Za-z0-9_-]{1,128}")) || reason.isBlank() || reason.length > 500 ||
            reason.any(Char::isISOControl)) invalid()
    }
    private fun canonical(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    private fun AnnouncementTransaction.audit(actor: String?, action: String, id: String?, requestId: String? = null, reason: String? = null) {
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), actor, action, id, requestId, reason, null, null, nowMillis()))
    }
    private fun AnnouncementTransaction.typedReplay(actor: String, requestId: String, fingerprint: String): AnnouncementAdminView? {
        val old = replay(actor, requestId) ?: return null
        if (old.fingerprint != fingerprint || old.responseType != "ANNOUNCEMENT_ADMIN_VIEW") conflict()
        return Json.decodeFromString(old.responseJson)
    }
    private fun AnnouncementTransaction.remember(actor: String, requestId: String, fingerprint: String, result: AnnouncementAdminView): AnnouncementAdminView {
        putReplay(AnnouncementReplay(actor, requestId, fingerprint, "ANNOUNCEMENT_ADMIN_VIEW", Json.encodeToString(result)))
        return result
    }
    private fun normalized(request: AnnouncementDraftRequest) = request.copy(
        titleVi = request.titleVi.trim(), titleEn = request.titleEn.trim(),
        bodyVi = request.bodyVi.trim(), bodyEn = request.bodyEn.trim(),
        selectedUserIds = request.selectedUserIds.sorted()
    )
    private fun AnnouncementTransaction.validateLinks(value: AnnouncementDefinition) {
        if (value.targetId != null && !publishedTarget(value.action, value.targetId)) invalid()
        if (value.supersedesAnnouncementId != null &&
            definition(value.supersedesAnnouncementId)?.status !in setOf(AnnouncementStatus.PUBLISHED, AnnouncementStatus.CANCELLED)) invalid()
    }
    private data class Audience(val ids: List<String>, val duplicates: Int, val invalid: List<String>)
    private fun AnnouncementTransaction.audience(value: AnnouncementDefinition): Audience {
        val requested = value.selectedUserIds
        val players = registeredPlayers().map { it.userId }.toSet()
        if (value.audience == AnnouncementAudience.ALL_REGISTERED) return Audience(players.sorted(), 0, emptyList())
        val invalidIds = requested.filter { !canonical(it) || it !in players }.distinct().sorted()
        return Audience(requested.distinct().sorted().filter { it in players }, requested.size - requested.distinct().size, invalidIds)
    }
    private fun token(value: AnnouncementDefinition, audience: Audience) = hashToken(
        "announcement-preview:${Json.encodeToString(value)}:${audience.ids.joinToString(",")}")

    suspend fun list(context: AnnouncementAdminContext, page: PageRequest = PageRequest(), status: AnnouncementStatus? = null): AnnouncementAdminPage =
        repository.transaction(admin = true) {
            requireAdmins().authorize(admin, context.token)
            val values = definitions(status, page.pageSize, page.offset)
            AnnouncementAdminPage(values.map { view(it) }, page.page, page.pageSize, definitionCount(status).toString(), nowMillis())
        }

    suspend fun detail(context: AnnouncementAdminContext, id: String): AnnouncementAdminView = repository.transaction(admin = true) {
        requireAdmins().authorize(admin, context.token)
        if (!canonical(id)) invalid()
        view(definition(id) ?: missing())
    }

    suspend fun capabilities(context: AnnouncementAdminContext): AnnouncementCapabilities = repository.transaction(admin = true) {
        requireAdmins().authorize(admin, context.token)
        AnnouncementCapabilities(pushConfigured(), nowMillis())
    }

    suspend fun create(context: AnnouncementAdminContext, request: AnnouncementDraftRequest): AnnouncementAdminView = repository.transaction(admin = true) {
        val service = requireAdmins(); service.requireOrigin(context.origin)
        validateMutation(request.requestId, request.reason)
        if (request.expectedVersion != null) invalid()
        val actor = service.authorize(admin, context.token, context.csrfToken, mutation = true)
        if (actor.role == AdminRole.SUPPORT) forbidden()
        val input = normalized(request)
        val fingerprint = hashToken("create:${Json.encodeToString(input)}")
        typedReplay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
        val now = nowMillis()
        val value = AnnouncementDefinition(UUID.randomUUID().toString(), 1, AnnouncementStatus.DRAFT,
            input.titleVi, input.titleEn, input.bodyVi, input.bodyEn, input.category, input.priority,
            input.startsAtEpochMillis, input.endsAtEpochMillis, input.showBanner, input.pushEnabled,
            input.action, input.targetId, input.supersedesAnnouncementId, input.audience,
            input.selectedUserIds, actor.id, now, now)
        putDefinition(value)
        audit(actor.id, "ANNOUNCEMENT_CREATE", value.id, request.requestId, request.reason)
        remember(actor.id, request.requestId, fingerprint, view(value))
    }

    suspend fun update(context: AnnouncementAdminContext, id: String, request: AnnouncementDraftRequest): AnnouncementAdminView = repository.transaction(admin = true) {
        val service = requireAdmins(); service.requireOrigin(context.origin)
        validateMutation(request.requestId, request.reason)
        if (request.expectedVersion == null || request.expectedVersion <= 0) invalid()
        val actor = service.authorize(admin, context.token, context.csrfToken, mutation = true)
        if (actor.role == AdminRole.SUPPORT) forbidden()
        if (!canonical(id)) invalid()
        val input = normalized(request)
        val fingerprint = hashToken("update:$id:${Json.encodeToString(input)}")
        typedReplay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
        val previous = definition(id, lock = true) ?: missing()
        if (previous.status != AnnouncementStatus.DRAFT || previous.version != request.expectedVersion) conflict()
        val changed = previous.copy(version = previous.version + 1, titleVi = input.titleVi, titleEn = input.titleEn,
            bodyVi = input.bodyVi, bodyEn = input.bodyEn, category = input.category, priority = input.priority,
            startsAtEpochMillis = input.startsAtEpochMillis, endsAtEpochMillis = input.endsAtEpochMillis,
            showBanner = input.showBanner, pushEnabled = input.pushEnabled, action = input.action, targetId = input.targetId,
            supersedesAnnouncementId = input.supersedesAnnouncementId, audience = input.audience,
            selectedUserIds = input.selectedUserIds, updatedAtEpochMillis = nowMillis())
        putDefinition(changed)
        audit(actor.id, "ANNOUNCEMENT_UPDATE", id, request.requestId, request.reason)
        remember(actor.id, request.requestId, fingerprint, view(changed))
    }

    suspend fun preview(context: AnnouncementAdminContext, id: String): AnnouncementPreview = repository.transaction(admin = true) {
        val service = requireAdmins(); service.requireOrigin(context.origin)
        val actor = service.authorize(admin, context.token, context.csrfToken, mutation = true)
        if (actor.role == AdminRole.SUPPORT) forbidden()
        if (!canonical(id)) invalid()
        val value = definition(id) ?: missing()
        if (value.status != AnnouncementStatus.DRAFT) conflict()
        val audience = audience(value)
        AnnouncementPreview(value, audience.ids.size.toString(), audience.duplicates.toString(), audience.invalid,
            token(value, audience), pushConfigured(), serverNowEpochMillis = nowMillis())
    }
    suspend fun publish(context: AnnouncementAdminContext, id: String, request: AnnouncementMutationRequest): AnnouncementAdminView = repository.transaction(admin = true) {
        val service = requireAdmins(); service.requireOrigin(context.origin)
        validateMutation(request.requestId, request.reason)
        if (!canonical(id) || request.expectedVersion <= 0 || request.previewToken == null) invalid()
        val actor = service.authorize(admin, context.token, context.csrfToken, mutation = true, ownerOnly = true)
        val fingerprint = hashToken("publish:$id:${Json.encodeToString(request)}")
        typedReplay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
        val value = definition(id, lock = true) ?: missing()
        if (value.status != AnnouncementStatus.DRAFT || value.version != request.expectedVersion || value.endsAtEpochMillis <= nowMillis()) conflict()
        validateLinks(value)
        val audience = audience(value)
        if (audience.invalid.isNotEmpty() || token(value, audience) != request.previewToken) conflict()
        val published = value.copy(version = value.version + 1, status = AnnouncementStatus.PUBLISHED,
            selectedUserIds = value.selectedUserIds.distinct().sorted(),
            publishedBy = actor.id, publishedAtEpochMillis = nowMillis(), updatedAtEpochMillis = nowMillis())
        putDefinition(published)
        audience.ids.forEach { putRecipient(AnnouncementRecipient(id, it)) }
        audit(actor.id, "ANNOUNCEMENT_PUBLISH", id, request.requestId, request.reason)
        remember(actor.id, request.requestId, fingerprint, view(published))
    }

    suspend fun cancel(context: AnnouncementAdminContext, id: String, request: AnnouncementMutationRequest): AnnouncementAdminView = repository.transaction(admin = true) {
        val service = requireAdmins(); service.requireOrigin(context.origin)
        validateMutation(request.requestId, request.reason)
        if (!canonical(id) || request.expectedVersion <= 0 || request.previewToken != null) invalid()
        val actor = service.authorize(admin, context.token, context.csrfToken, mutation = true, ownerOnly = true)
        val fingerprint = hashToken("cancel:$id:${Json.encodeToString(request)}")
        typedReplay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
        val value = definition(id, lock = true) ?: missing()
        if (value.status != AnnouncementStatus.PUBLISHED || value.version != request.expectedVersion) conflict()
        val cancelled = value.copy(version = value.version + 1, status = AnnouncementStatus.CANCELLED,
            cancelledBy = actor.id, cancelledAtEpochMillis = nowMillis(), cancellationReason = request.reason,
            updatedAtEpochMillis = nowMillis())
        putDefinition(cancelled)
        audit(actor.id, "ANNOUNCEMENT_CANCEL", id, request.requestId, request.reason)
        remember(actor.id, request.requestId, fingerprint, view(cancelled))
    }

    suspend fun deliverDueBatch(limit: Int = 100): Int {
        if (limit !in 1..1000) invalid()
        var remaining = limit
        var delivered = 0
        var offset = 0
        while (remaining > 0) {
            val ids = repository.transaction { definitions(AnnouncementStatus.PUBLISHED, 100, offset).map { it.id } }
            if (ids.isEmpty()) break
            for (id in ids) {
                if (remaining == 0) break
                val count = repository.transaction {
                    val value = definition(id, lock = true) ?: return@transaction 0
                    val now = nowMillis()
                    if (value.status != AnnouncementStatus.PUBLISHED || now < value.startsAtEpochMillis) return@transaction 0
                    val pending = mutableListOf<AnnouncementRecipient>()
                    var recipientOffset = 0
                    while (pending.size < remaining) {
                        val page = recipients(id, 500, recipientOffset)
                        if (page.isEmpty()) break
                        pending.addAll(page.filter { it.storedAtEpochMillis == null && it.skippedReason == null }.take(remaining - pending.size))
                        recipientOffset += page.size
                    }
                    pending.forEach { recipient ->
                        val player = currentPlayer(recipient.userId)
                        if (player == null) putRecipient(recipient.copy(skippedReason = "DELETED"))
                        else {
                            putRecipient(recipient.copy(storedAtEpochMillis = now))
                            notifications.create(recipient.userId, listOf(value.notification(player.notificationLanguage, now)))
                        }
                    }
                    pending.size
                }
                delivered += count; remaining -= count
            }
            offset += ids.size
        }
        return delivered
    }

    private fun AnnouncementTransaction.player(userId: String): AnnouncementPlayer =
        currentPlayer(userId) ?: unauthorized()
    private fun AnnouncementTransaction.playerItem(userId: String, snapshot: NotificationSnapshot): AnnouncementPlayerItem? {
        val id = snapshot.announcement?.id ?: return null
        val value = definition(id) ?: return null
        val receipt = recipient(id, userId) ?: return null
        return AnnouncementPlayerItem(snapshot.copy(announcement = value.publicContext(nowMillis())), value.publicContext(nowMillis()),
            receipt.dismissedAtEpochMillis != null, receipt.bannerHiddenAtEpochMillis != null)
    }
    suspend fun inbox(userId: String, page: PageRequest = PageRequest()): AnnouncementPlayerPage = repository.transaction {
        player(userId)
        val items = notifications.load(userId, page.pageSize, page.offset, includeDismissed = true, announcementOnly = true)
            .mapNotNull { playerItem(userId, it) }
        AnnouncementPlayerPage(items, page.page, page.pageSize, notifications.count(userId, true, true).toString(), nowMillis())
    }
    suspend fun playerDetail(userId: String, id: String): AnnouncementPlayerItem = repository.transaction {
        player(userId); if (!canonical(id)) invalid()
        val snapshot = notifications.load(userId, 1, 0, true, true, "admin-announcement:$id").singleOrNull() ?: missing()
        playerItem(userId, snapshot) ?: missing()
    }
    suspend fun banners(userId: String): AnnouncementBanners = repository.transaction {
        player(userId)
        val now = nowMillis()
        val snapshots = mutableListOf<NotificationSnapshot>()
        var offset = 0
        while (true) {
            val page = notifications.load(userId, 500, offset, true, true)
            if (page.isEmpty()) break
            snapshots.addAll(page)
            offset += page.size
        }
        val items = snapshots.mapNotNull { playerItem(userId, it) }
            .filter { it.announcement.state == com.hienthai.fastowin.protocol.AdminAnnouncementState.ACTIVE &&
                definition(it.announcement.id)?.showBanner == true && !it.dismissed && !it.bannerHidden }
            .sortedWith(compareByDescending<AnnouncementPlayerItem> { it.announcement.priority }
                .thenByDescending { it.announcement.startsAtEpochMillis }.thenBy { it.announcement.id }).take(1)
        AnnouncementBanners(items, now)
    }
    suspend fun markRead(userId: String, id: String?): AnnouncementMutationResult = mutatePlayer(userId, id) { target, now ->
        notifications.markRead(userId, target, now)
    }
    suspend fun dismiss(userId: String, id: String?): AnnouncementMutationResult = mutatePlayer(userId, id) { target, now ->
        notifications.dismiss(userId, target, now)
    }
    private suspend fun mutatePlayer(userId: String, id: String?, operation: AnnouncementTransaction.(String?, Long) -> Unit): AnnouncementMutationResult = repository.transaction {
        player(userId); if (id != null && !canonical(id)) invalid()
        val target = id?.let { "admin-announcement:$it" }
        val all = mutableListOf<NotificationSnapshot>()
        var offset = 0
        while (true) {
            val page = notifications.load(userId, 500, offset, true, true, target)
            if (page.isEmpty()) break
            all.addAll(page)
            offset += page.size
        }
        if (id != null && all.isEmpty()) missing()
        val items = all.filter { snapshot ->
            val announcementId = snapshot.announcement?.id ?: return@filter false
            recipient(announcementId, userId)?.dismissedAtEpochMillis == null
        }
        val now = nowMillis()
        items.forEach { operation(it.id, now) }
        AnnouncementMutationResult(items.size.toString(), now)
    }
    suspend fun hideBanner(userId: String, id: String): AnnouncementMutationResult = repository.transaction {
        if (!canonical(id)) invalid()
        val value = definition(id, lock = true) ?: missing()
        player(userId)
        val receipt = recipient(id, userId) ?: missing()
        if (receipt.storedAtEpochMillis == null || !value.showBanner) missing()
        val now = nowMillis()
        if (receipt.bannerHiddenAtEpochMillis == null) putRecipient(receipt.copy(bannerHiddenAtEpochMillis = now))
        AnnouncementMutationResult("1", now)
    }
    suspend fun preference(userId: String): AnnouncementPreferenceView = repository.transaction {
        player(userId); preference(userId).let { AnnouncementPreferenceView(it.version, it.adminEnabled, it.eventEnabled, nowMillis()) }
    }
    suspend fun updatePreference(userId: String, request: AnnouncementPreferenceRequest): AnnouncementPreferenceView = repository.transaction {
        player(userId)
        val current = preference(userId)
        if (request.expectedVersion <= 0) invalid()
        val same = current.adminEnabled == request.adminEnabled && current.eventEnabled == request.eventEnabled
        if (request.expectedVersion != current.version && !(same && request.expectedVersion == current.version - 1)) conflict()
        // Persist the default first: the store's optimistic version contract starts new rows at 1.
        putPreference(current)
        val next = if (same) current else current.copy(version = if (current.version == Int.MAX_VALUE) conflict() else current.version + 1,
            adminEnabled = request.adminEnabled, eventEnabled = request.eventEnabled)
        putPreference(next)
        AnnouncementPreferenceView(next.version, next.adminEnabled, next.eventEnabled, nowMillis())
    }
}
