package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberStoreBillingGateway(): StoreBillingGateway = remember {
    StoreKitBillingGateway(IosStoreKitBridge.host).also(IosStoreKitBridge::attach)
}

/**
 * StoreKit 2 bridge owned by the Swift host (`StoreKitCoordinator.swift`), like the push bridge.
 * Swift installs the native operations at launch and reports StoreKit results on the main thread.
 */
object IosStoreKitBridge : StoreKitEvents {
    private var installed: StoreKitHost? = null
    private var events: StoreKitEvents? = null

    internal val host: StoreKitHost? get() = installed

    fun install(
        loadProducts: (List<String>) -> Unit,
        purchase: (String, String) -> Unit,
        finish: (String) -> Unit
    ) {
        installed = object : StoreKitHost {
            override fun loadProducts(productIds: List<String>) = loadProducts(productIds)
            override fun purchase(productId: String, appAccountToken: String) = purchase(productId, appAccountToken)
            override fun finish(transactionId: String) = finish(transactionId)
        }
    }

    internal fun attach(gateway: StoreKitEvents) {
        events = gateway
    }

    override fun onProductsLoaded(productIds: List<String>, displayPrices: List<String>) {
        events?.onProductsLoaded(productIds, displayPrices)
    }
    override fun onProductsFailed() { events?.onProductsFailed() }
    override fun onTransaction(transactionId: String, productId: String, signedTransaction: String, appAccountToken: String?) {
        // Before Compose attaches, connect() re-reads Transaction.unfinished, so nothing is lost.
        events?.onTransaction(transactionId, productId, signedTransaction, appAccountToken)
    }
    override fun onPurchaseCancelled() { events?.onPurchaseCancelled() }
    override fun onPurchasePending() { events?.onPurchasePending() }
    override fun onPurchaseFailed() { events?.onPurchaseFailed() }
    override fun onFinished(transactionId: String, success: Boolean) { events?.onFinished(transactionId, success) }
}
