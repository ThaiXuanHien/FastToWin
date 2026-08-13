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

class PostgresFriendRepositoryTest {
    @Test
    fun `friend requests persist accept and cascade on account deletion`() = runTest {
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
            val firstEmail = "friend-a-${UUID.randomUUID()}@example.com"
            val secondEmail = "friend-b-${UUID.randomUUID()}@example.com"
            val first = assertIs<AuthResult.Success>(auth.register(firstEmail, PASSWORD, "Friend A", "android")).session
            val second = assertIs<AuthResult.Success>(auth.register(secondEmail, PASSWORD, "Friend B", "ios")).session
            try {
                val secondCode = dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT player_code FROM profiles WHERE user_id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(second.userId))
                        statement.executeQuery().use { result -> result.next(); result.getString(1) }
                    }
                }
                val friends = PostgresFriendRepository(dataSource)
                assertIs<FriendRequestResult.SelfRequest>(
                    friends.sendRequest(first.userId, first.userId.replace("-", "").take(10), NOW)
                )
                val sent = assertIs<FriendRequestResult.Success>(
                    friends.sendRequest(first.userId, secondCode.lowercase(), NOW)
                )
                assertEquals(second.userId, sent.recipientId)
                val incoming = friends.load(second.userId).incomingRequests.single()
                assertEquals(first.userId, incoming.userId)
                assertIs<FriendRequestResult.AlreadyExists>(
                    friends.sendRequest(first.userId, secondCode, NOW + 1)
                )
                assertIs<FriendResponseResult.Success>(
                    friends.respond(second.userId, incoming.requestId, accept = true, NOW + 2)
                )
                assertTrue(friends.areFriends(first.userId, second.userId))
                assertEquals("Friend B", friends.load(first.userId).friends.single().displayName)

                assertIs<AccountActionResult.Success>(auth.deleteAccount(second.accessToken, PASSWORD))
                assertTrue(friends.load(first.userId).friends.isEmpty())
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE email_normalized IN (?, ?)").use { statement ->
                        statement.setString(1, firstEmail)
                        statement.setString(2, secondEmail)
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
