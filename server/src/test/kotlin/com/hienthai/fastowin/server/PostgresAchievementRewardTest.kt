package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresAchievementRewardTest {
    @Test
    fun `first unlock grants achievement wallet reward and cosmetic exactly once`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Achievement reward", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            val occurredAt = Instant.parse("2026-09-10T06:00:00Z")
            try {
                val first = dataSource.connection.use { connection ->
                    connection.autoCommit = false
                    grantNewAchievements(connection, userId, setOf("FIRST_WIN"), occurredAt, null)
                        .also { connection.commit() }
                }
                val repeated = dataSource.connection.use { connection ->
                    connection.autoCommit = false
                    grantNewAchievements(connection, userId, setOf("FIRST_WIN"), occurredAt, null)
                        .also { connection.commit() }
                }

                assertEquals(listOf("FIRST_WIN"), first)
                assertEquals(emptyList(), repeated)
                val achievements = PostgresPlayerProfileRepository(dataSource)
                    .findByPlayerId(player.playerId)!!
                    .achievements
                assertEquals(20, achievements.size)
                val firstWin = achievements.single { it.code == "FIRST_WIN" }
                assertTrue(firstWin.unlocked)
                assertEquals(1, firstWin.progress)
                assertEquals(1, firstWin.target)
                assertEquals(20, firstWin.rewardXp)
                assertEquals(100, firstWin.rewardGold)
                assertEquals(0, firstWin.rewardGems)
                assertEquals("title_first_battle", firstWin.titleId)
                assertTrue(firstWin.unlockedAtEpochMillis > 0L)
                val rankedWins = achievements.single { it.code == "RANKED_WINS_100" }
                assertFalse(rankedWins.unlocked)
                assertEquals(0L, rankedWins.unlockedAtEpochMillis)
                assertEquals(0, rankedWins.progress)
                assertEquals(100, rankedWins.target)
                assertEquals(3, rankedWins.rewardGems)
                assertEquals("frame_diamond", rankedWins.frameId)
                dataSource.connection.use { connection ->
                    assertEquals(
                        1,
                        connection.count(
                            "SELECT COUNT(*) FROM user_achievements WHERE user_id = ? AND achievement_code = 'FIRST_WIN'",
                            userId
                        )
                    )
                    assertEquals(
                        1,
                        connection.count(
                            "SELECT COUNT(*) FROM wallet_transactions WHERE user_id = ? AND source_type = 'ACHIEVEMENT' AND source_id = 'FIRST_WIN'",
                            userId
                        )
                    )
                    assertEquals(
                        1,
                        connection.count(
                            "SELECT COUNT(*) FROM player_cosmetics WHERE user_id = ? AND cosmetic_id = 'title_first_battle' AND cosmetic_type = 'TITLE'",
                            userId
                        )
                    )
                    connection.prepareStatement(
                        "SELECT experience_points, gold, gems FROM player_stats WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(20, result.getInt("experience_points"))
                            assertEquals(100, result.getInt("gold"))
                            assertEquals(0, result.getInt("gems"))
                        }
                    }
                }
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}

private fun java.sql.Connection.count(sql: String, userId: UUID): Int =
    prepareStatement(sql).use { statement ->
        statement.setObject(1, userId)
        statement.executeQuery().use { result ->
            result.next()
            result.getInt(1)
        }
    }
