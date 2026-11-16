package com.hienthai.fastowin.server

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RateLimiterTest {
    @Test
    fun `token bucket limits bursts and refills continuously`() = runTest {
        var now = 1_000L
        val limiter = InMemoryRateLimiter(nowMillis = { now })
        val policy = RateLimitPolicy(capacity = 2, refillWindowMillis = 1_000L)

        assertTrue(limiter.consume("login", "player", policy).allowed)
        assertTrue(limiter.consume("login", "player", policy).allowed)
        val limited = limiter.consume("login", "player", policy)
        assertFalse(limited.allowed)
        assertEquals(500L, limited.retryAfterMillis)

        now += 499L
        assertFalse(limiter.consume("login", "player", policy).allowed)
        now += 1L
        assertTrue(limiter.consume("login", "player", policy).allowed)
    }

    @Test
    fun `buckets are isolated and bounded`() = runTest {
        var now = 1_000L
        val policy = RateLimitPolicy(capacity = 1, refillWindowMillis = 1_000L)
        val limiter = InMemoryRateLimiter(nowMillis = { now }, maxKeysPerBucket = 1)

        assertTrue(limiter.consume("login", "first", policy).allowed)
        assertTrue(limiter.consume("join", "first", policy).allowed)
        assertFalse(limiter.consume("login", "second", policy).allowed)

        now += 15 * 60_000L
        assertTrue(limiter.consume("login", "second", policy).allowed)
    }

    @Test
    fun `registration allowance is strict in production and relaxed for development servers`() {
        assertEquals(10, serverRateLimitPolicies("prod", emptyMap()).registerPerIp.capacity)
        assertEquals(10_000, serverRateLimitPolicies("dev", emptyMap()).registerPerIp.capacity)
        assertEquals(25, serverRateLimitPolicies("prod", mapOf("FASTTOWIN_REGISTER_LIMIT_PER_IP" to "25")).registerPerIp.capacity)
        assertEquals(3, serverRateLimitPolicies("dev", emptyMap()).registerPerAccount.capacity)
        assertFailsWith<IllegalArgumentException> { serverRateLimitPolicies("prod", mapOf("FASTTOWIN_REGISTER_LIMIT_PER_IP" to "0")) }
    }

    @Test
    fun `flooding one bucket with new keys does not block other buckets`() = runTest {
        val policy = RateLimitPolicy(capacity = 5, refillWindowMillis = 60_000L)
        val limiter = InMemoryRateLimiter(nowMillis = { 1_000L }, maxKeysPerBucket = 3)

        repeat(3) { assertTrue(limiter.consume("login-ip", "attacker-$it", policy).allowed) }
        assertFalse(limiter.consume("login-ip", "attacker-new", policy).allowed)

        assertTrue(limiter.consume("websocket-message-player", "player", policy).allowed)
        assertTrue(limiter.consume("register-ip", "player", policy).allowed)
        assertTrue(limiter.consume("login-ip", "attacker-0", policy).allowed)
    }

    @Test
    fun `stable keys do not retain raw identifiers`() {
        val email = "player@example.com"
        val first = stableRateLimitKey(email)

        assertEquals(64, first.length)
        assertNotEquals(email, first)
        assertEquals(first, stableRateLimitKey(email))
        assertNotEquals(first, stableRateLimitKey("other@example.com"))
    }
}
