package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentPlayerSnapshot
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationTargetException
import java.sql.Connection
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PostgresTournamentAdmissionTest {
    @Test
    fun `admission rolls back debit on snapshot failure and rejoin at zero gold is free`() = runTest {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL is required for PostgreSQL integration")
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = System.getenv("TEST_DATABASE_USER") ?: "fasttowin"
            password = System.getenv("TEST_DATABASE_PASSWORD") ?: "fasttowin"
            maximumPoolSize = 2
        }).use { dataSource ->
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            val player = PostgresGuestIdentityRepository(dataSource).resolveGuest("Atomic entry", null, 1_000L)
            val playerId = UUID.fromString(player.playerId)
            val tournamentId = UUID.randomUUID().toString()
            val hostId = UUID.randomUUID().toString()
            val wallet = PostgresPlayerProfileRepository(dataSource)
            val repository = PostgresTournamentRepository(dataSource)
            val lobby = TournamentSnapshot(
                tournamentId = tournamentId, name = "Atomic Cup", hostPlayerId = hostId,
                gameMode = ProtocolGameMode.ORDER, phase = TournamentPhase.LOBBY, maxPlayers = 4,
                players = listOf(TournamentPlayerSnapshot(hostId, "Host", isHost = true)),
                createdAtEpochMillis = 1_000L, entryFee = 100, prizePool = 100
            )
            val candidate = lobby.copy(players = lobby.players + TournamentPlayerSnapshot(player.playerId, "Guest"))
            try {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("UPDATE player_stats SET gold = 100 WHERE user_id = ?").use {
                        it.setObject(1, playerId)
                        assertEquals(1, it.executeUpdate())
                    }
                }
                repository.save(lobby)
                // Fails while binding the snapshot's UUID array, after the wallet INSERT/UPDATE.
                assertFailsWith<TournamentAdmissionSaveException> {
                    repository.admitPlayer(candidate.copy(players = candidate.players +
                        TournamentPlayerSnapshot("invalid-uuid", "Broken")), player.playerId, wallet)
                }
                assertEquals(100, wallet.findByPlayerId(player.playerId)!!.progression.gold)
                assertTrue(wallet.loadWalletHistory(player.playerId).none { it.sourceId == tournamentId })
                assertEquals(listOf(hostId), repository.loadActive().single { it.tournamentId == tournamentId }
                    .players.map { it.playerId })

                val joined = repository.admitPlayer(candidate, player.playerId, wallet).tournament!!
                assertEquals(200, joined.prizePool)
                assertEquals(0, wallet.findByPlayerId(player.playerId)!!.progression.gold)
                assertEquals(joined, repository.loadActive().single { it.tournamentId == tournamentId })

                val left = joined.copy(players = lobby.players)
                repository.save(left)
                val rejoined = repository.admitPlayer(left.copy(players = candidate.players), player.playerId, wallet)
                assertEquals(WalletMutationStatus.DUPLICATE, rejoined.walletStatus)
                assertEquals(200, rejoined.tournament!!.prizePool)
                assertEquals(0, wallet.findByPlayerId(player.playerId)!!.progression.gold)
                assertEquals(1, wallet.loadWalletHistory(player.playerId).count {
                    it.sourceType == "TOURNAMENT_ENTRY" && it.sourceId == tournamentId && it.goldDelta == -100
                })

                repository.save(left)
                // The database commits both records, but the acknowledgement is lost.
                val lostAckSource = object : HikariDataSource() {
                    override fun getConnection(): Connection {
                        val delegate = dataSource.connection
                        return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) {
                            _, method, args ->
                            val result = try {
                                method.invoke(delegate, *(args ?: emptyArray()))
                            } catch (error: InvocationTargetException) {
                                throw error.targetException
                            }
                            if (method.name == "commit") throw SQLException("commit acknowledgement lost")
                            result
                        } as Connection
                    }
                }
                lostAckSource.use {
                    val uncertainRepository = PostgresTournamentRepository(it)
                    assertFailsWith<TournamentPersistenceUncertainException> {
                        uncertainRepository.admitPlayer(left.copy(players = candidate.players), player.playerId, wallet)
                    }
                    // A stale engine must never erase the possibly committed admission.
                    assertFailsWith<TournamentPersistenceUncertainException> { uncertainRepository.save(left) }
                }
                val recovered = PostgresTournamentRepository(dataSource).loadActive()
                    .single { it.tournamentId == tournamentId }
                assertEquals(candidate.players, recovered.players)
                assertEquals(200, recovered.prizePool)
                assertEquals(0, wallet.findByPlayerId(player.playerId)!!.progression.gold)
            } finally {
                dataSource.connection.use { connection ->
                    connection.prepareStatement("DELETE FROM tournaments WHERE tournament_id = ?").use {
                        it.setObject(1, UUID.fromString(tournamentId))
                        it.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM users WHERE id = ?").use {
                        it.setObject(1, playerId)
                        it.executeUpdate()
                    }
                }
            }
        }
    }
}
