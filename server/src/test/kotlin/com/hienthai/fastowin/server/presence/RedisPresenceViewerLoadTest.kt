package com.hienthai.fastowin.server.presence

import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.server.RateLimitFailure
import com.hienthai.fastowin.server.RateLimitPolicy
import com.hienthai.fastowin.server.RateLimitUnavailableException
import com.hienthai.fastowin.server.ratelimit.RateLimitRuntime
import com.hienthai.fastowin.server.ratelimit.openRateLimitRuntime
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.time.Duration
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue

/** Disposable local fixture only. Output is a whitelist of aggregate metrics, never keys or URIs. */
class RedisPresenceViewerLoadTest {
    @Test
    fun `fixed rate viewers coexist with independent quota clients`() = runBlocking(Dispatchers.Default) {
        assumeTrue(System.getenv("FASTTOWIN_TEST_PRESENCE_VIEWER_LOAD") == "true", "Opt-in viewer load fixture required")
        val phaseSeconds = seconds("FASTTOWIN_TEST_PRESENCE_VIEWER_PHASE_SECONDS", 300, 1)
        val soakSeconds = seconds("FASTTOWIN_TEST_PRESENCE_VIEWER_SOAK_SECONDS", 1_800, 0)
        val qualification = phaseSeconds >= 300 && soakSeconds >= 1_800
        val namespace = "t" + UUID.randomUUID().toString().replace("-", "")
        val rateConfig = mapOf(
            "FASTTOWIN_RATE_LIMIT_STORE" to "redis",
            "FASTTOWIN_RATE_LIMIT_REDIS_URL" to required("FASTTOWIN_TEST_REDIS_URL"),
            "FASTTOWIN_RATE_LIMIT_NAMESPACE" to namespace
        )
        emit("configuration", mapOf(
            "qualification" to qualification, "phaseSeconds" to phaseSeconds, "soakSeconds" to soakSeconds,
            "socketRenewSeconds" to 20, "viewerReadSeconds" to 15, "rosterSize" to 50,
            "quotaPerSecond" to 10, "sampleSeconds" to 15, "operationBudgetMs" to 200,
            "lateThresholdMs" to LATE_MS, "scheduleP99GateMs" to SCHEDULE_GATE_MS,
            "redisMaxmemoryBytes" to MAXMEMORY, "containerMemoryCapBytes" to RSS_CAP,
            "syntheticNamespace" to namespace, "syntheticIds" to "random UUIDs, never emitted"
        ))
        val config = PresenceConfiguration(required("FASTTOWIN_TEST_PRESENCE_REDIS_URL"), namespace)
        RedisPresenceStore(config).use { first ->
            RedisPresenceStore(config).use { second ->
                openRateLimitRuntime(rateConfig).use { quotaA ->
                    openRateLimitRuntime(rateConfig).use { quotaB ->
                        val uri = object : RedisURI(RedisURI.create(required("FASTTOWIN_TEST_PRESENCE_STATS_URL"))) {
                            override fun toString() = "[redacted fixture INFO URI]"
                        }.apply { timeout = Duration.ofSeconds(2) }
                        val client = RedisClient.create(uri)
                        try {
                            client.connect().use { connection ->
                                val fixture = Fixture(listOf(first, second), listOf(quotaA, quotaB), connection.sync(), qualification)
                                fixture.warm()
                                val results = mutableListOf<PhaseResult>()
                                results += fixture.phase("baseline", 0, 0, phaseSeconds)
                                fixture.initializeSockets()
                                results += fixture.phase("viewers100", 500, 100, phaseSeconds)
                                fixture.initializeSockets()
                                val last = fixture.phase("viewers500", 500, 500, phaseSeconds)
                                results += last
                                if (results.all { it.failures.isEmpty() } && soakSeconds > 0) {
                                    // Keep the same leases, connections and absolute actor schedule across the checkpoint.
                                    // Drain/INFO/report time is visible as lag on the first continuation operations.
                                    results += fixture.phase("viewers500_soak", 500, 500, soakSeconds,
                                        last.startNanos + phaseSeconds * SECOND)
                                } else {
                                    emit("soak_skipped", mapOf("qualification" to qualification,
                                        "reason" to if (soakSeconds == 0L) "configured_zero" else "initial_phase_gate_failed"))
                                }
                                val failures = results.flatMap { result -> result.failures.map { "${result.name}:$it" } }
                                emit("result", mapOf("qualification" to qualification,
                                    "passed" to failures.isEmpty(), "phases" to results.map { it.name }, "failedGates" to failures))
                                assertTrue(failures.isEmpty(), "Viewer load gates failed: ${failures.joinToString()}")
                            }
                        } finally {
                            client.shutdown(Duration.ZERO, Duration.ofMillis(200))
                        }
                    }
                }
            }
        }
    }

    private class Fixture(
        val stores: List<RedisPresenceStore>,
        val quotas: List<RateLimitRuntime>,
        val stats: RedisCommands<String, String>,
        val qualification: Boolean
    ) {
        private val sockets = List(500) { Socket(UUID.randomUUID().toString(), UUID.randomUUID()) }
        private val policy = RateLimitPolicy(1_000, 60_000)

        suspend fun warm() {
            var passed = false
            try {
                // Complete all lazy presence handshakes and warm both Lua paths before measuring.
                stores.forEachIndexed { index, store ->
                    val socket = sockets[index]
                    var ready = false
                    repeat(12) {
                        if (!ready) {
                            ready = store.renew(socket.player, socket.connection, FriendPresence.ONLINE)
                            if (!ready) delay(250)
                        }
                    }
                    check(ready)
                    check(store.read(listOf(socket.player)) == mapOf(socket.player to FriendPresence.ONLINE))
                    check(store.revoke(socket.player, socket.connection))
                }
                quotas.forEachIndexed { index, quota -> check(quota.consume("login-ip", "warm-$index", policy).allowed) }
                val info = sample()
                check(info.long("maxmemory") == MAXMEMORY)
                check(info.fields["maxmemory_policy"] == "noeviction")
                check(info.long("aof_enabled") == 1L)
                passed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Avoid exception messages/causes containing fixture credentials or synthetic player IDs.
            }
            emit("preflight", mapOf("passed" to passed, "qualification" to qualification))
            check(passed) { "Viewer load preflight failed; no measured load result was produced." }
        }

        suspend fun initializeSockets() {
            var failed = 0
            // Bound setup concurrency; measured renewal scheduling is independent of these setup batches.
            sockets.withIndex().chunked(25).forEach { batch ->
                failed += coroutineScope {
                    batch.map { (index, socket) -> async {
                        try {
                            !stores[index % stores.size].renew(socket.player, socket.connection, FriendPresence.ONLINE)
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { true }
                    } }.awaitAll().count { it }
                }
            }
            emit("lease_initialization", mapOf("sockets" to sockets.size, "failed" to failed, "qualification" to qualification))
            check(failed == 0) { "Viewer load lease initialization failed; phase was not started." }
        }

        private suspend fun sample(): Info = withContext(Dispatchers.IO) { Info.parse(stats.info()) }

        suspend fun phase(name: String, socketCount: Int, viewers: Int, seconds: Long,
                          startNanos: Long = System.nanoTime()): PhaseResult {
            val duration = seconds * SECOND
            val renew = Operations(expected(socketCount, 20 * SECOND, duration))
            val read = Operations(expected(viewers, 15 * SECOND, duration))
            val quota = Operations(expected(1, SECOND / 10, duration))
            val operations = mapOf("renew" to renew, "read" to read, "quota" to quota)
            val samples = mutableListOf<Info>()
            var sampleErrors = 0
            suspend fun capture(event: String) {
                val info = try { sample().also { samples += it } } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { sampleErrors++; null }
                emit(event, mapOf("phase" to name, "qualification" to qualification,
                    "elapsedMs" to (System.nanoTime() - startNanos) / 1_000_000.0,
                    "operations" to operations.mapValues { it.value.report() },
                    "statsErrors" to sampleErrors, "redis" to info?.report()))
            }
            capture("phase_start")
            coroutineScope {
                fun actors(count: Int, period: Long, metrics: Operations, action: suspend (Int, Long) -> Outcome) {
                    repeat(count) { actor ->
                        launch {
                            var offset = actor.toLong() * period / count
                            var sequence = 0L
                            while (offset < duration) {
                                waitUntil(startNanos + offset)
                                metrics.scheduled.incrementAndGet()
                                val due = startNanos + offset
                                val currentSequence = sequence++
                                // Launch each due operation independently: no serial request or actor awaits hide load.
                                launch { metrics.measure(due) { action(actor, currentSequence) } }
                                offset += period
                            }
                        }
                    }
                }
                actors(socketCount, 20 * SECOND, renew) { actor, _ ->
                    val socket = sockets[actor]
                    if (stores[actor % stores.size].renew(socket.player, socket.connection, FriendPresence.ONLINE))
                        Outcome.OK else Outcome.UNAVAILABLE
                }
                val rosters = List(viewers) { actor -> List(50) { sockets[(actor * 37 + it) % 500].player } }
                actors(viewers, 15 * SECOND, read) { actor, _ ->
                    val roster = rosters[actor]
                    val state = stores[actor % stores.size].read(roster)
                    when {
                        state == null -> Outcome.UNAVAILABLE
                        state.size != roster.size || roster.any { state[it] != FriendPresence.ONLINE } -> Outcome.WRONG_STATE
                        else -> Outcome.OK
                    }
                }
                actors(1, SECOND / 10, quota) { _, sequence ->
                    val runtime = quotas[(sequence % quotas.size).toInt()]
                    // Both clients share bounded keys and assert allowed decisions at a stable 10 requests/sec.
                    if (runtime.consume("login-ip", "viewer-load-${sequence % 10}", policy).allowed)
                        Outcome.OK else Outcome.WRONG_STATE
                }
                launch {
                    var offset = 15 * SECOND
                    while (offset < duration) {
                        waitUntil(startNanos + offset)
                        capture("sample")
                        offset += 15 * SECOND
                    }
                    waitUntil(startNanos + duration)
                }
            }
            capture("phase_end")
            val failures = mutableListOf<String>()
            operations.forEach { (kind, metrics) ->
                if (metrics.scheduled.get() != metrics.due || metrics.started.get() != metrics.due || metrics.completed.get() != metrics.due)
                    failures += "$kind.work_count"
                if (metrics.errors.get() != 0L) failures += "$kind.errors"
                if (metrics.timeouts.get() != 0L) failures += "$kind.timeouts"
                if (metrics.unavailable.get() != 0L) failures += "$kind.unavailable"
                if (metrics.wrongStates.get() != 0L) failures += "$kind.wrong_states"
                if ((metrics.schedule.percentile(.99) ?: 0.0) >= SCHEDULE_GATE_MS) failures += "$kind.schedule_p99"
            }
            if ((read.latency.percentile(.95) ?: 0.0) >= 100) failures += "read.p95"
            if ((quota.latency.percentile(.99) ?: Double.POSITIVE_INFINITY) >= 200) failures += "quota.p99"
            if (sampleErrors > 0 || samples.size < 2) failures += "stats.unavailable"
            if (samples.any { it.long("maxmemory") != MAXMEMORY || it.fields["maxmemory_policy"] != "noeviction" || it.long("aof_enabled") != 1L })
                failures += "redis.configuration"
            if (samples.any { it.long("used_memory") >= MAXMEMORY * .70 }) failures += "memory.used_70pct"
            if (samples.any { it.long("used_memory_rss") >= RSS_CAP * .70 }) failures += "memory.rss_70pct"
            if (samples.isNotEmpty()) {
                val first = samples.first()
                if (samples.any { it.long("evicted_keys") != first.long("evicted_keys") }) failures += "redis.evictions"
                if (samples.any { it.long("total_error_replies") != first.long("total_error_replies") }) failures += "redis.error_replies"
                if (samples.any { it.long("aof_delayed_fsync") != first.long("aof_delayed_fsync") }) failures += "aof.delayed_fsync"
                if (samples.any { it.fields["aof_last_write_status"] != "ok" || it.fields["aof_last_bgrewrite_status"] != "ok" })
                    failures += "aof.status"
            }
            val first = samples.firstOrNull()
            val last = samples.lastOrNull()
            val cpuSeconds = if (first != null && last != null) last.cpuSeconds - first.cpuSeconds else null
            emit("phase_result", mapOf("phase" to name, "qualification" to qualification, "durationSeconds" to seconds,
                "sockets" to socketCount, "viewers" to viewers, "operations" to operations.mapValues { it.value.report() },
                "elapsedMs" to (System.nanoTime() - startNanos) / 1_000_000.0,
                "statsErrors" to sampleErrors, "statsSamples" to samples.size,
                "peakUsedBytes" to samples.maxOfOrNull { it.long("used_memory") },
                "peakRssBytes" to samples.maxOfOrNull { it.long("used_memory_rss") },
                "usedMemoryCapBytes" to MAXMEMORY, "rssCapBytes" to RSS_CAP, "memoryGateFraction" to .70,
                "cpuSecondsDelta" to cpuSeconds, "cpuQuotaUtilizationPct" to cpuSeconds?.let { it / seconds / .25 * 100 },
                "aofStartBytes" to first?.long("aof_current_size"), "aofEndBytes" to last?.long("aof_current_size"),
                "aofPeakBytes" to samples.maxOfOrNull { it.long("aof_current_size") },
                "failedGates" to failures, "passed" to failures.isEmpty()))
            return PhaseResult(name, startNanos, failures)
        }
    }

    private class Operations(val due: Long) {
        val scheduled = AtomicLong()
        val started = AtomicLong()
        val completed = AtomicLong()
        val late = AtomicLong()
        val errors = AtomicLong()
        val timeouts = AtomicLong()
        val unavailable = AtomicLong()
        val wrongStates = AtomicLong()
        val latency = Distribution()
        val schedule = Distribution()

        suspend fun measure(dueNanos: Long, action: suspend () -> Outcome) {
            val start = System.nanoTime()
            started.incrementAndGet()
            val lag = (start - dueNanos).coerceAtLeast(0) / 1_000_000.0
            schedule.add(lag)
            if (lag > LATE_MS) late.incrementAndGet()
            var explicitTimeout = false
            try {
                when (action()) {
                    Outcome.OK -> Unit
                    Outcome.UNAVAILABLE -> unavailable.incrementAndGet()
                    Outcome.WRONG_STATE -> wrongStates.incrementAndGet()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: RateLimitUnavailableException) {
                unavailable.incrementAndGet()
                explicitTimeout = failure.reason == RateLimitFailure.TIMEOUT
            } catch (_: Exception) { errors.incrementAndGet()
            } finally {
                val millis = (System.nanoTime() - start) / 1_000_000.0
                latency.add(millis)
                if (explicitTimeout || millis >= 200) timeouts.incrementAndGet()
                completed.incrementAndGet()
            }
        }

        fun report() = mapOf("due" to due, "scheduled" to scheduled.get(), "started" to started.get(),
            "completed" to completed.get(), "late" to late.get(), "errors" to errors.get(),
            "timeoutsOrBudgetExceeded" to timeouts.get(), "unavailable" to unavailable.get(), "wrongStates" to wrongStates.get(),
            "latencyMs" to latency.report(), "scheduleLagMs" to schedule.report())
    }

    private class Distribution {
        private val values = Collections.synchronizedList(mutableListOf<Double>())
        fun add(value: Double) { values.add(value) }
        fun percentile(fraction: Double): Double? = synchronized(values) {
            values.sorted().let { it.getOrNull(ceil(it.size * fraction).toInt() - 1) }
        }
        fun report() = mapOf("p50" to percentile(.50), "p95" to percentile(.95), "p99" to percentile(.99),
            "max" to synchronized(values) { values.maxOrNull() })
    }

    private data class Info(val fields: Map<String, String>) {
        fun long(name: String) = fields.getValue(name).toLong()
        val cpuSeconds get() = fields.getValue("used_cpu_sys").toDouble() + fields.getValue("used_cpu_user").toDouble()
        fun report(): Map<String, Any> = fields.mapValues { (_, value) -> value.toLongOrNull() ?: value.toDoubleOrNull() ?: value }
        companion object {
            private val required = setOf("used_memory", "used_memory_rss", "maxmemory", "maxmemory_policy", "used_cpu_sys",
                "used_cpu_user", "evicted_keys", "total_error_replies", "aof_enabled", "aof_current_size", "aof_base_size",
                "aof_rewrite_in_progress", "aof_rewrite_scheduled", "aof_last_bgrewrite_status", "aof_last_write_status", "aof_delayed_fsync")
            private val optional = setOf("aof_rewrites", "aof_rewrites_consecutive_failures", "aof_pending_rewrite",
                "aof_last_rewrite_time_sec", "aof_current_rewrite_time_sec", "aof_buffer_length", "aof_pending_bio_fsync",
                "aof_last_cow_size", "current_cow_size", "current_cow_peak", "used_cpu_sys_children", "used_cpu_user_children",
                "used_memory_peak", "mem_fragmentation_ratio", "instantaneous_ops_per_sec", "total_commands_processed")
            fun parse(text: String): Info {
                val fields = text.lineSequence().mapNotNull { line ->
                    val parts = line.split(':', limit = 2)
                    if (parts.size == 2 && parts[0] in required + optional) parts[0] to parts[1].trim() else null
                }.toMap()
                check(fields.keys.containsAll(required)) { "Required fixture INFO metrics missing." }
                return Info(fields)
            }
        }
    }

    private data class Socket(val player: String, val connection: UUID)
    private data class PhaseResult(val name: String, val startNanos: Long, val failures: List<String>)
    private enum class Outcome { OK, UNAVAILABLE, WRONG_STATE }

    companion object {
        private const val SECOND = 1_000_000_000L
        private const val MAXMEMORY = 64 * 1024 * 1024L
        private const val RSS_CAP = 250_000_000L
        private const val LATE_MS = 20.0
        private const val SCHEDULE_GATE_MS = 200.0
        private fun required(name: String) = checkNotNull(System.getenv(name)) { "Missing fixture variable $name" }
        private fun seconds(name: String, default: Long, minimum: Long): Long =
            (System.getenv(name)?.toLongOrNull() ?: default).also { require(it in minimum..86_400) { "Invalid $name" } }
        private fun expected(actors: Int, period: Long, duration: Long): Long = (0 until actors).sumOf { actor ->
            val offset = actor.toLong() * period / actors
            if (offset >= duration) 0L else (duration - 1 - offset) / period + 1
        }
        private suspend fun waitUntil(targetNanos: Long) {
            while (true) {
                val remaining = targetNanos - System.nanoTime()
                if (remaining <= 0) return
                delay((remaining / 1_000_000).coerceAtLeast(1))
            }
        }
        private fun json(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to json(it.value) })
            is Iterable<*> -> JsonArray(value.map(::json))
            else -> error("Unsupported aggregate receipt type")
        }
        private fun emit(event: String, fields: Map<String, Any?>) {
            println("PRESENCE_VIEWER_LOAD " + json(mapOf("event" to event) + fields))
        }
    }
}
