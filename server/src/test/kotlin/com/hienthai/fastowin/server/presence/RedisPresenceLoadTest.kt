package com.hienthai.fastowin.server.presence

import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.server.RateLimitPolicy
import com.hienthai.fastowin.server.ratelimit.openRateLimitRuntime
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Duration
import java.util.UUID
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Opt-in acceptance for a disposable Redis instance with separate presence, rate and INFO credentials. */
class RedisPresenceLoadTest {
    @Test
    fun `presence and rate limit coexist under steady renewal and batch reads`() = runBlocking {
        assumeTrue(System.getenv("FASTTOWIN_TEST_PRESENCE_LOAD") == "true", "Opt-in Redis load fixture required")
        val presenceUrl = checkNotNull(System.getenv("FASTTOWIN_TEST_PRESENCE_REDIS_URL"))
        val rateUrl = checkNotNull(System.getenv("FASTTOWIN_TEST_REDIS_URL"))
        val statsUrl = checkNotNull(System.getenv("FASTTOWIN_TEST_PRESENCE_STATS_URL"))
        val namespace = "t" + UUID.randomUUID().toString().replace("-", "")
        val config = PresenceConfiguration(presenceUrl, namespace)
        val rateConfig = mapOf(
            "FASTTOWIN_RATE_LIMIT_STORE" to "redis",
            "FASTTOWIN_RATE_LIMIT_REDIS_URL" to rateUrl,
            "FASTTOWIN_RATE_LIMIT_NAMESPACE" to namespace
        )
        RedisPresenceStore(config).use { first ->
            RedisPresenceStore(config).use { second ->
                openRateLimitRuntime(rateConfig).use { quotaA ->
                    openRateLimitRuntime(rateConfig).use { quotaB ->
                        val statsUri = object : RedisURI(RedisURI.create(statsUrl)) {
                            override fun toString() = "[redacted fixture INFO URI]"
                        }.apply { timeout = Duration.ofSeconds(2) }
                        val stats = RedisClient.create(statsUri)
                        try {
                            stats.connect().use { connection ->
                                val commands = connection.sync()
                                fun snapshot() = Stats.from(commands.info())
                                val stores = listOf(first, second)
                                val quotas = listOf(quotaA, quotaB)
                                val policy = RateLimitPolicy(1_000, 60_000)
                                for (players in listOf(0, 100, 500)) {
                                    val sockets = List(players) { UUID.randomUUID().toString() to UUID.randomUUID() }
                                    val before = snapshot()
                                    assertEquals(64 * 1024 * 1024L, before.maxMemory)
                                    assertEquals(1L, before.aofEnabled)
                                    val start = TimeSource.Monotonic.markNow()
                                    val readMillis = mutableListOf<Double>()
                                    var peakMemory = before.memory
                                    var peakAof = before.aofSize
                                    var writes = 0
                                    var protected = 0
                                    try {
                                        repeat(65) { tick ->
                                            delay((tick * 1_000L - start.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
                                            repeat(10) { request ->
                                                val runtime = quotas[request % quotas.size]
                                                assertTrue(runtime.consume("login-ip", "load-$players-$request", policy).allowed)
                                                protected++
                                            }
                                            sockets.forEachIndexed { index, (id, socket) ->
                                                if (index % 20 == tick % 20) {
                                                    assertTrue(stores[index % stores.size].renew(id, socket, FriendPresence.ONLINE))
                                                    writes++
                                                }
                                            }
                                            if (sockets.isNotEmpty() && tick >= 20 && tick % 15 == 0) {
                                                for (backend in stores.indices) {
                                                    val roster = List(50) { sockets[(tick + backend + it) % players].first }
                                                    val readStart = TimeSource.Monotonic.markNow()
                                                    val state = stores[backend].read(roster)
                                                    readMillis += readStart.elapsedNow().inWholeMicroseconds / 1_000.0
                                                    assertEquals(roster.associateWith { FriendPresence.ONLINE }, state)
                                                }
                                            }
                                            val current = snapshot()
                                            peakMemory = maxOf(peakMemory, current.memory)
                                            peakAof = maxOf(peakAof, current.aofSize)
                                        }
                                        val after = snapshot()
                                        assertEquals(before.evictions, after.evictions)
                                        assertEquals(before.errors, after.errors)
                                        assertTrue(peakMemory < before.maxMemory)
                                        val sorted = readMillis.sorted()
                                        val p95 = sorted.getOrNull(ceil(sorted.size * 0.95).toInt() - 1)
                                        println("PRESENCE_LOAD players=$players durationMs=${start.elapsedNow().inWholeMilliseconds} " +
                                            "protectedRequests=$protected renewals=$writes readBatches=${sorted.size} p95Ms=${p95 ?: "n/a"} " +
                                            "peakUsedBytes=$peakMemory aofStartBytes=${before.aofSize} aofPeakBytes=$peakAof " +
                                            "aofEndBytes=${after.aofSize} evictions=0 errors=0")
                                    } finally {
                                        sockets.forEachIndexed { index, (id, socket) -> stores[index % stores.size].revoke(id, socket) }
                                    }
                                }
                            }
                        } finally {
                            stats.shutdown(Duration.ZERO, Duration.ofMillis(200))
                        }
                    }
                }
            }
        }
    }

    private data class Stats(
        val memory: Long, val maxMemory: Long, val aofEnabled: Long, val aofSize: Long, val evictions: Long, val errors: Long
    ) {
        companion object {
            fun from(info: String): Stats {
                val fields = info.lineSequence().mapNotNull { line ->
                    val parts = line.split(':', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1].trim() else null
                }.toMap()
                fun field(name: String) = fields.getValue(name).toLong()
                return Stats(field("used_memory"), field("maxmemory"), field("aof_enabled"), field("aof_current_size"),
                    field("evicted_keys"), field("total_error_replies"))
            }
        }
    }
}
