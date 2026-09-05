package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val simplifiedChineseCatalog = LocalizationCatalog(
    language = AppLanguage.SIMPLIFIED_CHINESE,
    texts = mapOf(
        TextKey.Back to "返回",
        TextKey.Cancel to "取消",
        TextKey.Confirm to "确认",
        TextKey.Retry to "重试",
        TextKey.Close to "关闭",
        TextKey.Save to "保存",
        TextKey.Delete to "删除",
        TextKey.Loading to "加载中…",
        TextKey.UnknownError to "出了点问题，请重试。",
        TextKey.WelcomePlayer to "你好，{player}！"
    ),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.OTHER to "{count} 位玩家"
        )
    )
)
