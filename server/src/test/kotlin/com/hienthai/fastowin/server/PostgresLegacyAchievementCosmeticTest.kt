package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class PostgresLegacyAchievementCosmeticTest {
    @Test
    fun `inactive legacy achievements keep derived cosmetics unlocked and equipped`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Legacy cosmetics", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (
                            user_id, experience_points, equipped_frame_id, equipped_title_id, updated_at
                        ) VALUES (?, 1400, 'frame_perfect', 'title_speed', CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE SET
                            experience_points = 1400,
                            equipped_frame_id = 'frame_perfect',
                            equipped_title_id = 'title_speed',
                            updated_at = CURRENT_TIMESTAMP
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO user_achievements (user_id, achievement_code, unlocked_at, match_id)
                        VALUES (?, ?, CURRENT_TIMESTAMP, NULL)
                        ON CONFLICT (user_id, achievement_code) DO NOTHING
                        """.trimIndent()
                    ).use { statement ->
                        listOf("PERFECT_GAME", "SPEED_50").forEach { code ->
                            statement.setObject(1, userId)
                            statement.setString(2, code)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }

                val cosmetics = PostgresPlayerProfileRepository(dataSource)
                    .findByPlayerId(player.playerId)!!
                    .progression.cosmetics
                assertTrue(cosmetics.single { it.id == "frame_perfect" }.unlocked)
                assertTrue(cosmetics.single { it.id == "frame_perfect" }.equipped)
                assertTrue(cosmetics.single { it.id == "title_speed" }.unlocked)
                assertTrue(cosmetics.single { it.id == "title_speed" }.equipped)
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
