package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.protocol.PlayerProfileSnapshot
import com.hienthai.fastowin.protocol.PlayerStatisticsSnapshot
import com.hienthai.fastowin.protocol.ProtocolGameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

class PostgresPlayerProfileRepository(
    private val dataSource: DataSource
) : PlayerProfileRepository {
    override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val userId = UUID.fromString(playerId)
            val base = connection.prepareStatement(
                """
                SELECT p.display_name, p.player_code,
                       COALESCE(s.total_matches, 0) AS total_matches,
                       COALESCE(s.wins, 0) AS wins,
                       COALESCE(s.losses, 0) AS losses,
                       COALESCE(s.draws, 0) AS draws,
                       COALESCE(s.highest_score, 0) AS highest_score,
                       COALESCE(s.current_win_streak, 0) AS current_win_streak,
                       COALESCE(s.best_win_streak, 0) AS best_win_streak
                FROM profiles p
                LEFT JOIN player_stats s ON s.user_id = p.user_id
                WHERE p.user_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    if (!result.next()) return@withContext null
                    PlayerProfileSnapshot(
                        displayName = result.getString("display_name"),
                        playerCode = result.getString("player_code"),
                        statistics = PlayerStatisticsSnapshot(
                            totalMatches = result.getInt("total_matches"),
                            wins = result.getInt("wins"),
                            losses = result.getInt("losses"),
                            draws = result.getInt("draws"),
                            highestScore = result.getInt("highest_score"),
                            currentWinStreak = result.getInt("current_win_streak"),
                            bestWinStreak = result.getInt("best_win_streak")
                        )
                    )
                }
            }

            base.copy(recentMatches = connection.prepareStatement(
                """
                SELECT m.id, m.room_name, m.game_mode, m.ended_at,
                       mine.score AS player_score, mine.outcome,
                       COALESCE(opponent.display_name, 'Đối thủ') AS opponent_name,
                       COALESCE(opponent.score, 0) AS opponent_score
                FROM match_players mine
                JOIN matches m ON m.id = mine.match_id
                LEFT JOIN match_players opponent ON opponent.match_id = mine.match_id AND opponent.user_id <> mine.user_id
                WHERE mine.user_id = ?
                ORDER BY m.ended_at DESC
                LIMIT 20
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, userId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(
                            MatchHistorySnapshot(
                                matchId = result.getObject("id", UUID::class.java).toString(),
                                roomName = result.getString("room_name"),
                                gameMode = ProtocolGameMode.valueOf(result.getString("game_mode")),
                                opponentName = result.getString("opponent_name"),
                                playerScore = result.getInt("player_score"),
                                opponentScore = result.getInt("opponent_score"),
                                outcome = MatchHistoryOutcome.valueOf(result.getString("outcome")),
                                endedAtEpochMillis = result.getTimestamp("ended_at").time
                            )
                        )
                    }
                }
            })
        }
    }
}
