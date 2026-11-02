package com.hienthai.fastowin.server.ratelimit

import com.hienthai.fastowin.server.*
import io.lettuce.core.RedisClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class RedisRateLimiterTest {
    private fun fixture(): Pair<String, String> {
        val url = System.getenv("FASTTOWIN_TEST_REDIS_URL")
        assumeTrue(!url.isNullOrBlank(), "Isolated Redis fixture required for acceptance")
        return requireNotNull(url) to "t${UUID.randomUUID().toString().replace("-", "")}"
    }
    private fun settings(url: String, namespace: String) = mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to url, "FASTTOWIN_RATE_LIMIT_NAMESPACE" to namespace)

    @Test fun `independent runtimes share capacity under concurrency`() = runBlocking {
        val (url, ns) = fixture()
        openRateLimitRuntime(settings(url, ns)).use { first ->
            openRateLimitRuntime(settings(url, ns)).use { second ->
                val policy = RateLimitPolicy(3, 86_400_000)
                val results = coroutineScope {
                    (0 until 24).map { n -> async {
                        (if (n % 2 == 0) first else second).consume("login-ip", "shared", policy).allowed
                    } }.awaitAll()
                }
                assertEquals(3, results.count { it })
            }
        }
    }

    @Test fun `keys namespaces and local buckets are independent`() = runBlocking {
        val (url, ns) = fixture()
        openRateLimitRuntime(settings(url, ns)).use { first ->
            openRateLimitRuntime(settings(url, "${ns}x")).use { second ->
                val policy = RateLimitPolicy(1, 60_000)
                assertTrue(first.consume("login-ip", "private", policy).allowed)
                assertFalse(first.consume("login-ip", "private", policy).allowed)
                assertTrue(first.consume("login-ip", "different", policy).allowed)
                assertTrue(first.consume("login-account", "private", policy).allowed)
                assertTrue(second.consume("login-ip", "private", policy).allowed)
                assertTrue(first.consume("websocket-message-player", "private", policy).allowed)
                assertTrue(second.consume("websocket-message-player", "private", policy).allowed)
                assertFalse(first.renderMetrics().contains("private"))
                assertFalse(first.renderMetrics().contains(ns))
            }
        }
    }

    @Test fun `refill retry and expiry retain continuous bucket semantics`() = runBlocking {
        val (url, ns) = fixture()
        val client = RedisClient.create(url)
        try {
            client.connect().use { conn ->
                openRateLimitRuntime(settings(url, ns)).use { runtime ->
                    val policy = RateLimitPolicy(1, 300)
                    assertTrue(runtime.consume("login-ip", "key", policy).allowed)
                    val denied = runtime.consume("login-ip", "key", policy)
                    assertFalse(denied.allowed)
                    assertTrue(denied.retryAfterMillis in 1..300)
                    val redisKey = "ftw:$ns:rl:v1:login-ip:${stableRateLimitKey("key")}"
                    assertTrue(conn.sync().pttl(redisKey) in 899_000..900_000)
                    delay(350)
                    assertTrue(runtime.consume("login-ip", "key", policy).allowed)
                }
            }
        } finally { client.shutdown() }
    }

    @Test fun `policy mismatch and corrupt state refuse without overwriting`() = runBlocking {
        val (url, ns) = fixture()
        val client = RedisClient.create(url)
        try {
            client.connect().use { conn ->
                openRateLimitRuntime(settings(url, ns)).use { runtime ->
                    val policy = RateLimitPolicy(1, 60_000)
                    assertTrue(runtime.consume("login-ip", "key", policy).allowed)
                    val mismatch = assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "key", RateLimitPolicy(2, 60_000)) }
                    assertEquals(RateLimitFailure.POLICY_MISMATCH, mismatch.reason)
                    val redisKey = "ftw:$ns:rl:v1:login-ip:${stableRateLimitKey("key")}"
                    conn.sync().hset(redisKey, "tokens", "not-a-number")
                    val corrupt = assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "key", policy) }
                    assertEquals(RateLimitFailure.INVALID_RESPONSE, corrupt.reason)
                    assertEquals("not-a-number", conn.sync().hget(redisKey, "tokens"))
                    assertFalse(runtime.consume("websocket-message-ip", "key", policy).notAllowed())
                }
            }
        } finally { client.shutdown() }
    }
    private fun RateLimitResult.notAllowed() = !allowed
    @Test fun `wrong type and partial hash are not replaced and long windows have matching ttl`() = runBlocking {
        val (url, ns) = fixture(); val client = RedisClient.create(url)
        try {
            client.connect().use { conn ->
                openRateLimitRuntime(settings(url, ns)).use { runtime ->
                    val policy = RateLimitPolicy(1, 86_400_000)
                    val prefix = "ftw:$ns:rl:v1:login-ip:"
                    val wrong = prefix + stableRateLimitKey("wrong")
                    conn.sync().set(wrong, "untouched")
                    assertEquals(RateLimitFailure.INVALID_RESPONSE, assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "wrong", policy) }.reason)
                    assertEquals("untouched", conn.sync().get(wrong))
                    val partial = prefix + stableRateLimitKey("partial")
                    conn.sync().hset(partial, "tokens", "1")
                    assertEquals(RateLimitFailure.INVALID_RESPONSE, assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "partial", policy) }.reason)
                    assertEquals(1, conn.sync().hlen(partial))
                    assertTrue(runtime.consume("login-ip", "long", policy).allowed)
                    assertTrue(conn.sync().pttl(prefix + stableRateLimitKey("long")) in 86_399_000..86_400_000)
                    assertEquals(RateLimitFailure.OTHER, assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "invalid", RateLimitPolicy(1_000_001, 1)) }.reason)
                    assertEquals(RateLimitFailure.OTHER, assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "invalid", RateLimitPolicy(1, 86_400_001)) }.reason)
                }
            }
        } finally { client.shutdown() }
    }    @Test fun `all specified entry buckets share limits across runtimes`() = runBlocking {
        val (url, ns) = fixture()
        val buckets = listOf(
            "login-ip", "login-account", "password-reset-ip", "password-reset-account",
            "password-reset-confirm-ip", "password-reset-confirm-account", "email-verification-request-ip",
            "email-verification-request-account", "email-verification-confirm-ip", "email-verification-confirm-account",
            "websocket-connect-ip", "create-room-player", "create-room-ip", "join-room-player", "join-room-ip",
            "join-room-ip-room", "admin-login-ip", "admin-login-account"
        )
        openRateLimitRuntime(settings(url, ns)).use { first ->
            openRateLimitRuntime(settings(url, ns)).use { second ->
                buckets.forEach { bucket ->
                    assertTrue(first.consume(bucket, "key", RateLimitPolicy(1, 86_400_000)).allowed, bucket)
                    assertFalse(second.consume(bucket, "key", RateLimitPolicy(1, 86_400_000)).allowed, bucket)
                }
            }
        }
    }}
