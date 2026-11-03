package com.hienthai.fastowin.server.ratelimit

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.hienthai.fastowin.server.RateLimitPolicy
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** One-shot, test-only entry point. Its output deliberately contains only fixed labels and numbers. */
object RedisAcceptanceRunner {
    @JvmStatic fun validate(env: Map<String, String>): String {
        if (env["FASTTOWIN_TEST_REDIS_URL"].isNullOrBlank()) return "missing_url"
        val duration = env["REDIS_ACCEPTANCE_DURATION_SECONDS"] ?: "10"
        if (duration.toIntOrNull() !in 1..600) return "invalid_duration"
        val rate = env["REDIS_ACCEPTANCE_CONSUMES_PER_SECOND"] ?: "10"
        if (rate.toIntOrNull() !in 1..20) return "invalid_rate"
        val uri = runCatching { java.net.URI(env.getValue("FASTTOWIN_TEST_REDIS_URL")) }.getOrNull()
        if (uri?.scheme != "redis" || uri.host.isNullOrBlank()) return "invalid_url"
        return "ok"
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @JvmStatic fun invokeCase(type: String, name: String): Boolean = try {
        val klass = Class.forName(type)
        klass.getMethod(name).invoke(klass.getDeclaredConstructor().newInstance())
        true
    } catch (_: Throwable) { false }

    private fun verifyBundle(root: Path): Pair<String, String> {
        val manifest = Files.readAllBytes(root.resolve("SHA256SUMS"))
        val lines = manifest.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.toList()
        require(lines.isNotEmpty())
        val expected = mutableSetOf<String>()
        lines.forEach { line ->
            require(line.length > 66 && line.substring(64, 66) == "  ")
            val name = line.substring(66)
            require(name.matches(Regex("(?:lib/[A-Za-z0-9_.+-]+\\.jar|REVISION)")) && expected.add(name))
            require(digest(Files.readAllBytes(root.resolve(name))) == line.substring(0, 64))
        }
        val actual = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.map { root.relativize(it).toString().replace('\\', '/') }.toList().toSet() }
        require(actual == expected + "SHA256SUMS")
        val revision = Files.readString(root.resolve("REVISION")).trim()
        require(revision.matches(Regex("[0-9a-f]{40}")))
        return revision to digest(manifest)
    }

    @JvmStatic fun main(args: Array<String>) {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.OFF
        Thread({
            Thread.sleep(TimeUnit.MINUTES.toMillis(25))
            println("""{"status":"fail","reason":"deadline"}""")
            Runtime.getRuntime().halt(1)
        }, "acceptance-deadline").apply { isDaemon = true; start() }
        var revision = "unknown"; var checksum = "unknown"; var passed = 0; var failed = 0
        var startupMs = 0L; val latencies = mutableListOf<Long>(); var reason = "ok"
        var allowed = 0; var limited = 0; var unavailable = 0; var failureReason = "none"
        val observedLoadConcurrency = java.util.concurrent.atomic.AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "acceptance-case").apply { isDaemon = true } }
        fun phase(seconds: Long, action: () -> Unit) {
            val future = executor.submit(action)
            try { future.get(seconds, TimeUnit.SECONDS) } catch (e: Throwable) { future.cancel(true); throw e }
        }
        try {
            val validation = validate(System.getenv())
            require(validation == "ok") { validation }
            try { val metadata = verifyBundle(Path.of(System.getenv("REDIS_ACCEPTANCE_ROOT") ?: "/app")); revision = metadata.first; checksum = metadata.second }
            catch (_: Throwable) { reason = "invalid_bundle"; throw IllegalStateException() }
            val cases = listOf(
                "com.hienthai.fastowin.server.ratelimit.RedisRateLimiterTest" to "independent runtimes share capacity under concurrency",
                "com.hienthai.fastowin.server.ratelimit.RedisRateLimiterTest" to "keys namespaces and local buckets are independent",
                "com.hienthai.fastowin.server.ratelimit.RedisRateLimiterTest" to "refill retry and expiry retain continuous bucket semantics",
                "com.hienthai.fastowin.server.ratelimit.RedisRateLimiterTest" to "policy mismatch and corrupt state refuse without overwriting",
                "com.hienthai.fastowin.server.ratelimit.RedisRateLimiterTest" to "wrong type and partial hash are not replaced and long windows have matching ttl",
                "com.hienthai.fastowin.server.ratelimit.RedisRateLimiterTest" to "all specified entry buckets share limits across runtimes",
                "com.hienthai.fastowin.server.ratelimit.RedisFaultTest" to "lost response consumes once and does not replay",
                "com.hienthai.fastowin.server.ratelimit.RedisFaultTest" to "malformed response is unavailable after real write",
                "com.hienthai.fastowin.server.ratelimit.RedisFaultTest" to "held responses bound in flight and cancellation propagates",
                "com.hienthai.fastowin.server.ratelimit.RedisFaultTest" to "disconnect refuses shared traffic while local continues and reconnect recovers",
                "com.hienthai.fastowin.server.RedisNettyAcceptanceTest" to "two Netty applications share login and connection quotas with namespace isolation",
                "com.hienthai.fastowin.server.RedisNettyAcceptanceTest" to "two applications share admin login and room entry limits",
                "com.hienthai.fastowin.server.RedisNettyAcceptanceTest" to "outage fails login and room actions but existing socket remains usable and recovers"
            )
            cases.forEach { (type, name) ->
                try { phase(60) { check(invokeCase(type, name)) }; passed++ }
                catch (_: Throwable) { failed++; reason = "case_failed"; throw IllegalStateException() }
            }
            phase(605) {
                val ns = "t" + UUID.randomUUID().toString().replace("-", "")
                val settings = mapOf("FASTTOWIN_RATE_LIMIT_STORE" to "redis", "FASTTOWIN_RATE_LIMIT_REDIS_URL" to System.getenv("FASTTOWIN_TEST_REDIS_URL"), "FASTTOWIN_RATE_LIMIT_NAMESPACE" to ns)
                val started = System.nanoTime()
                openRateLimitRuntime(settings).use { first ->
                    openRateLimitRuntime(settings).use { second ->
                        startupMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                        val duration = (System.getenv("REDIS_ACCEPTANCE_DURATION_SECONDS") ?: "10").toInt()
                        val rate = (System.getenv("REDIS_ACCEPTANCE_CONSUMES_PER_SECOND") ?: "10").toInt()
                        val interval = 1_000_000_000L / rate
                        var next = System.nanoTime()
                        val deadline = next + TimeUnit.SECONDS.toNanos(duration.toLong())
                        var i = 0
                        while (i < duration * rate && System.nanoTime() < deadline) {
                            val wait = next - System.nanoTime()
                            if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait)
                            val before = System.nanoTime()
                            if (before >= deadline) break
                            next = before + interval
                            try {
                                observedLoadConcurrency.set(1)
                                val result = runBlocking { (if (i % 2 == 0) first else second).consume("login-ip", "load", RateLimitPolicy(1_000_000, 60_000)) }
                                if (result.allowed) allowed++ else limited++
                            } catch (e: com.hienthai.fastowin.server.RateLimitUnavailableException) {
                                unavailable++; failureReason = e.reason.name.lowercase(); throw e
                            } finally { latencies.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - before)) }
                            i++
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            if (reason == "ok") reason = validate(System.getenv()).let { if (it == "ok") "load_failed" else it }
        } finally { executor.shutdownNow() }
        val sorted = latencies.sorted()
        fun percentile(p: Int) = if (sorted.isEmpty()) 0 else sorted[((sorted.size * p + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)]
        val status = if (reason == "ok" && passed == 13 && failed == 0) "pass" else "fail"
        println("""{"status":"$status","reason":"$reason","revision":"$revision","manifest_sha256":"$checksum","cases_passed":$passed,"cases_failed":$failed,"startup_ms":$startupMs,"consume_total":${sorted.size},"consume_allowed":$allowed,"consume_limited":$limited,"consume_unavailable":$unavailable,"consume_failure_reason":"$failureReason","consume_p50_us":${percentile(50)},"consume_p95_us":${percentile(95)},"consume_p99_us":${percentile(99)},"pending_configured_limit":128,"load_max_issued_concurrently":${observedLoadConcurrency.get()}}""")
        exitProcess(if (status == "pass") 0 else 1)
    }
}
