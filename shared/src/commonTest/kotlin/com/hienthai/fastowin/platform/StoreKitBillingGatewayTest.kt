package com.hienthai.fastowin.platform

import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.STORE_PRODUCT_CATALOG
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*

class StoreKitBillingGatewayTest {
    private class Host : StoreKitHost {
        var gateway: StoreKitBillingGateway? = null
        val loaded = mutableListOf<List<String>>()
        val purchases = mutableListOf<Pair<String, String>>()
        val finished = mutableListOf<String>()
        var finishSucceeds = true
        override fun loadProducts(productIds: List<String>) { loaded += productIds }
        override fun purchase(productId: String, appAccountToken: String) { purchases += productId to appAccountToken }
        override fun finish(transactionId: String) {
            finished += transactionId
            gateway?.onFinished(transactionId, finishSucceeds)
        }
    }
    private val account = "3f2b8c1e-0a4d-4e6f-9b7a-1c2d3e4f5a6b"

    private fun ready(): Pair<Host, StoreKitBillingGateway> {
        val host = Host()
        val gateway = StoreKitBillingGateway(host).also { host.gateway = it }
        gateway.connect(STORE_PRODUCT_CATALOG, sandboxEnabled = false)
        gateway.onProductsLoaded(STORE_PRODUCT_CATALOG.map { it.productId }, listOf("19.000 ₫", "49.000 ₫", "99.000 ₫"))
        return host to gateway
    }

    @Test fun `account token matches the server derivation`() {
        assertEquals("487b948d-8c60-8882-b59e-c3879c5c9017", appStoreAccountToken(account))
    }

    @Test fun `loads App Store prices from the host`() {
        val (host, gateway) = ready()
        assertEquals(listOf(STORE_PRODUCT_CATALOG.map { it.productId }), host.loaded)
        val state = gateway.state.value
        assertTrue(state.isReady)
        assertFalse(state.isSandboxFallback)
        assertEquals("49.000 ₫", state.prices.getValue("fasttowin_gems_250").formattedPrice)
    }

    @Test fun `purchase binds the account and emits the signed transaction once`() = runTest {
        val (host, gateway) = ready()
        val emitted = mutableListOf<PlatformStorePurchase>()
        val collector = launch { gateway.purchases.collect { emitted += it } }
        yield()
        gateway.purchase("fasttowin_gems_80", account)
        assertEquals(listOf("fasttowin_gems_80" to appStoreAccountToken(account)), host.purchases)
        assertEquals("fasttowin_gems_80", gateway.state.value.purchasingProductId)
        gateway.purchase("fasttowin_gems_250", account) // a second checkout is ignored while one is open
        assertEquals(1, host.purchases.size)

        gateway.onTransaction("2000000001", "fasttowin_gems_80", "jws-1", appStoreAccountToken(account).uppercase())
        gateway.onTransaction("2000000001", "fasttowin_gems_80", "jws-1-resigned", appStoreAccountToken(account))
        yield()
        val purchase = emitted.single()
        assertEquals(StorePlatform.APP_STORE, purchase.store)
        assertEquals("jws-1", purchase.purchaseToken)
        assertEquals(account, purchase.accountId)
        assertTrue(purchase.requestId.length in 1..64)

        assertTrue(gateway.finishPurchaseAndConfirm("jws-1"))
        assertEquals(listOf("2000000001"), host.finished)
        assertEquals(TextKey.BillingGemsAdded, gateway.state.value.notice?.key)
        assertNull(gateway.state.value.purchasingProductId)
        collector.cancel()
    }

    @Test fun `unknown token or failed finish keeps the transaction retryable`() = runTest {
        val (host, gateway) = ready()
        assertFalse(gateway.finishPurchaseAndConfirm("never-seen"))
        assertTrue(host.finished.isEmpty())
        val first = launch { gateway.purchases.first() }
        yield()
        gateway.onTransaction("2000000002", "fasttowin_gems_650", "jws-2", null)
        first.join()
        host.finishSucceeds = false
        assertFalse(gateway.finishPurchaseAndConfirm("jws-2"))
        assertEquals(TextKey.BillingFinishLater, gateway.state.value.notice?.key)
        host.finishSucceeds = true
        assertTrue(gateway.finishPurchaseAndConfirm("jws-2"))
    }

    @Test fun `ignores transactions for products outside the catalog`() = runTest {
        val (_, gateway) = ready()
        val emitted = mutableListOf<PlatformStorePurchase>()
        val collector = launch { gateway.purchases.collect { emitted += it } }
        yield()
        gateway.onTransaction("3", "com.example.other", "jws", null)
        yield()
        assertTrue(emitted.isEmpty())
        collector.cancel()
    }

    @Test fun `user outcomes update the shop state`() {
        val (_, gateway) = ready()
        gateway.purchase("fasttowin_gems_80", account)
        gateway.onPurchaseCancelled()
        assertEquals(TextKey.BillingCancelled, gateway.state.value.notice?.key)
        assertNull(gateway.state.value.purchasingProductId)
        gateway.purchase("fasttowin_gems_80", account)
        gateway.onPurchasePending()
        assertEquals(TextKey.BillingPending, gateway.state.value.notice?.key)
        gateway.purchase("fasttowin_gems_80", account)
        gateway.onPurchaseFailed()
        assertEquals(TextKey.BillingIncomplete, gateway.state.value.error?.key)
        gateway.purchase("fasttowin_gems_80", null)
        assertEquals(TextKey.BillingIncomplete, gateway.state.value.error?.key)
    }

    @Test fun `missing products use sandbox only when the server enables it`() = runTest {
        val host = Host()
        val closed = StoreKitBillingGateway(host)
        closed.connect(STORE_PRODUCT_CATALOG, sandboxEnabled = false)
        closed.onProductsLoaded(emptyList(), emptyList())
        assertEquals(TextKey.BillingProductsMissing, closed.state.value.error?.key)
        closed.purchase("fasttowin_gems_80", account)
        assertEquals(TextKey.BillingProductUnavailable, closed.state.value.error?.key)
        assertTrue(host.purchases.isEmpty())

        val sandbox = StoreKitBillingGateway(host)
        sandbox.connect(STORE_PRODUCT_CATALOG, sandboxEnabled = true)
        sandbox.onProductsFailed()
        assertTrue(sandbox.state.value.isSandboxFallback)
        val emitted = launch { assertTrue(sandbox.purchases.first().purchaseToken.startsWith("dev:APP_STORE:fasttowin_gems_80:")) }
        yield()
        sandbox.purchase("fasttowin_gems_80", account)
        emitted.join()
        assertTrue(sandbox.finishPurchaseAndConfirm("dev:APP_STORE:fasttowin_gems_80:x"))
        assertTrue(host.purchases.isEmpty())
    }

    @Test fun `without a native host production reports App Store not configured`() {
        val gateway = StoreKitBillingGateway(null)
        gateway.connect(STORE_PRODUCT_CATALOG, sandboxEnabled = false)
        assertEquals(TextKey.BillingAppStoreNotConfigured, gateway.state.value.error?.key)
    }
}
