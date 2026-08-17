package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresDailyCheckInMilestoneTest {
    @Test
    fun `daily check in milestones unlock achievement and cosmetics`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Milestone test", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            val zone = ZoneId.of("Asia/Bangkok")
            val today = LocalDate.of(2026, 8, 17)
            val clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone)
            val repository = PostgresPlayerProfileRepository(dataSource, clock)
            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (
                            user_id, current_daily_check_in_streak, best_daily_check_in_streak,
                            total_daily_check_ins, last_daily_check_in_date, updated_at
                        ) VALUES (?, 6, 6, 6, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE SET
                            current_daily_check_in_streak = 6,
                            best_daily_check_in_streak = 6,
                            total_daily_check_ins = 6,
                            last_daily_check_in_date = EXCLUDED.last_daily_check_in_date,
                            updated_at = CURRENT_TIMESTAMP
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.setDate(2, Date.valueOf(today.minusDays(1)))
                        statement.executeUpdate()
                    }
                }

                assertFalse(repository.updateProfile(player.playerId, player.displayName, DAILY_CHECK_IN_AVATAR_ID))
                assertTrue(repository.claimDailyCheckIn(player.playerId)!!.claimed)
                val sevenDayProfile = repository.findByPlayerId(player.playerId)!!
                assertTrue(sevenDayProfile.achievements.any { it.code == "DAILY_STREAK_7" })

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        UPDATE player_stats
                        SET best_daily_check_in_streak = 30,
                            total_daily_check_ins = 50,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                }
                val fiftyCheckIns = repository.findByPlayerId(player.playerId)!!
                assertTrue(fiftyCheckIns.progression.cosmetics.any {
                    it.type == CosmeticType.TITLE && it.id == "title_diligent" && it.unlocked
                })
                assertTrue(fiftyCheckIns.progression.cosmetics.any {
                    it.type == CosmeticType.AVATAR && it.id == DAILY_CHECK_IN_AVATAR_ID && it.unlocked
                })
                assertFalse(fiftyCheckIns.progression.cosmetics.first { it.id == "frame_persistent" }.unlocked)
                assertTrue(repository.updateProfile(player.playerId, player.displayName, DAILY_CHECK_IN_AVATAR_ID))

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "UPDATE player_stats SET total_daily_check_ins = 100 WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                }
                assertTrue(
                    repository.findByPlayerId(player.playerId)!!.progression.cosmetics
                        .first { it.id == "frame_persistent" }.unlocked
                )
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
