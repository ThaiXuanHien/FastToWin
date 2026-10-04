package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.util.UUID
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceRecoveryTest {
    private val drain = MaintenancePolicy(MaintenanceState.DRAINING)
    private val closed = MaintenancePolicy(MaintenanceState.MAINTENANCE)

    @Test fun stalledSettlementTimesOutWithoutKillingRuntimeRetryWorker() = runTest {
        val rooms = InMemoryActiveRoomRepository(); var fail = true
        val engine = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository {
            if (fail) error("unavailable") else awaitCancellation()
        })
        val (host, _, room) = playing(engine); finish(engine, host, room); fail = false
        val runtime = MaintenanceRuntime(engine)
        val worker = backgroundScope.launchMaintenanceWorkers(runtime, {}, StandardTestDispatcher(testScheduler))
        runCurrent(); val children = worker.children.toList(); advanceTimeBy(5500); runCurrent()
        assertTrue(children.all { it.isActive }); assertEquals(1, engine.activeMaintenanceMatchCount())
        assertNotNull(rooms.loadAll().single().pendingCompletedMatch)
        worker.cancelAndJoin()
    }

    @Test fun tournamentResultSurvivesBothExitsAndRestoresProgressionOnce() = runTest {
        val rooms = InMemoryActiveRoomRepository(); val tournaments = InMemoryTournamentRepository()
        val original = GameEngine(activeRoomRepository = rooms)
        val (host, guest, room) = playing(original)
        tournaments.save(finalTournament(UUID.randomUUID().toString(), rooms.loadAll().single()))
        val failing = GameEngine(activeRoomRepository = rooms, tournamentRepository = tournaments,
            matchResultRepository = MatchResultRepository { error("unavailable") })
        failing.connectAccount(AuthenticatedAccount(UUID.fromString(host), "host")); finish(failing, host, room)
        failing.handle(host, ClientMessage.LeaveRoom(room)); failing.handle(guest, ClientMessage.LeaveRoom(room))
        assertNotNull(rooms.loadAll().single().pendingCompletedMatch)
        val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = rooms, tournamentRepository = tournaments, matchResultRepository = MatchResultRepository { results += it })
        restored.applyMaintenancePolicy(closed); restored.retryMaintenanceSettlements(); restored.retryMaintenanceSettlements()
        assertEquals(1, results.size); assertEquals(host, tournaments.loadRecent(host, 10).single().championPlayerId)
        assertTrue(rooms.loadAll().isEmpty()); assertEquals(0, restored.activeMaintenanceMatchCount())
    }

    @Test fun readyStartRechecksFenceAfterSuspendedQuotaConsumption() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val quotas = InMemoryPlayQuotaRepository()
        val engine = GameEngine(playQuotaRepository = object : PlayQuotaRepository by quotas {
            override suspend fun consumeMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption {
                entered.complete(Unit); release.await(); return quotas.consumeMatch(userIds, matchId, nowMillis)
            }
        })
        val host = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host")).playerId
        val guest = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "guest")).playerId
        val room = engine.handle(host, ClientMessage.CreateRoom("room", "", ProtocolGameMode.ORDER)).map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        engine.handle(guest, ClientMessage.JoinRoom(room, "")); engine.handle(host, ClientMessage.SetReady(room, true))
        val start = async { engine.handle(guest, ClientMessage.SetReady(room, true)) }
        entered.await(); engine.publishMaintenancePolicy(drain); release.complete(Unit)
        assertEquals("MAINTENANCE", start.await().errorCode()); assertEquals(0, engine.activeMaintenanceMatchCount())
    }

    @Test fun ownedWorkersObserveFenceAndCancelTogether() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        val blocked = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var hang = false; var reads = 0
        val service = MaintenanceService(object : MaintenanceRepository {
            override suspend fun <T> transaction(block: MaintenanceTransaction.() -> T): T {
                reads++
                if (hang) { blocked.complete(Unit); release.await() }
                return f.repository.transaction(block)
            }
        })
        val engine = GameEngine()
        val runtime = MaintenanceRuntime(engine, service, monotonicMillis = { testScheduler.currentTime })
        runtime.observe(); hang = true
        val job = backgroundScope.launchMaintenanceWorkers(runtime, {}, StandardTestDispatcher(testScheduler))
        runCurrent(); val children = job.children.toList(); assertEquals(3, children.size)
        advanceTimeBy(1000); runCurrent(); blocked.await()
        val stalledReads = reads
        advanceTimeBy(3100); runCurrent()
        assertFalse(engine.newIntentAllowed()); error(503) { runtime.publicStatus() }
        assertEquals(stalledReads, reads) // No replacement/hung observer jobs accumulate.
        hang = false; release.complete(Unit); runCurrent()
        assertTrue(engine.newIntentAllowed()); assertFalse(runtime.publicStatus().maintenance)
        job.cancelAndJoin(); assertTrue(children.all { it.isCompleted })
        val finalReads = reads; advanceTimeBy(5000); runCurrent(); assertEquals(finalReads, reads)
    }

    @Test fun failedRematchResultKeepsItsDistinctMatchIdentityAcrossRestart() = runTest {
        val rooms = InMemoryActiveRoomRepository(); var fail = false
        val engine = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository { if (fail) error("unavailable") })
        val (host, guest, room) = playing(engine); finish(engine, host, room)
        engine.handle(host, ClientMessage.RequestRematch(room)); engine.handle(guest, ClientMessage.RequestRematch(room))
        fail = true; finish(engine, host, room)
        engine.handle(host, ClientMessage.LeaveRoom(room)); engine.handle(guest, ClientMessage.LeaveRoom(room))
        val pending = rooms.loadAll().single().pendingCompletedMatch!!
        assertNotEquals(room, pending.matchId)
        val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository { results += it })
        restored.applyMaintenancePolicy(closed); restored.retryMaintenanceSettlements(); restored.retryMaintenanceSettlements()
        assertEquals(listOf(pending.matchId), results.map { it.matchId })
    }

    @Test fun unacknowledgedResultCannotBeOverwrittenByRematch() = runTest {
        val engine = GameEngine(matchResultRepository = MatchResultRepository { error("unavailable") })
        val (host, guest, room) = playing(engine)
        finish(engine, host, room)
        assertEquals("REMATCH_NOT_AVAILABLE", engine.handle(host, ClientMessage.RequestRematch(room)).errorCode())
        assertEquals("REMATCH_NOT_AVAILABLE", engine.handle(guest, ClientMessage.RequestRematch(room)).errorCode())
        assertEquals(1, engine.activeMaintenanceMatchCount())
    }

    @Test fun environmentClosureSurvivesStaleObservation() = runTest {
        var clock = 0L
        val engine = GameEngine()
        val runtime = MaintenanceRuntime(engine, environmentStatus = { ServiceStatusResponse(true) }, monotonicMillis = { clock })
        runtime.observe(); clock = 4000; runtime.fenceStaleObservation()
        assertTrue(engine.gameConnectionsClosed())
    }

    @Test fun reopeningSecondRoomSaveFailureRetainsWholeRoundForRetryAndRestore() = runTest { reopenFailure(false) }
    @Test fun reopeningTournamentSaveFailureRetainsWholeRoundForRetryAndRestore() = runTest { reopenFailure(true) }
    @Test fun restartBeforeReopenRetryRestoresPausedTournamentParticipants() = runTest { reopenFailure(true, true) }

    private suspend fun reopenFailure(failTournament: Boolean, restartBeforeRetry: Boolean = false) {
        val saved = InMemoryActiveRoomRepository(); var roomWrites = 0; var failRooms = false
        val rooms = object : ActiveRoomRepository by saved {
            override suspend fun save(room: StoredActiveRoom) {
                if (failRooms && ++roomWrites == 2) { failRooms = false; error("second room unavailable") }
                saved.save(room)
            }
        }
        var failBracket = false
        val tournaments = object : InMemoryTournamentRepository() {
            override suspend fun save(tournament: TournamentSnapshot) {
                if (failBracket) { failBracket = false; error("bracket unavailable") }
                super.save(tournament)
            }
        }
        val engine = GameEngine(activeRoomRepository = rooms, tournamentRepository = tournaments)
        val players = List(8) { engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "p$it")).playerId }
        val tournament = engine.handle(players[0], ClientMessage.CreateTournament("recovery cup", ProtocolGameMode.ORDER, 0, 8, TournamentVisibility.PUBLIC))
            .map { it.message }.filterIsInstance<ServerMessage.TournamentUpdated>().single().tournament
        players.drop(1).forEach { engine.handle(it, ClientMessage.JoinPublicTournament(tournament.tournamentId)) }
        val games = engine.handle(players[0], ClientMessage.StartTournament(tournament.tournamentId))
            .map { it.message }.filterIsInstance<ServerMessage.GameStarted>().map { it.game }
        engine.applyMaintenancePolicy(drain)
        games.forEach { finish(engine, it.hostId, it.roomId) }
        assertEquals(0, saved.loadAll().size)
        if (failTournament) failBracket = true else failRooms = true
        assertFailsWith<IllegalStateException> { engine.applyMaintenancePolicy(MaintenancePolicy(MaintenanceState.ACTIVE)) }
        assertEquals(0, saved.loadAll().size)
        assertEquals(0, tournaments.loadActive().single().matches.count { it.phase == TournamentMatchPhase.PLAYING })
        assertEquals(2, engine.activeMaintenanceMatchCount())
        val retryEngine = if (restartBeforeRetry) GameEngine(activeRoomRepository = rooms, tournamentRepository = tournaments) else engine
        val retry = retryEngine.applyMaintenancePolicy(MaintenancePolicy(MaintenanceState.ACTIVE))
        assertEquals(2, retry.deliveries.count { it.message is ServerMessage.GameStarted })
        val bracket = tournaments.loadActive().single()
        val roomIds = saved.loadAll().map { it.roomId }.toSet()
        assertEquals(2, roomIds.size)
        assertEquals(roomIds, bracket.matches.filter { it.phase == TournamentMatchPhase.PLAYING }.mapNotNull { it.roomId }.toSet())
        val restored = GameEngine(activeRoomRepository = saved, tournamentRepository = tournaments)
        restored.restoreActiveRooms(); assertEquals(2, restored.activeMaintenanceMatchCount())
    }

    @Test fun observationContinuesWhileRuntimeSettlementIsSuspended() = runTest {
        val f = MaintenanceFixture(); f.initialize()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var fail = true
        val engine = GameEngine(matchResultRepository = MatchResultRepository {
            if (fail) error("temporary failure")
            entered.complete(Unit); release.await()
        })
        val (host, _, room) = playing(engine); finish(engine, host, room); fail = false
        val runtime = MaintenanceRuntime(engine, f.service)
        val reconciling = async { runtime.refresh() }
        entered.await()
        f.service.update(f.owner, f.request().copy(mode = MaintenanceMode.EMERGENCY))
        runtime.observe()
        try { assertFalse(engine.newIntentAllowed()); assertFalse(reconciling.isCompleted) }
        finally { release.complete(Unit) }
        assertTrue(reconciling.await().closeSockets)
    }

    @Test fun stalledObservationFencesAtDeadlineAndRecoversWithoutOpeningKnownClosure() = runTest {
        val f = MaintenanceFixture(); f.initialize(); var clock = 0L
        val engine = GameEngine(); val runtime = MaintenanceRuntime(engine, f.service, monotonicMillis = { clock })
        runtime.observe(); assertTrue(engine.newIntentAllowed())
        clock = 4000; runtime.fenceStaleObservation()
        assertFalse(engine.newIntentAllowed()); error(503) { runtime.publicStatus() }
        runtime.observe(); assertTrue(engine.newIntentAllowed())
        f.service.update(f.owner, f.request().copy(mode = MaintenanceMode.EMERGENCY)); runtime.observe()
        clock += 4000; runtime.fenceStaleObservation()
        assertTrue(engine.gameConnectionsClosed())
    }

    @Test fun admissionClosesBeforeBlockedNormalRoomSaveCompletes() = runTest {
        val saved = InMemoryActiveRoomRepository(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repository = object : ActiveRoomRepository by saved {
            override suspend fun save(room: StoredActiveRoom) { entered.complete(Unit); release.await(); saved.save(room) }
        }
        val engine = GameEngine(activeRoomRepository = repository)
        val player = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host"))
        val creation = async { engine.handle(player.playerId, ClientMessage.CreateRoom("room", "", ProtocolGameMode.ORDER)) }
        entered.await()
        val applying = async { engine.applyMaintenancePolicy(closed) }
        runCurrent()
        try {
            assertFalse(engine.newIntentAllowed())
            assertFailsWith<MaintenanceAdmissionException> { engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "late account")) }
            assertEquals("MAINTENANCE", engine.handle(player.playerId, ClientMessage.CreateRoom("later", "", ProtocolGameMode.ORDER)).errorCode())
            assertFalse(applying.isCompleted)
        } finally { release.complete(Unit) }
        creation.await(); assertTrue(applying.await().closeSockets)
    }

    @Test fun failedResultSurvivesBothResultExitsAndRestart() = runTest { resultLifetime(leave = true) }
    @Test fun failedResultSurvivesDisconnectCleanupAndRestart() = runTest { resultLifetime(leave = false) }

    private suspend fun resultLifetime(leave: Boolean) {
        val rooms = InMemoryActiveRoomRepository(); var now = 1000L
        val engine = GameEngine(activeRoomRepository = rooms, nowMillis = { now }, matchResultRepository = MatchResultRepository { error("save unavailable") })
        val (host, guest, room) = playing(engine)
        engine.applyMaintenancePolicy(drain)
        finish(engine, host, room)
        if (leave) {
            engine.handle(host, ClientMessage.LeaveRoom(room)); engine.handle(guest, ClientMessage.LeaveRoom(room))
        } else {
            engine.markDisconnected(host); engine.markDisconnected(guest); now += 1_000_000; engine.cleanupExpiredSessions()
        }
        assertNotNull(rooms.loadAll().single().pendingCompletedMatch)
        val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository { results += it })
        restored.applyMaintenancePolicy(closed); restored.retryMaintenanceSettlements(); restored.retryMaintenanceSettlements()
        assertEquals(1, results.size)
        assertEquals(host, results.single().winnerPlayerId)
    }

    @Test fun disconnectGeneratedResultIsPersistedBeforeRoomCleanup() = runTest {
        val rooms = InMemoryActiveRoomRepository(); var now = 1000L
        val engine = GameEngine(activeRoomRepository = rooms, nowMillis = { now }, matchResultRepository = MatchResultRepository { error("save unavailable") })
        val (host, guest, _) = playing(engine)
        engine.markDisconnected(guest); now += 1_000_000; engine.cleanupExpiredSessions()
        val pending = rooms.loadAll().single().pendingCompletedMatch!!
        assertEquals(host, pending.winnerPlayerId)
        val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository { results += it })
        restored.applyMaintenancePolicy(closed); restored.retryMaintenanceSettlements()
        assertEquals(1, results.size)
    }

    @Test fun finalTournamentPrizeAndSnapshotFailureRetryUntilDurablyAcknowledged() = runTest {
        val stored = InMemoryActiveRoomRepository(); val tournaments = object : InMemoryTournamentRepository() {
            var fail = false
            override suspend fun save(tournament: TournamentSnapshot) {
                if (fail && tournament.phase == TournamentPhase.FINISHED) { fail = false; error("snapshot unavailable") }
                super.save(tournament)
            }
        }
        var failPrize = true; val ledger = mutableSetOf<String>()
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun applyWalletTransaction(playerId: String, sourceType: String, sourceId: String, goldDelta: Int, gemsDelta: Int, xpDelta: Int): WalletMutationStatus {
                if (sourceType == "TOURNAMENT_PRIZE" && failPrize) { failPrize = false; error("prize unavailable") }
                return if (ledger.add("$playerId:$sourceType:$sourceId")) WalletMutationStatus.APPLIED else WalletMutationStatus.DUPLICATE
            }
        }
        val engine = GameEngine(activeRoomRepository = stored, tournamentRepository = tournaments, playerProfileRepository = profiles)
        val (host, _, room) = playing(engine)
        val savedRoom = stored.loadAll().single()
        val id = UUID.randomUUID().toString()
        tournaments.save(finalTournament(id, savedRoom, prize = 100))
        val restored = GameEngine(activeRoomRepository = stored, tournamentRepository = tournaments, playerProfileRepository = profiles)
        restored.connectAccount(AuthenticatedAccount(UUID.fromString(host), "host"))
        runCatching { finish(restored, host, room) }
        assertEquals(1, restored.activeMaintenanceMatchCount())
        tournaments.fail = true
        runCatching { restored.retryMaintenanceSettlements() }
        assertEquals(1, restored.activeMaintenanceMatchCount())
        restored.retryMaintenanceSettlements()
        assertEquals(0, restored.activeMaintenanceMatchCount())
        assertEquals(host, tournaments.loadRecent(host, 10).single().championPlayerId)
        assertEquals(1, ledger.count { it.contains("TOURNAMENT_PRIZE") })
    }

    private fun finalTournament(id: String, room: StoredActiveRoom, prize: Int = 0) = TournamentSnapshot(id, "cup", room.host.playerId,
        ProtocolGameMode.ORDER, TournamentPhase.RUNNING, 4,
        players = listOf(TournamentPlayerSnapshot(room.host.playerId, "host"), TournamentPlayerSnapshot(room.guest!!.playerId, "guest")),
        matches = listOf(TournamentMatchSnapshot("final", 2, 1, room.host.playerId, room.guest.playerId, roomId = room.roomId, phase = TournamentMatchPhase.PLAYING)),
        createdAtEpochMillis = 0, prizePool = prize)

    private suspend fun playing(engine: GameEngine): Triple<String, String, String> {
        val host = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host")).playerId
        val guest = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "guest")).playerId
        val room = engine.handle(host, ClientMessage.CreateRoom("room", "", ProtocolGameMode.ORDER)).map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        engine.handle(guest, ClientMessage.JoinRoom(room, "")); engine.handle(host, ClientMessage.SetReady(room, true)); engine.handle(guest, ClientMessage.SetReady(room, true))
        return Triple(host, guest, room)
    }
    private suspend fun finish(engine: GameEngine, host: String, room: String) {
        for (number in 1..GAME_NUMBER_COUNT) engine.handle(host, ClientMessage.SelectNumber(room, number, "$number"))
    }
    private fun List<Delivery>.errorCode() = map { it.message }.filterIsInstance<ServerMessage.Error>().single().code
}
