package com.hienthai.fastowin.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

data class SeasonLifecycleResult(
    val archivedSeasons: Int = 0,
    val createdSeasons: Int = 0
)

interface SeasonLifecycleRepository {
    suspend fun maintain(nowEpochMillis: Long = System.currentTimeMillis()): SeasonLifecycleResult
}

object NoOpSeasonLifecycleRepository : SeasonLifecycleRepository {
    override suspend fun maintain(nowEpochMillis: Long) = SeasonLifecycleResult()
}

class PostgresSeasonLifecycleRepository(
    private val dataSource: DataSource
) : SeasonLifecycleRepository {
    override suspend fun maintain(nowEpochMillis: Long): SeasonLifecycleResult = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement("SELECT pg_advisory_xact_lock(?)").use { statement ->
                    statement.setLong(1, SEASON_LIFECYCLE_LOCK_ID)
                    statement.execute()
                }
                val now = Timestamp(nowEpochMillis)
                var archived = archiveEndedSeasons(connection, now)
                var created = 0

                while (!hasActiveSeason(connection, now)) {
                    val latest = latestSeason(connection)
                    if (latest != null && latest.endsAt.after(now)) break
                    createNextSeason(connection, now, latest)
                    created++
                    archived += archiveEndedSeasons(connection, now)
                }

                connection.commit()
                SeasonLifecycleResult(archivedSeasons = archived, createdSeasons = created)
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun archiveEndedSeasons(connection: Connection, now: Timestamp): Int {
        val seasonIds = connection.prepareStatement(
            "SELECT id FROM seasons WHERE ends_at <= ? AND closed_at IS NULL ORDER BY ends_at"
        ).use { statement ->
            statement.setTimestamp(1, now)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.getObject("id", UUID::class.java))
                }
            }
        }
        seasonIds.forEach { seasonId ->
            connection.prepareStatement(
                """
                INSERT INTO season_leaderboard_archive (
                    season_id, user_id, final_rank, display_name, player_code, avatar_url,
                    frame_id, wins, total_matches, highest_score, rating, season_matches, archived_at
                )
                SELECT ?, ranked.user_id, ranked.final_rank, ranked.display_name, ranked.player_code,
                       ranked.avatar_url, ranked.frame_id, ranked.wins, ranked.total_matches,
                       ranked.highest_score, ranked.rating, ranked.season_matches, ?
                FROM (
                    SELECT sr.user_id,
                           ROW_NUMBER() OVER (
                               ORDER BY sr.rating DESC, sr.matches_played ASC, sr.updated_at ASC, sr.user_id
                           )::INTEGER AS final_rank,
                           p.display_name, p.player_code, p.avatar_url,
                           COALESCE(ps.equipped_frame_id, 'frame_default') AS frame_id,
                           COALESCE(ps.wins, 0) AS wins,
                           COALESCE(ps.total_matches, 0) AS total_matches,
                           COALESCE(ps.highest_score, 0) AS highest_score,
                           sr.rating,
                           sr.matches_played AS season_matches
                    FROM season_ratings sr
                    JOIN profiles p ON p.user_id = sr.user_id
                    JOIN users u ON u.id = sr.user_id
                    LEFT JOIN player_stats ps ON ps.user_id = sr.user_id
                    WHERE sr.season_id = ?
                      AND sr.placement_matches >= 5
                      AND u.status = 'ACTIVE'
                ) ranked
                ON CONFLICT (season_id, user_id) DO NOTHING
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, seasonId)
                statement.setTimestamp(2, now)
                statement.setObject(3, seasonId)
                statement.executeUpdate()
            }
        }
        if (seasonIds.isNotEmpty()) {
            connection.prepareStatement(
                "UPDATE seasons SET closed_at = ? WHERE ends_at <= ? AND closed_at IS NULL"
            ).use { statement ->
                statement.setTimestamp(1, now)
                statement.setTimestamp(2, now)
                statement.executeUpdate()
            }
        }
        return seasonIds.size
    }

    private fun hasActiveSeason(connection: Connection, now: Timestamp): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM seasons WHERE ? >= starts_at AND ? < ends_at LIMIT 1"
        ).use { statement ->
            statement.setTimestamp(1, now)
            statement.setTimestamp(2, now)
            statement.executeQuery().use { it.next() }
        }

    private fun latestSeason(connection: Connection): StoredSeason? = connection.prepareStatement(
        "SELECT season_number, ends_at FROM seasons ORDER BY ends_at DESC, season_number DESC LIMIT 1"
    ).use { statement ->
        statement.executeQuery().use { result ->
            if (!result.next()) null else StoredSeason(
                number = result.getInt("season_number"),
                endsAt = result.getTimestamp("ends_at")
            )
        }
    }

    private fun createNextSeason(connection: Connection, now: Timestamp, latest: StoredSeason?) {
        val nextNumber = (latest?.number ?: 0) + 1
        connection.prepareStatement(
            if (latest == null) {
                """
                INSERT INTO seasons (
                    id, season_number, name, starts_at, ends_at, reward_description
                ) VALUES (
                    ?, ?, ?, date_trunc('month', ?::timestamptz),
                    date_trunc('month', ?::timestamptz) + INTERVAL '3 months', ?
                )
                """.trimIndent()
            } else {
                """
                INSERT INTO seasons (
                    id, season_number, name, starts_at, ends_at, reward_description
                ) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz + INTERVAL '3 months', ?)
                """.trimIndent()
            }
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setInt(2, nextNumber)
            statement.setString(3, "Mùa $nextNumber")
            if (latest == null) {
                statement.setTimestamp(4, now)
                statement.setTimestamp(5, now)
            } else {
                statement.setTimestamp(4, latest.endsAt)
                statement.setTimestamp(5, latest.endsAt)
            }
            statement.setString(6, STANDARD_REWARD_DESCRIPTION)
            statement.executeUpdate()
        }
    }

    private data class StoredSeason(val number: Int, val endsAt: Timestamp)

    private companion object {
        const val SEASON_LIFECYCLE_LOCK_ID = 4_617_354_981L
        const val STANDARD_REWARD_DESCRIPTION = "Vàng và Gem theo bậc xếp hạng cao nhất"
    }
}
