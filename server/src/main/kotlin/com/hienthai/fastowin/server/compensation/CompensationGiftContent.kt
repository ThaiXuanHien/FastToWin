package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.protocol.CompensationMailboxContext
import com.hienthai.fastowin.server.legacyFallback
import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*
import java.time.Instant

/** Player-safe copy only; the internal cause, evidence and actor never enter this projection. */
internal object CompensationGiftContent {
    fun draft(incident: CompensationIncident, request: CompensationRoundRequest): GiftDraftRequest {
        if (request.audience == CompensationAudience.ACTIVITY)
            throw AdminException(400, "ACTIVITY_SOURCE_UNAVAILABLE", "Reliable incident activity coverage and retention are unavailable.")
        for (value in listOf(request.receivingReasonVi, request.receivingReasonEn))
            if (value.isBlank() || value.length > 1000) invalid()
        val from = Instant.ofEpochMilli(incident.affectedFromEpochMillis).toString()
        val until = Instant.ofEpochMilli(incident.affectedUntilEpochMillis).toString()
        return GiftCampaignOperations.validateCompensationContent(GiftDraftRequest(
            incident.titleVi, incident.titleEn,
            legacyFallback("${incident.descriptionVi}\n\nKhoảng ảnh hưởng (UTC): [$from, $until)\n\nLý do nhận: ${request.receivingReasonVi}"),
            legacyFallback("${incident.descriptionEn}\n\nAffected interval (UTC): [$from, $until)\n\nReceiving reason: ${request.receivingReasonEn}"),
            request.reason, request.gold, request.xp,
            if (request.audience == CompensationAudience.USER_IDS) GiftAudience.USER_IDS else GiftAudience.ALL_REGISTERED,
            request.userIds, availableAtEpochMillis = request.availableAtEpochMillis, expiresAtEpochMillis = request.expiresAtEpochMillis,
            requestId = request.requestId, expectedVersion = request.expectedVersion
        ))
    }
    fun context(incident: CompensationIncident, round: CompensationRound) = CompensationMailboxContext(
        incident.id, incident.incidentKey, round.roundNumber, incident.affectedFromEpochMillis,
        incident.affectedUntilEpochMillis, round.receivingReasonVi, round.receivingReasonEn)
}
