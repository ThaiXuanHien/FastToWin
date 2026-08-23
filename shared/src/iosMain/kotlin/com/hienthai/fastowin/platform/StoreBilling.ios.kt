package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

@Composable
actual fun rememberStoreBillingGateway(): StoreBillingGateway = remember { IosSandboxStoreBillingGateway() }

private class IosSandboxStoreBillingGateway : StoreBillingGateway {
    private val _state = MutableStateFlow(StoreBillingState(StorePlatform.APP_STORE))
    override val state: StateFlow<StoreBillingState> = _state.asStateFlow()
    private val _purchases = MutableSharedFlow<PlatformStorePurchase>(extraBufferCapacity = 4)
    override val purchases: Flow<PlatformStorePurchase> = _purchases.asSharedFlow()
    private var packages = emptyList<GemPackageSnapshot>()
    private var sandboxEnabled = false

    override fun connect(packages: List<GemPackageSnapshot>, sandboxEnabled: Boolean) {
        this.packages = packages
        this.sandboxEnabled = sandboxEnabled
        _state.value = if (sandboxEnabled) {
            StoreBillingState(
                platform = StorePlatform.APP_STORE,
                isReady = true,
                isSandboxFallback = true,
                prices = packages.associate {
                    it.productId to StoreProductPrice(it.productId, "Sandbox")
                },
                notice = "StoreKit sandbox cần được xác nhận trên macOS trước khi phát hành."
            )
        } else {
            StoreBillingState(
                platform = StorePlatform.APP_STORE,
                error = "App Store chưa được cấu hình cho bản production."
            )
        }
    }

    override fun purchase(productId: String, accountId: String?) {
        if (!sandboxEnabled || packages.none { it.productId == productId }) return
        val suffix = Random.nextLong().toString().replace("-", "")
        val token = "dev:${StorePlatform.APP_STORE.name}:$productId:$suffix"
        val requestId = "ios-$suffix"
        _state.update { it.copy(purchasingProductId = productId, notice = "Đang xác thực giao dịch sandbox...") }
        _purchases.tryEmit(PlatformStorePurchase(
            requestId = requestId,
            store = StorePlatform.APP_STORE,
            productId = productId,
            purchaseToken = token
        ))
    }

    override fun finishPurchase(purchaseToken: String) {
        _state.update { it.copy(purchasingProductId = null, notice = "Đã cộng Gem vào tài khoản.") }
    }

    override fun close() = Unit
}
