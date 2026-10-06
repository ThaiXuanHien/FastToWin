package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Memory-only serialization: suspend for event mutex BEFORE entering synchronous admin transaction. */
class InMemoryRewardEventRepository(private val admins:AdminRepository):RewardEventRepository {
    private val gate=Mutex()
    private var records=linkedMapOf<String,RewardEventRecord>()
    private var requests=linkedMapOf<Pair<String,String>,RewardEventRequestRecord>()
    override suspend fun <T> transaction(block:RewardEventTransaction.()->T):T=gate.withLock {
        val next=LinkedHashMap(records); val replay=LinkedHashMap(requests)
        val result=admins.transaction {
            val adminTx=this
            object:RewardEventTransaction {
                override val admin=adminTx
                override fun records()=next.values.map { it.adminView(0) }
                override fun put(value:RewardEventAdminView) { next[value.id]=value.record() }
                override fun replay(actor:String,requestId:String)=replay[actor to requestId]?.let { it.fingerprint to it.result }
                override fun remember(actor:String,requestId:String,fingerprint:String,result:RewardEventAdminView) {
                    replay[actor to requestId]=RewardEventRequestRecord(fingerprint,result)
                }
            }.block()
        }
        records=next; requests=replay; result
    }
    override suspend fun <T> publicRead(block:RewardEventRead.()->T):T=gate.withLock {
        object:RewardEventRead { override fun records()=records.values.map { it.adminView(0) } }.block()
    }
    override suspend fun <T> withStartCatalog(block:suspend (RewardEventCatalog)->T):T=gate.withLock {
        block(RewardEventCatalog(records.values.toList()))
    }
}
