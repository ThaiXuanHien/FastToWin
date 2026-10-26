package com.hienthai.fastowin.localization

enum class QuantityKey { Players, Matches, Seasons, Warriors, Clans, Members }

enum class PluralCategory { ONE, OTHER }

/** Cardinal categories for non-negative integer game counts. */
internal fun pluralCategory(language: AppLanguage, count: Int): PluralCategory = when (language) {
    AppLanguage.ENGLISH ->
        if (count == 1) PluralCategory.ONE else PluralCategory.OTHER
    AppLanguage.VIETNAMESE -> PluralCategory.OTHER
}
