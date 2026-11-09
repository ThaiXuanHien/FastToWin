package com.hienthai.fastowin.server.ownership

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class RoomOwnershipDatabaseTimingsTest {
    @Test
    fun `records bounded percentile upper bounds without reexecuting nullable operations`() {
        var now = 0L
        val timings = RoomOwnershipDatabaseTimings { now }
        var calls = 0
        repeat(20) { index ->
            assertEquals(null, timings.measure(OwnershipDatabaseStage.POOL_ACQUIRE) {
                calls++
                now += (index + 1) * 1_000_000L
                null
            })
        }
        assertEquals(20, calls)
        val exported = timings.render()
        assertEquals(20.0, value(exported, "pool_acquire", "count"))
        assertEquals(0.210, value(exported, "pool_acquire", "sum"), 0.000001)
        assertEquals(0.020, value(exported, "pool_acquire", "max"))
        assertEquals(0.025, value(exported, "pool_acquire", "p95_upper_bound"))
        assertEquals(0.0, value(exported, "pool_acquire", "failures_total"))
    }

    @Test
    fun `preserves the original exception while exporting only fixed numeric fields`() {
        var now = 0L
        val timings = RoomOwnershipDatabaseTimings { now }
        val original = IllegalStateException("jdbc:postgresql://private/secret password=private-token")
        val observed = assertFailsWith<IllegalStateException> {
            timings.measure(OwnershipDatabaseStage.COMMIT) {
                now += 75_000_000
                throw original
            }
        }
        assertSame(original, observed)
        val exported = timings.render()
        assertEquals(1.0, value(exported, "commit", "count"))
        assertEquals(1.0, value(exported, "commit", "failures_total"))
        assertEquals(0.075, value(exported, "commit", "max"))
        assertEquals(0.100, value(exported, "commit", "p95_upper_bound"))
        listOf("jdbc:", "private", "password", "token").forEach { assertFalse(it in exported) }
    }

    private fun value(text: String, stage: String, suffix: String): Double =
        text.lineSequence().first { it.startsWith("fasttowin_room_ownership_db_${stage}_seconds_$suffix ") }
            .substringAfter(' ').toDouble()
}
