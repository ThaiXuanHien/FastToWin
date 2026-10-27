package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.resolveAppLanguage
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthEmailSenderTest {
    @Test
    fun `password reset email for a removed saved language renders English`() {
        val language = resolveAppLanguage("ja", listOf("vi-VN"))
        val content = passwordResetEmailContent(language, "reset-token")

        assertEquals("Reset password • Fast To Win", content.subject)
        assertContains(content.html, "<html lang=\"en\">")
        assertContains(content.html, "Use the code below to reset your password. The code is valid for 15 minutes.")
        assertContains(content.html, "reset-token")
    }

    @Test
    fun `verification email renders Vietnamese content`() {
        val content = emailVerificationContent(AppLanguage.VIETNAMESE, "123456")

        assertEquals("Xác minh email • Fast To Win", content.subject)
        assertContains(content.html, "<html lang=\"vi\">")
        assertContains(content.html, "Nhập mã 6 số bên dưới trong ứng dụng. Mã có hiệu lực trong 15 phút.")
        assertContains(content.html, "123456")
    }

    @Test
    fun `smtp settings are disabled when no values are provided`() {
        assertNull(SmtpEmailSettings.fromEnvironment(emptyMap()))
    }

    @Test
    fun `smtp settings support starttls and ssl defaults`() {
        val base = mapOf(
            "FASTTOWIN_SMTP_HOST" to "smtp.example.com",
            "FASTTOWIN_SMTP_USERNAME" to "mailer",
            "FASTTOWIN_SMTP_PASSWORD" to "secret",
            "FASTTOWIN_SMTP_FROM_EMAIL" to "hello@example.com"
        )
        val startTls = requireNotNull(SmtpEmailSettings.fromEnvironment(base))
        val ssl = requireNotNull(
            SmtpEmailSettings.fromEnvironment(base + ("FASTTOWIN_SMTP_SSL" to "true"))
        )

        assertEquals(587, startTls.port)
        assertEquals(true, startTls.startTls)
        assertEquals(false, startTls.ssl)
        assertEquals(465, ssl.port)
        assertEquals(false, ssl.startTls)
        assertEquals(true, ssl.ssl)
    }

    @Test
    fun `partial smtp configuration fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            SmtpEmailSettings.fromEnvironment(
                mapOf("FASTTOWIN_SMTP_HOST" to "smtp.example.com")
            )
        }
    }

    @Test
    fun `smtp password supports mounted secret file`() {
        val settings = requireNotNull(
            SmtpEmailSettings.fromEnvironment(
                values = mapOf(
                    "FASTTOWIN_SMTP_HOST" to "smtp.example.com",
                    "FASTTOWIN_SMTP_USERNAME" to "mailer",
                    "FASTTOWIN_SMTP_PASSWORD_FILE" to "/run/secrets/smtp_password",
                    "FASTTOWIN_SMTP_FROM_EMAIL" to "hello@example.com"
                ),
                fileReader = { "file-secret\n" }
            )
        )

        assertEquals("file-secret", settings.password)
    }
}
