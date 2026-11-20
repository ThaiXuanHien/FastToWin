package com.hienthai.fastowin.server.admin

import com.hienthai.fastowin.server.AuthEmailContent
import com.hienthai.fastowin.server.BrevoEmailSettings
import com.hienthai.fastowin.server.EmailHttpTransport
import com.hienthai.fastowin.server.JdkEmailHttpTransport
import com.hienthai.fastowin.server.ResendAuthEmailSender
import com.hienthai.fastowin.server.ResendEmailSettings
import com.hienthai.fastowin.server.legacyFallback
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** ADM-11: one security-relevant admin event. Never include passwords, tokens or codes. */
data class AdminAlert(val title: String, val details: List<Pair<String, String>>, val atEpochMillis: Long)

/** Delivers alerts without blocking or failing the admin request that raised them. */
fun interface AdminAlertSink {
    fun alert(alert: AdminAlert)

    companion object {
        val NONE = AdminAlertSink { }

        /**
         * HTTPS delivery to FASTTOWIN_ADMIN_ALERT_EMAILS (comma separated) via Brevo or Resend.
         */
        fun fromEnvironment(values: Map<String, String> = System.getenv()): AdminAlertSink {
            val recipients = values["FASTTOWIN_ADMIN_ALERT_EMAILS"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            if (recipients.isEmpty()) return NONE
            recipients.forEach { require(runCatching { InternetAddress(it, true).validate() }.isSuccess) { "FASTTOWIN_ADMIN_ALERT_EMAILS contains an invalid address." } }
            return when (values["FASTTOWIN_EMAIL_PROVIDER"]?.trim()?.lowercase()) {
                "brevo" -> BrevoAdminAlertSink(BrevoEmailSettings.fromEnvironment(values), recipients)
                "resend" -> {
                    val sender = ResendAuthEmailSender(ResendEmailSettings.fromEnvironment(values))
                    EmailAdminAlertSink { content -> sender.send(recipients, content) }
                }
                else -> {
                    System.err.println("Admin alerts disabled: FASTTOWIN_ADMIN_ALERT_EMAILS requires Brevo or Resend")
                    NONE
                }
            }
        }
    }
}

internal class BrevoAdminAlertSink(
    private val settings: BrevoEmailSettings,
    private val recipients: List<String>,
    private val transport: EmailHttpTransport = JdkEmailHttpTransport,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AdminAlertSink {
    private val delegate = EmailAdminAlertSink(scope) { content ->
        val status = transport.send(request(content))
        check(status in 200..299) { "Brevo returned HTTP $status" }
    }

    val failures get() = delegate.failures

    override fun alert(alert: AdminAlert) = delegate.alert(alert)

    private fun request(content: AuthEmailContent): HttpRequest {
        val payload = buildJsonObject {
            put("sender", buildJsonObject { put("email", settings.fromEmail); put("name", settings.fromName) })
            put("to", buildJsonArray { recipients.forEach { add(buildJsonObject { put("email", it) }) } })
            put("subject", content.subject)
            put("htmlContent", content.html)
        }
        return HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
            .header("api-key", settings.apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(java.time.Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
    }
}

private class EmailAdminAlertSink(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val send: suspend (AuthEmailContent) -> Unit
) : AdminAlertSink {
    val failures = AtomicLong()

    override fun alert(alert: AdminAlert) {
        scope.launch {
            runCatching { send(adminAlertContent(alert)) }.onFailure {
                failures.incrementAndGet()
                System.err.println("Admin alert delivery failed: ${it.message}")
            }
        }
    }
}

private fun adminAlertContent(alert: AdminAlert): AuthEmailContent {
    val time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Asia/Bangkok"))
        .format(Instant.ofEpochMilli(alert.atEpochMillis))
    val rows = (listOf(legacyFallback("Thời điểm") to legacyFallback("$time (giờ Việt Nam)")) + alert.details).joinToString("") { (label, value) ->
        "<tr><th align=\"left\" style=\"padding:4px 12px 4px 0\">${escape(label)}</th><td style=\"padding:4px 0\">${escape(value)}</td></tr>"
    }
    return AuthEmailContent(
        subject = "[FastToWin Admin] ${alert.title}",
        html = "<p><strong>${escape(alert.title)}</strong></p><table>$rows</table>" +
            legacyFallback("<p>Nếu đây không phải thao tác của nhóm, hãy kiểm tra Nhật ký kiểm toán và làm theo mục Khôi phục khẩn cấp trong docs/admin-management.md.</p>")
    )
}

private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Counts failed sign-ins per email and alerts on every fifth within fifteen minutes. */
internal class FailedLoginTracker(private val nowMillis: () -> Long) {
    private val windows = ConcurrentHashMap<String, Pair<Long, Int>>()

    /** Returns the failure count when it reaches an alert threshold, otherwise null. */
    fun record(email: String): Int? {
        val now = nowMillis()
        if (windows.size > 10_000) windows.entries.removeIf { now - it.value.first > WINDOW_MILLIS }
        val (_, count) = windows.compute(email) { _, current ->
            if (current == null || now - current.first > WINDOW_MILLIS) now to 1 else current.first to current.second + 1
        }!!
        return count.takeIf { it % THRESHOLD == 0 }
    }

    private companion object {
        const val WINDOW_MILLIS = 15 * 60_000L
        const val THRESHOLD = 5
    }
}
