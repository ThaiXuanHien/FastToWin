package com.hienthai.fastowin.ui.components

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UgcAvatarCacheTest {
    @Test fun `a fresh-only avatar lookup cannot reuse removed bytes from the previous revision`() = runTest {
        val cache = SharedResourceCache<String, String, String>(backgroundScope, 4) { key ->
            if (key == "old-revision") "Removed image" else "New image"
        }
        cache.load("old-revision", "player")
        assertEquals("Removed image", cache.peekForDisplay("new-revision", "player", allowStale = true))
        assertNull(cache.peekForDisplay("new-revision", "player", allowStale = false))
        cache.load("new-revision", "player")
        assertEquals("New image", cache.peekForDisplay("new-revision", "player", allowStale = false))
    }
}
