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
                                    SELECT p.user_id, p.display_name, p.player_code,
                                           ps.wins, ps.total_matches, ps.highest_score,
                                           sr.rating AS elo_rating,
                                           ROW_NUMBER() OVER (
                                               ORDER BY sr.rating DESC, sr.matches_played ASC, sr.updated_at ASC, p.user_id
                                           ) AS rank
                                    FROM season_ratings sr
                                    JOIN profiles p ON p.user_id = sr.user_id
                                    JOIN player_stats ps ON ps.user_id = sr.user_id
                                    JOIN users u ON u.id = sr.user_id
                                    WHERE sr.season_id = ? AND sr.matches_played > 0 AND u.status = 'ACTIVE'
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
                                            eloRating = seasonResult.getInt("elo_rating")
                                        )
                                        if (seasonResult.getObject("user_id", UUID::class.java) == currentId) seasonCurrentPlayer = entry
                                        if (entry.rank <= limit) seasonTopPlayers += entry
                                    }
                                }
                            }
                        }
                    }
                }
            }
            LeaderboardSnapshot(
                topPlayers = topPlayers,
                currentPlayer = currentPlayer,
                seasonName = seasonName,
                seasonTopPlayers = seasonTopPlayers,
                seasonCurrentPlayer = seasonCurrentPlayer
            )
        }

    private companion object {
        const val MAX_LEADERBOARD_SIZE = 100
    }
}
