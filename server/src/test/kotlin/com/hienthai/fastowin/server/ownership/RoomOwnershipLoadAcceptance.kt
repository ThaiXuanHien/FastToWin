package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.protocol.GAME_NUMBER_COUNT
import com.hienthai.fastowin.protocol.GameSnapshot
import com.hienthai.fastowin.protocol.RoomPhase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.random.Random

/** Durations include retries/reconnection inside select; failed commands fail the run. */
@Serializable
internal data class LoadReceipt(
    val seed: Int,
    val backendCount: Int,
    val concurrentRooms: Int,
    val configuredDurationSeconds: Int,
    val configuredSelectionsPerRoomPerSecond: Int,
    val fixtureRateLimits: String,
    val roomSetupSeconds: Double,
    val elapsedSeconds: Double,
    val selectionCount: Long,
    val selectionsByRoomSlot: List<Long>,
    val actualSelectionsPerSecond: Double,
    val completedMatches: Long,
    val commandP95Millis: Double,
    val commandMaxMillis: Double,
    val commandAttempts: Long,
    val commandRetries: Long,
    val minimumSelectionSpacingMillis: Long,
    val maxScheduledLatenessMillis: Double,
    val scheduledToAckP95Millis: Double,
    val recoveryMillis: List<Long>,
    val recoveryP95Millis: Double?,
    val maxQueuedEventSeconds: Double,
    val maxPostgresPoolSaturation: Double,
    val queuedEventP95Seconds: Double,
    val postgresPoolP95Saturation: Double,
    val resourceSamples: List<LoadResourceSample>,
    val resourceSampleCount: Int,
    val fullAcceptanceProfile: Boolean,
    val finalBackendMetrics: List<String>
)

@Serializable
internal data class LoadResourceSample(val elapsedSeconds: Double, val queuedEventSeconds: Double, val postgresPoolSaturation: Double)

/**
 * One sequential producer per room offers five commands a second across both players.
 * The monotonic schedule catches up after stalls rather than silently reducing offered load.
 * The fixture owns process/HTTP/JDBC resources; this module owns room/socket lifecycles.
 */
internal suspend fun runRoomOwnershipLoad(fixture: RoomAcceptanceFixture): LoadReceipt = coroutineScope {
    require(fixture.backendCount in 1..2)
    require(fixture.roomCount > 0 && fixture.durationSeconds > 0)
    val accounts = fixture.accounts()
    require(accounts.size >= fixture.roomCount * 2) { "Each room needs two distinct fixture accounts" }
    require(accounts.take(fixture.roomCount * 2).map { it.userId }.distinct().size == fixture.roomCount * 2) {
        "Fixture accounts must be distinct across room slots"
    }
    fixture.resetRooms()

    val slots = List(fixture.roomCount) { LoadRoomSlot(it) }
    // Seed each room separately so coroutine scheduling cannot change the sender stream.
    val randoms = slots.map { Random(fixture.seed xor (it.index * 0x45d9f3b)) }
    val commandMillis = Collections.synchronizedList(mutableListOf<Double>())
    val scheduledToAckMillis = Collections.synchronizedList(mutableListOf<Double>())
    val recoveries = mutableListOf<Long>()
    val completedMatches = AtomicLong()
    var maxBacklog = 0.0
    var maxSaturation = 0.0
    var sampleCount = 0
    var measurementStarted = 0L
    val resourceSamples = mutableListOf<LoadResourceSample>()

    suspend fun sampleResources() {
        val backlog = fixture.sampleBacklog()
        val saturation = fixture.poolSaturation()
        check(backlog.isFinite() && backlog >= 0) { "Invalid queued-event sample: $backlog" }
        check(saturation.isFinite() && saturation in 0.0..1.0) { "Invalid PostgreSQL saturation sample: $saturation" }
        maxBacklog = maxOf(maxBacklog, backlog)
        maxSaturation = maxOf(maxSaturation, saturation)
        sampleCount++
        val elapsed = if (measurementStarted == 0L) 0.0 else (System.nanoTime() - measurementStarted).toDouble() / NANOS_PER_SECOND
        resourceSamples += LoadResourceSample(elapsed, backlog, saturation)
        if (sampleCount % 60 == 0) println("Load progress: ${fixture.backendCount} backend(s), ${elapsed.toInt()}s, ${slots.sumOf { it.selections }} selections, ${recoveries.size} recoveries")
    }

    var sampler: kotlinx.coroutines.Job? = null
    var faultJob: kotlinx.coroutines.Job? = null
    var primaryFailure: Throwable? = null
    try {
        // Do not start the measurement until all twenty rooms are playing.
        val setupStarted = System.nanoTime()
        coroutineScope {
            slots.map { slot -> async { slot.attach(fixture.createMatch(slot.index)) } }.awaitAll()
        }
        val roomIds = slots.map { checkNotNull(it.match).roomId }
        check(roomIds.distinct().size == slots.size) { "Room slots must be independent" }
        sampleResources()
        val started = System.nanoTime()
        measurementStarted = started
        val roomSetupSeconds = (started - setupStarted).toDouble() / NANOS_PER_SECOND
        val deadline = started + fixture.durationSeconds.toLong() * NANOS_PER_SECOND
        sampler = launch {
            while (System.nanoTime() < deadline) {
                delay(1_000)
                sampleResources()
            }
        }
        faultJob = launch {
            if (fixture.backendCount == 2) {
                for ((faultIndex, offset) in OWNER_STOP_SECONDS.withIndex()) {
                    if (offset >= fixture.durationSeconds) break
                    delayUntil(started + offset * NANOS_PER_SECOND)
                    val selected = slots[Math.floorMod(fixture.seed + faultIndex, slots.size)]
                    selected.mutex.withLock {
                        var elapsed: Long? = null
                        var lastFailure: Throwable? = null
                        repeat(3) {
                            if (elapsed != null) return@repeat
                            try {
                                val match = checkNotNull(selected.match)
                                elapsed = match.withFaultLock {
                                    fixture.faultOwner(match, hardKill = false, matchLocked = true)
                                }
                            }
                            catch (failure: Exception) {
                                currentCoroutineContext().ensureActive()
                                lastFailure = failure
                                delay(100)
                            }
                        }
                        check(elapsed != null) { "Owner-stop fault did not reconnect after retries: ${lastFailure?.javaClass?.simpleName}" }
                        val recovery = checkNotNull(elapsed)
                        check(recovery in 0..RECOVERY_LIMIT_MILLIS) {
                            "Independent owner stop ${faultIndex + 1} exceeded 25 seconds: ${recovery}ms"
                        }
                        recoveries += recovery
                    }
                }
            }
        }
        withTimeout(fixture.durationSeconds.toLong() * 1_000 + MAX_MEASUREMENT_TAIL_MILLIS) {
            slots.map { slot ->
                async {
                    var due = started + slot.index * SELECTION_INTERVAL_NANOS / slots.size
                    var nextSendNotBefore = due
                    while (due < deadline) {
                        delayUntil(due)
                        // A command already offered before the deadline must be acknowledged.
                        slot.mutex.withLock {
                            // Drain recovery queues without bursting through per-player ingress caps.
                            delayUntil(nextSendNotBefore)
                            val sendStarted = System.nanoTime()
                            nextSendNotBefore = sendStarted + MINIMUM_SELECTION_SPACING_MILLIS * 1_000_000
                            val latenessMillis = ((sendStarted - due).toDouble() / 1_000_000).coerceAtLeast(0.0)
                            slot.maxScheduledLatenessMillis = maxOf(slot.maxScheduledLatenessMillis, latenessMillis)
                            val match = checkNotNull(slot.match)
                            val number = slot.nextNumber
                            val requestId = "load-${fixture.seed}-${slot.index}-${slot.matchOrdinal}-$number"
                            val playerIndex = randoms[slot.index].nextInt(2)
                        // The producer waits for the actor's committed ACK; peer snapshots are
                        // sampled every fifth command in the match harness to prevent one slow
                        // delivery stream from reducing the offered five-per-second load.
                        val receipt = match.select(number, requestId, playerIndex, verifyPeer = number % 5 == 0)
                            check(receipt.durationMillis.isFinite() && receipt.durationMillis >= 0) {
                                "Invalid command duration for slot ${slot.index}"
                            }
                            check(receipt.attempts >= 1) { "A selection has no recorded send attempt" }
                            verifyLoadSelection(match, receipt.snapshot, number, slot.lastSequence)
                            check(receipt.snapshot.roomId == slot.roomId && receipt.snapshot.matchId == slot.matchId) {
                                "A reconnect changed the original match identity"
                            }
                            val board = receipt.snapshot.numbers
                            check(board.sorted() == (1..GAME_NUMBER_COUNT).toList()) { "Match board is invalid" }
                            check(slot.board == null || slot.board == board) { "Committed match board changed" }
                            slot.board = board
                            val playerIds = receipt.snapshot.players.map { it.id }
                            check(slot.playerIds == null || slot.playerIds == playerIds) { "Player identities changed" }
                            slot.playerIds = playerIds
                            slot.correctSelections[playerIndex]++
                            receipt.snapshot.players.forEachIndexed { index, player ->
                                check(player.correctSelections == slot.correctSelections[index] && player.score == slot.correctSelections[index] * 10) {
                                    "Selection credited to the wrong player or score changed"
                                }
                            }
                            commandMillis += receipt.durationMillis
                            scheduledToAckMillis += latenessMillis + receipt.durationMillis
                            slot.attempts += receipt.attempts
                            slot.selections++
                            slot.nextNumber++
                            slot.lastSequence = receipt.snapshot.sequence
                            if (number == GAME_NUMBER_COUNT) {
                                match.verifyEconomy()
                                match.close()
                                slot.match = null
                                completedMatches.incrementAndGet()
                                slot.matchOrdinal++
                                // Keep twenty live slots until all scheduled selections are offered.
                                if (due + SELECTION_INTERVAL_NANOS < deadline) {
                                    slot.attach(fixture.createMatch(slot.index))
                                    slot.nextNumber = 1
                                    slot.lastSequence = -1
                                    slot.correctSelections.fill(0)
                                }
                            }
                        }
                        due += SELECTION_INTERVAL_NANOS
                    }
                }
            }.awaitAll()
        }
        faultJob?.join()
        sampler?.cancelAndJoin()
        sampler = null
        // Include the tail after the final ACK and independently verify unfinished matches.
        sampleResources()
        slots.forEach { slot -> slot.match?.verifyEconomy() }
        val elapsedSeconds = (System.nanoTime() - started).toDouble() / NANOS_PER_SECOND
        val latencies = synchronized(commandMillis) { commandMillis.toList().sorted() }
        val scheduledLatencies = synchronized(scheduledToAckMillis) { scheduledToAckMillis.toList().sorted() }
        val selections = slots.map { it.selections }
        check(latencies.isNotEmpty() && selections.all { it > 0 }) { "Load did not exercise every room" }
        check(latencies.size.toLong() == selections.sum()) { "Missing command latency samples" }
        // Exact offered count detects prematurely ended/silently skipped producers.
        val expectedPerRoom = fixture.durationSeconds.toLong() * SELECTIONS_PER_SECOND
        check(selections.all { it == expectedPerRoom }) { "Load lost scheduled commands: $selections" }
        val expectedStops = if (fixture.backendCount == 2) OWNER_STOP_SECONDS.count { it < fixture.durationSeconds } else 0
        check(recoveries.size == expectedStops) { "An owner-stop fault was not exercised" }
        val actualRate = selections.sum() / elapsedSeconds
        val fullProfile = fixture.roomCount == 20 && fixture.durationSeconds >= 600 &&
            (fixture.backendCount == 1 || recoveries.size >= 3)
        if (fullProfile) {
            check(elapsedSeconds <= fixture.durationSeconds * 1.05 + 1.0) {
                "Load exceeded the five-percent measurement tail: ${elapsedSeconds}s"
            }
            check(actualRate >= 0.95 * slots.size * SELECTIONS_PER_SECOND) {
                "Load did not sustain 95% of configured throughput: $actualRate selections/s"
            }
        }
        LoadReceipt(
            seed = fixture.seed,
            backendCount = fixture.backendCount,
            concurrentRooms = slots.size,
            configuredDurationSeconds = fixture.durationSeconds,
            configuredSelectionsPerRoomPerSecond = SELECTIONS_PER_SECOND,
            fixtureRateLimits = fixture.loadRateLimitsDescription,
            roomSetupSeconds = roomSetupSeconds,
            elapsedSeconds = elapsedSeconds,
            selectionCount = selections.sum(),
            selectionsByRoomSlot = selections,
            actualSelectionsPerSecond = actualRate,
            completedMatches = completedMatches.get(),
            commandP95Millis = percentile95(latencies),
            commandMaxMillis = latencies.last(),
            commandAttempts = slots.sumOf { it.attempts },
            commandRetries = slots.sumOf { it.attempts } - selections.sum(),
            minimumSelectionSpacingMillis = MINIMUM_SELECTION_SPACING_MILLIS,
            maxScheduledLatenessMillis = slots.maxOf { it.maxScheduledLatenessMillis },
            scheduledToAckP95Millis = percentile95(scheduledLatencies),
            recoveryMillis = recoveries.toList(),
            recoveryP95Millis = recoveries.takeIf { it.isNotEmpty() }?.let { percentile95(it.map(Long::toDouble).sorted()) },
            maxQueuedEventSeconds = maxBacklog,
            maxPostgresPoolSaturation = maxSaturation,
            queuedEventP95Seconds = percentile95(resourceSamples.map { it.queuedEventSeconds }.sorted()),
            postgresPoolP95Saturation = percentile95(resourceSamples.map { it.postgresPoolSaturation }.sorted()),
            resourceSamples = resourceSamples.toList(),
            resourceSampleCount = sampleCount,
            fullAcceptanceProfile = fullProfile,
            finalBackendMetrics = fixture.metrics()
        )
    } catch (failure: Throwable) {
        primaryFailure = failure
        fixture.captureDiagnostics()
        throw failure
    } finally {
        withContext(NonCancellable) {
            sampler?.cancelAndJoin()
            faultJob?.cancelAndJoin()
            var cleanupFailure: Throwable? = null
            slots.mapNotNull { it.match }.forEach { match ->
                try {
                    match.close()
                } catch (failure: Throwable) {
                    if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
                }
            }
            cleanupFailure?.let { failure ->
                if (primaryFailure == null) throw failure else primaryFailure?.addSuppressed(failure)
            }
        }
    }
}

private class LoadRoomSlot(val index: Int) {
    val mutex = Mutex()
    var match: AcceptanceMatch? = null
    var nextNumber = 1
    var lastSequence = -1L
    var matchOrdinal = 0
    var selections = 0L
    var attempts = 0L
    var maxScheduledLatenessMillis = 0.0
    var roomId: String? = null
    var matchId: String? = null
    var board: List<Int>? = null
    var playerIds: List<String>? = null
    val correctSelections = IntArray(2)

    fun attach(created: AcceptanceMatch) {
        match = created
        check(created.lastSnapshot.phase == RoomPhase.PLAYING && created.lastSnapshot.selectedNumbers.isEmpty()) {
            "A new load match must start playing with no selections"
        }
        roomId = created.roomId
        matchId = created.matchId
        board = created.lastSnapshot.numbers.toList()
        playerIds = created.lastSnapshot.players.map { it.id }
    }
}

private fun verifyLoadSelection(match: AcceptanceMatch, snapshot: GameSnapshot, number: Int, previousSequence: Long) {
    check(snapshot.roomId == match.roomId && snapshot.matchId == match.matchId) { "Selection moved to another match" }
    check(snapshot.sequence > previousSequence) { "Selection sequence did not advance" }
    val expected = (1..number).toList()
    check(snapshot.selectedNumbers == expected) { "Lost, duplicated or reordered selection at number $number" }
    check(snapshot.players.size == 2 && snapshot.players.map { it.id }.distinct().size == 2) { "Room membership changed" }
    check(snapshot.players.all { it.selectedNumbers == expected }) { "Players disagree on accepted selections" }
    check(snapshot.currentTarget == number + 1 && snapshot.players.all { it.currentTarget == number + 1 }) {
        "Target did not advance exactly once"
    }
    check(snapshot.players.sumOf { it.correctSelections } == number && snapshot.players.sumOf { it.wrongSelections } == 0) {
        "Selection accounting changed at number $number"
    }
    check(snapshot.phase == if (number == GAME_NUMBER_COUNT) RoomPhase.FINISHED else RoomPhase.PLAYING) {
        "Unexpected room phase at selection $number: ${snapshot.phase}"
    }
}

private suspend fun delayUntil(deadlineNanos: Long) {
    while (true) {
        val remaining = deadlineNanos - System.nanoTime()
        if (remaining <= 0) return
        delay((remaining / 1_000_000).coerceAtLeast(1))
    }
}

private fun percentile95(sortedValues: List<Double>): Double = sortedValues[ceil(sortedValues.size * 0.95).toInt() - 1]

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val SELECTIONS_PER_SECOND = 5
private const val SELECTION_INTERVAL_NANOS = NANOS_PER_SECOND / SELECTIONS_PER_SECOND
private const val RECOVERY_LIMIT_MILLIS = 25_000L
private const val MINIMUM_SELECTION_SPACING_MILLIS = 55L
private const val MAX_MEASUREMENT_TAIL_MILLIS = 30_000L
private val OWNER_STOP_SECONDS = listOf(60L, 180L, 360L)
