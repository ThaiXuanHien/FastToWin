package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PostgresClanRepositoryTest {
    @Test
    fun `player can request multiple clans and first approval wins`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val auth = AuthenticationService(
                PostgresAuthRepository(dataSource),
                PasswordHasher(iterations = 1_000),
                nowMillis = { NOW }
            )
            val suffix = UUID.randomUUID().toString()
            val firstOwner = assertIs<AuthResult.Success>(
                auth.register("clan-owner-a-$suffix@example.com", PASSWORD, "Owner A", "android")
            ).session
            val secondOwner = assertIs<AuthResult.Success>(
                auth.register("clan-owner-b-$suffix@example.com", PASSWORD, "Owner B", "android")
            ).session
            val applicant = assertIs<AuthResult.Success>(
                auth.register("clan-applicant-$suffix@example.com", PASSWORD, "Applicant", "ios")
            ).session
            try {
                val clans = PostgresClanRepository(dataSource)
                val shortSuffix = suffix.take(8)
                val firstClanId = requireNotNull(clans.createClan(firstOwner.userId, "Clan A $shortSuffix", "A"))
                val secondClanId = requireNotNull(clans.createClan(secondOwner.userId, "Clan B $shortSuffix", "B"))

                assertEquals(
                    ClanJoinRequestResult.OWN_CLAN,
                    clans.requestJoinClan(firstOwner.userId, firstClanId)
                )
                assertEquals(
                    ClanJoinRequestResult.REQUESTED,
                    clans.requestJoinClan(applicant.userId, firstClanId)
                )
                assertEquals(
                    ClanJoinRequestResult.REQUESTED,
                    clans.requestJoinClan(applicant.userId, secondClanId)
                )
                assertEquals(
                    setOf(firstClanId, secondClanId),
                    clans.getPendingJoinClanIds(applicant.userId).toSet()
                )

                assertEquals(
                    ClanJoinResponseResult.APPROVED,
                    clans.respondJoinRequest(secondClanId, secondOwner.userId, applicant.userId, accept = true)
                )
                assertEquals(secondClanId, clans.getClanByUserId(applicant.userId)?.id)
                assertTrue(clans.getPendingJoinClanIds(applicant.userId).isEmpty())
                assertEquals(
                    ClanJoinResponseResult.REQUEST_NOT_FOUND,
                    clans.respondJoinRequest(firstClanId, firstOwner.userId, applicant.userId, accept = true)
                )

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "UPDATE clans SET quest_progress = quest_target WHERE id = ?"
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(secondClanId))
                        statement.executeUpdate()
                    }
                }
                val completedClan = requireNotNull(clans.getClanById(secondClanId))
                assertEquals(1_000, completedClan.quest?.rewardGold)
                assertEquals(100, completedClan.quest?.rewardXp)
                assertTrue(clans.claimQuestReward(secondClanId, applicant.userId))
                assertTrue(!clans.claimQuestReward(secondClanId, applicant.userId))
                val rewardedClan = requireNotNull(clans.getClanById(secondClanId))
                assertTrue(rewardedClan.members.first { it.userId == applicant.userId }.questRewardClaimed)
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        SELECT s.gold, s.experience_points, COUNT(w.id) AS wallet_entries
                        FROM player_stats s
                        LEFT JOIN wallet_transactions w
                          ON w.user_id = s.user_id AND w.source_type = 'CLAN_QUEST'
                        WHERE s.user_id = ?
                        GROUP BY s.gold, s.experience_points
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(applicant.userId))
                        statement.executeQuery().use { result ->
                            assertTrue(result.next())
                            assertEquals(1_000, result.getInt("gold"))
                            assertEquals(100, result.getInt("experience_points"))
                            assertEquals(1, result.getInt("wallet_entries"))
                        }
                    }
                }
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id IN (?, ?, ?)").use { statement ->
                        statement.setObject(1, UUID.fromString(firstOwner.userId))
                        statement.setObject(2, UUID.fromString(secondOwner.userId))
                        statement.setObject(3, UUID.fromString(applicant.userId))
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NOW = 1_900_000_000_000L
    }
}
