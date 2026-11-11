package com.hienthai.fastowin.server.ownership

import com.hienthai.fastowin.server.DatabaseSettings
import com.hienthai.fastowin.server.StoredActivePlayer
import com.hienthai.fastowin.server.events.PostgresRewardEventRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.util.UUID

/** Owned recovery/economy storage, without public repositories or legacy startup maintenance. */
internal class OwnedWorkerDatabaseRuntime private constructor(private val source: HikariDataSource) : AutoCloseable {
    internal suspend fun openRoomOwnershipPilot(config: RoomOwnershipRuntimeConfig): RoomOwnershipPilot {
        require(config.fixtureId == null) { "An owned worker requires production room ownership" }
        return RoomOwnershipPilot.open(config, source, ::accountPlayer, PostgresRewardEventRepository(source),
            // Task 1A must replace this with authoritative durable admission policy. Existing work continues.
            newIntentAllowed = { false })
    }

    private suspend fun accountPlayer(id: UUID): StoredActivePlayer? = withContext(Dispatchers.IO) {
        source.connection.use { connection ->
            connection.prepareStatement("""
                SELECT p.display_name, p.avatar_url, COALESCE(s.equipped_frame_id, 'frame_default')
                FROM profiles p JOIN users u ON u.id = p.user_id
                LEFT JOIN player_stats s ON s.user_id = p.user_id
                WHERE u.id = ? AND u.account_type = 'REGISTERED' AND u.status = 'ACTIVE' AND u.email_verified_at IS NOT NULL
            """.trimIndent()).use { statement ->
                statement.setObject(1, id)
                statement.executeQuery().use { rows ->
                    if (rows.next()) StoredActivePlayer(id.toString(), rows.getString(1), true, rows.getString(2), rows.getString(3))
                    else null
                }
            }
        }
    }

    override fun close() = source.close()

    companion object {
        fun open(settings: DatabaseSettings): OwnedWorkerDatabaseRuntime {
            val source = HikariDataSource(HikariConfig().apply {
                jdbcUrl = settings.url
                username = settings.user
                password = settings.password
                maximumPoolSize = settings.maximumPoolSize
                minimumIdle = 1
                connectionTimeout = 7_000
                validationTimeout = 3_000
                poolName = "fasttowin-owned-worker-db"
            })
            try {
                Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
                return OwnedWorkerDatabaseRuntime(source)
            } catch (failure: Throwable) {
                source.close()
                throw failure
            }
        }
    }
}
