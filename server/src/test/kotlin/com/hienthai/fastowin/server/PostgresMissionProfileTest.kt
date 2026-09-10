package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresMissionProfileTest {
    @Test
    fun `profile returns only active missions and defaults missing progress to zero`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Mission profile", null, 1_000L)
            val userId = UUID.fromString(player.playerId)
            val today = LocalDate.of(2026, 9, 3)
            val zone = ZoneId.of("Asia/Bangkok")
            val clock = Clock.fixed(today.atTime(12, 0).atZone(zone).toInstant(), zone)
            val repository = PostgresPlayerProfileRepository(dataSource, clock)
            try {
                val initial = repository.findByPlayerId(player.playerId)!!.progression
                assertEquals(
                    listOf(
                        "DAILY_CHECK_IN",
                        "DAILY_RANKED_2",
                        "DAILY_CORRECT_100",
                        "DAILY_PERFECT_WIN_1"
                    ),
                    initial.dailyMissions.map { it.code }
                )
                assertTrue(initial.dailyMissions.all { it.progress == 0 })
                assertEquals(
                    listOf(
                        "WEEKLY_PLAY_15",
                        "WEEKLY_DONATE_GOLD_2000",
                        "WEEKLY_WIN_5",
                        "WEEKLY_PERFECT_3"
                    ),
                    initial.weeklyMissions.map { it.code }
                )
                assertTrue(initial.weeklyMissions.all { it.progress == 0 })

                assertTrue(repository.claimDailyCheckIn(player.playerId)!!.claimed)
                val refreshed = repository.findByPlayerId(player.playerId)!!.progression
                val checkInMission = refreshed.dailyMissions.single { it.code == "DAILY_CHECK_IN" }
                assertEquals(1, checkInMission.progress)
                assertTrue(checkInMission.completed)
                assertTrue(refreshed.dailyMissions.filterNot { it.code == "DAILY_CHECK_IN" }.all { it.progress == 0 })

                val reward = repository.claimMissionReward(player.playerId, "DAILY_CHECK_IN")!!
                val duplicateReward = repository.claimMissionReward(player.playerId, "DAILY_CHECK_IN")!!
                assertEquals(MissionRewardClaimStatus.CLAIMED, reward.status)
                assertEquals(10, reward.rewardXp)
                assertEquals(50, reward.rewardGold)
                assertEquals(MissionRewardClaimStatus.ALREADY_CLAIMED, duplicateReward.status)
                assertTrue(
                    repository.findByPlayerId(player.playerId)!!.progression.dailyMissions
                        .single { it.code == "DAILY_CHECK_IN" }
                        .rewardClaimed
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
