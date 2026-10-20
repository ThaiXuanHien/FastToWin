package com.hienthai.fastowin.server.ads

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.PostgresPlayQuotaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class PostgresAdMobRepository(private val dataSource: DataSource) : AdMobRepository {
    private val quota = PostgresPlayQuotaRepository(dataSource)
    override suspend fun findByCustomData(customData: String?) = withContext(Dispatchers.IO) {
        if (customData == null) return@withContext null
        dataSource.connection.use { c -> c.prepareStatement("SELECT * FROM admob_rewarded_sessions WHERE custom_data=?").use {
            it.setString(1, customData); it.executeQuery().use { r -> if (r.next()) r.session() else null }
        } }
    }
    override suspend fun findRequest(userId: String, requestId: String) = withContext(Dispatchers.IO) {
        dataSource.connection.use { c -> findRequest(c, userId, requestId) }
    }
    private fun findRequest(c: Connection, userId: String, requestId: String): AdMobSession? =
        c.prepareStatement("SELECT * FROM admob_rewarded_sessions WHERE user_id=? AND request_id=?").use {
            it.setObject(1, UUID.fromString(userId)); it.setString(2, requestId)
            it.executeQuery().use { r -> if (r.next()) r.session() else null }
        }
    override suspend fun find(userId: String, id: String) = withContext(Dispatchers.IO) {
        dataSource.connection.use { c -> c.prepareStatement("SELECT * FROM admob_rewarded_sessions WHERE user_id=? AND id=?").use {
            it.setObject(1, UUID.fromString(userId)); it.setString(2, id)
            it.executeQuery().use { r -> if (r.next()) r.session() else null }
        } }
    }
    override suspend fun prepare(userId: String, requestId: String, unit: String, now: Long): AdMobSession = transaction { c ->
        // Serialize owner creation to enforce the outstanding bound even for simultaneous HTTP requests.
        c.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE").use {
            it.setObject(1, UUID.fromString(userId)); it.executeQuery().use { r -> check(r.next()) }
        }
        findRequest(c, userId, requestId)?.let { return@transaction it }
        val pending = c.prepareStatement("SELECT count(*) FROM admob_rewarded_sessions WHERE user_id=? AND provider_transaction_id IS NULL AND expires_at_millis>?").use {
            it.setObject(1, UUID.fromString(userId)); it.setLong(2, now); it.executeQuery().use { r -> r.next(); r.getInt(1) }
        }
        if (pending >= 3) throw AdMobTooManySessions()
        val session = AdMobSession(newAdMobNonce(), userId, requestId, newAdMobNonce(), unit, now, now + SHOW_WINDOW)
        c.prepareStatement("INSERT INTO admob_rewarded_sessions (id,user_id,request_id,custom_data,ad_unit_id,created_at_millis,expires_at_millis) VALUES (?,?,?,?,?,?,?)").use {
            it.setString(1, session.id); it.setObject(2, UUID.fromString(userId)); it.setString(3, requestId)
            it.setString(4, session.customData); it.setString(5, unit); it.setLong(6, now); it.setLong(7, session.expiresAt); it.executeUpdate()
        }
        session
    }
    override suspend fun accept(event: VerifiedAdMobEvent, now: Long): AdMobSession? = transaction { c ->
        val session = c.prepareStatement("SELECT * FROM admob_rewarded_sessions WHERE custom_data=? FOR UPDATE").use {
            it.setString(1, event.customData); it.executeQuery().use { r -> if (r.next()) r.session() else null }
        } ?: return@transaction null
        if (!session.accepts(event)) throw AdMobInvalidRequest()
        if (session.transactionId != null) return@transaction session
        val grant = quota.grantRewardedAd(c, session.userId, RewardedAdProvider.ADMOB_ANDROID, event.transactionId, now)
        // An existing global transaction must not be rebound to another session or owner.
        if (grant.status != RewardedAdBonusStatus.GRANTED) throw AdMobInvalidRequest()
        c.prepareStatement("UPDATE admob_rewarded_sessions SET provider_transaction_id=?,provider_event_at_millis=? WHERE id=?").use {
            it.setString(1, event.transactionId); it.setLong(2, event.timestampMillis); it.setString(3, session.id)
            check(it.executeUpdate() == 1)
        }
        session.copy(transactionId = event.transactionId, eventAt = event.timestampMillis)
    }
    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { c ->
            c.autoCommit = false
            try { val result = block(c); c.commit(); result }
            catch (failure: Throwable) { c.rollback(); throw failure }
            finally { c.autoCommit = true }
        }
    }
    private fun ResultSet.session() = AdMobSession(getString("id"), getString("user_id"), getString("request_id"),
        getString("custom_data"), getString("ad_unit_id"), getLong("created_at_millis"), getLong("expires_at_millis"),
        getString("provider_transaction_id"), getObject("provider_event_at_millis")?.let { (it as Number).toLong() })
}
