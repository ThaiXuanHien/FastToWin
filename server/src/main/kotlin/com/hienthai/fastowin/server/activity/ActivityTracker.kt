package com.hienthai.fastowin.server.activity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Single-backend collector. A failed observation always breaks the healthy window. */
class ActivityTracker(private val repository: ActivityRepository = UnavailableActivityRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val active = linkedMapOf<UUID, ActivityObservation>()
    private var window: ActivityWindow? = null
    private var lastCleanup: Long? = null

    suspend fun connected(connectionId: UUID, userId: String) = mutex.withLock {
        if (repository === UnavailableActivityRepository) return@withLock
        val now = nowMillis()
        active[connectionId] = ActivityObservation(UUID.randomUUID(), connectionId, userId, now, now)
        observe(now)
    }

    suspend fun disconnected(connectionId: UUID) = mutex.withLock {
        if (!active.containsKey(connectionId)) return@withLock
        try { observe(nowMillis()) } finally { active.remove(connectionId) }
    }

    suspend fun checkpoint() = mutex.withLock {
        if (repository !== UnavailableActivityRepository) observe(nowMillis())
    }

    /** Called by Application's lifecycle coroutine; idle accounts do not need to send messages. */
    suspend fun run() {
        if (repository === UnavailableActivityRepository) return
        try {
            checkpoint()
            while (currentCoroutineContext().isActive) { delay(ACTIVITY_CHECKPOINT_MILLIS); checkpoint() }
        } finally { withContext(NonCancellable) { checkpoint() } }
    }

    private suspend fun observe(now: Long) {
        val previous = window
        val continuous = previous != null && now >= previous.until && now - previous.until <= ACTIVITY_MAX_GAP_MILLIS
        val next = if (continuous) previous!!.copy(until = now) else ActivityWindow(UUID.randomUUID(), now, now)
        val observations = active.values.map { observation ->
            if (continuous || observation.from == now) observation.copy(until = now)
            else observation.copy(id = UUID.randomUUID(), from = now, until = now)
        }
        val cleanup = lastCleanup?.let { now >= it && now - it < ACTIVITY_CLEANUP_MILLIS } != true
        try {
            repository.checkpoint(ActivityCheckpoint(observations, next, now, cleanup))
            observations.forEach { active[it.connectionId] = it }
            window = next
            if (cleanup) lastCleanup = now
        } catch (cancelled: CancellationException) {
            window = null
            throw cancelled
        } catch (_: Exception) {
            window = null
            // Never log a JDBC error, identity, token or connection details.
            System.err.println("Connected account activity collection unavailable")
        }
    }
}
