package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresMatchAchievementRewardTest {
    @Test
    fun `saving a qualifying match grants only new achievements and rewards once`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val identities = PostgresGuestIdentityRepository(dataSource)
            val winner = identities.resolveGuest("Achievement winner", null, 1_000L)
            val loser = identities.resolveGuest("Achievement loser", null, 1_000L)
            val winnerId = UUID.fromString(winner.playerId)
            val loserId = UUID.fromString(loser.playerId)
            val matchId = UUID.randomUUID()
            val startedAt = 1_788_739_200_000L
            val repository = PostgresMatchResultRepository(dataSource)
            try {
                val match = CompletedMatch(
                    matchId = matchId.toString(),
                    roomName = "Achievement match",
                    gameMode = ProtocolGameMode.ORDER,
                    startedAtMillis = startedAt,
                    endedAtMillis = startedAt + 2_000,
                    winnerPlayerId = winner.playerId,
                    players = listOf(
                        CompletedMatchPlayer(winner.playerId, winner.displayName, 1, MatchOutcome.WIN),
                        CompletedMatchPlayer(loser.playerId, loser.displayName, 0, MatchOutcome.LOSS)
                    ),
                    events = listOf(
                        MatchSelectionEvent(
                            playerId = winner.playerId,
                            requestId = "accepted-1",
                            number = 1,
                            expectedNumber = 1,
                            result = SelectionResult.ACCEPTED,
                            occurredAtMillis = startedAt + 1_000,
                            sequence = 1
                        )
                    ),
                    matchType = MatchType.CASUAL
                )

                repository.save(match)
                repository.save(match)

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT achievement_code FROM user_achievements WHERE user_id = ? ORDER BY achievement_code"
                    ).use { statement ->
                        statement.setObject(1, winnerId)
                        statement.executeQuery().use { result ->
                            val codes = buildList { while (result.next()) add(result.getString(1)) }
                            assertEquals(listOf("FIRST_WIN", "PERFECT_MATCH_1"), codes)
                        }
                    }
                    connection.prepareStatement(
                        """
                        SELECT COUNT(*) AS count, SUM(gold_delta) AS gold, SUM(gems_delta) AS gems, SUM(xp_delta) AS xp
                        FROM wallet_transactions
                        WHERE user_id = ? AND source_type = 'ACHIEVEMENT'
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, winnerId)
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(2, result.getInt("count"))
                            assertEquals(200, result.getInt("gold"))
                            assertEquals(0, result.getInt("gems"))
                            assertEquals(40, result.getInt("xp"))
                        }
                    }
                    connection.prepareStatement(
                        "SELECT gold, gems, experience_points FROM player_stats WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, winnerId)
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(280, result.getInt("gold"))
                            assertEquals(0, result.getInt("gems"))
                            assertEquals(64, result.getInt("experience_points"))
                        }
                    }
                    connection.prepareStatement(
                        "SELECT cosmetic_id FROM player_cosmetics WHERE user_id = ? ORDER BY cosmetic_id"
                    ).use { statement ->
                        statement.setObject(1, winnerId)
                        statement.executeQuery().use { result ->
                            val ids = buildList { while (result.next()) add(result.getString(1)) }
                            assertEquals(listOf("title_first_battle", "title_one_strike"), ids)
                        }
                    }
                }
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM matches WHERE id = ?").use { statement ->
                        statement.setObject(1, matchId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM users WHERE id IN (?, ?)").use { statement ->
                        statement.setObject(1, winnerId)
                        statement.setObject(2, loserId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
