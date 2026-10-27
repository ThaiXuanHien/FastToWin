package com.hienthai.fastowin.localization

enum class AppLanguage(val code: String, val languageTag: String, val nativeName: String) {
    VIETNAMESE("vi", "vi", "Tiếng Việt"),
    ENGLISH("en", "en", "English");

    /** Deliberately English secondary labels help users recover from an unfamiliar language. */
    val englishName: String get() = when (this) {
        VIETNAMESE -> "Vietnamese"
        ENGLISH -> "English"
    }
}

/** Languages shipped and exposed by FastToWin. */
val selectableAppLanguages: List<AppLanguage> = AppLanguage.entries

/** Normalizes the persisted selector without treating an unsupported legacy code as System. */
fun normalizeAppLanguageSelection(savedCode: String): String {
    val normalizedCode = savedCode.trim()
    if (normalizedCode.equals("system", ignoreCase = true)) return "system"
    return selectableAppLanguages.firstOrNull {
        it.code.equals(normalizedCode, ignoreCase = true)
    }?.code ?: AppLanguage.ENGLISH.code
}

/** Unsupported saved/system languages deliberately migrate to English. */
fun resolveAppLanguage(savedCode: String, systemTags: List<String>): AppLanguage {
    val selection = normalizeAppLanguageSelection(savedCode)
    selectableAppLanguages.firstOrNull { it.code == selection }?.let { return it }
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
