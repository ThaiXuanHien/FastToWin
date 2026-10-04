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

    /** Durably cancel with a full, idempotent refund of each recorded entry payment. */
    suspend fun cancelForMaintenance(cancelled: TournamentSnapshot, profiles: PlayerProfileRepository)

    /** Acknowledges bracket, generated rooms, prize and completed-room removal as one durable unit. */
    suspend fun commitProgression(batch: TournamentProgression, rooms: ActiveRoomRepository, profiles: PlayerProfileRepository)

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

data class TournamentProgression(val tournament: TournamentSnapshot, val newRooms: List<StoredActiveRoom>, val completedRoomId: String? = null,
    val removedRoomIds: List<String> = emptyList())

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
    private val entrants = mutableMapOf<String, MutableSet<String>>()

    override suspend fun commitProgression(batch: TournamentProgression, rooms: ActiveRoomRepository, profiles: PlayerProfileRepository) {
        // Process-local implementation: rollback snapshots on failure; wallet retries use their canonical key.
        val before = rooms.loadAll().associateBy { it.roomId }
        val previous = tournaments[batch.tournament.tournamentId]
        try {
            val tournament = batch.tournament
            if (tournament.phase == TournamentPhase.FINISHED && tournament.prizePool > 0) {
                val status = profiles.applyWalletTransaction(checkNotNull(tournament.championPlayerId), "TOURNAMENT_PRIZE", tournament.tournamentId, goldDelta = tournament.prizePool)
                check(status == WalletMutationStatus.APPLIED || status == WalletMutationStatus.DUPLICATE) { "Tournament prize not acknowledged" }
            }
            batch.newRooms.forEach { rooms.save(it) }
            save(tournament)
            batch.completedRoomId?.let { rooms.delete(it) }
            batch.removedRoomIds.forEach { rooms.delete(it) }
        } catch (failure: Exception) {
            for (room in batch.newRooms) before[room.roomId]?.let { rooms.save(it) } ?: rooms.delete(room.roomId)
            batch.completedRoomId?.let { id -> before[id]?.let { rooms.save(it) } }
            batch.removedRoomIds.forEach { id -> before[id]?.let { rooms.save(it) } }
            if (previous == null) tournaments.remove(batch.tournament.tournamentId) else tournaments[previous.tournamentId] = previous
            throw failure
        }
    }

    override suspend fun cancelForMaintenance(cancelled: TournamentSnapshot, profiles: PlayerProfileRepository) {
        require(cancelled.phase == TournamentPhase.CANCELLED && cancelled.championPlayerId == null)
        if (cancelled.entryFee > 0) {
            for (playerId in entrants[cancelled.tournamentId] ?: cancelled.players.map { it.playerId }.toSet()) {
                val result = profiles.applyWalletTransaction(playerId, "TOURNAMENT_MAINTENANCE_REFUND", cancelled.tournamentId,
                    goldDelta = cancelled.entryFee)
                check(result == WalletMutationStatus.APPLIED || result == WalletMutationStatus.DUPLICATE)
            }
        }
        save(cancelled)
    }

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
        entrants.getOrPut(candidate.tournamentId) { linkedSetOf() }.add(playerId)
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

    override suspend fun commitProgression(batch: TournamentProgression, rooms: ActiveRoomRepository, profiles: PlayerProfileRepository) = withContext(Dispatchers.IO) {
        requireKnownCommitOutcome()
        var committing = false
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val tournament = batch.tournament
                    connection.prepareStatement("SELECT tournament_id FROM tournaments WHERE tournament_id=? FOR UPDATE").use {
                        it.setObject(1, UUID.fromString(tournament.tournamentId)); it.executeQuery().use { rows -> check(rows.next()) { "Progression tournament missing" } }
                    }
                    if (tournament.phase == TournamentPhase.FINISHED && tournament.prizePool > 0) {
                        val winner = UUID.fromString(checkNotNull(tournament.championPlayerId))
                        check(tournament.players.any { it.playerId == winner.toString() }) { "Prize winner is not an entrant" }
                        connection.prepareStatement("SELECT user_id,gold_delta,gems_delta,xp_delta FROM wallet_transactions WHERE source_type='TOURNAMENT_PRIZE' AND source_id=?").use {
                            it.setString(1, tournament.tournamentId)
                            it.executeQuery().use { rows -> while (rows.next()) {
                                check(rows.getObject(1, UUID::class.java) == winner && rows.getInt(2) == tournament.prizePool && rows.getInt(3) == 0 && rows.getInt(4) == 0) {
                                    "Tournament prize source conflicts with its acknowledged grant"
                                }
                            } }
                        }
                        connection.prepareStatement("SELECT gold FROM player_stats WHERE user_id=? FOR UPDATE").use {
                            it.setObject(1, winner); it.executeQuery().use { rows -> check(rows.next()) { "Prize player missing" } }
                        }
                        val inserted = connection.prepareStatement("INSERT INTO wallet_transactions(id,user_id,source_type,source_id,gold_delta) VALUES (?,?,'TOURNAMENT_PRIZE',?,?) ON CONFLICT (user_id,source_type,source_id) DO NOTHING").use {
                            it.setObject(1, UUID.randomUUID()); it.setObject(2, winner); it.setString(3, tournament.tournamentId); it.setInt(4, tournament.prizePool); it.executeUpdate()
                        }
                        if (inserted == 1) connection.prepareStatement("UPDATE player_stats SET gold=gold+?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?").use {
                            it.setInt(1, tournament.prizePool); it.setObject(2, winner); check(it.executeUpdate() == 1)
                        }
                    }
                    batch.newRooms.forEach { room ->
                        connection.prepareStatement("INSERT INTO active_room_snapshots(room_id,state_json,updated_at) VALUES (?,CAST(? AS jsonb),CURRENT_TIMESTAMP) ON CONFLICT(room_id) DO UPDATE SET state_json=EXCLUDED.state_json,updated_at=EXCLUDED.updated_at").use {
                            it.setObject(1, UUID.fromString(room.roomId)); it.setString(2, ProtocolJson.encodeToString(room)); it.executeUpdate()
                        }
                    }
                    save(connection, tournament)
                    (batch.removedRoomIds + listOfNotNull(batch.completedRoomId)).forEach { id -> connection.prepareStatement("DELETE FROM active_room_snapshots WHERE room_id=?").use {
                        it.setObject(1, UUID.fromString(id)); it.executeUpdate()
                    } }
                    committing = true
                    connection.commit()
                } catch (failure: Exception) {
                    runCatching { connection.rollback() }.exceptionOrNull()?.let(failure::addSuppressed)
                    throw failure
                }
            }
        } catch (failure: Exception) {
            if (committing) { uncertainCommit = failure; throw TournamentPersistenceUncertainException(failure) }
            throw failure
        }
    }

    override suspend fun cancelForMaintenance(cancelled: TournamentSnapshot, profiles: PlayerProfileRepository) = withContext(Dispatchers.IO) {
        requireKnownCommitOutcome()
        require(cancelled.phase == TournamentPhase.CANCELLED && cancelled.championPlayerId == null)
        var committing = false
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    // Read the payment ledger, including entrants who left the lobby before closure.
                    val payments = connection.prepareStatement("SELECT user_id, -gold_delta FROM wallet_transactions WHERE source_type = 'TOURNAMENT_ENTRY' AND source_id = ? ORDER BY user_id").use {
                        it.setString(1, cancelled.tournamentId)
                        it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getObject(1, UUID::class.java) to rows.getInt(2)) } }
                    }
                    for ((userId, gold) in payments) {
                        connection.prepareStatement("SELECT gold FROM player_stats WHERE user_id = ? FOR UPDATE").use {
                            it.setObject(1, userId); it.executeQuery().use { rows -> check(rows.next()) }
                        }
                        val inserted = connection.prepareStatement("INSERT INTO wallet_transactions(id, user_id, source_type, source_id, gold_delta) VALUES (?, ?, 'TOURNAMENT_MAINTENANCE_REFUND', ?, ?) ON CONFLICT (user_id, source_type, source_id) DO NOTHING").use {
                            it.setObject(1, UUID.randomUUID()); it.setObject(2, userId); it.setString(3, cancelled.tournamentId); it.setInt(4, gold); it.executeUpdate()
                        }
                        if (inserted == 1) connection.prepareStatement("UPDATE player_stats SET gold = gold + ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?").use {
                            it.setInt(1, gold); it.setObject(2, userId); check(it.executeUpdate() == 1)
                        }
                    }
                    save(connection, cancelled)
                    committing = true
                    connection.commit()
                } catch (error: Exception) {
                    runCatching { connection.rollback() }.exceptionOrNull()?.let(error::addSuppressed)
                    throw error
                }
            }
        } catch (error: Exception) {
            if (committing) { uncertainCommit = error; throw TournamentPersistenceUncertainException(error) }
            throw error
        }
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
