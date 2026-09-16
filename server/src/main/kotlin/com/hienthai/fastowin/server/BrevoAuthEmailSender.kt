package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal class BrevoEmailSettings(
    val apiKey: String,
    val fromEmail: String,
    val fromName: String = "Fast To Win"
) {
    init {
        require(apiKey.isNotBlank() && apiKey.none { it == '\r' || it == '\n' }) {
            "FASTTOWIN_BREVO_API_KEY must be a non-blank single-line key."
        }
        require(fromEmail.isNotBlank() && runCatching { InternetAddress(fromEmail, true).validate() }.isSuccess) {
            "FASTTOWIN_EMAIL_FROM_EMAIL must be a valid email address."
        }
        require(fromName.isNotBlank()) { "FASTTOWIN_EMAIL_FROM_NAME must not be blank." }
    }

    companion object {
        fun fromEnvironment(
            values: Map<String, String> = System.getenv(),
            fileReader: (String) -> String = { Files.readString(Path.of(it)) }
        ) = BrevoEmailSettings(
            apiKey = requireNotNull(readEnvironmentSecret("FASTTOWIN_BREVO_API_KEY", values, fileReader)) {
                "FASTTOWIN_BREVO_API_KEY or FASTTOWIN_BREVO_API_KEY_FILE is required for Brevo."
            },
            fromEmail = values["FASTTOWIN_EMAIL_FROM_EMAIL"]?.trim().orEmpty(),
            fromName = values["FASTTOWIN_EMAIL_FROM_NAME"]?.trim().orEmpty().ifBlank { "Fast To Win" }
        )
    }
}

internal fun interface EmailHttpTransport {
    fun send(request: HttpRequest): Int
}

private object JdkEmailHttpTransport : EmailHttpTransport {
    private val client by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }

    override fun send(request: HttpRequest): Int =
        client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
}

internal class BrevoAuthEmailSender(
    private val settings: BrevoEmailSettings,
    private val transport: EmailHttpTransport = JdkEmailHttpTransport
) : AuthEmailSender {
    override val isConfigured = true

    override suspend fun sendPasswordReset(recipient: String, resetToken: String, language: AppLanguage) =
        send(recipient, passwordResetEmailContent(language, resetToken))

    override suspend fun sendEmailVerification(recipient: String, verificationCode: String, language: AppLanguage) =
        send(recipient, emailVerificationContent(language, verificationCode))

    private suspend fun send(recipient: String, content: AuthEmailContent) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("sender", buildJsonObject {
                put("email", settings.fromEmail)
                put("name", settings.fromName)
            })
            put("to", buildJsonArray { add(buildJsonObject { put("email", recipient) }) })
            put("subject", content.subject)
            put("htmlContent", content.html)
        }
        val request = HttpRequest.newBuilder(URI("https://api.brevo.com/v3/smtp/email"))
            .timeout(Duration.ofSeconds(10))
            .header("api-key", settings.apiKey)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), Charsets.UTF_8))
            .build()
        // Do not log provider response bodies, recipient addresses, keys or reset codes.
        // No automatic retry: a timeout may occur after the provider already accepted an email.
        val status = try {
            transport.send(request)
        } catch (_: IOException) {
            throw IllegalStateException("Brevo email delivery failed.")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Brevo email delivery interrupted.")
        }
        check(status == 201) { "Brevo email delivery rejected (HTTP $status)." }
    }
}
