package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.HumanVerificationAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * SEC-07: Cloudflare Turnstile settings. Disabled unless FASTTOWIN_TURNSTILE_ENABLED=true, so a
 * deploy without Cloudflare keys keeps today's behaviour. When enabled, both keys are required and
 * a missing key stops startup instead of silently serving unprotected sign-ups.
 */
internal data class TurnstileSettings(
    val siteKey: String,
    val secretKey: String,
    /** Optional extra check; Cloudflare already restricts the widget to the hostnames set on the site key. */
    val allowedHostnames: Set<String> = emptySet()
) {
    init {
        require(siteKey.isNotBlank() && siteKey.length <= 200 && siteKey.all(::isKeyCharacter)) {
            "FASTTOWIN_TURNSTILE_SITE_KEY must be a Cloudflare Turnstile site key."
        }
        require(secretKey.isNotBlank() && secretKey.none { it == '\r' || it == '\n' }) {
            "FASTTOWIN_TURNSTILE_SECRET_KEY must be a non-blank single-line key."
        }
    }

    override fun toString() = "TurnstileSettings(siteKey=$siteKey, allowedHostnames=$allowedHostnames)"

    companion object {
        fun fromEnvironment(
            values: Map<String, String> = System.getenv(),
            fileReader: (String) -> String = { Files.readString(Path.of(it)) }
        ): TurnstileSettings? {
            val enabled = values["FASTTOWIN_TURNSTILE_ENABLED"]?.trim().orEmpty()
            require(enabled.isEmpty() || enabled.equals("true", true) || enabled.equals("false", true)) {
                "FASTTOWIN_TURNSTILE_ENABLED must be true or false."
            }
            if (!enabled.equals("true", ignoreCase = true)) return null
            return TurnstileSettings(
                siteKey = requireNotNull(values["FASTTOWIN_TURNSTILE_SITE_KEY"]?.trim()?.takeIf(String::isNotEmpty)) {
                    "FASTTOWIN_TURNSTILE_SITE_KEY is required when FASTTOWIN_TURNSTILE_ENABLED=true."
                },
                secretKey = requireNotNull(readEnvironmentSecret("FASTTOWIN_TURNSTILE_SECRET_KEY", values, fileReader)) {
                    "FASTTOWIN_TURNSTILE_SECRET_KEY or FASTTOWIN_TURNSTILE_SECRET_KEY_FILE is required when Turnstile is enabled."
                },
                allowedHostnames = values["FASTTOWIN_TURNSTILE_HOSTNAMES"].orEmpty()
                    .split(',').map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()
            )
        }

        private fun isKeyCharacter(character: Char) = character.isLetterOrDigit() || character == '_' || character == '-'
    }
}

enum class TurnstileOutcome { PASSED, REJECTED, UNAVAILABLE }

interface TurnstileVerifier {
    /** Null when Turnstile is disabled; the public configuration then tells clients not to challenge. */
    val siteKey: String?
    suspend fun verify(token: String, action: HumanVerificationAction, remoteIp: String?): TurnstileOutcome
}

object DisabledTurnstileVerifier : TurnstileVerifier {
    override val siteKey: String? = null
    override suspend fun verify(token: String, action: HumanVerificationAction, remoteIp: String?) = TurnstileOutcome.PASSED
}

internal fun interface TurnstileHttpTransport {
    /** Returns the HTTP status and body of the siteverify call. */
    fun post(request: HttpRequest): Pair<Int, String>
}

private object JdkTurnstileHttpTransport : TurnstileHttpTransport {
    private val client by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }

    override fun post(request: HttpRequest): Pair<Int, String> =
        client.send(request, HttpResponse.BodyHandlers.ofString()).let { it.statusCode() to it.body() }
}

internal class CloudflareTurnstileVerifier(
    private val settings: TurnstileSettings,
    private val transport: TurnstileHttpTransport = JdkTurnstileHttpTransport,
    private val endpoint: URI = URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify")
) : TurnstileVerifier {
    override val siteKey: String = settings.siteKey

    override suspend fun verify(token: String, action: HumanVerificationAction, remoteIp: String?): TurnstileOutcome {
        // Cloudflare tokens are at most 2048 characters; anything else is rejected without a network call.
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) return TurnstileOutcome.REJECTED
        val form = buildList {
            add("secret" to settings.secretKey)
            add("response" to token)
            remoteIp?.takeIf(String::isNotBlank)?.let { add("remoteip" to it) }
        }.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, Charsets.UTF_8)}" }
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val (status, body) = try {
            withContext(Dispatchers.IO) { transport.post(request) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            System.err.println("Turnstile verification unavailable: ${error.javaClass.simpleName}")
            return TurnstileOutcome.UNAVAILABLE
        }
        if (status !in 200..299) {
            System.err.println("Turnstile verification unavailable: HTTP $status")
            return TurnstileOutcome.UNAVAILABLE
        }
        val result = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrElse {
            System.err.println("Turnstile verification returned an unreadable response")
            return TurnstileOutcome.UNAVAILABLE
        }
        return evaluate(result, action)
    }

    private fun evaluate(result: JsonObject, action: HumanVerificationAction): TurnstileOutcome {
        val success = runCatching { result["success"]?.jsonPrimitive?.boolean }.getOrNull() == true
        if (!success) {
            val errors = runCatching { result["error-codes"].toString() }.getOrDefault("")
            // Configuration mistakes on our side must page an operator, not look like a bot.
            if ("invalid-input-secret" in errors || "missing-input-secret" in errors || "internal-error" in errors) {
                System.err.println("Turnstile verification unavailable: $errors")
                return TurnstileOutcome.UNAVAILABLE
            }
            return TurnstileOutcome.REJECTED
        }
        // Cloudflare's published testing secrets pass every token and report no action or real
        // hostname, so there is nothing further to check (startup warns when one is configured).
        val testingKeyResult = runCatching {
            result["metadata"]?.jsonObject?.get("result_with_testing_key")?.jsonPrimitive?.boolean
        }.getOrNull() == true
        if (testingKeyResult) return TurnstileOutcome.PASSED
        // A token solved for one form must not unlock another (e.g. a reset token reused for sign-up).
        val solvedAction = result["action"]?.jsonPrimitive?.contentOrNull
        if (solvedAction != action.wireName) return TurnstileOutcome.REJECTED
        if (settings.allowedHostnames.isNotEmpty()) {
            val hostname = result["hostname"]?.jsonPrimitive?.contentOrNull?.lowercase()
            if (hostname !in settings.allowedHostnames) return TurnstileOutcome.REJECTED
        }
        return TurnstileOutcome.PASSED
    }

    private companion object {
        const val MAX_TOKEN_LENGTH = 2048
    }
}

/** Cloudflare's documented dummy secrets (always pass / always fail / token already spent). */
internal fun isTurnstileTestingSecret(secret: String): Boolean = Regex("^[123]x0{31}AA$").matches(secret)

internal fun configuredTurnstileVerifier(values: Map<String, String> = System.getenv()): TurnstileVerifier {
    val settings = TurnstileSettings.fromEnvironment(values) ?: return DisabledTurnstileVerifier
    if (isTurnstileTestingSecret(settings.secretKey)) {
        System.err.println("WARNING: Turnstile uses a Cloudflare testing secret; sign-ups are NOT protected.")
    }
    return CloudflareTurnstileVerifier(settings)
}
