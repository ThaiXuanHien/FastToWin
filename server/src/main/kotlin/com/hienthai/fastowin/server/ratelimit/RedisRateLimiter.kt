package com.hienthai.fastowin.server.ratelimit

import com.hienthai.fastowin.server.*
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SocketOptions
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.resource.DefaultClientResources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class RedisRateLimiter(private val config: RedisConfiguration) : RateLimiter, AutoCloseable {
    private val resources: DefaultClientResources
    private val client: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val permits = Semaphore(128)
    private val script = requireNotNull(javaClass.getResource("/redis/rate_limit.lua")).readText()

    init {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1850)
        fun remaining(): Long = (deadline - System.nanoTime()).coerceAtLeast(1)
        var openedResources: DefaultClientResources? = null
        var openedClient: RedisClient? = null
        var openedConnection: StatefulRedisConnection<String, String>? = null
        try {
            val uri = RedisURI.create(config.url).apply { timeout = Duration.ofSeconds(2) }
            require(uri.isVerifyPeer)
            val resource = DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build()
            openedResources = resource
            val c = RedisClient.create(resource, uri)
            openedClient = c
            c.options = ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .replayFilter { true }
                .requestQueueSize(128)
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(2)).build())
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200)))
                .build()
            val connectFuture = c.connectAsync(StringCodec.UTF8, uri)
            val conn = try { connectFuture.get(remaining(), TimeUnit.NANOSECONDS) } catch (e: Exception) { connectFuture.cancel(true); throw e }
            openedConnection = conn
            require(conn.async().ping().get(remaining(), TimeUnit.NANOSECONDS) == "PONG")
            resources = resource
            client = c
            connection = conn
        } catch (_: Exception) {
            runCatching { openedConnection?.close() }
            runCatching { openedClient?.shutdown(Duration.ZERO, Duration.ofMillis(30)) }
            runCatching { openedResources?.shutdown(0, 30, TimeUnit.MILLISECONDS)?.await(30, TimeUnit.MILLISECONDS) }
            throw IllegalArgumentException("Redis rate limit service unavailable.")
        }
    }

    override suspend fun consume(bucket: String, key: String, policy: RateLimitPolicy): RateLimitResult {
        currentCoroutineContext().ensureActive()
        if (policy.capacity > 1_000_000 || policy.refillWindowMillis > 86_400_000) throw RateLimitUnavailableException(RateLimitFailure.OTHER)
        if (!permits.tryAcquire()) throw RateLimitUnavailableException(RateLimitFailure.OVERLOADED)
        val redisKey = "ftw:${config.namespace}:rl:v1:$bucket:${stableRateLimitKey(key)}"
        try {
            val future = try {
                connection.async().eval<List<*>>(
                    script, ScriptOutputType.MULTI, arrayOf(redisKey),
                    policy.capacity.toString(), policy.refillWindowMillis.toString()
                )
            } catch (e: Exception) {
                permits.release()
                throw e
            }
            future.whenComplete { _, _ -> permits.release() }
            val result = withTimeoutOrNull(200) {
                suspendCancellableCoroutine<List<*>> { continuation ->
                    future.whenComplete { value, error ->
                        if (error == null) continuation.resume(value) else continuation.resumeWithException(error)
                    }
                    // The command can still execute after cancellation.
                }
            } ?: throw RateLimitUnavailableException(RateLimitFailure.TIMEOUT)
            if (result.size != 2 || result[0] !is Long || result[1] !is Long) throw RateLimitUnavailableException(RateLimitFailure.INVALID_RESPONSE)
            val status = result[0] as Long
            val retry = result[1] as Long
            return when {
                status == 1L && retry == 0L -> RateLimitResult(true)
                status == 0L && retry in 1..86_400_000 -> RateLimitResult(false, retry)
                status == 2L && retry == 0L -> throw RateLimitUnavailableException(RateLimitFailure.POLICY_MISMATCH)
                else -> throw RateLimitUnavailableException(RateLimitFailure.INVALID_RESPONSE)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: RateLimitUnavailableException) { throw e
        } catch (e: Exception) {
            if (e is io.lettuce.core.RedisCommandTimeoutException || e.cause is io.lettuce.core.RedisCommandTimeoutException) throw RateLimitUnavailableException(RateLimitFailure.TIMEOUT)
            throw RateLimitUnavailableException(if (!connection.isOpen) RateLimitFailure.DISCONNECTED else RateLimitFailure.OTHER)
        }
    }

    override fun close() {
        connection.close()
        client.shutdown(Duration.ZERO, Duration.ofMillis(200))
        resources.shutdown(0, 200, TimeUnit.MILLISECONDS).await(200, TimeUnit.MILLISECONDS)
    }
}
