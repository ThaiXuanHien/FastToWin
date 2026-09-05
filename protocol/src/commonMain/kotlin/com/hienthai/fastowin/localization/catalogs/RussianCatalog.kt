package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val russianCatalog = LocalizationCatalog(
    language = AppLanguage.RUSSIAN,
    texts = mapOf(
        TextKey.Back to "Назад",
        TextKey.Cancel to "Отмена",
        TextKey.Confirm to "Подтвердить",
        TextKey.Retry to "Повторить",
        TextKey.Close to "Закрыть",
        TextKey.Save to "Сохранить",
        TextKey.Delete to "Удалить",
        TextKey.Loading to "Загрузка…",
        TextKey.UnknownError to "Произошла ошибка. Попробуйте ещё раз.",
        TextKey.WelcomePlayer to "Привет, {player}!"
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} игрок",
            PluralCategory.FEW to "{count} игрока",
            PluralCategory.MANY to "{count} игроков",
            PluralCategory.OTHER to "{count} игрока"
        )
    )
)
