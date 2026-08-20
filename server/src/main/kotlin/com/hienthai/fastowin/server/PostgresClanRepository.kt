package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanMemberSnapshot
import com.hienthai.fastowin.protocol.ClanRole
import com.hienthai.fastowin.protocol.ClanSnapshot
import com.hienthai.fastowin.protocol.ClanSummarySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

class PostgresClanRepository(
    private val dataSource: DataSource
) : ClanRepository {
    override suspend fun createClan(ownerId: String, name: String, description: String): String? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val ownerUuid = UUID.fromString(ownerId)
            val clanId = UUID.randomUUID()

            try {
                connection.autoCommit = false
                val check = connection.prepareStatement("SELECT 1 FROM clan_members WHERE user_id = ?").use {
                    it.setObject(1, ownerUuid)
                    it.executeQuery().use { rs -> rs.next() }
                }
                if (check) {
                    connection.rollback()
                    return@withContext null
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

    override suspend fun joinClan(userId: String, clanId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val userUuid = UUID.fromString(userId)
            val clanUuid = UUID.fromString(clanId)
            try {
                connection.prepareStatement("INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', NOW())").use {
                    it.setObject(1, clanUuid)
                    it.setObject(2, userUuid)
                    it.executeUpdate() > 0
                }
            } catch (e: Exception) {
                false
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
                            maxMembers = 50
                        )
                    }
                }
            }

            if (clan != null) {
                val members = mutableListOf<ClanMemberSnapshot>()
                connection.prepareStatement(
                    "SELECT cm.user_id, cm.role, p.display_name, COALESCE(s.elo_rating, 1000) AS trophies " +
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
                                    trophies = rs.getInt("trophies")
                                )
                            )
                        }
                    }
                }
                clan = clan!!.copy(members = members)
            }
            clan
        }
    }

    override suspend fun getClanList(limit: Int, offset: Int): List<ClanSummarySnapshot> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ClanSummarySnapshot>()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT c.id, c.name, c.trophies, COUNT(cm.user_id) AS member_count " +
                "FROM clans c LEFT JOIN clan_members cm ON c.id = cm.clan_id " +
                "GROUP BY c.id ORDER BY c.trophies DESC LIMIT ? OFFSET ?"
            ).use {
                it.setInt(1, limit)
                it.setInt(2, offset)
                it.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(
                            ClanSummarySnapshot(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                memberCount = rs.getInt("member_count"),
                                maxMembers = 50,
                                trophies = rs.getInt("trophies")
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
}
