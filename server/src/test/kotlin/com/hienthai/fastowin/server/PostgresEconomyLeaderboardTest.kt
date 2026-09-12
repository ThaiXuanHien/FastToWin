package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostgresEconomyLeaderboardTest {
    @Test
    fun `leaderboards rank eligible lifetime income and clan progression independently`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        val username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        val password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        val schema = "economy_leaderboard_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            maximumPoolSize = 1
        }).use { admin ->
            admin.connection.use { connection ->
                connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            }
        }
        val schemaUrl = url + if ('?' in url) "&currentSchema=$schema" else "?currentSchema=$schema"
        try {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = schemaUrl
                this.username = username
                this.password = password
                maximumPoolSize = 2
            }).use { dataSource ->
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate()
                val auth = AuthenticationService(
                    PostgresAuthRepository(dataSource),
                    PasswordHasher(iterations = 1_000),
                    nowMillis = { NOW }
                )
                val suffix = UUID.randomUUID().toString()
                val first = assertIs<AuthResult.Success>(
                    auth.register("ranking-a-$suffix@example.com", PASSWORD, "Alpha", "android")
                ).session
                val current = assertIs<AuthResult.Success>(
                    auth.register("ranking-b-$suffix@example.com", PASSWORD, "Bravo", "android")
                ).session
                val userIds = listOf(UUID.fromString(first.userId), UUID.fromString(current.userId))
                val clanRepository = PostgresClanRepository(dataSource)
                val profileRepository = PostgresPlayerProfileRepository(dataSource)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (user_id, updated_at)
                        VALUES (?, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO NOTHING
                        """.trimIndent()
                    ).use { statement ->
                        userIds.forEach { userId ->
                            statement.setObject(1, userId)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }

                assertEquals(
                    WalletMutationStatus.APPLIED,
                    profileRepository.applyWalletTransaction(first.userId, "MATCH", "earned-a", goldDelta = 500, gemsDelta = 2)
                )
                assertEquals(
                    WalletMutationStatus.APPLIED,
                    profileRepository.applyWalletTransaction(current.userId, "MATCH", "earned-b", goldDelta = 300, gemsDelta = 5)
                )
                assertEquals(
                    WalletMutationStatus.APPLIED,
                    profileRepository.applyWalletTransaction(current.userId, "GOLD_EXCHANGE", "exchange-b", goldDelta = 10_000)
                )
                assertEquals(
                    WalletMutationStatus.APPLIED,
                    profileRepository.applyWalletTransaction(current.userId, "STORE_PURCHASE", "store-b", gemsDelta = 100)
                )

                val firstClan = requireNotNull(clanRepository.createClan(first.userId, "Alpha ${suffix.take(6)}", ""))
                val currentClan = requireNotNull(clanRepository.createClan(current.userId, "Bravo ${suffix.take(6)}", ""))
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        UPDATE clans
                        SET level = ?, experience_points = ?, donated_gold = ?, donated_gems = ?
                        WHERE id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setInt(1, 3)
                        statement.setLong(2, 2_500)
                        statement.setLong(3, 10_000)
                        statement.setLong(4, 2)
                        statement.setObject(5, UUID.fromString(firstClan))
                        statement.addBatch()
                        statement.setInt(1, 2)
                        statement.setLong(2, 2_000)
                        statement.setLong(3, 500)
                        statement.setLong(4, 10)
                        statement.setObject(5, UUID.fromString(currentClan))
                        statement.addBatch()
                        statement.executeBatch()
                    }
                }

                val leaderboard = PostgresLeaderboardRepository(dataSource).load(current.userId, limit = 1)
                assertEquals(listOf(first.userId), leaderboard.topGoldPlayers.map { it.userId })
                assertEquals(2, leaderboard.currentGoldPlayer?.rank)
                assertEquals(300L, leaderboard.currentGoldPlayer?.lifetimeEarnedGold)
                assertEquals(listOf(current.userId), leaderboard.topGemPlayers.map { it.userId })
                assertEquals(1, leaderboard.currentGemPlayer?.rank)
                assertEquals(5L, leaderboard.currentGemPlayer?.lifetimeEarnedGems)

                assertEquals(listOf(firstClan), leaderboard.topLevelClans.map { it.clanId })
                assertEquals(2, leaderboard.currentLevelClan?.rank)
                assertEquals(listOf(firstClan), leaderboard.topGoldClans.map { it.clanId })
                assertEquals(2, leaderboard.currentGoldClan?.rank)
                assertEquals(listOf(currentClan), leaderboard.topGemClans.map { it.clanId })
                assertEquals(1, leaderboard.currentGemClan?.rank)
                assertEquals(10L, leaderboard.currentGemClan?.donatedGems)
            }
        } finally {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url
                this.username = username
                this.password = password
                maximumPoolSize = 1
            }).use { admin ->
                admin.connection.use { connection ->
                    connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
    }

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NOW = 1_900_000_000_000L
    }
}
