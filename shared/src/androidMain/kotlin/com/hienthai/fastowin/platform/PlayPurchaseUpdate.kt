package com.hienthai.fastowin.platform

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.hienthai.fastowin.localization.LocalizedText
import com.hienthai.fastowin.localization.TextKey

/** Non-success checkout updates release the attempt; only purchased receipts enter verification. */
internal fun StoreBillingState.afterPlayPurchaseUpdate(responseCode: Int): StoreBillingState =
    when (responseCode) {
        BillingResponseCode.USER_CANCELED ->
            copy(purchasingProductId = null, notice = LocalizedText(TextKey.BillingCancelled))
        BillingResponseCode.ITEM_ALREADY_OWNED ->
            copy(purchasingProductId = null, notice = LocalizedText(TextKey.BillingRestoring))
        // BILLING_UNAVAILABLE also covers payment failures; only this code proves unsupported billing.
        BillingResponseCode.FEATURE_NOT_SUPPORTED ->
            copy(purchasingProductId = null, error = LocalizedText(TextKey.BillingUnavailable))
        else -> copy(purchasingProductId = null, error = LocalizedText(TextKey.BillingIncomplete))
    }
