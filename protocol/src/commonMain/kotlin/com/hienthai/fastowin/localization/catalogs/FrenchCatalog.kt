package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.economyProgressionTexts

internal val frenchCatalog = LocalizationCatalog(
    language = AppLanguage.FRENCH,
    texts = mapOf(
        TextKey.SettingsTitle to "Paramètres",
        TextKey.LanguageTitle to "Langue",
        TextKey.ChooseLanguageTitle to "Choisir une langue",
        TextKey.SystemLanguage to "Langue du système",
        TextKey.ResolvedSystemLanguage to "Langue du système · {language}",
        TextKey.AppearanceTitle to "Apparence",
        TextKey.AppearanceSubtitle to "Les préférences sont enregistrées sur cet appareil.",
        TextKey.Back to "Retour",
        TextKey.Cancel to "Annuler",
        TextKey.Confirm to "Confirmer",
        TextKey.Retry to "Réessayer",
        TextKey.Close to "Fermer",
        TextKey.Save to "Enregistrer",
        TextKey.Delete to "Supprimer",
        TextKey.Loading to "Chargement…",
        TextKey.UnknownError to "Une erreur est survenue. Réessayez.",
        TextKey.WelcomePlayer to "Bonjour {player} !"
) + frenchShellAuthTexts + frenchGameplayTexts + frenchProfileTexts + frenchSocialShopTexts + frenchProtocolMessageTexts + frenchDeliveryTexts + residualTexts(AppLanguage.FRENCH) + economyProgressionTexts.getValue(AppLanguage.FRENCH) + playQuotaTexts.getValue(AppLanguage.FRENCH),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} joueur",
            PluralCategory.OTHER to "{count} joueurs"
        )
    ) + frenchProfileQuantities + frenchSocialShopQuantities
)
