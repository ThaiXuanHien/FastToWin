package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val germanCatalog = LocalizationCatalog(
    language = AppLanguage.GERMAN,
    texts = mapOf(
        TextKey.Back to "Zurück",
        TextKey.Cancel to "Abbrechen",
        TextKey.Confirm to "Bestätigen",
        TextKey.Retry to "Erneut versuchen",
        TextKey.Close to "Schließen",
        TextKey.Save to "Speichern",
        TextKey.Delete to "Löschen",
        TextKey.Loading to "Wird geladen…",
        TextKey.UnknownError to "Ein Fehler ist aufgetreten. Bitte versuche es erneut.",
        TextKey.WelcomePlayer to "Hallo {player}!"
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} Spieler",
            PluralCategory.OTHER to "{count} Spieler"
        )
    )
)
