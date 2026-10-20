package com.hienthai.fastowin.platform

import com.hienthai.fastowin.data.network.RewardedAdSessionGateway
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VerifiedRewardedAdGatewayTest {
    private val pending = RewardedAdSessionView("session_1", "opaque_nonce", "owned_unit", 1000000, RewardedAdSessionStatus.PENDING)
    private class Store : PendingRewardedAdStore {
        val values = mutableMapOf<String, String>()
        override fun read(ownerId: String) = values[ownerId]
        override fun write(ownerId: String, sessionId: String?) { if (sessionId == null) values.remove(ownerId) else values[ownerId] = sessionId }
    }
    private inner class Api(override val ownerId: String = "owner") : RewardedAdSessionGateway {
        var prepared = 0
        var preparedView = pending
        var checked = 0
        var next = pending
        override suspend fun prepare(requestId: String): RewardedAdSessionView { prepared++; return preparedView }
        override suspend fun status(sessionId: String): RewardedAdSessionView { checked++; return next }
        override fun close() = Unit
    }
    private class Driver : RewardedAdDriver {
        var authorization = RewardedAdAuthorization.ALLOWED
        var shown = 0
        var available = true
        var loadResult: CompletableDeferred<Boolean>? = null
        override val adUnitId = "owned_unit"
        var outcome = CompletableDeferred<RewardedAdShowResult>()
        override suspend fun authorize() = authorization
        override suspend fun load() = loadResult?.await() ?: available
        override suspend fun show(session: RewardedAdSessionView): RewardedAdShowResult { shown++; return outcome.await() }
        override fun close() = Unit
    }
    private fun TestScope.gateway(api: Api, driver: Driver, store: Store = Store()) =
        VerifiedRewardedAdGateway(api, driver, store, backgroundScope, nowMillis = { testScheduler.currentTime })

    @Test fun `dismissal remains visible during pending verification timeout and retry`() = runTest {
        assertPendingOutcomeFeedback(RewardedAdShowResult.DISMISSED, com.hienthai.fastowin.localization.TextKey.RewardedAdCancelled)
    }
    @Test fun `show failure remains visible during pending verification timeout and retry`() = runTest {
        assertPendingOutcomeFeedback(RewardedAdShowResult.UNAVAILABLE, com.hienthai.fastowin.localization.TextKey.RewardedAdUnavailable)
    }
    private suspend fun TestScope.assertPendingOutcomeFeedback(outcome: RewardedAdShowResult, feedbackKey: com.hienthai.fastowin.localization.TextKey) {
        val api = Api(); val driver = Driver(); val store = Store(); val gateway = gateway(api, driver, store)
        val receipts = mutableListOf<RewardedAdReceipt>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.receipts.toList(receipts) }
        gateway.configure(RewardedAdAvailability.MOBILE_PRODUCTION); gateway.show("owner"); runCurrent()
        driver.outcome.complete(outcome); runCurrent()
        fun assertVisiblePending() {
            assertTrue(gateway.state.value.isPendingVerification)
            assertEquals(outcome, gateway.state.value.presentationOutcome)
            assertEquals(listOf(feedbackKey, com.hienthai.fastowin.localization.TextKey.RewardedAdPending), rewardedAdFeedback(gateway.state.value).map { it.key })
            assertEquals("session_1", store.read("owner")); assertTrue(receipts.isEmpty())
        }
        assertVisiblePending(); assertTrue(gateway.state.value.isLoading)
        advanceTimeBy(60001); runCurrent()
        assertVisiblePending(); assertFalse(gateway.state.value.isLoading)
        gateway.show("owner"); runCurrent()
        assertVisiblePending(); assertTrue(gateway.state.value.isLoading)
        assertEquals(1, api.prepared); assertEquals(1, driver.shown)
        api.next = pending.copy(status = RewardedAdSessionStatus.GRANTED, providerTransactionId = "verified", quota = PlayQuotaSnapshot("2026-10-20", nextResetAtEpochMillis = 999999))
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, receipts.size); assertFalse(gateway.state.value.isPendingVerification)
        assertNull(gateway.state.value.presentationOutcome)
        assertEquals(listOf(com.hienthai.fastowin.localization.TextKey.RewardedAdGranted), rewardedAdFeedback(gateway.state.value).map { it.key })
        gateway.close()
    }
    @Test fun `becoming available clears initial unavailable error`() = runTest {
        val gateway = gateway(Api(), Driver())
        gateway.configure(RewardedAdAvailability.UNAVAILABLE)
        assertNotNull(gateway.state.value.error)
        gateway.configure(RewardedAdAvailability.DEV_SIMULATED)
        assertTrue(gateway.state.value.isReady)
        assertNull(gateway.state.value.error)
        gateway.close()
    }
    @Test fun `double taps cannot show twice and SDK earned alone cannot grant`() = runTest {
        val api = Api(); val driver = Driver(); val store = Store(); val gateway = gateway(api, driver, store)
        val receipts = mutableListOf<RewardedAdReceipt>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.receipts.toList(receipts) }
        gateway.configure(RewardedAdAvailability.DEV_SIMULATED)
        gateway.show("owner"); gateway.show("owner"); runCurrent()
        assertEquals(1, api.prepared); assertEquals(1, driver.shown)
        assertEquals("session_1", store.read("owner"))
        driver.outcome.complete(RewardedAdShowResult.EARNED); runCurrent()
        assertTrue(receipts.isEmpty())
        assertTrue(gateway.state.value.isLoading)
        api.next = pending.copy(status = RewardedAdSessionStatus.GRANTED, providerTransactionId = "real_tx", quota = PlayQuotaSnapshot("2026-10-20", nextResetAtEpochMillis = 999999))
        advanceTimeBy(1000); runCurrent()
        assertEquals(listOf(RewardedAdReceipt("session_1", RewardedAdProvider.ADMOB_ANDROID, "real_tx", "session_1")), receipts)
        gateway.show("owner"); runCurrent()
        assertEquals(1, receipts.size)
        gateway.close()
    }
    @Test fun `pending times out within sixty seconds and is recovered after restart without another ad`() = runTest {
        val api = Api(); val driver = Driver(); val store = Store(); val gateway = gateway(api, driver, store)
        gateway.configure(RewardedAdAvailability.MOBILE_PRODUCTION)
        gateway.show("owner"); runCurrent(); driver.outcome.complete(RewardedAdShowResult.EARNED)
        advanceTimeBy(60001); runCurrent()
        assertFalse(gateway.state.value.isLoading)
        assertTrue(gateway.state.value.isPendingVerification)
        assertEquals("session_1", store.read("owner"))
        gateway.close()
        api.next = pending.copy(status = RewardedAdSessionStatus.GRANTED, providerTransactionId = "verified", quota = PlayQuotaSnapshot("2026-10-20", nextResetAtEpochMillis = 999999))
        val recovered = gateway(api, Driver(), store)
        val receipts = mutableListOf<RewardedAdReceipt>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recovered.receipts.toList(receipts) }
        recovered.configure(RewardedAdAvailability.DEV_SIMULATED); runCurrent()
        assertEquals(1, receipts.size); assertEquals(1, api.prepared)
        assertNull(store.read("owner")); recovered.close()
    }
    @Test fun `wrong owner close and delayed callbacks cannot emit receipts`() = runTest {
        val api = Api(); val driver = Driver(); val store = Store(); val gateway = gateway(api, driver, store)
        val receipts = mutableListOf<RewardedAdReceipt>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.receipts.toList(receipts) }
        gateway.configure(RewardedAdAvailability.DEV_SIMULATED)
        gateway.show("another_owner"); runCurrent(); assertEquals(0, api.prepared)
        gateway.show("owner"); runCurrent(); gateway.close()
        driver.outcome.complete(RewardedAdShowResult.EARNED)
        api.next = pending.copy(status = RewardedAdSessionStatus.GRANTED, providerTransactionId = "late", quota = PlayQuotaSnapshot("2026-10-20", nextResetAtEpochMillis = 999999))
        advanceTimeBy(60001); runCurrent()
        assertTrue(receipts.isEmpty()); assertEquals("session_1", store.read("owner"))
        val other = gateway(Api("other"), Driver(), store)
        other.configure(RewardedAdAvailability.DEV_SIMULATED); runCurrent()
        assertFalse(other.state.value.isPendingVerification); other.close()
    }
    @Test fun `privacy denied unknown or under13 never prepares or requests an ad`() = runTest {
        for (authorization in listOf(RewardedAdAuthorization.AGE_REQUIRED, RewardedAdAuthorization.AGE_BLOCKED, RewardedAdAuthorization.DENIED)) {
            val api = Api(); val driver = Driver().apply { this.authorization = authorization }; val gateway = gateway(api, driver)
            gateway.configure(RewardedAdAvailability.DEV_SIMULATED); gateway.show("owner"); runCurrent()
            assertEquals(0, api.prepared); assertEquals(0, driver.shown)
            assertFalse(gateway.state.value.isLoading); assertNotNull(gateway.state.value.error)
            gateway.close()
        }
    }
    @Test fun `no fill and cancellation never emit a local reward`() = runTest {
        for (outcome in RewardedAdShowResult.entries) {
            val api = Api(); val driver = Driver(); val gateway = gateway(api, driver)
            val receipts = mutableListOf<RewardedAdReceipt>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.receipts.toList(receipts) }
            gateway.configure(RewardedAdAvailability.MOBILE_PRODUCTION); gateway.show("owner"); runCurrent()
            driver.outcome.complete(outcome); advanceTimeBy(60001); runCurrent()
            assertTrue(receipts.isEmpty()); assertFalse(gateway.state.value.isLoading)
            gateway.close()
        }
    }
    @Test fun `expired or mismatched session cannot be shown or granted`() = runTest {
        val api = Api(); val store = Store(); store.write("owner", "session_1")
        api.next = pending.copy(sessionId = "other", status = RewardedAdSessionStatus.GRANTED, providerTransactionId = "tx", quota = PlayQuotaSnapshot("2026-10-20", nextResetAtEpochMillis = 999999))
        val gateway = gateway(api, Driver(), store)
        val receipts = mutableListOf<RewardedAdReceipt>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.receipts.toList(receipts) }
        gateway.configure(RewardedAdAvailability.MOBILE_PRODUCTION); runCurrent()
        assertTrue(receipts.isEmpty()); gateway.close()
    }
    @Test fun `closing quota dialog cancels loading before it can show an ad`() = runTest {
        val api = Api(); val load = CompletableDeferred<Boolean>(); val driver = Driver().apply { loadResult = load }
        val gateway = gateway(api, driver)
        gateway.configure(RewardedAdAvailability.DEV_SIMULATED); gateway.show("owner"); runCurrent()
        assertTrue(gateway.state.value.isLoading)
        gateway.cancel(); runCurrent(); load.complete(true); runCurrent()
        assertFalse(gateway.state.value.isLoading); assertEquals(0, api.prepared); assertEquals(0, driver.shown)
        gateway.close()
    }
    @Test fun `no fill consumes no pending server session and allows retry`() = runTest {
        val api = Api(); val driver = Driver().apply { available = false }; val store = Store()
        val gateway = gateway(api, driver, store)
        gateway.configure(RewardedAdAvailability.DEV_SIMULATED); gateway.show("owner"); runCurrent()
        assertEquals(0, api.prepared); assertNull(store.read("owner")); assertFalse(gateway.state.value.isLoading)
        driver.available = true; gateway.show("owner"); runCurrent()
        assertEquals(1, api.prepared); assertEquals(1, driver.shown); gateway.close()
    }
    @Test fun `expired prepare or wrong unit is rejected before SDK show`() = runTest {
        for (view in listOf(pending.copy(expiresAtEpochMillis = -1), pending.copy(adUnitId = "wrong_unit"))) {
            val api = Api().apply { preparedView = view }; val driver = Driver(); val store = Store()
            val gateway = gateway(api, driver, store)
            gateway.configure(RewardedAdAvailability.MOBILE_PRODUCTION); gateway.show("owner"); runCurrent()
            assertEquals(0, driver.shown); assertNull(store.read("owner")); assertNotNull(gateway.state.value.error)
            gateway.close()
        }
    }
    @Test fun `granted status missing authoritative quota or transaction cannot emit receipt`() = runTest {
        for (view in listOf(pending.copy(status = RewardedAdSessionStatus.GRANTED), pending.copy(status = RewardedAdSessionStatus.GRANTED, providerTransactionId = "tx"))) {
            val api = Api().apply { next = view }; val store = Store().apply { write("owner", "session_1") }
            val gateway = gateway(api, Driver(), store); val receipts = mutableListOf<RewardedAdReceipt>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gateway.receipts.toList(receipts) }
            gateway.configure(RewardedAdAvailability.MOBILE_PRODUCTION); runCurrent()
            assertTrue(receipts.isEmpty()); assertEquals("session_1", store.read("owner")); gateway.close()
        }
    }
    @Test fun `owned test mode without registered device cannot request and demo cannot use owned unit`() {
        // Synthetic identifier syntax only: no request is sent to any provider.
        val syntheticApp = "ca-app-pub-0000000000000000~0000000000"
        val syntheticUnit = "ca-app-pub-0000000000000000/0000000000"
        assertFalse(RewardedAdConfiguration(RewardedAdMode.TEST, syntheticApp, syntheticUnit, emptyList()).canRequest)
        assertTrue(RewardedAdConfiguration(RewardedAdMode.TEST, syntheticApp, syntheticUnit, listOf("A".repeat(32))).canRequest)
        assertFalse(RewardedAdConfiguration(RewardedAdMode.DEMO, "owned", "owned", emptyList()).canRequest)
        assertTrue(RewardedAdConfiguration(RewardedAdMode.DEMO, RewardedAdConfiguration.DEMO_APP_ID, RewardedAdConfiguration.DEMO_UNIT_ID).canRequest)
        assertFalse(RewardedAdConfiguration().canRequest)
    }
}
