package com.hienthai.fastowin.localization

enum class AppLanguage(val code: String, val languageTag: String, val nativeName: String) {
    VIETNAMESE("vi", "vi", "Tiếng Việt"),
    ENGLISH("en", "en", "English"),
    SIMPLIFIED_CHINESE("zh-Hans", "zh-Hans", "中文简体"),
    JAPANESE("ja", "ja", "日本語"),
    KOREAN("ko", "ko", "한국어"),
    SPANISH("es", "es", "Español"),
    BRAZILIAN_PORTUGUESE("pt-BR", "pt-BR", "Português (Brasil)"),
    FRENCH("fr", "fr", "Français"),
    GERMAN("de", "de", "Deutsch"),
    INDONESIAN("id", "id", "Bahasa Indonesia"),
    THAI("th", "th", "ไทย"),
    RUSSIAN("ru", "ru", "Русский");

    /** Deliberately English secondary labels help users recover from an unfamiliar language. */
    val englishName: String get() = when (this) {
        VIETNAMESE -> "Vietnamese"
        ENGLISH -> "English"
        SIMPLIFIED_CHINESE -> "Simplified Chinese"
        JAPANESE -> "Japanese"
        KOREAN -> "Korean"
        SPANISH -> "Spanish"
        BRAZILIAN_PORTUGUESE -> "Portuguese (Brazil)"
        FRENCH -> "French"
        GERMAN -> "German"
        INDONESIAN -> "Indonesian"
        THAI -> "Thai"
        RUSSIAN -> "Russian"
    }
}

/** Languages currently exposed by FastToWin. Legacy catalogs remain decodable for compatibility. */
val selectableAppLanguages: List<AppLanguage> = listOf(AppLanguage.VIETNAMESE, AppLanguage.ENGLISH)

/** Unsupported saved/system languages deliberately migrate to English. */
fun resolveAppLanguage(savedCode: String, systemTags: List<String>): AppLanguage {
    val normalizedCode = savedCode.trim()
    selectableAppLanguages.firstOrNull {
        it.code.equals(normalizedCode, ignoreCase = true)
    }?.let { return it }
    if (!normalizedCode.equals("system", ignoreCase = true)) return AppLanguage.ENGLISH
    return systemTags.firstNotNullOfOrNull(::languageForTag) ?: AppLanguage.ENGLISH
}

private fun languageForTag(tag: String): AppLanguage? {
    val parts = tag.trim().replace('_', '-').lowercase().split('-')
    val base = parts.firstOrNull() ?: return null
    // Only read the core tag, not region-looking values inside extensions/private use.
    return when (base) {
        "vi" -> AppLanguage.VIETNAMESE
        "en" -> AppLanguage.ENGLISH
        else -> null
    }
}
