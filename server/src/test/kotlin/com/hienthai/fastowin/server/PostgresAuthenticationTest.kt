package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class PostgresAuthenticationTest {
    @Test
    fun `guest upgrade preserves identity statistics and profile`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            val identityRepository = PostgresGuestIdentityRepository(dataSource)
            val guest = identityRepository.resolveGuest("Guest upgrade", null, NOW_MILLIS)
            val userId = UUID.fromString(guest.playerId)
            val email = "guest-upgrade-${UUID.randomUUID()}@example.com"
            val playerCode = dataSource.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO player_stats (user_id, total_matches, wins, highest_score, updated_at) VALUES (?, 7, 4, 480, NOW())"
                ).use { statement ->
                    statement.setObject(1, userId)
                    statement.executeUpdate()
                }
                connection.prepareStatement("SELECT player_code FROM profiles WHERE user_id = ?").use { statement ->
                    statement.setObject(1, userId)
                    statement.executeQuery().use { result -> result.next(); result.getString("player_code") }
                }
            }
            val service = AuthenticationService(
                repository = PostgresAuthRepository(dataSource),
                passwordHasher = PasswordHasher(iterations = 1_000),
                nowMillis = { NOW_MILLIS + 1_000 }
            )
            try {
                val upgraded = assertIs<AuthResult.Success>(
                    service.upgradeGuest(guest.resumeToken, email, PASSWORD, "android")
                ).session
                assertEquals(guest.playerId, upgraded.userId)
                assertEquals(guest.displayName, upgraded.displayName)
                assertEquals(userId, service.authenticateAccessToken(upgraded.accessToken)?.userId)

                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        """
                        SELECT u.account_type, u.email_normalized, p.player_code,
                               ps.total_matches, ps.wins, ps.highest_score
                        FROM users u
                        JOIN profiles p ON p.user_id = u.id
                        JOIN player_stats ps ON ps.user_id = u.id
                        WHERE u.id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.setObject(1, userId)
                        statement.executeQuery().use { result ->
                            result.next()
                            assertEquals("REGISTERED", result.getString("account_type"))
                            assertEquals(email, result.getString("email_normalized"))
                            assertEquals(playerCode, result.getString("player_code"))
                            assertEquals(7, result.getInt("total_matches"))
                            assertEquals(4, result.getInt("wins"))
                            assertEquals(480, result.getInt("highest_score"))
                        }
                    }
                    connection.prepareStatement(
                        "SELECT revoked_at FROM sessions WHERE resume_token_hash = ?"
                    ).use { statement ->
                        statement.setString(1, hashToken(guest.resumeToken))
                        statement.executeQuery().use { result ->
                            result.next()
                            assertNotEquals(null, result.getTimestamp("revoked_at"))
                        }
                    }
                }

                val reused = assertIs<AuthResult.Failure>(
                    service.upgradeGuest(guest.resumeToken, "other-$email", PASSWORD, "ios")
                )
                assertEquals("INVALID_GUEST_SESSION", reused.code)
                val login = assertIs<AuthResult.Success>(service.login(email, PASSWORD, "ios")).session
                assertEquals(guest.playerId, login.userId)
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

    @Test
    fun `registered session persists rotates and revokes in PostgreSQL`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            val email = "auth-${UUID.randomUUID()}@example.com"
            val service = AuthenticationService(
                repository = PostgresAuthRepository(dataSource),
                passwordHasher = PasswordHasher(iterations = 1_000),
                nowMillis = { NOW_MILLIS }
            )
            try {
                val registration = assertIs<AuthResult.Success>(
                    service.register(email, PASSWORD, "Postgres player", "android")
                ).session
                val login = assertIs<AuthResult.Success>(
                    service.login(email.uppercase(), PASSWORD, "ios")
                ).session
                assertEquals(registration.userId, login.userId)
                assertEquals("Postgres player", login.displayName)
                val authenticated = service.authenticateAccessToken(login.accessToken)
                assertEquals(login.userId, authenticated?.userId.toString())
                assertEquals("Postgres player", authenticated?.displayName)

                val sessions = assertIs<AccountSessionsResult.Success>(
                    service.listSessions(login.accessToken)
                ).sessions
                assertEquals(2, sessions.size)
                assertEquals("ios", sessions.single { it.isCurrent }.devicePlatform)
                val registrationSession = sessions.single { !it.isCurrent }
                assertIs<AccountActionResult.Success>(
                    service.revokeSession(login.accessToken, registrationSession.sessionId)
                )
                assertIs<AuthResult.Failure>(service.refresh(registration.refreshToken))

                val refreshed = assertIs<AuthResult.Success>(
                    service.refresh(login.refreshToken)
                ).session
                assertEquals(login.userId, refreshed.userId)
                assertNotEquals(login.refreshToken, refreshed.refreshToken)
                assertIs<AuthResult.Failure>(service.refresh(login.refreshToken))

                assertIs<AccountActionResult.Success>(
                    service.revokeAllSessions(refreshed.accessToken)
                )
                assertIs<AuthResult.Failure>(service.refresh(refreshed.refreshToken))
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE email_normalized = ?").use { statement ->
                        statement.setString(1, email)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    @Test
    fun `password reset changes credentials revokes sessions and is single use`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            var now = NOW_MILLIS
            val email = "password-reset-${UUID.randomUUID()}@example.com"
            val service = AuthenticationService(
                repository = PostgresAuthRepository(dataSource),
                passwordHasher = PasswordHasher(iterations = 1_000),
                nowMillis = { now }
            )
            try {
                val registered = assertIs<AuthResult.Success>(
                    service.register(email, PASSWORD, "Reset player", "android")
                ).session
                val request = assertIs<AccountActionResult.Success>(service.requestPasswordReset(email))
                val resetToken = requireNotNull(request.resetToken)
                now += 1_000
                assertIs<AccountActionResult.Success>(
                    service.resetPassword(email, resetToken, NEW_PASSWORD)
                )
                assertIs<AuthResult.Failure>(service.refresh(registered.refreshToken))
                assertIs<AccountActionResult.Failure>(
                    service.resetPassword(email, resetToken, "another-password")
                )
                val loggedIn = assertIs<AuthResult.Success>(
                    service.login(email, NEW_PASSWORD, "ios")
                ).session
                assertIs<AccountActionResult.Success>(
                    service.deleteAccount(loggedIn.accessToken, NEW_PASSWORD)
                )
                assertIs<AuthResult.Failure>(service.login(email, NEW_PASSWORD, "android"))
                dataSource.connection.use { connection ->
                    connection.prepareStatement("SELECT COUNT(*) FROM users WHERE email_normalized = ?").use { statement ->
                        statement.setString(1, email)
                        statement.executeQuery().use { result -> result.next(); assertEquals(0, result.getInt(1)) }
                    }
                    connection.prepareStatement("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(registered.userId))
                        statement.executeQuery().use { result -> result.next(); assertEquals(0, result.getInt(1)) }
                    }
                }
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE email_normalized = ?").use { statement ->
                        statement.setString(1, email)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NEW_PASSWORD = "new-strong-password-456"
        const val NOW_MILLIS = 1_800_000_000_000L
    }
}
