package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.MatchType
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val identityRepository = PostgresGuestIdentityRepository(dataSource)
            val matchRepository = PostgresMatchResultRepository(dataSource)
            val profileRepository = PostgresPlayerProfileRepository(dataSource)
            val leaderboardRepository = PostgresLeaderboardRepository(dataSource)
            val host = identityRepository.resolveGuest("Test host", null, 1_000L)
            val guest = identityRepository.resolveGuest("Test guest", null, 1_000L)
            val matchId = UUID.randomUUID().toString()
            val matchStartedAt = System.currentTimeMillis()
            try {
                val match = CompletedMatch(
                    matchId = matchId,
                    roomName = "Integration test",
                    gameMode = ProtocolGameMode.ORDER,
                    startedAtMillis = matchStartedAt,
                    endedAtMillis = matchStartedAt + 1_000L,
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
                            matchStartedAt + 90L + number * 10L,
                            number
                        )
                    } + MatchSelectionEvent(
                        guest.playerId,
                        "accepted-guest",
                        51,
                        51,
                        SelectionResult.ACCEPTED,
                        matchStartedAt + 595L,
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
                assertEquals(1, profile.modeStatistics.size)
                with(profile.modeStatistics.single()) {
                    assertEquals(ProtocolGameMode.ORDER, gameMode)
                    assertEquals(1, totalMatches)
                    assertEquals(1, wins)
                    assertEquals(0, losses)
                    assertEquals(0, draws)
                    assertEquals(500, highestScore)
                    assertEquals(500, averageScore)
                }
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT result, COUNT(*) AS count FROM match_events WHERE match_id = ? GROUP BY result"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(matchId))
                        statement.executeQuery().use { result ->
                            val counts = buildMap {
                                while (result.next()) put(result.getString("result"), result.getInt("count"))
                            }
                            assertEquals(mapOf("ACCEPTED" to 51), counts)
                        }
                    }
                }
                val guestProfile = profileRepository.findByPlayerId(guest.playerId)!!
                assertEquals(1, guestProfile.statistics.correctSelections)
                assertEquals(0, guestProfile.statistics.wrongSelections)
                assertEquals(5L, guestProfile.statistics.averageReactionMillis)
                assertEquals(984, guestProfile.statistics.eloRating)
                assertEquals(16, profile.recentMatches.single().eloChange)
                assertEquals(-16, guestProfile.recentMatches.single().eloChange)
                assertEquals(
                    setOf("FIRST_WIN", "PERFECT_GAME", "SPEED_50"),
                    profile.achievements.map { it.code }.toSet()
                )
                assertEquals(0, guestProfile.achievements.size)
                assertFalse(guestProfile.progression.weeklyMissions.first { it.code == "WEEKLY_PERFECT_1" }.completed)
                assertEquals(25, profile.progression.experiencePoints)
                assertEquals(1, profile.progression.level)
                assertEquals(1, profile.progression.dailyMissions.first { it.code == "DAILY_PLAY_3" }.progress)
                assertTrue(profile.progression.dailyMissions.first { it.code == "DAILY_WIN_1" }.completed)
                assertEquals(15, profile.progression.dailyMissions.first { it.code == "DAILY_WIN_1" }.rewardXp)
                val missionReward = profileRepository.claimMissionReward(host.playerId, "DAILY_WIN_1")!!
                val duplicateMissionReward = profileRepository.claimMissionReward(host.playerId, "DAILY_WIN_1")!!
                assertEquals(MissionRewardClaimStatus.CLAIMED, missionReward.status)
                assertEquals(15, missionReward.rewardXp)
                assertEquals(MissionRewardClaimStatus.ALREADY_CLAIMED, duplicateMissionReward.status)
                val rewardedProfile = profileRepository.findByPlayerId(host.playerId)!!
                assertEquals(40, rewardedProfile.progression.experiencePoints)
                assertTrue(rewardedProfile.progression.dailyMissions.first { it.code == "DAILY_WIN_1" }.rewardClaimed)
                assertEquals(50, profile.progression.weeklyMissions.first { it.code == "WEEKLY_CORRECT_100" }.progress)
                assertTrue(profile.progression.weeklyMissions.first { it.code == "WEEKLY_PERFECT_1" }.completed)
                assertFalse(profile.progression.cosmetics.first { it.id == "frame_perfect" }.unlocked)
                assertEquals("Mùa Khởi Đầu", profile.progression.season?.name)
                assertEquals(1016, profile.progression.season?.rating)
                assertEquals(1, profile.progression.season?.placementMatchesPlayed)
                assertEquals("Đang phân hạng", profile.progression.season?.tier)
                val firstCheckIn = profileRepository.claimDailyCheckIn(host.playerId)!!
                val duplicateCheckIn = profileRepository.claimDailyCheckIn(host.playerId)!!
                assertTrue(firstCheckIn.claimed)
                assertEquals(5, firstCheckIn.rewardXp)
                assertFalse(duplicateCheckIn.claimed)
                assertEquals(0, duplicateCheckIn.rewardXp)
                val checkedInProfile = profileRepository.findByPlayerId(host.playerId)!!
                assertEquals(45, checkedInProfile.progression.experiencePoints)
                assertTrue(checkedInProfile.progression.dailyCheckIn.claimedToday)
                assertEquals(1, checkedInProfile.progression.dailyCheckIn.currentStreak)
                assertEquals(1, checkedInProfile.progression.dailyCheckIn.totalCheckIns)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT COUNT(*) AS count FROM daily_check_ins WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(host.playerId))
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(1, result.getInt("count"))
                        }
                    }
                }
                val detail = profileRepository.findMatchDetail(host.playerId, matchId)!!
                assertEquals(1_000L, detail.durationMillis)
                assertEquals(51, detail.events.size)
                assertEquals(51, detail.events.count { it.accepted })
                assertFalse(profileRepository.equipCosmetics(host.playerId, "frame_perfect", "title_rookie"))
                assertTrue(profileRepository.equipCosmetics(host.playerId, "frame_default", "title_rookie"))
                assertTrue(
                    profileRepository.findByPlayerId(host.playerId)!!.progression.cosmetics
                        .first { it.id == "frame_default" }.equipped
                )
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
                assertEquals("Mùa Khởi Đầu", leaderboard.seasonName)
                assertEquals(null, leaderboard.seasonCurrentPlayer)
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


    @Test
    fun `casual match updates history but never changes elo or placement`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val identityRepository = PostgresGuestIdentityRepository(dataSource)
            val matchRepository = PostgresMatchResultRepository(dataSource)
            val profileRepository = PostgresPlayerProfileRepository(dataSource)
            val host = identityRepository.resolveGuest("Casual host", null, 1_000L)
            val guest = identityRepository.resolveGuest("Casual guest", null, 1_000L)
            val matchId = UUID.randomUUID().toString()
            val startedAt = System.currentTimeMillis()
            try {
                matchRepository.save(
                    CompletedMatch(
                        matchId = matchId,
                        roomName = "Casual test",
                        gameMode = ProtocolGameMode.ORDER,
                        startedAtMillis = startedAt,
                        endedAtMillis = startedAt + 1_000L,
                        winnerPlayerId = host.playerId,
                        players = listOf(
                            CompletedMatchPlayer(host.playerId, host.displayName, 500, MatchOutcome.WIN),
                            CompletedMatchPlayer(guest.playerId, guest.displayName, 0, MatchOutcome.LOSS)
                        ),
                        events = emptyList(),
                        matchType = MatchType.CASUAL
                    )
                )

                val profile = profileRepository.findByPlayerId(host.playerId)!!
                assertEquals(1, profile.statistics.totalMatches)
                assertEquals(1, profile.statistics.wins)
                assertEquals(1000, profile.statistics.eloRating)
                assertEquals(MatchType.CASUAL, profile.recentMatches.single().matchType)
                assertEquals(0, profile.recentMatches.single().eloChange)
                assertEquals(0, profile.progression.season?.placementMatchesPlayed)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT COUNT(*) FROM rating_history WHERE match_id = ?"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(matchId))
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals(0, result.getInt(1))
                        }
                    }
                }
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
