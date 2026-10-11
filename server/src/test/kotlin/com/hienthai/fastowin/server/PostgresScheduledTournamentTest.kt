package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.sql.SQLException
import java.sql.Connection
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import kotlin.test.*

class PostgresScheduledTournamentTest {
    @Test
    fun `scheduled check in and departed roster restore then overdue cancellation refunds and records history`() = runTest {
        database { source ->
            var now = 1_000_000L
            val startsAt = 3_000_000L
            val ids = createPlayers(source, 4)
            val profiles = PostgresPlayerProfileRepository(source)
            val repository = PostgresTournamentRepository(source)
            val rooms = PostgresActiveRoomRepository(source)
            fun engine() = GameEngine(playerProfileRepository = profiles, tournamentRepository = PostgresTournamentRepository(source),
                activeRoomRepository = rooms, nowMillis = { now })
            val original = engine()
            ids.forEach { original.connectAccount(AuthenticatedAccount(UUID.fromString(it), "Player")) }
            val created = original.handle(ids.first(), ClientMessage.CreateTournament("Restored cup", ProtocolGameMode.ORDER, 100,
                schedule = TournamentScheduleRequest(startsAt, "Asia/Bangkok"))).update()
            ids.drop(1).forEach { original.handle(it, ClientMessage.JoinPublicTournament(created.tournamentId)) }
            original.handle(ids.last(), ClientMessage.LeaveTournament(created.tournamentId))
            now = startsAt - 900_000
            original.handle(ids.first(), ClientMessage.CheckInTournament(created.tournamentId))
            val restarted = engine()
            restarted.restoreActiveRooms()
            val persisted = repository.loadActive().single()
            assertEquals(created.schedule, persisted.schedule)
            assertTrue(persisted.players.first().isCheckedIn)
            assertEquals(ids.toSet(), persisted.recordedPlayers.map { it.playerId }.toSet())
            now = startsAt
            val cancelled = restarted.advanceScheduledTournaments().update()
            assertEquals(TournamentPhase.CANCELLED, cancelled.phase)
            assertTrue(restarted.advanceScheduledTournaments().isEmpty())
            ids.forEach { id ->
                assertEquals(1_000, profiles.findByPlayerId(id)!!.progression.gold)
                assertEquals(created.tournamentId, repository.loadRecent(id, 10).single().tournamentId)
                assertEquals(1, profiles.loadWalletHistory(id).count { it.sourceId == created.tournamentId && it.goldDelta > 0 })
            }
            assertTrue(rooms.loadAll().isEmpty())
        }
    }

    @Test
    fun `room and bracket transaction failure preserves paid lobby and retry starts once`() = runTest {
        database { source ->
            var now = 1_000_000L
            val ids = createPlayers(source, 4)
            val profiles = PostgresPlayerProfileRepository(source)
            val repository = PostgresTournamentRepository(source)
            val rooms = PostgresActiveRoomRepository(source)
            val engine = GameEngine(playerProfileRepository = profiles, tournamentRepository = repository,
                activeRoomRepository = rooms, nowMillis = { now })
            ids.forEach { engine.connectAccount(AuthenticatedAccount(UUID.fromString(it), "Player")) }
            val id = engine.handle(ids.first(), ClientMessage.CreateTournament("Atomic start", ProtocolGameMode.ORDER, 100,
                schedule = TournamentScheduleRequest(3_000_000, "UTC"))).update().tournamentId
            ids.drop(1).forEach { engine.handle(it, ClientMessage.JoinPublicTournament(id)) }
            now = 2_100_000
            ids.forEach { engine.handle(it, ClientMessage.CheckInTournament(id)) }
            source.connection.use { connection -> connection.createStatement().use {
                it.execute("""CREATE FUNCTION reject_scheduled_start() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN IF NEW.status = 'RUNNING' THEN RAISE EXCEPTION 'injected bracket failure'; END IF; RETURN NEW; END ${'$'}${'$'}""")
                it.execute("CREATE TRIGGER fail_scheduled_start BEFORE UPDATE ON tournaments FOR EACH ROW EXECUTE FUNCTION reject_scheduled_start()")
            } }
            now = 3_000_000
            assertFailsWith<SQLException> { engine.advanceScheduledTournaments() }
            assertEquals(TournamentPhase.LOBBY, repository.loadActive().single().phase)
            assertTrue(rooms.loadAll().isEmpty())
            assertEquals(TournamentPhase.LOBBY, engine.handle(ids.first(), ClientMessage.GetTournamentHub)
                .map(Delivery::message).filterIsInstance<ServerMessage.TournamentHubData>().single().hub.activeTournament!!.phase)
            ids.forEach { assertEquals(900, profiles.findByPlayerId(it)!!.progression.gold) }
            source.connection.use { connection -> connection.createStatement().use { it.execute("DROP TRIGGER fail_scheduled_start ON tournaments") } }
            assertEquals(TournamentPhase.RUNNING, engine.advanceScheduledTournaments().update().phase)
            assertEquals(2, rooms.loadAll().size)
            assertTrue(engine.advanceScheduledTournaments().isEmpty())
            val restarted = GameEngine(playerProfileRepository = profiles, tournamentRepository = PostgresTournamentRepository(source),
                activeRoomRepository = rooms, nowMillis = { now })
            restarted.restoreActiveRooms()
            assertTrue(restarted.advanceScheduledTournaments().isEmpty())
            assertEquals(2, rooms.loadAll().size)
        }
    }

    @Test
    fun `scheduled and maintenance cancellations share recorded actual payments and rollback refunds atomically`() = runTest {
        database { source ->
            val ids = createPlayers(source, 2)
            val profiles = PostgresPlayerProfileRepository(source)
            val repository = PostgresTournamentRepository(source)
            val id = UUID.randomUUID().toString()
            val lobby = TournamentSnapshot(id, "Refund cup", ids.first(), ProtocolGameMode.ORDER, TournamentPhase.LOBBY,
                entryFee = 100, prizePool = 150, players = ids.map { TournamentPlayerSnapshot(it, "Player") },
                createdAtEpochMillis = 1_000_000, schedule = TournamentScheduleSnapshot(3_000_000, "UTC", 2_100_000, 2_100_000))
            repository.save(lobby)
            profiles.applyWalletTransaction(ids[0], "TOURNAMENT_ENTRY", id, goldDelta = -100)
            profiles.applyWalletTransaction(ids[1], "TOURNAMENT_ENTRY", id, goldDelta = -50)
            val cancelled = lobby.copy(phase = TournamentPhase.CANCELLED, finishedAtEpochMillis = 3_000_000)
            assertFailsWith<IllegalArgumentException> { repository.cancelWithRefund(cancelled.copy(
                players = cancelled.players + TournamentPlayerSnapshot("invalid-uuid", "Broken")), profiles) }
            assertEquals(900, profiles.findByPlayerId(ids[0])!!.progression.gold)
            assertEquals(950, profiles.findByPlayerId(ids[1])!!.progression.gold)
            assertEquals(TournamentPhase.LOBBY, repository.loadActive().single().phase)
            assertTrue(profiles.loadWalletHistory(ids[0]).none { it.goldDelta > 0 })
            repository.cancelWithRefund(cancelled, profiles)
            PostgresTournamentRepository(source).cancelForMaintenance(cancelled, profiles)
            ids.forEach {
                assertEquals(1_000, profiles.findByPlayerId(it)!!.progression.gold)
                assertEquals(1, profiles.loadWalletHistory(it).count { transaction -> transaction.goldDelta > 0 })
            }
            assertEquals(50, profiles.loadWalletHistory(ids[1]).single { it.goldDelta > 0 }.goldDelta)
        }
    }

    @Test
    fun `lost start commit acknowledgment blocks writes until restart restores committed rooms`() = runTest {
        database { source ->
            var now = 1_000_000L
            val ids = createPlayers(source, 4)
            val profiles = PostgresPlayerProfileRepository(source)
            val rooms = PostgresActiveRoomRepository(source)
            val repository = PostgresTournamentRepository(source)
            val original = GameEngine(playerProfileRepository = profiles, tournamentRepository = repository,
                activeRoomRepository = rooms, nowMillis = { now })
            ids.forEach { original.connectAccount(AuthenticatedAccount(UUID.fromString(it), "Player")) }
            val id = original.handle(ids.first(), ClientMessage.CreateTournament("Lost acknowledgment", ProtocolGameMode.ORDER, 100,
                schedule = TournamentScheduleRequest(3_000_000, "UTC"))).update().tournamentId
            ids.drop(1).forEach { original.handle(it, ClientMessage.JoinPublicTournament(id)) }
            now = 2_100_000
            ids.forEach { original.handle(it, ClientMessage.CheckInTournament(id)) }
            val lostAck = object : HikariDataSource() {
                override fun getConnection(): Connection {
                    val delegate = source.connection
                    return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                        val result = try { method.invoke(delegate, *(args ?: emptyArray())) }
                            catch (failure: InvocationTargetException) { throw failure.targetException }
                        if (method.name == "commit") throw SQLException("Injected lost commit acknowledgment")
                        result
                    } as Connection
                }
            }
            val uncertainRepository = PostgresTournamentRepository(lostAck)
            val engine = GameEngine(playerProfileRepository = profiles, tournamentRepository = uncertainRepository,
                activeRoomRepository = rooms, nowMillis = { now })
            ids.forEach { engine.connectAccount(AuthenticatedAccount(UUID.fromString(it), "Player")) }
            now = 3_000_000
            assertFailsWith<TournamentPersistenceUncertainException> { engine.advanceScheduledTournaments() }
            assertFailsWith<TournamentPersistenceUncertainException> { engine.advanceScheduledTournaments() }
            assertEquals(TournamentPhase.LOBBY, engine.handle(ids.first(), ClientMessage.GetTournamentHub)
                .map(Delivery::message).filterIsInstance<ServerMessage.TournamentHubData>().single().hub.activeTournament!!.phase)
            assertEquals(TournamentPhase.RUNNING, repository.loadActive().single().phase)
            val committedIds = rooms.loadAll().map { it.roomId }.toSet()
            assertEquals(2, committedIds.size)
            val restarted = GameEngine(playerProfileRepository = profiles, tournamentRepository = PostgresTournamentRepository(source),
                activeRoomRepository = rooms, nowMillis = { now })
            restarted.restoreActiveRooms()
            assertTrue(restarted.advanceScheduledTournaments().isEmpty())
            assertEquals(committedIds, rooms.loadAll().map { it.roomId }.toSet())
            ids.forEach { assertEquals(900, profiles.findByPlayerId(it)!!.progression.gold) }
        }
    }

    private suspend fun createPlayers(source: HikariDataSource, count: Int): List<String> = List(count) { index ->
        val id = PostgresGuestIdentityRepository(source).resolveGuest("Scheduled player $index", null, 1_000_000).playerId
        source.connection.use { connection -> connection.prepareStatement("INSERT INTO player_stats(user_id,gold,updated_at) VALUES (?,1000,CURRENT_TIMESTAMP)").use {
            it.setObject(1, UUID.fromString(id)); it.executeUpdate()
        } }
        id
    }

    private suspend fun database(test: suspend (HikariDataSource) -> Unit) {
        val url = checkNotNull(System.getenv("TEST_DATABASE_URL")) { "TEST_DATABASE_URL required; scheduled durability tests cannot be skipped" }
        val schemaName = "scheduled_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER")
            password = System.getenv("TEST_DATABASE_PASSWORD")
            maximumPoolSize = 2
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schemaName") } }
            try {
                HikariDataSource(config().apply { schema = schemaName }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schemaName).defaultSchema(schemaName)
                        .locations("classpath:db/migration").load().migrate()
                    test(source)
                }
            } finally {
                setup.connection.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA $schemaName CASCADE") } }
            }
        }
    }

    private fun List<Delivery>.update() = map(Delivery::message).filterIsInstance<ServerMessage.TournamentUpdated>().single().tournament
}
