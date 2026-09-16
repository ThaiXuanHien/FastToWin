package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import kotlin.test.*

class BrevoAuthEmailSenderTest {
    @Test
    fun `Brevo password reset sends localized JSON to the fixed HTTPS endpoint`() = runTest {
        var requests = 0
        val sender = BrevoAuthEmailSender(settings(), EmailHttpTransport { request ->
            requests++
            assertEquals("https://api.brevo.com/v3/smtp/email", request.uri().toString())
            assertEquals("POST", request.method())
            assertEquals("test-key-not-real", request.headers().firstValue("api-key").orElseThrow())
            assertEquals("application/json; charset=UTF-8", request.headers().firstValue("Content-Type").orElseThrow())
            assertEquals(10L, request.timeout().orElseThrow().seconds)
            val body = Json.parseToJsonElement(bodyText(request)).jsonObject
            assertEquals("sender@example.com", body.getValue("sender").jsonObject.getValue("email").jsonPrimitive.content)
            assertEquals("Fast To Win", body.getValue("sender").jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals("player@example.com", body.getValue("to").jsonArray.single().jsonObject.getValue("email").jsonPrimitive.content)
            assertEquals("パスワードを再設定 • Fast To Win", body.getValue("subject").jsonPrimitive.content)
            assertContains(body.getValue("htmlContent").jsonPrimitive.content, "token-with-\"quotes\"")
            assertContains(body.getValue("htmlContent").jsonPrimitive.content, "<html lang=\"ja\">")
            201
        })

        sender.sendPasswordReset("player@example.com", "token-with-\"quotes\"", AppLanguage.JAPANESE)
        assertEquals(1, requests)
    }

    @Test
    fun `Brevo verification reuses Chinese email content`() = runTest {
        val sender = BrevoAuthEmailSender(settings(), EmailHttpTransport { request ->
            val body = Json.parseToJsonElement(bodyText(request)).jsonObject
            assertEquals("验证邮箱 • Fast To Win", body.getValue("subject").jsonPrimitive.content)
            assertContains(body.getValue("htmlContent").jsonPrimitive.content, "123456")
            assertContains(body.getValue("htmlContent").jsonPrimitive.content, "<html lang=\"zh-Hans\">")
            201
        })
        sender.sendEmailVerification("player@example.com", "123456", AppLanguage.SIMPLIFIED_CHINESE)
    }

    @Test
    fun `Brevo rejected delivery fails without leaking secrets or automatically resending`() = runTest {
        for (status in listOf(400, 401, 429, 500, 302)) {
            var requests = 0
            val sender = BrevoAuthEmailSender(settings(), EmailHttpTransport {
                requests++
                status
            })
            val error = assertFailsWith<IllegalStateException> {
                sender.sendPasswordReset("player@example.com", "private-reset-token", AppLanguage.ENGLISH)
            }
            assertContains(error.message.orEmpty(), status.toString())
            assertFalse(error.message.orEmpty().contains("private-reset-token"))
            assertFalse(error.message.orEmpty().contains("test-key-not-real"))
            assertFalse(error.message.orEmpty().contains("player@example.com"))
            assertEquals(1, requests)
        }
    }

    @Test
    fun `Brevo network failure exposes only a generic delivery error`() = runTest {
        val sender = BrevoAuthEmailSender(settings(), EmailHttpTransport {
            throw IOException("private-reset-token test-key-not-real player@example.com")
        })
        val error = assertFailsWith<IllegalStateException> {
            sender.sendPasswordReset("player@example.com", "private-reset-token", AppLanguage.ENGLISH)
        }
        val exposedErrors = generateSequence<Throwable>(error) { throwable ->
            throwable.cause?.takeUnless { cause -> cause === throwable }
        }.take(8).toList()
        assertTrue(exposedErrors.none { it is IOException })
        exposedErrors.forEach { exposedError ->
            assertEquals("Brevo email delivery failed.", exposedError.message)
            assertFalse(exposedError.message.orEmpty().contains("private-reset-token"))
            assertFalse(exposedError.message.orEmpty().contains("test-key-not-real"))
            assertFalse(exposedError.message.orEmpty().contains("player@example.com"))
        }
    }

    @Test
    fun `Brevo factory selects API instead of partial SMTP configuration`() {
        val sender = configuredAuthEmailSender(brevoValues() + ("FASTTOWIN_SMTP_HOST" to "unused-host"))
        assertIs<BrevoAuthEmailSender>(sender)
        assertTrue(sender.isConfigured)
        assertSame(DisabledAuthEmailSender, configuredAuthEmailSender(emptyMap()))
        assertFailsWith<IllegalArgumentException> {
            configuredAuthEmailSender(brevoValues() + ("FASTTOWIN_EMAIL_PROVIDER" to "typo"))
        }
    }

    @Test
    fun `Brevo requires complete settings and supports mounted API secrets`() {
        assertFailsWith<IllegalArgumentException> { configuredAuthEmailSender(brevoValues() - "FASTTOWIN_BREVO_API_KEY") }
        assertFailsWith<IllegalArgumentException> { configuredAuthEmailSender(brevoValues() - "FASTTOWIN_EMAIL_FROM_EMAIL") }
        assertFailsWith<IllegalArgumentException> { configuredAuthEmailSender(brevoValues() + ("FASTTOWIN_EMAIL_FROM_EMAIL" to "not-email")) }
        val settings = BrevoEmailSettings.fromEnvironment(
            brevoValues() - "FASTTOWIN_BREVO_API_KEY" + ("FASTTOWIN_BREVO_API_KEY_FILE" to "/run/secrets/brevo"),
            fileReader = { "mounted-key\r\n" }
        )
        assertEquals("mounted-key", settings.apiKey)
        assertFalse(settings.toString().contains("mounted-key"))
    }

    private fun settings() = BrevoEmailSettings("test-key-not-real", "sender@example.com")
    private fun brevoValues() = mapOf(
        "FASTTOWIN_EMAIL_PROVIDER" to "brevo",
        "FASTTOWIN_BREVO_API_KEY" to "test-key-not-real",
        "FASTTOWIN_EMAIL_FROM_EMAIL" to "sender@example.com"
    )

    private fun bodyText(request: HttpRequest): String {
        val bytes = ByteArrayOutputStream()
        val completed = CompletableFuture<String>()
        request.bodyPublisher().orElseThrow().subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(subscription: Flow.Subscription) = subscription.request(Long.MAX_VALUE)
            override fun onNext(buffer: ByteBuffer) {
                val chunk = ByteArray(buffer.remaining())
                buffer.get(chunk)
                bytes.write(chunk)
            }
            override fun onError(error: Throwable) { completed.completeExceptionally(error) }
            override fun onComplete() { completed.complete(bytes.toString(Charsets.UTF_8)) }
        })
        return completed.get(1, TimeUnit.SECONDS)
    }
}
