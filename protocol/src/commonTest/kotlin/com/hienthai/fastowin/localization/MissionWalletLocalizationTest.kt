package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotEquals

class MissionWalletLocalizationTest {
    @Test
    fun missionActionsAndProgressDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.DailyMissions, TextKey.WeeklyMissions, TextKey.NoMissions,
            TextKey.MissionProgress, TextKey.Claimed, TextKey.Claiming,
            TextKey.ClaimReward, TextKey.MissionCompleted, TextKey.DifficultyEasy,
            TextKey.DifficultyNormal, TextKey.DifficultyHard,
        )
    }

    @Test
    fun walletEmptyStatesAndTransactionDescriptionsDoNotFallBackToEnglish() {
        assertLocalized(
            TextKey.NoWalletTransactions, TextKey.NoWalletTransactionsForFilter,
            TextKey.DailyCheckInSource, TextKey.MissionRewardSource,
            TextKey.MatchRewardSource, TextKey.ClanRewardSource,
            TextKey.ShopPurchaseSource, TextKey.WalletOtherSource,
            TextKey.WalletBalanceDescription, TextKey.RewardGoldDescription,
            TextKey.RewardGemsDescription,
            TextKey.WalletReceivedGold, TextKey.WalletUsedGold,
            TextKey.WalletReceivedXp, TextKey.WalletUsedXp,
            TextKey.WalletReceivedGems, TextKey.WalletUsedGems,
            TextKey.NoWalletActivity, TextKey.WalletRewardsAppear,
            TextKey.WalletNoActivityInFilter,
        )
    }

    @Test
    fun thaiWalletUsesTheExistingGemAndClanTerminology() {
        val thai = allLocalizationCatalogs.getValue(AppLanguage.THAI).texts
        assertContains(thai.getValue(TextKey.WalletBalanceDescription), "เจม")
        assertContains(thai.getValue(TextKey.RewardGemsDescription), "เจม")
        assertContains(thai.getValue(TextKey.WalletReceivedGems), "เจม")
        assertContains(thai.getValue(TextKey.ClanRewardSource), "แคลน")
    }

    private fun assertLocalized(vararg keys: TextKey) {
        val english = allLocalizationCatalogs.getValue(AppLanguage.ENGLISH).texts
        allLocalizationCatalogs.filterKeys {
            it != AppLanguage.ENGLISH && it != AppLanguage.VIETNAMESE
        }.forEach { (language, catalog) ->
            keys.forEach { key ->
                assertNotEquals(english.getValue(key), catalog.texts.getValue(key), "${language.code}/$key")
            }
        }
    }
}
