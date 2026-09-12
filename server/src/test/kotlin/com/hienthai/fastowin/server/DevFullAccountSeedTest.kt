package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.FRAME_CATALOG
import com.hienthai.fastowin.protocol.GOLD_EXCHANGE_OFFERS
import com.hienthai.fastowin.protocol.TITLE_CATALOG
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevFullAccountSeedTest {
    @Test
    fun `full development account exposes phase two economy without legacy shop inventory`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val email = "phase-two-seed-${UUID.randomUUID()}@example.com"
            val userId = dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val first = seedFullDevelopmentAccount(connection, email, "test-password-hash", "Phase Two Seed")
                    val second = seedFullDevelopmentAccount(connection, email, "test-password-hash", "Phase Two Seed")
                    assertEquals(first, second)
                    connection.commit()
                    first
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                }
            }

            try {
                val profile = assertNotNull(
                    PostgresPlayerProfileRepository(dataSource).findByPlayerId(userId.toString())
                )
                val cosmetics = profile.progression.cosmetics
                val personalAchievementRewardIds = ACHIEVEMENT_DEFINITIONS
                    .filterNot { it.isClanAchievement }
                    .flatMap { listOfNotNull(it.frameId, it.titleId) }
                    .toSet()
                val clanAchievementCodes = ACHIEVEMENT_DEFINITIONS
                    .filter { it.isClanAchievement }
                    .map { it.code }
                    .toSet()

                assertTrue(profile.progression.gems >= GOLD_EXCHANGE_OFFERS.sumOf { it.gemsCost })
                assertEquals(
                    FRAME_CATALOG.map { it.id }.toSet(),
                    cosmetics.filter { it.id in FRAME_CATALOG.map { frame -> frame.id } }
                        .map { it.id }
                        .toSet()
                )
                assertEquals(
                    TITLE_CATALOG.map { it.id }.toSet(),
                    cosmetics.filter { it.id in TITLE_CATALOG.map { title -> title.id } }
                        .map { it.id }
                        .toSet()
                )
                assertTrue(cosmetics.single { it.id == "frame_lightning" }.unlocked)
                assertTrue(cosmetics.single { it.id == "title_godspeed" }.unlocked)
                assertTrue(
                    cosmetics.filter { it.id in personalAchievementRewardIds }.all { it.unlocked }
                )
                assertTrue(profile.achievements.filterNot { it.code in clanAchievementCodes }.all { it.unlocked })
                assertTrue(profile.achievements.filter { it.code in clanAchievementCodes }.none { it.unlocked })
                assertTrue(
                    cosmetics.none { cosmetic ->
                        cosmetic.unlocked && cosmetic.type in setOf(CosmeticType.CARD_BACK, CosmeticType.BOARD_SKIN)
                    }
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
