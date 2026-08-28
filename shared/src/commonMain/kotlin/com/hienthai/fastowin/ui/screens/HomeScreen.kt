package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
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
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.GemColor
import com.hienthai.fastowin.ui.components.GoldColor
import com.hienthai.fastowin.ui.components.CrossedSwordsIcon
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay

internal enum class MainTab { HOME, ROOMS, LEADERBOARD, CLAN, ACCOUNT }

@Composable
internal fun HomeDashboard(
    state: GameState,
    isGuest: Boolean,
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
    var showMatchTypePicker by remember { mutableStateOf(false) }
    var pendingMatchType by remember { mutableStateOf<MatchType?>(null) }
    if (showMatchTypePicker) {
        MatchTypePickerDialog(
            title = "Chọn loại trận",
            onDismiss = { showMatchTypePicker = false },
            onSelect = { matchType ->
                showMatchTypePicker = false
                pendingMatchType = matchType
            }
        )
    }
    pendingMatchType?.let { matchType ->
        GameModePickerDialog(
            title = if (matchType == MatchType.RANKED) {
                "Chọn chế độ xếp hạng"
            } else {
                "Chọn chế độ đấu thường"
            },
            playerLevel = profile?.progression?.level ?: 1,
            onDismiss = { pendingMatchType = null },
            onSelect = { mode ->
                pendingMatchType = null
                onQuickMatch(mode, matchType)
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("home_screen")) {
        val compactHeight = maxHeight < 600.dp
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            ArcadePlayerSummary(
                displayName = displayName,
                avatarId = avatarId,
                frameId = equippedFrameId,
                level = profile?.progression?.level ?: 1,
                currentXp = profile?.progression?.currentLevelExperience ?: 0,
                nextXp = profile?.progression?.nextLevelExperience ?: 100,
                elo = elo,
                tier = profile?.progression?.season?.tier,
                onOpenProfile = if (isGuest) onUpgradeGuest else onOpenProfile
            )

            val season = profile?.progression?.season
            HomeMatchHero(
                kicker = when {
                    isGuest -> "CHẾ ĐỘ KHÁCH"
                    season != null -> season.name.uppercase()
                    else -> "XẾP HẠNG"
                },
                description = if (isGuest) {
                    "Đăng ký tài khoản để ghép đối thủ trực tuyến."
                } else {
                    "Đấu thường để luyện tập hoặc xếp hạng để tăng Elo."
                },
                compact = compactHeight
            )
            ArcadeActionButton(
                label = if (isGuest) "ĐĂNG NHẬP ĐỂ CHƠI" else "CHƠI NGAY",
                icon = Icons.Default.PlayArrow,
                style = ArcadeActionStyle.GOLD,
                onClick = if (isGuest) onUpgradeGuest else ({ showMatchTypePicker = true }),
                modifier = Modifier.fillMaxWidth().testTag("home_quick_match")
            )

            HomeRoomActions(
                onCreateRoom = onOpenRooms,
                onOpenRooms = onOpenRooms
            )

            if (!isGuest && profile != null) {
                DailyCheckInCard(
                    checkIn = profile.progression.dailyCheckIn,
                    isClaiming = state.isDailyCheckInClaiming,
                    onClaim = onClaimDailyCheckIn
                )
            }

            SectionTitle("Khám phá", "Tính năng và hoạt động")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeDiscoveryHighlights(
                    isGuest = isGuest,
                    onlineFriends = onlineFriends,
                    rank = rank,
                    openSocial = openSocial,
                    onOpenLeaderboard = onOpenLeaderboard
                )
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
            Spacer(modifier = Modifier.height(8.dp))
            if (!isGuest) {
                DelayedConnectionNotice(state.connectionStatus)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (isGuest) {
                ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Mint600) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LƯU TIẾN TRÌNH", fontWeight = FontWeight.Black)
                        Text(
                            "Tạo tài khoản để giữ Elo, lịch sử và bạn bè trên Android/iOS.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArcadeActionButton(
                                label = "TẠO TÀI KHOẢN",
                                onClick = onUpgradeGuest,
                                modifier = Modifier.fillMaxWidth(),
                                style = ArcadeActionStyle.GOLD
                            )
                            ArcadeActionButton(
                                label = "THOÁT CHẾ ĐỘ KHÁCH",
                                onClick = onLogout,
                                modifier = Modifier.fillMaxWidth(),
                                style = ArcadeActionStyle.OUTLINE
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMatchHero(
    kicker: String,
    description: String,
    compact: Boolean
) {
    val shape = RoundedCornerShape(if (compact) 20.dp else 24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 138.dp else 148.dp)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF184DB9),
                        Color(0xFF763FDA),
                        Color(0xFFC94988)
                    )
                ),
                shape
            )
            .border(1.dp, ArcadePalette.Blue300.copy(alpha = 0.72f), shape)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 20.dp)
                .size(56.dp)
                .background(Color.White.copy(alpha = 0.09f), CircleShape)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                kicker,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFE28B)
            )
            Text(
                "Chọn cách thi đấu",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE5EDFF),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 14.dp)
                .size(if (compact) 68.dp else 76.dp)
                .rotate(8f)
                .background(
                    Brush.linearGradient(listOf(Color(0xFFFFE36F), Color(0xFFFF8D37))),
                    RoundedCornerShape(23.dp)
                )
                .border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(23.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                CrossedSwordsIcon,
                contentDescription = null,
                tint = ArcadePalette.Navy800,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun HomeRoomActions(
    onCreateRoom: () -> Unit,
    onOpenRooms: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArcadeActionButton(
            label = "TẠO PHÒNG",
            icon = Icons.Default.Add,
            onClick = onCreateRoom,
            modifier = Modifier.fillMaxWidth().testTag("home_action:Tạo phòng")
        )
        ArcadeActionButton(
            label = "THAM GIA",
            icon = Icons.Default.MeetingRoom,
            style = ArcadeActionStyle.OUTLINE,
            onClick = onOpenRooms,
            modifier = Modifier.fillMaxWidth().testTag("home_action:Vào phòng")
        )
    }
}

@Composable
private fun HomeDiscoveryHighlights(
    isGuest: Boolean,
    onlineFriends: Int,
    rank: Int?,
    openSocial: () -> Unit,
    onOpenLeaderboard: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stack = maxWidth < 350.dp || LocalDensity.current.fontScale >= 1.3f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeQuickAction(
                    "Bạn bè",
                    if (isGuest) "Đăng ký để kết bạn" else "$onlineFriends người trực tuyến",
                    Icons.Default.Group,
                    ArcadePalette.Violet600,
                    openSocial,
                    Modifier.fillMaxWidth()
                )
                HomeQuickAction(
                    "Xếp hạng",
                    rank?.let { "Bạn đang hạng #$it" } ?: "Xem bảng Elo",
                    Icons.Default.EmojiEvents,
                    ArcadePalette.Gold500,
                    onOpenLeaderboard,
                    Modifier.fillMaxWidth()
                )
            }
        } else {
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
    tier: String?,
    onOpenProfile: () -> Unit
) {
    Surface(
        onClick = onOpenProfile,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ArcadePalette.Navy800,
        contentColor = Color.White,
        border = BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.55f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlayerAvatar(
                displayName = displayName,
                avatarId = avatarId,
                frameId = frameId,
                size = 58.dp
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
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
                Text(
                    buildString {
                        append(tier?.replaceFirstChar { it.uppercase() } ?: "Đang phân hạng")
                        elo?.let { append("  •  Elo ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.76f)
                )
                LinearProgressIndicator(
                    progress = { currentXp.toFloat() / nextXp.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = ArcadePalette.Mint400,
                    trackColor = ArcadePalette.Navy950
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
    val rewardCardHeight = if (LocalDensity.current.fontScale >= 1.3f) 82.dp else 62.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_check_in_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Điểm danh 7 ngày",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                "Chuỗi ${checkIn.currentStreak} ngày",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            DAILY_CHECK_IN_REWARDS_XP.forEachIndexed { index, _ ->
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
                    shape = RoundedCornerShape(11.dp),
                    color = when {
                        current -> Color(0xFF44371E)
                        completed -> ArcadePalette.Navy800.copy(alpha = 0.62f)
                        else -> ArcadePalette.Navy800
                    },
                    contentColor = Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (current) ArcadePalette.Gold400 else ArcadePalette.OutlineDark.copy(alpha = 0.72f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "Ngày",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = if (completed) 0.55f else 0.72f),
                            maxLines = 1
                        )
                        Text(
                            day.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = if (completed) 0.62f else 1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (rewardGems > 0) Icons.Default.Payments else Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = if (rewardGems > 0) GemColor else GoldColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                (if (rewardGems > 0) rewardGems else rewardGold).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = if (completed) 0.62f else 1f)
                            )
                        }
                    }
                }
            }
        }

        if (!checkIn.claimedToday) {
            ArcadeActionButton(
                label = "NHẬN THƯỞNG NGÀY ${checkIn.cycleDay}",
                onClick = onClaim,
                enabled = !isClaiming,
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth().testTag("daily_check_in_claim"),
                content = if (isClaiming) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ArcadePalette.Navy900
                        )
                    }
                } else {
                    null
                }
            )
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
            .heightIn(min = 92.dp)
            .testTag("home_action:$title")
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .testTag("home_action_content:$title"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
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
    ArcadeDialog(
        title = title.uppercase(),
        subtitle = "Chế độ khó hơn sẽ được mở khóa theo cấp độ.",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GameMode.entries.filter { !it.isLegacy && it != GameMode.TEAM_2V2 }.forEach { mode ->
                val unlocked = playerLevel >= mode.unlockLevel
                ModeChoice(
                    title = mode.title,
                    subtitle = if (unlocked) mode.description else "Mở khóa ở cấp ${mode.unlockLevel}",
                    icon = mode.modeIcon(),
                    enabled = unlocked,
                    modifier = Modifier.testTag("game_mode:${mode.name}")
                ) { onSelect(mode) }
            }
        }
        ArcadeActionButton(
            label = "ĐÓNG",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.OUTLINE
        )
    }
}

@Composable
internal fun MatchTypePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelect: (MatchType) -> Unit
) {
    ArcadeDialog(
        title = title.uppercase(),
        subtitle = "Chọn cách trận đấu được tính kết quả.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeChoice(
                title = "Đấu thường",
                subtitle = "Luyện kỹ năng, không ảnh hưởng Elo",
                icon = CrossedSwordsIcon,
                enabled = true,
                modifier = Modifier.testTag("match_type:CASUAL"),
                onClick = { onSelect(MatchType.CASUAL) }
            )
            ModeChoice(
                title = "Đấu xếp hạng",
                subtitle = "Thắng hoặc thua sẽ thay đổi Elo",
                icon = Icons.Default.EmojiEvents,
                enabled = true,
                modifier = Modifier.testTag("match_type:RANKED"),
                onClick = { onSelect(MatchType.RANKED) }
            )
        }
        ArcadeActionButton(
            label = "ĐÓNG",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.OUTLINE
        )
    }
}

@Composable
private fun ModeChoice(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp).alpha(if (enabled) 1f else 0.58f),
        shape = RoundedCornerShape(16.dp),
        color = ArcadePalette.Navy800,
        contentColor = Color.White,
        border = BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (enabled) ArcadePalette.Blue700 else ArcadePalette.Navy700
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(24.dp), tint = ArcadePalette.Blue100)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA9BADC))
            }
            Icon(
                imageVector = if (enabled) Icons.Default.ChevronRight else Icons.Default.Lock,
                contentDescription = null,
                tint = if (enabled) ArcadePalette.Blue300 else Color(0xFF7E91B7)
            )
        }
    }
}

internal fun GameMode.modeIcon(): ImageVector = when (this) {
    GameMode.ORDER -> Icons.Rounded.Bolt
    GameMode.RANDOM_TARGET -> Icons.Rounded.Shuffle
    GameMode.TIME_BONUS, GameMode.TIME_ATTACK -> Icons.Rounded.Timer
    GameMode.SPEED_UP -> Icons.Rounded.Speed
    GameMode.SURVIVAL -> Icons.Rounded.Favorite
    GameMode.COMBO -> Icons.Rounded.LocalFireDepartment
    GameMode.TEAM_2V2 -> Icons.Default.Group
}

private fun connectionLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DISCONNECTED -> "Chưa kết nối máy chủ"
    ConnectionStatus.CONNECTING -> "Đang kết nối máy chủ..."
    ConnectionStatus.AUTHENTICATING -> "Đang xác thực tài khoản..."
    ConnectionStatus.CONNECTED -> "Đã kết nối"
    ConnectionStatus.RECONNECTING -> "Mất kết nối, đang thử lại..."
}
