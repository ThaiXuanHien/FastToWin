package com.hienthai.fastowin.server

import com.hienthai.fastowin.server.analytics.AnalyticsFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * Revenue facts for stores whose signed purchase payload carries the charged amount (App Store).
 * Writes run inside the caller's purchase or refund transaction. No receipt, token or user ID is
 * stored in the facts; the anonymous analytics subject links payment and refund.
 */
object StoreRevenueFacts {
    fun recordPayment(c: Connection, userId: String, productId: String, payment: StorePayment) =
        insert(c, userId, productId, payment.store, payment.environment, payment.transactionId, "PAYMENT",
            payment.amountMinor, payment.currency, payment.exponent, payment.occurredAt)

    fun recordRefund(c: Connection, userId: String, productId: String, refund: StorePayment) =
        insert(c, userId, productId, refund.store, refund.environment, refund.transactionId, "REFUND",
            refund.amountMinor, refund.currency, refund.exponent, refund.occurredAt)

    private fun insert(c: Connection, userId: String, productId: String, store: String, environment: String,
        transactionId: String, type: String, amount: BigInteger, currency: String, exponent: Int, at: Instant) {
        check(!c.autoCommit)
        val subject = AnalyticsFacts.subjectKey(c, userId)
        c.prepareStatement("""INSERT INTO analytics_financial_events(source,environment,event_id,event_type,transaction_id,
            product_id,subject_key,amount_minor,currency,currency_exponent,occurred_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING""").use { s ->
            s.setString(1, store); s.setString(2, environment); s.setString(3, eventId(transactionId, type))
            s.setString(4, type); s.setString(5, transactionId); s.setString(6, productId); s.setObject(7, subject)
            s.setBigDecimal(8, amount.toBigDecimal()); s.setString(9, currency); s.setInt(10, exponent)
            s.setTimestamp(11, Timestamp.from(at)); s.executeUpdate()
        }
        // Signed purchases prove these events, but not that every historical charge reached us: PARTIAL.
        c.prepareStatement("""INSERT INTO analytics_source_coverage(metric,source,environment,status,coverage_start,freshness)
            VALUES ('FINANCIAL',?,?,'PARTIAL',?,clock_timestamp())
            ON CONFLICT (metric,source,environment) DO UPDATE SET status='PARTIAL',
                coverage_start=COALESCE(analytics_source_coverage.coverage_start, EXCLUDED.coverage_start),
                freshness=GREATEST(COALESCE(analytics_source_coverage.freshness, EXCLUDED.freshness), EXCLUDED.freshness),
                updated_at=clock_timestamp()""").use { s ->
            s.setString(1, store); s.setString(2, environment); s.setTimestamp(3, Timestamp.from(at)); s.executeUpdate()
        }
    }

    private fun eventId(transactionId: String, type: String) = MessageDigest.getInstance("SHA-256")
        .digest("$transactionId\u0000$type".toByteArray()).joinToString("") { "%02x".format(it) }
}

enum class StoreRefundOutcome { REVOKED, ALREADY_REFUNDED, PURCHASE_NOT_FOUND }

data class StoreRefundResult(val outcome: StoreRefundOutcome, val gemsRevoked: Int = 0, val gemsShortfall: Int = 0)

/**
 * Owner policy (2026-11-24): a refunded Gem pack takes back its Gems, but never below zero. When the
 * player already spent some, the rest is recorded as [StoreRefundResult.gemsShortfall] for admins.
 * One wallet entry per purchase (`STORE_REFUND`), so replays and late duplicates change nothing.
 */
object StoreRefundLedger {
    fun revoke(c: Connection, store: String, receiptKey: String, refundedAt: Instant): Pair<StoreRefundResult, Purchase?> {
        check(!c.autoCommit)
        val purchase = c.prepareStatement("""SELECT user_id,product_id,gems_granted,refunded_at FROM store_purchases
            WHERE store=? AND transaction_id=? FOR UPDATE""").use { s ->
            s.setString(1, store); s.setString(2, receiptKey)
            s.executeQuery().use { r -> if (!r.next()) null else Purchase(r.getObject(1, UUID::class.java).toString(),
                r.getString(2), r.getInt(3), r.getTimestamp(4) != null) }
        } ?: return StoreRefundResult(StoreRefundOutcome.PURCHASE_NOT_FOUND) to null
        if (purchase.refunded) return StoreRefundResult(StoreRefundOutcome.ALREADY_REFUNDED) to purchase
        val userId = UUID.fromString(purchase.userId)
        val balance = c.prepareStatement("SELECT gems FROM player_stats WHERE user_id=? FOR UPDATE").use { s ->
            s.setObject(1, userId); s.executeQuery().use { r -> if (r.next()) r.getInt(1) else 0 }
        }
        val revoked = minOf(purchase.gemsGranted, balance.coerceAtLeast(0))
        if (revoked > 0) {
            c.prepareStatement("""INSERT INTO wallet_transactions(id,user_id,source_type,source_id,gems_delta)
                VALUES (?,?,'STORE_REFUND',?,?) ON CONFLICT (user_id,source_type,source_id) DO NOTHING""").use { s ->
                s.setObject(1, UUID.randomUUID()); s.setObject(2, userId); s.setString(3, "$store:$receiptKey")
                s.setInt(4, -revoked); check(s.executeUpdate() == 1)
            }
            c.prepareStatement("UPDATE player_stats SET gems=gems-?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?").use { s ->
                s.setInt(1, revoked); s.setObject(2, userId); check(s.executeUpdate() == 1)
            }
        }
        c.prepareStatement("""UPDATE store_purchases SET refunded_at=?,gems_revoked=? WHERE store=? AND transaction_id=?""").use { s ->
            s.setTimestamp(1, Timestamp.from(refundedAt)); s.setInt(2, revoked); s.setString(3, store); s.setString(4, receiptKey)
            check(s.executeUpdate() == 1)
        }
        return StoreRefundResult(StoreRefundOutcome.REVOKED, revoked, purchase.gemsGranted - revoked) to purchase
    }

    data class Purchase(val userId: String, val productId: String, val gemsGranted: Int, val refunded: Boolean)
}

data class AppStoreRefund(val receiptKey: String, val refund: StorePayment?, val transactionId: String,
    val environment: String, val refundedAt: Instant)

interface StoreRefundRepository {
    /** Applies one App Store refund atomically with its notification receipt; replays are no-ops. */
    suspend fun applyAppStoreRefund(notificationId: UUID, refund: AppStoreRefund): StoreRefundResult
}

class PostgresStoreRefundRepository(private val dataSource: DataSource) : StoreRefundRepository {
    override suspend fun applyAppStoreRefund(notificationId: UUID, refund: AppStoreRefund): StoreRefundResult =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { c ->
                c.autoCommit = false
                try {
                    val fresh = c.prepareStatement("""INSERT INTO app_store_notifications(notification_uuid,notification_type)
                        VALUES (?,'REFUND') ON CONFLICT DO NOTHING""").use { s -> s.setObject(1, notificationId); s.executeUpdate() == 1 }
                    if (!fresh) { c.rollback(); return@withContext StoreRefundResult(StoreRefundOutcome.ALREADY_REFUNDED) }
                    val (result, purchase) = StoreRefundLedger.revoke(c, "APP_STORE", refund.receiptKey, refund.refundedAt)
                    if (result.outcome == StoreRefundOutcome.REVOKED && purchase != null && refund.refund != null) {
                        StoreRevenueFacts.recordRefund(c, purchase.userId, purchase.productId, refund.refund)
                    }
                    c.commit()
                    result
                } catch (failure: Throwable) {
                    c.rollback(); throw failure
                } finally { c.autoCommit = true }
            }
        }
}

object UnavailableStoreRefundRepository : StoreRefundRepository {
    override suspend fun applyAppStoreRefund(notificationId: UUID, refund: AppStoreRefund): StoreRefundResult =
        throw java.sql.SQLException("Store refunds need durable storage")
}

enum class AppStoreNotificationOutcome { APPLIED, IGNORED, INVALID }

/**
 * App Store Server Notifications V2. Only REFUND changes state; every other verified type is
 * acknowledged so Apple stops retrying. Authenticity comes from Apple's signature, not the caller.
 */
class AppStoreNotificationService(
    private val refunds: StoreRefundRepository,
    private val bundleId: String = APP_STORE_DEFAULT_BUNDLE_ID,
    private val sandboxAllowed: Boolean = true,
    private val signed: AppleSignedPayloadVerifier = AppleSignedPayloadVerifier()
) {
    suspend fun handle(signedPayload: String): AppStoreNotificationOutcome {
        val payload = signed.verify(signedPayload) ?: return AppStoreNotificationOutcome.INVALID
        val notificationId = payload.string("notificationUUID")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return AppStoreNotificationOutcome.INVALID
        val data = payload["data"] as? JsonObject ?: return AppStoreNotificationOutcome.INVALID
        if (data.string("bundleId") != bundleId) return AppStoreNotificationOutcome.INVALID
        val environment = when (data.string("environment")) {
            "Production" -> "LIVE"
            "Sandbox" -> if (sandboxAllowed) "SANDBOX" else return AppStoreNotificationOutcome.IGNORED
            else -> return AppStoreNotificationOutcome.IGNORED
        }
        if (payload.string("notificationType") != "REFUND") return AppStoreNotificationOutcome.IGNORED
        val transaction = data.string("signedTransactionInfo")?.let(signed::verify) ?: return AppStoreNotificationOutcome.INVALID
        val transactionId = transaction.string("transactionId")
            ?.takeIf { it.length in 1..64 && it.all(Char::isDigit) } ?: return AppStoreNotificationOutcome.INVALID
        if (transaction.string("bundleId") != bundleId || transaction.string("environment") != data.string("environment")) {
            return AppStoreNotificationOutcome.INVALID
        }
        val refundedAt = transaction.long("revocationDate")?.let(Instant::ofEpochMilli) ?: return AppStoreNotificationOutcome.INVALID
        val payment = appStorePayment(transaction, environment, transactionId)?.copy(occurredAt = refundedAt)
        refunds.applyAppStoreRefund(notificationId, AppStoreRefund(appStoreReceiptKey(environment, transactionId),
            payment, transactionId, environment, refundedAt))
        return AppStoreNotificationOutcome.APPLIED
    }
}

internal fun configuredAppStoreNotifications(refunds: StoreRefundRepository?, values: Map<String, String> = System.getenv()):
    AppStoreNotificationService? {
    if (refunds == null || values["APP_STORE_BILLING_ENABLED"]?.trim()?.lowercase() == "false") return null
    val bundleId = values["APP_STORE_BUNDLE_ID"]?.trim()?.takeIf(String::isNotEmpty) ?: APP_STORE_DEFAULT_BUNDLE_ID
    val sandbox = values["APP_STORE_ALLOW_SANDBOX"]?.trim()?.lowercase() != "false"
    return AppStoreNotificationService(refunds, bundleId, sandbox)
}
