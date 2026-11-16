package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLongArray
import javax.sql.DataSource

/** Thrown when sending would exceed the system-wide daily email allowance (SEC-03). */
class EmailDailyLimitReachedException : RuntimeException("Daily email allowance reached.")

/** Counts emails per calendar day across every server replica. */
interface EmailDailyBudget {
    /** Reserves one email for [day]; false once [limit] emails were already reserved. */
    suspend fun tryReserve(day: LocalDate, limit: Int): Boolean
}

class InMemoryEmailDailyBudget : EmailDailyBudget {
    private val mutex = Mutex()
    private var day: LocalDate? = null
    private var reserved = 0

    override suspend fun tryReserve(day: LocalDate, limit: Int): Boolean = mutex.withLock {
        if (this.day != day) {
            this.day = day
            reserved = 0
        }
        if (reserved >= limit) return@withLock false
        reserved++
        true
    }
}

class PostgresEmailDailyBudget(private val dataSource: DataSource) : EmailDailyBudget {
    override suspend fun tryReserve(day: LocalDate, limit: Int): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            // The conditional upsert is atomic, so concurrent replicas can never overshoot the limit.
            connection.prepareStatement(
                """
                INSERT INTO email_daily_usage (usage_day, reserved) VALUES (?, 1)
                ON CONFLICT (usage_day) DO UPDATE SET reserved = email_daily_usage.reserved + 1
                WHERE email_daily_usage.reserved < ?
                RETURNING reserved
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, day)
                statement.setInt(2, limit)
                statement.executeQuery().use { it.next() }
            }
        }
    }
}

internal data class EmailDeliverySettings(val dailyLimit: Int, val zone: ZoneId) {
    companion object {
        // Brevo Free allows 300 emails per day; stopping at 80% leaves room for retries and manual sends.
        const val DEFAULT_DAILY_LIMIT = 240

        fun fromEnvironment(values: Map<String, String> = System.getenv()): EmailDeliverySettings {
            val rawLimit = values["FASTTOWIN_EMAIL_DAILY_LIMIT"]?.trim().orEmpty()
            val limit = if (rawLimit.isEmpty()) DEFAULT_DAILY_LIMIT else requireNotNull(rawLimit.toIntOrNull()) {
                "FASTTOWIN_EMAIL_DAILY_LIMIT must be a whole number (0 disables the cap)."
            }
            require(limit >= 0) { "FASTTOWIN_EMAIL_DAILY_LIMIT must not be negative." }
            val zone = values["FASTTOWIN_EMAIL_DAILY_LIMIT_ZONE"]?.trim()?.takeIf(String::isNotEmpty)
                ?.let(ZoneId::of) ?: ZoneId.of("UTC")
            return EmailDeliverySettings(limit, zone)
        }
    }
}

/**
 * Applies the daily allowance and records delivery outcomes per email kind (EML-03),
 * so failed deliveries are visible even when players receive a generic response.
 */
class MeteredAuthEmailSender internal constructor(
    private val delegate: AuthEmailSender,
    private val budget: EmailDailyBudget,
    private val settings: EmailDeliverySettings,
    private val clock: Clock = Clock.systemUTC()
) : AuthEmailSender {
    override val isConfigured: Boolean get() = delegate.isConfigured

    private val outcomes = AtomicLongArray(EmailKind.entries.size * EmailOutcome.entries.size)

    override suspend fun sendPasswordReset(recipient: String, resetToken: String, language: AppLanguage) =
        deliver(EmailKind.PASSWORD_RESET) { delegate.sendPasswordReset(recipient, resetToken, language) }

    override suspend fun sendEmailVerification(recipient: String, verificationCode: String, language: AppLanguage) =
        deliver(EmailKind.EMAIL_VERIFICATION) { delegate.sendEmailVerification(recipient, verificationCode, language) }

    private suspend fun deliver(kind: EmailKind, send: suspend () -> Unit) {
        if (settings.dailyLimit > 0) {
            val reserved = try {
                budget.tryReserve(LocalDate.now(clock.withZone(settings.zone)), settings.dailyLimit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A counter outage must not lock players out; the miss stays visible in metrics.
                record(kind, EmailOutcome.BUDGET_UNAVAILABLE)
                true
            }
            if (!reserved) {
                record(kind, EmailOutcome.BUDGET_REJECTED)
                throw EmailDailyLimitReachedException()
            }
        }
        try {
            send()
            record(kind, EmailOutcome.SENT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            record(kind, EmailOutcome.FAILED)
            throw failure
        }
    }

    private fun record(kind: EmailKind, outcome: EmailOutcome) {
        outcomes.incrementAndGet(kind.ordinal * EmailOutcome.entries.size + outcome.ordinal)
    }

    fun renderMetrics(): String = buildString {
        append("# HELP fasttowin_auth_emails_total Account emails by kind and delivery outcome.\n")
        append("# TYPE fasttowin_auth_emails_total counter\n")
        EmailKind.entries.forEach { kind ->
            EmailOutcome.entries.forEach { outcome ->
                append("fasttowin_auth_emails_total{kind=\"${kind.label}\",outcome=\"${outcome.label}\"} ")
                append(outcomes.get(kind.ordinal * EmailOutcome.entries.size + outcome.ordinal)).append('\n')
            }
        }
        append("# HELP fasttowin_auth_email_daily_limit Daily account email allowance (0 means no cap).\n")
        append("# TYPE fasttowin_auth_email_daily_limit gauge\n")
        append("fasttowin_auth_email_daily_limit ").append(settings.dailyLimit).append('\n')
    }

    private enum class EmailKind(val label: String) {
        PASSWORD_RESET("password_reset"),
        EMAIL_VERIFICATION("email_verification")
    }

    private enum class EmailOutcome(val label: String) {
        SENT("sent"),
        FAILED("failed"),
        BUDGET_REJECTED("budget_rejected"),
        BUDGET_UNAVAILABLE("budget_unavailable")
    }
}
