package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.DailyCheckInSnapshot
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_XP
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GOLD
import com.hienthai.fastowin.protocol.DAILY_CHECK_IN_REWARDS_GEMS
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_home_hero
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.GemColor
import com.hienthai.fastowin.ui.components.GoldColor
import com.hienthai.fastowin.ui.components.CrossedSwordsIcon
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

internal enum class MainTab { HOME, ROOMS, LEADERBOARD, CLAN, ACCOUNT }

private enum class HomeLaunchAction { CASUAL, RANKED, CREATE_ROOM }

@Composable
internal fun HomeDashboard(
    state: GameState,
    isGuest: Boolean,
    onChooseMode: (GameMode, Boolean) -> Unit,
    onQuickMatch: (GameMode, MatchType) -> Unit,
    onOpenRooms: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenClan: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTournament: () -> Unit,
    onOpenShop: () -> Unit,
    onClaimDailyCheckIn: () -> Unit,
    onUpgradeGuest: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = state.profile
    val displayName = profile?.displayName ?: state.player.name
    val avatarId = profile?.avatarId ?: state.player.avatarId
    val equippedFrameId = profile?.progression?.cosmetics?.firstOrNull {
        it.type == CosmeticType.FRAME && it.equipped
    }?.id ?: state.player.frameId
    val elo = profile?.statistics?.eloRating
    val rank = state.leaderboard?.currentPlayer?.rank
    val onlineFriends = state.social.friends.count { it.presence != FriendPresence.OFFLINE }
    val openSocial = if (isGuest) onUpgradeGuest else onOpenFriends
    var launchAction by remember { mutableStateOf<HomeLaunchAction?>(null) }
    launchAction?.let { action ->
        GameModePickerDialog(
            title = when (action) {
                HomeLaunchAction.CREATE_ROOM -> "Tạo phòng mới"
                HomeLaunchAction.CASUAL -> "Chọn chế độ đấu thường"
                HomeLaunchAction.RANKED -> "Chọn chế độ xếp hạng"
            },
            playerLevel = profile?.progression?.level ?: 1,
            onDismiss = { launchAction = null },
            onSelect = { mode ->
                launchAction = null
                when (action) {
                    HomeLaunchAction.CASUAL -> onQuickMatch(mode, MatchType.CASUAL)
                    HomeLaunchAction.RANKED -> onQuickMatch(mode, MatchType.RANKED)
                    HomeLaunchAction.CREATE_ROOM -> onChooseMode(mode, true)
                }
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("home_screen")) {
        val compactHeight = maxHeight < 600.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ArcadePlayerSummary(
                displayName = displayName,
                avatarId = avatarId,
                frameId = equippedFrameId,
                level = profile?.progression?.level ?: 1,
                currentXp = profile?.progression?.currentLevelExperience ?: 0,
                nextXp = profile?.progression?.nextLevelExperience ?: 100,
                elo = elo,
                onOpenProfile = if (isGuest) onUpgradeGuest else onOpenProfile
            )

            val heroShape = RoundedCornerShape(if (compactHeight) 22.dp else 28.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(heroShape)
                    .background(
                        Brush.linearGradient(
                            listOf(ArcadePalette.Blue700, ArcadePalette.Violet600)
                        ),
                        heroShape
                    )
                    .border(2.dp, ArcadePalette.Blue300.copy(alpha = 0.75f), heroShape)
            ) {
                Image(
                    painter = painterResource(Res.drawable.arcade_home_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    ArcadePalette.Navy900.copy(alpha = 0.94f),
                                    ArcadePalette.Navy900.copy(alpha = 0.72f),
                                    ArcadePalette.Navy900.copy(alpha = 0.18f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(if (compactHeight) 16.dp else 22.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 8.dp else 12.dp)
                ) {
                    val season = profile?.progression?.season
                    Text(
                        if (isGuest) "CHẾ ĐỘ KHÁCH"
                        else if (elo == null) "ĐANG ĐỒNG BỘ DỮ LIỆU"
                        else buildString {
                            if (season != null && season.placementMatchesPlayed < season.placementMatchesRequired) {
                                append("PHÂN HẠNG ").append(season.placementMatchesPlayed)
                                    .append('/').append(season.placementMatchesRequired)
                            } else if (season != null) {
                                append(season.tier.uppercase())
                            } else {
                                append("ELO ").append(elo)
                            }
                            rank?.let { append("  •  Hạng #").append(it) }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = ArcadePalette.Gold400
                    )
                    Text(
                        "Chọn cách thi đấu",
                        style = if (compactHeight) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        if (isGuest) "Đăng ký tài khoản để ghép đối thủ trực tuyến."
                        else "Đấu thường để luyện tập hoặc xếp hạng để tăng Elo.",
                        color = Color.White.copy(alpha = 0.82f)
                    )
                    Button(
                        onClick = if (isGuest) onUpgradeGuest else ({ launchAction = HomeLaunchAction.RANKED }),
                        modifier = Modifier.fillMaxWidth()
                            .height(if (compactHeight) 50.dp else 58.dp)
                            .testTag("home_quick_match"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArcadePalette.Gold400,
                            contentColor = ArcadePalette.Navy900
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text(
                            if (isGuest) "  ĐĂNG NHẬP ĐỂ CHƠI" else "  CHƠI NGAY",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeHomeAction(
                    title = "Tạo phòng",
                    subtitle = "Mời bạn bè",
                    icon = Icons.Default.Add,
                    color = ArcadePalette.Mint600,
                    onClick = { launchAction = HomeLaunchAction.CREATE_ROOM },
                    modifier = Modifier.weight(1f).testTag("home_action:Tạo phòng")
                )
                ArcadeHomeAction(
                    title = "Tham gia",
                    subtitle = "Nhập mã phòng",
                    icon = Icons.Default.MeetingRoom,
                    color = ArcadePalette.Blue500,
                    onClick = onOpenRooms,
                    modifier = Modifier.weight(1f).testTag("home_action:Vào phòng")
                )
            }

            if (!isGuest && profile != null) {
                DailyCheckInCard(
                    checkIn = profile.progression.dailyCheckIn,
                    isClaiming = state.isDailyCheckInClaiming,
                    onClaim = onClaimDailyCheckIn
                )
            }

            SectionTitle("Khám phá", "Tính năng và hoạt động")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeQuickAction(
                        "Bạn bè",
                        if (isGuest) "Đăng ký để kết bạn" else "$onlineFriends người trực tuyến",
                        Icons.Default.Group,
                        ArcadePalette.Violet600,
                        openSocial,
                        Modifier.weight(1f)
                    )
                    HomeQuickAction(
                        "Xếp hạng",
                        rank?.let { "Bạn đang hạng #$it" } ?: "Xem bảng Elo",
                        Icons.Default.EmojiEvents,
                        ArcadePalette.Gold500,
                        onOpenLeaderboard,
                        Modifier.weight(1f)
                    )
                }
                HomeQuickAction(
                    "Đấu giải",
                    if (isGuest) "Đăng ký để tham gia" else "Giải riêng 4 người loại trực tiếp",
                    Icons.Default.EmojiEvents,
                    ArcadePalette.Coral600,
                    if (isGuest) onUpgradeGuest else onOpenTournament,
                    Modifier.fillMaxWidth().testTag("home_tournament")
                )
                HomeQuickAction(
                    "Cửa hàng",
                    "Mở khóa mặt bài, bàn số và vật phẩm mới",
                    Icons.Default.ShoppingCart,
                    ArcadePalette.Violet600,
                    onOpenShop,
                    Modifier.fillMaxWidth().testTag("home_shop")
                )
                HomeQuickAction(
                    "Luyện tập offline",
                    "Rèn tốc độ với bàn 50 số, không ảnh hưởng Elo",
                    Icons.Default.FitnessCenter,
                    ArcadePalette.Mint600,
                    onOpenPractice,
                    Modifier.fillMaxWidth()
                )
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
private fun ArcadePlayerSummary(
    displayName: String,
    avatarId: String?,
    frameId: String,
    level: Int,
    currentXp: Int,
    nextXp: Int,
    elo: Int?,
    onOpenProfile: () -> Unit
) {
    Surface(
        onClick = onOpenProfile,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ArcadePalette.Navy800,
        contentColor = Color.White,
        border = BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.55f)),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlayerAvatar(
                displayName = displayName,
                avatarId = avatarId,
                frameId = frameId,
                size = 76.dp
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ArcadePalette.Violet600,
                        contentColor = Color.White
                    ) {
                        Text(
                            "Cấp $level",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { currentXp.toFloat() / nextXp.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ArcadePalette.Gold400,
                    trackColor = ArcadePalette.Navy950
                )
                Text(
                    "$currentXp/$nextXp XP${elo?.let { "  •  Elo $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.76f)
                )
            }
        }
    }
}

@Composable
private fun ArcadeHomeAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(18.dp),
        color = color,
        contentColor = Color.White,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.32f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                Icon(icon, null, modifier = Modifier.padding(9.dp).size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DailyCheckInCard(
    checkIn: DailyCheckInSnapshot,
    isClaiming: Boolean,
    onClaim: () -> Unit
) {
    val rewardCardHeight = if (LocalDensity.current.fontScale >= 1.3f) 104.dp else 76.dp
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_check_in_card")
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Column {
                        Text("Điểm danh ngày ${checkIn.cycleDay}/7", fontWeight = FontWeight.Bold)
                        Text(
                            "Chuỗi ${checkIn.currentStreak} ngày • Tốt nhất ${checkIn.bestStreak}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (checkIn.claimedToday) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Đã điểm danh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DAILY_CHECK_IN_REWARDS_XP.forEachIndexed { index, rewardXp ->
                    val day = index + 1
                    val rewardGold = DAILY_CHECK_IN_REWARDS_GOLD[index]
                    val rewardGems = DAILY_CHECK_IN_REWARDS_GEMS[index]
                    val completed = day < checkIn.cycleDay || (day == checkIn.cycleDay && checkIn.claimedToday)
                    val current = day == checkIn.cycleDay
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(rewardCardHeight)
                            .testTag("daily_reward_day_$day"),
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            current -> MaterialTheme.colorScheme.primary
                            completed -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        },
                        contentColor = if (current) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("N$day", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = GoldColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(rewardGold.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Text("+$rewardXp XP", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.height(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (rewardGems > 0) {
                                    Icon(
                                        Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = GemColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(rewardGems.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (!checkIn.claimedToday) {
                Button(
                    onClick = onClaim,
                    enabled = !isClaiming,
                    modifier = Modifier.fillMaxWidth().testTag("daily_check_in_claim")
                ) {
                    if (isClaiming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            buildString {
                                append("Nhận ${checkIn.todayRewardGold} Vàng • ${checkIn.todayRewardXp} XP")
                                if (checkIn.todayRewardGems > 0) append(" • ${checkIn.todayRewardGems} Gem")
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
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
@Suppress("UNUSED_PARAMETER")
internal fun FastToWinBottomBar(
    selected: MainTab,
    friendNotificationCount: Int,
    onHome: () -> Unit,
    onRooms: () -> Unit,
    onLeaderboard: () -> Unit,
    onClan: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bottom_bar"),
        color = ArcadePalette.Navy900,
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .height(68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(
                tab = MainTab.HOME,
                label = "Trang chủ",
                icon = Icons.Default.Home,
                selected = selected == MainTab.HOME,
                onClick = onHome,
                modifier = Modifier.weight(1f)
            )
            BottomBarItem(
                tab = MainTab.ROOMS,
                label = "Phòng",
                icon = Icons.Default.MeetingRoom,
                selected = selected == MainTab.ROOMS,
                onClick = onRooms,
                modifier = Modifier.weight(1f)
            )
            BottomBarItem(
                MainTab.LEADERBOARD,
                "Xếp hạng",
                Icons.Default.EmojiEvents,
                selected == MainTab.LEADERBOARD,
                onLeaderboard,
                Modifier.weight(1f)
            )
            BottomBarItem(
                MainTab.CLAN,
                "Bang hội",
                CrossedSwordsIcon,
                selected == MainTab.CLAN,
                onClan,
                Modifier.weight(1f)
            )
            BottomBarItem(
                MainTab.ACCOUNT,
                "Tài khoản",
                Icons.Default.Person,
                selected == MainTab.ACCOUNT,
                onAccount,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: MainTab,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemColor = if (selected) ArcadePalette.Gold400 else Color.White.copy(alpha = 0.72f)
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .testTag("bottom_tab:${tab.name.lowercase()}")
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
            },
        shape = RoundedCornerShape(0.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 30.dp, height = 3.dp)
                    .background(
                        if (selected) ArcadePalette.Gold400 else Color.Transparent,
                        CircleShape
                    )
            )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = itemColor
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = itemColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomeQuickAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .height(92.dp)
            .testTag("home_action:$title")
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.padding(9.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
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
    playerLevel: Int = 1,
    onSelect: (GameMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GameMode.entries.filterNot(GameMode::isLegacy).forEach { mode ->
                    val unlocked = playerLevel >= mode.unlockLevel
                    ModeChoice(
                        title = mode.title,
                        subtitle = if (unlocked) mode.description else "Mở khóa ở cấp ${mode.unlockLevel}",
                        icon = if (mode == GameMode.TIME_BONUS || mode == GameMode.SPEED_UP) {
                            Icons.Rounded.Timer
                        } else {
                            Icons.Rounded.Bolt
                        },
                        enabled = unlocked
                    ) { onSelect(mode) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
private fun ModeChoice(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
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

private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCONNECTED -> "Chưa kết nối máy chủ"
    ConnectionStatus.CONNECTING -> "Đang kết nối máy chủ..."
    ConnectionStatus.AUTHENTICATING -> "Đang xác thực tài khoản..."
    ConnectionStatus.CONNECTED -> "Đã kết nối"
    ConnectionStatus.RECONNECTING -> "Mất kết nối, đang thử lại..."
}
