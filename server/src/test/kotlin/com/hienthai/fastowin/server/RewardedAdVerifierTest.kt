package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.RewardedAdProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RewardedAdVerifierTest {
    @Test
    fun `dev verifier accepts an exact account bound receipt`() = runTest {
        val verified = DevRewardedAdVerifier().verify(
            userId = "user-1",
            provider = RewardedAdProvider.DEV_SIMULATED,
            providerTransactionId = "transaction-1",
            proof = "FASTTOWIN_DEV_REWARDED_V1:user-1:transaction-1"
        )

        assertNotNull(verified)
        assertEquals(RewardedAdProvider.DEV_SIMULATED, verified.provider)
        assertEquals("transaction-1", verified.providerTransactionId)
    }

    @Test
    fun `dev verifier rejects a receipt copied to another account`() = runTest {
        val verified = DevRewardedAdVerifier().verify(
            userId = "user-2",
            provider = RewardedAdProvider.DEV_SIMULATED,
            providerTransactionId = "transaction-1",
            proof = "FASTTOWIN_DEV_REWARDED_V1:user-1:transaction-1"
        )

        assertNull(verified)
    }

    @Test
    fun `production placeholder rejects every receipt`() = runTest {
        val verified = RejectingRewardedAdVerifier.verify(
            userId = "user-1",
            provider = RewardedAdProvider.ADMOB_ANDROID,
            providerTransactionId = "transaction-1",
            proof = "provider-proof"
        )

        assertNull(verified)
    }
}
