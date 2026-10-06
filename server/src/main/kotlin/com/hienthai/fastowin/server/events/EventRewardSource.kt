package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.protocol.*

interface EventRewardSource {
    /** Catalog loaded before callback; callback must not borrow a database connection.
     * Acquire this gate before engine mutex. On mutex contention, return and retry outside the gate.
     */
    suspend fun <T> withStartCatalog(block:suspend (RewardEventCatalog)->T):T
}
class RewardEventCatalog internal constructor(records:List<RewardEventRecord>,private val neutral:Boolean=false) {
    private val definitions=records.filter { it.publishedAtEpochMillis!=null && !it.cancelled }
        .map { it.copy(draft=it.draft.copy(matchTypes=it.draft.matchTypes.toSet())) }.sortedBy { it.id }
    fun snapshot(atEpochMillis:Long,matchType:MatchType):MatchRewardEventSnapshot {
        if(neutral) return MatchRewardEventSnapshot()
        val sources=definitions.filter { with(it.draft) {
            matchType in matchTypes && atEpochMillis>=startsAtEpochMillis && atEpochMillis<endsAtEpochMillis && (goldMultiplier>1 || xpMultiplier>1)
        } }.map { with(it.draft) { RewardEventSourceSnapshot(it.id,it.version,nameVi,nameEn,startsAtEpochMillis,endsAtEpochMillis,goldMultiplier,xpMultiplier) } }
        return MatchRewardEventSnapshot(atEpochMillis,sources.maxOfOrNull { it.goldMultiplier }?:1,sources.maxOfOrNull { it.xpMultiplier }?:1,sources)
    }
}
object NoRewardEventSource:EventRewardSource {
    override suspend fun <T> withStartCatalog(block:suspend (RewardEventCatalog)->T):T=block(RewardEventCatalog(emptyList(),neutral=true))
}
