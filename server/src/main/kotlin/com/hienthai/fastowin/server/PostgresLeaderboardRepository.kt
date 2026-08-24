package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

class PostgresLeaderboardRepository(
    private val dataSource: DataSource
) : LeaderboardRepository {
    override suspend fun load(currentPlayerId: String, limit: Int): LeaderboardSnapshot =
        withContext(Dispatchers.IO) {
            val currentId = UUID.fromString(currentPlayerId)
            val topPlayers = mutableListOf<LeaderboardEntrySnapshot>()
            var currentPlayer: LeaderboardEntrySnapshot? = null
            val seasonTopPlayers = mutableListOf<LeaderboardEntrySnapshot>()
            var seasonCurrentPlayer: LeaderboardEntrySnapshot? = null
            var seasonName: String? = null
            
            val topClans = mutableListOf<com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot>()
            var currentClanEntry: com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot? = null

            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    WITH ranked AS (
                        SELECT p.user_id, p.display_name, p.player_code, p.avatar_url,
                               s.wins, s.total_matches, s.highest_score, s.elo_rating,
                               COALESCE(s.equipped_frame_id, 'frame_default') AS frame_id,
                               ROW_NUMBER() OVER (
                                   ORDER BY s.elo_rating DESC,
                                            s.wins DESC,
                                            (s.wins::NUMERIC / NULLIF(s.total_matches, 0)) DESC NULLS LAST,
                                            s.highest_score DESC,
                                            s.updated_at ASC,
                                            p.user_id ASC
                               ) AS rank
                        FROM player_stats s
                        JOIN profiles p ON p.user_id = s.user_id
                        JOIN users u ON u.id = s.user_id
                        WHERE s.total_matches > 0
                          AND u.status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1 FROM rating_history rh WHERE rh.user_id = s.user_id
                          )
                    )
                    SELECT * FROM ranked WHERE rank <= ? OR user_id = ? ORDER BY rank
                    """.trimIndent()
                ).use { statement ->
                    statement.setInt(1, limit.coerceIn(1, MAX_LEADERBOARD_SIZE))
                    statement.setObject(2, currentId)
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            val entry = LeaderboardEntrySnapshot(
                                rank = result.getInt("rank"),
                                displayName = result.getString("display_name"),
                                playerCode = result.getString("player_code"),
                                wins = result.getInt("wins"),
                                totalMatches = result.getInt("total_matches"),
                                highestScore = result.getInt("highest_score"),
                                eloRating = result.getInt("elo_rating"),
                                userId = result.getObject("user_id", UUID::class.java).toString(),
                                avatarId = result.getString("avatar_url"),
                                frameId = result.getString("frame_id")
                            )
                            if (result.getObject("user_id", UUID::class.java) == currentId) currentPlayer = entry
                            if (entry.rank <= limit) topPlayers += entry
                        }
                    }
                }
                
                connection.prepareStatement(
                    """
                    WITH clan_stats AS (
                        SELECT c.id, c.name, COUNT(m.user_id) as member_count, COALESCE(SUM(ps.elo_rating), 0) as total_elo
                        FROM clans c
                        JOIN clan_members m ON c.id = m.clan_id
                        JOIN player_stats ps ON m.user_id = ps.user_id
                        GROUP BY c.id, c.name
                    ),
                    ranked_clans AS (
                        SELECT id, name, member_count, total_elo,
                               ROW_NUMBER() OVER (ORDER BY total_elo DESC, name ASC) AS rank
                        FROM clan_stats
                    )
                    SELECT * FROM ranked_clans 
                    WHERE rank <= ? OR id = (SELECT clan_id FROM clan_members WHERE user_id = ?) 
                    ORDER BY rank
                    """.trimIndent()
                ).use { statement ->
                    statement.setInt(1, limit.coerceIn(1, MAX_LEADERBOARD_SIZE))
                    statement.setObject(2, currentId)
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            val entry = com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot(
                                rank = result.getInt("rank"),
                                clanId = result.getObject("id", UUID::class.java).toString(),
                                clanName = result.getString("name"),
                                totalElo = result.getInt("total_elo"),
                                memberCount = result.getInt("member_count")
                            )
                            val isMyClan = result.getInt("rank") > limit // Wait, how do I know if it's mine?
                            // Actually, I can check if it's mine in Kotlin later. Or I can check if it matches my clan ID.
                            // But I didn't fetch my clan ID! Let me fetch it inside the loop.
                            topClans += entry // We will filter it below
                        }
                    }
                }
                
                connection.prepareStatement(
                    "SELECT id, name FROM seasons WHERE CURRENT_TIMESTAMP >= starts_at AND CURRENT_TIMESTAMP < ends_at ORDER BY starts_at DESC LIMIT 1"
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            val seasonId = result.getObject("id", UUID::class.java)
                            seasonName = result.getString("name")
                            connection.prepareStatement(
                                """
                                WITH ranked AS (
                                    SELECT p.user_id, p.display_name, p.player_code, p.avatar_url,
                                           ps.wins, ps.total_matches, ps.highest_score,
                                           sr.rating AS elo_rating,
                                           COALESCE(ps.equipped_frame_id, 'frame_default') AS frame_id,
                                           ROW_NUMBER() OVER (
                                               ORDER BY sr.rating DESC, sr.matches_played ASC, sr.updated_at ASC, p.user_id
                                           ) AS rank
                                    FROM season_ratings sr
                                    JOIN profiles p ON p.user_id = sr.user_id
                                    JOIN player_stats ps ON ps.user_id = sr.user_id
                                    JOIN users u ON u.id = sr.user_id
                                    WHERE sr.season_id = ? AND sr.placement_matches >= 5 AND u.status = 'ACTIVE'
                                )
                                SELECT * FROM ranked WHERE rank <= ? OR user_id = ? ORDER BY rank
                                """.trimIndent()
                            ).use { seasonStatement ->
                                seasonStatement.setObject(1, seasonId)
                                seasonStatement.setInt(2, limit.coerceIn(1, MAX_LEADERBOARD_SIZE))
                                seasonStatement.setObject(3, currentId)
                                seasonStatement.executeQuery().use { seasonResult ->
                                    while (seasonResult.next()) {
                                        val entry = LeaderboardEntrySnapshot(
                                            rank = seasonResult.getInt("rank"),
                                            displayName = seasonResult.getString("display_name"),
                                            playerCode = seasonResult.getString("player_code"),
                                            wins = seasonResult.getInt("wins"),
                                            totalMatches = seasonResult.getInt("total_matches"),
                                            highestScore = seasonResult.getInt("highest_score"),
                                            eloRating = seasonResult.getInt("elo_rating"),
                                            userId = seasonResult.getObject("user_id", UUID::class.java).toString(),
                                            avatarId = seasonResult.getString("avatar_url"),
                                            frameId = seasonResult.getString("frame_id")
                                        )
                                        if (seasonResult.getObject("user_id", UUID::class.java) == currentId) seasonCurrentPlayer = entry
                                        if (entry.rank <= limit) seasonTopPlayers += entry
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Extract currentClanEntry
                connection.prepareStatement("SELECT clan_id FROM clan_members WHERE user_id = ?").use { stmt ->
                    stmt.setObject(1, currentId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val myClanId = rs.getObject("clan_id", UUID::class.java).toString()
                            currentClanEntry = topClans.firstOrNull { it.clanId == myClanId }
                        }
                    }
                }
            }
            LeaderboardSnapshot(
                topPlayers = topPlayers,
                currentPlayer = currentPlayer,
                seasonName = seasonName,
                seasonTopPlayers = seasonTopPlayers,
                seasonCurrentPlayer = seasonCurrentPlayer,
                topClans = topClans.filter { it.rank <= limit },
                currentClan = currentClanEntry
            )
        }

    private companion object {
        const val MAX_LEADERBOARD_SIZE = 100
    }
}
