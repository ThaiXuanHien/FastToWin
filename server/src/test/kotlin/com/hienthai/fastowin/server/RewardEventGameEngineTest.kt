package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.events.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.test.*

class RewardEventGameEngineTest {
    @Test fun `matchmaking source failure releases hidden room and both accounts can retry without reconnect`() = runTest {
        val quota = InMemoryPlayQuotaRepository(); val stored = InMemoryActiveRoomRepository()
        val source = MutableSource().apply { fail = true }
        val engine = GameEngine(playQuotaRepository = quota, activeRoomRepository = stored, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        assertTrue(engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)).any {
            (it.message as? ServerMessage.MatchmakingStatus)?.isSearching == true
        })
        val failed = engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        assertStopped(failed, setOf(host, guest))
        for (id in setOf(host, guest)) {
            assertTrue(failed.any { it.message is ServerMessage.Error && id in it.recipients.orEmpty() })
            assertEquals(0, quota.getSnapshot(id, 100).matchesConsumed)
        }
        assertTrue(stored.loadAll().isEmpty())
        source.fail = false
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val started = engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)).started()
        assertEquals(setOf(host, guest), started.players.map { it.id }.toSet())
        assertEquals(2, started.eventRewards?.goldMultiplier)
    }

    private suspend fun accounts(engine: GameEngine): Pair<String, String> =
        engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "host")).playerId to
            engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "guest")).playerId

    @Test fun `cancel matchmaking after callback before source completion cannot revive hidden room or remove new rooms`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val quota = InMemoryPlayQuotaRepository(); val stored = InMemoryActiveRoomRepository()
        val source = object : EventRewardSource {
            override suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T {
                val result = block(RewardEventCatalog(emptyList()))
                entered.complete(Unit); release.await(); return result
            }
        }
        val engine = GameEngine(playQuotaRepository = quota, activeRoomRepository = stored, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        entered.await()
        assertStopped(engine.handle(host, ClientMessage.CancelMatchmaking), setOf(host, guest))
        val newHostRoom = async { engine.handle(host, ClientMessage.CreateRoom("new host", "", ProtocolGameMode.ORDER)) }
        val newGuestRoom = async { engine.handle(guest, ClientMessage.CreateRoom("new guest", "", ProtocolGameMode.ORDER)) }
        // These commands may wait for persistence, but admission must already have released the old room.
        release.complete(Unit)
        assertTrue(start.await().none { it.message is ServerMessage.GameStarted })
        val hostRoom = newHostRoom.await().map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        val guestRoom = newGuestRoom.await().map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
        assertEquals(setOf(hostRoom, guestRoom), stored.loadAll().map { it.roomId }.toSet())
        assertEquals(0, quota.getSnapshot(host, 100).matchesConsumed)
        assertEquals(0, quota.getSnapshot(guest, 100).matchesConsumed)
    }

    @Test fun `cancelled matchmaking source propagates cancellation and retry worker notifies both participants`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val quota = InMemoryPlayQuotaRepository()
        val source = MutableSource().apply { before = { entered.complete(Unit); release.await() } }
        val engine = GameEngine(playQuotaRepository = quota, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        entered.await(); start.cancel()
        assertFailsWith<CancellationException> { start.await() }
        assertStopped(engine.retryMaintenanceSettlements(), setOf(host, guest))
        assertTrue(engine.retryMaintenanceSettlements().isEmpty())
        for (id in setOf(host, guest)) assertEquals(0, quota.getSnapshot(id, 100).matchesConsumed)
        source.before = {}
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        assertEquals(2, engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)).started().eventRewards?.goldMultiplier)
    }

    @Test fun `delayed cancelled matchmaking notification cannot stop a subsequent search`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val source = MutableSource().apply { before = { entered.complete(Unit); awaitCancellation() } }
        val engine = GameEngine(eventRewardSource = source)
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        entered.await(); start.cancelAndJoin()
        val searching = engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        assertTrue(searching.any { (it.message as? ServerMessage.MatchmakingStatus)?.isSearching == true })
        val delayed = engine.retryMaintenanceSettlements()
        assertStopped(delayed, setOf(guest))
        assertTrue(delayed.none { host in it.recipients.orEmpty() })
        engine.handle(host, ClientMessage.CancelMatchmaking)
        source.before = {}
        assertTrue(engine.handle(host, ClientMessage.CreateRoom("new", "", ProtocolGameMode.ORDER)).any { it.message is ServerMessage.RoomCreated })
        assertTrue(engine.handle(guest, ClientMessage.CreateRoom("new", "", ProtocolGameMode.ORDER)).any { it.message is ServerMessage.RoomCreated })
    }

    @Test fun `deferred matchmaking status stays invalidatable while unrelated retained start retry is suspended`() = runTest {
        val stored = InMemoryActiveRoomRepository(); var fail = true; var blockRetry = false
        val enteredSave = CompletableDeferred<Unit>(); val releaseSave = CompletableDeferred<Unit>()
        val repository = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                if (room.phase == RoomPhase.PLAYING && fail) error("start acknowledgement unavailable")
                if (room.phase == RoomPhase.PLAYING && blockRetry) { enteredSave.complete(Unit); releaseSave.await() }
                stored.save(room)
            }
        }
        val source = MutableSource()
        val engine = GameEngine(activeRoomRepository = repository, eventRewardSource = source, nowMillis = { 100 })
        val (c, d, room) = waiting(engine)
        engine.handle(c, ClientMessage.SetReady(room, true))
        assertTrue(engine.handle(d, ClientMessage.SetReady(room, true)).none { it.message is ServerMessage.GameStarted })
        val (a, b) = accounts(engine)
        val enteredSource = CompletableDeferred<Unit>()
        source.before = { enteredSource.complete(Unit); awaitCancellation() }
        engine.handle(a, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val cancelledStart = async { engine.handle(b, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        enteredSource.await(); cancelledStart.cancelAndJoin(); source.before = {}
        fail = false; blockRetry = true
        val drain = async { engine.retryMaintenanceSettlements() }
        enteredSave.await()
        val searching = engine.handle(a, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        assertTrue(searching.any { (it.message as? ServerMessage.MatchmakingStatus)?.isSearching == true })
        blockRetry = false; releaseSave.complete(Unit)
        val deliveries = drain.await()
        assertEquals(room, deliveries.started().roomId)
        val terminal = deliveries.filter { it.message is ServerMessage.MatchmakingStatus || it.message is ServerMessage.Error }
        assertTrue(terminal.none { a in it.recipients.orEmpty() }, "A fresh search must supersede the cancelled attempt through the entire retry wait")
        assertStopped(terminal, setOf(b))
        assertEquals(1, terminal.count { it.message is ServerMessage.MatchmakingStatus && b in it.recipients.orEmpty() })
        assertEquals(1, terminal.count { it.message is ServerMessage.Error && b in it.recipients.orEmpty() })
        assertTrue(engine.retryMaintenanceSettlements().isEmpty())
        val e = engine.connectAccount(AuthenticatedAccount(UUID.randomUUID(), "new opponent")).playerId
        val matched = engine.handle(e, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)).started()
        assertEquals(setOf(a, e), matched.players.map { it.id }.toSet())
    }

    @Test fun `deferred matchmaking status stays invalidatable while unrelated pending result retry is suspended`() = runTest {
        val enteredResult = CompletableDeferred<Unit>(); val releaseResult = CompletableDeferred<Unit>()
        val stored = InMemoryActiveRoomRepository(); val completed = mutableListOf<CompletedMatch>()
        var fail = true; var blockRetry = false
        val resultRepository = MatchResultRepository { match ->
            if (fail) error("result unavailable")
            if (blockRetry) { enteredResult.complete(Unit); releaseResult.await() }
            completed += match
        }
        val source = MutableSource()
        val engine = GameEngine(activeRoomRepository = stored, matchResultRepository = resultRepository,
            eventRewardSource = source, nowMillis = { 100 })
        val (c, d, room) = waiting(engine)
        engine.handle(c, ClientMessage.SetReady(room, true)); engine.handle(d, ClientMessage.SetReady(room, true))
        finish(engine, c, room)
        assertTrue(stored.loadAll().single().pendingCompletedMatch != null)
        val (a, b) = accounts(engine)
        val enteredSource = CompletableDeferred<Unit>()
        source.before = { enteredSource.complete(Unit); awaitCancellation() }
        engine.handle(a, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val cancelledStart = async { engine.handle(b, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        enteredSource.await(); cancelledStart.cancelAndJoin(); source.before = {}
        fail = false; blockRetry = true
        val drain = async { engine.retryMaintenanceSettlements() }
        enteredResult.await()
        val created = engine.handle(a, ClientMessage.CreateRoom("fresh room", "", ProtocolGameMode.ORDER))
            .map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game
        blockRetry = false; releaseResult.complete(Unit)
        val terminal = drain.await().filter { it.message is ServerMessage.MatchmakingStatus || it.message is ServerMessage.Error }
        assertTrue(terminal.none { a in it.recipients.orEmpty() }, "A fresh room must supersede the cancelled attempt through the entire settlement wait")
        assertStopped(terminal, setOf(b))
        assertEquals(1, terminal.count { it.message is ServerMessage.MatchmakingStatus && b in it.recipients.orEmpty() })
        assertEquals(1, terminal.count { it.message is ServerMessage.Error && b in it.recipients.orEmpty() })
        assertTrue(engine.retryMaintenanceSettlements().isEmpty())
        assertEquals(1, completed.size)
        assertEquals(created.roomId, stored.loadAll().single { it.host.playerId == a }.roomId)
    }

    @Test fun `disconnect rejects pre capture matchmaking admission and releases hidden room for connected survivor`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val quota = InMemoryPlayQuotaRepository()
        val source = MutableSource().apply { before = { entered.complete(Unit); release.await() } }
        val engine = GameEngine(playQuotaRepository = quota, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        entered.await(); engine.markDisconnected(host); release.complete(Unit)
        assertStopped(start.await(), setOf(host, guest))
        assertEquals(0, quota.getSnapshot(host, 100).matchesConsumed)
        assertEquals(0, quota.getSnapshot(guest, 100).matchesConsumed)
        assertTrue(engine.handle(guest, ClientMessage.CreateRoom("new", "", ProtocolGameMode.ORDER)).any { it.message is ServerMessage.RoomCreated })
    }

    @Test fun `quota exhaustion racing matchmaking preparation removes hidden room and stops both searches`() = runTest {
        val real = InMemoryPlayQuotaRepository(); var exhaust = true; var now = 100L
        val quota = object : PlayQuotaRepository by real {
            override suspend fun prepareMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption {
                if (exhaust) repeat(10) { real.consumeMatch(setOf(userIds.first()), "other-$it", nowMillis) }
                return real.prepareMatch(userIds, matchId, nowMillis)
            }
        }
        val stored = InMemoryActiveRoomRepository()
        val engine = GameEngine(playQuotaRepository = quota, activeRoomRepository = stored, nowMillis = { now })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val rejected = engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        assertStopped(rejected, setOf(host, guest)); assertTrue(stored.loadAll().isEmpty())
        exhaust = false; now = 86_400_100L
        for (id in setOf(host, guest)) assertTrue(engine.handle(id, ClientMessage.CreateRoom("new", "", ProtocolGameMode.ORDER))
            .any { it.message is ServerMessage.RoomCreated })
        assertEquals(2, stored.loadAll().size)
    }

    @Test fun `cancel matchmaking during late quota commit refunds original attempt without removing new search`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val real = InMemoryPlayQuotaRepository(); var delayPreparation = true
        val quota = object : PlayQuotaRepository by real {
            override suspend fun prepareMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption {
                if (delayPreparation) { entered.complete(Unit); release.await() }
                return real.prepareMatch(userIds, matchId, nowMillis)
            }
        }
        val engine = GameEngine(playQuotaRepository = quota, eventRewardSource = MutableSource(), nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        entered.await()
        assertStopped(engine.handle(guest, ClientMessage.CancelMatchmaking), setOf(host, guest))
        assertTrue(engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)).any {
            (it.message as? ServerMessage.MatchmakingStatus)?.isSearching == true
        })
        release.complete(Unit)
        assertTrue(start.await().none { it.message is ServerMessage.GameStarted })
        for (id in setOf(host, guest)) assertEquals(0, real.getSnapshot(id, 100).matchesConsumed)
        delayPreparation = false
        assertEquals(2, engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)).started().eventRewards?.goldMultiplier)
        for (id in setOf(host, guest)) assertEquals(1, real.getSnapshot(id, 100).matchesConsumed)
    }

    @Test fun `maintenance admission closes during source completion and releases unpublished matchmaking room`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val quota = InMemoryPlayQuotaRepository(); val stored = InMemoryActiveRoomRepository()
        val source = object : EventRewardSource {
            override suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T {
                val result = block(RewardEventCatalog(emptyList()))
                entered.complete(Unit); release.await(); return result
            }
        }
        val engine = GameEngine(playQuotaRepository = quota, activeRoomRepository = stored, eventRewardSource = source)
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        entered.await()
        engine.publishMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.DRAINING))
        release.complete(Unit)
        val rejected = start.await(); assertStopped(rejected, setOf(host, guest))
        for (id in setOf(host, guest)) assertTrue(rejected.any { (it.message as? ServerMessage.Error)?.code == "MAINTENANCE" && id in it.recipients.orEmpty() })
        assertTrue(stored.loadAll().isEmpty())
        engine.applyMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.ACTIVE))
        for (id in setOf(host, guest)) assertTrue(engine.handle(id, ClientMessage.CreateRoom("new", "", ProtocolGameMode.ORDER)).any { it.message is ServerMessage.RoomCreated })
    }

    @Test fun `slow matchmaking save is fenced against cancellation and publishes captured match once`() = runTest {
        val entered = CompletableDeferred<StoredActiveRoom>(); val release = CompletableDeferred<Unit>()
        val quota = InMemoryPlayQuotaRepository(); val stored = InMemoryActiveRoomRepository(); val source = MutableSource()
        val repo = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                if (room.phase == RoomPhase.PLAYING) { entered.complete(room); release.await() }
                stored.save(room)
            }
        }
        val engine = GameEngine(activeRoomRepository = repo, playQuotaRepository = quota, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        val frozen = entered.await()
        assertTrue(engine.handle(guest, ClientMessage.CancelMatchmaking).all { it.message is ServerMessage.Error })
        for (id in setOf(host, guest)) assertEquals(1, quota.getSnapshot(id, 100).matchesConsumed)
        source.fail = true; release.complete(Unit)
        val started = start.await().started()
        assertEquals(frozen.matchId, started.matchId); assertEquals(2, started.eventRewards?.goldMultiplier)
        assertEquals(1, source.calls); assertTrue(engine.retryMaintenanceSettlements().isEmpty())
    }

    @Test fun `full maintenance during matchmaking quota preparation discards unpublished room and permits recovery`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val real = InMemoryPlayQuotaRepository()
        val quota = object : PlayQuotaRepository by real {
            override suspend fun prepareMatch(userIds: Set<String>, matchId: String, nowMillis: Long): PlayQuotaConsumption {
                entered.complete(Unit); release.await()
                return real.prepareMatch(userIds, matchId, nowMillis)
            }
        }
        val stored = InMemoryActiveRoomRepository()
        val engine = GameEngine(playQuotaRepository = quota, activeRoomRepository = stored, nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        val start = async { engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)) }
        entered.await()
        val maintenance = engine.applyMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.MAINTENANCE))
        assertStopped(maintenance.deliveries, setOf(host, guest))
        assertTrue(stored.loadAll().isEmpty(), "An unpublished matchmaking room must not become a retained aborted lobby")
        release.complete(Unit)
        assertTrue(start.await().none { it.message is ServerMessage.GameStarted })
        for (id in setOf(host, guest)) assertEquals(0, real.getSnapshot(id, 100).matchesConsumed)
        engine.applyMaintenancePolicy(com.hienthai.fastowin.server.maintenance.MaintenancePolicy(MaintenanceState.ACTIVE))
        for (id in setOf(host, guest)) assertTrue(engine.handle(id, ClientMessage.CreateRoom("new", "", ProtocolGameMode.ORDER)).any { it.message is ServerMessage.RoomCreated })
    }

    @Test fun `failed matchmaking save retains frozen identity and quota through cancellation and source outage`() = runTest {
        val stored = InMemoryActiveRoomRepository(); var fail = true
        val repo = object : ActiveRoomRepository by stored {
            override suspend fun save(room: StoredActiveRoom) {
                stored.save(room)
                if (fail && room.phase == RoomPhase.PLAYING) error("lost acknowledgement")
            }
        }
        val quota = InMemoryPlayQuotaRepository(); val source = MutableSource()
        val engine = GameEngine(activeRoomRepository = repo, playQuotaRepository = quota, eventRewardSource = source, nowMillis = { 100 })
        val (host, guest) = accounts(engine)
        engine.handle(host, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER))
        assertTrue(engine.handle(guest, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER)).none { it.message is ServerMessage.GameStarted })
        val frozen = stored.loadAll().single()
        assertTrue(engine.handle(host, ClientMessage.CancelMatchmaking).none { it.message is ServerMessage.MatchmakingStatus })
        assertEquals(1, quota.getSnapshot(host, 100).matchesConsumed)
        assertEquals(1, quota.getSnapshot(guest, 100).matchesConsumed)
        source.fail = true; fail = false
        val retry = engine.retryMaintenanceSettlements().started()
        assertEquals(frozen.matchId, retry.matchId); assertEquals(frozen.roomId, retry.roomId)
        assertEquals(2, retry.eventRewards?.goldMultiplier); assertEquals(1, source.calls)
    }

    private fun assertStopped(deliveries: List<Delivery>, ids: Set<String>) {
        for (id in ids) assertTrue(deliveries.any {
            (it.message as? ServerMessage.MatchmakingStatus)?.isSearching == false && id in it.recipients.orEmpty()
        }, "Participant $id must receive terminal matchmaking status")
        assertTrue(deliveries.none { it.message is ServerMessage.GameStarted })
    }

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
