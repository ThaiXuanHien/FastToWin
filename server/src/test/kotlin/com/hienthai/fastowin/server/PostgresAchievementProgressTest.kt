package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresAchievementProgressTest {
    @Test
    fun `persisted statistics and events produce bounded progress for active achievements`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Achievement progress", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            val matchIds = List(100) { UUID.randomUUID() }
            try {
                dataSource.connection.use { connection ->
                    connection.autoCommit = false
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (
                            user_id, wins, best_win_streak, experience_points,
                            current_daily_check_in_streak, best_daily_check_in_streak,
                            total_daily_check_ins, updated_at
                        ) VALUES (?, 100, 10, 2900, 30, 30, 100, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE SET
                            wins = 100,
                            best_win_streak = 10,
                            experience_points = 2900,
                            current_daily_check_in_streak = 30,
                            best_daily_check_in_streak = 30,
                            total_daily_check_ins = 100,
                            updated_at = CURRENT_TIMESTAMP
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                    val base = Instant.parse("2026-09-01T00:00:00Z")
                    connection.prepareStatement(
                        """
                        INSERT INTO matches (id, room_name, game_mode, started_at, ended_at, winner_user_id, match_type)
                        VALUES (?, 'Achievement fixture', 'ORDER', ?, ?, ?, 'RANKED')
                        """.trimIndent()
                    ).use { matchStatement ->
                        connection.prepareStatement(
                            """
                            INSERT INTO match_players (match_id, user_id, display_name, score, outcome)
                            VALUES (?, ?, 'Achievement progress', 1, 'WIN')
                            """.trimIndent()
                        ).use { playerStatement ->
                            matchIds.forEachIndexed { index, matchId ->
                                val startedAt = base.plusSeconds(index * 60L)
                                matchStatement.setObject(1, matchId)
                                matchStatement.setTimestamp(2, Timestamp.from(startedAt))
                                matchStatement.setTimestamp(3, Timestamp.from(startedAt.plusSeconds(2)))
                                matchStatement.setObject(4, userId)
                                matchStatement.addBatch()

                                playerStatement.setObject(1, matchId)
                                playerStatement.setObject(2, userId)
                                playerStatement.addBatch()
                            }
                            matchStatement.executeBatch()
                            playerStatement.executeBatch()
                        }
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO match_events (
                            match_id, user_id, request_id, number, expected_number,
                            result, occurred_at, sequence
                        ) VALUES (?, ?, ?, 1, 1, 'ACCEPTED', ?, 1)
                        """.trimIndent()
                    ).use { statement ->
                        matchIds.take(10).forEachIndexed { index, matchId ->
                            statement.setObject(1, matchId)
                            statement.setObject(2, userId)
                            statement.setString(3, "accepted-$index")
                            statement.setTimestamp(4, Timestamp.from(base.plusSeconds(index * 60L + 1)))
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                    connection.commit()
                }

                val progress = dataSource.connection.use { achievementProgress(it, userId) }
                assertEquals(
                    mapOf(
                        "FIRST_WIN" to 1,
                        "WINS_10" to 10,
                        "WINS_50" to 50,
                        "RANKED_WINS_100" to 100,
                        "WIN_STREAK_10" to 10,
                        "PERFECT_MATCH_1" to 1,
                        "PERFECT_MATCHES_10" to 10,
                        "ACCURACY_90_TEN" to 10,
                        "RESPONSE_2500_TEN" to 10,
                        "RESPONSE_1500_TEN" to 10,
                        "CHECKIN_STREAK_7" to 7,
                        "CHECKIN_STREAK_30" to 30,
                        "CHECKINS_50" to 50,
                        "CHECKINS_100" to 100,
                        "PLAYER_LEVEL_30" to 30,
                        "CLAN_JOINED" to 0,
                        "CLAN_GOLD_10000" to 0,
                        "CLAN_GEMS_50" to 0,
                        "CLAN_QUESTS_10" to 0,
                        "CLAN_LEVEL_10" to 0
                    ),
                    progress
                )
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM matches WHERE id = ANY (?)").use { statement ->
                        statement.setArray(1, connection.createArrayOf("uuid", matchIds.toTypedArray()))
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
