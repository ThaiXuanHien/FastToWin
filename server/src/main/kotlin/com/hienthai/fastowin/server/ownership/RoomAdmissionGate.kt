package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.util.concurrent.atomic.AtomicBoolean

internal class RoomAdmissionGate {
    private val lock = Any()
    private val quiescent = CompletableDeferred<Unit>()
    private var active = 0

    @Volatile
    var accepting: Boolean = true
        private set

    fun acquire(): AutoCloseable? = synchronized(lock) {
        if (!accepting) return null
        active++
        val closed = AtomicBoolean()
        AutoCloseable {
            if (!closed.compareAndSet(false, true)) return@AutoCloseable
            synchronized(lock) {
                active--
                if (!accepting && active == 0) quiescent.complete(Unit)
            }
        }
    }

    suspend fun <T : Any> admit(block: suspend () -> T): T? =
        acquire()?.use { block() }

    fun beginDrain(): Deferred<Unit> = synchronized(lock) {
        accepting = false
        if (active == 0) quiescent.complete(Unit)
        quiescent
    }
}
