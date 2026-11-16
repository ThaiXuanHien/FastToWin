package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.AuthErrorResponse
import com.hienthai.fastowin.protocol.AuthSessionResponse
import com.hienthai.fastowin.protocol.EmailVerificationRequest
import com.hienthai.fastowin.protocol.HUMAN_VERIFICATION_CONFIG_PATH
import com.hienthai.fastowin.protocol.HUMAN_VERIFICATION_PAGE_PATH
import com.hienthai.fastowin.protocol.HumanVerificationAction
import com.hienthai.fastowin.protocol.HumanVerificationConfigResponse
import com.hienthai.fastowin.protocol.PasswordResetRequest
import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.RegisterRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HumanVerificationRoutesTest {
    private class FakeVerifier(private val outcome: TurnstileOutcome = TurnstileOutcome.PASSED) : TurnstileVerifier {
        override val siteKey = "0x4AAA-site"
        val calls = mutableListOf<Pair<String, HumanVerificationAction>>()
        override suspend fun verify(token: String, action: HumanVerificationAction, remoteIp: String?): TurnstileOutcome {
            calls += token to action
            return if (token == "good") outcome else TurnstileOutcome.REJECTED
        }
    }

    @Test
    fun `disabled turnstile keeps every flow open and says so`() = testApplication {
        application { gameModule() }
        val config = ProtocolJson.decodeFromString<HumanVerificationConfigResponse>(client.get(HUMAN_VERIFICATION_CONFIG_PATH).bodyAsText())
        assertFalse(config.enabled)
        assertEquals(HttpStatusCode.NotFound, client.get("$HUMAN_VERIFICATION_PAGE_PATH?action=register").status)
        assertEquals(HttpStatusCode.Created, client.postJson("/auth/register", RegisterRequest("open@example.com", PASSWORD, "Open")).status)
    }

    @Test
    fun `registration needs a passing token before the account is created`() = testApplication {
        val verifier = FakeVerifier()
        application { gameModule(humanVerifier = verifier) }
        val config = ProtocolJson.decodeFromString<HumanVerificationConfigResponse>(client.get(HUMAN_VERIFICATION_CONFIG_PATH).bodyAsText())
        assertEquals(HumanVerificationConfigResponse(enabled = true, siteKey = "0x4AAA-site"), config)

        val missing = client.postJson("/auth/register", RegisterRequest("bot@example.com", PASSWORD, "Bot"))
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals(TextKey.HumanVerificationRequired.name, missing.decode<AuthErrorResponse>().messageKey)
        val rejected = client.postJson("/auth/register", RegisterRequest("bot@example.com", PASSWORD, "Bot", humanVerificationToken = "bad"))
        assertEquals(HttpStatusCode.Forbidden, rejected.status)
        assertEquals("HUMAN_VERIFICATION_FAILED", rejected.decode<AuthErrorResponse>().code)

        val created = client.postJson("/auth/register", RegisterRequest("human@example.com", PASSWORD, "Human", humanVerificationToken = "good"))
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("good" to HumanVerificationAction.REGISTER, verifier.calls.last())
        // The rejected attempts did not create the account, so the address is still free.
        val retry = client.postJson("/auth/register", RegisterRequest("bot@example.com", PASSWORD, "Bot", humanVerificationToken = "good"))
        assertEquals(HttpStatusCode.Created, retry.status)
    }

    @Test
    fun `cloudflare outage answers service unavailable`() = testApplication {
        application { gameModule(humanVerifier = FakeVerifier(TurnstileOutcome.UNAVAILABLE)) }
        val response = client.postJson("/auth/register", RegisterRequest("down@example.com", PASSWORD, "Down", humanVerificationToken = "good"))
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("HUMAN_VERIFICATION_UNAVAILABLE", response.decode<AuthErrorResponse>().code)
    }

    @Test
    fun `password reset requests need a token and send no email without one`() = testApplication {
        val sender = RecordingSender()
        application { gameModule(environment = "prod", authEmailSender = sender, humanVerifier = FakeVerifier()) }
        client.postJson("/auth/register", RegisterRequest("reset@example.com", PASSWORD, "Reset", humanVerificationToken = "good"))

        assertEquals(HttpStatusCode.BadRequest, client.postJson("/auth/password-reset/request", PasswordResetRequest("reset@example.com")).status)
        assertTrue(sender.resets.isEmpty())
        val accepted = client.postJson("/auth/password-reset/request", PasswordResetRequest("reset@example.com", humanVerificationToken = "good"))
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals(1, sender.resets.size)
    }

    @Test
    fun `first verification email after sign-up skips the challenge but resends do not`() = testApplication {
        val sender = RecordingSender()
        application { gameModule(environment = "prod", authEmailSender = sender, humanVerifier = FakeVerifier()) }
        val session = client.postJson("/auth/register", RegisterRequest("verify@example.com", PASSWORD, "Verify", humanVerificationToken = "good"))
            .decode<AuthSessionResponse>()

        assertEquals(HttpStatusCode.OK, client.postJson("/auth/email-verification/request", EmailVerificationRequest(session.accessToken)).status)
        val resend = client.postJson("/auth/email-verification/request", EmailVerificationRequest(session.accessToken))
        assertEquals(HttpStatusCode.BadRequest, resend.status)
        assertEquals("HUMAN_VERIFICATION_REQUIRED", resend.decode<AuthErrorResponse>().code)
        val challenged = client.postJson("/auth/email-verification/request", EmailVerificationRequest(session.accessToken, humanVerificationToken = "good"))
        assertEquals(HttpStatusCode.OK, challenged.status)
        assertEquals(2, sender.verifications)
    }

    @Test
    fun `challenge page only accepts known actions and loads no inline script`() = testApplication {
        application { gameModule(humanVerifier = FakeVerifier()) }
        assertEquals(HttpStatusCode.NotFound, client.get("$HUMAN_VERIFICATION_PAGE_PATH?action=%3Cscript%3E").status)
        val page = client.get("$HUMAN_VERIFICATION_PAGE_PATH?action=password_reset&theme=dark&lang=vi")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue("data-sitekey=\"0x4AAA-site\"" in html)
        assertTrue("data-action=\"password_reset\"" in html)
        assertTrue("data-theme=\"dark\"" in html)
        assertFalse(Regex("<script>").containsMatchIn(html))
        val policy = requireNotNull(page.headers["Content-Security-Policy"])
        assertTrue("frame-ancestors 'none'" in policy)
        assertTrue("script-src 'self' https://challenges.cloudflare.com" in policy)
        assertEquals("no-store", page.headers[HttpHeaders.CacheControl])
        val script = client.get("$HUMAN_VERIFICATION_PAGE_PATH.js")
        assertEquals(HttpStatusCode.OK, script.status)
        assertTrue("fastToWinTurnstileLoaded" in script.bodyAsText())
    }

    private class RecordingSender : AuthEmailSender {
        override val isConfigured = true
        val resets = mutableListOf<String>()
        var verifications = 0
        override suspend fun sendPasswordReset(recipient: String, resetToken: String, language: AppLanguage) { resets += recipient }
        override suspend fun sendEmailVerification(recipient: String, verificationCode: String, language: AppLanguage) { verifications++ }
    }

    private suspend inline fun <reified T> io.ktor.client.HttpClient.postJson(path: String, body: T) = post(path) {
        contentType(ContentType.Application.Json)
        setBody(ProtocolJson.encodeToString(body))
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T = ProtocolJson.decodeFromString(bodyAsText())

    private companion object { const val PASSWORD = "strong-password-123" }
}
