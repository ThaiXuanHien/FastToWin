package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.hienthai.fastowin.protocol.AuthSessionResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresClanLifecycleTest {
    @Test
    fun `creation charges both currencies atomically and rejects insufficient wallet`() = runTest {
        withDatabase { dataSource ->
            val rich = register(dataSource, "rich")
            val poor = register(dataSource, "poor")
            fund(dataSource.connection, rich.userId, gold = 5_000, gems = 50)
            fund(dataSource.connection, poor.userId, gold = 5_000, gems = 19)
            val repository = PostgresClanRepository(dataSource)

            val created = repository.createClan(rich.userId, uniqueName("Paid"), "")
            assertEquals(ClanCreationStatus.CREATED, created.status)
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    SELECT s.gold, s.gems, COUNT(w.id) AS charges
                    FROM player_stats s
                    LEFT JOIN wallet_transactions w
                      ON w.user_id = s.user_id AND w.source_type = 'CLAN_CREATION'
                    WHERE s.user_id = ?
                    GROUP BY s.gold, s.gems
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(rich.userId))
                    statement.executeQuery().use { result ->
                        assertTrue(result.next())
                        assertEquals(3_000, result.getInt("gold"))
                        assertEquals(30, result.getInt("gems"))
                        assertEquals(1, result.getInt("charges"))
                    }
                }
            }

            assertEquals(
                ClanCreationStatus.INSUFFICIENT_FUNDS,
                repository.createClan(poor.userId, uniqueName("Rejected"), "").status
            )
            assertNull(repository.getClanByUserId(poor.userId))
            assertEquals(5_000 to 19, wallet(dataSource.connection, poor.userId))
        }
    }

    @Test
    fun `leader departure transfers to oldest member and sole leader deletes clan`() = runTest {
        withDatabase { dataSource ->
            val leader = register(dataSource, "leader")
            val oldest = register(dataSource, "oldest")
            val newest = register(dataSource, "newest")
            val solo = register(dataSource, "solo")
            listOf(leader, solo).forEach { fund(dataSource.connection, it.userId, 5_000, 50) }
            val repository = PostgresClanRepository(dataSource)
            val clanId = requireNotNull(repository.createClan(leader.userId, uniqueName("Transfer"), "").clanId)
            val soloClanId = requireNotNull(repository.createClan(solo.userId, uniqueName("Solo"), "").clanId)

            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', ?)"
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(clanId))
                    statement.setObject(2, UUID.fromString(oldest.userId))
                    statement.setTimestamp(3, java.sql.Timestamp(1_000))
                    statement.addBatch()
                    statement.setObject(1, UUID.fromString(clanId))
                    statement.setObject(2, UUID.fromString(newest.userId))
                    statement.setTimestamp(3, java.sql.Timestamp(2_000))
                    statement.addBatch()
                    statement.executeBatch()
                }
            }

            assertTrue(repository.leaveClan(leader.userId))
            val transferred = requireNotNull(repository.getClanById(clanId))
            assertEquals(oldest.userId, transferred.ownerId)
            assertEquals(
                com.hienthai.fastowin.protocol.ClanRole.LEADER,
                transferred.members.single { it.userId == oldest.userId }.role
            )
            assertNull(repository.getClanByUserId(leader.userId))

            assertTrue(repository.leaveClan(solo.userId))
            assertNull(repository.getClanById(soloClanId))
        }
    }

    @Test
    fun `concurrent leader and successor departures both complete without deadlock`() = runTest {
        withDatabase { dataSource ->
            val leader = register(dataSource, "concurrent-leader")
            val successor = register(dataSource, "concurrent-successor")
            val remaining = register(dataSource, "concurrent-remaining")
            fund(dataSource.connection, leader.userId, 5_000, 50)
            val repository = PostgresClanRepository(dataSource)
            val clanId = requireNotNull(
                repository.createClan(leader.userId, uniqueName("Concurrent"), "").clanId
            )
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO clan_members (clan_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', ?)"
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(clanId))
                    statement.setObject(2, UUID.fromString(successor.userId))
                    statement.setTimestamp(3, java.sql.Timestamp(1_000))
                    statement.addBatch()
                    statement.setObject(1, UUID.fromString(clanId))
                    statement.setObject(2, UUID.fromString(remaining.userId))
                    statement.setTimestamp(3, java.sql.Timestamp(2_000))
                    statement.addBatch()
                    statement.executeBatch()
                }
            }

            val start = CompletableDeferred<Unit>()
            val results = coroutineScope {
                listOf(leader.userId, successor.userId).map { userId ->
                    async(Dispatchers.IO) {
                        start.await()
                        PostgresClanRepository(dataSource).leaveClan(userId)
                    }
                }.also { start.complete(Unit) }.awaitAll()
            }

            assertTrue(results.all { it })
            val clan = requireNotNull(repository.getClanById(clanId))
            assertEquals(listOf(remaining.userId), clan.members.map { it.userId })
            assertEquals(remaining.userId, clan.ownerId)
        }
    }

    private suspend fun register(dataSource: HikariDataSource, label: String): AuthSessionResponse =
        assertIs<AuthResult.Success>(
            AuthenticationService(
                PostgresAuthRepository(dataSource),
                PasswordHasher(iterations = 1_000),
                nowMillis = { NOW }
            ).register("$label-${UUID.randomUUID()}@example.com", PASSWORD, label, "android")
        ).session

    private fun fund(connection: Connection, userId: String, gold: Int, gems: Int) {
        connection.use {
            it.prepareStatement(
                """
                INSERT INTO player_stats (user_id, gold, gems, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET gold = EXCLUDED.gold, gems = EXCLUDED.gems
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(userId))
                statement.setInt(2, gold)
                statement.setInt(3, gems)
                statement.executeUpdate()
            }
        }
    }

    private fun wallet(connection: Connection, userId: String): Pair<Int, Int> = connection.use {
        it.prepareStatement("SELECT gold, gems FROM player_stats WHERE user_id = ?").use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                check(result.next())
                result.getInt("gold") to result.getInt("gems")
            }
        }
    }

    private fun uniqueName(prefix: String) = "$prefix ${UUID.randomUUID().toString().take(8)}"

    private suspend fun withDatabase(block: suspend (HikariDataSource) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            block(dataSource)
        }
    }

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NOW = 1_900_000_000_000L
    }
}
