package com.hienthai.fastowin.server.ratelimit

import com.hienthai.fastowin.server.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLongArray

internal interface RateLimitRuntime : RateLimiter, AutoCloseable {
    fun renderMetrics(): String
}

internal fun openRateLimitRuntime(values: Map<String, String> = System.getenv(), fileReader: (String) -> String = { Files.readString(Path.of(it)) }): RateLimitRuntime {
    val config = rateLimitConfiguration(values, fileReader)
    return DispatchRateLimitRuntime(config?.let(::RedisRateLimiter))
}

private class DispatchRateLimitRuntime(private val redis: RedisRateLimiter?) : RateLimitRuntime {
    private val local = InMemoryRateLimiter()
    private val decisions = AtomicLongArray(3)
    private val failures = AtomicLongArray(RateLimitFailure.entries.size)
    override suspend fun consume(bucket: String, key: String, policy: RateLimitPolicy): RateLimitResult {
        if (redis == null || bucket !in sharedBuckets) return local.consume(bucket, key, policy)
        try {
            val result = redis.consume(bucket, key, policy)
            decisions.incrementAndGet(if (result.allowed) 0 else 1)
            return result
        } catch (failure: RateLimitUnavailableException) {
            decisions.incrementAndGet(2)
            failures.incrementAndGet(failure.reason.ordinal)
            throw failure
        }
    }
    override fun renderMetrics(): String = buildString {
        listOf("allowed", "limited", "unavailable").forEachIndexed { i, name -> appendLine("fasttowin_rate_limit_shared_decisions_total{outcome=\"$name\"} ${decisions.get(i)}") }
        RateLimitFailure.entries.forEach { reason -> appendLine("fasttowin_rate_limit_shared_failures_total{reason=\"${reason.name.lowercase()}\"} ${failures.get(reason.ordinal)}") }
    }
    override fun close() { redis?.close() }
}

private val sharedBuckets = setOf(
    "login-ip", "login-account", "password-reset-ip", "password-reset-account",
    "password-reset-confirm-ip", "password-reset-confirm-account", "email-verification-request-ip",
    "email-verification-request-account", "email-verification-confirm-ip", "email-verification-confirm-account",
    "websocket-connect-ip", "create-room-player", "create-room-ip", "join-room-player", "join-room-ip",
    "join-room-ip-room", "admin-login-ip", "admin-login-account"
)
