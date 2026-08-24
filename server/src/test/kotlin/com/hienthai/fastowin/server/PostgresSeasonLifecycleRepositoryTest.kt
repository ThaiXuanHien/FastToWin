package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
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
import kotlin.test.assertTrue

class PostgresSeasonLifecycleRepositoryTest {
    @Test
    fun `season rollover is atomic and creates only one next season`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        val schema = "season_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        adminDataSource(url).use { admin ->
            admin.connection.use { connection ->
                connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            }
            try {
                schemaDataSource(url, schema).use { dataSource ->
                    Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .defaultSchema(schema)
                        .load()
                        .migrate()
                    val repository = PostgresSeasonLifecycleRepository(dataSource)
                    val identityRepository = PostgresGuestIdentityRepository(dataSource)
                    val firstPlayer = identityRepository.resolveGuest("Hạng nhất", null, 1_000L)
                    val secondPlayer = identityRepository.resolveGuest("Hạng nhì", null, 1_000L)
                    val firstSeason = dataSource.connection.use { connection ->
                        connection.prepareStatement("SELECT id, ends_at FROM seasons WHERE season_number = 1").use { statement ->
                            statement.executeQuery().use { result ->
                                result.next()
                                result.getObject("id", UUID::class.java) to result.getTimestamp("ends_at")
                            }
                        }
                    }
                    dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            """
                            INSERT INTO season_ratings (
                                season_id, user_id, rating, matches_played,
                                placement_matches, peak_rating, updated_at
                            ) VALUES (?, ?, ?, 8, 5, ?, CURRENT_TIMESTAMP)
                            """.trimIndent()
                        ).use { statement ->
                            listOf(firstPlayer.playerId to 1_400, secondPlayer.playerId to 1_300).forEach { (playerId, rating) ->
                                statement.setObject(1, firstSeason.first)
                                statement.setObject(2, UUID.fromString(playerId))
                                statement.setInt(3, rating)
                                statement.setInt(4, rating)
                                statement.addBatch()
                            }
                            statement.executeBatch()
                        }
                    }
                    val firstSeasonEnd = firstSeason.second
                    val rolloverTime = firstSeasonEnd.time + 1_000L

                    val results = coroutineScope {
                        listOf(
                            async { repository.maintain(rolloverTime) },
                            async { repository.maintain(rolloverTime) }
                        ).awaitAll()
                    }

                    assertEquals(1, results.sumOf { it.createdSeasons })
                    assertEquals(1, results.sumOf { it.archivedSeasons })
                    val leaderboard = PostgresLeaderboardRepository(dataSource).load(firstPlayer.playerId, 100)
                    assertEquals("Mùa Khởi Đầu", leaderboard.previousSeasonName)
                    assertEquals(listOf(1_400, 1_300), leaderboard.previousSeasonTopPlayers.map { it.eloRating })
                    assertEquals(1, leaderboard.previousSeasonCurrentPlayer?.rank)
                    assertEquals("Hạng nhất", leaderboard.previousSeasonTopPlayers.first().displayName)
                    dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            "UPDATE player_stats SET elo_rating = ? WHERE user_id = ?"
                        ).use { statement ->
                            listOf(firstPlayer.playerId to 1_800, secondPlayer.playerId to 1_200).forEach { (playerId, rating) ->
                                statement.setInt(1, rating)
                                statement.setObject(2, UUID.fromString(playerId))
                                statement.addBatch()
                            }
                            statement.executeBatch()
                        }
                    }
                    val matchTime = rolloverTime + 2_000L
                    PostgresMatchResultRepository(dataSource).save(
                        CompletedMatch(
                            matchId = UUID.randomUUID().toString(),
                            roomName = "Kiểm thử reset Elo mùa",
                            gameMode = ProtocolGameMode.ORDER,
                            startedAtMillis = matchTime - 1_000L,
                            endedAtMillis = matchTime,
                            winnerPlayerId = firstPlayer.playerId,
                            players = listOf(
                                CompletedMatchPlayer(
                                    firstPlayer.playerId,
                                    firstPlayer.displayName,
                                    50,
                                    MatchOutcome.WIN
                                ),
                                CompletedMatchPlayer(
                                    secondPlayer.playerId,
                                    secondPlayer.displayName,
                                    40,
                                    MatchOutcome.LOSS
                                )
                            ),
                            events = emptyList(),
                            matchType = MatchType.RANKED
                        )
                    )
                    dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            """
                            SELECT sr.user_id, sr.rating, sr.placement_matches
                            FROM season_ratings sr
                            JOIN seasons s ON s.id = sr.season_id
                            WHERE s.season_number = 2
                            """.trimIndent()
                        ).use { statement ->
                            statement.executeQuery().use { result ->
                                val ratings = buildMap {
                                    while (result.next()) {
                                        assertEquals(1, result.getInt("placement_matches"))
                                        put(
                                            result.getObject("user_id", UUID::class.java).toString(),
                                            result.getInt("rating")
                                        )
                                    }
                                }
                                assertEquals(1_016, ratings[firstPlayer.playerId])
                                assertEquals(984, ratings[secondPlayer.playerId])
                            }
                        }
                    }
                    dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            "SELECT season_number, name, starts_at, ends_at FROM seasons ORDER BY season_number"
                        ).use { statement ->
                            statement.executeQuery().use { result ->
                                assertTrue(result.next())
                                assertEquals(1, result.getInt("season_number"))
                                assertTrue(result.next())
                                assertEquals(2, result.getInt("season_number"))
                                assertEquals("Mùa 2", result.getString("name"))
                                assertEquals(firstSeasonEnd, result.getTimestamp("starts_at"))
                                assertTrue(result.getTimestamp("ends_at").after(firstSeasonEnd))
                                assertTrue(!result.next())
                            }
                        }
                        connection.prepareStatement(
                            "SELECT closed_at FROM seasons WHERE season_number = 1"
                        ).use { statement ->
                            statement.executeQuery().use { result ->
                                result.next()
                                assertTrue(result.getTimestamp("closed_at") != null)
                            }
                        }
                    }
                    assertEquals(SeasonLifecycleResult(), repository.maintain(rolloverTime))
                }
            } finally {
                admin.connection.use { connection ->
                    connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
    }

    private fun adminDataSource(url: String) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        maximumPoolSize = 2
    })

    private fun schemaDataSource(url: String, schema: String) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        maximumPoolSize = 3
        connectionInitSql = "SET search_path TO $schema"
    })
}
