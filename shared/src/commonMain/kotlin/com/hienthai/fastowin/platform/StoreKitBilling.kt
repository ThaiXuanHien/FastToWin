package com.hienthai.fastowin.platform

import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import com.hienthai.fastowin.state.sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * StoreKit `appAccountToken` for an account. The server derives the same UUID
 * (`appStoreAccountToken` in server/AppStorePurchases.kt) so Apple never sees the internal id.
 */
fun appStoreAccountToken(accountId: String): String {
    val hex = sha256(accountId.encodeToByteArray()).take(32).toCharArray()
    hex[12] = '8' // RFC 9562 version 8 (custom)
    hex[16] = "89ab"[hex[16].digitToInt(16) and 0x3]
    val text = hex.concatToString()
    return "${text.substring(0, 8)}-${text.substring(8, 12)}-${text.substring(12, 16)}-" +
        "${text.substring(16, 20)}-${text.substring(20)}"
}

/** Native StoreKit 2 operations, implemented by the Swift host. Calls arrive and leave on the main thread. */
interface StoreKitHost {
    fun loadProducts(productIds: List<String>)
    fun purchase(productId: String, appAccountToken: String)
    fun finish(transactionId: String)
}

/** Events the Swift host reports back after StoreKit work. */
interface StoreKitEvents {
    fun onProductsLoaded(productIds: List<String>, displayPrices: List<String>)
    fun onProductsFailed()
    /** A verified, unrevoked consumable transaction that still needs the server grant and `finish()`. */
    fun onTransaction(transactionId: String, productId: String, signedTransaction: String, appAccountToken: String?)
    fun onPurchaseCancelled()
    fun onPurchasePending()
    fun onPurchaseFailed()
    fun onFinished(transactionId: String, success: Boolean)
}

/**
 * StoreKit 2 checkout mirroring the Google Play contract: the signed JWS is the purchase token,
 * and the transaction is finished only after the server reports GRANTED or ALREADY_GRANTED.
 */
class StoreKitBillingGateway(private val host: StoreKitHost?) : StoreBillingGateway, StoreKitEvents {
    private val _state = MutableStateFlow(StoreBillingState(StorePlatform.APP_STORE))
    override val state: StateFlow<StoreBillingState> = _state.asStateFlow()
    private val _purchases = MutableSharedFlow<PlatformStorePurchase>(extraBufferCapacity = 16)
    override val purchases: Flow<PlatformStorePurchase> = _purchases.asSharedFlow()
    private var packages = emptyList<GemPackageSnapshot>()
    private var sandboxEnabled = false
    private var realProducts = emptySet<String>()
    private val accountsByToken = mutableMapOf<String, String>()
    private val transactionByToken = mutableMapOf<String, String>()
    private val emittedTransactions = mutableSetOf<String>()
    private val finishing = mutableMapOf<String, CompletableDeferred<Boolean>>()

    override fun connect(packages: List<GemPackageSnapshot>, sandboxEnabled: Boolean) {
        this.packages = packages
        this.sandboxEnabled = sandboxEnabled
        if (packages.isEmpty()) {
            _state.update { it.copy(isLoading = false, isReady = false) }
            return
        }
        if (host == null) {
            useSandboxOrError(TextKey.BillingAppStoreNotConfigured)
            return
        }
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        // The host also re-reports Transaction.unfinished, so paid-but-ungranted work resumes here.
        host.loadProducts(packages.map { it.productId })
    }

    override fun onProductsLoaded(productIds: List<String>, displayPrices: List<String>) {
        val prices = productIds.zip(displayPrices)
            .filter { (id, _) -> packages.any { it.productId == id } }
            .associate { (id, price) -> id to StoreProductPrice(id, price) }
        realProducts = prices.keys
        if (prices.isEmpty()) {
            useSandboxOrError(TextKey.BillingProductsMissing)
            return
        }
        _state.update {
            it.copy(isLoading = false, isReady = true, isSandboxFallback = false, prices = prices, notice = null, error = null)
        }
    }

    override fun onProductsFailed() = useSandboxOrError(TextKey.BillingPriceLoadFailed)

    override fun purchase(productId: String, accountId: String?) {
        if (_state.value.purchasingProductId != null || packages.none { it.productId == productId }) return
        if (host == null || productId !in realProducts) {
            if (sandboxEnabled) emitSandboxPurchase(productId, accountId)
            else _state.update { it.copy(error = LocalizedText(TextKey.BillingProductUnavailable)) }
            return
        }
        // The server only grants transactions bound to the signed-in account.
        if (accountId == null) {
            _state.update { it.copy(error = LocalizedText(TextKey.BillingIncomplete)) }
            return
        }
        val token = appStoreAccountToken(accountId)
        accountsByToken[token] = accountId
        _state.update { it.copy(purchasingProductId = productId, error = null, notice = null) }
        host.purchase(productId, token)
    }

    override fun onTransaction(transactionId: String, productId: String, signedTransaction: String, appAccountToken: String?) {
        if (packages.none { it.productId == productId } || signedTransaction.isEmpty()) return
        _state.update { it.copy(purchasingProductId = productId, notice = LocalizedText(TextKey.BillingVerifying), error = null) }
        // StoreKit re-delivers a freshly signed JWS for the same transaction; the server keys on the id.
        if (!emittedTransactions.add(transactionId)) return
        transactionByToken[signedTransaction] = transactionId
        _purchases.tryEmit(PlatformStorePurchase(
            requestId = "ios-$transactionId-${randomSuffix()}",
            store = StorePlatform.APP_STORE,
            productId = productId,
            purchaseToken = signedTransaction,
            accountId = appAccountToken?.lowercase()?.let(accountsByToken::get)
        ))
    }

    override fun onPurchaseCancelled() =
        _state.update { it.copy(purchasingProductId = null, notice = LocalizedText(TextKey.BillingCancelled)) }

    override fun onPurchasePending() =
        _state.update { it.copy(purchasingProductId = null, notice = LocalizedText(TextKey.BillingPending)) }

    override fun onPurchaseFailed() =
        _state.update { it.copy(purchasingProductId = null, error = LocalizedText(TextKey.BillingIncomplete)) }

    override fun finishPurchase(purchaseToken: String) {
        if (purchaseToken.startsWith("dev:")) {
            markFinished(true)
            return
        }
        val transactionId = transactionByToken[purchaseToken] ?: return
        host?.finish(transactionId)
    }

    override suspend fun finishPurchaseAndConfirm(purchaseToken: String): Boolean {
        if (purchaseToken.startsWith("dev:")) {
            markFinished(true)
            return true
        }
        val transactionId = transactionByToken[purchaseToken] ?: return false
        val host = host ?: return false
        val done = finishing.getOrPut(transactionId) { CompletableDeferred() }
        host.finish(transactionId)
        val finished = withTimeoutOrNull(10_000) { done.await() } ?: false
        if (finishing[transactionId] === done) finishing.remove(transactionId)
        if (finished) {
            transactionByToken.remove(purchaseToken)
            emittedTransactions.remove(transactionId)
        }
        return finished
    }

    override fun onFinished(transactionId: String, success: Boolean) {
        finishing[transactionId]?.complete(success)
        markFinished(success)
    }

    private fun markFinished(success: Boolean) = _state.update {
        it.copy(purchasingProductId = null,
            notice = LocalizedText(if (success) TextKey.BillingGemsAdded else TextKey.BillingFinishLater))
    }

    private fun emitSandboxPurchase(productId: String, accountId: String?) {
        val suffix = randomSuffix()
        _state.update { it.copy(purchasingProductId = productId, notice = LocalizedText(TextKey.BillingSandboxVerifying)) }
        _purchases.tryEmit(PlatformStorePurchase(
            requestId = "ios-$suffix",
            store = StorePlatform.APP_STORE,
            productId = productId,
            purchaseToken = "dev:${StorePlatform.APP_STORE.name}:$productId:$suffix",
            accountId = accountId
        ))
    }

    private fun useSandboxOrError(messageKey: TextKey) {
        realProducts = emptySet()
        if (sandboxEnabled) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isReady = true,
                    isSandboxFallback = true,
                    prices = packages.associate { p -> p.productId to StoreProductPrice(p.productId, "Sandbox") },
                    notice = LocalizedText(TextKey.BillingStoreKitSandboxNotice),
                    error = null
                )
            }
        } else {
            _state.update { it.copy(isLoading = false, isReady = false, error = LocalizedText(messageKey)) }
        }
    }

    override fun close() = Unit

    private fun randomSuffix() = Random.nextLong().toULong().toString(16)
}
