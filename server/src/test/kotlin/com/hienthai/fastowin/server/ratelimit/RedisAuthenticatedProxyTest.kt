package com.hienthai.fastowin.server.ratelimit

import com.hienthai.fastowin.server.RateLimitPolicy
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.*

class RedisAuthenticatedProxyTest {
    @Test fun `authenticated direct and proxy runtimes share the selected database and reject wrong credentials`() = runBlocking {
        val url = System.getenv("FASTTOWIN_TEST_REDIS_URL")
        assumeTrue(!url.isNullOrBlank())
        val original = URI(requireNotNull(url))
        assumeTrue(original.rawUserInfo != null && original.rawPath != "/0")
        val ns = "t" + UUID.randomUUID().toString().replace("-", "")
        fun settings(target: String) = mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to target, "FASTTOWIN_RATE_LIMIT_NAMESPACE" to ns)
        val wrong = "redis://${original.rawUserInfo.substringBefore(':')}:incorrect-fixture-password@${original.host}:${original.port}${original.rawPath}"
        val failure = assertFailsWith<IllegalArgumentException> { openRateLimitRuntime(settings(wrong)) }
        assertNull(failure.cause)
        assertFalse(failure.toString().contains(wrong))
        assertFalse(failure.toString().contains(original.rawUserInfo))
        RedisFaultProxy(url).use { proxy ->
            openRateLimitRuntime(settings(url)).use { direct ->
                openRateLimitRuntime(settings(proxy.proxyUrl)).use { viaProxy ->
                    val policy = RateLimitPolicy(1, 60_000)
                    assertTrue(direct.consume("login-ip", "auth-regression", policy).allowed)
                    assertFalse(viaProxy.consume("login-ip", "auth-regression", policy).allowed)
                }
            }
        }
    }
}
