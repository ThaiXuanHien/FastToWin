package com.hienthai.fastowin.localization

import android.os.LocaleList

actual fun platformLanguageTags(): List<String> {
    val locales = LocaleList.getDefault()
    return List(locales.size()) { index -> locales[index].toLanguageTag() }
}

// Do not mutate the device locale or recreate the Activity when the app language changes.
actual fun applyPlatformLanguageTag(languageTag: String) = Unit
