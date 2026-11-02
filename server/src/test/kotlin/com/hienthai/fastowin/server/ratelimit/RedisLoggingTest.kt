package com.hienthai.fastowin.server.ratelimit

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hienthai.fastowin.server.RateLimitPolicy
import io.lettuce.core.AclSetuserArgs
import io.lettuce.core.RedisClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import kotlin.test.*

class RedisLoggingTest {
    @Test fun `debug logging does not expose Redis credentials or URI during success and failure`() = runBlocking {
        val url = System.getenv("FASTTOWIN_TEST_REDIS_URL")
        assumeTrue(!url.isNullOrBlank(), "Isolated Redis fixture required")
        val username = "u${UUID.randomUUID().toString().replace("-", "")}"; val password = "p${UUID.randomUUID().toString().replace("-", "")}"
        val admin = RedisClient.create(requireNotNull(url))
        try {
            admin.connect().use { conn ->
                conn.sync().aclSetuser(username, AclSetuserArgs().on().allKeys().allCommands().addPassword(password))
                try {
                    RedisFaultProxy(url).use { proxy ->
                        val address = URI(proxy.proxyUrl)
                        val credentialUrl = "redis://$username:$password@${address.host}:${address.port}/0"
                        val logger = LoggerFactory.getLogger("io.lettuce") as Logger
                        val previousLevel = logger.level
                        val capture = ListAppender<ILoggingEvent>().apply { start() }
                        logger.addAppender(capture); logger.level = Level.DEBUG
                        val unrelated = LoggerFactory.getLogger("com.hienthai.fastowin.log-test") as Logger
                        val unrelatedLevel = unrelated.level
                        unrelated.addAppender(capture); unrelated.level = Level.DEBUG
                        try {
                            val values = mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to credentialUrl, "FASTTOWIN_RATE_LIMIT_NAMESPACE" to "l${UUID.randomUUID().toString().replace("-", "")}")
                            openRateLimitRuntime(values).use { assertTrue(it.consume("login-ip", "x", RateLimitPolicy(1, 60_000)).allowed) }
                            val badUrl = "redis://$username:wrong-$password@${address.host}:${address.port}/0"
                            val error = assertFailsWith<IllegalArgumentException> { openRateLimitRuntime(values + ("FASTTOWIN_RATE_LIMIT_REDIS_URL" to badUrl)) }
                            assertNull(error.cause)
                            assertFalse(error.toString().contains(username))
                            assertFalse(error.toString().contains(password))
                            unrelated.debug("unrelated-debug-marker")
                            assertTrue(capture.list.any { it.formattedMessage == "unrelated-debug-marker" })
                            assertEquals(Level.DEBUG, logger.level)
                            val messages = capture.list.joinToString("\n") { it.formattedMessage }
                            assertFalse(messages.contains(username), "username appeared in Lettuce DEBUG logs")
                            assertFalse(messages.contains(password), "password appeared in Lettuce DEBUG logs")
                            assertFalse(messages.contains(credentialUrl), "URI appeared in Lettuce DEBUG logs")
                            assertFalse(messages.contains(":${address.port}"), "endpoint appeared in Lettuce DEBUG logs")
                        } finally {
                            logger.detachAppender(capture); logger.level = previousLevel
                            unrelated.detachAppender(capture); unrelated.level = unrelatedLevel; capture.stop()
                        }
                    }
                } finally { conn.sync().aclDeluser(username) }
            }
        } finally { admin.shutdown() }
    }
}
