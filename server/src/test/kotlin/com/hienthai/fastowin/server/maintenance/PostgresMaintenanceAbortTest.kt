package com.hienthai.fastowin.server.maintenance

import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.server.*
import com.zaxxer.hikari.*
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.*

class PostgresMaintenanceAbortTest {
    @Test fun progressionPrizeRoomsBracketAndAcknowledgmentAreOneTransaction() = runTest {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL required")
        val schema = "maintenance_progress_${UUID.randomUUID().toString().replace("-", "") }"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 2
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try { HikariDataSource(config().apply { this.schema = schema }).use { source ->
                Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                val player = PostgresGuestIdentityRepository(source).resolveGuest("winner", null, 1000).playerId
                source.connection.use { c -> c.prepareStatement("INSERT INTO player_stats(user_id,gold,updated_at) VALUES (?,0,CURRENT_TIMESTAMP)").use {
                    it.setObject(1, UUID.fromString(player)); it.executeUpdate()
                } }
                val profiles = PostgresPlayerProfileRepository(source); val rooms = PostgresActiveRoomRepository(source)
                val repository = PostgresTournamentRepository(source)
                val previous = StoredActiveRoom(UUID.randomUUID().toString(), roomName = "accepted", host = StoredActivePlayer(player, "winner", true),
                    gameMode = ProtocolGameMode.ORDER, phase = RoomPhase.FINISHED)
                rooms.save(previous)
                val tournament = TournamentSnapshot(UUID.randomUUID().toString(), "cup", player, ProtocolGameMode.ORDER, TournamentPhase.RUNNING, 4,
                    players = listOf(TournamentPlayerSnapshot(player, "winner")), createdAtEpochMillis = 1000, prizePool = 100)
                repository.save(tournament)
                val completed = tournament.copy(phase = TournamentPhase.FINISHED, championPlayerId = player, finishedAtEpochMillis = 2000)
                val next = previous.copy(roomId = UUID.randomUUID().toString(), phase = RoomPhase.PLAYING)
                val batch = TournamentProgression(completed, listOf(next), previous.roomId)
                // Failure on second room and then bracket binding occurs after prize/first-room writes.
                assertFailsWith<IllegalArgumentException> { repository.commitProgression(batch.copy(newRooms = listOf(next, next.copy(roomId = "bad-uuid"))), rooms, profiles) }
                assertFailsWith<IllegalArgumentException> { repository.commitProgression(batch.copy(tournament = completed.copy(players = completed.players + TournamentPlayerSnapshot("bad-uuid", "broken"))), rooms, profiles) }
                assertEquals(listOf(previous.roomId), rooms.loadAll().map { it.roomId })
                assertEquals(TournamentPhase.RUNNING, repository.loadActive().single().phase)
                assertEquals(0, profiles.findByPlayerId(player)!!.progression.gold)
                repository.commitProgression(batch, rooms, profiles)
                PostgresTournamentRepository(source).commitProgression(batch, rooms, profiles)
                assertEquals(listOf(next.roomId), rooms.loadAll().map { it.roomId })
                assertEquals(player, repository.loadRecent(player, 10).single().championPlayerId)
                assertEquals(100, profiles.findByPlayerId(player)!!.progression.gold)
                assertEquals(1, profiles.loadWalletHistory(player).count { it.sourceType == "TOURNAMENT_PRIZE" })
                assertFailsWith<IllegalStateException> { repository.commitProgression(batch.copy(tournament = completed.copy(prizePool = 200)), rooms, profiles) }
            } } finally { setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } } }
        }
    }

    @Test fun cancellationAndRefundRollbackTogetherThenSurviveRestartAndRetry() = runTest {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL required")
        val schema = "maintenance_abort_${UUID.randomUUID().toString().replace("-", "")}"
        fun config() = HikariConfig().apply {
            jdbcUrl = url; username = System.getenv("TEST_DATABASE_USER"); password = System.getenv("TEST_DATABASE_PASSWORD"); maximumPoolSize = 2
        }
        HikariDataSource(config()).use { setup ->
            setup.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
            try {
                HikariDataSource(config().apply { this.schema = schema }).use { source ->
                    Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate()
                    val user = PostgresGuestIdentityRepository(source).resolveGuest("entry", null, 1000)
                    source.connection.use { c -> c.prepareStatement("INSERT INTO player_stats(user_id, gold, updated_at) VALUES (?, 100, CURRENT_TIMESTAMP)").use {
                        it.setObject(1, UUID.fromString(user.playerId)); it.executeUpdate()
                    } }
                    val profiles = PostgresPlayerProfileRepository(source)
                    val repository = PostgresTournamentRepository(source)
                    val lobby = TournamentSnapshot(UUID.randomUUID().toString(), "cup", user.playerId, ProtocolGameMode.ORDER,
                        TournamentPhase.LOBBY, 4, players = listOf(TournamentPlayerSnapshot(user.playerId, "entry")),
                        createdAtEpochMillis = 1000, entryFee = 100)
                    val joined = repository.admitPlayer(lobby, user.playerId, profiles).tournament!!
                    val cancelled = joined.copy(phase = TournamentPhase.CANCELLED, prizePool = 0, finishedAtEpochMillis = 2000)
                    // Invalid snapshot binding fails AFTER ledger/balance writes, which must roll back.
                    assertFailsWith<IllegalArgumentException> { repository.cancelForMaintenance(cancelled.copy(
                        players = cancelled.players + TournamentPlayerSnapshot("invalid-uuid", "broken")), profiles) }
                    assertEquals(0, profiles.findByPlayerId(user.playerId)!!.progression.gold)
                    assertEquals(TournamentPhase.LOBBY, repository.loadActive().single().phase)
                    assertTrue(profiles.loadWalletHistory(user.playerId).none { it.sourceType == "TOURNAMENT_MAINTENANCE_REFUND" })
                    // A paid entrant who has since left the lobby still gets the original entry back.
                    repository.save(joined.copy(players = emptyList()))
                    repository.cancelForMaintenance(cancelled.copy(players = emptyList()), profiles)
                    PostgresTournamentRepository(source).cancelForMaintenance(cancelled.copy(players = emptyList()), profiles)
                    assertEquals(100, profiles.findByPlayerId(user.playerId)!!.progression.gold)
                    assertEquals(1, profiles.loadWalletHistory(user.playerId).count { it.sourceType == "TOURNAMENT_MAINTENANCE_REFUND" })
                    assertTrue(PostgresTournamentRepository(source).loadActive().isEmpty())
                }
            } finally {
                setup.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }
}
