package com.hienthai.fastowin.server

import kotlinx.coroutines.test.runTest
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class AppStoreNotificationServiceTest {
    private val chain = AppStoreTestChain()
    private val userId = UUID.randomUUID().toString()

    private class RecordingRefunds : StoreRefundRepository {
        val applied = mutableListOf<Pair<UUID, AppStoreRefund>>()
        override suspend fun applyAppStoreRefund(notificationId: UUID, refund: AppStoreRefund): StoreRefundResult {
            applied += notificationId to refund
            return StoreRefundResult(StoreRefundOutcome.REVOKED, 80)
        }
    }

    private fun service(refunds: StoreRefundRepository, sandbox: Boolean = true) = AppStoreNotificationService(
        refunds, sandboxAllowed = sandbox, signed = AppleSignedPayloadVerifier(setOf(chain.root)))

    private val revokedAt = Instant.parse("2026-11-24T03:00:00Z")
    private fun refundedTransaction(extra: Map<String, Any?> = emptyMap()) = chain.transaction(userId, mapOf(
        "transactionId" to "2000000999",
        "price" to 19_000_000, "currency" to "VND",
        "revocationDate" to revokedAt.toEpochMilli(), "revocationReason" to 0) + extra)

    @Test fun `verified refund applies once with Apple's amount keyed on the purchase receipt`() = runTest {
        val refunds = RecordingRefunds()
        assertEquals(AppStoreNotificationOutcome.APPLIED, service(refunds).handle(chain.notification("REFUND", refundedTransaction())))
        val (_, refund) = refunds.applied.single()
        assertEquals(appStoreReceiptKey("LIVE", "2000000999"), refund.receiptKey)
        assertEquals(revokedAt, refund.refundedAt)
        val amount = assertNotNull(refund.refund)
        assertEquals(BigInteger.valueOf(19_000_000), amount.amountMinor)
        assertEquals("VND", amount.currency); assertEquals(3, amount.exponent); assertEquals(revokedAt, amount.occurredAt)
    }

    @Test fun `other verified notification types are acknowledged without changes`() = runTest {
        val refunds = RecordingRefunds()
        for (type in listOf("CONSUMPTION_REQUEST", "TEST", "REFUND_DECLINED")) {
            assertEquals(AppStoreNotificationOutcome.IGNORED, service(refunds).handle(chain.notification(type, chain.transaction(userId))))
        }
        assertEquals(AppStoreNotificationOutcome.IGNORED, service(refunds, sandbox = false).handle(
            chain.notification("REFUND", refundedTransaction(mapOf("environment" to "Sandbox")), data = mapOf("environment" to "Sandbox"))))
        assertTrue(refunds.applied.isEmpty())
    }

    @Test fun `forged, foreign or incomplete refunds are rejected`() = runTest {
        val refunds = RecordingRefunds()
        val other = AppStoreTestChain()
        val cases = listOf(
            other.notification("REFUND", refundedTransaction()),                       // not Apple's chain
            chain.notification("REFUND", other.transaction(userId, mapOf("revocationDate" to 1L))), // forged inner transaction
            chain.notification("REFUND", refundedTransaction(), data = mapOf("bundleId" to "com.example.other")),
            chain.notification("REFUND", refundedTransaction(mapOf("revocationDate" to null))),
            chain.notification("REFUND", refundedTransaction(mapOf("environment" to "Sandbox"))), // inner/outer mismatch
            chain.notification("REFUND", null),
            chain.notification("REFUND", refundedTransaction(), overrides = mapOf("notificationUUID" to "not-a-uuid")),
            "not.a.jws"
        )
        cases.forEach { assertEquals(AppStoreNotificationOutcome.INVALID, service(refunds).handle(it)) }
        assertTrue(refunds.applied.isEmpty())
    }

    @Test fun `refund without a signed price still revokes but records no amount`() = runTest {
        val refunds = RecordingRefunds()
        val noPrice = chain.transaction(userId, mapOf("transactionId" to "2000000998", "revocationDate" to revokedAt.toEpochMilli()))
        assertEquals(AppStoreNotificationOutcome.APPLIED, service(refunds).handle(chain.notification("REFUND", noPrice)))
        assertNull(refunds.applied.single().second.refund)
    }

    @Test fun `signed purchase price becomes a milliunit payment only when present and positive`() {
        val verifier = AppleSignedPayloadVerifier(setOf(chain.root))
        val paid = assertNotNull(verifier.verify(chain.transaction(userId, mapOf("price" to 49_000_000, "currency" to "VND"))))
        val payment = assertNotNull(appStorePayment(paid, "LIVE", "2000000123456789"))
        assertEquals(BigInteger.valueOf(49_000_000), payment.amountMinor); assertEquals("VND", payment.currency)
        assertNull(appStorePayment(assertNotNull(verifier.verify(chain.transaction(userId))), "LIVE", "1"))
        assertNull(appStorePayment(assertNotNull(verifier.verify(chain.transaction(userId, mapOf("price" to 0, "currency" to "USD")))), "LIVE", "1"))
        assertNull(appStorePayment(paid, "XCODE", "1"))
    }
}
