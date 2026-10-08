package com.hienthai.fastowin.server.compensation

import com.hienthai.fastowin.server.admin.PageRequest

/** Bound to the enclosing gift transaction. Never opens connections or commits. */
interface CompensationStore {
    fun incident(id: String, lock: Boolean = false): CompensationIncident?
    fun incidentByKey(key: String): CompensationIncident?
    fun incidents(page: PageRequest): CompensationPage<CompensationIncident>
    fun putIncident(incident: CompensationIncident)
    fun round(campaignId: String): CompensationRound?
    fun rounds(incidentId: String, page: PageRequest): CompensationPage<CompensationRound>
    fun putRound(round: CompensationRound)
    fun maximumRoundNumber(incidentId: String): Int
    fun hasDraftRound(incidentId: String): Boolean
    fun hasPublishedRound(incidentId: String): Boolean
    fun replay(actorId: String, requestId: String): CompensationReplay?
    fun putReplay(actorId: String, requestId: String, replay: CompensationReplay)
    fun previousClaims(incidentId: String, beforeRoundNumber: Int, selectedUserIds: List<String>): CompensationPreviousClaims
}
