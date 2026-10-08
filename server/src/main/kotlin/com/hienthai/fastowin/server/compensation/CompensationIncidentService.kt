package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*
import com.hienthai.fastowin.server.hashToken
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

class CompensationIncidentService(val repository: GiftRepository, private val admins: AdminService,
    private val nowMillis: () -> Long = System::currentTimeMillis) {
    fun requireOrigin(origin: String?) = admins.requireOrigin(origin)
    private fun GiftTransaction.authorize(context: GiftAdminContext, mutation: Boolean = false): AdminIdentity {
        context.origin?.let(admins::requireOrigin)
        if (mutation) admins.requireOrigin(context.origin)
        val actor = admins.authorize(admin, context.token, context.csrfToken, mutation)
        if (mutation && actor.role == AdminRole.SUPPORT) forbidden()
        return actor
    }
    private fun GiftTransaction.audit(actor: AdminIdentity, action: String, id: String? = null,
        request: CompensationIncidentRequest? = null, before: Int? = null, after: Int? = null) {
        admin.appendAudit(AuditEntry(UUID.randomUUID().toString(), actor.id, action, id, request?.requestId, request?.reason,
            before?.let { "version=$it" }, after?.let { "version=$it" }, nowMillis()))
    }
    suspend fun list(context: GiftAdminContext, page: PageRequest): CompensationPage<CompensationIncident> = repository.transaction(true) {
        val actor = authorize(context); val result = compensation.incidents(page); audit(actor, "COMPENSATION_INCIDENT_LIST"); result
    }
    suspend fun detail(context: GiftAdminContext, id: String): CompensationIncident = repository.transaction(true) {
        val actor = authorize(context); uuid(id); val result = compensation.incident(id) ?: missing()
        audit(actor, "COMPENSATION_INCIDENT_DETAIL", id); result
    }
    suspend fun capabilities(context: GiftAdminContext): CompensationCapabilities = repository.transaction(true) {
        val actor = authorize(context); audit(actor, "COMPENSATION_CAPABILITIES")
        CompensationCapabilities(listOf(
            CompensationCapability(CompensationAudience.ALL_REGISTERED, true, "Current registered accounts are frozen at publication."),
            CompensationCapability(CompensationAudience.USER_IDS, true, "Explicit registered user UUIDs are verified before publication."),
            CompensationCapability(CompensationAudience.ACTIVITY, false, "Reliable incident activity coverage and retention are unavailable.")
        ))
    }
    suspend fun create(context: GiftAdminContext, request: CompensationIncidentRequest): CompensationIncident = repository.transaction(true) {
        val actor = authorize(context, true); val normalized = validate(request)
        if (normalized.expectedVersion != null) invalid()
        val fingerprint = hashToken("incident-create:" + Json.encodeToString(request))
        replay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
        responsible(normalized.responsibleAdminId)
        if (compensation.incidentByKey(normalized.incidentKey) != null) conflict()
        val now = nowMillis()
        val result = CompensationIncident(UUID.randomUUID().toString(), normalized.incidentKey, 1,
            normalized.titleVi, normalized.titleEn, normalized.descriptionVi, normalized.descriptionEn,
            normalized.affectedFromEpochMillis, normalized.affectedUntilEpochMillis, normalized.causeCode, normalized.evidence,
            normalized.responsibleAdminId, actor.id, actor.id, now, now)
        compensation.putIncident(result); audit(actor, "COMPENSATION_INCIDENT_CREATE", result.id, request, after = 1)
        save(actor.id, request.requestId, fingerprint, result)
    }
    suspend fun update(context: GiftAdminContext, id: String, request: CompensationIncidentRequest): CompensationIncident = repository.transaction(true) {
        val actor = authorize(context, true); uuid(id); val normalized = validate(request)
        if (normalized.expectedVersion == null || normalized.expectedVersion <= 0) invalid()
        val fingerprint = hashToken("incident-update:$id:" + Json.encodeToString(request))
        replay(actor.id, request.requestId, fingerprint)?.let { return@transaction it }
        val old = compensation.incident(id, true) ?: missing()
        if (old.version != normalized.expectedVersion || old.version == Int.MAX_VALUE || old.incidentKey != normalized.incidentKey || compensation.hasPublishedRound(id)) conflict()
        if (old.responsibleAdminId != normalized.responsibleAdminId) responsible(normalized.responsibleAdminId)
        val result = old.copy(version = old.version + 1, titleVi = normalized.titleVi, titleEn = normalized.titleEn,
            descriptionVi = normalized.descriptionVi, descriptionEn = normalized.descriptionEn,
            affectedFromEpochMillis = normalized.affectedFromEpochMillis, affectedUntilEpochMillis = normalized.affectedUntilEpochMillis,
            causeCode = normalized.causeCode, evidence = normalized.evidence, responsibleAdminId = normalized.responsibleAdminId,
            updatedByAdminId = actor.id, updatedAtEpochMillis = nowMillis())
        compensation.putIncident(result); audit(actor, "COMPENSATION_INCIDENT_UPDATE", id, request, old.version, result.version)
        save(actor.id, request.requestId, fingerprint, result)
    }
    private fun GiftTransaction.responsible(id: String) { if (admin.account(id)?.identity?.active != true) invalid() }
    private fun GiftTransaction.replay(actor: String, request: String, fingerprint: String): CompensationIncident? {
        val receipt = compensation.replay(actor, request) ?: return null
        if (receipt.fingerprint != fingerprint || receipt.responseType != "INCIDENT") conflict()
        return Json.decodeFromString<CompensationIncident>(receipt.responseJson)
    }
    private fun GiftTransaction.save(actor: String, request: String, fingerprint: String, result: CompensationIncident): CompensationIncident {
        compensation.putReplay(actor, request, CompensationReplay(fingerprint, "INCIDENT", Json.encodeToString(result))); return result
    }
    private fun validate(request: CompensationIncidentRequest): CompensationIncidentRequest {
        val key = request.incidentKey.trim().uppercase(Locale.ROOT)
        if (!key.matches(Regex("[A-Z0-9][A-Z0-9_-]{0,127}"))) invalid()
        for ((value, limit) in listOf(request.titleVi to 160, request.titleEn to 160, request.descriptionVi to 4000,
            request.descriptionEn to 4000, request.causeCode to 128, request.evidence to 10000)) {
            if (value.isBlank() || value.length > limit) invalid()
        }
        if (request.affectedFromEpochMillis < 0 || request.affectedUntilEpochMillis <= request.affectedFromEpochMillis || request.affectedUntilEpochMillis > 253402300799999L) invalid()
        if (request.reason.isBlank() || request.reason.length > 500 || request.reason.any(Char::isISOControl) || !request.requestId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid()
        uuid(request.responsibleAdminId)
        return request.copy(incidentKey = key, responsibleAdminId = request.responsibleAdminId.lowercase(Locale.ROOT))
    }
}
