package com.hienthai.fastowin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PostgresMatchResultRepository(
    private val dataSource: DataSource
) : MatchResultRepository {
    override suspend fun save(match: CompletedMatch): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                if (insertMatch(connection, match)) {
                    insertPlayers(connection, match)
                    insertEvents(connection, match)
                    updateStats(connection, match)
                }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    private fun insertMatch(connection: Connection, match: CompletedMatch): Boolean =
        connection.prepareStatement(
            """
            INSERT INTO matches (id, room_name, game_mode, started_at, ended_at, winner_user_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.fromString(match.matchId))
            statement.setString(2, match.roomName)
            statement.setString(3, match.gameMode.name)
            statement.setTimestamp(4, match.startedAtMillis.toTimestamp())
            statement.setTimestamp(5, match.endedAtMillis.toTimestamp())
            statement.setObject(6, match.winnerPlayerId?.let(UUID::fromString))
            statement.executeUpdate() == 1
        }

    private fun insertPlayers(connection: Connection, match: CompletedMatch) {
        connection.prepareStatement(
            """
            INSERT INTO match_players (match_id, user_id, display_name, score, outcome)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            match.players.forEach { player ->
                statement.setObject(1, UUID.fromString(match.matchId))
                statement.setObject(2, UUID.fromString(player.playerId))
                statement.setString(3, player.displayName)
                statement.setInt(4, player.score)
                statement.setString(5, player.outcome.name)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun insertEvents(connection: Connection, match: CompletedMatch) {
        if (match.events.isEmpty()) return
        connection.prepareStatement(
            """
            INSERT INTO match_events (
                match_id, user_id, request_id, number, expected_number, result, occurred_at, sequence
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            match.events.forEach { event ->
                statement.setObject(1, UUID.fromString(match.matchId))
                statement.setObject(2, UUID.fromString(event.playerId))
                statement.setString(3, event.requestId)
                statement.setInt(4, event.number)
                statement.setInt(5, event.expectedNumber)
                statement.setString(6, event.result.name)
                statement.setTimestamp(7, event.occurredAtMillis.toTimestamp())
                statement.setInt(8, event.sequence)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun updateStats(connection: Connection, match: CompletedMatch) {
        connection.prepareStatement(
            """
            INSERT INTO player_stats (
                user_id, total_matches, wins, losses, draws, highest_score,
                current_win_streak, best_win_streak, updated_at
            )
            VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                total_matches = player_stats.total_matches + 1,
                wins = player_stats.wins + EXCLUDED.wins,
                losses = player_stats.losses + EXCLUDED.losses,
                draws = player_stats.draws + EXCLUDED.draws,
                highest_score = GREATEST(player_stats.highest_score, EXCLUDED.highest_score),
                current_win_streak = CASE
                    WHEN EXCLUDED.wins = 1 THEN player_stats.current_win_streak + 1
                    ELSE 0
                END,
                best_win_streak = CASE
                    WHEN EXCLUDED.wins = 1 THEN GREATEST(
                        player_stats.best_win_streak,
                        player_stats.current_win_streak + 1
                    )
                    ELSE player_stats.best_win_streak
                END,
                updated_at = EXCLUDED.updated_at
            """.trimIndent()
        ).use { statement ->
            match.players.forEach { player ->
                val won = if (player.outcome == MatchOutcome.WIN) 1 else 0
                statement.setObject(1, UUID.fromString(player.playerId))
                statement.setInt(2, won)
                statement.setInt(3, if (player.outcome == MatchOutcome.LOSS) 1 else 0)
                statement.setInt(4, if (player.outcome == MatchOutcome.DRAW) 1 else 0)
                statement.setInt(5, player.score)
                statement.setInt(6, won)
                statement.setInt(7, won)
                statement.setTimestamp(8, match.endedAtMillis.toTimestamp())
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun Long.toTimestamp(): Timestamp = Timestamp.from(Instant.ofEpochMilli(this))
}
