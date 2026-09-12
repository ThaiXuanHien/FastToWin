package com.hienthai.fastowin.platform

import com.hienthai.fastowin.protocol.RewardedAdProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RewardedAdsTest {
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
