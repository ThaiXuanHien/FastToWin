package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val japaneseCatalog = LocalizationCatalog(
    language = AppLanguage.JAPANESE,
    texts = mapOf(
        TextKey.SettingsTitle to "設定",
        TextKey.LanguageTitle to "言語",
        TextKey.ChooseLanguageTitle to "言語を選択",
        TextKey.SystemLanguage to "システム設定に従う",
        TextKey.ResolvedSystemLanguage to "システム設定 · {language}",
        TextKey.AppearanceTitle to "外観",
        TextKey.AppearanceSubtitle to "設定はこの端末に保存されます。",
        TextKey.Back to "戻る",
        TextKey.Cancel to "キャンセル",
        TextKey.Confirm to "確認",
        TextKey.Retry to "再試行",
        TextKey.Close to "閉じる",
        TextKey.Save to "保存",
        TextKey.Delete to "削除",
        TextKey.Loading to "読み込み中…",
        TextKey.UnknownError to "エラーが発生しました。もう一度お試しください。",
        TextKey.WelcomePlayer to "こんにちは、{player}！"
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.OTHER to "プレイヤー{count}人"
        )
    )
)
