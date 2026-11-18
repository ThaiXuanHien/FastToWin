package com.hienthai.fastowin.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistenceLockTest {
    @Test
    fun `different rooms write together while one room stays ordered`() = runTest {
        val lock = PersistenceLock(); val inside = AtomicInteger(); var peak = 0
        val order = mutableListOf<Int>()
        (1..8).map { room -> async { lock.withRoom("room-$room") {
            peak = maxOf(peak, inside.incrementAndGet()); delay(10); inside.decrementAndGet() } } }.awaitAll()
        assertEquals(8, peak)
        (1..5).map { step -> async { lock.withRoom("same") { delay((6 - step).toLong()); order += step } } }.awaitAll()
        assertEquals(listOf(1, 2, 3, 4, 5), order)
    }

    @Test
    fun `exclusive section waits for room writes and holds new ones back`() = runTest {
        val lock = PersistenceLock(sharedPermits = 4); val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val roomWrite = launch { lock.withRoom("a") { events += "room-start"; release.await(); events += "room-end" } }
        yield()
        val exclusive = launch { lock.withLock { events += "exclusive" } }
        yield()
        val later = launch { lock.withRoom("b") { events += "later-room" } }
        yield()
        assertFalse("exclusive" in events)
        release.complete(Unit)
        listOf(roomWrite, exclusive, later).forEach { it.join() }
        assertEquals(listOf("room-start", "room-end", "exclusive", "later-room"), events)
        assertTrue(lock.withLock { true }) // all permits returned
    }
}
