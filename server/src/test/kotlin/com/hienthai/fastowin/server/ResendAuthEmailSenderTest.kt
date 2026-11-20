package com.hienthai.fastowin.server

import com.hienthai.fastowin.localization.AppLanguage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResendAuthEmailSenderTest {
    @Test
    fun `localized auth messages use the Resend HTTPS contract`() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val sender = ResendAuthEmailSender(settings, EmailHttpTransport { request ->
            requests += request
            200
        })

        sender.sendEmailVerification("player@example.com", "123456", AppLanguage.VIETNAMESE)
        sender.sendPasswordReset("other@example.com", "private-reset-code", AppLanguage.ENGLISH)

        assertEquals(2, requests.size)
        val idempotencyKeys = requests.map { it.headers().firstValue("Idempotency-Key").orElseThrow() }
        assertEquals(2, idempotencyKeys.toSet().size)
        val bodies = requests.map { request ->
            assertEquals("https://api.resend.com/emails", request.uri().toString())
            assertEquals("POST", request.method())
            assertEquals("Bearer re_test_credential", request.headers().firstValue("Authorization").orElseThrow())
            assertEquals("application/json; charset=UTF-8", request.headers().firstValue("Content-Type").orElseThrow())
            val body = Json.parseToJsonElement(bodyText(request)).jsonObject
            assertEquals("Fast To Win <sender@example.com>", body.getValue("from").jsonPrimitive.content)
            body
        }
        val (verification, reset) = bodies
        assertEquals("player@example.com", verification.getValue("to").jsonArray.single().jsonPrimitive.content)
        assertEquals("Xác minh email • Fast To Win", verification.getValue("subject").jsonPrimitive.content)
        assertContains(verification.getValue("html").jsonPrimitive.content, "123456")
        assertEquals("other@example.com", reset.getValue("to").jsonArray.single().jsonPrimitive.content)
        assertEquals("Reset password • Fast To Win", reset.getValue("subject").jsonPrimitive.content)
        assertContains(reset.getValue("html").jsonPrimitive.content, "private-reset-code")
    }

    @Test
    fun `Resend sends all configured alert recipients within the provider limit`() = runTest {
        val deliveries = mutableListOf<List<String>>()
        val sender = ResendAuthEmailSender(settings, EmailHttpTransport { request ->
            val body = Json.parseToJsonElement(bodyText(request)).jsonObject
            val recipients = body.getValue("to").jsonArray.map { it.jsonPrimitive.content }
            deliveries += recipients
            if (recipients.size <= 50) 200 else 422
        })
        val recipients = (1..51).map { "admin$it@example.com" }

        sender.send(recipients, AuthEmailContent("Admin alert", "<p>Admin alert</p>"))

        assertEquals(listOf(50, 1), deliveries.map { it.size })
        assertEquals(recipients, deliveries.flatten())
    }

    @Test
    fun `Resend rejects a quota response after one attempt`() = runTest {
        var sends = 0
        val sender = ResendAuthEmailSender(settings, EmailHttpTransport {
            sends++
            429
        })

        val error = assertFailsWith<IllegalStateException> {
            sender.sendEmailVerification("player@example.com", "123456", AppLanguage.ENGLISH)
        }

        assertEquals("Resend email delivery rejected (HTTP 429).", error.message)
        assertEquals(1, sends)
    }

    @Test
    fun `Resend transport errors discard potentially sensitive details`() = runTest {
        val sender = ResendAuthEmailSender(settings, EmailHttpTransport {
            throw IOException("player@example.com private-reset-code re_test_credential")
        })

        val error = assertFailsWith<IllegalStateException> {
            sender.sendPasswordReset("player@example.com", "private-reset-code", AppLanguage.ENGLISH)
        }

        generateSequence<Throwable>(error) { it.cause }.forEach { failure ->
            assertEquals("Resend email delivery failed.", failure.message)
        }
    }

    @Test
    fun `Resend rejects a sender address with an embedded display name`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ResendEmailSettings("re_test_credential", "Existing Name <sender@example.com>")
        }

        assertContains(error.message.orEmpty(), "FASTTOWIN_EMAIL_FROM_EMAIL")
    }

    private val settings = ResendEmailSettings("re_test_credential", "sender@example.com")
}
