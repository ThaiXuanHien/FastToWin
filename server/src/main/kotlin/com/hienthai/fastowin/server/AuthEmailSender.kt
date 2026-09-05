package com.hienthai.fastowin.server

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

interface AuthEmailSender {
    val isConfigured: Boolean

    suspend fun sendPasswordReset(recipient: String, resetToken: String)

    suspend fun sendEmailVerification(recipient: String, verificationCode: String)
}

object DisabledAuthEmailSender : AuthEmailSender {
    override val isConfigured: Boolean = false

    override suspend fun sendPasswordReset(recipient: String, resetToken: String) = Unit

    override suspend fun sendEmailVerification(recipient: String, verificationCode: String) = Unit
}

data class SmtpEmailSettings(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromEmail: String,
    val fromName: String = "Fast To Win",
    val startTls: Boolean = true,
    val ssl: Boolean = false
) {
    init {
        require(host.isNotBlank()) { "FASTTOWIN_SMTP_HOST must not be blank." }
        require(port in 1..65_535) { "FASTTOWIN_SMTP_PORT must be between 1 and 65535." }
        require(username.isNotBlank()) { "FASTTOWIN_SMTP_USERNAME must not be blank." }
        require(password.isNotBlank()) { "FASTTOWIN_SMTP_PASSWORD must not be blank." }
        require(fromEmail.isNotBlank()) { "FASTTOWIN_SMTP_FROM_EMAIL must not be blank." }
        require(!(startTls && ssl)) { "Enable either SMTP STARTTLS or SSL, not both." }
    }

    companion object {
        fun fromEnvironment(values: Map<String, String> = System.getenv()): SmtpEmailSettings? {
            val host = values["FASTTOWIN_SMTP_HOST"]?.trim().orEmpty()
            val username = values["FASTTOWIN_SMTP_USERNAME"]?.trim().orEmpty()
            val password = values["FASTTOWIN_SMTP_PASSWORD"].orEmpty()
            val fromEmail = values["FASTTOWIN_SMTP_FROM_EMAIL"]?.trim().orEmpty()
            val configuredValues = listOf(host, username, password, fromEmail)
            if (configuredValues.all(String::isEmpty)) return null
            require(configuredValues.none(String::isEmpty)) {
                "SMTP requires FASTTOWIN_SMTP_HOST, FASTTOWIN_SMTP_USERNAME, " +
                    "FASTTOWIN_SMTP_PASSWORD and FASTTOWIN_SMTP_FROM_EMAIL."
            }
            val ssl = values["FASTTOWIN_SMTP_SSL"]?.toBooleanStrictOrNull() ?: false
            val startTls = values["FASTTOWIN_SMTP_STARTTLS"]?.toBooleanStrictOrNull() ?: !ssl
            return SmtpEmailSettings(
                host = host,
                port = values["FASTTOWIN_SMTP_PORT"]?.toIntOrNull() ?: if (ssl) 465 else 587,
                username = username,
                password = password,
                fromEmail = fromEmail,
                fromName = values["FASTTOWIN_SMTP_FROM_NAME"]?.trim().orEmpty().ifBlank { "Fast To Win" },
                startTls = startTls,
                ssl = ssl
            )
        }
    }
}

class SmtpAuthEmailSender(private val settings: SmtpEmailSettings) : AuthEmailSender {
    override val isConfigured: Boolean = true

    private val session: Session by lazy {
        val properties = Properties().apply {
            put("mail.smtp.host", settings.host)
            put("mail.smtp.port", settings.port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", settings.startTls.toString())
            put("mail.smtp.starttls.required", settings.startTls.toString())
            put("mail.smtp.ssl.enable", settings.ssl.toString())
            put("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MILLIS.toString())
            put("mail.smtp.timeout", SMTP_TIMEOUT_MILLIS.toString())
            put("mail.smtp.writetimeout", SMTP_TIMEOUT_MILLIS.toString())
        }
        Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(
                settings.username,
                settings.password
            )
        })
    }

    override suspend fun sendPasswordReset(recipient: String, resetToken: String) {
        send(
            recipient = recipient,
            subject = "Khôi phục mật khẩu Fast To Win",
            heading = "Khôi phục mật khẩu",
            description = "Dùng mã bên dưới để đặt lại mật khẩu. Mã có hiệu lực trong 15 phút.",
            code = resetToken
        )
    }

    override suspend fun sendEmailVerification(recipient: String, verificationCode: String) {
        send(
            recipient = recipient,
            subject = "Xác minh email Fast To Win",
            heading = "Xác minh email",
            description = "Nhập mã 6 số bên dưới trong ứng dụng. Mã có hiệu lực trong 15 phút.",
            code = verificationCode
        )
    }

    private suspend fun send(
        recipient: String,
        subject: String,
        heading: String,
        description: String,
        code: String
    ) = withContext(Dispatchers.IO) {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(settings.fromEmail, settings.fromName, Charsets.UTF_8.name()))
            setRecipient(Message.RecipientType.TO, InternetAddress(recipient))
            setSubject(subject, Charsets.UTF_8.name())
            setContent(emailHtml(heading, description, code), "text/html; charset=UTF-8")
        }
        Transport.send(message)
    }

    private fun emailHtml(heading: String, description: String, code: String): String = """
        <!doctype html>
        <html lang="vi">
          <body style="margin:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#172033">
            <div style="max-width:560px;margin:32px auto;padding:28px;background:#ffffff;border-radius:18px">
              <div style="font-size:22px;font-weight:800;color:#1667d9">FAST TO WIN</div>
              <h1 style="font-size:24px;margin:24px 0 12px">$heading</h1>
              <p style="line-height:1.6">$description</p>
              <div style="margin:24px 0;padding:18px;border-radius:14px;background:#eef5ff;
                          font-size:24px;font-weight:800;letter-spacing:2px;text-align:center">$code</div>
              <p style="font-size:13px;color:#667085">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.</p>
            </div>
          </body>
        </html>
    """.trimIndent()

    private companion object {
        const val SMTP_TIMEOUT_MILLIS = 10_000
    }
}

internal fun configuredAuthEmailSender(): AuthEmailSender =
    SmtpEmailSettings.fromEnvironment()?.let(::SmtpAuthEmailSender) ?: DisabledAuthEmailSender
