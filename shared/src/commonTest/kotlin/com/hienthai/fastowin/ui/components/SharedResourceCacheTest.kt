package com.hienthai.fastowin.ui.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
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
        runCurrent()

        assertEquals("bitmap", cache.peekExact("avatar?v=1"))
        assertEquals("bitmap", cache.load("avatar?v=1", "avatar"))
        assertEquals(1, loadCount)
    }

    @Test
    fun `older revision completing last cannot replace the latest source fallback`() = runTest {
        val finishes = mapOf(
            "avatar?v=1" to CompletableDeferred<String>(),
            "avatar?v=2" to CompletableDeferred<String>()
        )
        val cache = SharedResourceCache<String, String, String>(
            scope = backgroundScope,
            maxEntries = 4,
            loader = { key -> finishes.getValue(key).await() }
        )

        val oldLoad = async { cache.load("avatar?v=1", "avatar") }
        runCurrent()
        val newLoad = async { cache.load("avatar?v=2", "avatar") }
        runCurrent()

        finishes.getValue("avatar?v=2").complete("new bitmap")
        runCurrent()
        assertEquals("new bitmap", newLoad.await())

        finishes.getValue("avatar?v=1").complete("old bitmap")
        runCurrent()
        assertEquals("old bitmap", oldLoad.await())
        assertEquals("old bitmap", cache.peekExact("avatar?v=1"))
        assertEquals("new bitmap", cache.peekLatest("avatar"))
    }
}
