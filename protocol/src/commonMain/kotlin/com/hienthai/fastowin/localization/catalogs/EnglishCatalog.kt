package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.economyProgressionTexts

internal val englishCatalog = LocalizationCatalog(
    language = AppLanguage.ENGLISH,
    texts = mapOf(
        TextKey.ClanLogoLion to "Lion",
        TextKey.ClanLogoPhoenix to "Phoenix",
        TextKey.ClanLogoKirin to "Kirin",
        TextKey.ClanLogoUnlockAtLevel to "Unlocks at clan level {level}",
        TextKey.ClanLogoCurrent to "In use",
        TextKey.ClanLogoUnlocked to "Unlocked",
        TextKey.ClanLogoUnlockDescription to "Clan level {current} · Choose an unlocked emblem. Your current emblem is kept.",
        TextKey.ClanLogoForClan to "Clan emblem: {clan}",
        TextKey.ServerClanLogoLocked to "Reach clan level {level} to use this emblem.",
        TextKey.ServerInvalidClanLogo to "This clan emblem is not available.",
        TextKey.ClanMemberProfileTitle to "Clan member",
        TextKey.ViewClanMemberProfile to "View {player}'s profile",
        TextKey.ClanMemberRole to "Clan role",
        TextKey.ClanQuestContribution to "Quest contribution",
        TextKey.ClanDonatedGold to "Gold donated",
        TextKey.ClanDonatedGems to "Gems donated",
        TextKey.SettingsTitle to "Settings",
        TextKey.LanguageTitle to "Language",
        TextKey.ChooseLanguageTitle to "Choose language",
        TextKey.SystemLanguage to "System default",
        TextKey.ResolvedSystemLanguage to "System default · {language}",
        TextKey.AppearanceTitle to "Appearance",
        TextKey.AppearanceSubtitle to "Preferences are saved on this device.",
        TextKey.Back to "Back",
        TextKey.Cancel to "Cancel",
        TextKey.Confirm to "Confirm",
        TextKey.Retry to "Retry",
        TextKey.Close to "Close",
        TextKey.Save to "Save",
        TextKey.Delete to "Delete",
        TextKey.Loading to "Loading…",
        TextKey.UnknownError to "Something went wrong. Please try again.",
        TextKey.WelcomePlayer to "Hello {player}!"
) + englishShellAuthTexts + englishGameplayTexts + englishProfileTexts + englishSocialShopTexts + englishProtocolMessageTexts + englishDeliveryTexts + residualTexts(AppLanguage.ENGLISH) + economyProgressionTexts.getValue(AppLanguage.ENGLISH) + playQuotaTexts.getValue(AppLanguage.ENGLISH),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} player",
            PluralCategory.OTHER to "{count} players"
        )
    ) + englishProfileQuantities + englishSocialShopQuantities
)
