package com.hienthai.fastowin.server.ownership

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertFails

class RailwayCapabilityTest {
    @Test
    fun `pause proof requires a pending operation instead of a response or broken connection`() {
        requirePending(CompletableFuture<Int>(), 1)
        assertFails { requirePending(CompletableFuture.completedFuture(42), 1) }
        assertFails { requirePending(CompletableFuture.failedFuture<Int>(java.io.EOFException()), 1) }
    }
}
