package com.hienthai.fastowin.server.gifts

import com.hienthai.fastowin.server.admin.*
import com.hienthai.fastowin.protocol.GiftCodeReward
import com.hienthai.fastowin.protocol.PlayCreditOffer
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
                    // Shared race/mission gate precedes campaign, recipient, user and wallet row locks.
                    connection.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(7421092187355)") }
                    val result = JdbcGiftTransaction(connection).block()
                    connection.commit(); result
                } catch (error: Throwable) { connection.rollback(); throw error }
            }
        } catch (_: SQLException) { unavailable() }
    }
}
internal class JdbcGiftTransaction(private val c: Connection) : GiftTransaction {
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
    override fun rewardActionPlayer(id: String) = rows(
        "SELECT id FROM users WHERE id=? AND account_type='REGISTERED' AND status='ACTIVE' FOR SHARE", listOf(id(id))
    ) { true }.isNotEmpty()
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
            if (cancelled) it.getLong("unclaimed") else 0, it.getLong("skipped"), Math.multiplyExact(claimed,campaign.draft.gold.toLong()), Math.multiplyExact(claimed,campaign.draft.xp.toLong()), Math.multiplyExact(claimed,campaign.draft.gems.toLong()), Math.multiplyExact(claimed,campaign.draft.plays.toLong()))
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
        val draft = campaign.draft
        return awardReward(userId, GiftCodeReward(draft.gold, draft.xp, draft.gems, draft.plays), campaign.walletSourceType(), campaign.id, now)
    }

    private fun awardReward(userId: String, reward: GiftCodeReward, source: String, sourceId: String, now: Long): GiftBalance {
        val before = balance(userId)
        val after = try {
            GiftBalance(Math.addExact(before.gold, reward.gold), Math.addExact(before.xp, reward.xp), Math.addExact(before.gems, reward.gems))
        } catch (_: ArithmeticException) { conflict() }
        update("UPDATE player_stats SET gold=?,experience_points=?,gems=?,play_credits=play_credits+?,updated_at=? WHERE user_id=?",
            after.gold, after.xp, after.gems, reward.plays, Timestamp(now), id(userId))
        walletEntry(userId, source, sourceId, reward, now)
        return after
    }

    private fun walletEntry(userId: String, source: String, sourceId: String, change: GiftCodeReward, now: Long) {
        update("""INSERT INTO wallet_transactions
            (id,user_id,source_type,source_id,gold_delta,xp_delta,gems_delta,play_credit_delta,created_at)
            VALUES (?,?,?,?,?,?,?,?,?)""", UUID.randomUUID(), id(userId), source, sourceId,
            change.gold, change.xp, change.gems, change.plays, Timestamp(now))
    }

    override fun exchangePlayCredits(userId: String, offer: PlayCreditOffer, requestId: String, now: Long) {
        val current = balance(userId)
        val previous = rows("""SELECT gems_delta,play_credit_delta FROM wallet_transactions
            WHERE user_id=? AND source_type='PLAY_CREDIT_EXCHANGE' AND source_id=?""", listOf(id(userId), requestId)) {
            it.getInt(1) to it.getInt(2)
        }.singleOrNull()
        if (previous != null) {
            if (previous != (-offer.gems to offer.plays)) conflict()
            return
        }
        if (current.gems < offer.gems) throw AdminException(409, "INSUFFICIENT_GEMS", "Insufficient gems")
        update("UPDATE player_stats SET gems=gems-?,play_credits=play_credits+?,updated_at=? WHERE user_id=?",
            offer.gems, offer.plays, Timestamp(now), id(userId))
        walletEntry(userId, "PLAY_CREDIT_EXCHANGE", requestId, GiftCodeReward(gems = -offer.gems, plays = offer.plays), now)
    }

    private fun readCode(row: ResultSet): GiftCode = Json.decodeFromString<GiftCode>(row.getString(1))
        .copy(claims = row.getInt(2), stopped = row.getBoolean(3))

    override fun listCodes(page: PageRequest): AdminPage<GiftCode> {
        val results = rows("SELECT payload,claims,stopped FROM gift_codes ORDER BY code LIMIT ? OFFSET ?",
            listOf(page.pageSize, page.offset), ::readCode)
        val count = rows("SELECT count(*) FROM gift_codes") { it.getLong(1) }.single()
        return page.result(results, count)
    }

    override fun code(code: String) = rows(
        "SELECT payload,claims,stopped FROM gift_codes WHERE code=? FOR UPDATE", listOf(code), ::readCode
    ).singleOrNull()

    override fun insertCode(code: GiftCode) {
        update("INSERT INTO gift_codes(code,payload) VALUES (?,?)", code.code, Json.encodeToString(code))
    }

    override fun stopCode(code: String) {
        update("UPDATE gift_codes SET stopped=TRUE WHERE code=?", code)
    }

    override fun giftCodeMutation(actor: String, requestId: String) = rows(
        "SELECT fingerprint FROM gift_code_admin_requests WHERE actor_id=? AND request_id=?", listOf(id(actor), requestId)
    ) { it.getString(1) }.singleOrNull()

    override fun recordGiftCodeMutation(actor: String, requestId: String, fingerprint: String) {
        update("INSERT INTO gift_code_admin_requests(actor_id,request_id,fingerprint) VALUES (?,?,?)", id(actor), requestId, fingerprint)
    }

    override fun redeemed(code: String, userId: String) = rows(
        "SELECT 1 FROM gift_code_redemptions WHERE code=? AND user_id=?", listOf(code, id(userId))
    ) { true }.isNotEmpty()

    override fun redeem(code: GiftCode, userId: String, now: Long) {
        awardReward(userId, code.reward, "GIFT_CODE", com.hienthai.fastowin.server.hashToken(code.code), now)
        update("INSERT INTO gift_code_redemptions(code,user_id,redeemed_at) VALUES (?,?,?)", code.code, id(userId), Timestamp(now))
        update("UPDATE gift_codes SET claims=claims+1 WHERE code=?", code.code)
    }
}
