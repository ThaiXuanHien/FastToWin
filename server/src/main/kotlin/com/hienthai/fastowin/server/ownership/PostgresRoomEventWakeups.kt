package com.hienthai.fastowin.server.ownership

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.postgresql.PGConnection
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

private const val CHANNEL = "room_ownership_pilot"
private const val NOTIFICATION_WAIT_MILLIS = 500
private const val LISTENER_RETRY_MILLIS = 1_000L

internal fun postgresRoomEventWakeups(dataSource: DataSource): Flow<UUID> = channelFlow {
    val listeningConnection = AtomicReference<Connection?>()
    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                dataSource.connection.use { connection ->
                    listeningConnection.set(connection)
                    try {
                        ensureActive()
                        connection.autoCommit = true
                        connection.setNetworkTimeout(Runnable::run, 1_000)
                        val postgres = connection.unwrap(PGConnection::class.java)
                        connection.createStatement().use { it.execute("LISTEN $CHANNEL") }
                        while (isActive) {
                            for (notification in postgres.getNotifications(NOTIFICATION_WAIT_MILLIS).orEmpty()) {
                                if (notification.name != CHANNEL) continue
                                val roomId = try {
                                    UUID.fromString(notification.parameter.substringBefore(':'))
                                } catch (_: IllegalArgumentException) {
                                    continue
                                }
                                send(roomId)
                            }
                        }
                    } finally {
                        if (listeningConnection.compareAndSet(connection, null)) {
                            // LISTEN is session-scoped. Remove it before returning a connection to a pool.
                            try {
                                connection.createStatement().use { it.execute("UNLISTEN $CHANNEL") }
                            } catch (_: SQLException) {
                                runCatching { connection.abort(Runnable::run) }
                            }
                        }
                    }
                }
            } catch (_: SQLException) {
                // Reconciliation runs independently while the dedicated listener reconnects.
                delay(LISTENER_RETRY_MILLIS)
            }
        }
    }
    awaitClose {
        // Abort a blocking JDBC read as part of cancellation, without waiting for it to return.
        listeningConnection.getAndSet(null)?.let { runCatching { it.abort(Runnable::run) } }
    }
}
