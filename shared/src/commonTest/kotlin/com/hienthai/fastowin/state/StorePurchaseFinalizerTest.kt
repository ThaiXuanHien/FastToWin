package com.hienthai.fastowin.state

import com.hienthai.fastowin.data.network.StorePurchaseGateway
import com.hienthai.fastowin.platform.*
import com.hienthai.fastowin.protocol.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class StorePurchaseFinalizerTest {
    private val purchase = PlatformStorePurchase("paid-1", StorePlatform.GOOGLE_PLAY, "fasttowin_gems_80", "receipt")
    private class Billing : StoreBillingGateway {
        override val state = MutableStateFlow(StoreBillingState(StorePlatform.GOOGLE_PLAY))
        override val purchases = emptyFlow<PlatformStorePurchase>()
        val finished = mutableListOf<String>()
        var finishSucceeds = true
        var finishThrows = false
        override fun connect(packages: List<GemPackageSnapshot>, sandboxEnabled: Boolean) = Unit
        override fun purchase(productId: String, accountId: String?) = Unit
        override fun finishPurchase(purchaseToken: String) { finished += purchaseToken }
        override suspend fun finishPurchaseAndConfirm(purchaseToken: String): Boolean {
            if (finishThrows) throw IllegalStateException("platform disconnected")
            finishPurchase(purchaseToken)
            return finishSucceeds
        }
        override fun close() = Unit
    }
    @Test fun `failed receipts remain retryable without socket and only durable grant finishes platform`() = runTest {
        val billing = Billing()
        var attempts = 0
        val gateway = StorePurchaseGateway { request ->
            attempts++
            ServerMessage.StorePurchaseResult(request.requestId, request.productId, if (attempts == 1) StorePurchaseStatus.UNAVAILABLE else StorePurchaseStatus.ALREADY_GRANTED, message = "")
        }
        val finalizer = StorePurchaseFinalizer(billing)
        finalizer.setAccount("account", gateway)
        finalizer.enqueue(purchase)
        finalizer.retryPending()
        assertEquals(1, finalizer.pendingCount)
        assertTrue(billing.finished.isEmpty())
        assertEquals(0, finalizer.state.value.profileRevision)
        finalizer.retryPending()
        assertEquals(0, finalizer.pendingCount)
        assertEquals(listOf("receipt"), billing.finished)
        assertEquals(1, finalizer.state.value.profileRevision)
    }
    @Test fun `late receipt response fences new account profile and retains owner retry`() = runTest {
        val billing = Billing()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val old = StorePurchaseGateway { request ->
            entered.complete(Unit); release.await()
            ServerMessage.StorePurchaseResult(request.requestId, request.productId, StorePurchaseStatus.GRANTED, message = "")
        }
        val finalizer = StorePurchaseFinalizer(billing)
        finalizer.setAccount("old", old); finalizer.enqueue(purchase)
        val request = async { finalizer.retryPending() }
        entered.await()
        finalizer.setAccount("new", StorePurchaseGateway { error("old receipt must not verify as new user") })
        release.complete(Unit); request.await()
        assertEquals(0, finalizer.state.value.profileRevision)
        assertNull(finalizer.state.value.result)
        assertTrue(billing.finished.isEmpty())
        assertEquals(1, finalizer.pendingCount)
        finalizer.retryPending()
        finalizer.setAccount("old", old)
        finalizer.retryPending()
        assertEquals(listOf("receipt"), billing.finished)
        assertEquals(1, finalizer.state.value.profileRevision)
    }
    @Test fun `recovered bound purchase waits for correct account and duplicates retain one pending item`() = runTest {
        val billing = Billing()
        val finalizer = StorePurchaseFinalizer(billing)
        finalizer.enqueue(purchase.copy(obfuscatedAccountId = sha256("owner".encodeToByteArray())))
        finalizer.enqueue(purchase.copy(obfuscatedAccountId = sha256("owner".encodeToByteArray())))
        finalizer.setAccount("other", StorePurchaseGateway { error("wrong account") })
        finalizer.retryPending()
        assertEquals(1, finalizer.pendingCount)
        finalizer.setAccount("owner", StorePurchaseGateway { request ->
            ServerMessage.StorePurchaseResult(request.requestId, request.productId, StorePurchaseStatus.GRANTED, message = "")
        })
        finalizer.retryPending()
        assertEquals(0, finalizer.pendingCount)
    }
    @Test fun `platform consume failure keeps durable receipt retryable`() = runTest {
        val billing = Billing().apply { finishSucceeds = false }
        val finalizer = StorePurchaseFinalizer(billing)
        finalizer.setAccount("owner", StorePurchaseGateway { request ->
            ServerMessage.StorePurchaseResult(request.requestId, request.productId, StorePurchaseStatus.ALREADY_GRANTED, message = "")
        })
        finalizer.enqueue(purchase)
        finalizer.retryPending()
        assertEquals(1, finalizer.pendingCount)
        billing.finishSucceeds = true
        finalizer.retryPending()
        assertEquals(0, finalizer.pendingCount)
        assertEquals(listOf("receipt", "receipt"), billing.finished)
    }
    @Test fun `platform exception retains receipt and subsequent retry completes`() = runTest {
        val billing = Billing().apply { finishThrows = true }
        val finalizer = StorePurchaseFinalizer(billing)
        finalizer.setAccount("owner", StorePurchaseGateway { request ->
            ServerMessage.StorePurchaseResult(request.requestId, request.productId, StorePurchaseStatus.GRANTED, message = "")
        })
        finalizer.enqueue(purchase)
        finalizer.retryPending()
        assertEquals(1, finalizer.pendingCount)
        billing.finishThrows = false
        finalizer.retryPending()
        assertEquals(0, finalizer.pendingCount)
    }
    @Test fun `all nondurable statuses retain receipt and cancellation never finishes platform`() = runTest {
        for (status in listOf(StorePurchaseStatus.INVALID, StorePurchaseStatus.FAILED, StorePurchaseStatus.UNAVAILABLE)) {
            val billing = Billing()
            val finalizer = StorePurchaseFinalizer(billing)
            finalizer.setAccount("owner", StorePurchaseGateway { request -> ServerMessage.StorePurchaseResult(request.requestId, request.productId, status, message = "") })
            finalizer.enqueue(purchase); finalizer.retryPending()
            assertEquals(1, finalizer.pendingCount)
            assertEquals(0, finalizer.state.value.profileRevision)
            assertTrue(billing.finished.isEmpty())
            finalizer.setAccount("owner", StorePurchaseGateway { throw CancellationException("cancelled") })
            assertFailsWith<CancellationException> { finalizer.retryPending() }
            assertEquals(1, finalizer.pendingCount)
            assertTrue(billing.finished.isEmpty())
        }
    }
}
