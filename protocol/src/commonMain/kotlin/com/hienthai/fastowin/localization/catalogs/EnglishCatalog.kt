package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val englishCatalog = LocalizationCatalog(
    language = AppLanguage.ENGLISH,
    texts = mapOf(
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
) + englishShellAuthTexts + englishGameplayTexts + englishProfileTexts,
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} player",
            PluralCategory.OTHER to "{count} players"
        )
    ) + englishProfileQuantities
)
