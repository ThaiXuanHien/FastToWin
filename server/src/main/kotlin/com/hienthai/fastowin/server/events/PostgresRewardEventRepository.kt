package com.hienthai.fastowin.server.events

import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

class PostgresRewardEventRepository(private val source:DataSource):RewardEventRepository {
    override suspend fun <T> transaction(block:RewardEventTransaction.()->T):T=connection { connection ->
        connection.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
        connection.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187350)") }
        currentCoroutineContext().ensureActive()
        JdbcRewardEventTransaction(connection).block()
    }
    override suspend fun <T> publicRead(block:RewardEventRead.()->T):T=connection { connection ->
        JdbcRewardEventTransaction(connection).block()
    }
    override suspend fun <T> withStartCatalog(block:suspend (RewardEventCatalog)->T):T=connection { connection ->
        connection.createStatement().use { it.execute("SELECT pg_advisory_xact_lock_shared(7421092187350)") }
        currentCoroutineContext().ensureActive()
        val catalog=RewardEventCatalog(JdbcRewardEventTransaction(connection).records().map { it.record() })
        block(catalog)
    }
    private suspend fun <T> connection(block:suspend (Connection)->T):T=withContext(Dispatchers.IO) {
        try {
            source.connection.use { connection ->
                connection.autoCommit=false
                try {
                    val result=block(connection); currentCoroutineContext().ensureActive()
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
private class JdbcRewardEventTransaction(private val connection:Connection):RewardEventTransaction {
    override val admin:AdminTransaction=JdbcAdminTransaction(connection)
    override fun records():List<RewardEventAdminView> = connection.prepareStatement("SELECT id,version,payload FROM reward_events ORDER BY id").use { s ->
        s.executeQuery().use { rs -> buildList {
            while(rs.next()) {
                val value=Json.decodeFromString<RewardEventRecord>(rs.getString("payload"))
                if(value.id!=rs.getString("id") || value.version!=rs.getLong("version")) unavailable()
                add(value.adminView(0))
            }
        } }
    }
    override fun put(value:RewardEventAdminView) {
        val record=value.record()
        val count=connection.prepareStatement("INSERT INTO reward_events(id,version,payload) VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET version=EXCLUDED.version,payload=EXCLUDED.payload WHERE reward_events.version=EXCLUDED.version-1").use { s ->
            s.setObject(1,UUID.fromString(value.id)); s.setLong(2,value.version); s.setString(3,Json.encodeToString(record)); s.executeUpdate()
        }
        if(count!=1) conflict()
    }
    override fun replay(actor:String,requestId:String):Pair<String,RewardEventAdminView>?=connection.prepareStatement("SELECT fingerprint,safe_result FROM reward_event_requests WHERE actor_id=? AND request_id=?").use { s ->
        s.setObject(1,UUID.fromString(actor)); s.setString(2,requestId)
        s.executeQuery().use { rs -> if(rs.next()) rs.getString(1) to Json.decodeFromString<RewardEventAdminView>(rs.getString(2)) else null }
    }
    override fun remember(actor:String,requestId:String,fingerprint:String,result:RewardEventAdminView) {
        connection.prepareStatement("INSERT INTO reward_event_requests(actor_id,request_id,fingerprint,safe_result) VALUES (?,?,?,?)").use { s ->
            s.setObject(1,UUID.fromString(actor)); s.setString(2,requestId); s.setString(3,fingerprint); s.setString(4,Json.encodeToString(result)); s.executeUpdate()
        }
    }
}
