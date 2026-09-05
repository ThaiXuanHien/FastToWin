package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val brazilianPortugueseCatalog = LocalizationCatalog(
    language = AppLanguage.BRAZILIAN_PORTUGUESE,
    texts = mapOf(
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
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} jogador",
            PluralCategory.OTHER to "{count} jogadores"
        )
    )
)
