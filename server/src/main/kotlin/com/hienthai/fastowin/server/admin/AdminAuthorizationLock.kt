package com.hienthai.fastowin.server.admin

import java.sql.Connection

/**
 * PostgreSQL advisory lock that orders admin access changes (role, revocation, moderation
 * terms, account deletion) against everything that must see a consistent snapshot of them.
 *
 * ADM-01: holders that only need that snapshot take it SHARED, so registrations, guest
 * sessions and admin reads (search, overview, analytics) run concurrently and never queue
 * behind each other. Holders that change the protected state keep taking it EXCLUSIVE, which
 * still waits for, and then blocks, every shared holder - the original ordering guarantee.
 */
internal object AdminAuthorizationLock {
    const val KEY = 7421092187349L

    /** Transaction-scoped exclusive lock for writers of access or moderation state. */
    fun exclusive(connection: Connection) = execute(connection, "SELECT pg_advisory_xact_lock($KEY)")

    /** Transaction-scoped shared lock for readers of access or moderation state. */
    fun shared(connection: Connection) = execute(connection, "SELECT pg_advisory_xact_lock_shared($KEY)")

    /** Session-scoped shared lock; release it with [unlockSessionShared] before returning the connection. */
    fun sessionShared(connection: Connection) = execute(connection, "SELECT pg_advisory_lock_shared($KEY)")

    fun unlockSessionShared(connection: Connection): Boolean =
        connection.prepareStatement("SELECT pg_advisory_unlock_shared($KEY)").use { statement ->
            statement.executeQuery().use { it.next() && it.getBoolean(1) }
        }

    private fun execute(connection: Connection, sql: String) {
        connection.prepareStatement(sql).use { it.execute() }
    }
}
