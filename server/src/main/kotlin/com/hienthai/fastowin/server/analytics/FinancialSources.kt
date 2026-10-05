package com.hienthai.fastowin.server.analytics

import com.hienthai.fastowin.server.VerifiedStoreOrder
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

data class KnownFinancialOrder(val order: VerifiedStoreOrder, val productId: String,
    val receiptFingerprint: String, val subjectKey: UUID)
data class FinancialMoney(val minor: BigInteger, val currency: String, val exponent: Int)
data class FinancialEvent(val key: String, val type: String, val amount: FinancialMoney, val occurredAt: Instant)
data class FinancialSnapshot(val lastEventAt: Instant, val events: List<FinancialEvent>)

/** No browser payload implements this authority: only an authenticated provider adapter does. */
fun interface FinancialSource { fun fetch(order: KnownFinancialOrder): FinancialSnapshot }

object UnsupportedAppStoreFinancialSource : FinancialSource {
    const val dependency = "https://github.com/ThaiXuanHien/FastToWin/issues/14"
    override fun fetch(order: KnownFinancialOrder): FinancialSnapshot = throw FinancialSourceUnavailable()
}
class FinancialSourceUnavailable : RuntimeException("Financial source unavailable")
class FinancialSnapshotConflict : RuntimeException("Financial snapshot requires reconciliation")
