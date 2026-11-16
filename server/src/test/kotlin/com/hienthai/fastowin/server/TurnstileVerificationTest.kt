package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.HumanVerificationAction
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TurnstileVerificationTest {
    private val settings = TurnstileSettings("0x4AAAAAAA-site_key", "secret-value", setOf("fasttowin.up.railway.app"))

    @Test
    fun `turnstile stays disabled unless explicitly enabled`() {
        assertNull(TurnstileSettings.fromEnvironment(emptyMap()))
        assertNull(TurnstileSettings.fromEnvironment(mapOf("FASTTOWIN_TURNSTILE_ENABLED" to "false", "FASTTOWIN_TURNSTILE_SITE_KEY" to "key")))
        assertSame(DisabledTurnstileVerifier, configuredTurnstileVerifier(emptyMap()))
        assertFailsWith<IllegalArgumentException> {
            TurnstileSettings.fromEnvironment(mapOf("FASTTOWIN_TURNSTILE_ENABLED" to "yes"))
        }
    }

    @Test
    fun `enabled turnstile requires both keys and reads the secret file`() {
        assertFailsWith<IllegalArgumentException> {
            TurnstileSettings.fromEnvironment(mapOf("FASTTOWIN_TURNSTILE_ENABLED" to "true", "FASTTOWIN_TURNSTILE_SECRET_KEY" to "s"))
        }
        assertFailsWith<IllegalArgumentException> {
            TurnstileSettings.fromEnvironment(mapOf("FASTTOWIN_TURNSTILE_ENABLED" to "true", "FASTTOWIN_TURNSTILE_SITE_KEY" to "site"))
        }
        val loaded = requireNotNull(TurnstileSettings.fromEnvironment(
            mapOf(
                "FASTTOWIN_TURNSTILE_ENABLED" to "TRUE",
                "FASTTOWIN_TURNSTILE_SITE_KEY" to "0x4AAA-site",
                "FASTTOWIN_TURNSTILE_SECRET_KEY_FILE" to "/run/secrets/turnstile",
                "FASTTOWIN_TURNSTILE_HOSTNAMES" to " FastToWin.up.railway.app , play.example.com "
            ),
            fileReader = { path -> assertEquals("/run/secrets/turnstile", path); "file-secret\n" }
        ))
        assertEquals("file-secret", loaded.secretKey)
        assertEquals(setOf("fasttowin.up.railway.app", "play.example.com"), loaded.allowedHostnames)
        assertFalse("file-secret" in loaded.toString())
    }

    @Test
    fun `a passing token must match the form action and hostname`() = runBlocking {
        val requests = mutableListOf<String>()
        fun verifier(body: String) = CloudflareTurnstileVerifier(settings, transport = { request ->
            requests += request.bodyText()
            200 to body
        })
        val passing = """{"success":true,"action":"register","hostname":"fasttowin.up.railway.app"}"""
        assertEquals(TurnstileOutcome.PASSED, verifier(passing).verify("token-1", HumanVerificationAction.REGISTER, "203.0.113.9"))
        assertTrue(requests.single().contains("secret=secret-value"))
        assertTrue(requests.single().contains("response=token-1"))
        assertTrue(requests.single().contains("remoteip=203.0.113.9"))

        assertEquals(TurnstileOutcome.REJECTED, verifier(passing).verify("token-2", HumanVerificationAction.PASSWORD_RESET, null))
        assertEquals(
            TurnstileOutcome.REJECTED,
            verifier("""{"success":true,"action":"register","hostname":"evil.example"}""")
                .verify("token-3", HumanVerificationAction.REGISTER, null)
        )
        assertEquals(
            TurnstileOutcome.REJECTED,
            verifier("""{"success":false,"error-codes":["timeout-or-duplicate"]}""")
                .verify("token-4", HumanVerificationAction.REGISTER, null)
        )
    }

    @Test
    fun `cloudflare testing keys pass without an action and are recognised`() = runBlocking {
        // Shape returned by the dummy secret 1x0000000000000000000000000000000AA.
        val verifier = CloudflareTurnstileVerifier(settings, transport = {
            200 to """{"success":true,"error-codes":[],"hostname":"example.com","metadata":{"result_with_testing_key":true}}"""
        })
        assertEquals(TurnstileOutcome.PASSED, verifier.verify("XXXX.DUMMY.TOKEN.XXXX", HumanVerificationAction.REGISTER, null))
        assertTrue(isTurnstileTestingSecret("1x0000000000000000000000000000000AA"))
        assertTrue(isTurnstileTestingSecret("2x0000000000000000000000000000000AA"))
        assertFalse(isTurnstileTestingSecret("0x4AAAAAAAreal-secret"))
    }

    @Test
    fun `oversized tokens are rejected without calling Cloudflare`() = runBlocking {
        val verifier = CloudflareTurnstileVerifier(settings, transport = { error("must not be called") })
        assertEquals(TurnstileOutcome.REJECTED, verifier.verify("x".repeat(2049), HumanVerificationAction.REGISTER, null))
        assertEquals(TurnstileOutcome.REJECTED, verifier.verify(" ", HumanVerificationAction.REGISTER, null))
    }

    @Test
    fun `outages and our own key mistakes are reported as unavailable`() = runBlocking {
        val failing = CloudflareTurnstileVerifier(settings, transport = { throw IOException("down") })
        assertEquals(TurnstileOutcome.UNAVAILABLE, failing.verify("token", HumanVerificationAction.REGISTER, null))
        val serverError = CloudflareTurnstileVerifier(settings, transport = { 502 to "" })
        assertEquals(TurnstileOutcome.UNAVAILABLE, serverError.verify("token", HumanVerificationAction.REGISTER, null))
        val badSecret = CloudflareTurnstileVerifier(settings, transport = {
            200 to """{"success":false,"error-codes":["invalid-input-secret"]}"""
        })
        assertEquals(TurnstileOutcome.UNAVAILABLE, badSecret.verify("token", HumanVerificationAction.REGISTER, null))
        val garbage = CloudflareTurnstileVerifier(settings, transport = { 200 to "<html>" })
        assertEquals(TurnstileOutcome.UNAVAILABLE, garbage.verify("token", HumanVerificationAction.REGISTER, null))
    }

    private fun HttpRequest.bodyText(): String {
        val publisher = bodyPublisher().orElseThrow()
        val bytes = java.io.ByteArrayOutputStream()
        val done = java.util.concurrent.CountDownLatch(1)
        publisher.subscribe(object : java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
            override fun onSubscribe(subscription: java.util.concurrent.Flow.Subscription) = subscription.request(Long.MAX_VALUE)
            override fun onNext(item: java.nio.ByteBuffer) { val array = ByteArray(item.remaining()); item.get(array); bytes.write(array) }
            override fun onError(throwable: Throwable) = done.countDown()
            override fun onComplete() = done.countDown()
        })
        done.await()
        return bytes.toString(Charsets.UTF_8)
    }
}
