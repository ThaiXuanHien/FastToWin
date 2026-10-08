package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.server.gifts.*

internal data class MemoryCompensationState(
    val incidents: MutableMap<String, CompensationIncident> = linkedMapOf(),
    val rounds: MutableMap<String, CompensationRound> = linkedMapOf(),
    val replays: MutableMap<Pair<String, String>, CompensationReplay> = linkedMapOf()
) {
    fun snapshot() = MemoryCompensationState(incidents.toMutableMap(), rounds.toMutableMap(), replays.toMutableMap())
}
internal class MemoryCompensationStore(private val state: MemoryCompensationState,
    private val campaign: (String) -> GiftCampaign?, private val recipients: () -> Collection<GiftRecipient>) : CompensationStore {
    override fun incident(id: String, lock: Boolean) = state.incidents[id]
    override fun incidentByKey(key: String) = state.incidents.values.firstOrNull { it.incidentKey == key }
    override fun incidents(page: PageRequest): CompensationPage<CompensationIncident> {
        val all = state.incidents.values.sortedByDescending { it.id }
        return CompensationPage(all.drop(page.offset).take(page.pageSize), page.page, page.pageSize, all.size.toString())
    }
    override fun putIncident(incident: CompensationIncident) {
        if (incidentByKey(incident.incidentKey)?.id?.let { it != incident.id } == true) conflict()
        if (state.incidents[incident.id]?.incidentKey?.let { it != incident.incidentKey } == true) conflict()
        state.incidents[incident.id] = incident
    }
    override fun round(campaignId: String) = state.rounds[campaignId]
    override fun rounds(incidentId: String, page: PageRequest): CompensationPage<CompensationRound> {
        val all = state.rounds.values.filter { it.incidentId == incidentId }.sortedByDescending { it.roundNumber }
        return CompensationPage(all.drop(page.offset).take(page.pageSize), page.page, page.pageSize, all.size.toString())
    }
    override fun putRound(round: CompensationRound) {
        val context = campaign(round.campaignId)?.compensation ?: conflict()
        if (state.incidents[round.incidentId] == null || context.incidentId != round.incidentId || context.roundNumber != round.roundNumber || round.roundNumber <= 0) conflict()
        if (state.rounds.values.any { it.campaignId != round.campaignId && it.incidentId == round.incidentId && it.roundNumber == round.roundNumber }) conflict()
        val old = state.rounds[round.campaignId]
        if (old != null && (old.incidentId != round.incidentId || old.roundNumber != round.roundNumber)) conflict()
        if (old == null && campaign(round.campaignId)?.state == CampaignState.DRAFT && hasDraftRound(round.incidentId)) conflict()
        state.rounds[round.campaignId] = round
    }
    override fun maximumRoundNumber(incidentId: String) = state.rounds.values.filter { it.incidentId == incidentId }.maxOfOrNull { it.roundNumber } ?: 0
    override fun hasDraftRound(incidentId: String) = state.rounds.values.any { it.incidentId == incidentId && campaign(it.campaignId)?.state == CampaignState.DRAFT }
    override fun hasPublishedRound(incidentId: String) = state.rounds.values.any { it.incidentId == incidentId && campaign(it.campaignId)?.publishedAtEpochMillis != null }
    override fun replay(actorId: String, requestId: String) = state.replays[actorId to requestId]
    override fun putReplay(actorId: String, requestId: String, replay: CompensationReplay) {
        if (state.replays.putIfAbsent(actorId to requestId, replay) != null) conflict()
    }
    override fun previousClaims(incidentId: String, beforeRoundNumber: Int, selectedUserIds: List<String>): CompensationPreviousClaims {
        val ids = state.rounds.values.filter { it.incidentId == incidentId && it.roundNumber < beforeRoundNumber }.map { it.campaignId }.toSet()
        val paid = recipients().filter { it.campaignId in ids && it.claimedAt != null }
        val users = paid.map { it.userId }.toSet(); val selected = selectedUserIds.toSet()
        return CompensationPreviousClaims(users.size.toString(), paid.size.toString(), users.count { it in selected }.toString())
    }
}
