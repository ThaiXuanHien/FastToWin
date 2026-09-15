package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertNotEquals

class ShopPurchaseLocalizationTest {
    @Test
    fun gemPurchaseAndGoldExchangeCopyDoesNotFallBackToEnglish() {
        val keys = setOf(
            TextKey.GoldVault, TextKey.GemVaultDescription, TextKey.ChooseGemPackage,
            TextKey.GemPackageDescription, TextKey.LoginToBuyGems, TextKey.PriceUnavailable,
            TextKey.EarnGemsDescription, TextKey.GoldVaultDescription,
            TextKey.ExchangeGemsForGold, TextKey.GoldExchangeConfirmation,
            TextKey.NotEnoughGems, TextKey.GoldExchangeGranted,
            TextKey.GoldExchangeAlreadyGranted, TextKey.GoldExchangeFailed,
            TextKey.BillingPlayNotReady, TextKey.BillingPriceLoadFailed,
            TextKey.BillingCancelled, TextKey.BillingUnavailable,
            TextKey.BillingPending, TextKey.BillingGemsAdded,
            TextKey.BillingWebUnsupported,
        )
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys { it != AppLanguage.ENGLISH }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
