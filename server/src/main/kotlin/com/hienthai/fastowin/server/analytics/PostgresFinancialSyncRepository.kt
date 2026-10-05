package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.VerifiedStoreOrder
import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

class PostgresFinancialSyncRepository(private val dataSource: DataSource) {
    /** Lock spans the bounded provider GET: overlapping workers cannot apply out-of-order responses. */
    fun syncNext(source: FinancialSource, collectionStart: Instant, now: Instant): Boolean = transaction { c ->
        val row = c.prepareStatement("""SELECT * FROM store_financial_orders
            WHERE next_check_at <= ? ORDER BY next_check_at,order_id FOR UPDATE SKIP LOCKED LIMIT 1""").use { s ->
            s.setTimestamp(1, Timestamp.from(now))
            s.executeQuery().use { r -> if (!r.next()) null else Pair(
                KnownFinancialOrder(VerifiedStoreOrder(r.getString("order_id"), r.getString("package_name"), r.getString("environment")),
                    r.getString("product_id"), r.getString("receipt_fingerprint"), r.getObject("subject_key", UUID::class.java)),
                r.getTimestamp("last_event_at")?.toInstant()) }
        } ?: return@transaction false
        val (order, lastAt) = row
        var status = "OK"
        var acceptedAt: Instant? = null
        try {
            val fetched = source.fetch(order)
            // PostgreSQL timestamps retain microseconds. Normalize before comparison and insert,
            // never let the driver round a nanosecond into a different bucket or replay conflict.
            val snapshot = fetched.copy(lastEventAt = fetched.lastEventAt.truncatedTo(ChronoUnit.MICROS),
                events = fetched.events.map { it.copy(occurredAt = it.occurredAt.truncatedTo(ChronoUnit.MICROS)) })
            reconcile(c, order, lastAt, snapshot, collectionStart)
            acceptedAt = snapshot.lastEventAt
        } catch (_: FinancialSnapshotConflict) {
            status = "CONFLICT"
        } catch (_: FinancialSourceUnavailable) {
            status = "FAILED"
        }
        c.prepareStatement("""UPDATE store_financial_orders SET checked_at=?,next_check_at=?,
            last_event_at=COALESCE(?,last_event_at),sync_status=? WHERE source='GOOGLE_PLAY' AND environment=? AND order_id=?""").use { s ->
            s.setTimestamp(1, Timestamp.from(now)); s.setTimestamp(2, Timestamp.from(now.plusSeconds(3600)))
            s.setTimestamp(3, acceptedAt?.let(Timestamp::from)); s.setString(4, status)
            s.setString(5, order.order.environment); s.setString(6, order.order.orderId); s.executeUpdate()
        }
        // A known-order registry cannot prove complete store history. Failed first fetch is unknown, not zero.
        c.prepareStatement("""INSERT INTO analytics_source_coverage(metric,source,environment,status)
            VALUES ('FINANCIAL','GOOGLE_PLAY',?,'UNAVAILABLE') ON CONFLICT DO NOTHING""").use { s ->
            s.setString(1, order.order.environment); s.executeUpdate()
        }
        c.prepareStatement("""UPDATE analytics_source_coverage SET status=CASE WHEN EXISTS (
                SELECT 1 FROM analytics_financial_events WHERE source='GOOGLE_PLAY' AND environment=?) THEN 'PARTIAL' ELSE 'UNAVAILABLE' END,
            coverage_start=CASE WHEN coverage_start IS NULL THEN ? ELSE GREATEST(coverage_start,?) END,
            freshness=CASE WHEN ?='OK' THEN GREATEST(freshness,clock_timestamp()) ELSE freshness END,
            updated_at=clock_timestamp() WHERE metric='FINANCIAL' AND source='GOOGLE_PLAY' AND environment=?""").use { s ->
            s.setString(1, order.order.environment); s.setTimestamp(2, Timestamp.from(collectionStart)); s.setTimestamp(3, Timestamp.from(collectionStart))
            s.setString(4, status); s.setString(5, order.order.environment); s.executeUpdate()
        }
        true
    }

    private fun reconcile(c: Connection, order: KnownFinancialOrder, lastAt: Instant?, snapshot: FinancialSnapshot, start: Instant) {
        fun verify(value: Boolean) { if (!value) throw FinancialSnapshotConflict() }
        val events = snapshot.events
        val payment = events.singleOrNull { it.type == "PAYMENT" } ?: throw FinancialSnapshotConflict()
        verify(payment.occurredAt >= start)
        verify(events.map { it.key }.distinct().size == events.size)
        verify(events.all { it.type in setOf("PAYMENT", "REFUND") && it.amount.currency == payment.amount.currency &&
            it.amount.exponent == payment.amount.exponent && it.amount.minor.signum() > 0 &&
            it.amount.minor.toString().length <= 38 && it.occurredAt >= payment.occurredAt && it.occurredAt <= snapshot.lastEventAt })
        verify(events.filter { it.type == "REFUND" }.fold(BigInteger.ZERO) { acc, e -> acc + e.amount.minor } <= payment.amount.minor)
        val incoming = events.associateBy { eventId(order.order.orderId, it.key) }
        val existing = c.prepareStatement("""SELECT event_id,event_type,amount_minor,currency,currency_exponent,occurred_at
            FROM analytics_financial_events WHERE source='GOOGLE_PLAY' AND environment=? AND transaction_id=?""").use { s ->
            s.setString(1, order.order.environment); s.setString(2, order.order.orderId)
            s.executeQuery().use { r -> buildMap { while (r.next()) put(r.getString(1), FinancialEvent("", r.getString(2),
                FinancialMoney(r.getBigDecimal(3).toBigIntegerExact(), r.getString(4), r.getInt(5)), r.getTimestamp(6).toInstant())) } }
        }
        verify(lastAt == null || snapshot.lastEventAt >= lastAt)
        verify(existing.all { (id, prior) -> incoming[id]?.copy(key = "") == prior })
        verify(lastAt == null || snapshot.lastEventAt > lastAt || incoming.keys == existing.keys)
        for ((id, event) in incoming) {
            if (id in existing) continue
            c.prepareStatement("""INSERT INTO analytics_financial_events(source,environment,event_id,event_type,transaction_id,
                product_id,subject_key,amount_minor,currency,currency_exponent,occurred_at)
                VALUES ('GOOGLE_PLAY',?,?,?,?,?,?,?,?,?,?)""").use { s ->
                s.setString(1, order.order.environment); s.setString(2, id); s.setString(3, event.type)
                s.setString(4, order.order.orderId); s.setString(5, order.productId); s.setObject(6, order.subjectKey)
                s.setBigDecimal(7, event.amount.minor.toBigDecimal()); s.setString(8, event.amount.currency); s.setInt(9, event.amount.exponent)
                s.setTimestamp(10, Timestamp.from(event.occurredAt)); s.executeUpdate()
            }
        }
    }

    /** Called at process startup even when disabled, so old coverage never masquerades as active. */
    fun markUnavailable() = transaction { c -> c.createStatement().use { it.executeUpdate(
        "UPDATE analytics_source_coverage SET status='UNAVAILABLE',updated_at=clock_timestamp() WHERE metric='FINANCIAL'") }; Unit }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { c ->
        c.autoCommit = false
        try { val result = block(c); c.commit(); result } catch (failure: Throwable) { c.rollback(); throw failure }
    }

    companion object {
        /** Same transaction and identity lock as wallet grant. No identity foreign key is retained. */
        fun capture(c: Connection, userId: String, store: String, product: String, fingerprint: String, order: VerifiedStoreOrder) {
            check(!c.autoCommit)
            require(store == "GOOGLE_PLAY" && order.environment in setOf("LIVE", "SANDBOX"))
            require(order.orderId.length in 1..256 && order.packageName.length in 1..256)
            require(fingerprint.matches(Regex("[0-9a-f]{64}")))
            val subject = AnalyticsFacts.subjectKey(c, userId)
            c.prepareStatement("""INSERT INTO store_financial_orders(source,environment,order_id,package_name,product_id,receipt_fingerprint,subject_key)
                VALUES ('GOOGLE_PLAY',?,?,?,?,?,?) ON CONFLICT DO NOTHING""").use { s ->
                s.setString(1, order.environment); s.setString(2, order.orderId); s.setString(3, order.packageName)
                s.setString(4, product); s.setString(5, fingerprint); s.setObject(6, subject); s.executeUpdate()
            }
            c.prepareStatement("""SELECT package_name,product_id,receipt_fingerprint,subject_key FROM store_financial_orders
                WHERE source='GOOGLE_PLAY' AND environment=? AND order_id=? FOR UPDATE""").use { s ->
                s.setString(1, order.environment); s.setString(2, order.orderId)
                s.executeQuery().use { r -> check(r.next() && r.getString(1) == order.packageName && r.getString(2) == product &&
                    r.getString(3) == fingerprint && r.getObject(4, UUID::class.java) == subject) }
            }
        }
        private fun eventId(orderId: String, key: String) = MessageDigest.getInstance("SHA-256")
            .digest("$orderId\u0000$key".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
