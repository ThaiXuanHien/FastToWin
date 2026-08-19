package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.ProtocolJson
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

interface TournamentRepository {
    suspend fun loadActive(): List<TournamentSnapshot>
    suspend fun loadRecent(playerId: String, limit: Int): List<TournamentSnapshot>
    suspend fun save(tournament: TournamentSnapshot)
}

class InMemoryTournamentRepository : TournamentRepository {
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
}

class PostgresTournamentRepository(
    private val dataSource: HikariDataSource
) : TournamentRepository {
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
        dataSource.connection.use { connection ->
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
}
