package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val russianCatalog = LocalizationCatalog(
    language = AppLanguage.RUSSIAN,
    texts = mapOf(
        TextKey.SettingsTitle to "Настройки",
        TextKey.LanguageTitle to "Язык",
        TextKey.ChooseLanguageTitle to "Выбрать язык",
        TextKey.SystemLanguage to "Язык системы",
        TextKey.ResolvedSystemLanguage to "Язык системы · {language}",
        TextKey.AppearanceTitle to "Оформление",
        TextKey.AppearanceSubtitle to "Настройки сохраняются на этом устройстве.",
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
) + russianShellAuthTexts + russianGameplayTexts + russianProfileTexts + russianSocialShopTexts,
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.ONE to "{count} игрок",
            PluralCategory.FEW to "{count} игрока",
            PluralCategory.MANY to "{count} игроков",
            PluralCategory.OTHER to "{count} игрока"
        )
    ) + russianProfileQuantities + russianSocialShopQuantities
)
