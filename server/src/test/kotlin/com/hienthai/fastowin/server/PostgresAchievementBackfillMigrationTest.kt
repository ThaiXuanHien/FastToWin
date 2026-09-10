package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresAchievementBackfillMigrationTest {
    @Test
    fun `migration rewards legacy unlocks and persisted qualifiers exactly once`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        val username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        val password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        val schema = "achievement_backfill_${UUID.randomUUID().toString().replace("-", "")}" 
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            maximumPoolSize = 1
        }).use { admin ->
            admin.connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schema") } }
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
                    .target(MigrationVersion.fromVersion("44"))
                    .load()
                    .migrate()
                val player = PostgresGuestIdentityRepository(dataSource)
                    .resolveGuest("Backfill achievement", null, 1_000L)
                val userId = UUID.fromString(player.playerId)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (
                            user_id, wins, best_win_streak, experience_points,
                            best_daily_check_in_streak, total_daily_check_ins, updated_at
                        ) VALUES (?, 10, 10, 2900, 30, 100, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE SET
                            wins = 10,
                            best_win_streak = 10,
                            experience_points = 2900,
                            best_daily_check_in_streak = 30,
                            total_daily_check_ins = 100,
                            updated_at = CURRENT_TIMESTAMP
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO user_achievements (user_id, achievement_code, unlocked_at, match_id)
                        VALUES (?, 'FIRST_WIN', CURRENT_TIMESTAMP, NULL)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeUpdate()
                    }
                }

                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate()

                dataSource.connection.use { connection ->
                    assertEquals(
                        setOf(
                            "FIRST_WIN", "WINS_10", "WIN_STREAK_10", "CHECKIN_STREAK_7",
                            "CHECKIN_STREAK_30", "CHECKINS_50", "CHECKINS_100", "PLAYER_LEVEL_30"
                        ),
                        connection.prepareStatement(
                            "SELECT achievement_code FROM user_achievements WHERE user_id = ?"
                        ).use { statement ->
                            statement.setObject(1, userId)
                            statement.executeQuery().use { result ->
                                buildSet { while (result.next()) add(result.getString(1)) }
                            }
                        }
                    )
                    connection.prepareStatement(
                        """
                        SELECT COUNT(*) AS count, SUM(gold_delta) AS gold,
                               SUM(gems_delta) AS gems, SUM(xp_delta) AS xp
                        FROM wallet_transactions
                        WHERE user_id = ? AND source_type = 'ACHIEVEMENT'
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(8, result.getInt("count"))
                            assertEquals(3_700, result.getInt("gold"))
                            assertEquals(8, result.getInt("gems"))
                            assertEquals(740, result.getInt("xp"))
                        }
                    }
                    connection.prepareStatement(
                        "SELECT experience_points, gold, gems FROM player_stats WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(3_640, result.getInt("experience_points"))
                            assertEquals(3_700, result.getInt("gold"))
                            assertEquals(8, result.getInt("gems"))
                        }
                    }
                }
            }
        } finally {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url
                this.username = username
                this.password = password
                maximumPoolSize = 1
            }).use { admin ->
                admin.connection.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
