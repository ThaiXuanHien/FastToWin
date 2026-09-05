package com.hienthai.fastowin.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.min

data class RateLimitPolicy(
    val capacity: Int,
    val refillWindowMillis: Long
) {
    init {
        require(capacity > 0) { "Rate limit capacity must be positive." }
        require(refillWindowMillis > 0) { "Rate limit refill window must be positive." }
    }
}

data class ServerRateLimitPolicies(
    val loginPerIp: RateLimitPolicy = RateLimitPolicy(20, 60_000L),
    val loginPerAccount: RateLimitPolicy = RateLimitPolicy(8, 5 * 60_000L),
    val passwordResetPerIp: RateLimitPolicy = RateLimitPolicy(10, 15 * 60_000L),
    val passwordResetPerAccount: RateLimitPolicy = RateLimitPolicy(3, 15 * 60_000L),
    val passwordResetConfirmPerIp: RateLimitPolicy = RateLimitPolicy(30, 15 * 60_000L),
    val passwordResetConfirmPerAccount: RateLimitPolicy = RateLimitPolicy(8, 15 * 60_000L),
    val emailVerificationRequestPerIp: RateLimitPolicy = RateLimitPolicy(60, 15 * 60_000L),
    val emailVerificationRequestPerAccount: RateLimitPolicy = RateLimitPolicy(3, 15 * 60_000L),
    val emailVerificationConfirmPerIp: RateLimitPolicy = RateLimitPolicy(120, 15 * 60_000L),
    val emailVerificationConfirmPerAccount: RateLimitPolicy = RateLimitPolicy(8, 15 * 60_000L),
    val websocketConnectPerIp: RateLimitPolicy = RateLimitPolicy(60, 60_000L),
    val websocketMessagesPerIp: RateLimitPolicy = RateLimitPolicy(300, 1_000L),
    val websocketMessagesPerPlayer: RateLimitPolicy = RateLimitPolicy(120, 1_000L),
    val createRoomPerPlayer: RateLimitPolicy = RateLimitPolicy(5, 60_000L),
    val createRoomPerIp: RateLimitPolicy = RateLimitPolicy(20, 60_000L),
    val joinRoomPerPlayer: RateLimitPolicy = RateLimitPolicy(12, 60_000L),
    val joinRoomPerIp: RateLimitPolicy = RateLimitPolicy(60, 60_000L),
    val joinRoomPerIpAndRoom: RateLimitPolicy = RateLimitPolicy(20, 60_000L),
    val selectNumberPerPlayer: RateLimitPolicy = RateLimitPolicy(20, 1_000L),
    val selectNumberPerIp: RateLimitPolicy = RateLimitPolicy(80, 1_000L),
    val verifyPurchasePerPlayer: RateLimitPolicy = RateLimitPolicy(6, 60_000L),
    val verifyPurchasePerIp: RateLimitPolicy = RateLimitPolicy(20, 60_000L)
)

data class RateLimitResult(
    val allowed: Boolean,
    val retryAfterMillis: Long = 0L
)

interface RateLimiter {
    suspend fun consume(bucket: String, key: String, policy: RateLimitPolicy): RateLimitResult
}

class InMemoryRateLimiter(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val maxBuckets: Int = DEFAULT_MAX_BUCKETS
) : RateLimiter {
    private val mutex = Mutex()
    private val states = mutableMapOf<String, BucketState>()
    private var checks = 0L
    private var lastCapacityCleanupAtMillis: Long? = null

    init {
        require(maxBuckets > 0) { "Maximum rate limit bucket count must be positive." }
    }

    override suspend fun consume(
        bucket: String,
        key: String,
        policy: RateLimitPolicy
    ): RateLimitResult = mutex.withLock {
        val now = nowMillis()
        checks++
        if (checks % CLEANUP_INTERVAL_CHECKS == 0L) removeIdleBuckets(now)

        val stateKey = "$bucket:$key"
        var state = states[stateKey]
        if (state == null) {
            if (states.size >= maxBuckets) {
                val lastCleanup = lastCapacityCleanupAtMillis
                if (lastCleanup == null || now - lastCleanup >= CAPACITY_CLEANUP_INTERVAL_MILLIS) {
                    removeIdleBuckets(now)
                    lastCapacityCleanupAtMillis = now
                }
                if (states.size >= maxBuckets) {
                    return@withLock RateLimitResult(false, BUCKET_CAPACITY_RETRY_MILLIS)
                }
            }
            state = BucketState(
                tokens = policy.capacity.toDouble(),
                refilledAtMillis = now,
                lastSeenAtMillis = now
            )
            states[stateKey] = state
        }

        val elapsedMillis = (now - state.refilledAtMillis).coerceAtLeast(0L)
        val refillRate = policy.capacity.toDouble() / policy.refillWindowMillis.toDouble()
        state.tokens = min(
            policy.capacity.toDouble(),
            state.tokens + elapsedMillis.toDouble() * refillRate
        )
        state.refilledAtMillis = now
        state.lastSeenAtMillis = now

        if (state.tokens >= 1.0) {
            state.tokens -= 1.0
            RateLimitResult(allowed = true)
        } else {
            val missingTokens = 1.0 - state.tokens
            RateLimitResult(
                allowed = false,
                retryAfterMillis = ceil(missingTokens / refillRate).toLong().coerceAtLeast(1L)
            )
        }
    }

    private fun removeIdleBuckets(now: Long) {
        states.entries.removeAll { (_, state) -> now - state.lastSeenAtMillis >= IDLE_BUCKET_TTL_MILLIS }
    }

    private data class BucketState(
        var tokens: Double,
        var refilledAtMillis: Long,
        var lastSeenAtMillis: Long
    )

    private companion object {
        const val DEFAULT_MAX_BUCKETS = 50_000
        const val CLEANUP_INTERVAL_CHECKS = 1_024L
        const val CAPACITY_CLEANUP_INTERVAL_MILLIS = 1_000L
        const val IDLE_BUCKET_TTL_MILLIS = 15 * 60_000L
        const val BUCKET_CAPACITY_RETRY_MILLIS = 60_000L
    }
}

internal object RateLimitBuckets {
    const val LOGIN_IP = "login-ip"
    const val LOGIN_ACCOUNT = "login-account"
    const val PASSWORD_RESET_IP = "password-reset-ip"
    const val PASSWORD_RESET_ACCOUNT = "password-reset-account"
    const val PASSWORD_RESET_CONFIRM_IP = "password-reset-confirm-ip"
    const val PASSWORD_RESET_CONFIRM_ACCOUNT = "password-reset-confirm-account"
    const val EMAIL_VERIFICATION_REQUEST_IP = "email-verification-request-ip"
    const val EMAIL_VERIFICATION_REQUEST_ACCOUNT = "email-verification-request-account"
    const val EMAIL_VERIFICATION_CONFIRM_IP = "email-verification-confirm-ip"
    const val EMAIL_VERIFICATION_CONFIRM_ACCOUNT = "email-verification-confirm-account"
    const val WEBSOCKET_CONNECT_IP = "websocket-connect-ip"
    const val WEBSOCKET_MESSAGE_IP = "websocket-message-ip"
    const val WEBSOCKET_MESSAGE_PLAYER = "websocket-message-player"
    const val CREATE_ROOM_PLAYER = "create-room-player"
    const val CREATE_ROOM_IP = "create-room-ip"
    const val JOIN_ROOM_PLAYER = "join-room-player"
    const val JOIN_ROOM_IP = "join-room-ip"
    const val JOIN_ROOM_IP_AND_ROOM = "join-room-ip-room"
    const val SELECT_NUMBER_PLAYER = "select-number-player"
    const val SELECT_NUMBER_IP = "select-number-ip"
    const val VERIFY_PURCHASE_PLAYER = "verify-purchase-player"
    const val VERIFY_PURCHASE_IP = "verify-purchase-ip"
}

internal fun stableRateLimitKey(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
