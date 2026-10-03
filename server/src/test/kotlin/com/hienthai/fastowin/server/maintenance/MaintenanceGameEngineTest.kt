package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.*

class MaintenanceGameEngineTest {
    private val draining = MaintenancePolicy(MaintenanceState.DRAINING)
    private val closed = MaintenancePolicy(MaintenanceState.MAINTENANCE)

    @Test fun failedCompletedResultRemainsDurableAndRestoredRetryFinishesOnce() = runTest {
        val rooms = InMemoryActiveRoomRepository()
        val engine = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository { throw java.sql.SQLException("test failure") })
        val (host, _, room) = playing(engine)
        engine.applyMaintenancePolicy(draining)
        for (number in 1..GAME_NUMBER_COUNT) engine.handle(host.playerId, ClientMessage.SelectNumber(room, number, "n$number"))
        assertNotNull(rooms.loadAll().single().pendingCompletedMatch)
        assertEquals(1, engine.activeMaintenanceMatchCount())
        val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = rooms, matchResultRepository = MatchResultRepository { results += it })
        restored.applyMaintenancePolicy(closed)
        restored.retryMaintenanceSettlements(); restored.retryMaintenanceSettlements()
        assertEquals(1, results.size); assertEquals(host.playerId, results.single().winnerPlayerId)
        assertNull(rooms.loadAll().single().pendingCompletedMatch)
    }

    @Test fun drainCountsAdmittedSettlementUntilItCompletesEvenIfSocketWorkIsCancelled() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val results = mutableListOf<CompletedMatch>()
        val engine = GameEngine(matchResultRepository = MatchResultRepository { entered.complete(Unit); release.await(); results += it })
        val (host, _, room) = playing(engine)
        engine.applyMaintenancePolicy(draining)
        for (number in 1 until GAME_NUMBER_COUNT) engine.handle(host.playerId, ClientMessage.SelectNumber(room, number, "n$number"))
        val completion = launch { engine.handle(host.playerId, ClientMessage.SelectNumber(room, GAME_NUMBER_COUNT, "last")) }
        entered.await()
        assertEquals(1, engine.activeMaintenanceMatchCount())
        completion.cancel()
        release.complete(Unit); completion.join()
        assertEquals(1, results.size)
        assertEquals(0, engine.activeMaintenanceMatchCount())
    }

    @Test fun restoredLiveMatchResumesWithPersistedTokenAndLegacyAccountSnapshotOnly() = runTest {
        val rooms = InMemoryActiveRoomRepository()
        val original = GameEngine(activeRoomRepository = rooms)
        val (host, _, roomId) = playing(original)
        val saved = rooms.loadAll().single()
        val account = AuthenticatedAccount(UUID.fromString(host.playerId), "host")
        val restored = GameEngine(activeRoomRepository = rooms)
        restored.applyMaintenancePolicy(draining)
        assertEquals(roomId, restored.connectAccount(account, host.resumeToken).currentGame!!.roomId)
        assertFailsWith<InvalidResumeTokenException> { restored.connectAccount(account, "wrong") }

        rooms.save(saved.copy(host = saved.host.copy(accountResumeToken = null)))
        val legacy = GameEngine(activeRoomRepository = rooms)
        legacy.applyMaintenancePolicy(draining)
        val resumed = legacy.connectAccount(account, "stale-pre-restart-token")
        assertEquals(roomId, resumed.currentGame!!.roomId)
        assertNotEquals("stale-pre-restart-token", resumed.resumeToken)
        assertFailsWith<MaintenanceAdmissionException> { legacy.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "unrelated")) }
        assertFailsWith<InvalidResumeTokenException> { legacy.connectAccount(account, "stale-pre-restart-token") }

        rooms.save(saved.copy(phase = RoomPhase.WAITING, host = saved.host.copy(accountResumeToken = null)))
        val waiting = GameEngine(activeRoomRepository = rooms)
        waiting.applyMaintenancePolicy(draining)
        assertFailsWith<MaintenanceAdmissionException> { waiting.connectAccount(account) }
    }

    @Test fun drainStopsNextTournamentRoundAndClosureCancelsWithoutChampionOrIncompleteResults() = runTest {
        val tournaments = InMemoryTournamentRepository(); val rooms = InMemoryActiveRoomRepository()
        val results = mutableListOf<CompletedMatch>()
        val engine = GameEngine(tournamentRepository = tournaments, activeRoomRepository = rooms,
            matchResultRepository = MatchResultRepository { results += it })
        val players = List(4) { engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "p$it")) }
        val tournament = engine.handle(players[0].playerId, ClientMessage.CreateTournament("cup", ProtocolGameMode.ORDER,
            0, 4, TournamentVisibility.PUBLIC)).map { it.message }.filterIsInstance<ServerMessage.TournamentUpdated>().single().tournament
        players.drop(1).forEach { engine.handle(it.playerId, ClientMessage.JoinPublicTournament(tournament.tournamentId)) }
        val games = engine.handle(players[0].playerId, ClientMessage.StartTournament(tournament.tournamentId))
            .map { it.message }.filterIsInstance<ServerMessage.GameStarted>().map { it.game }
        assertEquals(2, games.size)
        engine.applyMaintenancePolicy(draining)
        games.forEach { game ->
            for (number in 1..GAME_NUMBER_COUNT) engine.handle(game.hostId, ClientMessage.SelectNumber(game.roomId, number, "${game.roomId}:$number"))
        }
        assertEquals(2, results.size)
        assertEquals(0, engine.activeMaintenanceMatchCount())
        val pending = tournaments.loadActive().single()
        assertEquals(2, pending.matches.count { it.phase == TournamentMatchPhase.FINISHED })
        assertEquals(1, pending.matches.count { it.phase == TournamentMatchPhase.PENDING })
        engine.applyMaintenancePolicy(MaintenancePolicy(MaintenanceState.ACTIVE))
        assertEquals(1, engine.activeMaintenanceMatchCount())
        engine.applyMaintenancePolicy(closed)
        val cancelled = tournaments.loadRecent(players[0].playerId, 10).single()
        assertEquals(TournamentPhase.CANCELLED, cancelled.phase); assertNull(cancelled.championPlayerId)
        assertEquals(2, results.size)
    }

    @Test fun restoredAbortedTournamentSnapshotNeverInventsWinner() = runTest {
        val tournaments = InMemoryTournamentRepository(); val rooms = InMemoryActiveRoomRepository()
        val first = GameEngine(activeRoomRepository = rooms)
        val (host, guest, roomId) = playing(first)
        val tournamentId = UUID.randomUUID().toString()
        tournaments.save(TournamentSnapshot(tournamentId, "cup", host.playerId, ProtocolGameMode.ORDER,
            TournamentPhase.RUNNING, 4, players = listOf(TournamentPlayerSnapshot(host.playerId, "host"), TournamentPlayerSnapshot(guest.playerId, "guest")),
            matches = listOf(TournamentMatchSnapshot("round", 1, 0, host.playerId, guest.playerId, roomId = roomId, phase = TournamentMatchPhase.PLAYING)),
            createdAtEpochMillis = 0))
        rooms.save(rooms.loadAll().single().copy(phase = RoomPhase.FINISHED, maintenanceAborted = true, resultQueued = true,
            scores = mapOf(host.playerId to 10, guest.playerId to 0)))
        val restored = GameEngine(activeRoomRepository = rooms, tournamentRepository = tournaments)
        val snapshot = restored.connectAccount(AuthenticatedAccount(UUID.fromString(host.playerId), "host")).currentGame!!
        assertNull(snapshot.winnerPlayerId)
        assertTrue(restored.advanceTimedGames().none { it.message is ServerMessage.GameFinished })
        restored.applyMaintenancePolicy(closed)
        assertNull(tournaments.loadRecent(host.playerId, 10).single().championPlayerId)
    }

    @Test fun acceptedWalletWorkCompletesWithoutHoldingPolicyMutexAndLaterWorkIsRefused() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var grants = 0
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun exchangeGemsForGold(playerId: String, requestId: String, offer: GoldExchangeOffer): GoldExchangeStatus {
                entered.complete(Unit); release.await(); grants++; return GoldExchangeStatus.GRANTED
            }
        }
        val engine = GameEngine(playerProfileRepository = profiles)
        val player = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "player"))
        val command = ClientMessage.ExchangeGemsForGold("wallet-1", GOLD_EXCHANGE_OFFERS.first().id)
        val accepted = async { engine.handle(player.playerId, command) }
        entered.await()
        withTimeout(1000) { engine.applyMaintenancePolicy(closed) }
        assertEquals("MAINTENANCE", engine.handle(player.playerId, command.copy(requestId = "wallet-2")).map { it.message }.filterIsInstance<ServerMessage.Error>().single().code)
        release.complete(Unit)
        assertEquals(GoldExchangeStatus.GRANTED, accepted.await().map { it.message }.filterIsInstance<ServerMessage.GoldExchangeResult>().single().status)
        assertEquals(1, grants)
    }

    @Test fun failedAbortPersistenceNeverPermitsCloseOrSettlementAndRetryPersistsBeforeReopening() = runTest {
        val stored = InMemoryActiveRoomRepository(); var fail = false
        val repository = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                if (fail) throw java.sql.SQLException("abort persistence unavailable")
                stored.save(room)
            }
        }
        val results = mutableListOf<CompletedMatch>()
        val engine = GameEngine(activeRoomRepository = repository, matchResultRepository = MatchResultRepository { results += it })
        val (host, _, _) = playing(engine)
        fail = true
        assertFailsWith<java.sql.SQLException> { engine.applyMaintenancePolicy(closed) }
        assertFalse(engine.newIntentAllowed())
        engine.advanceTimedGames(); engine.markDisconnected(host.playerId)
        assertTrue(results.isEmpty())
        fail = false
        engine.applyMaintenancePolicy(MaintenancePolicy(MaintenanceState.ACTIVE))
        assertTrue(stored.loadAll().single().maintenanceAborted)
        assertTrue(engine.newIntentAllowed())
    }

    @Test fun drainFinishesAdmittedMatchExactlyOnceAndRejectsOldSocketNewIntent() = runTest {
        val results = mutableListOf<CompletedMatch>()
        val engine = GameEngine(matchResultRepository = MatchResultRepository { results += it })
        val (host, guest, room) = playing(engine)
        engine.applyMaintenancePolicy(draining)
        for (message in listOf(ClientMessage.CreateRoom("blocked", "", ProtocolGameMode.ORDER),
            ClientMessage.JoinRoom(room, ""), ClientMessage.SetReady(room, true),
            ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER), ClientMessage.ClaimDailyCheckIn)) {
            assertEquals("MAINTENANCE", engine.handle(host.playerId, message).map { it.message }.filterIsInstance<ServerMessage.Error>().single().code)
        }
        for (number in 1..GAME_NUMBER_COUNT) engine.handle(host.playerId, ClientMessage.SelectNumber(room, number, "n$number"))
        engine.advanceTimedGames()
        assertEquals(1, results.size)
        assertEquals(0, engine.activeMaintenanceMatchCount())
        assertEquals("MAINTENANCE", engine.handle(guest.playerId, ClientMessage.RequestRematch(room)).map { it.message }.filterIsInstance<ServerMessage.Error>().single().code)
    }

    @Test fun emergencyPersistsAbortBeforeCloseAndRestoreNeverSettlesOrManufacturesWinner() = runTest {
        val repository = InMemoryActiveRoomRepository()
        val results = mutableListOf<CompletedMatch>()
        var now = 1_000L
        val engine = GameEngine(activeRoomRepository = repository, matchResultRepository = MatchResultRepository { results += it }, nowMillis = { now })
        val (host, guest, room) = playing(engine)
        val change = engine.applyMaintenancePolicy(closed)
        assertEquals(setOf(room), change.abortedRoomIds)
        val saved = repository.loadAll().single()
        assertTrue(saved.maintenanceAborted)
        assertTrue(saved.resultQueued)
        assertNull(saved.forcedWinnerId)
        engine.markDisconnected(host.playerId); engine.markDisconnected(guest.playerId)
        now += 1_000_000
        engine.advanceTimedGames(); engine.cleanupExpiredSessions()
        repository.save(saved)
        val restored = GameEngine(activeRoomRepository = repository, matchResultRepository = MatchResultRepository { results += it }, nowMillis = { now })
        restored.applyMaintenancePolicy(closed)
        now += 1_000_000
        restored.advanceTimedGames(); restored.cleanupExpiredSessions()
        assertTrue(results.isEmpty())
    }

    @Test fun onlyMatchingLiveResumeIsAdmittedDuringDrain() = runTest {
        val engine = GameEngine()
        val (host, _, _) = playing(engine)
        engine.applyMaintenancePolicy(draining)
        val account = AuthenticatedAccount(UUID.fromString(host.playerId), "host")
        assertFailsWith<MaintenanceAdmissionException> { engine.connectAccount(account) }
        assertFailsWith<MaintenanceAdmissionException> { engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "new")) }
        assertNotNull(engine.connectAccount(account, host.resumeToken).currentGame)
        engine.applyMaintenancePolicy(closed)
        assertFailsWith<MaintenanceAdmissionException> { engine.connectAccount(account, host.resumeToken) }
    }

    @Test fun blockedTransitionWinsAgainstSuspendedProfileLookup() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var pause = false
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? {
                if (pause) { entered.complete(Unit); release.await() }
                return null
            }
        }
        val engine = GameEngine(playerProfileRepository = profiles)
        val player = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "player"))
        pause = true
        val creating = async { engine.handle(player.playerId, ClientMessage.CreateRoom("race", "", ProtocolGameMode.ORDER)) }
        entered.await(); engine.applyMaintenancePolicy(draining); release.complete(Unit)
        assertEquals("MAINTENANCE", creating.await().map { it.message }.filterIsInstance<ServerMessage.Error>().single().code)
        assertTrue(engine.roomList().rooms.isEmpty())
    }

    private suspend fun playing(engine: GameEngine): Triple<ConnectedPlayer, ConnectedPlayer, String> {
        val host = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host"))
        val guest = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "guest"))
        val room = engine.handle(host.playerId, ClientMessage.CreateRoom("maintenance", "", ProtocolGameMode.ORDER))
            .map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        engine.handle(guest.playerId, ClientMessage.JoinRoom(room, ""))
        engine.handle(host.playerId, ClientMessage.SetReady(room, true))
        engine.handle(guest.playerId, ClientMessage.SetReady(room, true))
        return Triple(host, guest, room)
    }
}
