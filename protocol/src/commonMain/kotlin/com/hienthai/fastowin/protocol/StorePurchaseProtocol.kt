package com.hienthai.fastowin.protocol

import kotlinx.serialization.Serializable

@Serializable
data class StorePurchaseRequest(
    val store: StorePlatform,
    val productId: String,
    val purchaseToken: String,
    val requestId: String
)

// Known consumables are also needed for restoring unfinished platform purchases without a game socket.
val STORE_PRODUCT_CATALOG = listOf(
    GemPackageSnapshot("fasttowin_gems_80", "", 80),
    GemPackageSnapshot("fasttowin_gems_250", "", 250, featured = true),
    GemPackageSnapshot("fasttowin_gems_650", "", 650)
)
