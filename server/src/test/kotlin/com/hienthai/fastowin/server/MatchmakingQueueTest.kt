package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import com.hienthai.fastowin.server.maintenance.MaintenancePolicy
import java.util.UUID
import kotlin.test.*

class MatchmakingQueueTest {
    private class Fixture(val friends: FriendRepository = NoOpFriendRepository) {
        var now = 1_800_000_000_000L
        var profileReads = 0
        val quota = InMemoryPlayQuotaRepository()
        val profiles = mutableMapOf<String, PlayerProfileSnapshot>()
        val engine = GameEngine(nowMillis = { now }, friendRepository = friends, playQuotaRepository = quota,
            playerProfileRepository = object : PlayerProfileRepository {
                override suspend fun findByPlayerId(playerId: String): PlayerProfileSnapshot? { profileReads++; return profiles[playerId] }
                override suspend fun updateProfile(playerId: String, displayName: String, avatarId: String?) = false
            })
        suspend fun player(rating: Int): String {
            val id = UUID.randomUUID()
            profiles[id.toString()] = PlayerProfileSnapshot(id.toString(), "Player", "CODE1234",
                statistics = PlayerStatisticsSnapshot(eloRating = rating))
            engine.connectAccount(AuthenticatedAccount(id, "Player"))
            return id.toString()
        }
        suspend fun join(id: String, type: MatchType = MatchType.RANKED) =
            engine.handle(id, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER, type))
        suspend fun sweep(): List<Delivery> {
            val deliveries = mutableListOf<Delivery>()
            engine.advanceMatchmaking { deliveries += it }
            return deliveries
        }
    }

    @Test fun `queued players match after range expansion without rejoining and consume quota only once`() = runTest {
        val f = Fixture(); val a = f.player(1000); val b = f.player(1200)
        f.join(a); f.join(b)
        val reads = f.profileReads
        f.now += 10_000
        val expanded = f.sweep().map(Delivery::message)
        assertTrue(expanded.filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertTrue(expanded.filterIsInstance<ServerMessage.MatchmakingStatus>().isEmpty(), "Idle sweeps must not revive the UI after cancellation")
        assertEquals(reads, f.profileReads, "Waiting sweeps must not refetch profiles")
        assertEquals(10, f.quota.getSnapshot(a, f.now).remainingMatches)
        assertTrue(f.sweep().isEmpty(), "Do not resend unchanged search status")
        f.now += 10_000
        val matched = f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().single()
        assertEquals(setOf(a, b), matched.game.players.map { it.id }.toSet())
        assertTrue(f.sweep().isEmpty())
        assertEquals(9, f.quota.getSnapshot(a, f.now).remainingMatches)
        assertEquals(9, f.quota.getSnapshot(b, f.now).remainingMatches)
    }

    @Test fun `ranked range stays capped and casual ranked queues never mix during sweeps`() = runTest {
        val f = Fixture(); val ranked = f.player(1000); val far = f.player(1301); val casual = f.player(1000)
        f.join(ranked); f.join(far); f.join(casual, MatchType.CASUAL)
        f.now += 100_000
        val result = f.sweep().map(Delivery::message)
        assertTrue(result.filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertTrue(result.filterIsInstance<ServerMessage.MatchmakingStatus>().isEmpty())
    }

    @Test fun `switching queue type resets waiting time instead of inheriting expanded ranked range`() = runTest {
        val f = Fixture(); val a = f.player(1000); val b = f.player(1250)
        f.join(a); f.now += 40_000; f.join(a, MatchType.CASUAL); f.join(b)
        val switched = f.join(a).map(Delivery::message)
        assertEquals(100, switched.filterIsInstance<ServerMessage.MatchmakingStatus>().single().ratingRange)
        assertTrue(f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        f.now += 30_000
        assertEquals(1, f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().size)
    }

    @Test fun `cancelled or disconnected entries do not pair on a later sweep`() = runTest {
        for (disconnect in listOf(false, true)) {
            val f = Fixture(); val a = f.player(1000); val b = f.player(1200)
            f.join(a); f.join(b)
            if (disconnect) f.engine.markDisconnected(a) else f.engine.handle(a, ClientMessage.CancelMatchmaking)
            f.now += 30_000
            assertTrue(f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        }
    }

    @Test fun `blocked pair is checked once per sweep and transient failure is retried next sweep`() = runTest {
        var checks = 0; var failure = false; var blocked = true
        val f = Fixture(object : FriendRepository by NoOpFriendRepository {
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String): Boolean {
                checks++; if (failure) throw IllegalStateException("Fixture storage failure"); return blocked
            }
        })
        val a = f.player(1000); val b = f.player(1200); f.join(a); f.join(b)
        assertEquals(0, checks, "Do not query blocked status for ineligible Elo pairs")
        f.now += 20_000
        f.sweep(); assertEquals(1, checks)
        failure = true
        assertTrue(f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertEquals(2, checks)
        failure = false; blocked = false
        assertEquals(1, f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().size)
        assertEquals(3, checks)
    }

    @Test fun `cancel and rejoin during blocked lookup cannot match the stale queue entry`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var suspendLookup = true
        val f = Fixture(object : FriendRepository by NoOpFriendRepository {
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String): Boolean {
                if (suspendLookup) { entered.complete(Unit); release.await() }; return false
            }
        })
        val a = f.player(1000); val b = f.player(1200); f.join(a); f.join(b); f.now += 20_000
        val sweep = async { f.sweep() }; entered.await()
        assertTrue(f.sweep().isEmpty(), "Concurrent sweeps must not overlap")
        f.engine.handle(a, ClientMessage.CancelMatchmaking)
        suspendLookup = false
        f.profiles[a] = f.profiles.getValue(a).copy(statistics = PlayerStatisticsSnapshot(eloRating = 1401))
        assertTrue(f.join(a).map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        release.complete(Unit)
        assertTrue(sweep.await().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertTrue(f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        f.now += 10_000
        assertEquals(1, f.sweep().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().size)
    }

    @Test fun `maintenance arriving during blocked lookup prevents queued match start`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val f = Fixture(object : FriendRepository by NoOpFriendRepository {
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String): Boolean {
                entered.complete(Unit); release.await(); return false
            }
        })
        val a = f.player(1000); val b = f.player(1200); f.join(a); f.join(b); f.now += 20_000
        val sweep = async { f.sweep() }; entered.await()
        f.engine.applyMaintenancePolicy(MaintenancePolicy(MaintenanceState.MAINTENANCE))
        release.complete(Unit)
        assertTrue(sweep.await().map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertTrue(f.sweep().isEmpty())
    }

    @Test fun `arrival racing periodic sweep publishes one match and charges each player once`() = runTest {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var first = true
        val f = Fixture(object : FriendRepository by NoOpFriendRepository {
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String): Boolean {
                if (first) { first = false; entered.complete(Unit); release.await() }; return false
            }
        })
        val a = f.player(1000); val b = f.player(1000); f.join(a)
        val joining = async { f.join(b) }; entered.await()
        val sweep = f.sweep()
        release.complete(Unit)
        val combined = (sweep + joining.await()).map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>()
        assertEquals(1, combined.size)
        assertEquals(setOf(a, b), combined.single().game.players.map { it.id }.toSet())
        assertEquals(9, f.quota.getSnapshot(a, f.now).remainingMatches)
        assertEquals(9, f.quota.getSnapshot(b, f.now).remainingMatches)
        assertTrue(f.sweep().isEmpty())
    }

    @Test fun `quota exhausted while waiting is checked again before automatic match start`() = runTest {
        val f = Fixture(); val a = f.player(1000); val b = f.player(1200); f.join(a); f.join(b)
        repeat(10) { f.quota.consumeMatch(setOf(a), "other-match-$it", f.now) }
        f.now += 20_000
        val messages = f.sweep().map(Delivery::message)
        assertTrue(messages.filterIsInstance<ServerMessage.GameStarted>().isEmpty())
        assertTrue(messages.filterIsInstance<ServerMessage.Error>().any { it.code == "PLAY_QUOTA_EXHAUSTED" })
        assertEquals(10, f.quota.getSnapshot(b, f.now).remainingMatches)
        assertTrue(f.sweep().isEmpty())
    }

    @Test fun `first pair is delivered before a later pair lookup completes`() = runTest {
        var holdQueues = true
        val stalledPair = mutableSetOf<String>()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val f = Fixture(object : FriendRepository by NoOpFriendRepository {
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String): Boolean {
                if (holdQueues) return true
                if (setOf(firstUserId, secondUserId) == stalledPair) { entered.complete(Unit); release.await() }
                return false
            }
        })
        val players = List(4) { f.player(1000) }
        for (player in players) { f.join(player); f.now++ }
        stalledPair.addAll(players.takeLast(2)); holdQueues = false
        val delivered = mutableListOf<ServerMessage.GameStarted>()
        val sweep = async { f.engine.advanceMatchmaking { batch -> delivered += batch.map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>() } }
        entered.await()
        assertEquals(1, delivered.size, "The first room is running and must already be announced while the next lookup waits")
        assertEquals(players.take(2).toSet(), delivered.single().game.players.map { it.id }.toSet())
        release.complete(Unit); sweep.await()
        assertEquals(2, delivered.size)
    }

    @Test fun `nearer but ineligible player cannot hide older eligible opponent`() = runTest {
        // Block only the two existing queue members so both remain in the queue.
        val blocked = mutableSetOf<Set<String>>()
        val f = Fixture(object : FriendRepository by NoOpFriendRepository {
            override suspend fun isBlockedEitherWay(firstUserId: String, secondUserId: String) = setOf(firstUserId, secondUserId) in blocked
        })
        val older = f.player(1200); val nearer = f.player(1150); val incoming = f.player(1000)
        blocked += setOf(older, nearer)
        f.join(older); f.now += 20_000; f.join(nearer)
        val started = f.join(incoming).map(Delivery::message).filterIsInstance<ServerMessage.GameStarted>()
        assertEquals(1, started.size, "The 200-Elo older opponent is eligible; the fresh 150-Elo opponent is not")
        assertEquals(setOf(older, incoming), started.single().game.players.map { it.id }.toSet())
    }
}
