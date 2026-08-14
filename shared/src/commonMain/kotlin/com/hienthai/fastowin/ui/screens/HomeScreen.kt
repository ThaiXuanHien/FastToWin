package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import kotlinx.coroutines.delay

internal enum class MainTab { HOME, LEADERBOARD, FRIENDS, ACCOUNT }

private enum class HomeLaunchAction { PLAY, CREATE_ROOM }

@Composable
internal fun HomeDashboard(
    state: GameState,
    isGuest: Boolean,
    onChooseMode: (GameMode, Boolean) -> Unit,
    onQuickMatch: (GameMode) -> Unit,
    onOpenRooms: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPractice: () -> Unit,
    onUpgradeGuest: () -> Unit,
    onLogout: () -> Unit,
    sessionStartedAtMillis: Long,
    modifier: Modifier = Modifier
) {
    var launchAction by remember { mutableStateOf<HomeLaunchAction?>(null) }
    launchAction?.let { action ->
        GameModePickerDialog(
            title = if (action == HomeLaunchAction.CREATE_ROOM) "Tạo phòng mới" else "Chọn chế độ chơi",
            onDismiss = { launchAction = null },
            onSelect = { mode ->
                launchAction = null
                if (action == HomeLaunchAction.PLAY) {
                    onQuickMatch(mode)
                } else {
                    onChooseMode(mode, true)
                }
            }
        )
    }

    val profile = state.profile
    val displayName = profile?.displayName ?: state.player.name
    val elo = profile?.statistics?.eloRating
    val rank = state.leaderboard?.currentPlayer?.rank
    val onlineFriends = state.social.friends.count { it.presence != FriendPresence.OFFLINE }
    val openSocial = if (isGuest) onUpgradeGuest else onOpenFriends

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "FAST TO WIN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text("Chào $displayName 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("THỜI GIAN TRUY CẬP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SessionDuration(sessionStartedAtMillis)
                }
                IconButton(onClick = onOpenNotifications) {
                    BadgedBox(
                        badge = {
                            if (state.unreadNotificationCount > 0) {
                                Badge {
                                    Text(
                                        if (state.unreadNotificationCount > 99) "99+"
                                        else state.unreadNotificationCount.toString()
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Thông báo")
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Cài đặt")
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (elo == null) "ĐANG ĐỒNG BỘ DỮ LIỆU"
                        else buildString {
                            append("ELO ").append(elo)
                            rank?.let { append("  •  Hạng #").append(it) }
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text("Sẵn sàng chơi nhanh?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (isGuest) "Đăng ký tài khoản để ghép đối thủ theo Elo."
                        else "Tự động tìm đối thủ có Elo gần bạn.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    )
                    Button(
                        onClick = if (isGuest) onUpgradeGuest else ({ launchAction = HomeLaunchAction.PLAY }),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("  Chơi nhanh", fontWeight = FontWeight.Bold)
                    }
                }
            }

            SectionTitle("Lối tắt", "Mọi chức năng chính đều ở tầng đầu")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeQuickAction(
                        "Tạo phòng",
                        "Chọn chế độ rồi tạo",
                        Icons.Default.Add,
                        { launchAction = HomeLaunchAction.CREATE_ROOM },
                        Modifier.weight(1f)
                    )
                    HomeQuickAction(
                        "Vào phòng",
                        "${state.availableRooms.size} phòng đang mở",
                        Icons.Default.MeetingRoom,
                        onOpenRooms,
                        Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeQuickAction(
                        "Bạn bè",
                        if (isGuest) "Đăng ký để kết bạn" else "$onlineFriends người online",
                        Icons.Default.Group,
                        openSocial,
                        Modifier.weight(1f)
                    )
                    HomeQuickAction(
                        "Xếp hạng",
                        rank?.let { "Bạn đang hạng #$it" } ?: "Xem bảng Elo",
                        Icons.Default.EmojiEvents,
                        onOpenLeaderboard,
                        Modifier.weight(1f)
                    )
                }
                HomeQuickAction(
                    "Luyện tập offline",
                    "Rèn tốc độ với bàn 50 số, không ảnh hưởng Elo",
                    Icons.Default.FitnessCenter,
                    onOpenPractice,
                    Modifier.fillMaxWidth()
                )
            }

            state.profile?.recentMatches?.firstOrNull()?.let { match ->
                SectionTitle("Trận gần nhất")
                ElevatedCard(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(match.roomName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Đối thủ ${match.opponentName}  •  ${matchOutcomeLabel(match.outcome)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            if (match.eloChange >= 0) "+${match.eloChange} Elo" else "${match.eloChange} Elo",
                            color = if (match.eloChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!isGuest) {
                DelayedConnectionNotice(state.connectionStatus)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (isGuest) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lưu tiến trình của bạn", fontWeight = FontWeight.Bold)
                        Text("Đăng ký email để lưu Elo, lịch sử và bạn bè trên Android/iOS.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onUpgradeGuest) { Text("Tạo tài khoản") }
                            TextButton(onClick = onLogout) { Text("Thoát chế độ khách") }
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun SessionDuration(sessionStartedAtMillis: Long) {
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(sessionStartedAtMillis) {
        while (true) {
            elapsedSeconds = ((epochMillis() - sessionStartedAtMillis) / 1_000).coerceAtLeast(0)
            delay(1_000)
        }
    }
    Text(
        formatSessionDuration(elapsedSeconds),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DelayedConnectionNotice(status: ConnectionStatus) {
    var visibleStatus by remember { mutableStateOf<ConnectionStatus?>(null) }
    LaunchedEffect(status) {
        visibleStatus = null
        if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.RECONNECTING) {
            delay(2_000)
            visibleStatus = status
        }
    }
    visibleStatus?.let { stableStatus ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Text(
                connectionLabel(stableStatus),
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun FastToWinBottomBar(
    selected: MainTab,
    friendNotificationCount: Int,
    onHome: () -> Unit,
    onLeaderboard: () -> Unit,
    onPlay: () -> Unit,
    onFriends: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .height(68.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BottomBarItem("Trang chủ", Icons.Default.Home, selected == MainTab.HOME, onHome, Modifier.weight(1f))
            BottomBarItem("Xếp hạng", Icons.Default.EmojiEvents, selected == MainTab.LEADERBOARD, onLeaderboard, Modifier.weight(1f))
            BottomBarItem("Chơi", Icons.Default.SportsEsports, false, onPlay, Modifier.weight(1f))
            BottomBarItem(
                label = "Bạn bè",
                icon = Icons.Default.Group,
                selected = selected == MainTab.FRIENDS,
                onClick = onFriends,
                modifier = Modifier.weight(1f),
                badgeCount = friendNotificationCount
            )
            BottomBarItem("Tài khoản", Icons.Default.Person, selected == MainTab.ACCOUNT, onAccount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                    }
                }
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeQuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun GameModePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelect: (GameMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeChoice("Đua thứ tự", "Tìm nhanh các số từ 1 đến 50", Icons.Rounded.Bolt) {
                    onSelect(GameMode.ORDER)
                }
                ModeChoice("Đua 60 giây", "Giành nhiều điểm nhất trong 60 giây", Icons.Rounded.Timer) {
                    onSelect(GameMode.TIME_ATTACK)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
private fun ModeChoice(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun matchOutcomeLabel(outcome: MatchHistoryOutcome): String = when (outcome) {
    MatchHistoryOutcome.WIN -> "Thắng"
    MatchHistoryOutcome.LOSS -> "Thua"
    MatchHistoryOutcome.DRAW -> "Hòa"
}

private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCONNECTED -> "Chưa kết nối máy chủ"
    ConnectionStatus.CONNECTING -> "Đang kết nối máy chủ..."
    ConnectionStatus.AUTHENTICATING -> "Đang xác thực tài khoản..."
    ConnectionStatus.CONNECTED -> "Đã kết nối"
    ConnectionStatus.RECONNECTING -> "Mất kết nối, đang thử lại..."
}

private fun formatSessionDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    fun Long.twoDigits() = toString().padStart(2, '0')
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}"
}
