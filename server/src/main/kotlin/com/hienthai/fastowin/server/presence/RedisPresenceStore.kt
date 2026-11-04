package com.hienthai.fastowin.server.presence

import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.server.ratelimit.RedisLogProtection
import com.hienthai.fastowin.server.stableRateLimitKey
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class RedisPresenceStore(
    private val config: PresenceConfiguration,
    private val leaseMillis: Long = 60_000
) : PresenceStore {
    private val resources: DefaultClientResources
    private val client: RedisClient
    private val uri: RedisURI
    private var opening: CompletableFuture<StatefulRedisConnection<String, String>>? = null
    private var lastConnectAttempt: Long? = null
    private var closed = false
    private val updateScript = requireNotNull(javaClass.getResource("/redis/presence_update.lua")).readText()
    private val readScript = requireNotNull(javaClass.getResource("/redis/presence_read.lua")).readText()
    private val readFailures = AtomicLong()
    private val writeFailures = AtomicLong()

    init {
        require(leaseMillis in 1..60_000)
        RedisLogProtection.install()
        resources = DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build()
        var openedClient: RedisClient? = null
        try {
            uri = object : RedisURI(RedisURI.create(config.url)) {
                override fun toString() = "[redacted presence Redis URI]"
            }.apply { timeout = Duration.ofSeconds(2) }
            require(uri.isVerifyPeer)
            client = RedisClient.create(resources, uri)
            openedClient = client
            client.options = ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .replayFilter { true }
                .requestQueueSize(128)
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(2)).build())
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200)))
                .build()
        } catch (_: Exception) {
            runCatching { openedClient?.shutdown(Duration.ZERO, Duration.ofMillis(200)) }
            resources.shutdown(0, 200, TimeUnit.MILLISECONDS)
            throw IllegalArgumentException("Redis presence service unavailable.")
        }
        openConnection()
    }

    // One shared handshake. Individual operation timeouts must not cancel it for other callers.
    @Synchronized
    private fun openConnection(): CompletableFuture<StatefulRedisConnection<String, String>>? {
        if (closed) return null
        opening?.takeUnless { it.isCompletedExceptionally }?.let { return it }
        val now = System.nanoTime()
        if (lastConnectAttempt?.let { now - it < TimeUnit.SECONDS.toNanos(1) } == true) return null
        lastConnectAttempt = now
        return runCatching { client.connectAsync(StringCodec.UTF8, uri).toCompletableFuture() }
            .getOrNull().also { opening = it }
    }

    override suspend fun renew(playerId: String, connectionId: UUID, presence: FriendPresence): Boolean {
        require(presence != FriendPresence.OFFLINE)
        return update(playerId, connectionId, presence.ordinal)
    }

    override suspend fun revoke(playerId: String, connectionId: UUID): Boolean = update(playerId, connectionId, 0)

    private suspend fun update(playerId: String, connectionId: UUID, state: Int): Boolean {
        val updated = evaluate<Long>(updateScript, ScriptOutputType.INTEGER, keys(playerId),
            connectionId.toString(), state.toString(), leaseMillis.toString()) == 1L
        if (!updated) writeFailures.incrementAndGet()
        return updated
    }

    override suspend fun read(playerIds: Collection<String>): Map<String, FriendPresence>? {
        val result = withTimeoutOrNull(200) { readBatches(playerIds) }
        if (result == null) readFailures.incrementAndGet()
        return result
    }

    private suspend fun readBatches(playerIds: Collection<String>): Map<String, FriendPresence>? {
        val result = mutableMapOf<String, FriendPresence>()
        for (batch in playerIds.distinct().chunked(50)) {
            val batchKeys = batch.flatMap { keys(it).asList() }.toTypedArray()
            val reply = evaluate<List<*>>(readScript, ScriptOutputType.MULTI, batchKeys) ?: return null
            if (reply.size != batch.size) return null
            batch.forEachIndexed { index, id ->
                val code = reply[index] as? Long ?: return null
                if (code !in 0L..3L) return null
                result[id] = FriendPresence.entries[code.toInt()]
            }
        }
        return result
    }

    override fun renderMetrics(): String = buildString {
        appendLine("# HELP fasttowin_presence_redis_failures_total Failed Redis presence operations.")
        appendLine("# TYPE fasttowin_presence_redis_failures_total counter")
        appendLine("fasttowin_presence_redis_failures_total{operation=\"read\"} ${readFailures.get()}")
        appendLine("fasttowin_presence_redis_failures_total{operation=\"write\"} ${writeFailures.get()}")
    }

    private fun keys(playerId: String): Array<String> {
        val key = "ftw:${config.namespace}:presence:v1:${stableRateLimitKey(playerId)}"
        return arrayOf("$key:online", "$key:room", "$key:playing")
    }

    private suspend fun <T> evaluate(script: String, type: ScriptOutputType, keys: Array<String>, vararg args: String): T? =
        try {
            withTimeoutOrNull(200) {
                val connection = openConnection()?.await() ?: return@withTimeoutOrNull null
                connection.async().eval<T>(script, type, keys, *args).toCompletableFuture().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
        whenComplete { value, error ->
            if (error == null) continuation.resume(value) else continuation.resumeWithException(error)
        }
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
        }
        try {
            client.shutdown(Duration.ZERO, Duration.ofMillis(200))
        } finally {
            resources.shutdown(0, 200, TimeUnit.MILLISECONDS).await(200, TimeUnit.MILLISECONDS)
        }
    }
}
