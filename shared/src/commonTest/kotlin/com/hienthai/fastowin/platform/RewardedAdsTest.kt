package com.hienthai.fastowin.platform

import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RewardedAdsTest {
    @Test
    fun `Web and iOS default gateways stay ready after Android enabled dev quota refresh`() = runTest {
        // Wire contract also asserted at the producing server's GameEngine and granted session HTTP boundary.
        val granted = ProtocolJson.decodeFromString<RewardedAdSessionView>("""{"sessionId":"session","customData":"nonce","adUnitId":"unit","expiresAtEpochMillis":1800001800000,"status":"GRANTED","providerTransactionId":"abcdef","quota":{"quotaDate":"2027-01-15","remainingMatches":12,"bonusMatchesGranted":2,"nextResetAtEpochMillis":1800032400000,"rewardedAdAvailability":"DEV_SIMULATED"}}""")
        for (isWeb in listOf(true, false)) {
            val gateway = DefaultRewardedAdGateway(isWeb)
            val receipts = mutableListOf<RewardedAdReceipt>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { gateway.receipts.take(2).toList(receipts) }
            gateway.configure(RewardedAdAvailability.DEV_SIMULATED)
            gateway.show("owner")
            gateway.configure(assertNotNull(granted.quota).rewardedAdAvailability)
            assertTrue(gateway.state.value.isReady)
            gateway.show("owner")
            collector.join()
            assertEquals(2, receipts.size)
            assertTrue(receipts.all { it.provider == RewardedAdProvider.DEV_SIMULATED && it.proof.startsWith("FASTTOWIN_DEV_REWARDED_V1:owner:") })
            gateway.close()
        }
    }
    @Test
    fun `dev receipt is bound to account and transaction`() {
        val receipt = assertNotNull(
            devRewardedAdReceipt(
                userId = "user-1",
                transactionId = "transaction-1",
                requestId = "request-1"
            )
        )

        assertEquals("request-1", receipt.requestId)
        assertEquals(RewardedAdProvider.DEV_SIMULATED, receipt.provider)
        assertEquals("transaction-1", receipt.providerTransactionId)
        assertEquals(
            "FASTTOWIN_DEV_REWARDED_V1:user-1:transaction-1",
            receipt.proof
        )
    }

    @Test
    fun `dev receipt rejects blank identity fields`() {
        assertEquals(null, devRewardedAdReceipt("", "transaction-1", "request-1"))
        assertEquals(null, devRewardedAdReceipt("user-1", "", "request-1"))
        assertEquals(null, devRewardedAdReceipt("user-1", "transaction-1", ""))
    }
}
