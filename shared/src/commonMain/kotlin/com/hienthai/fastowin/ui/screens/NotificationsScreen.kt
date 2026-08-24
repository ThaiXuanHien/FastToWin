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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.state.AppNotification
import com.hienthai.fastowin.state.AppNotificationKind
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.components.FastToWinHeader

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
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xóa tất cả thông báo?") },
            text = { Text("Các thông báo đang hiển thị sẽ được xóa. Tài khoản đăng nhập sẽ đồng bộ thay đổi này trên các thiết bị.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text("Xóa tất cả", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Hủy") } }
        )
    }

    ResponsiveScreen(modifier = modifier, maxContentWidth = 920.dp) { contentModifier ->
        Column(
            modifier = contentModifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FastToWinHeader(
                title = "Thông báo",
                gold = 0,
                gems = 0,
                unreadNotifications = notifications.count { !it.isRead },
                onNotifications = {},
                onBack = onBack,
                applySafeDrawingInset = false,
                showNotifications = false,
                showBalances = false,
                actions = {
                    IconButton(
                        onClick = onMarkAllRead,
                        enabled = notifications.any { !it.isRead }
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Đánh dấu tất cả đã đọc")
                    }
                    IconButton(
                        onClick = { confirmClear = true },
                        enabled = notifications.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa tất cả thông báo")
                    }
                }
            )

            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔔", style = MaterialTheme.typography.displayMedium)
                        Text("Chưa có thông báo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Lời mời và phần thưởng mới sẽ xuất hiện tại đây.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = AppNotification::id) { notification ->
                        NotificationCard(
                            notification = notification,
                            onOpen = { onOpen(notification.id) },
                            onDismiss = { onDismiss(notification.id) }
                        )
                    }
                    item { Spacer(Modifier.size(12.dp)) }
                }
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
    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (notification.isRead) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        notificationIcon(notification.kind),
                        contentDescription = null,
                        tint = if (notification.isRead) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    notification.title,
                    fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
                )
                Text(notification.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    relativeNotificationTime(notification.createdAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Delete, contentDescription = "Xóa thông báo")
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
