package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanJoinRequestSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanQuestSnapshot
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

private const val CLAN_MAX_MEMBERS = 50

class PostgresClanRepository(
    private val dataSource: DataSource
) : ClanRepository {
    override suspend fun createClan(ownerId: String, name: String, description: String): String? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val ownerUuid = UUID.fromString(ownerId)
            val clanId = UUID.randomUUID()

            try {
                connection.autoCommit = false
                connection.prepareStatement("SELECT 1 FROM users WHERE id = ? FOR UPDATE").use {
                    it.setObject(1, ownerUuid)
                    it.executeQuery().use { result ->
                        if (!result.next()) {
                            connection.rollback()
                            return@withContext null
                        }
                    }
                }
                val check = connection.prepareStatement("SELECT 1 FROM clan_members WHERE user_id = ?").use {
                    it.setObject(1, ownerUuid)
                    it.executeQuery().use { rs -> rs.next() }
                }
                if (check) {
                    connection.rollback()
                    return@withContext null
                }

                connection.prepareStatement("DELETE FROM clan_join_requests WHERE user_id = ?").use {
                    it.setObject(1, ownerUuid)
                    it.executeUpdate()
                }

                connection.prepareStatement("INSERT INTO clans (id, name, description, owner_id, trophies, created_at) VALUES (?, ?, ?, ?, 0, NOW())").use {
                    it.setObject(1, clanId)
                    it.setString(2, name)
                    it.setString(3, description)
                    it.setObject(4, ownerUuid)
                    it.executeUpdate()
                }

                connection.prepareStatement("INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'LEADER', NOW())").use {
                    it.setObject(1, clanId)
                    it.setObject(2, ownerUuid)
                    it.executeUpdate()
                }

                connection.commit()
                clanId.toString()
            } catch (e: Exception) {
                connection.rollback()
                null
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun requestJoinClan(userId: String, clanId: String): ClanJoinRequestResult = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            try {
                connection.autoCommit = false
                val userUuid = UUID.fromString(userId)
                val clanUuid = UUID.fromString(clanId)

                connection.prepareStatement("SELECT 1 FROM users WHERE id = ? FOR UPDATE").use {
                    it.setObject(1, userUuid)
                    it.executeQuery().use { result ->
                        if (!result.next()) {
                            connection.rollback()
                            return@withContext ClanJoinRequestResult.FAILED
                        }
                    }
                }

                val clanInfo = connection.prepareStatement(
                    "SELECT c.owner_id, COUNT(cm.user_id) AS member_count FROM clans c " +
                        "LEFT JOIN clan_members cm ON cm.clan_id = c.id " +
                        "WHERE c.id = ? GROUP BY c.id, c.owner_id"
                ).use {
                    it.setObject(1, clanUuid)
                    it.executeQuery().use { result ->
                        if (result.next()) {
                            result.getObject("owner_id", UUID::class.java) to result.getInt("member_count")
                        } else {
                            null
                        }
                    }
                } ?: run {
                    connection.rollback()
                    return@withContext ClanJoinRequestResult.CLAN_NOT_FOUND
                }

                if (clanInfo.first == userUuid) {
                    connection.rollback()
                    return@withContext ClanJoinRequestResult.OWN_CLAN
                }

                val alreadyMember = connection.prepareStatement("SELECT 1 FROM clan_members WHERE user_id = ?").use {
                    it.setObject(1, userUuid)
                    it.executeQuery().use { result -> result.next() }
                }
                if (alreadyMember) {
                    connection.rollback()
                    return@withContext ClanJoinRequestResult.ALREADY_MEMBER
                }
                if (clanInfo.second >= CLAN_MAX_MEMBERS) {
                    connection.rollback()
                    return@withContext ClanJoinRequestResult.CLAN_FULL
                }

                connection.prepareStatement(
                    "INSERT INTO clan_join_requests (clan_id, user_id, requested_at) VALUES (?, ?, NOW()) " +
                        "ON CONFLICT (clan_id, user_id) DO UPDATE SET requested_at = EXCLUDED.requested_at"
                ).use {
                    it.setObject(1, clanUuid)
                    it.setObject(2, userUuid)
                    it.executeUpdate()
                }
                connection.commit()
                ClanJoinRequestResult.REQUESTED
            } catch (e: Exception) {
                connection.rollback()
                ClanJoinRequestResult.FAILED
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun respondJoinRequest(
        clanId: String,
        ownerId: String,
        userId: String,
        accept: Boolean
    ): ClanJoinResponseResult = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            try {
                connection.autoCommit = false
                val clanUuid = UUID.fromString(clanId)
                val ownerUuid = UUID.fromString(ownerId)
                val userUuid = UUID.fromString(userId)

                connection.prepareStatement("SELECT 1 FROM users WHERE id = ? FOR UPDATE").use {
                    it.setObject(1, userUuid)
                    it.executeQuery().use { result ->
                        if (!result.next()) {
                            connection.rollback()
                            return@withContext ClanJoinResponseResult.FAILED
                        }
                    }
                }

                val ownerMatches = connection.prepareStatement(
                    "SELECT 1 FROM clans WHERE id = ? AND owner_id = ? FOR UPDATE"
                ).use {
                    it.setObject(1, clanUuid)
                    it.setObject(2, ownerUuid)
                    it.executeQuery().use { result -> result.next() }
                }
                if (!ownerMatches) {
                    connection.rollback()
                    return@withContext ClanJoinResponseResult.FAILED
                }

                val requestExists = connection.prepareStatement(
                    "SELECT 1 FROM clan_join_requests WHERE clan_id = ? AND user_id = ? FOR UPDATE"
                ).use {
                    it.setObject(1, clanUuid)
                    it.setObject(2, userUuid)
                    it.executeQuery().use { result -> result.next() }
                }
                if (!requestExists) {
                    connection.rollback()
                    return@withContext ClanJoinResponseResult.REQUEST_NOT_FOUND
                }

                if (!accept) {
                    connection.prepareStatement(
                        "DELETE FROM clan_join_requests WHERE clan_id = ? AND user_id = ?"
                    ).use {
                        it.setObject(1, clanUuid)
                        it.setObject(2, userUuid)
                        it.executeUpdate()
                    }
                    connection.commit()
                    return@withContext ClanJoinResponseResult.REJECTED
                }

                val alreadyMember = connection.prepareStatement("SELECT 1 FROM clan_members WHERE user_id = ?").use {
                    it.setObject(1, userUuid)
                    it.executeQuery().use { result -> result.next() }
                }
                if (alreadyMember) {
                    connection.prepareStatement("DELETE FROM clan_join_requests WHERE user_id = ?").use {
                        it.setObject(1, userUuid)
                        it.executeUpdate()
                    }
                    connection.commit()
                    return@withContext ClanJoinResponseResult.ALREADY_MEMBER
                }

                val memberCount = connection.prepareStatement(
                    "SELECT COUNT(*) FROM clan_members WHERE clan_id = ?"
                ).use {
                    it.setObject(1, clanUuid)
                    it.executeQuery().use { result -> result.next(); result.getInt(1) }
                }
                if (memberCount >= CLAN_MAX_MEMBERS) {
                    connection.rollback()
                    return@withContext ClanJoinResponseResult.CLAN_FULL
                }

                connection.prepareStatement(
                    "INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', NOW())"
                ).use {
                    it.setObject(1, clanUuid)
                    it.setObject(2, userUuid)
                    it.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM clan_join_requests WHERE user_id = ?").use {
                    it.setObject(1, userUuid)
                    it.executeUpdate()
                }
                connection.commit()
                ClanJoinResponseResult.APPROVED
            } catch (e: Exception) {
                connection.rollback()
                ClanJoinResponseResult.FAILED
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun getPendingJoinClanIds(userId: String): List<String> = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT clan_id FROM clan_join_requests WHERE user_id = ? ORDER BY requested_at DESC"
            ).use {
                it.setObject(1, UUID.fromString(userId))
                it.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.getString("clan_id"))
                    }
                }
            }
        }
    }

    override suspend fun leaveClan(userId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            try {
                val userUuid = UUID.fromString(userId)
                
                // If leader leaves, what happens? For simplicity, we just let them leave.
                // Or maybe prevent leader from leaving if not empty.
                connection.prepareStatement("DELETE FROM clan_members WHERE user_id = ?").use {
                    it.setObject(1, userUuid)
                    it.executeUpdate() > 0
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun getClanByUserId(userId: String): ClanSnapshot? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val userUuid = UUID.fromString(userId)
            var clanId: String? = null
            connection.prepareStatement("SELECT clan_id FROM clan_members WHERE user_id = ?").use {
                it.setObject(1, userUuid)
                it.executeQuery().use { rs ->
                    if (rs.next()) clanId = rs.getString("clan_id")
                }
            }
            if (clanId != null) getClanById(clanId!!) else null
        }
    }

    override suspend fun getClanById(clanId: String): ClanSnapshot? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val clanUuid = UUID.fromString(clanId)
            var clan: ClanSnapshot? = null
            connection.prepareStatement("SELECT * FROM clans WHERE id = ?").use {
                it.setObject(1, clanUuid)
                it.executeQuery().use { rs ->
                    if (rs.next()) {
                        clan = ClanSnapshot(
                            id = rs.getString("id"),
                            name = rs.getString("name"),
                            description = rs.getString("description") ?: "",
                            ownerId = rs.getString("owner_id"),
                            members = emptyList(),
                            trophies = rs.getInt("trophies"),
                            logoId = rs.getString("logo_id"),
                            maxMembers = 50,
                            quest = ClanQuestSnapshot(
                                progress = rs.getInt("quest_progress"),
                                target = rs.getInt("quest_target"),
                                rewardGold = rs.getInt("quest_reward_gold"),
                                rewardXp = rs.getInt("quest_reward_xp"),
                                rewardGems = rs.getInt("quest_reward_gems")
                            )
                        )
                    }
                }
            }

            if (clan != null) {
                val members = mutableListOf<ClanMemberSnapshot>()
                connection.prepareStatement(
                    "SELECT cm.user_id, cm.role, cm.quest_contribution, cm.quest_reward_claimed, " +
                    "p.display_name, COALESCE(s.elo_rating, 1000) AS trophies " +
                    "FROM clan_members cm JOIN profiles p ON cm.user_id = p.user_id " +
                    "LEFT JOIN player_stats s ON cm.user_id = s.user_id " +
                    "WHERE cm.clan_id = ?"
                ).use {
                    it.setObject(1, clanUuid)
                    it.executeQuery().use { rs ->
                        while (rs.next()) {
                            members.add(
                                ClanMemberSnapshot(
                                    userId = rs.getString("user_id"),
                                    displayName = rs.getString("display_name"),
                                    role = ClanRole.valueOf(rs.getString("role")),
                                    trophies = rs.getInt("trophies"),
                                    questContribution = rs.getInt("quest_contribution"),
                                    questRewardClaimed = rs.getBoolean("quest_reward_claimed")
                                )
                            )
                        }
                    }
                }
                val joinRequests = mutableListOf<ClanJoinRequestSnapshot>()
                connection.prepareStatement(
                    "SELECT r.user_id, p.display_name, p.player_code, r.requested_at " +
                        "FROM clan_join_requests r JOIN profiles p ON p.user_id = r.user_id " +
                        "WHERE r.clan_id = ? ORDER BY r.requested_at ASC"
                ).use {
                    it.setObject(1, clanUuid)
                    it.executeQuery().use { rs ->
                        while (rs.next()) {
                            joinRequests.add(
                                ClanJoinRequestSnapshot(
                                    userId = rs.getString("user_id"),
                                    displayName = rs.getString("display_name"),
                                    playerCode = rs.getString("player_code"),
                                    requestedAtEpochMillis = rs.getTimestamp("requested_at").time
                                )
                            )
                        }
                    }
                }
                clan = clan!!.copy(members = members, joinRequests = joinRequests)
            }
            clan
        }
    }

    override suspend fun getClanList(limit: Int, offset: Int, query: String?): List<ClanSummarySnapshot> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ClanSummarySnapshot>()
        dataSource.connection.use { connection ->
            val sql = if (query != null && query.isNotBlank()) {
                "SELECT c.id, c.name, c.trophies, c.logo_id, COUNT(cm.user_id) AS member_count " +
                "FROM clans c LEFT JOIN clan_members cm ON c.id = cm.clan_id " +
                "WHERE c.name ILIKE ? " +
                "GROUP BY c.id ORDER BY c.trophies DESC LIMIT ? OFFSET ?"
            } else {
                "SELECT c.id, c.name, c.trophies, c.logo_id, COUNT(cm.user_id) AS member_count " +
                "FROM clans c LEFT JOIN clan_members cm ON c.id = cm.clan_id " +
                "GROUP BY c.id ORDER BY c.trophies DESC LIMIT ? OFFSET ?"
            }
            connection.prepareStatement(sql).use {
                var index = 1
                if (query != null && query.isNotBlank()) {
                    it.setString(index++, "%$query%")
                }
                it.setInt(index++, limit)
                it.setInt(index++, offset)
                it.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(
                            ClanSummarySnapshot(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                memberCount = rs.getInt("member_count"),
                                maxMembers = 50,
                                trophies = rs.getInt("trophies"),
                                logoId = rs.getString("logo_id")
                            )
                        )
                    }
                }
            }
        }
        list
    }

    override suspend fun kickMember(clanId: String, currentUserId: String, targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            try {
                connection.prepareStatement("DELETE FROM clan_members WHERE clan_id = ? AND user_id = ?").use {
                    it.setObject(1, UUID.fromString(clanId))
                    it.setObject(2, UUID.fromString(targetUserId))
                    it.executeUpdate() > 0
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun updateLogoId(clanId: String, logoId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE clans SET logo_id = ? WHERE id = ?").use { statement ->
                statement.setString(1, logoId)
                statement.setObject(2, UUID.fromString(clanId))
                statement.executeUpdate() > 0
            }
        }    }

    override suspend fun addClanTrophies(clanId: String, amount: Int): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE clans SET trophies = trophies + ? WHERE id = ?").use {
                it.setInt(1, amount)
                it.setObject(2, UUID.fromString(clanId))
                it.executeUpdate() > 0
            }
        }
    }

    override suspend fun addQuestProgress(clanId: String, userId: String, amount: Int): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val clanUuid = UUID.fromString(clanId)
                val userUuid = UUID.fromString(userId)
                
                connection.prepareStatement("UPDATE clans SET quest_progress = quest_progress + ? WHERE id = ?").use {
                    it.setInt(1, amount)
                    it.setObject(2, clanUuid)
                    it.executeUpdate()
                }

                connection.prepareStatement("UPDATE clan_members SET quest_contribution = quest_contribution + ? WHERE clan_id = ? AND user_id = ?").use {
                    it.setInt(1, amount)
                    it.setObject(2, clanUuid)
                    it.setObject(3, userUuid)
                    it.executeUpdate()
                }
                
                connection.commit()
                true
            } catch (e: Exception) {
                connection.rollback()
                false
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun claimQuestReward(clanId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val clanUuid = UUID.fromString(clanId)
                val userUuid = UUID.fromString(userId)
                val reward = connection.prepareStatement(
                    """
                    SELECT c.quest_progress, c.quest_target, c.quest_reward_gold,
                           c.quest_reward_xp, c.quest_reward_gems, cm.quest_reward_claimed
                    FROM clans c
                    JOIN clan_members cm ON cm.clan_id = c.id AND cm.user_id = ?
                    WHERE c.id = ?
                    FOR UPDATE OF c, cm
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, userUuid)
                    statement.setObject(2, clanUuid)
                    statement.executeQuery().use { result ->
                        if (!result.next() ||
                            result.getBoolean("quest_reward_claimed") ||
                            result.getInt("quest_progress") < result.getInt("quest_target")
                        ) null else Triple(
                            result.getInt("quest_reward_gold"),
                            result.getInt("quest_reward_xp"),
                            result.getInt("quest_reward_gems")
                        )
                    }
                } ?: run {
                    connection.rollback()
                    return@withContext false
                }
                connection.prepareStatement(
                    """
                    INSERT INTO player_stats (
                        user_id, gold, experience_points, gems, updated_at
                    ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id) DO UPDATE SET
                        gold = player_stats.gold + EXCLUDED.gold,
                        experience_points = player_stats.experience_points + EXCLUDED.experience_points,
                        gems = player_stats.gems + EXCLUDED.gems,
                        updated_at = EXCLUDED.updated_at
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, userUuid)
                    statement.setInt(2, reward.first)
                    statement.setInt(3, reward.second)
                    statement.setInt(4, reward.third)
                    check(statement.executeUpdate() == 1)
                }
                connection.prepareStatement(
                    """
                    INSERT INTO wallet_transactions (
                        id, user_id, source_type, source_id, gold_delta, gems_delta, xp_delta
                    ) VALUES (?, ?, 'CLAN_QUEST', ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, userUuid)
                    statement.setString(3, clanId)
                    statement.setInt(4, reward.first)
                    statement.setInt(5, reward.third)
                    statement.setInt(6, reward.second)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "UPDATE clan_members SET quest_reward_claimed = TRUE WHERE clan_id = ? AND user_id = ?"
                ).use { statement ->
                    statement.setObject(1, clanUuid)
                    statement.setObject(2, userUuid)
                    check(statement.executeUpdate() == 1)
                }
                connection.commit()
                true
            } catch (error: Throwable) {
                connection.rollback()
                false
            } finally {
                connection.autoCommit = true
            }
        }
    }
}
