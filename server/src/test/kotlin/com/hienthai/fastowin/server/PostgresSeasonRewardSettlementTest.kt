package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.RankedTier
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresSeasonRewardSettlementTest {
    @Test
    fun `completed season reward is granted exactly once across concurrent profile loads`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 3
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val identityRepository = PostgresGuestIdentityRepository(dataSource)
            val profileRepository = PostgresPlayerProfileRepository(dataSource)
            val player = identityRepository.resolveGuest("Season reward test", null, 1_000L)
            val seasonId = UUID.randomUUID()
            val incompleteSeasonId = UUID.randomUUID()
            val silverSeasonId = UUID.randomUUID()
            var seasonNumber = 0
            try {
                dataSource.connection.use { connection ->
                    seasonNumber = connection.prepareStatement(
                        "SELECT COALESCE(MAX(season_number), 0) + 100 FROM seasons"
                    ).use { statement ->
                        statement.executeQuery().use { result -> result.next(); result.getInt(1) }
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO seasons (id, season_number, name, starts_at, ends_at, reward_description)
                        VALUES (?, ?, 'Mùa Kiểm Thử', CURRENT_TIMESTAMP - INTERVAL '30 days',
                                CURRENT_TIMESTAMP - INTERVAL '1 day', 'Thưởng theo bậc cao nhất')
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, seasonId)
                        statement.setInt(2, seasonNumber)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO seasons (id, season_number, name, starts_at, ends_at, reward_description)
                        VALUES (?, ?, 'Mùa Chưa Phân Hạng', CURRENT_TIMESTAMP - INTERVAL '60 days',
                                CURRENT_TIMESTAMP - INTERVAL '31 days', 'Thưởng theo bậc cao nhất')
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, incompleteSeasonId)
                        statement.setInt(2, seasonNumber + 1)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO season_ratings (
                            season_id, user_id, rating, matches_played, updated_at,
                            placement_matches, peak_rating
                        ) VALUES (?, ?, 1320, 8, CURRENT_TIMESTAMP, 5, 1380)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, seasonId)
                        statement.setObject(2, UUID.fromString(player.playerId))
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO season_ratings (
                            season_id, user_id, rating, matches_played, updated_at,
                            placement_matches, peak_rating
                        ) VALUES (?, ?, 2400, 4, CURRENT_TIMESTAMP, 4, 2400)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, incompleteSeasonId)
                        statement.setObject(2, UUID.fromString(player.playerId))
                        statement.executeUpdate()
                    }
                }

                val results = coroutineScope {
                    listOf(
                        async { profileRepository.settleCompletedSeasonRewards(player.playerId) },
                        async { profileRepository.settleCompletedSeasonRewards(player.playerId) }
                    ).awaitAll()
                }
                assertEquals(1, results.count { it })
                assertFalse(profileRepository.settleCompletedSeasonRewards(player.playerId))

                val profile = profileRepository.findByPlayerId(player.playerId)!!
                assertEquals(1_000, profile.progression.gold)
                assertEquals(1, profile.progression.gems)
                with(profile.progression.latestSeasonReward!!) {
                    assertEquals("Mùa Kiểm Thử", seasonName)
                    assertEquals(RankedTier.GOLD, tier)
                    assertEquals(1_380, peakRating)
                    assertEquals(1_000, gold)
                    assertEquals(1, gems)
                    assertEquals("season_${seasonNumber}_gold", cosmetic?.id)
                    assertEquals("Khung Mùa Kiểm Thử • Vàng", cosmetic?.name)
                    assertEquals(com.hienthai.fastowin.protocol.CosmeticType.FRAME, cosmetic?.type)
                }
                val seasonFrame = profile.progression.cosmetics.first {
                    it.id == "season_${seasonNumber}_gold"
                }
                assertTrue(seasonFrame.unlocked)
                assertFalse(seasonFrame.equipped)
                assertTrue(
                    profileRepository.equipCosmetics(
                        player.playerId,
                        seasonFrame.id,
                        "title_rookie"
                    )
                )
                assertTrue(
                    profileRepository.findByPlayerId(player.playerId)!!.progression.cosmetics
                        .first { it.id == seasonFrame.id }.equipped
                )
                val history = profileRepository.loadWalletHistory(player.playerId)
                assertEquals(1, history.count { it.sourceType == "SEASON_REWARD" && it.sourceId == seasonId.toString() })
                assertEquals(0, history.count { it.sourceId == incompleteSeasonId.toString() })
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT COUNT(*) FROM player_cosmetics WHERE user_id = ? AND cosmetic_id = ?"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(player.playerId))
                        statement.setString(2, seasonFrame.id)
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(1, result.getInt(1))
                        }
                    }
                }

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO seasons (id, season_number, name, starts_at, ends_at, reward_description)
                        VALUES (?, ?, 'Mùa Danh Hiệu', CURRENT_TIMESTAMP - INTERVAL '10 days',
                                CURRENT_TIMESTAMP - INTERVAL '12 hours', 'Thưởng theo bậc cao nhất')
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, silverSeasonId)
                        statement.setInt(2, seasonNumber + 2)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO season_ratings (
                            season_id, user_id, rating, matches_played, updated_at,
                            placement_matches, peak_rating
                        ) VALUES (?, ?, 1180, 5, CURRENT_TIMESTAMP, 5, 1200)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, silverSeasonId)
                        statement.setObject(2, UUID.fromString(player.playerId))
                        statement.executeUpdate()
                    }
                }
                assertTrue(profileRepository.settleCompletedSeasonRewards(player.playerId))
                val profileWithTitle = profileRepository.findByPlayerId(player.playerId)!!
                val seasonTitle = profileWithTitle.progression.cosmetics.first {
                    it.id == "season_${seasonNumber + 2}_silver"
                }
                assertEquals(com.hienthai.fastowin.protocol.CosmeticType.TITLE, seasonTitle.type)
                assertTrue(seasonTitle.unlocked)
                assertTrue(
                    profileRepository.equipCosmetics(
                        player.playerId,
                        seasonFrame.id,
                        seasonTitle.id
                    )
                )
                assertTrue(
                    profileRepository.findByPlayerId(player.playerId)!!.progression.cosmetics
                        .first { it.id == seasonTitle.id }.equipped
                )
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(player.playerId))
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM seasons WHERE id = ?").use { statement ->
                        statement.setObject(1, seasonId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM seasons WHERE id = ?").use { statement ->
                        statement.setObject(1, incompleteSeasonId)
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM seasons WHERE id = ?").use { statement ->
                        statement.setObject(1, silverSeasonId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
