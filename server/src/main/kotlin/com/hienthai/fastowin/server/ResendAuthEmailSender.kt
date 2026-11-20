package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

internal class ResendEmailSettings(
    val apiKey: String,
    fromEmail: String,
    fromName: String = "Fast To Win"
) {
    val from: String

    init {
        require(apiKey.startsWith("re_") && apiKey.length >= 8 && apiKey.all { it in '!'..'~' }) {
            "FASTTOWIN_RESEND_API_KEY must be a valid Resend API key."
        }
        val address = runCatching { InternetAddress(fromEmail, true).also(InternetAddress::validate) }.getOrNull()
        require(address != null && !address.isGroup && address.address == fromEmail) {
            "FASTTOWIN_EMAIL_FROM_EMAIL must contain a single email address without a display name."
        }
        require(fromName.isNotBlank() && fromName.none(Char::isISOControl)) {
            "FASTTOWIN_EMAIL_FROM_NAME must be a non-blank single-line name."
        }
        from = InternetAddress(address.address, fromName, Charsets.UTF_8.name()).toUnicodeString()
    }

    companion object {
        fun fromEnvironment(
            values: Map<String, String>,
            fileReader: (String) -> String = { Files.readString(Path.of(it)) }
        ) = ResendEmailSettings(
            apiKey = requireNotNull(readEnvironmentSecret("FASTTOWIN_RESEND_API_KEY", values, fileReader)) {
                "FASTTOWIN_RESEND_API_KEY or FASTTOWIN_RESEND_API_KEY_FILE is required for Resend."
            },
            fromEmail = values["FASTTOWIN_EMAIL_FROM_EMAIL"]?.trim().orEmpty(),
            fromName = values["FASTTOWIN_EMAIL_FROM_NAME"]?.trim().orEmpty().ifBlank { "Fast To Win" }
        )
    }
}

internal class ResendAuthEmailSender(
    private val settings: ResendEmailSettings,
    private val transport: EmailHttpTransport = JdkEmailHttpTransport
) : AuthEmailSender {
    override val isConfigured = true

    override suspend fun sendPasswordReset(recipient: String, resetToken: String, language: AppLanguage) =
        send(listOf(recipient), passwordResetEmailContent(language, resetToken))

    override suspend fun sendEmailVerification(recipient: String, verificationCode: String, language: AppLanguage) =
        send(listOf(recipient), emailVerificationContent(language, verificationCode))

    suspend fun send(recipients: List<String>, content: AuthEmailContent) = withContext(Dispatchers.IO) {
        for (group in recipients.chunked(50)) sendRequest(group, content)
    }

    private fun sendRequest(recipients: List<String>, content: AuthEmailContent) {
        val payload = buildJsonObject {
            put("from", settings.from)
            put("to", buildJsonArray { recipients.forEach { add(it) } })
            put("subject", content.subject)
            put("html", content.html)
        }
        val request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
        // A transport error can occur after acceptance. Leave resending to the caller.
        val status = try {
            transport.send(request)
        } catch (_: IOException) {
            throw IllegalStateException("Resend email delivery failed.")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Resend email delivery interrupted.")
        }
        check(status in 200..299) { "Resend email delivery rejected (HTTP $status)." }
    }
}
