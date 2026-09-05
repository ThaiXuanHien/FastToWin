package com.hienthai.fastowin.localization

/** Immutable renderer; callers replace this value when their language changes. */
class LocalizationService(
    val language: AppLanguage,
    catalogs: Map<AppLanguage, LocalizationCatalog> = allLocalizationCatalogs
) {
    private val english = catalogs.getValue(AppLanguage.ENGLISH)
    private val selected = catalogs[language] ?: english

    fun text(key: TextKey, arguments: Map<String, Any?> = emptyMap()): String {
        val template = selected.texts[key]?.takeIf(String::isNotBlank)
            ?: english.texts.getValue(key)
        return render(template, arguments)
    }

    fun quantity(key: QuantityKey, count: Int, arguments: Map<String, Any?> = emptyMap()): String {
        require(count >= 0) { "Quantity must be non-negative." }
        val forms = selected.quantities[key]
        val template = forms?.get(pluralCategory(selected.language, count))?.takeIf(String::isNotBlank)
            ?: forms?.get(PluralCategory.OTHER)?.takeIf(String::isNotBlank)
            ?: english.quantities.getValue(key).let { fallback ->
                fallback[pluralCategory(AppLanguage.ENGLISH, count)]?.takeIf(String::isNotBlank)
                    ?: fallback.getValue(PluralCategory.OTHER)
            }
        return render(template, arguments + ("count" to count))
    }

    private fun render(template: String, arguments: Map<String, Any?>): String =
        PLACEHOLDER.replace(template) { match ->
            // Single-pass replacement preserves braces and dollar signs in player content.
            requireNotNull(arguments[match.groupValues[1]]) { "Missing localization argument." }.toString()
        }

    private companion object {
        val PLACEHOLDER = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
    }
}
