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
                // Extending an existing anonymous observation needs no identity lock: one batched statement.
                val updated = if (checkpoint.observations.isEmpty()) IntArray(0) else connection.prepareStatement(
                    "UPDATE activity_connection_observations SET observed_until = GREATEST(observed_until, ?) WHERE observation_id = ?"
                ).use { s ->
                    checkpoint.observations.forEach { observation ->
                        s.setTimestamp(1, Timestamp(observation.until)); s.setObject(2, observation.id); s.addBatch()
                    }
                    s.executeBatch()
                }
                val created = checkpoint.observations.filterIndexed { index, _ -> updated[index] == 0 }
                // Match analytics/deletion's identity lock before creating any anonymous links, only for new
                // observations. One sorted statement keeps a stable lock order across concurrent writers.
                val registered = if (created.isEmpty()) emptySet() else connection.prepareStatement(
                    "SELECT id FROM users WHERE id = ANY(?) AND account_type = 'REGISTERED' AND status <> 'DELETED' ORDER BY id FOR UPDATE"
                ).use { s ->
                    s.setArray(1, connection.createArrayOf("uuid", created.map { UUID.fromString(it.userId) }.distinct().toTypedArray()))
                    s.executeQuery().use { result -> buildSet { while (result.next()) add(result.getObject(1, UUID::class.java).toString()) } }
                }
                val subjects = registered.sorted().associateWith { userId -> AnalyticsFacts.subjectKey(connection, userId) }
                val inserts = created.filter { it.userId in subjects }
                if (inserts.isNotEmpty()) connection.prepareStatement("""
                    INSERT INTO activity_connection_observations(observation_id,connection_id,subject_key,started_at,observed_until)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()).use { s ->
                    inserts.forEach { observation ->
                        s.setObject(1, observation.id); s.setObject(2, observation.connectionId); s.setObject(3, subjects.getValue(observation.userId))
                        s.setTimestamp(4, Timestamp(maxOf(observation.from, cutoff))); s.setTimestamp(5, Timestamp(observation.until)); s.addBatch()
                    }
                    s.executeBatch()
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
