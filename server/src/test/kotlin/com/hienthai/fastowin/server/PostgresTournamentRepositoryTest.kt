package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentPlayerSnapshot
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresTournamentRepositoryTest {
    @Test
    fun `tournament moves from active list to player history`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL") ?: return@runTest
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val repository = PostgresTournamentRepository(dataSource)
            val tournamentId = UUID.randomUUID().toString()
            val hostId = UUID.randomUUID().toString()
            val guestId = UUID.randomUUID().toString()
            val createdAt = System.currentTimeMillis()
            val lobby = TournamentSnapshot(
                tournamentId = tournamentId,
                name = "Cúp PostgreSQL",
                hostPlayerId = hostId,
                gameMode = ProtocolGameMode.ORDER,
                phase = TournamentPhase.LOBBY,
                maxPlayers = 8,
                players = listOf(
                    TournamentPlayerSnapshot(hostId, "Hiền", isHost = true, isOnline = true),
                    TournamentPlayerSnapshot(guestId, "Hiếu", isOnline = true)
                ),
                createdAtEpochMillis = createdAt
            )
            try {
                repository.save(lobby)
                assertTrue(repository.loadActive().any { it.tournamentId == tournamentId })

                repository.save(
                    lobby.copy(
                        phase = TournamentPhase.FINISHED,
                        championPlayerId = hostId,
                        startedAtEpochMillis = createdAt + 1_000,
                        finishedAtEpochMillis = createdAt + 2_000
                    )
                )

                assertFalse(repository.loadActive().any { it.tournamentId == tournamentId })
                val saved = repository.loadRecent(hostId, 10).single { it.tournamentId == tournamentId }
                assertEquals(hostId, saved.championPlayerId)
                assertEquals(TournamentPhase.FINISHED, saved.phase)
                assertEquals(8, saved.maxPlayers)
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM tournaments WHERE tournament_id = ?").use { statement ->
                        statement.setObject(1, UUID.fromString(tournamentId))
                        statement.executeUpdate()
                    }
                }
            }
        }
    }
}
