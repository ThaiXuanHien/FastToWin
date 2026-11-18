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

/**
 * Single-backend collector. A failed observation always breaks the healthy window.
 *
 * Socket open/close only change memory; the periodic checkpoint writes every active and recently
 * closed connection in one transaction. Writing all active connections on every open/close cost
 * O(online players) statements per socket event under one lock, which the SCL-01 load test
 * measured as the first single-backend ceiling (session_ready waited behind it).
 */
class ActivityTracker(private val repository: ActivityRepository = UnavailableActivityRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()          // In-memory state only; never held across I/O.
    private val writeMutex = Mutex()     // One checkpoint transaction at a time.
    private val active = linkedMapOf<UUID, ActivityObservation>()
    private val closed = linkedMapOf<UUID, ActivityObservation>()
    private var window: ActivityWindow? = null
    private var lastCleanup: Long? = null

    suspend fun connected(connectionId: UUID, userId: String) = mutex.withLock {
        if (repository === UnavailableActivityRepository) return@withLock
        val now = nowMillis()
        // With nobody else observed, coverage starts at this connection, as when every event was written.
        // After a failed checkpoint other connections stay unbridged: the next checkpoint starts a new window.
        if (window == null && active.isEmpty() && closed.isEmpty()) window = ActivityWindow(UUID.randomUUID(), now, now)
        active[connectionId] = ActivityObservation(UUID.randomUUID(), connectionId, userId, now, now)
    }

    suspend fun disconnected(connectionId: UUID) = mutex.withLock {
        val observation = active.remove(connectionId) ?: return@withLock
        closed[connectionId] = observation.copy(until = maxOf(observation.until, nowMillis()))
    }

    suspend fun checkpoint() {
        if (repository !== UnavailableActivityRepository) observe()
    }

    /** Called by Application's lifecycle coroutine; idle accounts do not need to send messages. */
    suspend fun run() {
        if (repository === UnavailableActivityRepository) return
        try {
            checkpoint()
            while (currentCoroutineContext().isActive) { delay(ACTIVITY_CHECKPOINT_MILLIS); checkpoint() }
        } finally { withContext(NonCancellable) { checkpoint() } }
    }

    private class Prepared(val checkpoint: ActivityCheckpoint, val flushedClosed: Set<UUID>)

    private suspend fun observe() = writeMutex.withLock {
        val prepared = mutex.withLock {
            val now = nowMillis()
            val previous = window
            val continuous = previous != null && now >= previous.until && now - previous.until <= ACTIVITY_MAX_GAP_MILLIS
            val next = if (continuous) previous!!.copy(until = now) else ActivityWindow(UUID.randomUUID(), now, now)
            val current = active.values.map { observation ->
                if (continuous || observation.from == now) observation.copy(until = now)
                else observation.copy(id = UUID.randomUUID(), from = now, until = now)
            }
            // Without continuous coverage a closed connection's time was unobserved; never bridge it.
            val finished = if (continuous) closed.values.toList() else emptyList()
            val cleanup = lastCleanup?.let { now >= it && now - it < ACTIVITY_CLEANUP_MILLIS } != true
            Prepared(ActivityCheckpoint(current + finished, next, now, cleanup), closed.keys.toSet())
        }
        try {
            repository.checkpoint(prepared.checkpoint)
            mutex.withLock {
                prepared.flushedClosed.forEach(closed::remove)
                prepared.checkpoint.observations.forEach { written ->
                    // Keep the written segment identity. A socket closed during the write keeps its close time.
                    if (active.containsKey(written.connectionId)) active[written.connectionId] = written
                    closed[written.connectionId]?.let { later ->
                        closed[written.connectionId] = written.copy(until = maxOf(written.until, later.until))
                    }
                }
                window = prepared.checkpoint.window
                if (prepared.checkpoint.cleanup) lastCleanup = prepared.checkpoint.now
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { breakWindow(prepared) }
            throw cancelled
        } catch (_: Exception) {
            breakWindow(prepared)
            // Never log a JDBC error, identity, token or connection details.
            System.err.println("Connected account activity collection unavailable")
        }
    }

    private suspend fun breakWindow(prepared: Prepared) = mutex.withLock {
        window = null
        prepared.flushedClosed.forEach(closed::remove)
    }
}
