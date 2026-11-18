package com.hienthai.fastowin.server.ratelimit

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.server.RateLimitPolicy
import com.hienthai.fastowin.server.RateLimitUnavailableException
import com.hienthai.fastowin.server.presence.PresenceConfiguration
import com.hienthai.fastowin.server.presence.RedisPresenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * SCL-01 Redis capacity benchmark (test-only). Drives the production RedisRateLimiter and
 * RedisPresenceStore classes (same 128 permits, 200 ms timeouts, single Lettuce connection)
 * with an open-loop offered rate against a LOOPBACK Redis container only, stepping the rate
 * until failures pass 1 %. Output is aggregate JSON lines: no URLs, keys or secrets.
 *
 * Env: BENCH_RATE_LIMIT_URL_FILE, BENCH_PRESENCE_URL_FILE, BENCH_NAMESPACE,
 *      BENCH_RATES (comma list, ops/s), BENCH_SECONDS (per step), BENCH_KIND (rate-limit|presence-renew|presence-read|memory)
 */
object RedisCapacityBenchmark {
    private class Histogram {
        // 0.01 ms buckets up to 50 ms, then 1 ms buckets up to 5 s.
        val fine = AtomicLongArray(5000); val coarse = AtomicLongArray(5000); val count = AtomicLong()
        fun record(nanos: Long) {
            val micros = nanos / 1000; count.incrementAndGet()
            if (micros < 50_000) fine.incrementAndGet((micros / 10).toInt()) else coarse.incrementAndGet((micros / 1000).coerceAtMost(4999).toInt())
        }
        fun percentile(p: Double): Double {
            val rank = kotlin.math.ceil(p * count.get()).toLong().coerceAtLeast(1); var seen = 0L
            for (i in 0 until fine.length()) { seen += fine.get(i); if (seen >= rank) return (i + 1) / 100.0 }
            for (i in 0 until coarse.length()) { seen += coarse.get(i); if (seen >= rank) return (i + 1).toDouble() }
            return Double.NaN
        }
    }

    private fun loopbackUrl(fileVariable: String): String {
        val url = Files.readString(Path.of(requireNotNull(System.getenv(fileVariable)) { "missing $fileVariable" })).trim()
        val uri = URI(url)
        require(uri.scheme == "redis" && uri.host == "127.0.0.1" && uri.port > 0) { "Benchmark accepts only a loopback Redis" }
        return url
    }

    @JvmStatic fun main(args: Array<String>) {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.OFF
        val namespace = requireNotNull(System.getenv("BENCH_NAMESPACE")).also { require(it.matches(Regex("[a-z0-9][a-z0-9_-]{0,47}"))) }
        val kind = System.getenv("BENCH_KIND") ?: "rate-limit"
        val rates = (System.getenv("BENCH_RATES") ?: "1000,2000,5000,10000,20000,40000,80000").split(',').map(String::toInt)
        val seconds = (System.getenv("BENCH_SECONDS") ?: "15").toInt().coerceIn(3, 120)
        val keySpace = (System.getenv("BENCH_KEYS") ?: "10000").toInt().coerceIn(1, 1_000_000)
        val rateLimiter = if (kind == "rate-limit" || kind == "memory") RedisRateLimiter(RedisConfiguration(loopbackUrl("BENCH_RATE_LIMIT_URL_FILE"), namespace)) else null
        val presence = if (kind.startsWith("presence")) RedisPresenceStore(PresenceConfiguration(loopbackUrl("BENCH_PRESENCE_URL_FILE"), namespace)) else null
        val policy = RateLimitPolicy(1_000_000, 60_000)
        val connections = (1..keySpace).map { UUID.randomUUID() }
        try {
            if (kind == "memory") {
                // Unique keys like distinct players/IPs; Redis INFO used_memory is read by the caller before/after.
                runBlocking { (0 until keySpace).chunked(100).forEach { chunk ->
                    chunk.map { i -> launch(Dispatchers.Default) { runCatching { rateLimiter!!.consume("bench-memory", "player-$i", RateLimitPolicy(20, 60_000)) } } }.joinAll()
                } }
                println("""{"kind":"memory","keysWritten":$keySpace}""")
                return
            }
            if (kind == "presence-read") runBlocking {
                // Seed online leases so reads touch live sorted sets.
                (0 until keySpace).chunked(100).forEach { chunk -> chunk.map { i -> launch(Dispatchers.Default) {
                    presence!!.renew("player-$i", connections[i], FriendPresence.ONLINE) } }.joinAll() }
            }
            for (rate in rates) {
                val histogram = Histogram(); val outcomes = ConcurrentHashMap<String, AtomicLong>()
                fun outcome(name: String) = outcomes.computeIfAbsent(name) { AtomicLong() }.incrementAndGet()
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val started = System.nanoTime(); val end = started + seconds * 1_000_000_000L
                var issued = 0L
                runBlocking {
                    while (System.nanoTime() < end) {
                        val due = ((System.nanoTime() - started) / 1e9 * rate).toLong()
                        while (issued < due) {
                            val i = (issued % keySpace).toInt(); issued++
                            scope.launch {
                                val t0 = System.nanoTime()
                                try {
                                    when (kind) {
                                        "rate-limit" -> rateLimiter!!.consume("bench-ip", "ip-$i", policy)
                                        "presence-renew" -> if (!presence!!.renew("player-$i", connections[i], FriendPresence.ONLINE)) error("write")
                                        "presence-read" -> presence!!.read((0 until 20).map { j -> "player-${(i + j * 37) % keySpace}" }) ?: error("read")
                                    }
                                    histogram.record(System.nanoTime() - t0); outcome("ok")
                                } catch (e: RateLimitUnavailableException) { outcome(e.reason.name.lowercase())
                                } catch (_: Exception) { outcome("failed") }
                            }
                        }
                        delay(1)
                    }
                    scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList().joinAll()
                }
                scope.cancel()
                val elapsed = (System.nanoTime() - started) / 1e9
                val ok = outcomes["ok"]?.get() ?: 0L
                val failed = outcomes.filterKeys { it != "ok" }.values.sumOf { it.get() }
                val failurePercent = if (issued == 0L) 0.0 else failed * 100.0 / issued
                println(buildString {
                    append("""{"kind":"$kind","offeredPerSecond":$rate,"achievedOkPerSecond":${"%.0f".format(ok / elapsed)},""")
                    append(""""p50Ms":${histogram.percentile(0.5)},"p95Ms":${histogram.percentile(0.95)},"p99Ms":${histogram.percentile(0.99)},""")
                    append(""""failurePercent":${"%.2f".format(failurePercent)},"outcomes":{""")
                    append(outcomes.entries.joinToString(",") { "\"${it.key}\":${it.value.get()}" }); append("}}")
                })
                if (failurePercent > 20) break
            }
        } finally {
            runCatching { rateLimiter?.close() }; runCatching { presence?.close() }
        }
    }
}
