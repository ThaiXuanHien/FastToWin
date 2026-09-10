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

class PostgresMatchRewardTest {
    @Test
    fun `match rewards vary by match type outcome and intentional leave`() = runTest {
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
            val profileRepository = PostgresPlayerProfileRepository(dataSource)
            val cases = listOf(
                RewardCase("casual-win", MatchType.CASUAL, MatchOutcome.WIN, false, 80, 24),
                RewardCase("casual-draw", MatchType.CASUAL, MatchOutcome.DRAW, false, 40, 16),
                RewardCase("casual-loss", MatchType.CASUAL, MatchOutcome.LOSS, false, 20, 8),
                RewardCase("ranked-win", MatchType.RANKED, MatchOutcome.WIN, false, 100, 30),
                RewardCase("ranked-draw", MatchType.RANKED, MatchOutcome.DRAW, false, 50, 20),
                RewardCase("ranked-loss", MatchType.RANKED, MatchOutcome.LOSS, false, 25, 10),
                RewardCase("casual-leave", MatchType.CASUAL, MatchOutcome.LOSS, true, 0, 0),
                RewardCase("ranked-leave", MatchType.RANKED, MatchOutcome.LOSS, true, 0, 0)
            )

            cases.forEachIndexed { index, case ->
                val player = identityRepository.resolveGuest("Reward ${case.name}", null, 10_000L + index)
                val opponent = identityRepository.resolveGuest("Opponent ${case.name}", null, 20_000L + index)
                val matchId = UUID.randomUUID().toString()
                val opponentOutcome = when (case.outcome) {
                    MatchOutcome.WIN -> MatchOutcome.LOSS
                    MatchOutcome.LOSS -> MatchOutcome.WIN
                    MatchOutcome.DRAW -> MatchOutcome.DRAW
                }
                val winnerId = when (case.outcome) {
                    MatchOutcome.WIN -> player.playerId
                    MatchOutcome.LOSS -> opponent.playerId
                    MatchOutcome.DRAW -> null
                }
                try {
                    matchRepository.save(
                        CompletedMatch(
                            matchId = matchId,
                            roomName = "Reward ${case.name}",
                            gameMode = ProtocolGameMode.ORDER,
                            matchType = case.matchType,
                            startedAtMillis = System.currentTimeMillis(),
                            endedAtMillis = System.currentTimeMillis() + 1_000L,
                            winnerPlayerId = winnerId,
                            players = listOf(
                                CompletedMatchPlayer(
                                    playerId = player.playerId,
                                    displayName = player.displayName,
                                    score = if (case.outcome == MatchOutcome.WIN) 50 else 20,
                                    outcome = case.outcome,
                                    intentionalLeave = case.intentionalLeave
                                ),
                                CompletedMatchPlayer(
                                    playerId = opponent.playerId,
                                    displayName = opponent.displayName,
                                    score = if (opponentOutcome == MatchOutcome.WIN) 50 else 20,
                                    outcome = opponentOutcome
                                )
                            )
                        )
                    )

                    val profile = profileRepository.findByPlayerId(player.playerId)!!
                    assertEquals(case.expectedGold, profile.progression.gold, "${case.name} profile Gold")
                    assertEquals(case.expectedXp, profile.progression.experiencePoints, "${case.name} profile XP")
                    dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            """
                            SELECT
                                COUNT(*) AS transaction_count,
                                COALESCE(SUM(gold_delta), 0) AS gold_delta,
                                COALESCE(SUM(xp_delta), 0) AS xp_delta
                            FROM wallet_transactions
                            WHERE user_id = ? AND source_type = 'MATCH' AND source_id = ?
                            """.trimIndent()
                        ).use { statement ->
                            statement.setObject(1, UUID.fromString(player.playerId))
                            statement.setString(2, matchId)
                            statement.executeQuery().use { result ->
                                result.next()
                                val expectedTransactionCount =
                                    if (case.expectedGold == 0 && case.expectedXp == 0) 0 else 1
                                assertEquals(
                                    expectedTransactionCount,
                                    result.getInt("transaction_count"),
                                    "${case.name} wallet transaction count"
                                )
                                assertEquals(case.expectedGold, result.getInt("gold_delta"), "${case.name} wallet Gold")
                                assertEquals(case.expectedXp, result.getInt("xp_delta"), "${case.name} wallet XP")
                            }
                        }
                    }
                } finally {
                    dataSource.connection.use { connection ->
                        connection.prepareStatement("DELETE FROM matches WHERE id = ?").use { statement ->
                            statement.setObject(1, UUID.fromString(matchId))
                            statement.executeUpdate()
                        }
                        connection.prepareStatement("DELETE FROM users WHERE id IN (?, ?)").use { statement ->
                            statement.setObject(1, UUID.fromString(player.playerId))
                            statement.setObject(2, UUID.fromString(opponent.playerId))
                            statement.executeUpdate()
                        }
                    }
                }
            }
        }
    }

    private data class RewardCase(
        val name: String,
        val matchType: MatchType,
        val outcome: MatchOutcome,
        val intentionalLeave: Boolean,
        val expectedGold: Int,
        val expectedXp: Int
    )
}
