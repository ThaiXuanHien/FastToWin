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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton
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
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)

    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AppNotification?>(null) }
    if (confirmClear) {
        ArcadeDialog(
            onDismissRequest = { confirmClear = false },
            title = "Xóa tất cả thông báo?",
            subtitle = "Các thông báo đang hiển thị sẽ bị xóa và đồng bộ trên mọi thiết bị."
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = "Xóa tất cả",
                    onClick = {
                        confirmClear = false
                        onClearAll()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "Hủy",
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
                title = "Thông báo",
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
                        Icon(Icons.Default.DoneAll, contentDescription = "Đánh dấu tất cả đã đọc")
                    }
                    ArcadeHeaderIconButton(
                        onClick = { confirmClear = true },
                        enabled = notifications.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa tất cả thông báo")
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
            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ArcadeEmptyState(
                        illustration = Res.drawable.arcade_notifications_inbox,
                        title = "Hộp thư đang trống",
                        description = "Lời mời, phần thưởng và tin mới sẽ xuất hiện tại đây."
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
                            title = "Tin mới dành cho bạn",
                            subtitle = "Theo dõi lời mời, phần thưởng và hoạt động quan trọng.",
                            accent = ArcadePalette.Violet400
                        )
                    }
                    items(notifications, key = AppNotification::id) { notification ->
                        NotificationCard(
                            notification = notification,
                            onOpen = { onOpen(notification.id) },
                            onDismiss = { pendingDelete = notification }
                        )
                    }
                    item { Spacer(Modifier.size(12.dp)) }
                }
            }
        }
    }
    }
    pendingDelete?.let { notification ->
        ArcadeDialog(
            onDismissRequest = { pendingDelete = null },
            title = "Xóa thông báo?",
            subtitle = "Thông báo “${notification.title}” sẽ bị xóa khỏi danh sách."
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = "XÓA",
                    onClick = {
                        pendingDelete = null
                        onDismiss(notification.id)
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "HỦY",
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
                        notificationIcon(notification.kind),
                        contentDescription = null,
                        tint = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant else ArcadePalette.White
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    notification.title,
                    fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
                )
                Text(
                    notification.message,
                    color = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant else ArcadePalette.Blue100
                )
                Text(
                    relativeNotificationTime(notification.createdAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notification.isRead) MaterialTheme.colorScheme.primary else ArcadePalette.Gold500,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Xóa thông báo",
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

private fun relativeNotificationTime(createdAtMillis: Long): String {
    val elapsed = (epochMillis() - createdAtMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = elapsed / 3_600_000L
    val days = elapsed / 86_400_000L
    return when {
        minutes < 1L -> "Vừa xong"
        hours < 1L -> "$minutes phút trước"
        days < 1L -> "$hours giờ trước"
        else -> "$days ngày trước"
    }
}
