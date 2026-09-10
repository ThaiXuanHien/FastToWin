package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.sql.Date
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresMissionProgressTest {
    @Test
    fun `matches update only active missions with authoritative progress`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val identityRepository = PostgresGuestIdentityRepository(dataSource)
            val matchRepository = PostgresMatchResultRepository(dataSource)
            val users = mutableListOf<String>()
            val matches = mutableListOf<String>()
            try {
                val casualDate = LocalDate.of(2026, 9, 2)
                val casualPlayer = identityRepository.resolveGuest("Casual missions", null, 1_000L)
                val casualOpponent = identityRepository.resolveGuest("Casual opponent", null, 1_001L)
                users += listOf(casualPlayer.playerId, casualOpponent.playerId)
                matches += saveWin(
                    repository = matchRepository,
                    player = casualPlayer,
                    opponent = casualOpponent,
                    date = casualDate,
                    matchType = MatchType.CASUAL,
                    accepted = 9,
                    rejected = 1
                )
                assertEquals(
                    mapOf(
                        "DAILY_CASUAL_2" to 1,
                        "DAILY_WIN_1" to 1,
                        "DAILY_ACCURACY_90" to 1
                    ),
                    missionProgress(dataSource, casualPlayer.playerId, casualDate, "DAILY_%")
                )
                assertEquals(
                    mapOf(
                        "WEEKLY_PLAY_15" to 1,
                        "WEEKLY_WIN_5" to 1
                    ),
                    missionProgress(dataSource, casualPlayer.playerId, LocalDate.of(2026, 8, 31), "WEEKLY_%")
                )

                val perfectDate = LocalDate.of(2026, 9, 3)
                val perfectPlayer = identityRepository.resolveGuest("Perfect missions", null, 2_000L)
                val perfectOpponent = identityRepository.resolveGuest("Perfect opponent", null, 2_001L)
                users += listOf(perfectPlayer.playerId, perfectOpponent.playerId)
                matches += saveWin(
                    repository = matchRepository,
                    player = perfectPlayer,
                    opponent = perfectOpponent,
                    date = perfectDate,
                    matchType = MatchType.RANKED,
                    accepted = 50,
                    rejected = 0
                )
                assertEquals(
                    mapOf(
                        "DAILY_RANKED_2" to 1,
                        "DAILY_CORRECT_100" to 50,
                        "DAILY_PERFECT_WIN_1" to 1
                    ),
                    missionProgress(dataSource, perfectPlayer.playerId, perfectDate, "DAILY_%")
                )
                assertEquals(
                    mapOf(
                        "WEEKLY_PLAY_15" to 1,
                        "WEEKLY_WIN_5" to 1,
                        "WEEKLY_PERFECT_3" to 1
                    ),
                    missionProgress(dataSource, perfectPlayer.playerId, LocalDate.of(2026, 8, 31), "WEEKLY_%")
                )

                val streakDate = LocalDate.of(2026, 9, 14)
                val streakPlayer = identityRepository.resolveGuest("Streak missions", null, 3_000L)
                val streakOpponent = identityRepository.resolveGuest("Streak opponent", null, 3_001L)
                users += listOf(streakPlayer.playerId, streakOpponent.playerId)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (
                            user_id, current_win_streak, best_win_streak, updated_at
                        ) VALUES (?, 2, 2, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE SET
                            current_win_streak = 2,
                            best_win_streak = 2,
                            updated_at = CURRENT_TIMESTAMP
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(streakPlayer.playerId))
                        statement.executeUpdate()
                    }
                }
                matches += saveWin(
                    repository = matchRepository,
                    player = streakPlayer,
                    opponent = streakOpponent,
                    date = streakDate,
                    matchType = MatchType.RANKED,
                    accepted = 1,
                    rejected = 0
                )
                assertEquals(
                    mapOf(
                        "WEEKLY_PLAY_15" to 1,
                        "WEEKLY_RANKED_WIN_3" to 1,
                        "WEEKLY_CORRECT_500" to 1,
                        "WEEKLY_STREAK_3" to 3
                    ),
                    missionProgress(dataSource, streakPlayer.playerId, streakDate, "WEEKLY_%")
                )
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM matches WHERE id = ?").use { statement ->
                        matches.forEach { matchId ->
                            statement.setObject(1, UUID.fromString(matchId))
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        users.forEach { playerId ->
                            statement.setObject(1, UUID.fromString(playerId))
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
            }
        }
    }

    private suspend fun saveWin(
        repository: PostgresMatchResultRepository,
        player: GuestIdentity,
        opponent: GuestIdentity,
        date: LocalDate,
        matchType: MatchType,
        accepted: Int,
        rejected: Int
    ): String {
        val matchId = UUID.randomUUID().toString()
        val startedAt = date.atTime(12, 0).atZone(MISSION_ZONE).toInstant().toEpochMilli()
        val events = buildList {
            repeat(accepted) { index ->
                add(
                    MatchSelectionEvent(
                        playerId = player.playerId,
                        requestId = "accepted-$matchId-$index",
                        number = index + 1,
                        expectedNumber = index + 1,
                        result = SelectionResult.ACCEPTED,
                        occurredAtMillis = startedAt + index + 1,
                        sequence = index + 1
                    )
                )
            }
            repeat(rejected) { index ->
                add(
                    MatchSelectionEvent(
                        playerId = player.playerId,
                        requestId = "rejected-$matchId-$index",
                        number = accepted + index + 2,
                        expectedNumber = accepted + 1,
                        result = SelectionResult.REJECTED,
                        occurredAtMillis = startedAt + accepted + index + 1,
                        sequence = accepted + index + 1
                    )
                )
            }
        }
        repository.save(
            CompletedMatch(
                matchId = matchId,
                roomName = "Mission integration",
                gameMode = ProtocolGameMode.ORDER,
                matchType = matchType,
                startedAtMillis = startedAt,
                endedAtMillis = startedAt + 1_000,
                winnerPlayerId = player.playerId,
                players = listOf(
                    CompletedMatchPlayer(player.playerId, player.displayName, accepted, MatchOutcome.WIN),
                    CompletedMatchPlayer(opponent.playerId, opponent.displayName, 0, MatchOutcome.LOSS)
                ),
                events = events
            )
        )
        return matchId
    }

    private fun missionProgress(
        dataSource: HikariDataSource,
        playerId: String,
        periodStart: LocalDate,
        codePattern: String
    ): Map<String, Int> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT mission_code, progress
            FROM user_missions
            WHERE user_id = ? AND period_start = ? AND mission_code LIKE ?
            ORDER BY mission_code
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.fromString(playerId))
            statement.setDate(2, Date.valueOf(periodStart))
            statement.setString(3, codePattern)
            statement.executeQuery().use { result ->
                buildMap {
                    while (result.next()) put(result.getString("mission_code"), result.getInt("progress"))
                }
            }
        }
    }

    private companion object {
        val MISSION_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")
    }
}
