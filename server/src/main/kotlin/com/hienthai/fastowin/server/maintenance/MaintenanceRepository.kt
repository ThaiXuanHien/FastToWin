package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.server.admin.*

/** All operations use one atomic snapshot, connection and shared admin authorization lock.
 * Lock order: shared admin advisory lock -> maintenance singleton row. Auth revocation uses the same lock.
 */
interface MaintenanceRepository {
    suspend fun <T> transaction(block:MaintenanceTransaction.()->T):T
}
interface MaintenanceTransaction {
    val admin:AdminTransaction
    /** Locks the singleton row until commit. Missing durable storage is unavailable, never ACTIVE. */
    fun snapshot():MaintenanceSnapshot
    /** Conditional version update; a mismatch fails the transaction. */
    fun putSnapshot(expectedVersion:Long,value:MaintenanceSnapshot)
    fun request(actor:String,requestId:String):MaintenanceRequestRecord?
    fun putRequest(actor:String,requestId:String,record:MaintenanceRequestRecord)
}
object UnavailableMaintenanceRepository:MaintenanceRepository {
    override suspend fun <T> transaction(block:MaintenanceTransaction.()->T):T=unavailable()
}
