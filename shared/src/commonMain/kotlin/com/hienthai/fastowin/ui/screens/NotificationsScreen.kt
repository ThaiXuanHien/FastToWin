package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.AdminAnnouncementAction
import com.hienthai.fastowin.protocol.AdminAnnouncementCategory
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.AnnouncementActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeChoiceChip
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.A1Palette
import kotlinx.coroutines.delay

@Composable
fun NotificationsScreen(
    notifications: List<AppNotification>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
    nowMillis: Long = epochMillis(),
    announcementState: com.hienthai.fastowin.state.AnnouncementState? = null,
    onAnnouncementMore: () -> Unit = {},
    onAnnouncementRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    hasLoadedNotifications: Boolean = true,
    notificationsError: String? = null,
    onNotificationsRetry: () -> Unit = {}
) {
    SystemBackHandler(onBack = onBack)

    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AppNotification?>(null) }
    var visibleNotificationCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val presentation = notificationInboxPresentation(notifications.size, announcementState, hasLoadedNotifications, notificationsError)
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(presentation) {
        showLoading = false
        if (presentation == NotificationInboxPresentation.LOADING) {
            delay(200)
            showLoading = true
        }
    }
    if (confirmClear) {
        ArcadeDialog(
            onDismissRequest = { confirmClear = false },
            title = localized(TextKey.ClearAllNotificationsTitle),
            subtitle = localized(TextKey.ClearAllNotificationsDescription)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = localized(TextKey.ClearAllNotifications),
                    onClick = {
                        confirmClear = false
                        onClearAll()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_clear_notifications")
                )
                ArcadeActionButton(
                    label = localized(TextKey.Cancel),
                    onClick = { confirmClear = false },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    var filter by remember { mutableStateOf(NotificationFilter.ALL) }
    val announcementByNotification = announcementState?.items.orEmpty().associateBy { it.notification.id }
    val filtered = notifications.filter { filter.matches(it) }
    val visibleFiltered = filtered.take(visibleNotificationCount)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            // Như thiết kế: nút quay lại, tiêu đề và nút trắng "Đọc hết".
            FastToWinHeader(
                title = localized(TextKey.Notifications),
                gold = 0,
                gems = 0,
                unreadNotifications = notifications.count { !it.isRead },
                onNotifications = {},
                onBack = onBack,
                showNotifications = false,
                showBalances = false,
                actions = {
                    val markAllDescription = localized(TextKey.MarkAllRead)
                    ArcadeActionButton(
                        label = localized(TextKey.NotificationsReadAll),
                        onClick = onMarkAllRead,
                        enabled = notifications.any { !it.isRead },
                        style = ArcadeActionStyle.OUTLINE,
                        compact = true,
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .widthIn(max = 140.dp)
                            .semantics { contentDescription = markAllDescription }
                    )
                }
            )
        }
    ) { paddingValues ->
        ResponsiveScreen(
            modifier = Modifier
                .padding(paddingValues)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                ),
            maxContentWidth = 720.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Column(
                modifier = contentModifier.padding(top = 8.dp),
                // Chip cùng lề với thẻ (thẻ chừa 4dp cho chấm đỏ chưa đọc).
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (presentation == NotificationInboxPresentation.LOADING) {
                    val label = localized(TextKey.Loading)
                    Box(Modifier.fillMaxSize().testTag("notifications_initial_loading").semantics { stateDescription = label }, contentAlignment = Alignment.Center) {
                        if (showLoading) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
                    }
                } else if (presentation == NotificationInboxPresentation.ERROR) {
                    Column(
                        Modifier.fillMaxWidth().a1Card().padding(18.dp).testTag("notifications_load_error"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(localized(TextKey.AnnouncementError), color = A1Palette.Ink, fontWeight = FontWeight.SemiBold)
                        ArcadeActionButton(localized(TextKey.Retry), onClick = {
                            onNotificationsRetry()
                            onAnnouncementRetry()
                        }, modifier = Modifier.testTag("notifications_retry"))
                    }
                } else if (presentation == NotificationInboxPresentation.EMPTY) {
                    NotificationEmptyCard()
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("notification_filters"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NotificationFilter.entries.forEach { value ->
                            ArcadeChoiceChip(
                                label = localized(value.label),
                                selected = filter == value,
                                onClick = {
                                    filter = value
                                    visibleNotificationCount = DEFAULT_ARCADE_PAGE_SIZE
                                },
                                modifier = Modifier.testTag("notification_filter_${value.name.lowercase()}")
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        announcementState?.let { announcement ->
                            if (announcement.error != null) item(key = "announcement_error") {
                                Column(Modifier.fillMaxWidth().a1Card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(localized(TextKey.AnnouncementError), color = A1Palette.Ink, fontWeight = FontWeight.SemiBold)
                                    AnnouncementActionButton(localized(TextKey.AnnouncementRetry), onAnnouncementRetry, enabled = !announcement.loading && !announcement.pending)
                                }
                            }
                        }
                        if (filtered.isEmpty()) item(key = "notifications_filter_empty") {
                            Text(
                                localized(TextKey.NotificationsFilterEmpty),
                                modifier = Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
                                color = A1Palette.Muted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(visibleFiltered, key = AppNotification::id) { notification ->
                            NotificationCard(
                                notification = notification,
                                nowMillis = nowMillis,
                                dismissed = announcementByNotification[notification.id]?.dismissed == true,
                                onOpen = { onOpen(notification.id) },
                                onDismiss = { pendingDelete = notification }
                            )
                        }
                        item(key = "notifications_load_more") {
                            ArcadeLoadMoreButton(
                                visibleItemCount = visibleFiltered.size,
                                totalItemCount = filtered.size,
                                onLoadMore = { visibleNotificationCount = nextArcadePageItemCount(visibleNotificationCount, filtered.size) },
                                testTag = "notifications_load_more"
                            )
                        }
                        if (announcementState?.hasMore == true) item(key = "announcement_more") {
                            AnnouncementActionButton(
                                localized(TextKey.AnnouncementMore), onAnnouncementMore,
                                enabled = !announcementState.loading && !announcementState.pending, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item(key = "notifications_clear_all") {
                            val clearDescription = localized(TextKey.ClearAllNotificationsA11y)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    localized(TextKey.ClearAllNotifications),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable(role = Role.Button) { confirmClear = true }
                                        .semantics { contentDescription = clearDescription }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = A1Palette.Ink
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { notification ->
        ArcadeDialog(
            onDismissRequest = { pendingDelete = null },
            title = localized(TextKey.DeleteNotificationTitle),
            subtitle = localized(TextKey.DeleteNotificationDescription, "title" to notification.title)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = localized(TextKey.Delete),
                    onClick = {
                        pendingDelete = null
                        onDismiss(notification.id)
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = localized(TextKey.Cancel),
                    onClick = { pendingDelete = null },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Chip lọc của thiết kế: Tất cả / Sự kiện / Bạn bè / Hệ thống. */
private enum class NotificationFilter(val label: TextKey) {
    ALL(TextKey.RecentFilterAll),
    EVENTS(TextKey.AnnouncementEvent),
    FRIENDS(TextKey.Friends),
    SYSTEM(TextKey.NotificationsSystem);

    fun matches(notification: AppNotification): Boolean = when (this) {
        ALL -> true
        EVENTS -> notification.announcement?.category == AdminAnnouncementCategory.EVENT
        FRIENDS -> notification.announcement == null && notification.kind in setOf(
            AppNotificationKind.FRIEND_REQUEST, AppNotificationKind.ROOM_INVITATION, AppNotificationKind.CLAN_INVITATION
        )
        SYSTEM -> !EVENTS.matches(notification) && !FRIENDS.matches(notification)
    }
}

/** Ô icon theo loại tin (sự kiện vàng, quà xanh lá, bạn bè xanh dương, thành tích tím, bảo trì đen). */
private fun notificationTile(notification: AppNotification): Triple<ImageVector, Color, Color> {
    val announcement = notification.announcement
    if (announcement != null) return when (announcement.category) {
        AdminAnnouncementCategory.EVENT -> Triple(Icons.Rounded.Celebration, A1Palette.Yellow2, Color(0xFF6A3300))
        AdminAnnouncementCategory.COMPENSATION -> Triple(Icons.Rounded.Redeem, A1Palette.Green2, Color.White)
        AdminAnnouncementCategory.MAINTENANCE -> Triple(Icons.Rounded.Construction, A1Palette.Ink, Color.White)
        AdminAnnouncementCategory.GENERAL -> Triple(Icons.Rounded.Campaign, A1Palette.Blue2, Color.White)
    }
    return when (notification.kind) {
        AppNotificationKind.FRIEND_REQUEST -> Triple(Icons.Rounded.PersonAdd, A1Palette.Blue2, Color.White)
        AppNotificationKind.ROOM_INVITATION -> Triple(Icons.Rounded.MeetingRoom, A1Palette.Blue2, Color.White)
        AppNotificationKind.CLAN_INVITATION -> Triple(Icons.Rounded.Shield, A1Palette.Purple2, Color.White)
        AppNotificationKind.MISSION -> Triple(Icons.Rounded.TaskAlt, A1Palette.Green2, Color.White)
        AppNotificationKind.ACHIEVEMENT -> Triple(Icons.Rounded.EmojiEvents, A1Palette.Purple2, Color.White)
        AppNotificationKind.COSMETIC -> Triple(Icons.Rounded.Redeem, A1Palette.Yellow2, Color(0xFF6A3300))
    }
}

@Composable
private fun NotificationEmptyCard() {
    Column(
        Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        A1IconTile(Icons.Rounded.NotificationsNone, A1Palette.Blue2, size = 52.dp, radius = 16.dp, iconSize = 30.dp)
        Text(
            localized(TextKey.EmptyInbox),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontWeight = FontWeight.ExtraBold,
            color = A1Palette.Ink,
            textAlign = TextAlign.Center
        )
        Text(
            localized(TextKey.EmptyInboxDescription),
            style = MaterialTheme.typography.bodyMedium,
            color = A1Palette.Muted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Thẻ tin (".card.nt"): tin chưa đọc là thẻ đậm có chấm đỏ ở mép trái (sự kiện thêm nền vàng nhạt),
 * tin đã đọc là thẻ nhẹ. Bấm để mở; giữ lâu (hoặc thao tác trợ năng) để xóa.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationCard(
    notification: AppNotification,
    nowMillis: Long,
    dismissed: Boolean = false,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val announcement = notification.announcement
    val unread = !notification.isRead
    val event = announcement?.category == AdminAnnouncementCategory.EVENT
    val (icon, tileColor, tileTint) = notificationTile(notification)
    val deleteLabel = localized(TextKey.DeleteNotification)
    val unreadLabel = localized(TextKey.NotificationUnread)
    val radius = if (unread) 22.dp else 18.dp
    Box(Modifier.fillMaxWidth().testTag("notification_card:${notification.id}")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (unread) Modifier.a1Card(background = Color.Transparent) else Modifier.a1Card(radius = 18.dp, depth = 3.dp, background = Color.Transparent))
                .clip(RoundedCornerShape(radius))
                .background(
                    if (unread && event) Brush.linearGradient(
                        0f to Color.White, 0.55f to Color.White, 1f to A1Palette.Highlight,
                        start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ) else androidx.compose.ui.graphics.SolidColor(Color.White)
                )
                .combinedClickable(onClick = onOpen, onLongClick = { if (!dismissed) onDismiss() })
                .semantics {
                    if (unread) stateDescription = unreadLabel
                    if (!dismissed) customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDismiss(); true })
                }
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            A1IconTile(icon, tileColor, tileTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Thiết kế chỉ gắn nhãn cho tin Sự kiện.
                if (event) {
                    A1Tag(
                        localized(com.hienthai.fastowin.ui.components.announcementCategoryKey(announcement.category)),
                        when (announcement.category) {
                            AdminAnnouncementCategory.EVENT -> A1TagTone.YELLOW
                            AdminAnnouncementCategory.COMPENSATION -> A1TagTone.GREEN
                            AdminAnnouncementCategory.MAINTENANCE -> A1TagTone.INK
                            AdminAnnouncementCategory.GENERAL -> A1TagTone.BLUE
                        },
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = A1Palette.Ink
                )
                Text(
                    notification.message,
                    maxLines = if (announcement != null) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted
                )
                if (dismissed) {
                    Text(localized(TextKey.AnnouncementDismissed), style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
                }
                when {
                    announcement?.action == AdminAnnouncementAction.GIFT_MAILBOX -> ArcadeActionButton(
                        label = localized(TextKey.NotificationOpenGifts),
                        onClick = onOpen,
                        style = ArcadeActionStyle.SUCCESS,
                        compact = true,
                        modifier = Modifier.padding(top = 6.dp).width(IntrinsicSize.Max)
                    )
                    event -> Text(
                        localized(TextKey.NotificationSeeDetails),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = A1Palette.Blue2
                    )
                }
            }
            Text(
                shortNotificationTime(notification.createdAtEpochMillis, nowMillis),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted
            )
        }
        if (unread) {
            // Chấm đỏ ở mép trái thẻ (".nt.new::before").
            Box(
                Modifier
                    .padding(top = 18.dp)
                    .offset(x = (-3).dp)
                    .size(8.dp)
                    .background(Color(0xFFFF3B5C), CircleShape)
            )
        }
    }
}

/** "Vừa xong", "5 phút", "2 giờ", "Hôm qua", "3 ngày" như thiết kế. */
@Composable
private fun shortNotificationTime(createdAtMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - createdAtMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1L -> localized(TextKey.JustNow).replaceFirstChar { it.uppercase() }
        hours < 1L -> localized(TextKey.NotificationMinutesShort, "count" to minutes)
        days < 1L -> localized(TextKey.NotificationHoursShort, "count" to hours)
        days == 1L -> localized(TextKey.NotificationYesterday)
        else -> localized(TextKey.NotificationDaysShort, "count" to days)
    }
}
