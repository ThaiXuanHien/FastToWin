package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hienthai.fastowin.protocol.GemPackageSnapshot
import com.hienthai.fastowin.protocol.StorePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

@Composable
actual fun rememberStoreBillingGateway(): StoreBillingGateway = remember {
    WebUnavailableStoreBillingGateway()
}

private class WebUnavailableStoreBillingGateway : StoreBillingGateway {
    private val mutableState = MutableStateFlow(
        StoreBillingState(
            platform = StorePlatform.GOOGLE_PLAY,
            isReady = false,
            error = "Mua Gem trên web chưa được hỗ trợ. Hãy dùng ứng dụng Android hoặc iOS."
        )
    )
    override val state: StateFlow<StoreBillingState> = mutableState
    override val purchases: Flow<PlatformStorePurchase> = emptyFlow()

    override fun connect(packages: List<GemPackageSnapshot>, sandboxEnabled: Boolean) = Unit
    override fun purchase(productId: String, accountId: String?) = Unit
    override fun finishPurchase(purchaseToken: String) = Unit
    override fun close() = Unit
}
