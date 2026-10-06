package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.server.admin.*

/** Both paths atomically commit snapshot/version/audit on one connection.
 * Authenticated operations lock global admin authorization before the maintenance singleton row.
 * Trusted runtime observation locks only the singleton row and never acquires authorization afterward.
 */
interface MaintenanceRepository {
    suspend fun <T> transaction(block:MaintenanceTransaction.()->T):T
    suspend fun <T> observationTransaction(block:MaintenanceObservationTransaction.()->T):T
}
/** Restricted to trusted observation and automatic transitions; no account, session or replay access. */
interface MaintenanceObservationTransaction {
    /** Locks the singleton row until commit. Missing durable storage is unavailable, never ACTIVE. */
    fun snapshot():MaintenanceSnapshot
    /** Conditional version update; a mismatch fails the transaction. */
    fun putSnapshot(expectedVersion:Long,value:MaintenanceSnapshot)
    fun appendAudit(entry:AuditEntry)
}
interface MaintenanceTransaction:MaintenanceObservationTransaction {
    val admin:AdminTransaction
    override fun appendAudit(entry:AuditEntry) = admin.appendAudit(entry)
    fun request(actor:String,requestId:String):MaintenanceRequestRecord?
    fun putRequest(actor:String,requestId:String,record:MaintenanceRequestRecord)
}
object UnavailableMaintenanceRepository:MaintenanceRepository {
    override suspend fun <T> transaction(block:MaintenanceTransaction.()->T):T=unavailable()
    override suspend fun <T> observationTransaction(block:MaintenanceObservationTransaction.()->T):T=unavailable()
}
