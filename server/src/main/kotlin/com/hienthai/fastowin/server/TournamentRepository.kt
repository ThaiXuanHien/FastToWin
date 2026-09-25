package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.util.UUID

interface TournamentRepository {
    suspend fun loadActive(): List<TournamentSnapshot>
    suspend fun loadRecent(playerId: String, limit: Int): List<TournamentSnapshot>
    suspend fun save(tournament: TournamentSnapshot)

    /**
     * Candidate already includes the player, but its pool excludes any NEW payment.
     * Persist the entry debit and adjusted snapshot together. A prior payment admits a
     * returning player without another pool contribution. No unsafe default for durable stores.
     * [profiles] is used only by the non-durable in-memory implementation.
     */
    suspend fun admitPlayer(
        candidate: TournamentSnapshot,
        playerId: String,
        profiles: PlayerProfileRepository
    ): TournamentAdmissionResult
}

data class TournamentAdmissionResult(
    val walletStatus: WalletMutationStatus,
    val tournament: TournamentSnapshot? = null
)

class TournamentAdmissionSaveException(cause: Throwable) : RuntimeException("Could not save admission", cause)

class TournamentPersistenceUncertainException(cause: Throwable?) : IllegalStateException(
    "Tournament commit outcome is unknown; restart and restore durable snapshots before further writes", cause
)

open class InMemoryTournamentRepository : TournamentRepository {
    private val tournaments = linkedMapOf<String, TournamentSnapshot>()

    override suspend fun loadActive(): List<TournamentSnapshot> = tournaments.values.filter {
        it.phase == TournamentPhase.LOBBY || it.phase == TournamentPhase.RUNNING
    }

    override suspend fun loadRecent(playerId: String, limit: Int): List<TournamentSnapshot> = tournaments.values
        .asSequence()
        .filter { tournament -> tournament.players.any { it.playerId == playerId } }
        .filter { it.phase == TournamentPhase.FINISHED || it.phase == TournamentPhase.CANCELLED }
        .sortedByDescending { it.finishedAtEpochMillis ?: it.createdAtEpochMillis }
        .take(limit)
        .toList()

    override suspend fun save(tournament: TournamentSnapshot) {
        tournaments[tournament.tournamentId] = tournament
    }

    /** In-memory/test storage only: completion and rollback cannot be cancelled by the request. */
    override suspend fun admitPlayer(
        candidate: TournamentSnapshot,
        playerId: String,
        profiles: PlayerProfileRepository
    ): TournamentAdmissionResult = withContext(NonCancellable) {
        val status = if (candidate.entryFee > 0) profiles.applyWalletTransaction(
            playerId, "TOURNAMENT_ENTRY", candidate.tournamentId, goldDelta = -candidate.entryFee
        ) else WalletMutationStatus.APPLIED
        val snapshot = candidate.afterPayment(status) ?: return@withContext TournamentAdmissionResult(status)
        try {
            save(snapshot)
        } catch (error: Exception) {
            if (candidate.entryFee > 0 && status == WalletMutationStatus.APPLIED) {
                val reversed = profiles.reverseTournamentEntry(playerId, candidate.tournamentId, candidate.entryFee)
                check(reversed == WalletReversalStatus.REVERSED || reversed == WalletReversalStatus.ALREADY_REVERSED) {
                    "In-memory admission save and entry reversal both failed"
                }
            }
            throw TournamentAdmissionSaveException(error)
        }
        TournamentAdmissionResult(status, snapshot)
    }
}

private fun TournamentSnapshot.afterPayment(status: WalletMutationStatus): TournamentSnapshot? = when (status) {
    WalletMutationStatus.APPLIED -> copy(prizePool = prizePool + entryFee)
    WalletMutationStatus.DUPLICATE -> this
    WalletMutationStatus.INSUFFICIENT_FUNDS, WalletMutationStatus.PLAYER_NOT_FOUND -> null
}

class PostgresTournamentRepository(
    private val dataSource: HikariDataSource
) : TournamentRepository {
    @Volatile private var uncertainCommit: Throwable? = null

    private fun requireKnownCommitOutcome() {
        uncertainCommit?.let { throw TournamentPersistenceUncertainException(it) }
    }

    override suspend fun loadActive(): List<TournamentSnapshot> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT snapshot_json::text
            FROM tournaments
            WHERE status IN ('LOBBY', 'RUNNING')
            ORDER BY created_at, tournament_id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(ProtocolJson.decodeFromString<TournamentSnapshot>(result.getString(1)))
                }
            }
        }
    }

    override suspend fun loadRecent(playerId: String, limit: Int): List<TournamentSnapshot> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT snapshot_json::text
                FROM tournaments
                WHERE ? = ANY(player_ids)
                  AND status IN ('FINISHED', 'CANCELLED')
                ORDER BY COALESCE(finished_at, updated_at) DESC
                LIMIT ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.fromString(playerId))
                statement.setInt(2, limit.coerceIn(1, 20))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(ProtocolJson.decodeFromString<TournamentSnapshot>(result.getString(1)))
                    }
                }
            }
        }

    override suspend fun save(tournament: TournamentSnapshot) {
        requireKnownCommitOutcome()
        dataSource.connection.use { connection ->
            save(connection, tournament)
        }
    }

    override suspend fun admitPlayer(
        candidate: TournamentSnapshot,
        playerId: String,
        profiles: PlayerProfileRepository
    ): TournamentAdmissionResult = withContext(NonCancellable + Dispatchers.IO) {
        requireKnownCommitOutcome()
        var committing = false
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val status = if (candidate.entryFee > 0) debitEntry(connection, playerId, candidate)
                        else WalletMutationStatus.APPLIED
                    val snapshot = candidate.afterPayment(status)
                    if (snapshot == null) {
                        connection.rollback()
                        return@withContext TournamentAdmissionResult(status)
                    }
                    save(connection, snapshot)
                    committing = true
                    connection.commit()
                    TournamentAdmissionResult(status, snapshot)
                } catch (error: Exception) {
                    runCatching { connection.rollback() }.exceptionOrNull()?.let(error::addSuppressed)
                    throw error
                }
            }
        } catch (error: Exception) {
            if (committing) {
                // A failed acknowledgement (or connection close after commit) does not prove
                // rollback. Never compensate or allow stale memory to overwrite durable state.
                uncertainCommit = error
                throw TournamentPersistenceUncertainException(error)
            }
            throw TournamentAdmissionSaveException(error)
        }
    }

    private fun debitEntry(connection: Connection, playerId: String, candidate: TournamentSnapshot): WalletMutationStatus {
        val userId = UUID.fromString(playerId)
        // Same lock order as all other wallet operations: balance row, then ledger.
        val gold = connection.prepareStatement("SELECT gold FROM player_stats WHERE user_id = ? FOR UPDATE").use {
            it.setObject(1, userId)
            it.executeQuery().use { result -> if (result.next()) result.getInt(1) else null }
        } ?: return WalletMutationStatus.PLAYER_NOT_FOUND
        val existing = connection.prepareStatement(
            "SELECT gold_delta FROM wallet_transactions WHERE user_id = ? AND source_type = 'TOURNAMENT_ENTRY' AND source_id = ?"
        ).use {
            it.setObject(1, userId)
            it.setString(2, candidate.tournamentId)
            it.executeQuery().use { result -> if (result.next()) result.getInt(1) else null }
        }
        // Check prior payment BEFORE funds: a returning player may now have zero Gold.
        if (existing != null) {
            check(existing == -candidate.entryFee) { "Tournament entry fee does not match its payment" }
            return WalletMutationStatus.DUPLICATE
        }
        if (gold < candidate.entryFee) return WalletMutationStatus.INSUFFICIENT_FUNDS
        connection.prepareStatement(
            "INSERT INTO wallet_transactions(id, user_id, source_type, source_id, gold_delta) VALUES (?, ?, 'TOURNAMENT_ENTRY', ?, ?)"
        ).use {
            it.setObject(1, UUID.randomUUID())
            it.setObject(2, userId)
            it.setString(3, candidate.tournamentId)
            it.setInt(4, -candidate.entryFee)
            check(it.executeUpdate() == 1)
        }
        connection.prepareStatement(
            "UPDATE player_stats SET gold = gold - ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?"
        ).use {
            it.setInt(1, candidate.entryFee)
            it.setObject(2, userId)
            check(it.executeUpdate() == 1)
        }
        return WalletMutationStatus.APPLIED
    }

    private fun save(connection: Connection, tournament: TournamentSnapshot) {
        connection.prepareStatement(
            """
            INSERT INTO tournaments(
                tournament_id, status, player_ids, snapshot_json,
                created_at, started_at, finished_at, updated_at
            )
            VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (tournament_id) DO UPDATE SET
                status = EXCLUDED.status,
                player_ids = EXCLUDED.player_ids,
                snapshot_json = EXCLUDED.snapshot_json,
                started_at = EXCLUDED.started_at,
                finished_at = EXCLUDED.finished_at,
                updated_at = EXCLUDED.updated_at
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.fromString(tournament.tournamentId))
            statement.setString(2, tournament.phase.name)
            statement.setArray(
                3,
                connection.createArrayOf(
                    "uuid",
                    tournament.players.map { UUID.fromString(it.playerId) }.toTypedArray()
                )
            )
            statement.setString(4, ProtocolJson.encodeToString(tournament))
            statement.setTimestamp(5, java.sql.Timestamp(tournament.createdAtEpochMillis))
            statement.setTimestamp(6, tournament.startedAtEpochMillis?.let { java.sql.Timestamp(it) })
            statement.setTimestamp(7, tournament.finishedAtEpochMillis?.let { java.sql.Timestamp(it) })
            statement.executeUpdate()
        }
    }
}
