package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClanDonationCurrency
import com.hienthai.fastowin.protocol.ClanDonationStatus
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostgresClanDonationTest {
    @Test
    fun `donation atomically updates wallet member clan and level exactly once`() = runTest {
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
            val owner = assertIs<AuthResult.Success>(
                auth.register("clan-donation-$suffix@example.com", PASSWORD, "Donor", "android")
            ).session
            val ownerId = UUID.fromString(owner.userId)
            val repository = PostgresClanRepository(dataSource)
            try {
                val clanId = requireNotNull(
                    repository.createClan(owner.userId, "Donate ${suffix.take(8)}", "Clan donation test")
                )
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (user_id, gold, gems, updated_at)
                        VALUES (?, 20000, 10, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE SET
                            gold = EXCLUDED.gold,
                            gems = EXCLUDED.gems,
                            updated_at = EXCLUDED.updated_at
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, ownerId)
                        statement.executeUpdate()
                    }
                }

                val goldRequest = UUID.randomUUID().toString()
                assertEquals(
                    ClanDonationResult(ClanDonationStatus.APPLIED, experienceGranted = 100, clanLevel = 1),
                    repository.donateToClan(owner.userId, clanId, goldRequest, ClanDonationCurrency.GOLD, 1_000)
                )
                assertEquals(
                    ClanDonationResult(ClanDonationStatus.DUPLICATE),
                    repository.donateToClan(owner.userId, clanId, goldRequest, ClanDonationCurrency.GOLD, 1_000)
                )
                assertEquals(
                    ClanDonationStatus.APPLIED,
                    repository.donateToClan(
                        owner.userId,
                        clanId,
                        UUID.randomUUID().toString(),
                        ClanDonationCurrency.GEMS,
                        2
                    ).status
                )
                assertEquals(
                    ClanDonationStatus.INVALID_AMOUNT,
                    repository.donateToClan(
                        owner.userId,
                        clanId,
                        UUID.randomUUID().toString(),
                        ClanDonationCurrency.GOLD,
                        150
                    ).status
                )
                assertEquals(
                    ClanDonationResult(ClanDonationStatus.APPLIED, experienceGranted = 880, clanLevel = 2),
                    repository.donateToClan(
                        owner.userId,
                        clanId,
                        UUID.randomUUID().toString(),
                        ClanDonationCurrency.GOLD,
                        8_800
                    )
                )
                assertEquals(
                    ClanDonationStatus.INSUFFICIENT_FUNDS,
                    repository.donateToClan(
                        owner.userId,
                        clanId,
                        UUID.randomUUID().toString(),
                        ClanDonationCurrency.GEMS,
                        50
                    ).status
                )

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        SELECT s.gold, s.gems,
                               c.level, c.experience_points, c.donated_gold, c.donated_gems,
                               cm.donated_gold AS member_gold, cm.donated_gems AS member_gems,
                               (SELECT COUNT(*) FROM clan_donations d WHERE d.user_id = ?) AS donation_count,
                               (SELECT COUNT(*) FROM wallet_transactions w
                                WHERE w.user_id = ? AND w.source_type = 'CLAN_DONATION') AS wallet_count
                        FROM player_stats s
                        JOIN clan_members cm ON cm.user_id = s.user_id
                        JOIN clans c ON c.id = cm.clan_id
                        WHERE s.user_id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, ownerId)
                        statement.setObject(2, ownerId)
                        statement.setObject(3, ownerId)
                        statement.executeQuery().use { result ->
                            check(result.next())
                            assertEquals(10_200, result.getInt("gold"))
                            assertEquals(8, result.getInt("gems"))
                            assertEquals(2, result.getInt("level"))
                            assertEquals(1_000L, result.getLong("experience_points"))
                            assertEquals(9_800L, result.getLong("donated_gold"))
                            assertEquals(2L, result.getLong("donated_gems"))
                            assertEquals(9_800L, result.getLong("member_gold"))
                            assertEquals(2L, result.getLong("member_gems"))
                            assertEquals(3, result.getInt("donation_count"))
                            assertEquals(3, result.getInt("wallet_count"))
                        }
                    }
                }

                val snapshot = requireNotNull(repository.getClanById(clanId))
                assertEquals(2, snapshot.level)
                assertEquals(1_000L, snapshot.experiencePoints)
                assertEquals(0, snapshot.currentLevelExperience)
                assertEquals(1_500, snapshot.nextLevelExperience)
                assertEquals(9_800L, snapshot.donatedGold)
                assertEquals(2L, snapshot.donatedGems)
                snapshot.members.single { it.userId == owner.userId }.let { member ->
                    assertEquals(9_800L, member.donatedGold)
                    assertEquals(2L, member.donatedGems)
                }
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        statement.setObject(1, ownerId)
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
