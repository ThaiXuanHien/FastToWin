package com.hienthai.fastowin.server.ratelimit

import com.hienthai.fastowin.server.*
import io.lettuce.core.RedisClient
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*

class RedisFaultTest {
    private fun fixture(): String {
        val url = System.getenv("FASTTOWIN_TEST_REDIS_URL")
        assumeTrue(!url.isNullOrBlank(), "Isolated Redis fixture required for acceptance")
        return requireNotNull(url)
    }
    private fun settings(url: String, ns: String) = mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to url, "FASTTOWIN_RATE_LIMIT_NAMESPACE" to ns)
    private fun namespace() = "f${UUID.randomUUID().toString().replace("-", "")}"

    @Test fun `lost response consumes once and does not replay`() = runBlocking {
        val url = fixture(); val ns = namespace(); val policy = RateLimitPolicy(2, 86_400_000)
        RedisFaultProxy(url).use { proxy ->
            openRateLimitRuntime(settings(proxy.proxyUrl, ns)).use { runtime ->
                proxy.mode = RedisFaultProxy.Mode.DROP
                assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "sensitive", policy) }
                assertTrue(proxy.observed.await(1, TimeUnit.SECONDS))
                proxy.release()
                delay(1200)
                openRateLimitRuntime(settings(url, ns)).use { direct ->
                    assertTrue(direct.consume("login-ip", "sensitive", policy).allowed)
                    assertFalse(direct.consume("login-ip", "sensitive", policy).allowed)
                }
                val metrics = runtime.renderMetrics()
                assertTrue(metrics.contains("outcome=\"unavailable\"} 1"))
                assertFalse(metrics.contains("sensitive")); assertFalse(metrics.contains(ns)); assertFalse(metrics.contains(url))
            }
        }
    }

    @Test fun `malformed response is unavailable after real write`() = runBlocking {
        val url = fixture(); val ns = namespace()
        RedisFaultProxy(url).use { proxy ->
            openRateLimitRuntime(settings(proxy.proxyUrl, ns)).use { runtime ->
                proxy.mode = RedisFaultProxy.Mode.MALFORMED
                val failure = assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "key", RateLimitPolicy(1, 60_000)) }
                assertEquals(RateLimitFailure.INVALID_RESPONSE, failure.reason)
                openRateLimitRuntime(settings(url, ns)).use { direct -> assertFalse(direct.consume("login-ip", "key", RateLimitPolicy(1, 60_000)).allowed) }
            }
        }
    }

    @Test fun `held responses bound in flight and cancellation propagates`() = runBlocking {
        val url = fixture(); val ns = namespace()
        RedisFaultProxy(url).use { proxy ->
            openRateLimitRuntime(settings(proxy.proxyUrl, ns)).use { runtime ->
                proxy.mode = RedisFaultProxy.Mode.HOLD
                val policy = RateLimitPolicy(1, 86_400_000)
                val cancelled = async { runtime.consume("login-ip", "cancel", policy) }
                assertTrue(withContext(Dispatchers.IO) { proxy.observed.await(2, TimeUnit.SECONDS) })
                cancelled.cancelAndJoin()
                assertTrue(cancelled.isCancelled)
                val results = (0 until 200).map { n -> async(start = CoroutineStart.UNDISPATCHED) {
                    try { runtime.consume("login-ip", "key$n", policy); null } catch (e: RateLimitUnavailableException) { e.reason }
                } }.awaitAll()
                assertTrue(results.contains(RateLimitFailure.OVERLOADED))
                assertTrue(results.contains(RateLimitFailure.TIMEOUT))
                val secondWave = assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "second-wave", policy) }
                assertEquals(RateLimitFailure.OVERLOADED, secondWave.reason)
                assertFalse(secondWave.toString().contains("Internal stack size"))
                assertNull(secondWave.cause)
                assertFalse(runtime.renderMetrics().contains("Internal stack size"))
                assertTrue(runtime.consume("websocket-message-player", "game", policy).allowed)
                proxy.release()
                openRateLimitRuntime(settings(url, ns)).use { direct -> assertFalse(direct.consume("login-ip", "cancel", policy).allowed) }
            }
        }
    }
    @Test fun `disconnect refuses shared traffic while local continues and reconnect recovers`() = runBlocking {
        val url = fixture(); val ns = namespace(); val policy = RateLimitPolicy(2, 86_400_000)
        RedisFaultProxy(url).use { proxy ->
            openRateLimitRuntime(settings(proxy.proxyUrl, ns)).use { runtime ->
                assertTrue(runtime.consume("login-ip", "before", policy).allowed)
                proxy.mode = RedisFaultProxy.Mode.HOLD
                proxy.disconnect()
                delay(100)
                val failure = assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "during", policy) }
                assertEquals(RateLimitFailure.DISCONNECTED, failure.reason)
                assertTrue(runtime.consume("websocket-message-player", "game", policy).allowed)
                proxy.release()
                var recovered = false
                repeat(40) {
                    if (!recovered) {
                        delay(100)
                        try { recovered = runtime.consume("login-ip", "after", policy).allowed } catch (_: RateLimitUnavailableException) {}
                    }
                }
                assertTrue(recovered, "connection did not recover")
            }
        }
    }

    @Test fun `closing runtime ends its connection and later shared requests fail safely`() = runBlocking {
        val url = fixture(); val ns = namespace(); val runtime = openRateLimitRuntime(settings(url, ns))
        assertTrue(runtime.consume("login-ip", "first", RateLimitPolicy(1, 60_000)).allowed)
        runtime.close()
        val failure = assertFailsWith<RateLimitUnavailableException> { runtime.consume("login-ip", "second", RateLimitPolicy(1, 60_000)) }
        assertTrue(failure.cause == null)
        assertTrue(runtime.consume("websocket-message-player", "game", RateLimitPolicy(1, 60_000)).allowed)
    }
}
