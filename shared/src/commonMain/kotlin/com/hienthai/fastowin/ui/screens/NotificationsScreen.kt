package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.reportReferences
import com.hienthai.fastowin.ui.components.UgcSafetyButton
import com.hienthai.fastowin.ui.components.UgcTarget
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.AnnouncementActionButton
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_notifications_inbox

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
    val visibleNotifications = notifications.take(visibleNotificationCount)
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
                    modifier = Modifier.fillMaxWidth()
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
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
                    ArcadeHeaderIconButton(
                        onClick = onMarkAllRead,
                        enabled = notifications.any { !it.isRead }
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = localized(TextKey.MarkAllRead))
                    }
                    ArcadeHeaderIconButton(
                        onClick = { confirmClear = true },
                        enabled = notifications.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = localized(TextKey.ClearAllNotificationsA11y))
                    }
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
            maxContentWidth = 920.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Column(
                modifier = contentModifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            announcementState?.let { announcement ->
                if (announcement.hasLoadedInbox && announcement.total != "0") {
                    Text(localized(TextKey.AnnouncementHistory, "count" to announcement.total), fontWeight = FontWeight.Bold)
                }
                if (announcement.error != null && presentation == NotificationInboxPresentation.CONTENT) {
                    Text(localized(TextKey.AnnouncementError))
                    AnnouncementActionButton(localized(TextKey.AnnouncementRetry), onAnnouncementRetry, enabled = !announcement.loading && !announcement.pending)
                }
            }
            if (presentation == NotificationInboxPresentation.LOADING) {
                val label = localized(TextKey.Loading)
                Box(Modifier.fillMaxSize().testTag("notifications_initial_loading").semantics { stateDescription = label }, contentAlignment = Alignment.Center) {
                    if (showLoading) CircularProgressIndicator(Modifier.size(32.dp), color = ArcadePalette.Blue300)
                }
            } else if (presentation == NotificationInboxPresentation.ERROR) {
                Column(Modifier.fillMaxSize().testTag("notifications_load_error"),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(localized(TextKey.AnnouncementError), style = MaterialTheme.typography.bodyMedium)
                    ArcadeActionButton(localized(TextKey.Retry), onClick = {
                        onNotificationsRetry()
                        onAnnouncementRetry()
                    }, modifier = Modifier.testTag("notifications_retry"))
                }
            } else if (presentation == NotificationInboxPresentation.EMPTY) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ArcadeEmptyState(
                        illustration = Res.drawable.arcade_notifications_inbox,
                        title = localized(TextKey.EmptyInbox),
                        description = localized(TextKey.EmptyInboxDescription)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "notifications_hero") {
                        ArcadeFeatureHero(
                            illustration = Res.drawable.arcade_notifications_inbox,
                            title = localized(TextKey.NotificationsHero),
                            subtitle = localized(TextKey.NotificationsHeroDescription),
                            accent = ArcadePalette.Violet400
                        )
                    }
                    items(visibleNotifications, key = AppNotification::id) { notification ->
                        NotificationCard(
                            notification = notification,
                            nowMillis = nowMillis,
                            dismissed = announcementState?.items?.firstOrNull { it.notification.id == notification.id }?.dismissed == true,
                            onOpen = { onOpen(notification.id) },
                            onDismiss = { pendingDelete = notification }
                        )
                    }
                    item(key = "notifications_load_more") {
                        ArcadeLoadMoreButton(
                            visibleItemCount = visibleNotifications.size,
                            totalItemCount = notifications.size,
                            onLoadMore = {
                                visibleNotificationCount = nextArcadePageItemCount(
                                    visibleNotificationCount,
                                    notifications.size
                                )
                            },
                            testTag = "notifications_load_more"
                        )
                    }
                    item { Spacer(Modifier.size(12.dp)) }
                    if (announcementState?.hasMore == true) item(key = "announcement_more") {
                        AnnouncementActionButton(localized(TextKey.AnnouncementMore), onAnnouncementMore,
                            enabled = !announcementState.loading && !announcementState.pending, modifier = Modifier.fillMaxWidth())
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

@Composable
private fun NotificationCard(
    notification: AppNotification,
    nowMillis: Long,
    dismissed: Boolean = false,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (notification.isRead) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        } else {
            ArcadePalette.Navy800
        },
        contentColor = if (notification.isRead) MaterialTheme.colorScheme.onSurface else ArcadePalette.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (notification.isRead) ArcadePalette.OutlineDark.copy(alpha = 0.5f) else ArcadePalette.Blue300
        ),
        shadowElevation = if (notification.isRead) 1.dp else 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (notification.isRead) MaterialTheme.colorScheme.surfaceContainerHighest else ArcadePalette.Blue700,
                modifier = Modifier.size(48.dp),
                border = if (notification.isRead) null else androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.Blue300)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (notification.announcement != null) Icons.Default.Campaign else notificationIcon(notification.kind),
                        contentDescription = null,
                        tint = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant else ArcadePalette.White
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                notification.announcement?.let { context ->
                    Text(localized(TextKey.AnnouncementLabel), fontWeight = FontWeight.Bold)
                    Text(localized(com.hienthai.fastowin.ui.components.announcementCategoryKey(context.category)) + " · " +
                        localized(com.hienthai.fastowin.ui.components.announcementStateKey(context.state)), style = MaterialTheme.typography.bodySmall)
                    if (dismissed) Text(localized(TextKey.AnnouncementDismissed), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    notification.title,
                    fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
                )
                Text(
                    notification.message,
                    maxLines = if (notification.announcement != null) 3 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    color = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant else ArcadePalette.Blue100
                )
                Text(
                    relativeNotificationTime(notification.createdAtEpochMillis, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notification.isRead) MaterialTheme.colorScheme.primary else ArcadePalette.Gold500,
                    fontWeight = FontWeight.Bold
                )
            }
            UgcSafetyButton(notification.reportReferences().map { UgcTarget(it.type, it.id, it.label) })
            IconButton(onClick = onDismiss, enabled = !dismissed) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = localized(TextKey.DeleteNotification),
                    tint = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant else ArcadePalette.Coral400
                )
            }
        }
    }
}

private fun notificationIcon(kind: AppNotificationKind): ImageVector = when (kind) {
    AppNotificationKind.FRIEND_REQUEST -> Icons.Default.PersonAdd
    AppNotificationKind.ROOM_INVITATION -> Icons.Default.MeetingRoom
    AppNotificationKind.MISSION -> Icons.Default.TaskAlt
    AppNotificationKind.ACHIEVEMENT -> Icons.Default.EmojiEvents
    AppNotificationKind.COSMETIC -> Icons.Default.Redeem
    AppNotificationKind.CLAN_INVITATION -> Icons.Default.PersonAdd
}

@Composable
private fun relativeNotificationTime(createdAtMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - createdAtMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1L -> localized(TextKey.JustNow)
        hours < 1L -> localized(TextKey.MinutesAgo, "count" to minutes)
        days < 1L -> localized(TextKey.HoursAgo, "count" to hours)
        else -> localized(TextKey.DaysAgo, "count" to days)
    }
}
