package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationService
import com.hienthai.fastowin.localization.TextKey
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
import java.nio.file.Files
import java.nio.file.Path

interface AuthEmailSender {
    val isConfigured: Boolean

    suspend fun sendPasswordReset(recipient: String, resetToken: String, language: AppLanguage)

    suspend fun sendEmailVerification(recipient: String, verificationCode: String, language: AppLanguage)
}

object DisabledAuthEmailSender : AuthEmailSender {
    override val isConfigured: Boolean = false

    override suspend fun sendPasswordReset(recipient: String, resetToken: String, language: AppLanguage) = Unit

    override suspend fun sendEmailVerification(
        recipient: String,
        verificationCode: String,
        language: AppLanguage
    ) = Unit
}

internal data class AuthEmailContent(val subject: String, val html: String)

internal fun passwordResetEmailContent(language: AppLanguage, resetToken: String): AuthEmailContent =
    authEmailContent(
        language = language,
        headingKey = TextKey.ResetPasswordTitle,
        descriptionKey = TextKey.EmailPasswordResetDescription,
        code = resetToken
    )

internal fun emailVerificationContent(
    language: AppLanguage,
    verificationCode: String
): AuthEmailContent = authEmailContent(
    language = language,
    headingKey = TextKey.VerifyEmailTitle,
    descriptionKey = TextKey.EmailVerificationDescription,
    code = verificationCode
)

private fun authEmailContent(
    language: AppLanguage,
    headingKey: TextKey,
    descriptionKey: TextKey,
    code: String
): AuthEmailContent {
    val localization = LocalizationService(language)
    val heading = localization.text(headingKey)
    val description = localization.text(descriptionKey)
    val ignoreRequest = localization.text(TextKey.EmailIgnoreRequest)
    return AuthEmailContent(
        subject = "$heading • Fast To Win",
        html = """
            <!doctype html>
            <html lang="${language.languageTag}">
              <body style="margin:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#172033">
                <div style="max-width:560px;margin:32px auto;padding:28px;background:#ffffff;border-radius:18px">
                  <div style="font-size:22px;font-weight:800;color:#1667d9">FAST TO WIN</div>
                  <h1 style="font-size:24px;margin:24px 0 12px">$heading</h1>
                  <p style="line-height:1.6">$description</p>
                  <div style="margin:24px 0;padding:18px;border-radius:14px;background:#eef5ff;
                              font-size:24px;font-weight:800;letter-spacing:2px;text-align:center">$code</div>
                  <p style="font-size:13px;color:#667085">$ignoreRequest</p>
                </div>
              </body>
            </html>
        """.trimIndent()
    )
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
        fun fromEnvironment(
            values: Map<String, String> = System.getenv(),
            fileReader: (String) -> String = { Files.readString(Path.of(it)) }
        ): SmtpEmailSettings? {
            val host = values["FASTTOWIN_SMTP_HOST"]?.trim().orEmpty()
            val username = values["FASTTOWIN_SMTP_USERNAME"]?.trim().orEmpty()
            val password = readEnvironmentSecret("FASTTOWIN_SMTP_PASSWORD", values, fileReader).orEmpty()
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

    override suspend fun sendPasswordReset(
        recipient: String,
        resetToken: String,
        language: AppLanguage
    ) {
        send(recipient, passwordResetEmailContent(language, resetToken))
    }

    override suspend fun sendEmailVerification(
        recipient: String,
        verificationCode: String,
        language: AppLanguage
    ) {
        send(recipient, emailVerificationContent(language, verificationCode))
    }

    private suspend fun send(
        recipient: String,
        content: AuthEmailContent
    ) = withContext(Dispatchers.IO) {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(settings.fromEmail, settings.fromName, Charsets.UTF_8.name()))
            setRecipient(Message.RecipientType.TO, InternetAddress(recipient))
            setSubject(content.subject, Charsets.UTF_8.name())
            setContent(content.html, "text/html; charset=UTF-8")
        }
        Transport.send(message)
    }

    private companion object {
        const val SMTP_TIMEOUT_MILLIS = 10_000
    }
}

internal fun configuredAuthEmailSender(): AuthEmailSender =
    SmtpEmailSettings.fromEnvironment()?.let(::SmtpAuthEmailSender) ?: DisabledAuthEmailSender
