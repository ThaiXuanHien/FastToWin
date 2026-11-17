package com.hienthai.fastowin.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SCL-05: graceful shutdown. Once draining, `/ready` reports 503, new game sockets are
 * refused, and open sockets are closed spread across [windowMillis] so clients (which add
 * their own random jitter) reconnect to the new deployment gradually instead of all at once.
 * Liveness (`/health`) keeps answering OK so the platform does not kill the process early.
 */
class ServerDrain(val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    init { require(windowMillis in 0..120_000) { "FASTTOWIN_DRAIN_SECONDS must be between 0 and 120." } }

    private val active = AtomicBoolean(false)
    val draining: Boolean get() = active.get()

    /** Closes every connection once, spread evenly across the window; returns when all were closed. */
    suspend fun drain(connections: List<suspend () -> Unit>) {
        if (!active.compareAndSet(false, true)) return
        val offsets = schedule(connections.size, windowMillis)
        coroutineScope {
            connections.zip(offsets).forEach { (close, offset) ->
                launch {
                    delay(offset)
                    try { close() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Already gone. */ }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 10_000L

        /** Evenly spaced close times within the window, first one immediately. */
        internal fun schedule(count: Int, windowMillis: Long): List<Long> =
            if (count <= 1) List(count) { 0L } else List(count) { index -> windowMillis * index / count }

        fun fromEnvironment(values: Map<String, String> = System.getenv()): ServerDrain {
            val seconds = values["FASTTOWIN_DRAIN_SECONDS"]?.trim()?.takeIf(String::isNotEmpty)
                ?.let { requireNotNull(it.toLongOrNull()) { "FASTTOWIN_DRAIN_SECONDS must be a whole number." } }
            return ServerDrain(seconds?.times(1_000) ?: DEFAULT_WINDOW_MILLIS)
        }
    }
}
