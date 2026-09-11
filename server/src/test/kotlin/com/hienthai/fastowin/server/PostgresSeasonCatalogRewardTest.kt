package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresSeasonCatalogRewardTest {
    @Test
    fun `challenger unlocks its catalog frame while only final rank one receives speed king`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val identities = PostgresGuestIdentityRepository(dataSource)
            val champion = identities.resolveGuest("Season champion", null, 1_000L)
            val runnerUp = identities.resolveGuest("Season runner up", null, 1_000L)
            val championId = UUID.fromString(champion.playerId)
            val runnerUpId = UUID.fromString(runnerUp.playerId)
            val seasonId = UUID.randomUUID()
            try {
                dataSource.connection.use { connection ->
                    val seasonNumber = connection.prepareStatement(
                        "SELECT COALESCE(MAX(season_number), 0) + 200 FROM seasons"
                    ).use { statement ->
                        statement.executeQuery().use { result -> result.next(); result.getInt(1) }
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO seasons (id, season_number, name, starts_at, ends_at, reward_description)
                        VALUES (?, ?, 'Mùa Thách Đấu', CURRENT_TIMESTAMP - INTERVAL '30 days',
                                CURRENT_TIMESTAMP - INTERVAL '1 day', 'Thưởng theo bậc cao nhất')
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, seasonId)
                        statement.setInt(2, seasonNumber)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO season_ratings (
                            season_id, user_id, rating, matches_played, updated_at,
                            placement_matches, peak_rating
                        ) VALUES (?, ?, 2750, 30, CURRENT_TIMESTAMP, 5, 2810),
                                 (?, ?, 2720, 32, CURRENT_TIMESTAMP, 5, 2780)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, seasonId)
                        statement.setObject(2, championId)
                        statement.setObject(3, seasonId)
                        statement.setObject(4, runnerUpId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO season_leaderboard_archive (
                            season_id, user_id, final_rank, display_name, player_code,
                            avatar_url, frame_id, wins, total_matches, highest_score,
                            rating, season_matches
                        ) VALUES (?, ?, 1, 'Season champion', 'CHAMP001', NULL,
                                  'frame_default', 20, 30, 50, 2750, 30),
                                 (?, ?, 2, 'Season runner up', 'RUNNER02', NULL,
                                  'frame_default', 18, 32, 50, 2720, 32)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, seasonId)
                        statement.setObject(2, championId)
                        statement.setObject(3, seasonId)
                        statement.setObject(4, runnerUpId)
                        statement.executeUpdate()
                    }
                }

                val repository = PostgresPlayerProfileRepository(dataSource)
                assertTrue(repository.settleCompletedSeasonRewards(champion.playerId))
                assertTrue(repository.settleCompletedSeasonRewards(runnerUp.playerId))
                assertFalse(repository.settleCompletedSeasonRewards(champion.playerId))

                val championProfile = repository.findByPlayerId(champion.playerId)!!
                assertEquals("frame_challenger", championProfile.progression.latestSeasonReward?.cosmetic?.id)
                assertEquals("Thách Đấu", championProfile.progression.latestSeasonReward?.cosmetic?.name)
                val championCosmetics = championProfile.progression.cosmetics
                assertTrue(championCosmetics.single { it.id == "frame_challenger" }.unlocked)
                assertTrue(championCosmetics.single { it.id == "title_speed_king" }.unlocked)

                val runnerUpCosmetics = repository.findByPlayerId(runnerUp.playerId)!!
                    .progression.cosmetics
                assertTrue(runnerUpCosmetics.single { it.id == "frame_challenger" }.unlocked)
                assertFalse(runnerUpCosmetics.single { it.id == "title_speed_king" }.unlocked)

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        SELECT cosmetic_id, COUNT(*)
                        FROM player_cosmetics
                        WHERE user_id = ? AND cosmetic_id IN ('frame_challenger', 'title_speed_king')
                        GROUP BY cosmetic_id
                        ORDER BY cosmetic_id
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, championId)
                        statement.executeQuery().use { result ->
                            val counts = buildMap {
                                while (result.next()) put(result.getString(1), result.getInt(2))
                            }
                            assertEquals(mapOf("frame_challenger" to 1, "title_speed_king" to 1), counts)
                        }
                    }
                }
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id IN (?, ?)").use { statement ->
                        statement.setObject(1, championId)
                        statement.setObject(2, runnerUpId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM seasons WHERE id = ?").use { statement ->
                        statement.setObject(1, seasonId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
