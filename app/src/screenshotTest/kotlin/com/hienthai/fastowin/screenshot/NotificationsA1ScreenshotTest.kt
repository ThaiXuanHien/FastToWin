package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.localization.AppLanguage
import com.hienthai.fastowin.localization.ProvideLocalization
import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.protocol.AdminAnnouncementContext
import com.hienthai.fastowin.protocol.AdminAnnouncementState
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationDestination
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.ui.components.A1SkyBackdrop
import com.hienthai.fastowin.ui.screens.NotificationsScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme

private const val NOW = 1_800_000_000_000L
private const val HOUR = 3_600_000L

private fun announcement(id: String, title: String, body: String, category: AdminAnnouncementCategory, action: AdminAnnouncementAction) =
    AdminAnnouncementContext(id, title, title, body, body, category, 0, NOW - HOUR, NOW + 48 * HOUR, AdminAnnouncementState.ACTIVE, action)

/** Dữ liệu giống bảng thiết kế "Thông báo". */
private fun designNotifications() = listOf(
    AppNotification("n1", AppNotificationKind.COSMETIC, "Cuối tuần x2 Vàng", "Mọi trận thắng nhận gấp đôi Vàng đến 23:59 Chủ nhật.", NOW - 2 * HOUR,
        destination = AppNotificationDestination.PROFILE,
        announcement = announcement("a1", "Cuối tuần x2 Vàng", "Mọi trận thắng nhận gấp đôi Vàng đến 23:59 Chủ nhật.", AdminAnnouncementCategory.EVENT, AdminAnnouncementAction.REWARD_EVENT)),
    AppNotification("n2", AppNotificationKind.COSMETIC, "Quà bù bảo trì", "300 Vàng đã gửi vào Hộp quà của bạn.", NOW - 5 * HOUR,
        destination = AppNotificationDestination.PROFILE,
        announcement = announcement("a2", "Quà bù bảo trì", "300 Vàng đã gửi vào Hộp quà của bạn.", AdminAnnouncementCategory.COMPENSATION, AdminAnnouncementAction.GIFT_MAILBOX)),
    AppNotification("n3", AppNotificationKind.FRIEND_REQUEST, "VyVy muốn kết bạn", "Mở Bạn bè để chấp nhận.", NOW - 26 * HOUR, isRead = true,
        destination = AppNotificationDestination.FRIENDS),
    AppNotification("n4", AppNotificationKind.ACHIEVEMENT, "Tổng kết mùa 6", "Bạn đạt Vàng I · nhận Khung mùa độc quyền.", NOW - 72 * HOUR, isRead = true,
        destination = AppNotificationDestination.PROFILE),
    AppNotification("n5", AppNotificationKind.COSMETIC, "Bảo trì đã hoàn tất", "Cảm ơn bạn đã chờ.", NOW - 96 * HOUR, isRead = true,
        destination = AppNotificationDestination.PROFILE,
        announcement = announcement("a5", "Bảo trì đã hoàn tất", "Cảm ơn bạn đã chờ.", AdminAnnouncementCategory.MAINTENANCE, AdminAnnouncementAction.NONE))
)

@Composable
private fun NotificationsFrame(empty: Boolean = false) {
    ProvideLocalization(AppLanguage.VIETNAMESE) {
        FastToWinTheme {
            A1SkyBackdrop(Modifier.fillMaxSize(), showHills = false) {
                NotificationsScreen(
                    notifications = if (empty) emptyList() else designNotifications(),
                    onBack = {}, onOpen = {}, onDismiss = {}, onMarkAllRead = {}, onClearAll = {},
                    nowMillis = NOW
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "design_390", widthDp = 390, heightDp = 844)
@Preview(name = "small_large_text", widthDp = 360, heightDp = 740, fontScale = 1.6f)
@Composable
fun notificationsA1() = NotificationsFrame()

@PreviewTest
@Preview(name = "empty_390", widthDp = 390, heightDp = 844)
@Composable
fun notificationsA1Empty() = NotificationsFrame(empty = true)
