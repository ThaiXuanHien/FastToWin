package com.hienthai.fastowin.localization

import com.hienthai.fastowin.localization.catalogs.brazilianPortugueseCatalog
import com.hienthai.fastowin.localization.catalogs.englishCatalog
import com.hienthai.fastowin.localization.catalogs.frenchCatalog
import com.hienthai.fastowin.localization.catalogs.germanCatalog
import com.hienthai.fastowin.localization.catalogs.indonesianCatalog
import com.hienthai.fastowin.localization.catalogs.japaneseCatalog
import com.hienthai.fastowin.localization.catalogs.koreanCatalog
import com.hienthai.fastowin.localization.catalogs.russianCatalog
import com.hienthai.fastowin.localization.catalogs.simplifiedChineseCatalog
import com.hienthai.fastowin.localization.catalogs.spanishCatalog
import com.hienthai.fastowin.localization.catalogs.thaiCatalog
import com.hienthai.fastowin.localization.catalogs.vietnameseCatalog

data class LocalizationCatalog(
    val language: AppLanguage,
    val texts: Map<TextKey, String>,
    val quantities: Map<QuantityKey, Map<PluralCategory, String>>
)

val allLocalizationCatalogs: Map<AppLanguage, LocalizationCatalog> = listOf(
    englishCatalog, vietnameseCatalog, simplifiedChineseCatalog, japaneseCatalog,
    koreanCatalog, spanishCatalog, brazilianPortugueseCatalog, frenchCatalog,
    germanCatalog, indonesianCatalog, thaiCatalog, russianCatalog
).associateBy { it.language }
