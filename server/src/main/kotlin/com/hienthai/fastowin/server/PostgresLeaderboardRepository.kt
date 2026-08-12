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
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    WITH ranked AS (
                        SELECT p.user_id, p.display_name, p.player_code,
                               s.wins, s.total_matches, s.highest_score, s.elo_rating,
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
                        WHERE s.total_matches > 0 AND u.status = 'ACTIVE'
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
                                eloRating = result.getInt("elo_rating")
                            )
                            if (result.getObject("user_id", UUID::class.java) == currentId) currentPlayer = entry
                            if (entry.rank <= limit) topPlayers += entry
                        }
                    }
                }
            }
            LeaderboardSnapshot(topPlayers = topPlayers, currentPlayer = currentPlayer)
        }

    private companion object {
        const val MAX_LEADERBOARD_SIZE = 100
    }
}
