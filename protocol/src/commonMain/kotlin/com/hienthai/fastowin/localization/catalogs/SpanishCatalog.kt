package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val spanishCatalog = LocalizationCatalog(
    language = AppLanguage.SPANISH,
    texts = mapOf(
        TextKey.SettingsTitle to "Ajustes",
        TextKey.LanguageTitle to "Idioma",
        TextKey.ChooseLanguageTitle to "Elegir idioma",
        TextKey.SystemLanguage to "Usar idioma del sistema",
        TextKey.ResolvedSystemLanguage to "Idioma del sistema · {language}",
        TextKey.AppearanceTitle to "Apariencia",
        TextKey.AppearanceSubtitle to "Las preferencias se guardan en este dispositivo.",
        TextKey.Back to "Volver",
        TextKey.Cancel to "Cancelar",
        TextKey.Confirm to "Confirmar",
        TextKey.Retry to "Reintentar",
        TextKey.Close to "Cerrar",
        TextKey.Save to "Guardar",
        TextKey.Delete to "Eliminar",
        TextKey.Loading to "Cargando…",
        TextKey.UnknownError to "Algo salió mal. Inténtalo de nuevo.",
        TextKey.WelcomePlayer to "¡Hola, {player}!"
) + spanishShellAuthTexts + spanishGameplayTexts + spanishProfileTexts + spanishSocialShopTexts + spanishProtocolMessageTexts,
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} jugador",
            PluralCategory.OTHER to "{count} jugadores"
        )
    ) + spanishProfileQuantities + spanishSocialShopQuantities
)
