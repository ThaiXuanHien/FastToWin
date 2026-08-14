package com.hienthai.fastowin.server

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val limiter = InMemoryRateLimiter(nowMillis = { now }, maxBuckets = 2)

        assertTrue(limiter.consume("login", "first", policy).allowed)
        assertTrue(limiter.consume("join", "first", policy).allowed)
        assertFalse(limiter.consume("login", "second", policy).allowed)

        now += 15 * 60_000L
        assertTrue(limiter.consume("login", "second", policy).allowed)
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
