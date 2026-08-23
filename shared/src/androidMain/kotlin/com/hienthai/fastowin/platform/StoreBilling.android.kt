package com.hienthai.fastowin.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import java.util.UUID

@Composable
actual fun rememberStoreBillingGateway(): StoreBillingGateway {
    val context = LocalContext.current
    return remember(context) { AndroidStoreBillingGateway(requireNotNull(context.findActivity())) }
}

private class AndroidStoreBillingGateway(
    private val activity: Activity
) : StoreBillingGateway, PurchasesUpdatedListener {
    private val _state = MutableStateFlow(StoreBillingState(StorePlatform.GOOGLE_PLAY))
    override val state: StateFlow<StoreBillingState> = _state.asStateFlow()
    private val _purchases = MutableSharedFlow<PlatformStorePurchase>(extraBufferCapacity = 8)
    override val purchases: Flow<PlatformStorePurchase> = _purchases.asSharedFlow()
    private val productDetails = mutableMapOf<String, ProductDetails>()
    private val emittedTokens = mutableSetOf<String>()
    private var packages = emptyList<GemPackageSnapshot>()
    private var sandboxEnabled = false

    private val billingClient = BillingClient.newBuilder(activity.applicationContext)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    override fun connect(packages: List<GemPackageSnapshot>, sandboxEnabled: Boolean) {
        this.packages = packages
        this.sandboxEnabled = sandboxEnabled
        _state.update { it.copy(isLoading = true, error = null, notice = null) }
        if (packages.isEmpty()) {
            _state.update { it.copy(isLoading = false, isReady = false) }
            return
        }
        if (billingClient.isReady) {
            queryProducts()
            queryUnfinishedPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryUnfinishedPurchases()
                } else {
                    useSandboxOrError("Google Play Billing chưa sẵn sàng.")
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(isReady = false, notice = "Đang kết nối lại Google Play...") }
            }
        })
    }

    private fun queryProducts() {
        val products = packages.map { gemPackage ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(gemPackage.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                useSandboxOrError("Không tải được giá từ Google Play.")
                return@queryProductDetailsAsync
            }
            productDetails.clear()
            queryResult.productDetailsList.forEach { productDetails[it.productId] = it }
            if (productDetails.isEmpty()) {
                useSandboxOrError("Các gói Gem chưa được tạo trên Google Play Console.")
                return@queryProductDetailsAsync
            }
            val prices = productDetails.mapValues { (productId, details) ->
                val formatted = details.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()?.formattedPrice ?: "--"
                StoreProductPrice(productId, formatted)
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    isReady = true,
                    isSandboxFallback = false,
                    prices = prices,
                    notice = null,
                    error = null
                )
            }
        }
    }

    private fun queryUnfinishedPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    override fun purchase(productId: String, accountId: String?) {
        if (_state.value.purchasingProductId != null) return
        val details = productDetails[productId]
        if (details == null) {
            if (sandboxEnabled) emitSandboxPurchase(productId) else {
                _state.update { it.copy(error = "Gói Gem này chưa sẵn sàng trên Google Play.") }
            }
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let(::setOfferToken)
            }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setIsOfferPersonalized(false)
            .apply { accountId?.let { setObfuscatedAccountId(obfuscatedAccountId(it)) } }
            .build()
        _state.update { it.copy(purchasingProductId = productId, error = null, notice = null) }
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(purchasingProductId = null, error = "Không thể mở thanh toán Google Play.")
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.update { it.copy(purchasingProductId = null, notice = "Đã hủy thanh toán.") }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(purchasingProductId = null, notice = "Đang khôi phục giao dịch trước...") }
                queryUnfinishedPurchases()
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                _state.update { it.copy(purchasingProductId = null, error = "Google Play Billing không khả dụng trên thiết bị.") }
            else -> _state.update { it.copy(purchasingProductId = null, error = "Thanh toán chưa hoàn tất.") }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    val productId = purchase.products.firstOrNull { id -> packages.any { it.productId == id } }
                        ?: return@forEach
                    if (emittedTokens.add(purchase.purchaseToken)) {
                        _purchases.tryEmit(PlatformStorePurchase(
                            requestId = UUID.randomUUID().toString(),
                            store = StorePlatform.GOOGLE_PLAY,
                            productId = productId,
                            purchaseToken = purchase.purchaseToken
                        ))
                    }
                    _state.update { it.copy(purchasingProductId = productId, notice = "Đang xác thực giao dịch...") }
                }
                Purchase.PurchaseState.PENDING -> _state.update {
                    it.copy(purchasingProductId = null, notice = "Thanh toán đang chờ Google Play xử lý.")
                }
                else -> Unit
            }
        }
    }

    private fun emitSandboxPurchase(productId: String) {
        val token = "dev:${StorePlatform.GOOGLE_PLAY.name}:$productId:${UUID.randomUUID()}"
        _state.update { it.copy(purchasingProductId = productId, notice = "Đang xác thực giao dịch sandbox...") }
        _purchases.tryEmit(PlatformStorePurchase(
            requestId = UUID.randomUUID().toString(),
            store = StorePlatform.GOOGLE_PLAY,
            productId = productId,
            purchaseToken = token
        ))
    }

    override fun finishPurchase(purchaseToken: String) {
        if (purchaseToken.startsWith("dev:")) {
            emittedTokens.remove(purchaseToken)
            _state.update { it.copy(purchasingProductId = null, notice = "Đã cộng Gem vào tài khoản.") }
            return
        }
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        billingClient.consumeAsync(params) { result, token ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                emittedTokens.remove(token)
                _state.update { it.copy(purchasingProductId = null, notice = "Đã cộng Gem vào tài khoản.") }
            } else {
                _state.update { it.copy(purchasingProductId = null, notice = "Gem đã được cộng; giao dịch sẽ được hoàn tất lại sau.") }
            }
        }
    }

    private fun useSandboxOrError(message: String) {
        if (sandboxEnabled) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isReady = true,
                    isSandboxFallback = true,
                    prices = packages.associate { gemPackage ->
                        gemPackage.productId to StoreProductPrice(gemPackage.productId, "Sandbox")
                    },
                    notice = "Đang dùng thanh toán thử nghiệm.",
                    error = null
                )
            }
        } else {
            _state.update { it.copy(isLoading = false, isReady = false, error = message) }
        }
    }

    override fun close() = billingClient.endConnection()
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun obfuscatedAccountId(accountId: String): String = MessageDigest.getInstance("SHA-256")
    .digest(accountId.toByteArray())
    .joinToString("") { "%02x".format(it) }
