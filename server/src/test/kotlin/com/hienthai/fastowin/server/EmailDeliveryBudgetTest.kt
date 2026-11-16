package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailDeliveryBudgetTest {
    @Test
    fun `in-memory budget stops at the limit and resets on a new day`() = runTest {
        val budget = InMemoryEmailDailyBudget()
        val day = LocalDate.of(2026, 11, 16)

        assertTrue(budget.tryReserve(day, 2))
        assertTrue(budget.tryReserve(day, 2))
        assertFalse(budget.tryReserve(day, 2))
        assertTrue(budget.tryReserve(day.plusDays(1), 2))
    }

    @Test
    fun `sender refuses once the daily allowance is used and records each outcome`() = runTest {
        val delegate = RecordingSender()
        val sender = MeteredAuthEmailSender(delegate, InMemoryEmailDailyBudget(), EmailDeliverySettings(2, ZoneOffset.UTC), fixedClock())

        sender.sendEmailVerification("a@example.com", "123456", AppLanguage.ENGLISH)
        sender.sendPasswordReset("b@example.com", "token", AppLanguage.ENGLISH)
        assertFailsWith<EmailDailyLimitReachedException> {
            sender.sendEmailVerification("c@example.com", "654321", AppLanguage.ENGLISH)
        }

        assertEquals(listOf("a@example.com", "b@example.com"), delegate.recipients)
        val metrics = sender.renderMetrics()
        assertContains(metrics, "fasttowin_auth_emails_total{kind=\"email_verification\",outcome=\"sent\"} 1")
        assertContains(metrics, "fasttowin_auth_emails_total{kind=\"password_reset\",outcome=\"sent\"} 1")
        assertContains(metrics, "fasttowin_auth_emails_total{kind=\"email_verification\",outcome=\"budget_rejected\"} 1")
        assertContains(metrics, "fasttowin_auth_email_daily_limit 2")
    }

    @Test
    fun `delivery failures are counted and rethrown`() = runTest {
        val sender = MeteredAuthEmailSender(
            RecordingSender(fail = true), InMemoryEmailDailyBudget(), EmailDeliverySettings(5, ZoneOffset.UTC), fixedClock()
        )

        assertFailsWith<IllegalStateException> { sender.sendPasswordReset("a@example.com", "token", AppLanguage.ENGLISH) }

        assertContains(sender.renderMetrics(), "fasttowin_auth_emails_total{kind=\"password_reset\",outcome=\"failed\"} 1")
    }

    @Test
    fun `a counter outage does not block delivery`() = runTest {
        val broken = object : EmailDailyBudget {
            override suspend fun tryReserve(day: LocalDate, limit: Int): Boolean = error("database down")
        }
        val delegate = RecordingSender()
        val sender = MeteredAuthEmailSender(delegate, broken, EmailDeliverySettings(1, ZoneOffset.UTC), fixedClock())

        sender.sendEmailVerification("a@example.com", "123456", AppLanguage.ENGLISH)

        assertEquals(listOf("a@example.com"), delegate.recipients)
        assertContains(sender.renderMetrics(), "outcome=\"budget_unavailable\"} 1")
    }

    @Test
    fun `zero limit disables the cap`() = runTest {
        val delegate = RecordingSender()
        val sender = MeteredAuthEmailSender(delegate, InMemoryEmailDailyBudget(), EmailDeliverySettings(0, ZoneOffset.UTC), fixedClock())

        repeat(3) { sender.sendEmailVerification("p$it@example.com", "123456", AppLanguage.ENGLISH) }

        assertEquals(3, delegate.recipients.size)
    }

    @Test
    fun `settings default to eighty percent of the Brevo free quota`() {
        assertEquals(EmailDeliverySettings(240, ZoneId.of("UTC")), EmailDeliverySettings.fromEnvironment(emptyMap()))
        assertEquals(
            EmailDeliverySettings(4000, ZoneId.of("Asia/Bangkok")),
            EmailDeliverySettings.fromEnvironment(
                mapOf("FASTTOWIN_EMAIL_DAILY_LIMIT" to "4000", "FASTTOWIN_EMAIL_DAILY_LIMIT_ZONE" to "Asia/Bangkok")
            )
        )
        assertFailsWith<IllegalArgumentException> { EmailDeliverySettings.fromEnvironment(mapOf("FASTTOWIN_EMAIL_DAILY_LIMIT" to "-1")) }
        assertFailsWith<IllegalArgumentException> { EmailDeliverySettings.fromEnvironment(mapOf("FASTTOWIN_EMAIL_DAILY_LIMIT" to "many")) }
    }

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC)

    private class RecordingSender(private val fail: Boolean = false) : AuthEmailSender {
        val recipients = mutableListOf<String>()
        override val isConfigured: Boolean = true

        override suspend fun sendPasswordReset(recipient: String, resetToken: String, language: AppLanguage) = record(recipient)

        override suspend fun sendEmailVerification(recipient: String, verificationCode: String, language: AppLanguage) =
            record(recipient)

        private fun record(recipient: String) {
            check(!fail) { "provider unavailable" }
            recipients += recipient
        }
    }
}
