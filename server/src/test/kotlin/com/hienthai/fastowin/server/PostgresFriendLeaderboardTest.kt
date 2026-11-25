package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostgresFriendLeaderboardTest {
    @Test
    fun `friend leaderboard ranks the player and accepted active friends by Elo`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        val username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
        val password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
        val schema = "friend_leaderboard_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            maximumPoolSize = 1
        }).use { admin ->
            admin.connection.use { connection ->
                connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            }
        }
        val schemaUrl = url + if ('?' in url) "&currentSchema=$schema" else "?currentSchema=$schema"
        try {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = schemaUrl
                this.username = username
                this.password = password
                maximumPoolSize = 2
            }).use { dataSource ->
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas(schema)
                    .defaultSchema(schema)
                    .load()
                    .migrate()
                val auth = AuthenticationService(
                    PostgresAuthRepository(dataSource),
                    PasswordHasher(iterations = 1_000),
                    nowMillis = { NOW }
                )
                val suffix = UUID.randomUUID().toString()
                suspend fun register(name: String) = assertIs<AuthResult.Success>(
                    auth.register("friend-$name-$suffix@example.com", PASSWORD, name, "android")
                ).session.userId
                val me = register("Me")
                val strong = register("Strong")
                val fresh = register("Fresh")
                val pending = register("Pending")
                val stranger = register("Stranger")
                val banned = register("Banned")

                dataSource.connection.use { connection ->
                    // Elo: Strong 1900, Me 1500, Stranger 2500 (không phải bạn); Fresh chưa có player_stats.
                    connection.prepareStatement(
                        """
                        INSERT INTO player_stats (user_id, elo_rating, wins, total_matches, updated_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id) DO UPDATE SET elo_rating = EXCLUDED.elo_rating,
                            wins = EXCLUDED.wins, total_matches = EXCLUDED.total_matches
                        """.trimIndent()
                    ).use { statement ->
                        listOf(me to 1500, strong to 1900, stranger to 2500, banned to 2200, pending to 2100).forEach { (id, elo) ->
                            statement.setObject(1, UUID.fromString(id))
                            statement.setInt(2, elo)
                            statement.setInt(3, 10)
                            statement.setInt(4, 20)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                    connection.prepareStatement("DELETE FROM player_stats WHERE user_id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(fresh))
                        statement.executeUpdate()
                    }
                    connection.prepareStatement(
                        """
                        INSERT INTO friendships (id, requester_id, addressee_id, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.trimIndent()
                    ).use { statement ->
                        listOf(
                            Triple(me, strong, "ACCEPTED"),
                            Triple(fresh, me, "ACCEPTED"),
                            Triple(me, pending, "PENDING"),
                            Triple(banned, me, "ACCEPTED")
                        ).forEach { (requester, addressee, status) ->
                            statement.setObject(1, UUID.randomUUID())
                            statement.setObject(2, UUID.fromString(requester))
                            statement.setObject(3, UUID.fromString(addressee))
                            statement.setString(4, status)
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                    connection.prepareStatement("UPDATE users SET status = 'BLOCKED' WHERE id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(banned))
                        statement.executeUpdate()
                    }
                }

                val friends = PostgresLeaderboardRepository(dataSource).load(me, 30).friendPlayers

                assertEquals(listOf("Strong", "Me", "Fresh"), friends.map { it.displayName })
                assertEquals(listOf(1, 2, 3), friends.map { it.rank })
                assertEquals(listOf(1900, 1500, 1000), friends.map { it.eloRating })
                assertEquals(listOf(strong, me, fresh), friends.map { it.userId })
            }
        } finally {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url
                this.username = username
                this.password = password
                maximumPoolSize = 1
            }).use { admin ->
                admin.connection.use { connection ->
                    connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
    }

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NOW = 1_900_000_000_000L
    }
}
