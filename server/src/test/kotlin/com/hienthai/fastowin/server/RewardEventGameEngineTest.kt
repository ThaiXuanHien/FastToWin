package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.events.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

class RewardEventGameEngineTest {
    @Test fun `maintenance published while start save is suspended suppresses gameplay and aborts frozen match durably`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val stored = InMemoryActiveRoomRepository()
        val repo = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                if (room.phase == RoomPhase.PLAYING) { entered.complete(Unit); release.await() }
                stored.save(room)
            }
        }
        val engine = GameEngine(activeRoomRepository = repo, eventRewardSource = MutableSource(), nowMillis = { 100 })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        val start = async { engine.handle(guest, ClientMessage.SetReady(room, true)) }
        entered.await()
        engine.publishMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.MAINTENANCE))
        release.complete(Unit)
        assertTrue(start.await().none { it.message is ServerMessage.GameStarted })
        engine.reconcileMaintenancePolicy()
        val aborted = stored.loadAll().single()
        assertTrue(aborted.maintenanceAborted); assertEquals(2, aborted.eventRewards.goldMultiplier)
        assertNull(aborted.pendingCompletedMatch); assertEquals(0, engine.activeMaintenanceMatchCount())
    }

    @Test fun `restart during active event match retains frozen rewards with unavailable source`() = runTest {
        val stored = InMemoryActiveRoomRepository(); val quota = InMemoryPlayQuotaRepository()
        val original = GameEngine(activeRoomRepository = stored, playQuotaRepository = quota, eventRewardSource = MutableSource(), nowMillis = { 100 })
        val (host, guest, room) = waiting(original)
        original.handle(host, ClientMessage.SetReady(room, true)); original.handle(guest, ClientMessage.SetReady(room, true))
        original.handle(host, ClientMessage.SelectNumber(room, 1, "before-restart"))
        val source = MutableSource().apply { fail = true }; val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = stored, playQuotaRepository = quota, eventRewardSource = source,
            nowMillis = { 300 }, matchResultRepository = MatchResultRepository { results += it })
        val resumed = restored.connectAccount(AuthenticatedAccount(UUID.fromString(host), "host"))
        assertEquals(2, resumed.currentGame?.eventRewards?.goldMultiplier)
        for(n in 2..GAME_NUMBER_COUNT) restored.handle(host, ClientMessage.SelectNumber(room, n, "after-$n"))
        assertEquals(2, results.single().eventRewards.goldMultiplier); assertEquals(0, source.calls)
        assertEquals(1, quota.getSnapshot(host, 100).matchesConsumed)
    }
    @Test fun `durable retry reuses the tenth quota receipt and negative readiness cannot activate it`() = runTest {
        val quota = InMemoryPlayQuotaRepository(); val stored = InMemoryActiveRoomRepository(); var fail = true
        val repo = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                if (room.phase == RoomPhase.PLAYING && fail) error("unavailable")
                stored.save(room)
            }
        }
        val engine = GameEngine(playQuotaRepository = quota, activeRoomRepository = repo, eventRewardSource = MutableSource(), nowMillis = { 100 })
        val (host, guest, room) = waiting(engine)
        repeat(9) { quota.consumeMatch(setOf(host, guest), "previous-$it", 100) }
        engine.handle(host, ClientMessage.SetReady(room, true)); engine.handle(guest, ClientMessage.SetReady(room, true))
        assertEquals(10, quota.getSnapshot(host, 100).matchesConsumed)
        fail = false
        assertTrue(engine.handle(guest, ClientMessage.SetReady(room, false)).none { it.message is ServerMessage.GameStarted })
        assertEquals(2, engine.handle(guest, ClientMessage.SetReady(room, true)).started().eventRewards?.goldMultiplier)
        assertEquals(10, quota.getSnapshot(host, 100).matchesConsumed)
    }

    @Test fun `quota failure after committing reservation refunds both players and retry starts normally`() = runTest {
        val real = InMemoryPlayQuotaRepository(); var fail = true
        val quota = object : PlayQuotaRepository by real {
            override suspend fun prepareMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption {
                val result = real.prepareMatch(userIds, matchId, nowMillis)
                if (fail) error("quota acknowledgement lost")
                return result
            }
        }
        val source = MutableSource()
        val engine = GameEngine(playQuotaRepository = quota, nowMillis = { 100 }, eventRewardSource = source)
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true)); engine.handle(guest, ClientMessage.SetReady(room, true))
        assertEquals(0, real.getSnapshot(host, 100).matchesConsumed); assertEquals(0, real.getSnapshot(guest, 100).matchesConsumed)
        assertEquals(0, source.calls)
        fail = false; assertEquals(2, engine.handle(guest, ClientMessage.SetReady(room, true)).started().eventRewards?.goldMultiplier)
    }
    @Test fun `tournament initial and later rounds remain neutral with unavailable configured event source`() = runTest {
        val source = MutableSource().apply { fail = true }; val results = mutableListOf<CompletedMatch>()
        val rooms = InMemoryActiveRoomRepository(); val tournaments = InMemoryTournamentRepository()
        val engine = GameEngine(activeRoomRepository = rooms, tournamentRepository = tournaments, eventRewardSource = source,
            matchResultRepository = MatchResultRepository { results += it })
        val ids = (1..4).map { engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "player$it")).playerId }
        val tournament = engine.handle(ids[0], ClientMessage.CreateTournament("Cup", ProtocolGameMode.ORDER, 0, 4, TournamentVisibility.PUBLIC))
            .map { it.message }.filterIsInstance<ServerMessage.TournamentUpdated>().single().tournament
        ids.drop(1).forEach { engine.handle(it, ClientMessage.JoinPublicTournament(tournament.tournamentId)) }
        val first = engine.handle(ids[0], ClientMessage.StartTournament(tournament.tournamentId))
            .map { it.message }.filterIsInstance<ServerMessage.GameStarted>().map { it.game }
        assertEquals(2, first.size)
        for(game in first) { assertEquals(1, game.eventRewards?.goldMultiplier); finish(engine, game.hostId, game.roomId) }
        val final = rooms.loadAll().single { it.phase == RoomPhase.PLAYING }
        assertEquals(MatchRewardEventSnapshot(), final.eventRewards)
        // Restart between tournament rounds must retain exclusion without contacting a catalog.
        val restored = GameEngine(activeRoomRepository = rooms, tournamentRepository = tournaments, eventRewardSource = source,
            matchResultRepository = MatchResultRepository { results += it })
        restored.connectAccount(AuthenticatedAccount(UUID.fromString(final.host.playerId), "finalist"))
        finish(restored, final.host.playerId, final.roomId)
        assertEquals(3, results.size); assertTrue(results.all { it.eventRewards == MatchRewardEventSnapshot() }); assertEquals(0, source.calls)
    }
    @Test fun `maintenance abort of uncertain start preserves frozen identity and never later activates it`() = runTest {
        val stored = InMemoryActiveRoomRepository(); var fail = true
        val repo = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                if (room.phase == RoomPhase.PLAYING && fail) error("unavailable")
                stored.save(room)
            }
        }
        val engine = GameEngine(activeRoomRepository = repo, eventRewardSource = MutableSource(), nowMillis = { 100 })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true)); engine.handle(guest, ClientMessage.SetReady(room, true))
        fail = false
        engine.applyMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.MAINTENANCE))
        assertTrue(stored.loadAll().single().maintenanceAborted)
        engine.applyMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.ACTIVE))
        assertTrue(engine.retryMaintenanceSettlements().none { it.message is ServerMessage.GameStarted })
        assertTrue(stored.loadAll().single().maintenanceAborted)
    }

    @Test fun `disconnect during quota preparation invalidates admission and refunds original reservation`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val real = InMemoryPlayQuotaRepository()
        val quota = object : PlayQuotaRepository by real {
            override suspend fun prepareMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption {
                val result = real.prepareMatch(userIds, matchId, nowMillis); entered.complete(Unit); release.await(); return result
            }
        }
        val engine = GameEngine(playQuotaRepository = quota, nowMillis = { 100 }, eventRewardSource = MutableSource())
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        val start = async { engine.handle(guest, ClientMessage.SetReady(room, true)) }
        entered.await(); engine.markDisconnected(host); release.complete(Unit)
        assertTrue(start.await().none { it.message is ServerMessage.GameStarted })
        assertEquals(0, real.getSnapshot(host, 100).matchesConsumed)
    }

    @Test fun `memory prepared quota refunds original date and never finalized receipts`() = runTest {
        val quota = InMemoryPlayQuotaRepository(); val users = setOf("a", "b")
        val beforeMidnight = 61_199_999L; val afterMidnight = 61_200_001L
        assertEquals(PlayQuotaConsumptionStatus.CONSUMED, quota.prepareMatch(users, "abandoned", beforeMidnight).status)
        quota.releaseMatch(users, "abandoned", emptySet())
        assertEquals(0, quota.getSnapshot("a", beforeMidnight).matchesConsumed)
        assertEquals(0, quota.getSnapshot("a", afterMidnight).matchesConsumed)
        quota.prepareMatch(users, "started", beforeMidnight); quota.finalizeMatch(users, "started")
        quota.releaseMatch(users, "started", emptySet()); quota.recoverPreparedMatches(emptySet())
        assertEquals(1, quota.getSnapshot("a", beforeMidnight).matchesConsumed)
        quota.prepareMatch(users, "recover", beforeMidnight); quota.recoverPreparedMatches(setOf("recover"))
        assertEquals(2, quota.getSnapshot("a", beforeMidnight).matchesConsumed)
    }
    @Test fun `actual boundary capture survives ending and result retry and rematch captures fresh catalog`() = runTest {
        var now = 99L
        val source = MutableSource()
        val rooms = InMemoryActiveRoomRepository()
        val saved = mutableListOf<CompletedMatch>()
        var failResult = true
        val engine = GameEngine(nowMillis = { now }, eventRewardSource = source, activeRoomRepository = rooms,
            matchResultRepository = MatchResultRepository { if(failResult) error("settlement unavailable") else saved += it })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        assertEquals(0, source.calls)
        source.before = { now = 100 }
        val started = engine.handle(guest, ClientMessage.SetReady(room, true)).started()
        assertEquals(100, started.eventRewards?.capturedAtEpochMillis)
        assertEquals(listOf("gold", "xp"), started.eventRewards?.sources?.map { it.id })
        assertEquals(2, started.eventRewards?.goldMultiplier); assertEquals(2, started.eventRewards?.xpMultiplier)
        now = 200; source.fail = true
        finish(engine, host, room)
        assertEquals(2, rooms.loadAll().single().pendingCompletedMatch?.eventRewards?.goldMultiplier)
        failResult = false; engine.retryMaintenanceSettlements(); engine.retryMaintenanceSettlements()
        assertEquals(1, saved.size)
        val reward = RewardEconomy.matchRewardBreakdown(saved.single().matchType, MatchOutcome.WIN, false, saved.single().eventRewards)
        assertEquals(80, reward.baseGold); assertEquals(80, reward.bonusGold); assertEquals(160, reward.totalGold); assertEquals(48, reward.totalXp)
        source.fail = false; source.before = {}
        engine.handle(host, ClientMessage.RequestRematch(room))
        val rematch = engine.handle(guest, ClientMessage.RespondRematch(room, true)).started()
        assertNotEquals(started.matchId, rematch.matchId)
        assertEquals(1, rematch.eventRewards?.goldMultiplier)
        finish(engine, host, room)
        assertEquals(2, saved.size); assertEquals(1, saved.last().eventRewards.goldMultiplier)
    }

    @Test fun `cancelled source transaction cleans reservation and keeps room retryable`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val quota = InMemoryPlayQuotaRepository(); var cancel = true
        val real = MutableSource()
        val source = object : EventRewardSource {
            override suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T = real.withStartCatalog {
                val result = block(it)
                if (cancel) { entered.complete(Unit); release.await(); throw CancellationException("cancelled source") }
                result
            }
        }
        val engine = GameEngine(playQuotaRepository = quota, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        val job = async { engine.handle(guest, ClientMessage.SetReady(room, true)) }
        entered.await(); release.complete(Unit)
        assertFailsWith<CancellationException> { job.await() }
        assertEquals(0, quota.getSnapshot(host, 100).matchesConsumed)
        cancel = false
        assertEquals(2, engine.handle(guest, ClientMessage.SetReady(room, true)).started().eventRewards?.goldMultiplier)
    }

    @Test fun `uncertain start write retries exact frozen payload without source and reconnect cannot overwrite it`() = runTest {
        val stored = InMemoryActiveRoomRepository(); val source = MutableSource(); var fail = true
        val repo = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                stored.save(room)
                if (room.phase == RoomPhase.PLAYING && fail) error("lost acknowledgement")
            }
        }
        val engine = GameEngine(activeRoomRepository = repo, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        assertTrue(engine.handle(guest, ClientMessage.SetReady(room, true)).none { it.message is ServerMessage.GameStarted })
        assertEquals(1, engine.activeMaintenanceMatchCount(), "Captured uncertain start remains admitted work")
        assertNotEquals(RoomPhase.PLAYING, engine.connectAccount(AuthenticatedAccount(UUID.fromString(host), "host")).currentGame?.phase)
        assertEquals(RoomPhase.PLAYING, stored.loadAll().single().phase, "Reconnect overwrote uncertain durable start")
        source.fail = true; fail = false
        val retried = engine.retryMaintenanceSettlements().started()
        assertEquals(2, retried.eventRewards?.goldMultiplier); assertEquals(1, source.calls)
    }

    @Test fun `slow start storage keeps room nonplayable and engine commands progress`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val stored = InMemoryActiveRoomRepository()
        val repo = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                if (room.phase == RoomPhase.PLAYING) { entered.complete(Unit); release.await() }
                stored.save(room)
            }
        }
        var fcmUpdated = false
        val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
            override suspend fun updateFcmToken(playerId: String, token: String, languageTag: String): Boolean { fcmUpdated = true; return true }
        }
        val engine = GameEngine(activeRoomRepository = repo, playerProfileRepository = profiles, eventRewardSource = MutableSource(), nowMillis = { 100 })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        val start = async { engine.handle(guest, ClientMessage.SetReady(room, true)) }
        entered.await()
        engine.handle(host, ClientMessage.UpdateFcmToken("token", "en"))
        assertTrue(fcmUpdated)
        assertTrue(engine.handle(host, ClientMessage.LeaveRoom(room)).any { it.message is ServerMessage.Error })
        assertTrue(engine.handle(host, ClientMessage.SelectNumber(room, 1, "early")).none { it.message is ServerMessage.GameStateUpdated })
        release.complete(Unit); assertEquals(2, start.await().started().eventRewards?.goldMultiplier)
    }

    @Test fun `legacy active snapshot and completed payload decode neutral without querying source`() = runTest {
        val stored = InMemoryActiveRoomRepository()
        val original = GameEngine(activeRoomRepository = stored)
        val (host, guest, room) = waiting(original)
        original.handle(host, ClientMessage.SetReady(room, true)); original.handle(guest, ClientMessage.SetReady(room, true))
        val json = ProtocolJson.encodeToJsonElement(stored.loadAll().single()).jsonObject
        stored.save(ProtocolJson.decodeFromJsonElement<StoredActiveRoom>(JsonObject(json - "eventRewards")))
        val source = MutableSource().apply { fail = true }; val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = stored, eventRewardSource = source, matchResultRepository = MatchResultRepository { results += it })
        restored.connectAccount(AuthenticatedAccount(UUID.fromString(host), "host"))
        finish(restored, host, room)
        assertEquals(1, results.single().eventRewards.goldMultiplier); assertEquals(0, source.calls)
        val legacy = JsonObject(ProtocolJson.encodeToJsonElement(results.single()).jsonObject - "eventRewards")
        assertEquals(MatchRewardEventSnapshot(), ProtocolJson.decodeFromJsonElement<CompletedMatch>(legacy).eventRewards)
    }

    @Test fun `intentional leave remains zero and frozen context survives result persistence`() = runTest {
        val results = mutableListOf<CompletedMatch>()
        val engine = GameEngine(eventRewardSource = MutableSource(), nowMillis = { 100 }, matchResultRepository = MatchResultRepository { results += it })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true)); engine.handle(guest, ClientMessage.SetReady(room, true))
        engine.handle(host, ClientMessage.LeaveRoom(room))
        val match = results.single(); val leaver = match.players.single { it.playerId == host }
        assertTrue(leaver.intentionalLeave); assertEquals(2, match.eventRewards.goldMultiplier)
        assertEquals(0, RewardEconomy.matchRewardBreakdown(match.matchType, leaver.outcome, true, match.eventRewards).totalGold)
    }

    @Test fun `restored pending intentional leave exposes zero breakdown and settles frozen result once`() = runTest {
        val stored = InMemoryActiveRoomRepository()
        val original = GameEngine(activeRoomRepository = stored, eventRewardSource = MutableSource(), nowMillis = { 100 },
            matchResultRepository = MatchResultRepository { error("result unavailable") })
        val (host, guest, room) = waiting(original)
        original.handle(host, ClientMessage.SetReady(room, true)); original.handle(guest, ClientMessage.SetReady(room, true))
        original.handle(host, ClientMessage.LeaveRoom(room))
        val results = mutableListOf<CompletedMatch>()
        val restored = GameEngine(activeRoomRepository = stored, eventRewardSource = MutableSource().apply { fail = true },
            matchResultRepository = MatchResultRepository { results += it })
        val resumed = restored.connectAccount(AuthenticatedAccount(UUID.fromString(guest), "guest"))
        assertEquals(0, resumed.currentGame?.rewardBreakdowns?.get(host)?.totalGold)
        restored.retryMaintenanceSettlements(); restored.retryMaintenanceSettlements()
        assertEquals(1, results.size); assertTrue(results.single().players.single { it.playerId == host }.intentionalLeave)
        assertEquals(2, results.single().eventRewards.goldMultiplier)
    }

    private fun List<Delivery>.started() = map { it.message }.filterIsInstance<ServerMessage.GameStarted>().single().game
    private suspend fun finish(engine: GameEngine, host: String, room: String) {
        for(n in 1..GAME_NUMBER_COUNT) engine.handle(host, ClientMessage.SelectNumber(room, n, "finish-$n"))
    }

    private class MutableSource : EventRewardSource {
        var calls = 0; var fail = false; var before: suspend () -> Unit = {}
        override suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T {
            calls++; if(fail) error("source unavailable"); before()
            return block(RewardEventCatalog(listOf(event("gold", 2, 1), event("xp", 1, 2))))
        }
        private fun event(id: String, gold: Int, xp: Int) = RewardEventRecord(id, 2,
            RewardEventDraft("Thưởng", "Reward", "Thưởng", "Reward", "UTC", 100, 200, gold, xp, MatchType.entries.toSet()), "actor", "reason", 0, 0)
    }
    @Test fun `configured source failure rejects actual ready start without spending quota`() = runTest {
        val quota = InMemoryPlayQuotaRepository()
        val rooms = InMemoryActiveRoomRepository()
        val engine = GameEngine(playQuotaRepository = quota, activeRoomRepository = rooms,
            eventRewardSource = object : EventRewardSource {
                override suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T = error("event storage unavailable")
            })
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        val result = engine.handle(guest, ClientMessage.SetReady(room, true))
        assertTrue(result.none { it.message is ServerMessage.GameStarted }, "A failed configured source must never advertise gameplay")
        assertEquals(RoomPhase.WAITING, rooms.loadAll().single().phase)
        assertEquals(0, quota.getSnapshot(host, System.currentTimeMillis()).matchesConsumed)
    }

    @Test fun `failed durable start never exposes gameplay on later reconnect`() = runTest {
        val stored = InMemoryActiveRoomRepository()
        val repository = object : ActiveRoomRepository by stored {
            var fail = true
            override suspend fun save(room: StoredActiveRoom) {
                if (room.phase == RoomPhase.PLAYING && fail) { fail = false; error("disk unavailable") }
                stored.save(room)
            }
        }
        val engine = GameEngine(activeRoomRepository = repository)
        val (host, guest, room) = waiting(engine)
        engine.handle(host, ClientMessage.SetReady(room, true))
        runCatching { engine.handle(guest, ClientMessage.SetReady(room, true)) }
        val connected = engine.connectAccount(AuthenticatedAccount(UUID.fromString(host), "host"))
        assertNotEquals(RoomPhase.PLAYING, connected.currentGame?.phase, "Undurable start leaked from memory")
    }

    private suspend fun waiting(engine: GameEngine): Triple<String, String, String> {
        val host = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host")).playerId
        val guest = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "guest")).playerId
        val room = engine.handle(host, ClientMessage.CreateRoom("events", "", ProtocolGameMode.ORDER))
            .map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        engine.handle(guest, ClientMessage.JoinRoom(room, ""))
        return Triple(host, guest, room)
    }
}
