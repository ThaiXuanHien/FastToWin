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
                val registered = checkpoint.observations.map { it.userId }.distinct().sorted().filter { userId ->
                    connection.prepareStatement("SELECT id FROM users WHERE id = ? AND account_type = 'REGISTERED' AND status <> 'DELETED' FOR UPDATE").use { s ->
                        s.setObject(1, UUID.fromString(userId)); s.executeQuery().use { it.next() }
                    }
                }.toSet()
                val subjects = mutableMapOf<String, UUID>()
                checkpoint.observations.forEach { observation ->
                    val updated = connection.prepareStatement("UPDATE activity_connection_observations SET observed_until = GREATEST(observed_until, ?) WHERE observation_id = ?").use { s ->
                        s.setTimestamp(1, Timestamp(observation.until)); s.setObject(2, observation.id); s.executeUpdate()
                    }
                    if (updated == 0 && observation.userId in registered) {
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
