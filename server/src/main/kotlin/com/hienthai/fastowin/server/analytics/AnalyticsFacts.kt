package com.hienthai.fastowin.server.analytics

import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID

/** Writes inside the caller's identity/wallet transaction; never commits independently. */
object AnalyticsFacts {
    fun recordRegistration(connection: Connection, userId: String, registeredAt: Long) {
        val subject = subjectKey(connection, userId)
        connection.prepareStatement("""
            INSERT INTO analytics_registrations (subject_key, registered_at) VALUES (?, ?)
            ON CONFLICT (subject_key) DO NOTHING
        """.trimIndent()).use { statement ->
            statement.setObject(1, subject)
            statement.setTimestamp(2, Timestamp(registeredAt))
            statement.executeUpdate()
        }
        refresh(connection, "REGISTRATIONS")
    }

    fun recordItemPurchase(connection: Connection, eventId: UUID, userId: String, itemId: String,
                           currency: String, amount: Long, purchasedAt: Long) {
        require(currency == "GOLD" || currency == "GEMS")
        require(amount > 0)
        val subject = subjectKey(connection, userId)
        connection.prepareStatement("""
            INSERT INTO analytics_item_purchases (event_id, subject_key, item_id, currency, amount, purchased_at)
            VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
        """.trimIndent()).use { statement ->
            statement.setObject(1, eventId)
            statement.setObject(2, subject)
            statement.setString(3, itemId)
            statement.setString(4, currency)
            statement.setLong(5, amount)
            statement.setTimestamp(6, Timestamp(purchasedAt))
            statement.executeUpdate()
        }
        refresh(connection, "ITEM_PURCHASES")
    }

    /** Future verified adapters must use this same key and transaction before storing facts. */
    fun subjectKey(connection: Connection, userId: String): UUID {
        check(!connection.autoCommit) { "Analytics writes require the caller's transaction" }
        val id = UUID.fromString(userId)
        // A lock on the live identity prevents a concurrent delete from restoring this link.
        connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE").use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { check(it.next()) { "Analytics subject requires an existing identity" } }
        }
        connection.prepareStatement("""
            INSERT INTO analytics_subject_links (user_id, subject_key) VALUES (?, ?)
            ON CONFLICT (user_id) DO NOTHING
        """.trimIndent()).use { statement ->
            statement.setObject(1, id)
            statement.setObject(2, UUID.randomUUID())
            statement.executeUpdate()
        }
        return connection.prepareStatement("SELECT subject_key FROM analytics_subject_links WHERE user_id = ?").use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { result -> check(result.next()); result.getObject(1, UUID::class.java) }
        }
    }

    fun eraseIdentityLink(connection: Connection, userId: String) {
        check(!connection.autoCommit) { "Analytics erasure requires the caller's transaction" }
        connection.prepareStatement("DELETE FROM analytics_subject_links WHERE user_id = ?").use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeUpdate()
        }
    }

    private fun refresh(connection: Connection, metric: String) {
        connection.prepareStatement("""
            UPDATE analytics_source_coverage
            SET freshness = GREATEST(freshness, clock_timestamp()), updated_at = clock_timestamp()
            WHERE metric = ? AND source = 'SERVER' AND environment = 'LIVE'
        """.trimIndent()).use { statement -> statement.setString(1, metric); statement.executeUpdate() }
    }
}
