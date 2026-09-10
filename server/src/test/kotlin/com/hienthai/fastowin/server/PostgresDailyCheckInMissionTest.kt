package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.sql.Date
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresDailyCheckInMissionTest {
    @Test
    fun `daily check in completes the active mission exactly once`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Check-in mission", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            val today = LocalDate.of(2026, 9, 3)
            val zone = ZoneId.of("Asia/Bangkok")
            val clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone)
            val repository = PostgresPlayerProfileRepository(dataSource, clock)
            try {
                assertTrue(repository.claimDailyCheckIn(player.playerId)!!.claimed)
                assertFalse(repository.claimDailyCheckIn(player.playerId)!!.claimed)

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        SELECT progress, target, reward_xp, reward_gold, reward_gems, completed_at
                        FROM user_missions
                        WHERE user_id = ? AND mission_code = 'DAILY_CHECK_IN' AND period_start = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.setDate(2, Date.valueOf(today))
                        statement.executeQuery().use { result ->
                            assertTrue(result.next())
                            assertEquals(1, result.getInt("progress"))
                            assertEquals(1, result.getInt("target"))
                            assertEquals(10, result.getInt("reward_xp"))
                            assertEquals(50, result.getInt("reward_gold"))
                            assertEquals(0, result.getInt("reward_gems"))
                            assertTrue(result.getTimestamp("completed_at") != null)
                            assertFalse(result.next())
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
