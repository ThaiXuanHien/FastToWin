package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_AVATAR_ID
import com.hienthai.fastowin.protocol.DailyCheckInRewardSnapshot
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
            val firstHistoryDate = LocalDate.of(2025, 9, 1)
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
                    connection.prepareStatement(
                        """
                        INSERT INTO daily_check_ins (
                            user_id, check_in_date, cycle_day, reward_xp, reward_gold, reward_gems, streak_after
                        ) VALUES (?, ?, 1, 17, 73, 2, 1)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        for (date in listOf(firstHistoryDate, firstHistoryDate.minusDays(1), today.plusDays(1))) {
                            statement.setDate(2, Date.valueOf(date))
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }

                assertFalse(repository.updateProfile(player.playerId, player.displayName, DAILY_CHECK_IN_AVATAR_ID))
                val claim = repository.claimDailyCheckIn(player.playerId)!!
                assertTrue(claim.claimed)
                assertFalse(repository.claimDailyCheckIn(player.playerId)!!.claimed)
                val sevenDayProfile = repository.findByPlayerId(player.playerId)!!
                assertTrue(sevenDayProfile.achievements.any { it.code == "CHECKIN_STREAK_7" })
                assertFalse(sevenDayProfile.achievements.any { it.code == "DAILY_STREAK_7" })
                assertEquals(60, sevenDayProfile.progression.experiencePoints)
                assertEquals(300, sevenDayProfile.progression.gold)
                assertEquals(1, sevenDayProfile.progression.gems)
                val checkIn = sevenDayProfile.progression.dailyCheckIn
                assertEquals(today.toString(), checkIn.todayDate)
                assertEquals(listOf(firstHistoryDate.toString(), today.toString()), checkIn.historyDates)
                assertEquals(
                    mapOf(
                        firstHistoryDate.toString() to DailyCheckInRewardSnapshot(gold = 73, xp = 17, gems = 2),
                        today.toString() to DailyCheckInRewardSnapshot(
                            gold = claim.rewardGold,
                            xp = claim.rewardXp,
                            gems = claim.rewardGems
                        )
                    ),
                    checkIn.historyRewards
                )
                dataSource.connection.use { connection ->
                    assertEquals(
                        1,
                        connection.prepareStatement(
                            """
                            SELECT COUNT(*)
                            FROM wallet_transactions
                            WHERE user_id = ? AND source_type = 'ACHIEVEMENT'
                              AND source_id = 'CHECKIN_STREAK_7'
                            """.trimIndent()
                        ).use { statement ->
                            statement.setObject(1, userId)
                            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
                        }
                    )
                    assertEquals(
                        1,
                        connection.prepareStatement(
                            """
                            SELECT COUNT(*)
                            FROM player_cosmetics
                            WHERE user_id = ? AND cosmetic_id = 'frame_wildfire'
                              AND cosmetic_type = 'FRAME'
                            """.trimIndent()
                        ).use { statement ->
                            statement.setObject(1, userId)
                            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
                        }
                    )
                }

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
