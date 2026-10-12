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
        assertEquals(32_000, store.observations.getValue(replacement).from)
        assertEquals(34_001, store.observations.getValue(replacement).until)
        val short = UUID.randomUUID(); tracker.connected(short, user); tracker.disconnected(short)
        assertEquals(34_001, store.observations.getValue(short).from)
        assertEquals(34_001, store.observations.getValue(short).until)
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
        var fail = false; var cleanups = 0
        override suspend fun checkpoint(checkpoint: ActivityCheckpoint) {
            if (fail) error("unavailable")
            checkpoint.observations.forEach { observations[it.connectionId] = it; segments[it.id] = it }
            windows[checkpoint.window.id] = checkpoint.window
            if (checkpoint.cleanup) cleanups++
        }
    }
}
