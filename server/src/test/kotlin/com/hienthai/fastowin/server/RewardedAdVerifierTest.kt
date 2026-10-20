package com.hienthai.fastowin.server

import com.hienthai.fastowin.protocol.*
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RewardedAdVerifierTest {
    @Test
    fun `Android enabled dev quota snapshots preserve Web and iOS simulation availability`() = runTest {
        val playerId = UUID.randomUUID()
        val availability = configuredRewardedAdAvailability("dev", adMobEnabled = true)
        val engine = GameEngine(rewardedAdAvailability = availability)
        engine.connectAccount(AuthenticatedAccount(playerId, "Web or iOS dev"))
        val quota = engine.handle(playerId.toString(), ClientMessage.GetPlayQuota)
            .map(Delivery::message).filterIsInstance<ServerMessage.PlayQuotaData>().single().quota
        assertEquals(RewardedAdAvailability.DEV_SIMULATED, quota.rewardedAdAvailability)
        assertEquals(RewardedAdAvailability.DEV_SIMULATED, configuredRewardedAdAvailability("dev", false))
        assertEquals(RewardedAdAvailability.MOBILE_PRODUCTION, configuredRewardedAdAvailability("prod", true))
        assertEquals(RewardedAdAvailability.UNAVAILABLE, configuredRewardedAdAvailability("prod", false))
    }
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
