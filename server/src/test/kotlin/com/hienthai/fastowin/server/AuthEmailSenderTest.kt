package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthEmailSenderTest {
    @Test
    fun `password reset email renders Japanese content and language`() {
        val content = passwordResetEmailContent(AppLanguage.JAPANESE, "reset-token")

        assertEquals("パスワードを再設定 • Fast To Win", content.subject)
        assertContains(content.html, "<html lang=\"ja\">")
        assertContains(content.html, "以下のコードを使ってパスワードを再設定してください。コードは15分間有効です。")
        assertContains(content.html, "reset-token")
    }

    @Test
    fun `verification email renders Simplified Chinese content`() {
        val content = emailVerificationContent(AppLanguage.SIMPLIFIED_CHINESE, "123456")

        assertEquals("验证邮箱 • Fast To Win", content.subject)
        assertContains(content.html, "<html lang=\"zh-Hans\">")
        assertContains(content.html, "在应用中输入下面的 6 位验证码。验证码有效期为 15 分钟。")
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
