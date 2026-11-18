package com.hienthai.fastowin.server

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * SCL-06: the engine's durable-write lock. [withLock] is exclusive, exactly like the former single
 * Mutex. [withRoom] lets writes of different rooms run together while writes of one room stay
 * ordered: the SCL-01 load test measured the one global lock capping the whole server at about
 * 200 selections/s with PostgreSQL half idle. Neither section may be entered from inside another.
 */
class PersistenceLock(@PublishedApi internal val sharedPermits: Int = 64, stripes: Int = 64) {
    @PublishedApi internal val permits = Semaphore(sharedPermits)
    @PublishedApi internal val writer = Mutex()
    private val roomLocks = Array(stripes) { Mutex() }

    /** Exclusive: waits for every room write to finish and admits none until [block] returns. */
    suspend inline fun <T> withLock(block: () -> T): T = writer.withLock {
        var acquired = 0
        try {
            while (acquired < sharedPermits) { permits.acquire(); acquired++ }
            block()
        } finally {
            withContext(NonCancellable) { repeat(acquired) { permits.release() } }
        }
    }

    /** Shared with other rooms, exclusive per room (striped by id). */
    suspend fun <T> withRoom(roomId: String, block: suspend () -> T): T {
        permits.acquire()
        try {
            return roomLocks[Math.floorMod(roomId.hashCode(), roomLocks.size)].withLock { block() }
        } finally {
            permits.release()
        }
    }
}
