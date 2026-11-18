package com.hienthai.fastowin.server.activity

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActivityTrackerTest {
    @Test fun idleReplacementAndShortConnectionObserveIndependentIntervals() = runTest {
        val store = RecordingRepository(); var now = 1_000L
        val tracker = ActivityTracker(store, { now })
        tracker.checkpoint()
        val old = UUID.randomUUID(); val replacement = UUID.randomUUID(); val user = UUID.randomUUID().toString()
        tracker.connected(old, user)
        now = 31_000; tracker.checkpoint()
        now = 32_000; tracker.connected(replacement, user)
        now = 33_000; tracker.disconnected(old)
        now = 34_000; tracker.checkpoint()
        assertEquals(33_000, store.observations.getValue(old).until)
        assertEquals(34_000, store.observations.getValue(replacement).until)
        now = 34_001; tracker.disconnected(replacement)
        val short = UUID.randomUUID(); tracker.connected(short, user); tracker.disconnected(short)
        tracker.checkpoint()
        assertEquals(32_000, store.observations.getValue(replacement).from)
        assertEquals(34_001, store.observations.getValue(replacement).until)
        assertEquals(34_001, store.observations.getValue(short).from)
        assertEquals(34_001, store.observations.getValue(short).until)
    }

    @Test fun socketEventsWriteNothingUntilTheCheckpointWritesEachConnectionOnce() = runTest {
        val store = RecordingRepository(); var now = 1_000L
        val tracker = ActivityTracker(store, { now })
        val ids = List(500) { UUID.randomUUID() }
        ids.forEach { tracker.connected(it, UUID.randomUUID().toString()) }
        now = 2_000; ids.take(100).forEach { tracker.disconnected(it) }
        assertEquals(0, store.checkpoints)
        now = 31_000; tracker.checkpoint()
        assertEquals(1, store.checkpoints); assertEquals(500, store.lastSize)
        assertEquals(2_000, store.observations.getValue(ids.first()).until)
        assertEquals(31_000, store.observations.getValue(ids.last()).until)
        now = 61_000; tracker.checkpoint()
        assertEquals(400, store.lastSize) // closed connections are flushed exactly once
    }

    @Test fun failedCheckpointDoesNotLetALaterConnectionBridgeOtherSockets() = runTest {
        val store = RecordingRepository(); var now = 1_000L
        val tracker = ActivityTracker(store, { now }); val early = UUID.randomUUID()
        tracker.connected(early, UUID.randomUUID().toString())
        now = 31_000; store.fail = true; tracker.checkpoint(); store.fail = false
        now = 32_000; tracker.connected(UUID.randomUUID(), UUID.randomUUID().toString())
        now = 40_000; tracker.checkpoint()
        assertEquals(40_000, store.observations.getValue(early).from)
        assertEquals(listOf(40_000L to 40_000L), store.windows.values.map { it.from to it.until })
    }

    @Test fun failureLongGapAndRestartNeverBridgeUnobservedTime() = runTest {
        val store = RecordingRepository(); var now = 1_000L
        val tracker = ActivityTracker(store, { now }); val id = UUID.randomUUID()
        tracker.connected(id, UUID.randomUUID().toString())
        now = 31_000; tracker.checkpoint()
        store.fail = true; now = 61_000; tracker.checkpoint()
        store.fail = false; now = 91_000; tracker.checkpoint()
        now = 152_000; tracker.checkpoint()
        ActivityTracker(store, { 200_000 }).checkpoint()
        assertEquals(listOf(1_000L to 31_000L, 91_000L to 91_000L, 152_000L to 152_000L, 200_000L to 200_000L), store.windows.values.map { it.from to it.until })
        assertEquals(listOf(1_000L to 31_000L, 91_000L to 91_000L, 152_000L to 152_000L), store.segments.values.map { it.from to it.until })
    }

    @Test fun lifecycleTicksIdleAccountsAndFlushesOnCancellationWithHourlyCleanup() = runTest {
        val store = RecordingRepository(); val tracker = ActivityTracker(store, { testScheduler.currentTime })
        val id = UUID.randomUUID(); tracker.connected(id, UUID.randomUUID().toString())
        val job = backgroundScope.launch { tracker.run() }; runCurrent()
        advanceTimeBy(30_001); runCurrent()
        assertEquals(30_000, store.observations.getValue(id).until)
        assertEquals(1, store.cleanups)
        job.cancelAndJoin()
        assertEquals(30_001, store.observations.getValue(id).until)
        assertEquals(1, store.cleanups)
    }

    @Test fun cancellationPropagatesAndUnavailableDefaultDoesNothing() = runTest {
        val tracker = ActivityTracker(object : ActivityRepository {
            override suspend fun checkpoint(checkpoint: ActivityCheckpoint) { throw CancellationException() }
        })
        assertFailsWith<CancellationException> { tracker.checkpoint() }
        ActivityTracker().connected(UUID.randomUUID(), UUID.randomUUID().toString())
        ActivityTracker().checkpoint()
    }

    private class RecordingRepository : ActivityRepository {
        val observations = linkedMapOf<UUID, ActivityObservation>()
        val segments = linkedMapOf<UUID, ActivityObservation>()
        val windows = linkedMapOf<UUID, ActivityWindow>()
        var fail = false; var cleanups = 0; var checkpoints = 0; var lastSize = 0
        override suspend fun checkpoint(checkpoint: ActivityCheckpoint) {
            if (fail) error("unavailable")
            checkpoints++; lastSize = checkpoint.observations.size
            checkpoint.observations.forEach { observations[it.connectionId] = it; segments[it.id] = it }
            windows[checkpoint.window.id] = checkpoint.window
            if (checkpoint.cleanup) cleanups++
        }
    }
}
