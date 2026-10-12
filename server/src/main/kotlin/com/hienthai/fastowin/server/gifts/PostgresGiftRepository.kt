package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.server.admin.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class PostgresGiftRepository(private val source: DataSource) : GiftRepository {
    override suspend fun <T> transaction(admin: Boolean, block: GiftTransaction.() -> T): T = withContext(Dispatchers.IO) {
        try {
            source.connection.use { connection ->
                connection.autoCommit = false
                try {
                    if (admin) connection.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187349)") }
                    val result = JdbcGiftTransaction(connection).block()
                    connection.commit(); result
                } catch (error: Throwable) { connection.rollback(); throw error }
            }
        } catch (_: SQLException) { unavailable() }
    }
}
private class JdbcGiftTransaction(private val c: Connection) : GiftTransaction {
    override val admin: AdminTransaction = JdbcAdminTransaction(c)
    override val compensation = com.hienthai.fastowin.server.compensation.JdbcCompensationStore(c)
    private fun <T> rows(sql: String, values: List<Any?> = emptyList(), read: (ResultSet) -> T): List<T> = c.prepareStatement(sql).use { s ->
        values.forEachIndexed { i, value -> s.setObject(i + 1, value) }
        s.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
    }
    private fun update(sql: String, vararg values: Any?) = c.prepareStatement(sql).use { s ->
        values.forEachIndexed { i, value -> s.setObject(i + 1, value) }; s.executeUpdate()
    }
    private fun id(value: String) = UUID.fromString(value)
    private fun recipient(rs: ResultSet) = GiftRecipient(rs.getString("campaign_id"), rs.getString("user_id"), rs.getString("delivery"), rs.getTimestamp("claimed_at")?.time)
    override fun campaign(id: String, lock: Boolean) = rows("SELECT payload FROM admin_gift_campaigns WHERE id=?" + if (lock) " FOR UPDATE" else "", listOf(id(id))) { Json.decodeFromString<GiftCampaign>(it.getString(1)) }.singleOrNull()
    override fun putCampaign(campaign: GiftCampaign) {
        update("""INSERT INTO admin_gift_campaigns(id,version,state,available_at,expires_at,payload,source_type) VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET version=excluded.version,state=excluded.state,available_at=excluded.available_at,expires_at=excluded.expires_at,payload=excluded.payload,source_type=excluded.source_type""",
            id(campaign.id), campaign.version, campaign.state.name, Timestamp(campaign.draft.availableAtEpochMillis), Timestamp(campaign.draft.expiresAtEpochMillis!!), Json.encodeToString(campaign), campaign.walletSourceType())
    }
    override fun campaigns(page: PageRequest): AdminPage<GiftCampaign> {
        val items = rows("SELECT payload FROM admin_gift_campaigns WHERE source_type='ADMIN_GIFT' ORDER BY id DESC LIMIT ? OFFSET ?", listOf(page.pageSize, page.offset)) { Json.decodeFromString<GiftCampaign>(it.getString(1)) }
        return page.result(items, rows("SELECT count(*) FROM admin_gift_campaigns WHERE source_type='ADMIN_GIFT'") { it.getLong(1) }.single())
    }
    override fun activityCoverage(from: Long, until: Long, now: Long) =
        com.hienthai.fastowin.server.activity.JdbcActivityReader(c).coverage(from, until, now)
    override fun activityRecipients(from: Long, until: Long, now: Long) =
        com.hienthai.fastowin.server.activity.JdbcActivityReader(c).registeredRecipients(from, until, now)
    override fun registeredPlayers() = rows("SELECT id,created_at FROM users WHERE account_type='REGISTERED' AND status <> 'DELETED' ORDER BY id") { GiftPlayer(it.getString(1), it.getTimestamp(2).time) }
    override fun activePlayer(id: String) = rows("SELECT id FROM users WHERE id=? AND account_type='REGISTERED' AND status <> 'DELETED' FOR KEY SHARE", listOf(id(id))) { it.getString(1) }.isNotEmpty()
    override fun request(actor: String, requestId: String) = rows("SELECT fingerprint,safe_result FROM admin_gift_requests WHERE actor_id=? AND request_id=?", listOf(id(actor), requestId)) {
        GiftRequestRecord(it.getString(1), Json.decodeFromString<GiftCampaignDetail>(it.getString(2)))
    }.singleOrNull()
    override fun putRequest(actor: String, requestId: String, record: GiftRequestRecord) {
        update("INSERT INTO admin_gift_requests(actor_id,request_id,fingerprint,safe_result) VALUES (?,?,?,?)", id(actor), requestId, record.fingerprint, Json.encodeToString(record.result))
    }
    override fun freeze(campaignId: String, users: List<String>) {
        c.prepareStatement("INSERT INTO admin_gift_recipients(campaign_id,user_id) VALUES (?,?)").use { s ->
            users.forEach { user -> s.setObject(1,id(campaignId)); s.setObject(2,id(user)); s.addBatch() }; s.executeBatch()
        }
    }
    override fun recipient(campaignId: String, userId: String, lock: Boolean) = rows("SELECT * FROM admin_gift_recipients WHERE campaign_id=? AND user_id=?" + if (lock) " FOR UPDATE" else "", listOf(id(campaignId), id(userId)), ::recipient).singleOrNull()
    override fun putRecipient(recipient: GiftRecipient) {
        update("UPDATE admin_gift_recipients SET delivery=?,claimed_at=? WHERE campaign_id=? AND user_id=?", recipient.delivery, recipient.claimedAt?.let(::Timestamp), id(recipient.campaignId), id(recipient.userId))
    }
    override fun pendingCampaigns(now: Long, limit: Int) = rows("""SELECT c.id FROM admin_gift_campaigns c WHERE c.state='PUBLISHED' AND c.available_at <= ?
        AND EXISTS (SELECT 1 FROM admin_gift_recipients r WHERE r.campaign_id=c.id AND r.delivery='PENDING') ORDER BY c.id LIMIT ?""", listOf(Timestamp(now), limit)) { it.getString(1) }
    override fun pendingRecipients(campaignId: String, limit: Int) = rows("SELECT * FROM admin_gift_recipients WHERE campaign_id=? AND delivery='PENDING' ORDER BY user_id LIMIT ? FOR UPDATE", listOf(id(campaignId), limit), ::recipient)
    override fun counters(campaign: GiftCampaign, now: Long): GiftCounters = rows("""SELECT count(*) AS eligible,
        count(*) FILTER (WHERE delivery='PENDING') AS pending,
        count(*) FILTER (WHERE delivery='DELIVERED') AS delivered,
        count(*) FILTER (WHERE claimed_at IS NOT NULL) AS claimed,
        count(*) FILTER (WHERE delivery='SKIPPED') AS skipped,
        count(*) FILTER (WHERE delivery <> 'SKIPPED' AND claimed_at IS NULL) AS unclaimed,
        count(*) FILTER (WHERE delivery='DELIVERED' AND claimed_at IS NULL) AS delivered_unclaimed
        FROM admin_gift_recipients WHERE campaign_id=?""", listOf(id(campaign.id))) {
        val claimed = it.getLong("claimed"); val cancelled = campaign.state == CampaignState.CANCELLED
        GiftCounters(it.getLong("eligible"), if (cancelled) 0 else it.getLong("pending"), it.getLong("delivered"), claimed,
            if (!cancelled && now >= campaign.draft.expiresAtEpochMillis!!) it.getLong("delivered_unclaimed") else 0,
            if (cancelled) it.getLong("unclaimed") else 0, it.getLong("skipped"), Math.multiplyExact(claimed,campaign.draft.gold.toLong()), Math.multiplyExact(claimed,campaign.draft.xp.toLong()), Math.multiplyExact(claimed,campaign.draft.gems.toLong()))
    }.single()
    override fun mailbox(userId: String, now: Long, page: PageRequest): Pair<List<Pair<GiftCampaign, GiftRecipient>>, Long> {
        val from = "FROM admin_gift_recipients r JOIN admin_gift_campaigns c ON c.id=r.campaign_id WHERE r.user_id=? AND r.delivery='DELIVERED' AND c.available_at <= ?"
        val values = listOf(id(userId), Timestamp(now))
        val items = rows("SELECT c.payload,r.* $from ORDER BY c.available_at DESC,c.id DESC LIMIT ? OFFSET ?", values + listOf(page.pageSize,page.offset)) {
            Json.decodeFromString<GiftCampaign>(it.getString("payload")) to recipient(it)
        }
        return items to rows("SELECT count(*) $from", values) { it.getLong(1) }.single()
    }
    override fun mailboxAvailableCount(userId: String, now: Long): Long = rows("""SELECT count(*)
        FROM admin_gift_recipients r JOIN admin_gift_campaigns c ON c.id=r.campaign_id
        WHERE r.user_id=? AND r.delivery='DELIVERED' AND r.claimed_at IS NULL
        AND c.state='PUBLISHED' AND c.available_at <= ? AND c.expires_at > ?""",
        listOf(id(userId), Timestamp(now), Timestamp(now))) { it.getLong(1) }.single()
    override fun balance(userId: String): GiftBalance {
        update("INSERT INTO player_stats(user_id,updated_at) VALUES (?,CURRENT_TIMESTAMP) ON CONFLICT(user_id) DO NOTHING", id(userId))
        return rows("SELECT gold,experience_points,gems FROM player_stats WHERE user_id=? FOR UPDATE", listOf(id(userId))) { GiftBalance(it.getInt(1),it.getInt(2),it.getInt(3)) }.single()
    }
    override fun award(userId: String, campaign: GiftCampaign, now: Long): GiftBalance {
        val before = balance(userId)
        val after = try { GiftBalance(Math.addExact(before.gold,campaign.draft.gold), Math.addExact(before.xp,campaign.draft.xp), Math.addExact(before.gems,campaign.draft.gems)) } catch (_: ArithmeticException) { conflict() }
        update("UPDATE player_stats SET gold=?,experience_points=?,gems=?,updated_at=? WHERE user_id=?", after.gold,after.xp,after.gems,Timestamp(now),id(userId))
        update("INSERT INTO wallet_transactions(id,user_id,source_type,source_id,gold_delta,xp_delta,gems_delta,created_at) VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(),id(userId),campaign.walletSourceType(),campaign.id,campaign.draft.gold,campaign.draft.xp,campaign.draft.gems,Timestamp(now))
        return after
    }
}
