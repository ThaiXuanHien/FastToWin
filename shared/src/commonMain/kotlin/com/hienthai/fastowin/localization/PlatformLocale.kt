package com.hienthai.fastowin.localization

/** Ordered device language preferences, independent of the in-app selection. */
expect fun platformLanguageTags(): List<String>

/** Updates platform accessibility metadata; UI strings come from the app catalog. */
expect fun applyPlatformLanguageTag(languageTag: String)

fun resolveSavedLanguage(code: String, systemTags: List<String>): AppLanguage =
    resolveAppLanguage(code, systemTags)
