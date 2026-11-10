package com.hienthai.fastowin.server.activity

import com.hienthai.fastowin.server.analytics.AnalyticsFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class PostgresActivityRepository(private val dataSource: DataSource) : ActivityRepository {
    override suspend fun checkpoint(checkpoint: ActivityCheckpoint): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val cutoff = checkpoint.now - ACTIVITY_RETENTION_MILLIS
                // Match analytics/deletion's identity lock before creating any anonymous links.
                // Stable ordering prevents opposite socket iteration orders from deadlocking.
                // Existing anonymous rows only extend time; they need no live identity lock.
                val users = connection.createArrayOf("uuid", checkpoint.observations.map { UUID.fromString(it.userId) }.toTypedArray())
                val observationIds = connection.createArrayOf("uuid", checkpoint.observations.map { it.id }.toTypedArray())
                val registered = try {
                    connection.prepareStatement("""
                        SELECT id FROM users
                        WHERE id IN (
                            SELECT incoming.user_id FROM unnest(?::uuid[], ?::uuid[]) AS incoming(user_id, observation_id)
                            WHERE NOT EXISTS (
                                SELECT 1 FROM activity_connection_observations AS observation
                                WHERE observation.observation_id = incoming.observation_id
                            )
                        ) AND account_type = 'REGISTERED' AND status <> 'DELETED'
                        ORDER BY id FOR UPDATE
                    """.trimIndent()).use { s ->
                        s.setArray(1, users); s.setArray(2, observationIds)
                        s.executeQuery().use { result -> buildSet { while (result.next()) add(result.getObject(1, UUID::class.java).toString()) } }
                    }
                } finally { users.free(); observationIds.free() }
                // Update the whole healthy observation set in one round trip. The tracker still
                // serializes checkpoints, preserving its shared coverage-window semantics.
                val ids = connection.createArrayOf("uuid", checkpoint.observations.map { it.id }.toTypedArray())
                val until = connection.createArrayOf("int8", checkpoint.observations.map { it.until }.toTypedArray())
                val updated = try {
                    connection.prepareStatement("""
                        UPDATE activity_connection_observations AS observation
                        SET observed_until = GREATEST(observation.observed_until, to_timestamp(incoming.until_millis / 1000.0))
                        FROM unnest(?::uuid[], ?::bigint[]) AS incoming(id, until_millis)
                        WHERE observation.observation_id = incoming.id
                        RETURNING observation.observation_id
                    """.trimIndent()).use { s ->
                        s.setArray(1, ids); s.setArray(2, until)
                        s.executeQuery().use { result -> buildSet { while (result.next()) add(result.getObject(1, UUID::class.java)) } }
                    }
                } finally { ids.free(); until.free() }
                val subjects = mutableMapOf<String, UUID>()
                checkpoint.observations.forEach { observation ->
                    if (observation.id !in updated && observation.userId in registered) {
                        val subject = subjects.getOrPut(observation.userId) { AnalyticsFacts.subjectKey(connection, observation.userId) }
                        connection.prepareStatement("""
                            INSERT INTO activity_connection_observations(observation_id,connection_id,subject_key,started_at,observed_until)
                            VALUES (?, ?, ?, ?, ?)
                        """.trimIndent()).use { s ->
                            s.setObject(1, observation.id); s.setObject(2, observation.connectionId); s.setObject(3, subject)
                            s.setTimestamp(4, Timestamp(maxOf(observation.from, cutoff))); s.setTimestamp(5, Timestamp(observation.until)); s.executeUpdate()
                        }
                    }
                }
                connection.prepareStatement("""
                    INSERT INTO activity_coverage_windows(window_id,started_at,observed_until) VALUES (?, ?, ?)
                    ON CONFLICT (window_id) DO UPDATE SET observed_until = GREATEST(activity_coverage_windows.observed_until, EXCLUDED.observed_until)
                """.trimIndent()).use { s ->
                    s.setObject(1, checkpoint.window.id); s.setTimestamp(2, Timestamp(maxOf(checkpoint.window.from, cutoff)))
                    s.setTimestamp(3, Timestamp(checkpoint.window.until)); s.executeUpdate()
                }
                if (checkpoint.cleanup) cleanup(connection, cutoff)
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    private fun cleanup(connection: Connection, cutoff: Long) {
        // Retain an instantaneous connection event exactly at the cutoff, but not an interval ending there.
        connection.prepareStatement("DELETE FROM activity_connection_observations WHERE observed_until < ? OR (observed_until = ? AND started_at < observed_until)").use { s ->
            s.setTimestamp(1, Timestamp(cutoff)); s.setTimestamp(2, Timestamp(cutoff)); s.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM activity_coverage_windows WHERE observed_until < ?").use { s -> s.setTimestamp(1, Timestamp(cutoff)); s.executeUpdate() }
        listOf("activity_connection_observations", "activity_coverage_windows").forEach { table ->
            connection.prepareStatement("UPDATE $table SET started_at = ? WHERE started_at < ?").use { s ->
                s.setTimestamp(1, Timestamp(cutoff)); s.setTimestamp(2, Timestamp(cutoff)); s.executeUpdate()
            }
        }
    }
}
