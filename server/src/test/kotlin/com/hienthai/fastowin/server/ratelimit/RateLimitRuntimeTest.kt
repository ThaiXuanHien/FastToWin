package com.hienthai.fastowin.server.ratelimit

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RateLimitRuntimeTest {
    @Test fun `unresponsive Redis fails startup promptly without exposing credentials`() {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            val finished = CountDownLatch(1)
            val worker = thread(isDaemon = true) {
                try { server.accept().use { finished.await(5, TimeUnit.SECONDS) } } catch (_: Exception) { }
            }
            val secret = "sensitive-test-password"
            val start = System.nanoTime()
            try {
                val failure = assertFailsWith<IllegalArgumentException> {
                    openRateLimitRuntime(mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to "redis://:$secret@127.0.0.1:${server.localPort}", "FASTTOWIN_RATE_LIMIT_NAMESPACE" to "startup"))
                }
                assertTrue(!failure.toString().contains(secret) && failure.cause == null)
                assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000, "startup exceeded bound")
            } finally { finished.countDown(); worker.join(1000) }
        }
    }
}
