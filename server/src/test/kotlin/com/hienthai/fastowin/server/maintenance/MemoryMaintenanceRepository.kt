package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.server.admin.InMemoryAdminRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Transaction-equivalent test fixture only; never selected by production wiring. */
internal class MemoryMaintenanceRepository(private val admins: InMemoryAdminRepository) : MaintenanceRepository {
    private val mutex=Mutex()
    private var snapshot=MaintenanceSnapshot()
    private var requests=mutableMapOf<Pair<String,String>,MaintenanceRequestRecord>()
    // This test fixture uses one in-memory admin commit for atomic audit rollback; PostgreSQL
    // concurrency guarantees are exercised with the real repository, not this stronger mutex.
    override suspend fun <T> observationTransaction(block: MaintenanceObservationTransaction.() -> T): T = transaction { block() }
    var failAudit=false
    override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T = mutex.withLock {
        var next=snapshot; val replay=requests.toMutableMap()
        val result=admins.transaction {
            val adminTx=this
            val tx=object : MaintenanceTransaction {
                override val admin=object : com.hienthai.fastowin.server.admin.AdminTransaction by adminTx {
                    override fun appendAudit(entry:com.hienthai.fastowin.server.admin.AuditEntry) {
                        if (failAudit) com.hienthai.fastowin.server.admin.unavailable()
                        adminTx.appendAudit(entry)
                    }
                }
                override fun snapshot()=next
                override fun putSnapshot(expectedVersion:Long,value:MaintenanceSnapshot) {
                    if (next.version!=expectedVersion) com.hienthai.fastowin.server.admin.conflict()
                    next=value
                }
                override fun request(actor:String,requestId:String)=replay[actor to requestId]
                override fun putRequest(actor:String,requestId:String,record:MaintenanceRequestRecord) { replay[actor to requestId]=record }
            }
            tx.block()
        }
        snapshot=next; requests=replay; result
    }
}
