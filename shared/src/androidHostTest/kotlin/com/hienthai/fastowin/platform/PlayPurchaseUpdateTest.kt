package com.hienthai.fastowin.platform

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.protocol.StorePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayPurchaseUpdateTest {
    private val buying = StoreBillingState(
        platform = StorePlatform.GOOGLE_PLAY,
        isReady = true,
        prices = mapOf("gems" to StoreProductPrice("gems", "19,000 VND")),
        purchasingProductId = "gems"
    )

    @Test
    fun `billing error ends the attempt without claiming the device is unsupported`() {
        val failed = buying.afterPlayPurchaseUpdate(BillingResponseCode.BILLING_UNAVAILABLE)
        assertEquals(LocalizedText(TextKey.BillingIncomplete), failed.error)
        assertNull(failed.purchasingProductId)
        assertTrue(failed.isReady)
        assertEquals(buying.prices, failed.prices)
    }

    @Test
    fun `unsupported feature is distinguished from a payment failure`() {
        val unsupported = buying.afterPlayPurchaseUpdate(BillingResponseCode.FEATURE_NOT_SUPPORTED)
        assertEquals(LocalizedText(TextKey.BillingUnavailable), unsupported.error)
        assertNull(unsupported.purchasingProductId)
    }

    @Test
    fun `cancel releases the purchase and remains a notice`() {
        val cancelled = buying.afterPlayPurchaseUpdate(BillingResponseCode.USER_CANCELED)
        assertEquals(LocalizedText(TextKey.BillingCancelled), cancelled.notice)
        assertNull(cancelled.error)
        assertNull(cancelled.purchasingProductId)
    }

    @Test
    fun `already owned keeps the restore notice and releases the purchase`() {
        val restoring = buying.afterPlayPurchaseUpdate(BillingResponseCode.ITEM_ALREADY_OWNED)
        assertEquals(LocalizedText(TextKey.BillingRestoring), restoring.notice)
        assertNull(restoring.error)
        assertNull(restoring.purchasingProductId)
    }

    @Test
    fun `other failures allow retry without falsely reporting payment declined`() {
        for (code in listOf(BillingResponseCode.NETWORK_ERROR, BillingResponseCode.ERROR,
            BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.SERVICE_UNAVAILABLE)) {
            val failed = buying.afterPlayPurchaseUpdate(code)
            assertEquals(LocalizedText(TextKey.BillingIncomplete), failed.error)
            assertNull(failed.purchasingProductId)
            assertEquals(buying.prices, failed.prices)
        }
    }
}
