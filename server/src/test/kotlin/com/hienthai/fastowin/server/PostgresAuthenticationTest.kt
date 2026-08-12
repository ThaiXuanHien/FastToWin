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

                val refreshed = assertIs<AuthResult.Success>(
                    service.refresh(login.refreshToken)
                ).session
                assertEquals(login.userId, refreshed.userId)
                assertNotEquals(login.refreshToken, refreshed.refreshToken)
                assertIs<AuthResult.Failure>(service.refresh(login.refreshToken))

                service.logout(refreshed.refreshToken)
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

    private companion object {
        const val PASSWORD = "strong-password-123"
        const val NOW_MILLIS = 1_800_000_000_000L
    }
}
