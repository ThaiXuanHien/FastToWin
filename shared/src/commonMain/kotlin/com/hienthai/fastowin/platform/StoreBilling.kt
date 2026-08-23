package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class StoreProductPrice(
    val productId: String,
    val formattedPrice: String,
    val available: Boolean = true
)

data class StoreBillingState(
    val platform: StorePlatform,
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val isSandboxFallback: Boolean = false,
    val prices: Map<String, StoreProductPrice> = emptyMap(),
    val purchasingProductId: String? = null,
    val notice: String? = null,
    val error: String? = null
)

data class PlatformStorePurchase(
    val requestId: String,
    val store: StorePlatform,
    val productId: String,
    val purchaseToken: String
)

interface StoreBillingGateway {
    val state: StateFlow<StoreBillingState>
    val purchases: Flow<PlatformStorePurchase>

    fun connect(packages: List<GemPackageSnapshot>, sandboxEnabled: Boolean)
    fun purchase(productId: String, accountId: String?)
    fun finishPurchase(purchaseToken: String)
    fun close()
}

@Composable
expect fun rememberStoreBillingGateway(): StoreBillingGateway
