package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.economyProgressionTexts

internal val brazilianPortugueseCatalog = LocalizationCatalog(
    language = AppLanguage.BRAZILIAN_PORTUGUESE,
    texts = mapOf(
        TextKey.SettingsTitle to "Configurações",
        TextKey.LanguageTitle to "Idioma",
        TextKey.ChooseLanguageTitle to "Escolher idioma",
        TextKey.SystemLanguage to "Usar idioma do sistema",
        TextKey.ResolvedSystemLanguage to "Idioma do sistema · {language}",
        TextKey.AppearanceTitle to "Aparência",
        TextKey.AppearanceSubtitle to "As preferências são salvas neste dispositivo.",
        TextKey.Back to "Voltar",
        TextKey.Cancel to "Cancelar",
        TextKey.Confirm to "Confirmar",
        TextKey.Retry to "Tentar novamente",
        TextKey.Close to "Fechar",
        TextKey.Save to "Salvar",
        TextKey.Delete to "Excluir",
        TextKey.Loading to "Carregando…",
        TextKey.UnknownError to "Algo deu errado. Tente novamente.",
        TextKey.WelcomePlayer to "Olá, {player}!"
) + brazilianPortugueseShellAuthTexts + brazilianPortugueseGameplayTexts + brazilianPortugueseProfileTexts + brazilianPortugueseSocialShopTexts + brazilianPortugueseProtocolMessageTexts + brazilianPortugueseDeliveryTexts + residualTexts(AppLanguage.BRAZILIAN_PORTUGUESE) + economyProgressionTexts.getValue(AppLanguage.BRAZILIAN_PORTUGUESE) + playQuotaTexts.getValue(AppLanguage.BRAZILIAN_PORTUGUESE),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} jogador",
            PluralCategory.OTHER to "{count} jogadores"
        )
    ) + brazilianPortugueseProfileQuantities + brazilianPortugueseSocialShopQuantities
)
