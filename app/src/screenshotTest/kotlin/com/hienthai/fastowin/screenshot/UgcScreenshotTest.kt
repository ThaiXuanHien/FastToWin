package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.*
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Composable private fun UgcFrame(english: Boolean = false, content: @Composable () -> Unit) {
    ProvideLocalization(if (english) AppLanguage.ENGLISH else AppLanguage.VIETNAMESE) {
        FastToWinTheme(preferences = AppPreferences(themeMode = AppThemeMode.DARK, visualEffectsEnabled = false)) {
            Surface(Modifier.fillMaxSize()) { content() }
        }
    }
}
@Composable private fun UgcReport(error: Boolean = false, receipt: Boolean = false) {
    UgcReportDialog(UgcReportDraft(UgcTargetType.PLAYER_PROFILE,
        "12345678-1234-1234-1234-123456789abc", "<b>Player nickname</b>",
        reason = UgcReportReason.HARASSMENT, note = "Additional details for the review team.",
        request = if (error) ContentReportRequest(UgcTargetType.PLAYER_PROFILE,
            "12345678-1234-1234-1234-123456789abc", UgcReportReason.HARASSMENT, requestId = "stable-retry") else null,
        error = if (error) "REPORT_RATE_LIMITED" else null,
        receipt = if (receipt) ContentReportReceipt("12345678-1234-1234-1234-123456789abc") else null),
        { _, _, _ -> }, {}, {}, {})
}
@Composable private fun UgcConsent(pending: Boolean = false, error: Boolean = false) {
    ContentTermsDialog(UgcState(terms = ContentTermsStatus("2026-10-20", null, true),
        showTerms = true, termsPending = pending, termsError = if (error) "TERMS_UNCONFIRMED" else null), {}, {}, {}, {})
}
@PreviewTest @Preview(name = "report_phone_vi", widthDp = 375, heightDp = 812) @Composable
fun ugcReportPhone() = UgcFrame { UgcReport() }
@PreviewTest @Preview(name = "report_desktop_en", widthDp = 1024, heightDp = 768) @Composable
fun ugcReportDesktop() = UgcFrame(true) { UgcReport() }
@PreviewTest @Preview(name = "report_retry_small_large_text", widthDp = 320, heightDp = 932, fontScale = 1.6f) @Composable
fun ugcReportRetry() = UgcFrame { UgcReport(error = true) }
@PreviewTest @Preview(name = "report_receipt_en", widthDp = 375, heightDp = 812) @Composable
fun ugcReportReceipt() = UgcFrame(true) { UgcReport(receipt = true) }
@PreviewTest @Preview(name = "consent_phone_vi", widthDp = 375, heightDp = 812) @Composable
fun ugcConsentPhone() = UgcFrame { UgcConsent() }
@PreviewTest @Preview(name = "consent_desktop_en", widthDp = 1024, heightDp = 768) @Composable
fun ugcConsentDesktop() = UgcFrame(true) { UgcConsent() }
@PreviewTest @Preview(name = "consent_landscape_large_text", widthDp = 844, heightDp = 390, fontScale = 1.6f) @Composable
fun ugcConsentLandscape() = UgcFrame(true) { UgcConsent() }
@PreviewTest @Preview(name = "consent_pending", widthDp = 375, heightDp = 812) @Composable
fun ugcConsentPending() = UgcFrame { UgcConsent(pending = true) }
@PreviewTest @Preview(name = "consent_error", widthDp = 375, heightDp = 812) @Composable
fun ugcConsentError() = UgcFrame { UgcConsent(error = true) }
