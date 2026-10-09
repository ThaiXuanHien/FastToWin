package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.AppThemeMode
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.CompensationMailboxContext
import com.hienthai.fastowin.protocol.GiftState
import com.hienthai.fastowin.protocol.MailboxGift
import com.hienthai.fastowin.state.GiftMailboxState
import com.hienthai.fastowin.ui.screens.GiftMailboxScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

// Actual production screen: no recreated card, claim path, reward math, or injected UI theme.
private const val compensationNow = 1791432000000L
private val compensationContext = CompensationMailboxContext(
    "00000000-0000-0000-0000-000000000001", "INC-DEMO", 2,
    1791424800000, 1791428400000,
    "Bạn có tên trong danh sách đã xác minh.", "You are on the verified list."
)
private val compensationGift = MailboxGift(
    "00000000-0000-0000-0000-000000000002",
    "Đền bù sự cố", "Incident compensation",
    "Thông tin sự cố đã xác minh.\nUTC: [2026-10-08T02:00:00Z, 2026-10-08T03:00:00Z)\nBạn có tên trong danh sách đã xác minh.",
    "Verified incident information.\nUTC: [2026-10-08T02:00:00Z, 2026-10-08T03:00:00Z)\nYou are on the verified list.",
    Int.MAX_VALUE, Int.MAX_VALUE, compensationNow, 1794024000000,
    GiftState.AVAILABLE, compensation = compensationContext
)
private val legacyGift = compensationGift.copy(
    titleVi = "Quà chào mừng", titleEn = "Welcome gift",
    bodyVi = "Chúc bạn có những trận đấu thú vị.", bodyEn = "Enjoy your next matches.",
    gold = 100, xp = 0, compensation = null
)
private val longGift = compensationGift.copy(
    bodyVi = "Sự cố kết nối đã ảnh hưởng đến việc hoàn thành trận đấu. Đội ngũ đã xác minh phạm vi ảnh hưởng và khôi phục dịch vụ. " +
        "Nội dung này được lưu khi đợt đền bù được công bố để bạn có thể xem lại đầy đủ thông tin.\n\n" +
        "UTC: [2026-10-08T02:00:00Z, 2026-10-08T03:00:00Z)\n" +
        "Bạn có tên trong danh sách người chơi được xác minh cho sự cố này. Phần thưởng của đợt bổ sung được hiển thị bên dưới.",
    bodyEn = "A connection incident affected match completion. The team verified the affected scope and restored service. " +
        "This information was frozen when the compensation round was published so you can review the complete explanation.\n\n" +
        "UTC: [2026-10-08T02:00:00Z, 2026-10-08T03:00:00Z)\n" +
        "You are on the verified list of players affected by this incident. This supplemental round's reward is shown below.",
    compensation = compensationContext.copy(incidentKey = "INC-" + "VERIFIED_OUTAGE_".repeat(8).take(124), roundNumber = Int.MAX_VALUE)
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "phone320", widthDp = 320, heightDp = 568, showSystemUi = false)
@Preview(name = "phone430", widthDp = 430, heightDp = 932, showSystemUi = false)
@Preview(name = "desktop1024", widthDp = 1024, heightDp = 768, showSystemUi = false)
@Preview(name = "phone320_large", widthDp = 320, heightDp = 568, fontScale = 1.6f, showSystemUi = false)
@Preview(name = "phone430_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Preview(name = "desktop1024_large", widthDp = 1024, heightDp = 768, fontScale = 1.6f, showSystemUi = false)
annotation class CompensationMailboxDevices

@Composable
private fun CompensationMailboxPreview(
    language: AppLanguage,
    theme: AppThemeMode,
    gift: MailboxGift = compensationGift
) = ProvideLocalization(language) {
    FastToWinTheme(preferences = AppPreferences(themeMode = theme, visualEffectsEnabled = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            GiftMailboxScreen(
                state = GiftMailboxState(isOpen = true, items = listOf(gift), total = 1),
                isGuest = false, onBack = {}, onRefresh = {}, onLoadMore = {}, onClaim = {},
                onClaimAll = {}, onUpgradeGuest = {}, nowMillis = compensationNow
            )
        }
    }
}

@PreviewTest
@CompensationMailboxDevices
@Composable
fun compensationAvailableViDark() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.DARK)

@PreviewTest
@CompensationMailboxDevices
@Composable
fun compensationAvailableViLight() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.LIGHT)

@PreviewTest
@CompensationMailboxDevices
@Composable
fun compensationAvailableEnDark() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.DARK)

@PreviewTest
@CompensationMailboxDevices
@Composable
fun compensationAvailableEnLight() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.LIGHT)

@PreviewTest
@Preview(name = "claimed_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationClaimedViDark() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.DARK, compensationGift.copy(state = GiftState.CLAIMED, gold = 0, xp = Int.MAX_VALUE))

@PreviewTest
@Preview(name = "claimed_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationClaimedViLight() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.LIGHT, compensationGift.copy(state = GiftState.CLAIMED, gold = 0, xp = Int.MAX_VALUE))

@PreviewTest
@Preview(name = "claimed_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationClaimedEnDark() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.DARK, compensationGift.copy(state = GiftState.CLAIMED, gold = 0, xp = Int.MAX_VALUE))

@PreviewTest
@Preview(name = "claimed_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationClaimedEnLight() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.LIGHT, compensationGift.copy(state = GiftState.CLAIMED, gold = 0, xp = Int.MAX_VALUE))

@PreviewTest
@Preview(name = "expired_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationExpiredViDark() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.DARK, compensationGift.copy(state = GiftState.EXPIRED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "expired_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationExpiredViLight() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.LIGHT, compensationGift.copy(state = GiftState.EXPIRED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "expired_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationExpiredEnDark() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.DARK, compensationGift.copy(state = GiftState.EXPIRED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "expired_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationExpiredEnLight() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.LIGHT, compensationGift.copy(state = GiftState.EXPIRED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "cancelled_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationCancelledViDark() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.DARK, compensationGift.copy(state = GiftState.CANCELLED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "cancelled_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationCancelledViLight() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.LIGHT, compensationGift.copy(state = GiftState.CANCELLED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "cancelled_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationCancelledEnDark() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.DARK, compensationGift.copy(state = GiftState.CANCELLED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "cancelled_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationCancelledEnLight() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.LIGHT, compensationGift.copy(state = GiftState.CANCELLED, gold = Int.MAX_VALUE, xp = 0))

@PreviewTest
@Preview(name = "legacy_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationLegacyViDark() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.DARK, legacyGift)

@PreviewTest
@Preview(name = "legacy_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationLegacyViLight() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.LIGHT, legacyGift)

@PreviewTest
@Preview(name = "legacy_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationLegacyEnDark() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.DARK, legacyGift)

@PreviewTest
@Preview(name = "legacy_large", widthDp = 430, heightDp = 932, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationLegacyEnLight() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.LIGHT, legacyGift)

// Expanded viewport companion to phone captures: inspect the entire long card and its CTA.
@PreviewTest
@Preview(name = "long_phone_full_card", widthDp = 320, heightDp = 2400, fontScale = 1.6f, showSystemUi = false)
@Preview(name = "long_desktop", widthDp = 1024, heightDp = 1400, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationLongViDark() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.DARK, longGift)

// Expanded viewport companion to phone captures: inspect the entire long card and its CTA.
@PreviewTest
@Preview(name = "long_phone_full_card", widthDp = 320, heightDp = 2400, fontScale = 1.6f, showSystemUi = false)
@Preview(name = "long_desktop", widthDp = 1024, heightDp = 1400, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationLongEnLight() = CompensationMailboxPreview(AppLanguage.ENGLISH, AppThemeMode.LIGHT, longGift)

@PreviewTest
@Preview(name = "phone375", widthDp = 375, heightDp = 812, fontScale = 1.6f, showSystemUi = false)
@Preview(name = "landscape", widthDp = 844, heightDp = 390, fontScale = 1.6f, showSystemUi = false)
@Composable
fun compensationOrientation() = CompensationMailboxPreview(AppLanguage.VIETNAMESE, AppThemeMode.LIGHT)
