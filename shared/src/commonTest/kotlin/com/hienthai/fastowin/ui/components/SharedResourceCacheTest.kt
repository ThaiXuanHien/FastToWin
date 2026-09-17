package com.hienthai.fastowin.ui.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedResourceCacheTest {
    @Test
    fun `successful shared load is cached after the original waiter is cancelled`() = runTest {
        val loadStarted = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<String>()
        var loadCount = 0
        val cache = SharedResourceCache<String, String, String>(
            scope = backgroundScope,
            maxEntries = 4,
            loader = {
                loadCount += 1
                loadStarted.complete(Unit)
                finishLoad.await()
            }
        )

        val originalWaiter = async { cache.load("avatar?v=1", "avatar") }
        loadStarted.await()
        originalWaiter.cancelAndJoin()
        finishLoad.complete("bitmap")
        advanceUntilIdle()

        assertEquals("bitmap", cache.peekExact("avatar?v=1"))
        assertEquals("bitmap", cache.load("avatar?v=1", "avatar"))
        assertEquals(1, loadCount)
    }
}
