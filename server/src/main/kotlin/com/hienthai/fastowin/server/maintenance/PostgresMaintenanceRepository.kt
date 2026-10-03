package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

class PostgresMaintenanceRepository(private val source:DataSource):MaintenanceRepository {
    override suspend fun <T> transaction(block:MaintenanceTransaction.()->T):T=withContext(Dispatchers.IO) {
        try {
            source.connection.use { connection ->
                connection.autoCommit=false
                try {
                    connection.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
                    val result=JdbcMaintenanceTransaction(connection).block()
                    connection.commit(); result
                } catch(error:Throwable) {
                    try { connection.rollback() } catch(rollback:SQLException) { error.addSuppressed(rollback) }
                    throw error
                }
            }
        } catch(_:SQLException) { unavailable() }
        catch(_:SerializationException) { unavailable() }
    }
}
private class JdbcMaintenanceTransaction(private val connection:Connection):MaintenanceTransaction {
    override val admin:AdminTransaction=JdbcAdminTransaction(connection)
    override fun snapshot():MaintenanceSnapshot=connection.prepareStatement("SELECT version,state,payload FROM runtime_maintenance WHERE singleton=TRUE FOR UPDATE").use { statement ->
        statement.executeQuery().use { rs ->
            if(!rs.next()) unavailable()
            val value=Json.decodeFromString<MaintenanceSnapshot>(rs.getString("payload"))
            if(value.version!=rs.getLong("version") || value.state.name!=rs.getString("state")) unavailable()
            if(value.state!=com.hienthai.fastowin.protocol.MaintenanceState.ACTIVE &&
                (value.mode==null || value.messageVi.isNullOrBlank() || value.messageEn.isNullOrBlank() ||
                    value.startedAtEpochMillis==null || value.state!=com.hienthai.fastowin.protocol.MaintenanceState.MAINTENANCE &&
                    (value.transitionAtEpochMillis==null || value.drainDeadlineEpochMillis==null))) unavailable()
            value
        }
    }
    override fun putSnapshot(expectedVersion:Long,value:MaintenanceSnapshot) {
        val count=connection.prepareStatement("UPDATE runtime_maintenance SET version=?,state=?,payload=? WHERE singleton=TRUE AND version=?").use { s ->
            s.setLong(1,value.version); s.setString(2,value.state.name); s.setString(3,Json.encodeToString(value)); s.setLong(4,expectedVersion); s.executeUpdate()
        }
        if(count!=1) conflict()
    }
    override fun request(actor:String,requestId:String):MaintenanceRequestRecord?=connection.prepareStatement("SELECT fingerprint,safe_result FROM runtime_maintenance_requests WHERE actor_id=? AND request_id=?").use { s ->
        s.setObject(1,UUID.fromString(actor)); s.setString(2,requestId)
        s.executeQuery().use { rs -> if(rs.next()) MaintenanceRequestRecord(rs.getString(1),Json.decodeFromString<MaintenanceAdminResponse>(rs.getString(2))) else null }
    }
    override fun putRequest(actor:String,requestId:String,record:MaintenanceRequestRecord) {
        connection.prepareStatement("INSERT INTO runtime_maintenance_requests(actor_id,request_id,fingerprint,safe_result) VALUES (?,?,?,?)").use { s ->
            s.setObject(1,UUID.fromString(actor)); s.setString(2,requestId); s.setString(3,record.fingerprint); s.setString(4,Json.encodeToString(record.result)); s.executeUpdate()
        }
    }
}
