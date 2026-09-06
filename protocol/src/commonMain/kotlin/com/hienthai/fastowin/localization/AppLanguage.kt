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

/** Unknown saved codes follow system preferences; unsupported devices use English. */
fun resolveAppLanguage(savedCode: String, systemTags: List<String>): AppLanguage =
    AppLanguage.entries.firstOrNull { it.code.equals(savedCode.trim(), ignoreCase = true) }
        ?: systemTags.firstNotNullOfOrNull(::languageForTag)
        ?: AppLanguage.ENGLISH

private fun languageForTag(tag: String): AppLanguage? {
    val parts = tag.trim().replace('_', '-').lowercase().split('-')
    val base = parts.firstOrNull() ?: return null
    // Only read the core tag, not region-looking values inside extensions/private use.
    val subtags = parts.drop(1).takeWhile { it.length > 1 }
    val script = subtags.firstOrNull { it.length == 4 }
    val region = subtags.firstOrNull { it.length == 2 || it.length == 3 && it.all(Char::isDigit) }
    return when (base) {
        "zh" -> if (script == "hans" || script == null && region in setOf("cn", "sg")) {
            AppLanguage.SIMPLIFIED_CHINESE
        } else null
        "pt" -> AppLanguage.BRAZILIAN_PORTUGUESE.takeIf { region == "br" }
        else -> AppLanguage.entries.firstOrNull { it.code.lowercase() == base }
    }
}
