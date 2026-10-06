package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class MaintenanceRuntimeTest {
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
    @Test fun policyCommittedBetweenReadsCannotTreatActiveEngineZeroAsEmptyDrain() = runTest {
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
                }
                return f.repository.transaction(block)
            }
        }
        val service = MaintenanceService(repository, f.admin, { f.now })
        MaintenanceRuntime(engine, service).refresh()
        assertEquals(MaintenanceState.DRAINING, service.effectivePolicy().state)
        assertEquals(1, engine.activeMaintenanceMatchCount())
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
