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
