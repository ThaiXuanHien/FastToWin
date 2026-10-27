package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.data.preferences.*
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.screens.*
import com.hienthai.fastowin.ui.theme.FastToWinTheme

private const val announcementId = "12345678-1234-1234-1234-123456789abc"
private fun announcementFixture(state: AdminAnnouncementState = AdminAnnouncementState.ACTIVE, long: Boolean = false, dismissed: Boolean = false): AnnouncementPlayerItem {
    val context = AdminAnnouncementContext(announcementId,
        if (long) "Thông báo chính thức về lịch thi đấu và những thay đổi cần biết trước khi tham gia đấu trường tuần này" else "Đấu trường cuối tuần",
        if (long) "Official announcement about the arena schedule and changes to review before joining the weekend competition" else "Weekend arena",
        if (long) "Nội dung thông báo dài, chỉ là văn bản. <b>Không chạy mã</b>. Xem chi tiết và quay lại khi đã đọc.\n".repeat(45).take(4000) else "Lịch thi đấu cuối tuần đã được công bố. Hãy xem sự kiện để biết điều kiện tham gia.",
        if (long) "Long announcement, shown as plain text. <b>No code runs</b>. Read the details and return when ready.\n".repeat(45).take(4000) else "The weekend schedule is published. View the event to see participation requirements.",
        AdminAnnouncementCategory.EVENT, 80, ArcadeScreenshotFixtures.NOW_MILLIS, ArcadeScreenshotFixtures.NOW_MILLIS + 3600000,
        state, AdminAnnouncementAction.REWARD_EVENT, announcementId, announcementId)
    return AnnouncementPlayerItem(NotificationSnapshot("admin-announcement:$announcementId", NotificationKind.COSMETIC, context.titleVi, context.bodyVi,
        ArcadeScreenshotFixtures.NOW_MILLIS, destination = NotificationDestination.PROFILE, announcement = context), context, dismissed, false)
}
@Composable private fun AnnouncementFrame(english: Boolean = false, content: @Composable () -> Unit) {
    ProvideLocalization(if (english) AppLanguage.ENGLISH else AppLanguage.VIETNAMESE) {
        FastToWinTheme(preferences = AppPreferences(themeMode = AppThemeMode.DARK, visualEffectsEnabled = false)) {
            Surface(Modifier.fillMaxSize()) { content() }
        }
    }
}
@Composable private fun Detail(state: AdminAnnouncementState = AdminAnnouncementState.ACTIVE, long: Boolean = false, footer: Boolean = false) {
    AnnouncementDetailScreen(AnnouncementState(selected = announcementFixture(state, long)), {}, {}, {}, {},
        scrollState = rememberScrollState(if (footer) 100000 else 0))
}
@Composable private fun Inbox(state: AdminAnnouncementState = AdminAnnouncementState.ACTIVE, empty: Boolean = false, long: Boolean = false, dismissed: Boolean = false) {
    val row = announcementFixture(state, long, dismissed)
    val en = LocalLocalization.current.language == AppLanguage.ENGLISH
    val admin = AppNotification(row.notification.id, AppNotificationKind.COSMETIC, if (en) row.announcement.titleEn else row.announcement.titleVi,
        if (en) row.announcement.bodyEn else row.announcement.bodyVi, row.notification.createdAtEpochMillis, destination = AppNotificationDestination.PROFILE, announcement = row.announcement)
    NotificationsScreen(if (empty) emptyList() else listOf(admin) + ArcadeScreenshotFixtures.notifications().take(1), {}, {}, {}, {}, {},
        nowMillis = ArcadeScreenshotFixtures.NOW_MILLIS, announcementState = AnnouncementState(items = if (empty) emptyList() else listOf(row), total = if (empty) "0" else "9223372036854775807", hasMore = !empty, hasLoadedInbox = true))
}
@Composable private fun Consent(error: String? = null, pending: Boolean = false) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AnnouncementConsentSettings(AnnouncementState(preferences = AnnouncementPreferenceView(1, false, false, ArcadeScreenshotFixtures.NOW_MILLIS),
            pending = pending, error = error, preferenceIntent = if (error == "NETWORK_ERROR") AnnouncementPreferenceRequest(1, true, false) else null), { _, _ -> }, {})
    }
}
@Composable private fun AnnouncementHome() {
    LobbyScreen(state = ArcadeScreenshotFixtures.homeState(), onModeSelected = {}, onStartMatchmaking = { _, _ -> }, onCancelMatchmaking = {},
        onOpenRoomBrowser = {}, onCreateRoom = { _, _, _, _ -> }, onJoinRoom = { _, _ -> }, onLeaveRoom = {}, onSetReady = {}, onKickOpponent = {},
        onRefreshRooms = {}, onOpenProfile = {}, onOpenLeaderboard = {}, onOpenFriends = {}, onOpenFriendProfile = {}, onBackToMode = {}, onLogout = {},
        isGuest = false, onUpgradeGuest = {}, onOpenNotifications = {}, onOpenClan = {}, onOpenPractice = {}, onOpenTournament = {}, onOpenShop = {},
        onShareRoom = { _, _ -> Result.success(Unit) }, onResolveRoomLink = {}, onClaimDailyCheckIn = {}, announcementState = AnnouncementState(banner = announcementFixture()))
}

@PreviewTest @Preview(name = "home_vi_dark_430", widthDp = 430, heightDp = 932) @Composable
fun announcementHomeViDark() = AnnouncementFrame { AnnouncementHome() }
@PreviewTest @Preview(name = "home_en_dark_1024", widthDp = 1024, heightDp = 768) @Composable
fun announcementHomeEnDark() = AnnouncementFrame(true) { AnnouncementHome() }
@PreviewTest @Preview(name = "banner_vi_dark_320_large", widthDp = 320, heightDp = 480, fontScale = 1.6f) @Composable
fun announcementBannerLarge() = AnnouncementFrame { Column(Modifier.padding(16.dp)) { AnnouncementBanner(announcementFixture(long = true), false, {}, {}) } }
@PreviewTest @Preview(name = "active_vi_dark_320", widthDp = 320, heightDp = 932) @Composable
fun announcementActiveViDark() = AnnouncementFrame { Detail() }
@PreviewTest @Preview(name = "active_en_dark_430", widthDp = 430, heightDp = 932) @Composable
fun announcementActiveEnDark() = AnnouncementFrame(true) { Detail() }
@PreviewTest @Preview(name = "expired_en_dark_430_large", widthDp = 430, heightDp = 1500, fontScale = 1.6f) @Composable
fun announcementExpiredEnDark() = AnnouncementFrame(true) { Detail(AdminAnnouncementState.EXPIRED) }
@PreviewTest @Preview(name = "cancelled_vi_dark_1024_large", widthDp = 1024, heightDp = 1000, fontScale = 1.6f) @Composable
fun announcementCancelledViDark() = AnnouncementFrame { Detail(AdminAnnouncementState.CANCELLED) }
@PreviewTest @Preview(name = "long_en_dark_320_large", widthDp = 320, heightDp = 932, fontScale = 1.6f) @Composable
fun announcementLongEnDark() = AnnouncementFrame(true) { Detail(long = true) }
@PreviewTest @Preview(name = "long_vi_dark_430_large", widthDp = 430, heightDp = 932, fontScale = 1.6f) @Composable
fun announcementLongViDark() = AnnouncementFrame { Detail(long = true) }
@PreviewTest @Preview(name = "long_footer_en_dark_320_large", widthDp = 320, heightDp = 932, fontScale = 1.6f) @Composable
fun announcementLongFooterEnDark() = AnnouncementFrame(true) { Detail(long = true, footer = true) }
@PreviewTest @Preview(name = "long_footer_vi_dark_1024", widthDp = 1024, heightDp = 768) @Composable
fun announcementLongFooterViDark() = AnnouncementFrame { Detail(long = true, footer = true) }
@PreviewTest @Preview(name = "landscape_en_dark_844_large", widthDp = 844, heightDp = 390, fontScale = 1.6f) @Composable
fun announcementLandscape() = AnnouncementFrame(true) { Detail() }
@PreviewTest @Preview(name = "inbox_legacy_vi_dark_430", widthDp = 430, heightDp = 1200) @Composable
fun announcementInboxViDark() = AnnouncementFrame { Inbox() }
@PreviewTest @Preview(name = "inbox_legacy_en_dark_1024", widthDp = 1024, heightDp = 768) @Composable
fun announcementInboxEnDark() = AnnouncementFrame(true) { Inbox(AdminAnnouncementState.EXPIRED) }
@PreviewTest @Preview(name = "inbox_cancelled_dismissed_vi_dark_320_large", widthDp = 320, heightDp = 1500, fontScale = 1.6f) @Composable
fun announcementInboxCancelled() = AnnouncementFrame { Inbox(AdminAnnouncementState.CANCELLED, dismissed = true) }
@PreviewTest @Preview(name = "inbox_long_en_dark_430_large", widthDp = 430, heightDp = 1500, fontScale = 1.6f) @Composable
fun announcementInboxLong() = AnnouncementFrame(true) { Inbox(long = true) }
@PreviewTest @Preview(name = "empty_zero_en_dark_320", widthDp = 320, heightDp = 568) @Composable
fun announcementEmpty() = AnnouncementFrame(true) { Inbox(empty = true) }
@PreviewTest @Preview(name = "consent_default_vi_dark_320_large", widthDp = 320, heightDp = 1200, fontScale = 1.6f) @Composable
fun announcementConsentDefault() = AnnouncementFrame { Consent() }
@PreviewTest @Preview(name = "consent_pending_en_dark_430", widthDp = 430, heightDp = 932) @Composable
fun announcementConsentPending() = AnnouncementFrame(true) { Consent(pending = true) }
@PreviewTest @Preview(name = "consent_uncertain_vi_dark_430_large", widthDp = 430, heightDp = 1200, fontScale = 1.6f) @Composable
fun announcementConsentUncertain() = AnnouncementFrame { Consent("NETWORK_ERROR") }
@PreviewTest @Preview(name = "consent_conflict_en_dark_1024", widthDp = 1024, heightDp = 768) @Composable
fun announcementConsentConflict() = AnnouncementFrame(true) { Consent("CONFLICT") }
@PreviewTest @Preview(name = "update_unsupported_vi_dark_320_large", widthDp = 320, heightDp = 568, fontScale = 1.6f) @Composable
fun announcementUpdateUnsupportedVi() = AnnouncementFrame { AnnouncementUpdateScreen(false, false, {}, {}) }
@PreviewTest @Preview(name = "update_unsupported_en_dark_1024", widthDp = 1024, heightDp = 768) @Composable
fun announcementUpdateUnsupportedEn() = AnnouncementFrame(true) { AnnouncementUpdateScreen(false, false, {}, {}) }
@PreviewTest @Preview(name = "update_available_en_dark_430", widthDp = 430, heightDp = 932) @Composable
fun announcementUpdateAvailable() = AnnouncementFrame(true) { AnnouncementUpdateScreen(true, true, {}, {}) }
@PreviewTest @Preview(name = "update_current_vi_dark_landscape", widthDp = 844, heightDp = 390) @Composable
fun announcementUpdateCurrent() = AnnouncementFrame { AnnouncementUpdateScreen(true, false, {}, {}) }
