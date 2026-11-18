package com.hienthai.fastowin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

class PasswordHashingDispatcherTest {
    @Test
    fun `a login burst never occupies more than half the cores and game work keeps running`() = runBlocking {
        val running = AtomicInteger(); val peak = AtomicInteger()
        val burst = (1..passwordHashingParallelism * 6).map {
            async(PasswordHashing) {
                val now = running.incrementAndGet(); peak.accumulateAndGet(now, ::maxOf)
                Thread.sleep(50) // Blocks like PBKDF2: a whole thread for the duration.
                running.decrementAndGet()
            }
        }
        // A short game-side task on the shared Default pool must not wait for the burst.
        val started = System.nanoTime()
        withContext(Dispatchers.Default) { launch { }.join() }
        val waitedMillis = (System.nanoTime() - started) / 1_000_000
        burst.awaitAll()
        assertTrue(peak.get() <= passwordHashingParallelism, "peak ${peak.get()} > $passwordHashingParallelism")
        assertTrue(waitedMillis < 50, "game work waited $waitedMillis ms behind password hashing")
    }
}
