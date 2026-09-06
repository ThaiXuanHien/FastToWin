package com.hienthai.fastowin.localization.catalogs

import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.LocalizationCatalog
import com.hienthai.fastowin.localization.PluralCategory
import com.hienthai.fastowin.localization.QuantityKey
import com.hienthai.fastowin.localization.TextKey

internal val thaiCatalog = LocalizationCatalog(
    language = AppLanguage.THAI,
    texts = mapOf(
        TextKey.SettingsTitle to "การตั้งค่า",
        TextKey.LanguageTitle to "ภาษา",
        TextKey.ChooseLanguageTitle to "เลือกภาษา",
        TextKey.SystemLanguage to "ตามระบบ",
        TextKey.ResolvedSystemLanguage to "ตามระบบ · {language}",
        TextKey.AppearanceTitle to "การแสดงผล",
        TextKey.AppearanceSubtitle to "การตั้งค่าจะบันทึกไว้ในอุปกรณ์นี้",
        TextKey.Back to "กลับ",
        TextKey.Cancel to "ยกเลิก",
        TextKey.Confirm to "ยืนยัน",
        TextKey.Retry to "ลองอีกครั้ง",
        TextKey.Close to "ปิด",
        TextKey.Save to "บันทึก",
        TextKey.Delete to "ลบ",
        TextKey.Loading to "กำลังโหลด…",
        TextKey.UnknownError to "เกิดข้อผิดพลาด โปรดลองอีกครั้ง",
        TextKey.WelcomePlayer to "สวัสดี {player}!"
) + thaiShellAuthTexts + thaiGameplayTexts + thaiProfileTexts,
    quantities = mapOf(
        QuantityKey.Players to mapOf(
            PluralCategory.OTHER to "ผู้เล่น {count} คน"
        )
    ) + thaiProfileQuantities
)
