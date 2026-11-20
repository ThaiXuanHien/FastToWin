package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.PendingEmailVerificationResponse
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostgresPendingEmailVerificationTest {
    @Test
    fun `correction retains referral and discards verification and reset tokens for the old address`() = runTest {
        withDatabase { database ->
            val auth = AuthenticationService(PostgresAuthRepository(database), PasswordHasher(1_000))
            suspend fun register(email: String, referralCode: String? = null) = assertIs<AuthResult.Success>(
                auth.register(email, PASSWORD, "Player", "android", referralCode = referralCode)
            ).session
            val inviter = register("inviter@example.com")
            val inviterVerification = assertIs<AccountActionResult.Success>(auth.requestEmailVerification(inviter.accessToken))
            assertIs<AccountActionResult.Success>(
                auth.confirmEmailVerification(inviter.accessToken, checkNotNull(inviterVerification.emailVerificationCode))
            )
            val inviterCode = checkNotNull(auth.referralSummary(UUID.fromString(inviter.userId))).code
            val originalEmail = "typo@example.com"
            val invitee = register(originalEmail, inviterCode)
            val account = checkNotNull(auth.authenticateAccessToken(invitee.accessToken))
            val verification = assertIs<AccountActionResult.Success>(auth.requestEmailVerification(invitee.accessToken))
            val reset = assertIs<AccountActionResult.Success>(auth.requestPasswordReset(originalEmail))
            val correctedEmail = "corrected@example.com"

            assertEquals(
                PendingEmailVerificationResult.EmailAlreadyExists,
                auth.changePendingEmail(account, invitee.accessToken, "INVITER@example.com")
            )
            assertEquals(
                PendingEmailVerificationResult.Current(PendingEmailVerificationResponse(correctedEmail, false)),
                auth.changePendingEmail(account, invitee.accessToken, " CORRECTED@example.com ")
            )
            assertEquals(invitee.userId, auth.authenticateAccessToken(invitee.accessToken)?.userId?.toString())
            assertEquals(1, auth.referralSummary(UUID.fromString(inviter.userId))?.pendingCount)
            assertEquals("INVALID_VERIFICATION_CODE", assertIs<AccountActionResult.Failure>(
                auth.confirmEmailVerification(invitee.accessToken, checkNotNull(verification.emailVerificationCode))
            ).code)
            database.connection.use { connection ->
                connection.prepareStatement("SELECT count(*) FROM password_reset_tokens WHERE user_id = ?").use { statement ->
                    statement.setObject(1, account.userId)
                    statement.executeQuery().use { result ->
                        check(result.next())
                        assertEquals(0, result.getInt(1))
                    }
                }
            }
            assertIs<AccountActionResult.Failure>(
                auth.resetPassword(originalEmail, checkNotNull(reset.resetToken), "another-password")
            )
            val corrected = assertIs<AccountActionResult.Success>(auth.requestEmailVerification(invitee.accessToken))
            assertIs<AccountActionResult.Success>(
                auth.confirmEmailVerification(invitee.accessToken, checkNotNull(corrected.emailVerificationCode))
            )
            assertEquals(
                PendingEmailVerificationResult.Current(PendingEmailVerificationResponse(correctedEmail, true)),
                auth.emailVerificationStatus(invitee.accessToken)
            )

            val exempt = register("support@example.com")
            val exemptAccount = checkNotNull(auth.authenticateAccessToken(exempt.accessToken))
            database.connection.use { connection ->
                connection.prepareStatement("UPDATE users SET support_email_verification_exempt = TRUE WHERE id = ?")
                    .use { statement ->
                        statement.setObject(1, exemptAccount.userId)
                        assertEquals(1, statement.executeUpdate())
                    }
            }
            assertEquals(
                PendingEmailVerificationResult.EmailAlreadyVerified,
                auth.changePendingEmail(exemptAccount, exempt.accessToken, "new-support@example.com")
            )
        }
    }

    @Test
    fun `an older delivery finishing last cannot retire a newer staged code`() = runTest {
        withDatabase { database ->
            val auth = AuthenticationService(PostgresAuthRepository(database), PasswordHasher(1_000))
            val session = assertIs<AuthResult.Success>(
                auth.register("first@example.com", PASSWORD, "Player", "android")
            ).session
            val oldAddressStarted = CompletableDeferred<String>()
            val oldAddressAccepted = CompletableDeferred<Unit>()
            val oldAddressDelivery = async {
                auth.requestEmailVerification(session.accessToken) { recipient, _ ->
                    oldAddressStarted.complete(recipient)
                    oldAddressAccepted.await()
                }
            }
            assertEquals("first@example.com", oldAddressStarted.await())
            val account = checkNotNull(auth.authenticateAccessToken(session.accessToken))
            assertIs<PendingEmailVerificationResult.Current>(
                auth.changePendingEmail(account, session.accessToken, "corrected@example.com")
            )

            val olderStarted = CompletableDeferred<String>()
            val olderAccepted = CompletableDeferred<Unit>()
            val olderDelivery = async {
                auth.requestEmailVerification(session.accessToken) { recipient, _ ->
                    olderStarted.complete(recipient)
                    olderAccepted.await()
                }
            }
            assertEquals("corrected@example.com", olderStarted.await())
            val newest = assertIs<AccountActionResult.Success>(auth.requestEmailVerification(session.accessToken))
            olderAccepted.complete(Unit)
            oldAddressAccepted.complete(Unit)
            assertEquals("EMAIL_VERIFICATION_SUPERSEDED", assertIs<AccountActionResult.Failure>(olderDelivery.await()).code)
            assertEquals("EMAIL_VERIFICATION_SUPERSEDED", assertIs<AccountActionResult.Failure>(oldAddressDelivery.await()).code)
            assertIs<AccountActionResult.Success>(
                auth.confirmEmailVerification(session.accessToken, checkNotNull(newest.emailVerificationCode))
            )
        }
    }

    private suspend fun withDatabase(test: suspend (DataSource) -> Unit) {
        val url = System.getenv("TEST_DATABASE_URL") ?: return
        val schema = "pending_email_test_" + UUID.randomUUID().toString().replace("-", "")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 4
        }).use { source ->
            val flyway = Flyway.configure().dataSource(source).schemas(schema).cleanDisabled(false).load()
            try {
                flyway.migrate()
                test(object : DataSource by source {
                    override fun getConnection() = source.connection.apply { this.schema = schema }
                })
            } finally {
                flyway.clean()
            }
        }
    }

    private companion object {
        const val PASSWORD = "strong-password-123"
    }
}
