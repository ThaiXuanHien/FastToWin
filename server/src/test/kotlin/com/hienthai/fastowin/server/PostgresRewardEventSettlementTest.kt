package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.events.*
import com.hienthai.fastowin.server.admin.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.*

class PostgresRewardEventSettlementTest {
    @Test fun `pool two queued publication and busy engine JDBC command release gate before mutex retry`() = runBlocking {
        withTimeout(20_000) { database { ds ->
            val now = System.currentTimeMillis()
            val origin = "https://admin.test"
            val admin = AdminService(PostgresAdminRepository(ds), AdminConfiguration(origin, false), PasswordHasher(1000), { now })
            admin.bootstrapOwner("owner@example.com", "GoodPassword123!")
            val login = admin.login("owner@example.com", "GoodPassword123!", origin, "127.0.0.1")
            val owner = RewardEventAdminContext(login.token, login.response.csrfToken, origin)
            val repository = PostgresRewardEventRepository(ds)
            val service = RewardEventService(repository, admin, { now })
            val draft = service.create(owner, RewardEventDraftRequest(0, UUID.randomUUID().toString(), "test",
                RewardEventDraft("Thưởng", "Reward", "Thưởng", "Reward", "UTC", now - 1, now + 100_000, 2, 2, MatchType.entries.toSet())))
            val publish = RewardEventMutationRequest(draft.version, UUID.randomUUID().toString(), "publish",
                service.preview(owner, draft.id).previewToken)
            val sourceEntered = CompletableDeferred<Unit>(); val releaseSource = CompletableDeferred<Unit>()
            var attempts = 0
            val source = object : EventRewardSource {
                override suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T = repository.withStartCatalog { catalog ->
                    if (++attempts == 1) { sourceEntered.complete(Unit); releaseSource.await() }
                    block(catalog)
                }
            }
            val identity = PostgresGuestIdentityRepository(ds)
            val host = identity.resolveGuest("host", null, now); val guest = identity.resolveGuest("guest", null, now)
            val profileEntered = CompletableDeferred<Unit>()
            val profiles = object : PlayerProfileRepository by NoOpPlayerProfileRepository {
                override suspend fun updateFcmToken(playerId: String, token: String, languageTag: String): Boolean = withContext(Dispatchers.IO) {
                    profileEntered.complete(Unit)
                    ds.connection.use { c -> c.prepareStatement("SELECT 1").use { s -> s.execute() } }
                }
            }
            val completed = mutableListOf<CompletedMatch>()
            val resultRepository = PostgresMatchResultRepository(ds)
            val engine = GameEngine(activeRoomRepository = PostgresActiveRoomRepository(ds), playQuotaRepository = PostgresPlayQuotaRepository(ds),
                playerProfileRepository = profiles, eventRewardSource = source, nowMillis = { now },
                matchResultRepository = MatchResultRepository { completed += it; resultRepository.save(it) })
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(host.playerId), "host"))
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(guest.playerId), "guest"))
            val room = engine.handle(host.playerId, ClientMessage.CreateRoom("pool", "", ProtocolGameMode.ORDER))
                .map { it.message }.filterIsInstance<ServerMessage.RoomCreated>().single().game.roomId
            engine.handle(guest.playerId, ClientMessage.JoinRoom(room, "")); engine.handle(host.playerId, ClientMessage.SetReady(room, true))
            val start = async(Dispatchers.Default) { engine.handle(guest.playerId, ClientMessage.SetReady(room, true)) }
            sourceEntered.await()
            val writer = async(Dispatchers.IO) { service.schedule(owner, draft.id, publish) }
            while(ds.hikariPoolMXBean.activeConnections != 2) delay(5)
            val profile = async(Dispatchers.Default) { engine.handle(host.playerId, ClientMessage.UpdateFcmToken("test", "en")) }
            profileEntered.await()
            while(ds.hikariPoolMXBean.threadsAwaitingConnection == 0) delay(5)
            assertFalse(profile.isCompleted); assertFalse(writer.isCompleted)
            releaseSource.complete(Unit)
            profile.await(); writer.await()
            val game = start.await().map { it.message }.filterIsInstance<ServerMessage.GameStarted>().single().game
            assertTrue(attempts >= 2, "Busy engine must release the source transaction before retry")
            assertEquals(2, game.eventRewards?.goldMultiplier)
            service.cancel(owner, draft.id, RewardEventMutationRequest(2, UUID.randomUUID().toString(), "cancel after start"))
            for(n in 1..GAME_NUMBER_COUNT) engine.handle(host.playerId, ClientMessage.SelectNumber(room, n, "pool-$n"))
            assertEquals(2, completed.single().eventRewards.goldMultiplier)
            val history = PostgresPlayerProfileRepository(ds).findByPlayerId(host.playerId)!!.recentMatches.single()
            assertEquals(160, history.rewardBreakdown?.totalGold)
        } }
    }
    private suspend fun database(block: suspend (HikariDataSource) -> Unit) {
        val url = checkNotNull(System.getenv("TEST_DATABASE_URL")) { "Actual PostgreSQL is required for this suite" }
        fun config() = HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER")
            password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 2
            connectionTimeout = 5_000
        }
        val schema = "settlement_${UUID.randomUUID().toString().replace("-", "")}"
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema }).use { ds ->
                    Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    block(ds)
                }
            } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }

    @Test fun `engine frozen ranked win credits exact total MATCH reward once`() = runTest {
        database { ds ->
            val identity = PostgresGuestIdentityRepository(ds)
            val host = identity.resolveGuest("event host", null, 1_000)
            val guest = identity.resolveGuest("event guest", null, 1_000)
            val accepted = mutableListOf<CompletedMatch>()
            val results = PostgresMatchResultRepository(ds)
            val now = System.currentTimeMillis()
            val engine = GameEngine(activeRoomRepository = PostgresActiveRoomRepository(ds),
                playQuotaRepository = PostgresPlayQuotaRepository(ds),
                matchResultRepository = MatchResultRepository { accepted += it; results.save(it) },
                eventRewardSource = doubledSource(now), nowMillis = { now })
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(host.playerId), host.displayName))
            engine.connectAccount(AuthenticatedAccount(UUID.fromString(guest.playerId), guest.displayName))
            engine.handle(host.playerId, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER, MatchType.RANKED))
            val room = engine.handle(guest.playerId, ClientMessage.JoinMatchmaking(ProtocolGameMode.ORDER, MatchType.RANKED))
                .map { it.message }.filterIsInstance<ServerMessage.GameStarted>().single().game.roomId
            for (n in 1..GAME_NUMBER_COUNT) engine.handle(host.playerId, ClientMessage.SelectNumber(room, n, "event-$n"))
            val completed = accepted.single()
            assertEquals(2, completed.eventRewards.goldMultiplier)
            results.save(completed)
            val profiles = PostgresPlayerProfileRepository(ds)
            val history = profiles.findByPlayerId(host.playerId)!!.recentMatches.single()
            assertEquals(100, history.rewardBreakdown?.baseGold); assertEquals(100, history.rewardBreakdown?.bonusGold)
            assertEquals(200, history.rewardBreakdown?.totalGold); assertEquals(60, history.rewardBreakdown?.totalXp)
            assertEquals(history.rewardBreakdown, profiles.findMatchDetail(host.playerId, completed.matchId)?.summary?.rewardBreakdown)
            ds.connection.use { c ->
                c.prepareStatement("SELECT gold_delta, xp_delta FROM wallet_transactions WHERE user_id=? AND source_type='MATCH' AND source_id=?").use { s ->
                    s.setObject(1, UUID.fromString(host.playerId)); s.setString(2, completed.matchId)
                    s.executeQuery().use { r ->
                        assertTrue(r.next())
                        assertEquals(200, r.getInt(1), "Frozen ranked x2 reward must be part of the single MATCH credit")
                        assertEquals(60, r.getInt(2)); assertFalse(r.next())
                    }
                }
                c.prepareStatement("SELECT event_rewards::text FROM matches WHERE id=?").use { s ->
                    s.setObject(1, UUID.fromString(completed.matchId)); s.executeQuery().use { r -> r.next()
                        assertEquals(completed.eventRewards, ProtocolJson.decodeFromString<MatchRewardEventSnapshot>(r.getString(1)))
                    }
                }
                c.prepareStatement("SELECT COUNT(*) FROM wallet_transactions WHERE source_id=?").use { s ->
                    s.setString(1, completed.matchId); s.executeQuery().use { r -> r.next(); assertEquals(2, r.getInt(1)) }
                }
                c.prepareStatement("UPDATE match_players SET reward_breakdown=NULL WHERE match_id=?").use { s ->
                    s.setObject(1, UUID.fromString(completed.matchId)); s.executeUpdate()
                }
            }
            assertNull(profiles.findByPlayerId(host.playerId)!!.recentMatches.single().rewardBreakdown)
        }
    }

    @Test fun `casual leave exact accounting and transaction rollback never partially grant`() = runTest { database { ds ->
        val ids = PostgresGuestIdentityRepository(ds)
        val host = ids.resolveGuest("host", null, 1_000); val guest = ids.resolveGuest("guest", null, 1_000)
        val repository = PostgresMatchResultRepository(ds)
        val match = CompletedMatch(UUID.randomUUID().toString(), "room", ProtocolGameMode.ORDER, 1_000, 2_000, host.playerId,
            listOf(CompletedMatchPlayer(host.playerId, "host", 10, MatchOutcome.WIN), CompletedMatchPlayer(guest.playerId, "guest", 0, MatchOutcome.LOSS, true)),
            matchType = MatchType.CASUAL, eventRewards = MatchRewardEventSnapshot(1_000, 2, 2))
        ds.connection.use { c -> c.createStatement().use { s ->
            s.execute("CREATE FUNCTION reject_test_wallet() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test wallet unavailable''; END;'")
            s.execute("CREATE TRIGGER reject_test_wallet BEFORE INSERT ON wallet_transactions FOR EACH ROW EXECUTE FUNCTION reject_test_wallet()")
        } }
        assertFails { repository.save(match) }
        ds.connection.use { c -> c.createStatement().use { s ->
            for(table in listOf("matches", "match_players", "wallet_transactions")) s.executeQuery("SELECT count(*) FROM $table").use { r -> r.next(); assertEquals(0, r.getInt(1)) }
            s.execute("DROP TRIGGER reject_test_wallet ON wallet_transactions")
        } }
        repository.save(match); repository.save(match)
        ds.connection.use { c ->
            c.prepareStatement("SELECT gold_delta, xp_delta, source_type FROM wallet_transactions WHERE source_id=?").use { s ->
                s.setString(1, match.matchId); s.executeQuery().use { r ->
                    assertTrue(r.next()); assertEquals(160, r.getInt(1)); assertEquals(48, r.getInt(2)); assertEquals("MATCH", r.getString(3)); assertFalse(r.next())
                }
            }
            c.prepareStatement("SELECT reward_breakdown::text FROM match_players WHERE match_id=? AND user_id=?").use { s ->
                s.setObject(1, UUID.fromString(match.matchId)); s.setObject(2, UUID.fromString(guest.playerId))
                s.executeQuery().use { r -> r.next(); assertEquals(0, ProtocolJson.decodeFromString<MatchRewardBreakdown>(r.getString(1)).totalGold) }
            }
        }
    } }

    @Test fun `quota reservation atomic retry original day recovery and finalized legacy preservation`() = runTest { database { ds ->
        val identity = PostgresGuestIdentityRepository(ds)
        val users = (1..2).map { identity.resolveGuest("quota$it", null, 0).playerId }.toSet()
        val quota = PostgresPlayQuotaRepository(ds); val originalDay = 61_199_999L; val nextDay = 61_200_001L
        val match = UUID.randomUUID().toString()
        val attempts = coroutineScope { (1..5).map { async(Dispatchers.IO) { quota.prepareMatch(users, match, originalDay) } }.awaitAll() }
        assertEquals(1, attempts.count { it.status == PlayQuotaConsumptionStatus.CONSUMED })
        assertEquals(4, attempts.count { it.status == PlayQuotaConsumptionStatus.ALREADY_CONSUMED })
        assertFails { quota.releaseMatch(setOf(users.first()), match, emptySet()) }
        quota.releaseMatch(users, match, emptySet()); quota.releaseMatch(users, match, emptySet())
        assertEquals(0, quota.getSnapshot(users.first(), originalDay).matchesConsumed)
        assertEquals(0, quota.getSnapshot(users.first(), nextDay).matchesConsumed)
        val legacy = UUID.randomUUID().toString(); quota.consumeMatch(users, legacy, originalDay)
        val abandoned = UUID.randomUUID().toString(); quota.prepareMatch(users, abandoned, originalDay)
        quota.recoverPreparedMatches(emptySet())
        assertEquals(1, quota.getSnapshot(users.first(), originalDay).matchesConsumed)
        val completed = UUID.randomUUID().toString(); quota.prepareMatch(users, completed, originalDay)
        PostgresMatchResultRepository(ds).save(CompletedMatch(completed, "durable", ProtocolGameMode.ORDER, 0, 1, null,
            users.map { CompletedMatchPlayer(it, "player", 0, MatchOutcome.DRAW) }))
        quota.recoverPreparedMatches(emptySet()); quota.releaseMatch(users, completed, emptySet())
        assertEquals(2, quota.getSnapshot(users.first(), originalDay).matchesConsumed)
        val durableStart = UUID.randomUUID().toString()
        quota.prepareMatch(users, durableStart, originalDay)
        val snapshots = PostgresActiveRoomRepository(ds)
        val snapshot = StoredActiveRoom(durableStart, roomName = "frozen", host = StoredActivePlayer(users.first(), "host", true),
            guest = StoredActivePlayer(users.last(), "guest", true), gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.PLAYING,
            startedAtEpochMillis = 100, eventRewards = MatchRewardEventSnapshot(100, 2, 2))
        snapshots.save(snapshot)
        quota.recoverPreparedMatches(emptySet())
        assertEquals(3, quota.getSnapshot(users.first(), originalDay).matchesConsumed)
        assertEquals(2, snapshots.loadAll().single().eventRewards.goldMultiplier)
        val pendingId = UUID.randomUUID().toString(); quota.prepareMatch(users, pendingId, originalDay)
        val pending = CompletedMatch(pendingId, "pending", ProtocolGameMode.ORDER, 100, 200, null,
            users.map { CompletedMatchPlayer(it, "player", 0, MatchOutcome.DRAW) }, eventRewards = MatchRewardEventSnapshot(100, 2, 2))
        snapshots.save(snapshot.copy(pendingCompletedMatch = pending))
        quota.recoverPreparedMatches(emptySet())
        assertEquals(4, quota.getSnapshot(users.first(), originalDay).matchesConsumed)
        val outsider = identity.resolveGuest("outsider", null, 0).playerId
        assertFails { quota.prepareMatch(setOf(outsider), legacy, originalDay) }
    } }

    @Test fun `MATCH lifetime accounting and stats include bonus exactly once without changing base achievement awards`() = runTest { database { ds ->
        val identities = PostgresGuestIdentityRepository(ds)
        val players = (1..2).map { identities.resolveGuest("earned$it", null, 0) }
        val match = CompletedMatch(UUID.randomUUID().toString(), "earned", ProtocolGameMode.ORDER, 0, 1000, null,
            players.map { CompletedMatchPlayer(it.playerId, it.displayName, 0, MatchOutcome.DRAW) },
            matchType = MatchType.RANKED, eventRewards = MatchRewardEventSnapshot(0, 2, 2))
        val repository = PostgresMatchResultRepository(ds); repository.save(match); repository.save(match)
        ds.connection.use { c -> c.prepareStatement("SELECT gold, experience_points, lifetime_earned_gold, lifetime_earned_gems, total_matches FROM player_stats WHERE user_id=?").use { s ->
            s.setObject(1, UUID.fromString(players.first().playerId)); s.executeQuery().use { r ->
                assertTrue(r.next()); assertEquals(100, r.getInt(1)); assertEquals(40, r.getInt(2)); assertEquals(100, r.getInt(3));
                assertEquals(0, r.getInt(4)); assertEquals(1, r.getInt(5))
            }
        } }
    } }
}

internal fun doubledSource(now: Long): EventRewardSource = object : EventRewardSource {
    override suspend fun <T> withStartCatalog(block: suspend (RewardEventCatalog) -> T): T = block(RewardEventCatalog(listOf(
        RewardEventRecord(UUID.randomUUID().toString(), 2,
            RewardEventDraft("Nhân đôi", "Double", "Thưởng", "Reward", "UTC", now - 1, now + 100_000, 2, 2, MatchType.entries.toSet()),
            "internal", "test", now - 1, now - 1)
    )))
}
