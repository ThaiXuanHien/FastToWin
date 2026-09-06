package com.hienthai.fastowin.localization

enum class QuantityKey { Players, Matches, Seasons }

enum class PluralCategory { ONE, FEW, MANY, OTHER }

/** Cardinal categories for non-negative integer game counts. */
internal fun pluralCategory(language: AppLanguage, count: Int): PluralCategory = when (language) {
    AppLanguage.RUSSIAN -> when {
        count % 10 == 1 && count % 100 != 11 -> PluralCategory.ONE
        count % 10 in 2..4 && count % 100 !in 12..14 -> PluralCategory.FEW
        count % 10 == 0 || count % 10 in 5..9 || count % 100 in 11..14 -> PluralCategory.MANY
        else -> PluralCategory.OTHER
    }
    AppLanguage.ENGLISH, AppLanguage.SPANISH, AppLanguage.GERMAN ->
        if (count == 1) PluralCategory.ONE else PluralCategory.OTHER
    AppLanguage.BRAZILIAN_PORTUGUESE, AppLanguage.FRENCH ->
        if (count == 0 || count == 1) PluralCategory.ONE else PluralCategory.OTHER
    else -> PluralCategory.OTHER
}
