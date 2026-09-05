package com.hienthai.fastowin.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductionConfigurationTest {
    @Test
    fun `secret can be loaded from a mounted file`() {
        val value = readEnvironmentSecret(
            name = "DATABASE_PASSWORD",
            values = mapOf("DATABASE_PASSWORD_FILE" to "/run/secrets/database_password"),
            fileReader = { path ->
                assertEquals("/run/secrets/database_password", path)
                "a-strong-password\r\n"
            }
        )

        assertEquals("a-strong-password", value)
    }

    @Test
    fun `secret rejects simultaneous direct and file values`() {
        assertFailsWith<IllegalArgumentException> {
            readEnvironmentSecret(
                name = "DATABASE_PASSWORD",
                values = mapOf(
                    "DATABASE_PASSWORD" to "direct-value",
                    "DATABASE_PASSWORD_FILE" to "/run/secrets/database_password"
                )
            )
        }
    }

    @Test
    fun `production origins require an https origin without path`() {
        assertEquals(
            listOf(AllowedWebOrigin("play.fasttowin.vn", "https")),
            parseAllowedWebOrigins("https://play.fasttowin.vn", requireHttps = true)
        )
        assertFailsWith<IllegalArgumentException> {
            parseAllowedWebOrigins("http://play.fasttowin.vn", requireHttps = true)
        }
        assertFailsWith<IllegalArgumentException> {
            parseAllowedWebOrigins("https://play.fasttowin.vn/account", requireHttps = true)
        }
    }

    @Test
    fun `complete production environment passes validation with mounted secrets`() {
        validateProductionEnvironment(
            values = productionValues(),
            fileReader = { path ->
                when (path) {
                    "/run/secrets/database_password" -> "database-password-123"
                    "/run/secrets/smtp_password" -> "smtp-password-123"
                    else -> error("Unexpected secret path: $path")
                }
            }
        )
    }

    @Test
    fun `production environment rejects placeholder domain and weak database password`() {
        assertFailsWith<IllegalArgumentException> {
            validateProductionEnvironment(
                values = productionValues() + ("FASTTOWIN_PUBLIC_URL" to "https://example.com"),
                fileReader = { "database-password-123" }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateProductionEnvironment(
                values = productionValues() +
                    ("DATABASE_PASSWORD" to "fasttowin") -
                    "DATABASE_PASSWORD_FILE",
                fileReader = { "smtp-password-123" }
            )
        }
    }

    private fun productionValues() = mapOf(
        "FASTTOWIN_PUBLIC_URL" to "https://play.fasttowin.vn",
        "FASTTOWIN_WEB_ORIGINS" to "https://play.fasttowin.vn",
        "DATABASE_URL" to "jdbc:postgresql://database:5432/fasttowin",
        "DATABASE_PASSWORD_FILE" to "/run/secrets/database_password",
        "FASTTOWIN_SMTP_HOST" to "smtp.provider.vn",
        "FASTTOWIN_SMTP_USERNAME" to "mailer",
        "FASTTOWIN_SMTP_PASSWORD_FILE" to "/run/secrets/smtp_password",
        "FASTTOWIN_SMTP_FROM_EMAIL" to "no-reply@fasttowin.vn"
    )
}
