package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PlayQuotaSnapshot
import com.hienthai.fastowin.protocol.RewardedAdBonusStatus
import com.hienthai.fastowin.protocol.RewardedAdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Date
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class PostgresPlayQuotaRepository(
    private val dataSource: DataSource
) : PlayQuotaRepository {
    override suspend fun getSnapshot(userId: String, nowMillis: Long): PlayQuotaSnapshot =
        withContext(Dispatchers.IO) {
            val parsedUserId = UUID.fromString(userId)
            dataSource.connection.use { connection ->
                loadSnapshots(connection, listOf(parsedUserId), nowMillis).getValue(userId)
            }
        }

    override suspend fun canPlay(userIds: Set<String>, nowMillis: Long): PlayQuotaCheck =
        withContext(Dispatchers.IO) {
            val parsedUserIds = parseUserIds(userIds)
            dataSource.connection.use { connection ->
                val quotas = loadSnapshots(connection, parsedUserIds, nowMillis)
                val exhaustedUserIds = quotas.filterValues { it.remainingMatches <= 0 }.keys
                PlayQuotaCheck(
                    allowed = exhaustedUserIds.isEmpty(),
                    quotas = quotas,
                    exhaustedUserIds = exhaustedUserIds
                )
            }
        }

    override suspend fun consumeMatch(
        userIds: Set<String>,
        matchId: String,
        nowMillis: Long
    ): PlayQuotaConsumption = consume(userIds, matchId, nowMillis, false)

    override suspend fun prepareMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption =
        consume(userIds, matchId, nowMillis, true)

    private suspend fun consume(userIds: Set<String>, matchId: String, nowMillis: Long, reserved: Boolean): PlayQuotaConsumption = withContext(Dispatchers.IO) {
        val parsedUserIds = parseUserIds(userIds)
        val parsedMatchId = UUID.fromString(matchId)
        val quotaDate = PlayQuotaRules.quotaDate(nowMillis)
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                lockMatch(connection, matchId)
                val existingParticipants = connection.prepareStatement("SELECT user_id::text FROM play_quota_match_consumptions WHERE match_id=?").use { s ->
                    s.setObject(1, parsedMatchId)
                    s.executeQuery().use { r -> buildSet { while (r.next()) add(r.getString(1)) } }
                }
                check(existingParticipants.isEmpty() || existingParticipants == userIds) { "Quota match participants changed" }
                lockUsers(connection, parsedUserIds)
                ensureDailyRows(connection, parsedUserIds, quotaDate)
                val quotas = loadSnapshots(connection, parsedUserIds, nowMillis, lockRows = true, lockCredits = true)
                val existingReceipts = existingMatchReceiptCount(
                    connection = connection,
                    userIds = parsedUserIds,
                    matchId = parsedMatchId
                )
                if (existingReceipts == parsedUserIds.size) {
                    connection.commit()
                    return@withContext PlayQuotaConsumption(
                        status = PlayQuotaConsumptionStatus.ALREADY_CONSUMED,
                        quotas = quotas
                    )
                }
                check(existingReceipts == 0) {
                    "Partial quota consumption detected for match $matchId."
                }

                val exhaustedUserIds = quotas.filterValues { it.remainingMatches <= 0 }.keys
                if (exhaustedUserIds.isNotEmpty()) {
                    connection.rollback()
                    return@withContext PlayQuotaConsumption(
                        status = PlayQuotaConsumptionStatus.EXHAUSTED,
                        quotas = quotas,
                        exhaustedUserIds = exhaustedUserIds
                    )
                }

                val creditUsers = parsedUserIds.filter { quotas.getValue(it.toString()).dailyMatchesRemaining == 0 }.toSet()
                val dailyUsers = parsedUserIds.filterNot { it in creditUsers }
                insertMatchReceipts(connection, parsedUserIds, parsedMatchId, quotaDate, creditUsers)
                if (reserved) connection.prepareStatement("UPDATE play_quota_match_consumptions SET reservation_state='RESERVED' WHERE match_id=?").use {
                    it.setObject(1, parsedMatchId); it.executeUpdate()
                }
                if (dailyUsers.isNotEmpty()) incrementConsumedMatches(connection, dailyUsers, quotaDate)
                creditUsers.forEach { changeBankedMatches(connection, it, -1) }
                val updatedQuotas = loadSnapshots(connection, parsedUserIds, nowMillis)
                connection.commit()
                PlayQuotaConsumption(
                    status = PlayQuotaConsumptionStatus.CONSUMED,
                    quotas = updatedQuotas
                )
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun lockMatch(connection: Connection, matchId: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use {
            it.setString(1, "quota:$matchId"); it.execute()
        }
    }

    private fun lockUsers(connection: Connection, userIds: List<UUID>) {
        val placeholders = userIds.joinToString(",") { "?" }
        // Wallet mutations and account deletion lock users before stats. Acquire the
        // receipt foreign-key lock first so quota stats locks cannot invert that order.
        connection.prepareStatement(
            "SELECT id FROM users WHERE id IN ($placeholders) ORDER BY id FOR KEY SHARE"
        ).use { statement ->
            userIds.forEachIndexed { index, userId -> statement.setObject(index + 1, userId) }
            statement.executeQuery().use { rows -> while (rows.next()) { /* Hold each participant lock until commit. */ } }
        }
    }

    override suspend fun finalizeMatch(userIds: Set<String>, matchId: String) = reconcileReservation(userIds, matchId, setOf(matchId))
    override suspend fun releaseMatch(userIds: Set<String>, matchId: String, durableMatchIds: Set<String>) =
        reconcileReservation(userIds, matchId, durableMatchIds)

    override suspend fun recoverPreparedMatches(durableMatchIds: Set<String>) {
        val reservations = withContext(Dispatchers.IO) {
            dataSource.connection.use { c ->
                c.prepareStatement("SELECT match_id, user_id FROM play_quota_match_consumptions WHERE reservation_state='RESERVED'").use { s ->
                    s.executeQuery().use { r -> buildList { while(r.next()) add(r.getString(1) to r.getString(2)) } }
                }
            }
        }.groupBy({ it.first }, { it.second })
        reservations.forEach { (match, users) -> reconcileReservation(users.toSet(), match, durableMatchIds) }
    }

    private suspend fun reconcileReservation(userIds: Set<String>, matchId: String, durableMatchIds: Set<String>) = withContext(Dispatchers.IO) {
        dataSource.connection.use { c ->
            c.autoCommit = false
            try {
                lockMatch(c, matchId)
                lockUsers(c, parseUserIds(userIds))
                val receipts = c.prepareStatement("SELECT user_id, quota_date, reservation_state, used_play_credit FROM play_quota_match_consumptions WHERE match_id=? ORDER BY user_id FOR UPDATE").use { s ->
                    s.setObject(1, UUID.fromString(matchId))
                    s.executeQuery().use { r ->
                        buildList {
                            while (r.next()) add(ReservationReceipt(r.getObject(1, UUID::class.java), r.getDate(2), r.getString(3), r.getBoolean(4)))
                        }
                    }
                }
                if (receipts.any { it.state == "RESERVED" }) {
                    check(receipts.map { it.userId.toString() }.toSet() == userIds && receipts.all { it.state == "RESERVED" }) { "Reservation identity mismatch" }
                    // Durable results and snapshots win over a stale caller, including uncertain save outcomes.
                    val durable = matchId in durableMatchIds || c.prepareStatement("""
                        SELECT EXISTS(SELECT 1 FROM matches WHERE id=?) OR EXISTS(
                            SELECT 1 FROM active_room_snapshots WHERE (state_json->>'matchId'=? AND state_json->>'startedAtEpochMillis' IS NOT NULL)
                              OR state_json->'pendingCompletedMatch'->>'matchId'=?)
                    """.trimIndent()).use { s ->
                        s.setObject(1, UUID.fromString(matchId)); s.setString(2, matchId); s.setString(3, matchId)
                        s.executeQuery().use { r -> r.next(); r.getBoolean(1) }
                    }
                    if (durable) c.prepareStatement("UPDATE play_quota_match_consumptions SET reservation_state='FINALIZED' WHERE match_id=?").use {
                        it.setObject(1, UUID.fromString(matchId)); it.executeUpdate()
                    } else {
                        receipts.filterNot { it.usedCredit }.forEach { (user, date) ->
                            c.prepareStatement("UPDATE daily_play_quotas SET matches_consumed=matches_consumed-1, updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND quota_date=?").use {
                                it.setObject(1, user); it.setDate(2, date); check(it.executeUpdate() == 1)
                            }
                        }
                        receipts.filter { it.usedCredit }.forEach { changeBankedMatches(c, it.userId, 1) }
                        c.prepareStatement("DELETE FROM play_quota_match_consumptions WHERE match_id=? AND reservation_state='RESERVED'").use {
                            it.setObject(1, UUID.fromString(matchId)); it.executeUpdate()
                        }
                    }
                }
                c.commit()
            } catch (failure: Throwable) { c.rollback(); throw failure }
        }
    }

    override suspend fun grantRewardedAd(
        userId: String,
        provider: RewardedAdProvider,
        providerTransactionId: String,
        nowMillis: Long
    ): PlayQuotaGrant = withContext(Dispatchers.IO) {
        require(providerTransactionId.isNotBlank()) { "Provider transaction ID must not be blank." }
        require(providerTransactionId.length <= 256) { "Provider transaction ID is too long." }
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val grant = grantRewardedAd(connection, userId, provider, providerTransactionId, nowMillis)
                connection.commit()
                grant
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Reuses a caller-owned transaction for durable SSV acceptance and the existing global receipt. */
    internal fun grantRewardedAd(connection: Connection, userId: String, provider: RewardedAdProvider,
        providerTransactionId: String, nowMillis: Long): PlayQuotaGrant {
        require(providerTransactionId.isNotBlank() && providerTransactionId.length <= 256)
        val parsedUserId = UUID.fromString(userId)
        val quotaDate = PlayQuotaRules.quotaDate(nowMillis)
        ensureDailyRows(connection, listOf(parsedUserId), quotaDate)
        val quota = loadSnapshots(
            connection,
            listOf(parsedUserId),
            nowMillis,
            lockRows = true
        ).getValue(userId)
        val inserted = connection.prepareStatement(
            """
            INSERT INTO rewarded_ad_grants (
                id, user_id, provider, provider_transaction_id, matches_granted
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (provider, provider_transaction_id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, parsedUserId)
            statement.setString(3, provider.name)
            statement.setString(4, providerTransactionId)
            statement.setInt(5, PlayQuotaRules.REWARDED_AD_MATCHES)
            statement.executeUpdate()
        }
        if (inserted == 0) {
            return PlayQuotaGrant(
                status = RewardedAdBonusStatus.ALREADY_GRANTED,
                quota = quota
            )
        }

        connection.prepareStatement(
            """
            UPDATE daily_play_quotas
            SET bonus_matches_granted = bonus_matches_granted + ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND quota_date = ?
            """.trimIndent()
        ).use { statement ->
            statement.setInt(1, PlayQuotaRules.REWARDED_AD_MATCHES)
            statement.setObject(2, parsedUserId)
            statement.setDate(3, Date.valueOf(quotaDate))
            check(statement.executeUpdate() == 1)
        }
        val updatedBonus = quota.bonusMatchesGranted + PlayQuotaRules.REWARDED_AD_MATCHES
        return PlayQuotaGrant(
            status = RewardedAdBonusStatus.GRANTED,
            quota = quota.copy(
                bonusMatchesGranted = updatedBonus,
                remainingMatches = PlayQuotaRules.totalRemaining(
                    PlayQuotaRules.remaining(quota.matchesConsumed, updatedBonus), quota.bankedMatches
                )
            )
        )
    }

    private fun parseUserIds(userIds: Set<String>): List<UUID> {
        require(userIds.isNotEmpty()) { "At least one user is required." }
        return userIds.map(UUID::fromString).sortedBy(UUID::toString)
    }

    private fun ensureDailyRows(
        connection: Connection,
        userIds: List<UUID>,
        quotaDate: LocalDate
    ) {
        connection.prepareStatement(
            """
            INSERT INTO daily_play_quotas (user_id, quota_date)
            VALUES (?, ?)
            ON CONFLICT (user_id, quota_date) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            userIds.forEach { userId ->
                statement.setObject(1, userId)
                statement.setDate(2, Date.valueOf(quotaDate))
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun loadSnapshots(
        connection: Connection,
        userIds: List<UUID>,
        nowMillis: Long,
        lockRows: Boolean = false,
        lockCredits: Boolean = false
    ): Map<String, PlayQuotaSnapshot> {
        val quotaDate = PlayQuotaRules.quotaDate(nowMillis)
        val rows = mutableMapOf<UUID, DailyQuotaRow>()
        val placeholders = userIds.joinToString(",") { "?" }
        val lockClause = if (lockRows) " FOR UPDATE" else ""
        connection.prepareStatement(
            """
            SELECT user_id, matches_consumed, bonus_matches_granted
            FROM daily_play_quotas
            WHERE quota_date = ? AND user_id IN ($placeholders)
            ORDER BY user_id$lockClause
            """.trimIndent()
        ).use { statement ->
            statement.setDate(1, Date.valueOf(quotaDate))
            userIds.forEachIndexed { index, userId -> statement.setObject(index + 2, userId) }
            statement.executeQuery().use { result ->
                while (result.next()) {
                    rows[result.getObject("user_id", UUID::class.java)] = DailyQuotaRow(
                        matchesConsumed = result.getInt("matches_consumed"),
                        bonusMatchesGranted = result.getInt("bonus_matches_granted")
                    )
                }
            }
        }
        val balances = loadCreditBalances(connection, userIds, placeholders, lockCredits)
        return userIds.associate { userId ->
            val row = rows[userId] ?: DailyQuotaRow()
            val daily = PlayQuotaRules.remaining(row.matchesConsumed, row.bonusMatchesGranted)
            val banked = balances[userId] ?: 0
            userId.toString() to PlayQuotaSnapshot(
                quotaDate = quotaDate.toString(),
                baseMatches = PlayQuotaRules.BASE_MATCHES,
                matchesConsumed = row.matchesConsumed,
                bonusMatchesGranted = row.bonusMatchesGranted,
                remainingMatches = PlayQuotaRules.totalRemaining(daily, banked),
                nextResetAtEpochMillis = PlayQuotaRules.nextResetAtEpochMillis(nowMillis),
                bankedMatches = banked
            )
        }
    }

    private fun loadCreditBalances(connection: Connection, ids: List<UUID>, placeholders: String, lock: Boolean): Map<UUID, Long> {
        val lockClause = if (lock) " FOR UPDATE" else ""
        return connection.prepareStatement(
            "SELECT user_id, play_credits FROM player_stats WHERE user_id IN ($placeholders) ORDER BY user_id$lockClause"
        ).use { statement ->
            ids.forEachIndexed { index, id -> statement.setObject(index + 1, id) }
            statement.executeQuery().use { rows ->
                buildMap { while (rows.next()) put(rows.getObject(1, UUID::class.java), rows.getLong(2)) }
            }
        }
    }

    private fun changeBankedMatches(connection: Connection, userId: UUID, amount: Int) {
        connection.prepareStatement("UPDATE player_stats SET play_credits=play_credits+? WHERE user_id=?").use { statement ->
            statement.setInt(1, amount)
            statement.setObject(2, userId)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun existingMatchReceiptCount(
        connection: Connection,
        userIds: List<UUID>,
        matchId: UUID
    ): Int {
        val placeholders = userIds.joinToString(",") { "?" }
        return connection.prepareStatement(
            """
            SELECT COUNT(*)
            FROM play_quota_match_consumptions
            WHERE match_id = ? AND user_id IN ($placeholders)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, matchId)
            userIds.forEachIndexed { index, userId -> statement.setObject(index + 2, userId) }
            statement.executeQuery().use { result ->
                check(result.next())
                result.getInt(1)
            }
        }
    }

    private fun insertMatchReceipts(
        connection: Connection,
        userIds: List<UUID>,
        matchId: UUID,
        quotaDate: LocalDate,
        creditUsers: Set<UUID>
    ) {
        connection.prepareStatement(
            """
            INSERT INTO play_quota_match_consumptions (user_id, match_id, quota_date, used_play_credit)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            userIds.forEach { userId ->
                statement.setObject(1, userId)
                statement.setObject(2, matchId)
                statement.setDate(3, Date.valueOf(quotaDate))
                statement.setBoolean(4, userId in creditUsers)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun incrementConsumedMatches(
        connection: Connection,
        userIds: List<UUID>,
        quotaDate: LocalDate
    ) {
        val placeholders = userIds.joinToString(",") { "?" }
        connection.prepareStatement(
            """
            UPDATE daily_play_quotas
            SET matches_consumed = matches_consumed + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE quota_date = ? AND user_id IN ($placeholders)
            """.trimIndent()
        ).use { statement ->
            statement.setDate(1, Date.valueOf(quotaDate))
            userIds.forEachIndexed { index, userId -> statement.setObject(index + 2, userId) }
            check(statement.executeUpdate() == userIds.size)
        }
    }

    private data class DailyQuotaRow(
        val matchesConsumed: Int = 0,
        val bonusMatchesGranted: Int = 0
    )

    private data class ReservationReceipt(val userId: UUID, val date: Date, val state: String, val usedCredit: Boolean)
}
