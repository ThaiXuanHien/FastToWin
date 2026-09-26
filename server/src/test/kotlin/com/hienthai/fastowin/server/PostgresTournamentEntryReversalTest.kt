package com.hienthai.fastowin.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresTournamentEntryReversalTest {
    @Test
    fun `reversal restores gold preserves audit and makes entry key retryable`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL is required for PostgreSQL integration")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource)
                .resolveGuest("Tournament reversal test", null, 1_000L)
            val playerId = UUID.fromString(player.playerId)
            val tournamentId = UUID.randomUUID().toString()
            val repository = PostgresPlayerProfileRepository(dataSource)
            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "UPDATE player_stats SET gold = 1000, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?"
                    ).use { statement ->
                        statement.setObject(1, playerId)
                        assertEquals(1, statement.executeUpdate())
                    }
                }
                assertEquals(WalletMutationStatus.APPLIED, repository.applyWalletTransaction(
                    player.playerId, "TOURNAMENT_ENTRY", tournamentId, goldDelta = -100
                ))
                assertEquals(WalletReversalStatus.REVERSED,
                    repository.reverseTournamentEntry(player.playerId, tournamentId, 100))
                assertEquals(WalletReversalStatus.ALREADY_REVERSED,
                    repository.reverseTournamentEntry(player.playerId, tournamentId, 100))
                assertEquals(1000, repository.findByPlayerId(player.playerId)!!.progression.gold)

                assertEquals(WalletMutationStatus.APPLIED, repository.applyWalletTransaction(
                    player.playerId, "TOURNAMENT_ENTRY", tournamentId, goldDelta = -100
                ))
                assertEquals(900, repository.findByPlayerId(player.playerId)!!.progression.gold)
                val history = repository.loadWalletHistory(player.playerId, 20)
                assertEquals(1, history.count {
                    it.sourceType == "TOURNAMENT_ENTRY" && it.sourceId == tournamentId && it.goldDelta == -100
                })
                assertEquals(1, history.count {
                    it.sourceType == "TOURNAMENT_ENTRY_REVERSED" && it.sourceId.startsWith("$tournamentId:") && it.goldDelta == -100
                })
                assertEquals(1, history.count {
                    it.sourceType == "TOURNAMENT_ENTRY_REFUND" && it.sourceId.startsWith("$tournamentId:") && it.goldDelta == 100
                })
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use { statement ->
                        statement.setObject(1, playerId)
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
