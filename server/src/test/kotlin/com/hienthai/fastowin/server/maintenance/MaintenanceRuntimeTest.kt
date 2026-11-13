package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import java.util.UUID
import kotlin.test.*

class MaintenanceRuntimeTest {

    @Test fun adminCannotTreatAnInProgressOwnedSampleAsTrustedZero() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val runtime = MaintenanceRuntime(GameEngine(), f.service, ownedWorkCount = {
            entered.complete(Unit); release.await(); 1
        })
        runtime.observe()
        f.service.update(f.owner, f.request().copy(warningMinutes = 0))
        val observing = async { runtime.observe() }
        try {
            entered.await()
            val unknown = f.service.read(f.owner).runtime
            assertFalse(unknown.available)
            assertNull(unknown.activeMatches)
            assertFalse(runtime.publicStatus().maintenance)
        } finally { release.complete(Unit) }
        observing.await()
        val confirmed = f.service.read(f.owner).runtime
        assertTrue(confirmed.available)
        assertEquals(1, confirmed.activeMatches)
    }


    @Test fun plannedMaintenanceWaitsForGlobalOwnedWorkAfterBlockingLegacyAdmission() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        val engine = GameEngine()
        var ownedWork = 1
        var samples = 0
        val runtime = MaintenanceRuntime(engine, f.service, ownedWorkCount = {
            assertFalse(engine.newIntentAllowed(), "Legacy admission must be blocked before trusted owned sampling")
            samples++
            ownedWork
        })
        runtime.refresh()
        assertEquals(0, samples)
        f.service.update(f.owner, f.request().copy(warningMinutes = 0, drainMinutes = 1))
        f.now += 120_000
        assertFalse(runtime.refresh().closeSockets)
        assertFalse(runtime.publicStatus().maintenance)
        assertEquals(MaintenanceState.DRAINING, runtime.publicStatus().state)
        assertEquals(0, engine.activeMaintenanceMatchCount())
        assertEquals(1, f.service.read(f.owner).runtime.activeMatches)
        ownedWork = 0
        assertTrue(runtime.refresh().closeSockets)
        assertTrue(runtime.publicStatus().maintenance)
    }

    @Test fun unavailableOwnedWorkNeverBecomesAnEmptyPlannedDrain() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        val engine = GameEngine()
        var unavailable = true
        val runtime = MaintenanceRuntime(engine, f.service, ownedWorkCount = {
            if (unavailable) throw java.sql.SQLException("Owned count unavailable")
            0
        })
        runtime.refresh()
        f.service.update(f.owner, f.request().copy(warningMinutes = 0))
        runtime.refresh()
        assertFalse(engine.newIntentAllowed())
        error(503) { runtime.publicStatus() }
        val unknown = f.service.read(f.owner).runtime
        assertFalse(unknown.available)
        assertNull(unknown.activeMatches)
        assertEquals(MaintenanceState.DRAINING, f.service.effectivePolicy().state)
        unavailable = false
        assertTrue(runtime.refresh().closeSockets)
        assertTrue(runtime.publicStatus().maintenance)
    }

    @Test fun announcingMaintenanceDoesNotStopOwnedAdmissionEarly() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        var samples = 0
        val engine = GameEngine()
        val runtime = MaintenanceRuntime(engine, f.service, ownedWorkCount = { samples++; 1 })
        runtime.refresh()
        f.service.update(f.owner, f.request())
        runtime.refresh()
        assertEquals(MaintenanceState.ANNOUNCING, runtime.publicStatus().state)
        assertTrue(engine.newIntentAllowed())
        assertEquals(0, samples)
    }

    @Test fun emergencyClosureRetainsNonzeroOwnedWorkObservation() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        val runtime = MaintenanceRuntime(GameEngine(), f.service, ownedWorkCount = { 2 })
        runtime.refresh()
        f.service.update(f.owner, f.request().copy(mode = MaintenanceMode.EMERGENCY))
        assertTrue(runtime.refresh().closeSockets)
        assertTrue(runtime.publicStatus().maintenance)
        assertEquals(2, f.service.read(f.owner).runtime.activeMatches)
    }

    @Test fun environmentEmergencyStopsOwnedAdmissionWithoutWaitingForWorkToFinish() = runTest {
        var samples = 0
        val engine = GameEngine()
        val runtime = MaintenanceRuntime(engine, environmentStatus = { ServiceStatusResponse(true) },
            ownedWorkCount = { assertFalse(engine.newIntentAllowed()); samples++; 2 })
        assertTrue(runtime.refresh().closeSockets)
        assertEquals(1, samples)
        assertTrue(runtime.publicStatus().maintenance)
    }

    @Test fun nonSqlPolicyReadFailureAlsoBlocksNewIntent() = runTest {
        val f = MaintenanceFixture(); f.initialize(); var fail = false
        val service = MaintenanceService(object : MaintenanceRepository {
            override suspend fun <T> observationTransaction(block: MaintenanceObservationTransaction.() -> T): T = transaction { block() }
            override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T {
                if (fail) throw IllegalStateException("corrupt private snapshot")
                return f.repository.transaction(block)
            }
        })
        val engine = GameEngine(); val runtime = MaintenanceRuntime(engine, service)
        runtime.refresh(); assertTrue(engine.newIntentAllowed())
        fail = true; runtime.refresh()
        assertFalse(engine.newIntentAllowed()); error(503) { runtime.publicStatus() }
    }
    @Test fun overduePolicyCommittedBetweenReadsCannotTreatActiveEngineZeroAsEmptyDrain() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        val engine = GameEngine()
        val host = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host"))
        val guest = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "guest"))
        var reads = 0
        val repository = object : MaintenanceRepository {
            override suspend fun <T> observationTransaction(block: MaintenanceObservationTransaction.() -> T): T = transaction { block() }
            override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T {
                if (++reads == 2) {
                    val room = engine.handle(host.playerId, ClientMessage.CreateRoom("race", "", ProtocolGameMode.ORDER))
                        .map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
                    engine.handle(guest.playerId, ClientMessage.JoinRoom(room, ""))
                    engine.handle(host.playerId, ClientMessage.SetReady(room, true))
                    engine.handle(guest.playerId, ClientMessage.SetReady(room, true))
                    f.service.update(f.owner, f.request().copy(warningMinutes = 0))
                    f.now += 16 * 60_000
                }
                return f.repository.transaction(block)
            }
        }
        val service = MaintenanceService(repository, f.admin, { f.now })
        MaintenanceRuntime(engine, service).refresh()
        assertEquals(MaintenanceState.DRAINING, service.effectivePolicy().state)
        assertEquals(1, engine.activeMaintenanceMatchCount())
    }
    @Test fun overduePlannedDrainWaitsForLiveMatchAndItsFailedSettlement() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        var failSettlement = true
        val results = mutableListOf<CompletedMatch>()
        val rooms = InMemoryActiveRoomRepository()
        val engine = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository {
            if (failSettlement) throw java.sql.SQLException("synthetic settlement failure")
            results += it
        })
        val runtime = MaintenanceRuntime(engine, f.service)
        runtime.refresh()
        val host = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host"))
        val guest = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "guest"))
        val room = engine.handle(host.playerId, ClientMessage.CreateRoom("drain", "", ProtocolGameMode.ORDER))
            .map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        engine.handle(guest.playerId, ClientMessage.JoinRoom(room, ""))
        engine.handle(host.playerId, ClientMessage.SetReady(room, true))
        engine.handle(guest.playerId, ClientMessage.SetReady(room, true))
        f.service.update(f.owner, f.request().copy(warningMinutes = 0, drainMinutes = 1))
        f.now += 120_000
        assertFalse(runtime.refresh().closeSockets)
        assertEquals(MaintenanceState.DRAINING, runtime.publicStatus().state)
        assertFalse(engine.newIntentAllowed())
        assertEquals(1, engine.activeMaintenanceMatchCount())
        assertFalse(rooms.loadAll().single().maintenanceAborted)
        assertFailsWith<MaintenanceAdmissionException> {
            engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "new player"))
        }
        for (number in 1..GAME_NUMBER_COUNT) {
            engine.handle(host.playerId, ClientMessage.SelectNumber(room, number, "n$number"))
        }
        assertNotNull(rooms.loadAll().single().pendingCompletedMatch)
        assertFalse(runtime.refresh().closeSockets)
        assertEquals(MaintenanceState.DRAINING, runtime.publicStatus().state)
        assertEquals(1, engine.activeMaintenanceMatchCount())
        failSettlement = false
        runtime.refresh() // Retry the admitted result before observing empty drain.
        assertEquals(1, results.size)
        assertEquals(host.playerId, results.single().winnerPlayerId)
        assertEquals(0, engine.activeMaintenanceMatchCount())
        assertTrue(runtime.refresh().closeSockets)
        assertEquals(MaintenanceState.MAINTENANCE, runtime.publicStatus().state)
        runtime.refresh()
        assertEquals(1, results.size)
        assertFalse(rooms.loadAll().single().maintenanceAborted)
    }
    @Test fun refreshAppliesBlockBeforeEmptyDrainAndKeepsClosureOnReadFailure() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        var failing = false
        val repository = object : MaintenanceRepository {
            override suspend fun <T> observationTransaction(block: MaintenanceObservationTransaction.() -> T): T = transaction { block() }
            override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T {
                if (failing) throw java.sql.SQLException("private failure")
                return f.repository.transaction(block)
            }
        }
        val service = MaintenanceService(repository, f.admin, { f.now })
        val engine = GameEngine()
        val player = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "player"))
        val runtime = MaintenanceRuntime(engine, service)
        runtime.refresh()
        f.service.update(f.owner, f.request().copy(warningMinutes = 0))
        assertTrue(runtime.refresh().closeSockets)
        assertEquals(MaintenanceState.MAINTENANCE, service.publicStatus().state)
        assertFalse(engine.admitsNewIntent())
        failing = true
        runtime.refresh()
        assertFailsWith<MaintenanceAdmissionException> { engine.connectAccount(AuthenticatedAccount(UUID.fromString(player.playerId), "player")) }
        error(503) { runtime.publicStatus() }
    }

    @Test fun unavailableStartupClosesButExplicitEnvironmentOnlyModeRemainsUsable() = runTest {
        val engine = GameEngine()
        val unavailable = MaintenanceRuntime(engine, MaintenanceService(UnavailableMaintenanceRepository))
        unavailable.refresh()
        assertFalse(engine.admitsNewIntent())
        error(503) { unavailable.publicStatus() }
        val memory = GameEngine()
        val runtime = MaintenanceRuntime(memory, environmentStatus = { ServiceStatusResponse(maintenance = false) })
        runtime.refresh()
        assertTrue(memory.admitsNewIntent())
        assertFalse(runtime.publicStatus().maintenance)
    }

    @Test fun cancellationRefundRetryCannotGrantTwice() = runTest {
        val ledger = mutableMapOf<String, Int>()
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun applyWalletTransaction(playerId: String, sourceType: String, sourceId: String,
                goldDelta: Int, gemsDelta: Int, xpDelta: Int): WalletMutationStatus {
                val key = "$playerId:$sourceType:$sourceId"
                return if (ledger.putIfAbsent(key, goldDelta) == null) WalletMutationStatus.APPLIED else WalletMutationStatus.DUPLICATE
            }
        }
        val repository = InMemoryTournamentRepository()
        val id = UUID.randomUUID().toString(); val player = UUID.randomUUID().toString()
        val cancelled = TournamentSnapshot(id, "cup", player, ProtocolGameMode.ORDER, TournamentPhase.CANCELLED,
            maxPlayers = 4, entryFee = 100, prizePool = 100,
            players = listOf(TournamentPlayerSnapshot(player, "player")), createdAtEpochMillis = 0)
        repository.cancelForMaintenance(cancelled, profiles)
        repository.cancelForMaintenance(cancelled, profiles)
        assertEquals(listOf(100), ledger.values.toList())
        assertNull(repository.loadRecent(player, 10).single().championPlayerId)
        assertTrue(repository.loadActive().isEmpty())
    }
}
