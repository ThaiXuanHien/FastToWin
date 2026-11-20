package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PostgresPlayerReferralTest {
    @Test
    fun `verified first matches grant both rewards once with a monthly cap for the inviter`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        val schema = "referral_test_" + UUID.randomUUID().toString().replace("-", "")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 4
        }).use { source ->
            val flyway = Flyway.configure().dataSource(source).schemas(schema).cleanDisabled(false).load()
            flyway.migrate()
            try {
                val database = object : javax.sql.DataSource by source {
                    override fun getConnection() = source.connection.apply { this.schema = schema }
                }
                var now = Instant.parse("2026-11-30T16:57:00Z").toEpochMilli()
                val auth = AuthenticationService(PostgresAuthRepository(database), PasswordHasher(1_000), { now })
                val matches = PostgresMatchResultRepository(database)
                val notifications = PostgresNotificationRepository(database)
                val profiles = PostgresPlayerProfileRepository(database)

                suspend fun signup(email: String, code: String? = null): AuthResult =
                    auth.register(email, "Referral-test-123!", "Test player", "android", referralCode = code)

                suspend fun verify(account: AuthSessionResponse) {
                    val issued = assertIs<AccountActionResult.Success>(auth.requestEmailVerification(account.accessToken))
                    assertIs<AccountActionResult.Success>(
                        auth.confirmEmailVerification(account.accessToken, checkNotNull(issued.emailVerificationCode))
                    )
                }

                suspend fun verifiedAccount(index: Int, code: String? = null): AuthSessionResponse =
                    assertIs<AuthResult.Success>(signup("referral-$index@example.test", code)).session.also { verify(it) }

                val inviter = verifiedAccount(0)
                val opponent = verifiedAccount(1)
                val inviterId = UUID.fromString(inviter.userId)
                val code = checkNotNull(auth.referralSummary(inviterId)).code
                val invalid = assertIs<AuthResult.Failure>(signup("new@example.test", "MISSINGCODE"))
                assertEquals("INVALID_REFERRAL_CODE", invalid.code)
                val invitee = assertIs<AuthResult.Success>(signup("new@example.test", " ${code.lowercase()} ")).session
                assertEquals(1, auth.referralSummary(inviterId)?.pendingCount)

                fun match(
                    player: AuthSessionResponse,
                    eligible: Boolean = true,
                    left: Boolean = false,
                    played: Boolean = true
                ) = CompletedMatch(
                    matchId = UUID.randomUUID().toString(),
                    roomName = "Referral test",
                    gameMode = ProtocolGameMode.ORDER,
                    startedAtMillis = now + 1_000,
                    endedAtMillis = now + 10_000,
                    winnerPlayerId = opponent.userId,
                    players = listOf(
                        CompletedMatchPlayer(opponent.userId, "Test opponent", 50, MatchOutcome.WIN),
                        CompletedMatchPlayer(player.userId, "Test player", 14, MatchOutcome.LOSS, left)
                    ),
                    events = if (played) listOf(
                        MatchSelectionEvent(player.userId, "correct", 1, 1, SelectionResult.ACCEPTED, now + 5_000, 1)
                    ) else emptyList(),
                    matchType = MatchType.CASUAL,
                    referralEligible = eligible
                )

                assertTrue(matches.saveAndGetReferralRecipients(match(invitee)).isEmpty())
                verify(invitee)
                assertTrue(matches.saveAndGetReferralRecipients(match(invitee, left = true)).isEmpty())
                assertTrue(matches.saveAndGetReferralRecipients(match(invitee, eligible = false)).isEmpty())
                assertTrue(matches.saveAndGetReferralRecipients(match(invitee, played = false)).isEmpty())
                assertEquals(1, auth.referralSummary(inviterId)?.pendingCount)

                val firstMatch = match(invitee)
                assertEquals(setOf(inviter.userId, invitee.userId), matches.saveAndGetReferralRecipients(firstMatch))
                assertTrue(matches.saveAndGetReferralRecipients(firstMatch).isEmpty())
                assertTrue(matches.saveAndGetReferralRecipients(match(invitee)).isEmpty())
                assertEquals(500, profiles.findByPlayerId(inviter.userId)?.progression?.gold)
                assertEquals(1, notifications.loadNotifications(inviter.userId).count { it.titleKey == TextKey.ReferralRewardTitle.name })
                assertEquals("300", notifications.loadNotifications(invitee.userId)
                    .single { it.titleKey == TextKey.ReferralRewardTitle.name }.messageArgs["gold"])

                repeat(5) { index ->
                    val player = verifiedAccount(index + 2, code)
                    val expected = if (index < 4) setOf(inviter.userId, player.userId) else setOf(player.userId)
                    assertEquals(expected, matches.saveAndGetReferralRecipients(match(player)))
                }
                with(checkNotNull(auth.referralSummary(inviterId))) {
                    assertEquals(0, pendingCount)
                    assertEquals(5, rewardedCount)
                    assertEquals(5, rewardedThisMonth)
                    assertEquals(1, completedWithoutInviterBonusCount)
                }
                assertEquals(2500, profiles.findByPlayerId(inviter.userId)?.progression?.gold)

                now = Instant.parse("2026-11-30T17:00:00Z").toEpochMilli()
                assertEquals(0, auth.referralSummary(inviterId)?.rewardedThisMonth)
                val guest = PostgresGuestIdentityRepository(database).resolveGuest("Guest player", null, now)
                suspend fun upgrade(referralCode: String) = auth.upgradeGuest(
                    guest.resumeToken, "guest@example.test", "Referral-test-123!", "android", referralCode = referralCode
                )
                assertEquals("INVALID_REFERRAL_CODE", assertIs<AuthResult.Failure>(upgrade("MISSINGCODE")).code)
                val nextMonthPlayer = assertIs<AuthResult.Success>(upgrade(code)).session
                assertEquals(guest.playerId, nextMonthPlayer.userId)
                verify(nextMonthPlayer)
                assertEquals(setOf(inviter.userId, nextMonthPlayer.userId), matches.saveAndGetReferralRecipients(match(nextMonthPlayer)))
                assertEquals(1, auth.referralSummary(inviterId)?.rewardedThisMonth)
                assertEquals(3000, profiles.findByPlayerId(inviter.userId)?.progression?.gold)
            } finally {
                flyway.clean()
            }
        }
    }
}
