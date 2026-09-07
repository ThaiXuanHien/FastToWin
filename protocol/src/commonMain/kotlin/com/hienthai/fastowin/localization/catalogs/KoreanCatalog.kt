package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val koreanCatalog = LocalizationCatalog(
    language = AppLanguage.KOREAN,
    texts = mapOf(
        TextKey.SettingsTitle to "설정",
        TextKey.LanguageTitle to "언어",
        TextKey.ChooseLanguageTitle to "언어 선택",
        TextKey.SystemLanguage to "시스템 설정 사용",
        TextKey.ResolvedSystemLanguage to "시스템 설정 · {language}",
        TextKey.AppearanceTitle to "화면 설정",
        TextKey.AppearanceSubtitle to "설정은 이 기기에 저장됩니다.",
        TextKey.Back to "뒤로",
        TextKey.Cancel to "취소",
        TextKey.Confirm to "확인",
        TextKey.Retry to "다시 시도",
        TextKey.Close to "닫기",
        TextKey.Save to "저장",
        TextKey.Delete to "삭제",
        TextKey.Loading to "불러오는 중…",
        TextKey.UnknownError to "오류가 발생했습니다. 다시 시도해 주세요.",
        TextKey.WelcomePlayer to "{player}님, 안녕하세요!"
) + koreanShellAuthTexts + koreanGameplayTexts + koreanProfileTexts + koreanSocialShopTexts + koreanProtocolMessageTexts + koreanDeliveryTexts + residualTexts(AppLanguage.KOREAN),
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.OTHER to "플레이어 {count}명"
        )
    ) + koreanProfileQuantities + koreanSocialShopQuantities
)
