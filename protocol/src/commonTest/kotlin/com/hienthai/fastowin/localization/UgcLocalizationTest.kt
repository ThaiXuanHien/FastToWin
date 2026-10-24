package com.hienthai.fastowin.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UgcLocalizationTest {
    @Test fun `report action identifies untrusted content without interpreting markup`() {
        for (language in listOf(AppLanguage.VIETNAMESE, AppLanguage.ENGLISH)) {
            val rendered = LocalizationService(language).text(TextKey.UgcReportNamed,
                mapOf("content" to "<script>{player}</script>"))
            assertTrue(rendered.contains("<script>{player}</script>"))
        }
    }
    @Test fun `consent and report failures have explicit Vietnamese and English copy`() {
        val keys = listOf(TextKey.UgcTermsTitle, TextKey.UgcTermsAgreement, TextKey.UgcReportTitle,
            TextKey.UgcReportReceived, TextKey.UgcReportFailed, TextKey.UgcTermsFailed)
        for (language in listOf(AppLanguage.VIETNAMESE, AppLanguage.ENGLISH)) {
            val catalog = allLocalizationCatalogs.getValue(language)
            keys.forEach { key -> assertTrue(catalog.texts.getValue(key).isNotBlank()) }
        }
    }
}
