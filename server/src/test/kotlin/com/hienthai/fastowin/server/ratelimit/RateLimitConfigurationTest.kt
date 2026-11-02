package com.hienthai.fastowin.server.ratelimit

import com.hienthai.fastowin.server.RateLimitPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimitConfigurationTest {
    @Test fun `memory default does not read Redis credential`() = runTest {
        openRateLimitRuntime(emptyMap()) { error("must not read a credential") }.use { runtime ->
            val policy = RateLimitPolicy(1, 60_000)
            assertTrue(runtime.consume("login-ip", "local", policy).allowed)
            assertFalse(runtime.consume("login-ip", "local", policy).allowed)
        }
    }

    @Test fun `explicit empty or unknown store is rejected`() {
        for (store in listOf("", "REDIS", "other")) {
            assertFailsWith<IllegalArgumentException> { openRateLimitRuntime(mapOf("FASTTOWIN_RATE_LIMIT_STORE" to store)) }
        }
    }

    @Test fun `redis requires URL and valid namespace without leaking secret errors`() {
        val secret = "password-private-test"
        val base = mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_NAMESPACE" to "test")
        val failures = listOf(
            assertFailsWith<IllegalArgumentException> { openRateLimitRuntime(base) },
            assertFailsWith<IllegalArgumentException> { openRateLimitRuntime(base + ("FASTTOWIN_RATE_LIMIT_REDIS_URL" to secret)) },
            assertFailsWith<IllegalArgumentException> { openRateLimitRuntime(base + mapOf("FASTTOWIN_RATE_LIMIT_REDIS_URL_FILE" to secret)) { error(secret) } },
            assertFailsWith<IllegalArgumentException> { openRateLimitRuntime(base + mapOf("FASTTOWIN_RATE_LIMIT_REDIS_URL" to "redis://localhost:6379", "FASTTOWIN_RATE_LIMIT_NAMESPACE" to "BAD")) }
        )
        failures.forEach { failure ->
            assertFalse(failure.toString().contains(secret))
            assertTrue(failure.cause == null)
        }
    }
    @Test fun `invalid Redis URI and namespace are rejected before connecting`() {
        val base = mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_NAMESPACE" to "test")
        for (url in listOf("http://localhost", "redis://localhost:0", "redis://localhost:99999", "redis://localhost?verifyPeer=false", "redis://localhost/#fragment")) {
            assertFailsWith<IllegalArgumentException>(url) { rateLimitConfiguration(base + ("FASTTOWIN_RATE_LIMIT_REDIS_URL" to url)) { error("unexpected") } }
        }
        for (namespace in listOf("", "_test", "TEST", "x".repeat(49))) {
            assertFailsWith<IllegalArgumentException>(namespace) { rateLimitConfiguration(base + mapOf("FASTTOWIN_RATE_LIMIT_REDIS_URL" to "redis://localhost", "FASTTOWIN_RATE_LIMIT_NAMESPACE" to namespace)) { error("unexpected") } }
        }
    }

    @Test fun `file credential is read and configuration is redacted`() {
        val secret = "redis://:file-secret@127.0.0.1:6379"
        val config = rateLimitConfiguration(mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL_FILE" to "ignored-path", "FASTTOWIN_RATE_LIMIT_NAMESPACE" to "test")) { "$secret\n" }
        assertTrue(config != null)
        assertFalse(config.toString().contains("file-secret"))
        assertFailsWith<IllegalArgumentException> {
            rateLimitConfiguration(mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to secret, "FASTTOWIN_RATE_LIMIT_REDIS_URL_FILE" to "other", "FASTTOWIN_RATE_LIMIT_NAMESPACE" to "test")) { secret }
        }
    }}
