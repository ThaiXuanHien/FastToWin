package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresMatchResultRepositoryTest {
    @Test
    fun `saving the same result twice only increments statistics once`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            val identityRepository = PostgresGuestIdentityRepository(dataSource)
            val matchRepository = PostgresMatchResultRepository(dataSource)
            val profileRepository = PostgresPlayerProfileRepository(dataSource)
            val leaderboardRepository = PostgresLeaderboardRepository(dataSource)
            val host = identityRepository.resolveGuest("Test host", null, 1_000L)
            val guest = identityRepository.resolveGuest("Test guest", null, 1_000L)
            val matchId = UUID.randomUUID().toString()
            try {
                val match = CompletedMatch(
                    matchId = matchId,
                    roomName = "Integration test",
                    gameMode = ProtocolGameMode.ORDER,
                    startedAtMillis = 1_000L,
                    endedAtMillis = 2_000L,
                    winnerPlayerId = host.playerId,
                    players = listOf(
                        CompletedMatchPlayer(host.playerId, host.displayName, 500, MatchOutcome.WIN),
                        CompletedMatchPlayer(guest.playerId, guest.displayName, 0, MatchOutcome.LOSS)
                    ),
                    events = (1..50).map { number ->
                        MatchSelectionEvent(
                            host.playerId,
                            "accepted-$number",
                            number,
                            number,
                            SelectionResult.ACCEPTED,
                            1_090L + number * 10L,
                            number
                        )
                    } + MatchSelectionEvent(
                        guest.playerId,
                        "rejected-1",
                        99,
                        50,
                        SelectionResult.REJECTED,
                        1_595L,
                        51
                    )
                )

                matchRepository.save(match)
                matchRepository.save(match)

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT total_matches, wins, losses, highest_score FROM player_stats WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(host.playerId))
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(1, result.getInt("total_matches"))
                            assertEquals(1, result.getInt("wins"))
                            assertEquals(0, result.getInt("losses"))
                            assertEquals(500, result.getInt("highest_score"))
                        }
                    }
                }

                val profile = profileRepository.findByPlayerId(host.playerId)!!
                assertEquals(host.displayName, profile.displayName)
                assertEquals(1, profile.statistics.totalMatches)
                assertEquals(1, profile.statistics.wins)
                assertEquals(500, profile.statistics.highestScore)
                assertEquals(50, profile.statistics.correctSelections)
                assertEquals(0, profile.statistics.wrongSelections)
                assertEquals(11L, profile.statistics.averageReactionMillis)
                assertEquals(1016, profile.statistics.eloRating)
                assertEquals(1, profile.recentMatches.size)
                assertEquals(guest.displayName, profile.recentMatches.single().opponentName)
                assertEquals(500, profile.recentMatches.single().playerScore)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT result, COUNT(*) AS count FROM match_events WHERE match_id = ? GROUP BY result"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(matchId))
                        statement.executeQuery().use { result ->
                            val counts = buildMap {
                                while (result.next()) put(result.getString("result"), result.getInt("count"))
                            }
                            assertEquals(mapOf("ACCEPTED" to 50, "REJECTED" to 1), counts)
                        }
                    }
                }
                val guestProfile = profileRepository.findByPlayerId(guest.playerId)!!
                assertEquals(0, guestProfile.statistics.correctSelections)
                assertEquals(1, guestProfile.statistics.wrongSelections)
                assertEquals(0L, guestProfile.statistics.averageReactionMillis)
                assertEquals(984, guestProfile.statistics.eloRating)
                assertEquals(16, profile.recentMatches.single().eloChange)
                assertEquals(-16, guestProfile.recentMatches.single().eloChange)
                assertEquals(
                    setOf("FIRST_WIN", "PERFECT_GAME", "SPEED_50"),
                    profile.achievements.map { it.code }.toSet()
                )
                assertEquals(0, guestProfile.achievements.size)
                assertTrue(profileRepository.updateProfile(host.playerId, "Updated host", "crown"))
                val updatedProfile = profileRepository.findByPlayerId(host.playerId)!!
                assertEquals("Updated host", updatedProfile.displayName)
                assertEquals("crown", updatedProfile.avatarId)
                assertEquals(profile.statistics.totalMatches, updatedProfile.statistics.totalMatches)
                val leaderboard = leaderboardRepository.load(host.playerId, 100)
                assertEquals("Updated host", leaderboard.currentPlayer?.displayName)
                assertEquals(1, leaderboard.currentPlayer?.wins)
                assertEquals(1016, leaderboard.currentPlayer?.eloRating)
                assertEquals("Updated host", leaderboard.topPlayers.first().displayName)
                assertEquals("Updated host", leaderboard.topPlayers.first { it.displayName == "Updated host" }.displayName)
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM matches WHERE id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(matchId))
                        statement.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM users WHERE id IN (?, ?)").use { statement ->
                        statement.setObject(1, UUID.fromString(host.playerId))
                        statement.setObject(2, UUID.fromString(guest.playerId))
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
