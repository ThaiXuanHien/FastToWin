package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.server.admin.*

interface RewardEventRepository:EventRewardSource {
    suspend fun <T> transaction(block:RewardEventTransaction.()->T):T
    suspend fun <T> publicRead(block:RewardEventRead.()->T):T
}
interface RewardEventRead {
    // Records are internal to server persistence, never serialized directly to public routes.
    fun records():List<RewardEventAdminView>
}
interface RewardEventTransaction:RewardEventRead {
    val admin:AdminTransaction
    fun put(value:RewardEventAdminView)
    fun replay(actor:String,requestId:String):Pair<String,RewardEventAdminView>?
    fun remember(actor:String,requestId:String,fingerprint:String,result:RewardEventAdminView)
}
internal fun RewardEventAdminView.record()=RewardEventRecord(id,version,draft.copy(matchTypes=draft.matchTypes.toSet()),actorId,reason,changedAtEpochMillis,publishedAtEpochMillis,state==com.hienthai.fastowin.protocol.RewardEventState.CANCELLED)
object UnavailableRewardEventRepository:RewardEventRepository {
    override suspend fun <T> transaction(block:RewardEventTransaction.()->T):T=unavailable()
    override suspend fun <T> publicRead(block:RewardEventRead.()->T):T=unavailable()
    override suspend fun <T> withStartCatalog(block:suspend (RewardEventCatalog)->T):T=unavailable()
}
