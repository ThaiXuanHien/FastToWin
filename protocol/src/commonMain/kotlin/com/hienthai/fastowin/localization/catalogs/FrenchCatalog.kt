package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val frenchCatalog = LocalizationCatalog(
    language = AppLanguage.FRENCH,
    texts = mapOf(
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
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} joueur",
            PluralCategory.OTHER to "{count} joueurs"
        )
    )
)
