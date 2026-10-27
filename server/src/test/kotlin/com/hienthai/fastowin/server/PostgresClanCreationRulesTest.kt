package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ClientMessage
import com.hienthai.fastowin.protocol.ServerMessage
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresClanCreationRulesTest {
    @Test
    fun `game engine returns localized level requirement when rich player cannot create clan`() = runTest {
        withPlayers { fixture ->
            val player = fixture.player(gold = 20_000, gems = 200, experience = 1_259)
            val engine = GameEngine(clanRepository = fixture.repository)
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(player), "Creation rules"))

            val deliveries = engine.handle(player, ClientMessage.CreateClan(uniqueName(), ""))

            val error = assertIs<ServerMessage.Error>(deliveries.single().message)
            assertEquals("CLAN_CREATION_LEVEL_TOO_LOW", error.code)
            assertEquals("ServerClanCreationLevelTooLow", error.messageKey)
        }
    }

    @Test
    fun `rich level nine player is rejected without clearing pending requests or changing persisted state`() = runTest {
        withPlayers { fixture ->
            val owner = fixture.player(gold = 10_000, gems = 100, experience = 1_260)
            val applicant = fixture.player(gold = 20_000, gems = 200, experience = 1_259)
            val existingClan = requireNotNull(fixture.repository.createClan(owner, uniqueName(), "").clanId)
            assertEquals(ClanJoinRequestResult.REQUESTED, fixture.repository.requestJoinClan(applicant, existingClan))

            val rejected = fixture.repository.createClan(applicant, uniqueName(), "")

            assertEquals("LEVEL_TOO_LOW", rejected.status.name)
            assertNull(rejected.clanId)
            fixture.assertState(applicant, gold = 20_000, gems = 200, clans = 0, memberships = 0, charges = 0)
            assertEquals(listOf(existingClan), fixture.repository.getPendingJoinClanIds(applicant))
            assertEquals(owner, fixture.repository.getClanById(existingClan)?.ownerId)
        }
    }

    @Test
    fun `level ten player with exact price creates clan and records both debits`() = runTest {
        withPlayers { fixture ->
            // Level 10 starts at 100 + 110 + ... + 180 = 1,260 XP.
            val player = fixture.player(gold = 10_000, gems = 100, experience = 1_260)

            val created = fixture.repository.createClan(player, uniqueName(), "")

            assertEquals(ClanCreationStatus.CREATED, created.status)
            assertEquals(created.clanId, fixture.repository.getClanByUserId(player)?.id)
            fixture.assertState(player, gold = 0, gems = 0, clans = 1, memberships = 1, charges = 1)
            fixture.dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT source_id, gold_delta, gems_delta, xp_delta FROM wallet_transactions WHERE user_id = ? AND source_type = 'CLAN_CREATION'"
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(player))
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(created.clanId, result.getString("source_id"))
                        assertEquals(-10_000, result.getInt("gold_delta"))
                        assertEquals(-100, result.getInt("gems_delta"))
                        assertEquals(0, result.getInt("xp_delta"))
                    }
                }
            }
        }
    }

    @Test
    fun `either insufficient currency rejects creation without charging the other currency`() = runTest {
        withPlayers { fixture ->
            listOf(9_999 to 100, 10_000 to 99).forEach { (gold, gems) ->
                val player = fixture.player(gold, gems, experience = 1_260)

                val rejected = fixture.repository.createClan(player, uniqueName(), "")

                assertEquals(ClanCreationStatus.INSUFFICIENT_FUNDS, rejected.status)
                assertNull(rejected.clanId)
                fixture.assertState(player, gold, gems, clans = 0, memberships = 0, charges = 0)
            }
        }
    }

    @Test
    fun `concurrent creation charges eligible player only once`() = runTest {
        withPlayers { fixture ->
            val player = fixture.player(gold = 20_000, gems = 200, experience = 1_260)
            val start = CompletableDeferred<Unit>()

            val results = coroutineScope {
                List(2) {
                    async(Dispatchers.IO) {
                        start.await()
                        fixture.repository.createClan(player, uniqueName(), "")
                    }
                }.also { start.complete(Unit) }.awaitAll()
            }

            assertEquals(listOf(ClanCreationStatus.CREATED, ClanCreationStatus.ALREADY_MEMBER).toSet(), results.map { it.status }.toSet())
            fixture.assertState(player, gold = 10_000, gems = 100, clans = 1, memberships = 1, charges = 1)
        }
    }

    @Test
    fun `existing membership takes priority over new creation eligibility rules`() = runTest {
        withPlayers { fixture ->
            val player = fixture.player(gold = 10_000, gems = 100, experience = 1_260)
            val clanId = requireNotNull(fixture.repository.createClan(player, uniqueName(), "").clanId)
            fixture.dataSource.connection.use { connection ->
                connection.prepareStatement("UPDATE player_stats SET experience_points = 0 WHERE user_id = ?").use {
                    it.setObject(1, UUID.fromString(player))
                    it.executeUpdate()
                }
            }

            assertEquals(ClanCreationStatus.ALREADY_MEMBER, fixture.repository.createClan(player, uniqueName(), "").status)

            assertEquals(clanId, fixture.repository.getClanByUserId(player)?.id)
            fixture.assertState(player, gold = 0, gems = 0, clans = 1, memberships = 1, charges = 1)
        }
    }

    private class Fixture(val dataSource: HikariDataSource) {
        val repository = PostgresClanRepository(dataSource)
        val users = mutableListOf<UUID>()

        suspend fun player(gold: Int, gems: Int, experience: Int): String {
            val player = PostgresGuestIdentityRepository(dataSource).resolveGuest("Creation rules", null, 1_900_000_000_000L)
            val userId = UUID.fromString(player.playerId)
            users += userId
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO player_stats (user_id, gold, gems, experience_points, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id) DO UPDATE SET gold = EXCLUDED.gold, gems = EXCLUDED.gems,
                        experience_points = EXCLUDED.experience_points
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.setInt(2, gold)
                    statement.setInt(3, gems)
                    statement.setInt(4, experience)
                    statement.executeUpdate()
                }
            }
            return player.playerId
        }

        fun assertState(userId: String, gold: Int, gems: Int, clans: Int, memberships: Int, charges: Int) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT s.gold, s.gems,
                        (SELECT COUNT(*) FROM clans WHERE owner_id = s.user_id) AS clans,
                        (SELECT COUNT(*) FROM clan_members WHERE user_id = s.user_id) AS memberships,
                        (SELECT COUNT(*) FROM wallet_transactions WHERE user_id = s.user_id AND source_type = 'CLAN_CREATION') AS charges
                    FROM player_stats s WHERE s.user_id = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(userId))
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(gold, result.getInt("gold"))
                        assertEquals(gems, result.getInt("gems"))
                        assertEquals(clans, result.getInt("clans"))
                        assertEquals(memberships, result.getInt("memberships"))
                        assertEquals(charges, result.getInt("charges"))
                    }
                }
            }
        }
    }

    private suspend fun withPlayers(block: suspend (Fixture) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val fixture = Fixture(dataSource)
            try {
                block(fixture)
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        fixture.users.forEach { userId ->
                            statement.setObject(1, userId)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                }
            }
        }
    }

    private fun uniqueName() = "Create ${UUID.randomUUID().toString().take(8)}"
}
