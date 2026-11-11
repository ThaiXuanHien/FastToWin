package com.hienthai.fastowin.server

import java.sql.Connection

/** Bound authorization reads even when PostgreSQL cannot answer a query cancellation. */
internal inline fun <T> Connection.withGameplayReadTimeout(block: () -> T): T {
    val previous = networkTimeout
    setNetworkTimeout(Runnable::run, if (previous in 1..3000) previous else 3000)
    var failure: Throwable? = null
    try {
        return block()
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        if (!isClosed) {
            try {
                setNetworkTimeout(Runnable::run, previous)
            } catch (reset: Throwable) {
                // A connection with unknown timeout state must never return healthy to its pool.
                runCatching { abort(Runnable::run) }.exceptionOrNull()?.let(reset::addSuppressed)
                runCatching { close() }.exceptionOrNull()?.let(reset::addSuppressed)
                if (failure == null) throw reset else failure.addSuppressed(reset)
            }
        }
    }
}
