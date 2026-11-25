package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.ui.components.LocalNewOnlineIntentAllowed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.hienthai.fastowin.ui.components.AppOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton
import com.hienthai.fastowin.ui.components.ArcadeChoiceChip
import com.hienthai.fastowin.ui.components.A1Dimens
import com.hienthai.fastowin.ui.components.A1FieldLabel
import com.hienthai.fastowin.ui.components.A1BottomSheet
import com.hienthai.fastowin.ui.components.A1HeroTone
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.A1Tag
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.AddCircle
import com.hienthai.fastowin.ui.components.A1LevelBadge
import com.hienthai.fastowin.ui.components.drawLightRays
import com.hienthai.fastowin.ui.components.a1Hills
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.draw.drawBehind
import com.hienthai.fastowin.ui.components.A1OutlinedText
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.theme.A1Palette
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.state.AvailableRoom
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.HeaderRefreshAction
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeIconHero
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LobbyScreen(
    state: GameState,
    onModeSelected: (GameMode) -> Unit,
    onStartMatchmaking: (GameMode, MatchType) -> Unit,
    onCancelMatchmaking: () -> Unit,
    onOpenRoomBrowser: (String) -> Unit,
    onCreateRoom: (GameMode, MatchType, String, String) -> Unit,
    onJoinRoom: (String, String) -> Unit,
    onLeaveRoom: () -> Unit,
    onSetReady: (Boolean) -> Unit,
    onKickOpponent: () -> Unit,
    onRefreshRooms: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    onBackToMode: () -> Unit,
    onLogout: () -> Unit,
    isGuest: Boolean,
    onUpgradeGuest: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenGifts: () -> Unit = {},
    unclaimedGiftCount: Long = 0,
    rewardEventState: com.hienthai.fastowin.state.RewardEventUiState? = null,
    announcementState: com.hienthai.fastowin.state.AnnouncementState? = null,
    onOpenAnnouncement: (String) -> Unit = {},
    onHideAnnouncement: (String) -> Unit = {},
    onOpenRewardEvents: () -> Unit = {},
    onRefreshRewardEvents: () -> Unit = {},
    onOpenClan: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTournament: () -> Unit,
    onOpenShop: () -> Unit,
    onShareRoom: (String, String) -> Result<Unit>,
    onResolveRoomLink: (String?) -> Unit,
    onClaimDailyCheckIn: () -> Unit,
    onDismissDailyCheckInReward: () -> Unit = {},
    onPlayQuotaExhausted: () -> Unit = {},
    serverUrl: String = "",
    modifier: Modifier = Modifier,
    onOpenMissions: () -> Unit = {},
    claimableMissionCount: Int = 0,
    onOpenCheckInDetails: (String) -> Unit = {}
) {
    val hasBottomNavigation = state.lobbyStage == LobbyStage.SELECT_MODE ||
        state.lobbyStage == LobbyStage.ROOM_BROWSER
    // Tab "Chơi" (A1) mở màn Chọn chế độ ngay trên sảnh; Tạo/Vào phòng ở Trang chủ vẫn mở danh sách phòng.
    var showModeSelect by rememberSaveable { mutableStateOf(false) }
    val modeSelectVisible = showModeSelect && state.lobbyStage == LobbyStage.SELECT_MODE
    val displayName = (state.profile?.displayName ?: state.player.name).ifBlank { localized(TextKey.Player) }
    val localization = LocalLocalization.current
    var isRoomListRefreshing by remember { mutableStateOf(false) }
    val roomRefreshScope = rememberCoroutineScope()
    val refreshRooms = {
        if (!state.isSearching && !isRoomListRefreshing) {
            isRoomListRefreshing = true
            onRefreshRooms()
            roomRefreshScope.launch {
                delay(800)
                isRoomListRefreshing = false
            }
        }
    }
    val openRoomBrowserAction = {
        if (isGuest && state.player.name.isBlank()) {
            onModeSelected(state.gameMode)
        } else {
            onOpenRoomBrowser(displayName)
        }
    }
    SystemBackHandler(enabled = modeSelectVisible) { showModeSelect = false }
    SystemBackHandler(enabled = state.lobbyStage != LobbyStage.SELECT_MODE) {
        when (state.lobbyStage) {
            LobbyStage.SELECT_MODE -> Unit
            LobbyStage.ENTER_NAME, LobbyStage.ROOM_BROWSER -> {
                onBackToMode()
            }
            LobbyStage.ROOM_WAITING, LobbyStage.MATCHED -> onLeaveRoom()
            LobbyStage.MATCHMAKING -> onCancelMatchmaking()
        }
    }
    // Consume the keyboard once for the list and bottom bar; sibling inset padding adds twice.
    Column(modifier = modifier.fillMaxSize().imePadding().testTag("lobby_screen")) {
        // A1: màn Tìm trận không có thanh trên cùng, chỉ có nhãn kiểu trận.
        if (state.lobbyStage != LobbyStage.MATCHMAKING && state.lobbyStage != LobbyStage.MATCHED) FastToWinHeader(
            title = when (state.lobbyStage) {
                // A1: trang chủ không có tiêu đề; lời chào nằm trong bong bóng của linh vật.
                LobbyStage.SELECT_MODE -> if (modeSelectVisible) localized(TextKey.ModeSelectTitle) else ""
                LobbyStage.ENTER_NAME -> localized(TextKey.DisplayName)
                LobbyStage.ROOM_BROWSER -> localized(TextKey.Rooms)
                LobbyStage.ROOM_WAITING -> state.currentRoomName ?: localized(TextKey.Rooms)
                LobbyStage.MATCHMAKING -> localized(TextKey.Matchmaking)
                LobbyStage.MATCHED -> localized(TextKey.MatchFound)
            },
            subtitle = null,
            gold = state.profile?.progression?.gold ?: 0,
            gems = state.profile?.progression?.gems ?: 0,
            unreadNotifications = state.unreadNotificationCount,
            onNotifications = onOpenNotifications,
            // Theo thiết kế: phòng chờ chỉ có nút quay lại + tên phòng; danh sách phòng có ô tiền, không có chuông.
            showBalances = !modeSelectVisible && state.lobbyStage != LobbyStage.ROOM_WAITING,
            showNotifications = state.lobbyStage == LobbyStage.SELECT_MODE && !modeSelectVisible,
            onBack = when (state.lobbyStage) {
                LobbyStage.SELECT_MODE -> if (modeSelectVisible) ({ showModeSelect = false }) else null
                LobbyStage.ROOM_BROWSER -> onBackToMode
                LobbyStage.MATCHED -> null
                LobbyStage.ENTER_NAME -> onBackToMode
                LobbyStage.ROOM_WAITING -> onLeaveRoom
                LobbyStage.MATCHMAKING -> onCancelMatchmaking
            },
            actions = {
                if (state.lobbyStage == LobbyStage.ROOM_BROWSER) {
                    HeaderRefreshAction(
                        isRefreshing = isRoomListRefreshing || state.isSearching,
                        onRefresh = refreshRooms
                    )
                }
            }
        )
        ResponsiveScreen(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (hasBottomNavigation) {
                            WindowInsetsSides.Horizontal
                        } else {
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        }
                    )
                ),
            maxContentWidth = 760.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Box(
                modifier = contentModifier.then(
                    if (state.lobbyStage == LobbyStage.SELECT_MODE) Modifier else Modifier.padding(vertical = 16.dp)
                ),
                // Màn Tìm trận bám mép trên như thiết kế; các màn khác giữ căn giữa.
                contentAlignment = if (state.lobbyStage == LobbyStage.MATCHMAKING) Alignment.TopCenter else Alignment.Center
            ) {
                when (state.lobbyStage) {
                LobbyStage.SELECT_MODE -> if (modeSelectVisible) ModeSelectContent(
                    playerLevel = state.profile?.progression?.level ?: 1,
                    quota = state.playQuota,
                    onPlay = { mode, matchType ->
                        if (isGuest) onUpgradeGuest() else onStartMatchmaking(mode, matchType)
                    },
                    onPractice = onOpenPractice,
                    onQuotaExhausted = onPlayQuotaExhausted
                ) else HomeDashboard(
                    state = state,
                    isGuest = isGuest,
                    serverUrl = serverUrl,
                    onQuickMatch = onStartMatchmaking,
                    onOpenRooms = openRoomBrowserAction,
                    onOpenFriends = onOpenFriends,
                    onOpenLeaderboard = if (isGuest) onUpgradeGuest else onOpenLeaderboard,
                    onOpenProfile = onOpenProfile,
                    onOpenNotifications = onOpenNotifications,
                    onOpenGifts = onOpenGifts,
                    onOpenMissions = onOpenMissions,
                    claimableMissionCount = claimableMissionCount,
                    onOpenCheckInDetails = onOpenCheckInDetails,
                    unclaimedGiftCount = if (isGuest) 0 else unclaimedGiftCount,
                    rewardEventState = rewardEventState,
                    announcementState = announcementState,
                    onOpenAnnouncement = onOpenAnnouncement,
                    onHideAnnouncement = onHideAnnouncement,
                    onOpenRewardEvents = onOpenRewardEvents,
                    onRefreshRewardEvents = onRefreshRewardEvents,
                    onOpenClan = onOpenClan,
                    onOpenPractice = onOpenPractice,
                    onOpenTournament = onOpenTournament,
                    onOpenShop = onOpenShop,
                    onClaimDailyCheckIn = onClaimDailyCheckIn,
                    onDismissDailyCheckInReward = onDismissDailyCheckInReward,
                    onUpgradeGuest = onUpgradeGuest,
                    onLogout = onLogout,
                    onPlayQuotaExhausted = onPlayQuotaExhausted
                )
                LobbyStage.ENTER_NAME -> NameEntry(onOpenRoomBrowser)
                LobbyStage.ROOM_BROWSER -> RoomBrowser(
                    state = state,
                    onCreateRoom = onCreateRoom,
                    onJoinRoom = onJoinRoom,
                    onRefreshRooms = onRefreshRooms,
                    isRefreshing = isRoomListRefreshing || state.isSearching,
                    onRefresh = refreshRooms,
                    onOpenProfile = onOpenProfile,
                    onOpenFriends = onOpenFriends,
                    isGuest = isGuest,
                    onUpgradeGuest = onUpgradeGuest,
                    onResolveRoomLink = onResolveRoomLink,
                    onPlayQuotaExhausted = onPlayQuotaExhausted
                )
                LobbyStage.ROOM_WAITING -> RoomWaiting(
                    state = state,
                    onLeaveRoom = onLeaveRoom,
                    onOpenFriends = onOpenFriends,
                    onSetReady = onSetReady,
                    onKickOpponent = onKickOpponent,
                    onOpenFriendProfile = onOpenFriendProfile,
                    onShareRoom = onShareRoom,
                    isGuest = isGuest
                )
                LobbyStage.MATCHMAKING -> MatchmakingScreen(state, onCancelMatchmaking)
                LobbyStage.MATCHED -> MatchedStatus(state, onOpenFriendProfile)
                }
            }
        }
        if (hasBottomNavigation) {
            FastToWinBottomBar(
                selected = when {
                    modeSelectVisible -> MainTab.ROOMS
                    state.lobbyStage == LobbyStage.ROOM_BROWSER -> MainTab.ROOMS
                    else -> MainTab.HOME
                },
                friendNotificationCount = 0,
                unclaimedGiftCount = if (isGuest) 0 else unclaimedGiftCount,
                onHome = {
                    showModeSelect = false
                    if (state.lobbyStage == LobbyStage.ROOM_BROWSER) onBackToMode()
                },
                onRooms = {
                    if (state.lobbyStage == LobbyStage.ROOM_BROWSER) onBackToMode()
                    showModeSelect = true
                },
                onLeaderboard = if (isGuest) onUpgradeGuest else onOpenLeaderboard,
                onClan = if (isGuest) onUpgradeGuest else onOpenClan,
                onAccount = if (isGuest) onUpgradeGuest else onOpenProfile
            )
        }
    }
}

/**
 * Màn Tìm trận theo thiết kế A1: nhãn kiểu trận, thẻ người chơi, chữ VS với vòng sóng lan ra,
 * thẻ "Đang tìm đối thủ…" (khoảng Elo + đồng hồ), ghi chú và nút HỦY.
 */
@Composable
private fun MatchmakingScreen(state: GameState, onCancel: () -> Unit) {
    var elapsedSeconds by remember(state.matchmakingStartedAtMillis) { mutableStateOf(0L) }
    LaunchedEffect(state.matchmakingStartedAtMillis) {
        while (true) {
            val startedAt = state.matchmakingStartedAtMillis ?: epochMillis()
            elapsedSeconds = ((epochMillis() - startedAt) / 1_000L).coerceAtLeast(0L)
            delay(1_000)
        }
    }
    val expandedRange = (100 + (elapsedSeconds / 10L).toInt() * 50).coerceAtMost(300)
    val ranked = state.matchType == MatchType.RANKED
    val profile = state.profile
    val elo = profile?.statistics?.eloRating
    val displayName = (profile?.displayName ?: state.player.name).ifBlank { localized(TextKey.Player) }
    val frameId = profile?.progression?.cosmetics?.firstOrNull { it.type == com.hienthai.fastowin.protocol.CosmeticType.FRAME && it.equipped }?.id
        ?: state.player.frameId
    val timer = "${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}"

    // Phần trên cuộn được; ghi chú + nút HỦY luôn nằm sát đáy như thiết kế. Đồi cỏ vẽ theo cả màn.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .a1Hills(bushes = false)
            .testTag("matchmaking_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "${localized(if (ranked) TextKey.Ranked else TextKey.Casual)} · ${state.gameMode.displayName()}",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier
                .background(A1Palette.Ink, RoundedCornerShape(11.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
        // Thẻ người chơi.
        Row(
            modifier = Modifier.fillMaxWidth().a1Card().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                PlayerAvatar(
                    displayName = displayName,
                    avatarId = profile?.avatarId ?: state.player.avatarId,
                    userId = profile?.userId,
                    frameId = frameId,
                    size = 76.dp
                )
                A1LevelBadge(profile?.progression?.level ?: 1, Modifier.align(Alignment.BottomEnd))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, color = A1Palette.Ink, maxLines = 1)
                Text(
                    listOfNotNull(elo?.let { "Elo $it" }, profile?.progression?.season?.tier).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = A1Palette.Muted
                )
            }
            // Nhãn "Bạn" (".tag.b" của thiết kế): Baloo đậm, cao ~24dp.
            Text(
                localized(TextKey.MatchmakingYou),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0D3A7A),
                modifier = Modifier
                    .background(Color(0xFFDCEBFF), RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF6AA4F0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
        MatchmakingVersus()
        // Thẻ đối thủ đang tìm (viền nét đứt).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .drawBehind {
                    drawRoundRect(
                        A1Palette.Line,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx())
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(A1Palette.SurfaceAlt2, RoundedCornerShape(17.dp))
                    .border(3.dp, A1Palette.Line, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = A1Palette.Muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(localized(TextKey.MatchmakingSearching), style = MaterialTheme.typography.titleLarge, color = A1Palette.Ink)
                Text(
                    if (ranked && elo != null) {
                        localized(TextKey.MatchmakingEloWindow, "min" to (elo - expandedRange), "max" to (elo + expandedRange))
                    } else localized(TextKey.NoEloImpact),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = A1Palette.Muted
                )
            }
            Text(
                timer,
                style = MaterialTheme.typography.headlineSmall,
                color = A1Palette.Ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        A1OutlinedText(
            text = localized(if (ranked) TextKey.MatchmakingRankedNote else TextKey.CasualMatchmakingHint),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            outlineWidth = 2.dp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        state.latencyMillis?.let {
            Text(connectionQualityLabel(it), style = MaterialTheme.typography.bodySmall, color = A1Palette.Ink)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            localized(TextKey.MatchmakingBotFallback),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = A1Palette.Ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().a1Card(radius = 16.dp, depth = 3.dp).padding(horizontal = 14.dp, vertical = 10.dp)
        )
        ArcadeActionButton(
            label = localized(TextKey.CancelMatchmaking),
            style = ArcadeActionStyle.OUTLINE,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().testTag("matchmaking_cancel")
        )
        Spacer(Modifier.height(8.dp))
    }
    }
}

/** Chữ "VS" đỏ viền đậm giữa ba vòng sóng trắng lan dần ra (".ring" + keyframes "ping"). */
@Composable
private fun MatchmakingVersus() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ping")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
        ),
        label = "pingT"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .drawBehind {
                drawLightRays(center, 320.dp.toPx())
                for (k in 0 until 3) {
                    val p = (t + k / 3f) % 1f
                    val d = 120.dp.toPx() + (330.dp.toPx() - 120.dp.toPx()) * p
                    drawCircle(
                        Color.White.copy(alpha = 0.67f * (1f - p)),
                        radius = d / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())
                    )
                }
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        A1OutlinedText(
            text = "VS",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
            outline = A1Palette.RedDeep,
            outlineWidth = 5.dp
        )
    }
}

@Composable
private fun NameEntry(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        ArcadeIconHero(
            kicker = localized(TextKey.GuestMode),
            title = localized(TextKey.GuestNameTitle),
            subtitle = localized(TextKey.GuestNameDescription),
            icon = Icons.Default.Person
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = ArcadePalette.Navy800,
            border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = localized(TextKey.EnterNickname),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                ArcadeActionButton(
                    label = localized(TextKey.Continue).uppercase(),
                    style = ArcadeActionStyle.GOLD,
                    onClick = { onContinue(name) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ArcadePalette.Navy800.copy(alpha = 0.82f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.5f))
        ) {
            Text(
                localized(TextKey.SaveNameProgressDescription),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA9BADC)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomBrowser(
    state: GameState,
    onCreateRoom: (GameMode, MatchType, String, String) -> Unit,
    onJoinRoom: (String, String) -> Unit,
    onRefreshRooms: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    isGuest: Boolean,
    onUpgradeGuest: () -> Unit,
    onResolveRoomLink: (String?) -> Unit,
    onPlayQuotaExhausted: () -> Unit
) {
    val localization = LocalLocalization.current
    var selectedRoom by remember { mutableStateOf<AvailableRoom?>(null) }
    val newIntentAllowed = LocalNewOnlineIntentAllowed.current
    var showCreateSheet by remember { mutableStateOf(false) }
    var showJoinCode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchTypeFilter by remember { mutableStateOf<MatchType?>(null) }
    val filteredRooms = remember(state.availableRooms, searchQuery, matchTypeFilter) {
        val query = searchQuery.trim()
        state.availableRooms.filter { room ->
            room.gameMode != GameMode.TEAM_2V2 &&
                (query.isEmpty() || room.name.contains(query, ignoreCase = true) ||
                    room.hostName.contains(query, ignoreCase = true)) &&
                (matchTypeFilter == null || room.matchType == matchTypeFilter)
        }
    }
    var visibleRoomCount by remember(searchQuery, matchTypeFilter) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleRooms = filteredRooms.take(visibleRoomCount)

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            onRefreshRooms()
        }
    }

    LaunchedEffect(state.pendingRoomLinkId, state.roomListVersion, newIntentAllowed) {
        if (!newIntentAllowed) return@LaunchedEffect
        val roomId = state.pendingRoomLinkId ?: return@LaunchedEffect
        if (state.roomListVersion <= state.pendingRoomLinkListVersion) return@LaunchedEffect
        val linkedRoom = state.availableRooms.firstOrNull { it.id == roomId }
        if (linkedRoom == null) {
            onResolveRoomLink(localization.text(TextKey.RoomLinkUnavailable))
        } else {
            onResolveRoomLink(null)
            if (linkedRoom.requiresPassword) selectedRoom = linkedRoom
            else onJoinRoom(linkedRoom.id, "")
        }
    }

    selectedRoom?.let { room ->
        JoinRoomDialog(
            room = room,
            onDismiss = { selectedRoom = null },
            onJoin = { password ->
                selectedRoom = null
                onJoinRoom(room.id, password)
            }
        )
    }
    if (showCreateSheet) {
        CreateRoomSheet(
            isLoading = state.isSearching,
            defaultRoomName = localized(TextKey.DefaultRoomName, "player" to state.player.name),
            playerLevel = state.profile?.progression?.level ?: 1,
            onDismiss = { showCreateSheet = false },
            onCreate = { mode, matchType, roomName, password ->
                if (state.playQuota?.remainingMatches == 0) {
                    showCreateSheet = false
                    onPlayQuotaExhausted()
                } else {
                    showCreateSheet = false
                    onCreateRoom(mode, matchType, roomName, password)
                }
            }
        )
    }
    if (showJoinCode) {
        JoinByCodeDialog(
            rooms = state.availableRooms,
            onDismiss = { showJoinCode = false },
            onSelect = { room ->
                showJoinCode = false
                if (room.requiresPassword) selectedRoom = room else onJoinRoom(room.id, "")
            }
        )
    }
    FastToWinPullRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("room_list"),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            state.error?.let { message ->
                item(key = "room_error") {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (isGuest) {
                item(key = "room_guest_upgrade") {
                    ArcadeActionButton(
                        label = localized(TextKey.SaveProgressEmail),
                        icon = Icons.Default.Person,
                        style = ArcadeActionStyle.OUTLINE,
                        enabled = state.connectionStatus == ConnectionStatus.CONNECTED,
                        onClick = onUpgradeGuest,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(key = "room_actions") {
                RoomBrowserActions(
                    canCreate = newIntentAllowed && state.connectionStatus == ConnectionStatus.CONNECTED && !state.isSearching,
                    onCreate = { showCreateSheet = true },
                    onJoinCode = { showJoinCode = true }
                )
            }

            item(key = "room_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = localized(TextKey.SearchRooms),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
                )
            }

            item(key = "room_filters") {
                // ".chips" của thiết kế: Tất cả · N / Xếp hạng / Thường.
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArcadeChoiceChip(
                        label = localized(TextKey.RoomsAllCount, "count" to state.availableRooms.count { it.gameMode != GameMode.TEAM_2V2 }),
                        selected = matchTypeFilter == null,
                        onClick = { matchTypeFilter = null },
                        modifier = Modifier.testTag("room_filter:all")
                    )
                    ArcadeChoiceChip(
                        label = localized(TextKey.Ranked),
                        selected = matchTypeFilter == MatchType.RANKED,
                        onClick = { matchTypeFilter = MatchType.RANKED },
                        modifier = Modifier.testTag("room_filter:ranked")
                    )
                    ArcadeChoiceChip(
                        label = localized(TextKey.CasualShort),
                        selected = matchTypeFilter == MatchType.CASUAL,
                        onClick = { matchTypeFilter = MatchType.CASUAL },
                        modifier = Modifier.testTag("room_filter:casual")
                    )
                }
            }

            when {
                state.isSearching && visibleRooms.isEmpty() -> item(key = "room_loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = A1Palette.Line)
                        Text(
                            when (state.connectionStatus) {
                                ConnectionStatus.RECONNECTING -> localized(TextKey.Reconnecting)
                                ConnectionStatus.AUTHENTICATING -> localized(TextKey.AuthenticatingSession)
                                else -> localized(TextKey.Connecting)
                            },
                            color = A1Palette.Ink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                visibleRooms.isEmpty() -> item(key = "room_empty") {
                    Text(
                        if (searchQuery.isBlank() && matchTypeFilter == null) {
                            localized(TextKey.NoWaitingRooms)
                        } else {
                            localized(TextKey.NoMatchingRooms)
                        },
                        modifier = Modifier.fillMaxWidth().a1Card(radius = 18.dp, depth = 3.dp).padding(vertical = 28.dp, horizontal = 16.dp),
                        color = A1Palette.Muted,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                else -> {
                    items(visibleRooms, key = { it.id }) { room ->
                        RoomCard(
                            room = room,
                            onClick = {
                                if (room.requiresPassword) selectedRoom = room else onJoinRoom(room.id, "")
                            }
                        )
                    }
                    item(key = "rooms_load_more") {
                        ArcadeLoadMoreButton(
                            visibleItemCount = visibleRooms.size,
                            totalItemCount = filteredRooms.size,
                            onLoadMore = {
                                visibleRoomCount = nextArcadePageItemCount(visibleRoomCount, filteredRooms.size)
                            },
                            testTag = "rooms_load_more"
                        )
                    }
                }
            }
        }
    }
}

/** Hàng nút "Tạo phòng" (xanh) + "Nhập mã" (tím) như thiết kế. */
@Composable
private fun RoomBrowserActions(
    canCreate: Boolean,
    onCreate: () -> Unit,
    onJoinCode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArcadeActionButton(
            label = localized(TextKey.CreateRoom),
            icon = Icons.Rounded.AddCircle,
            style = ArcadeActionStyle.PRIMARY,
            enabled = canCreate,
            onClick = onCreate,
            modifier = Modifier.weight(1f).testTag("create_room_open")
        )
        ArcadeActionButton(
            label = localized(TextKey.EnterCodeShort),
            icon = Icons.Rounded.Pin,
            style = ArcadeActionStyle.SPECIAL,
            onClick = onJoinCode,
            enabled = LocalNewOnlineIntentAllowed.current,
            modifier = Modifier.weight(1f).testTag("join_code_open")
        )
    }
}

/** Độ dài mã phòng hiển thị/nhập: 6 ký tự đầu của mã phòng. */
private const val ROOM_CODE_LENGTH = 6

internal fun roomCodeOf(roomId: String?): String = roomId?.replace("-", "")?.take(ROOM_CODE_LENGTH)?.uppercase().orEmpty()

/** Tìm phòng theo mã: khớp toàn bộ mã phòng, hoặc khớp duy nhất 6 ký tự đầu. */
internal fun findRoomByCode(rooms: List<AvailableRoom>, code: String): AvailableRoom? {
    val normalized = code.trim()
    if (normalized.isEmpty()) return null
    rooms.firstOrNull { it.id.equals(normalized, ignoreCase = true) }?.let { return it }
    if (normalized.length < ROOM_CODE_LENGTH) return null
    return rooms.filter { roomCodeOf(it.id).equals(normalized.take(ROOM_CODE_LENGTH), ignoreCase = true) }.singleOrNull()
}

/** Hộp thoại "Nhập mã phòng": ô icon tím, 6 ô ký tự, TÌM PHÒNG + Huỷ (thiết kế PlayFlows). */
@Composable
fun JoinByCodeDialog(
    rooms: List<AvailableRoom>,
    onDismiss: () -> Unit,
    onSelect: (AvailableRoom) -> Unit
) {
    val localization = LocalLocalization.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ArcadeDialog(
        title = localized(TextKey.EnterRoomCode),
        subtitle = localized(TextKey.PasteRoomCode),
        onDismissRequest = onDismiss,
        heroIcon = Icons.Rounded.Pin,
        heroTone = A1HeroTone.PURPLE
    ) {
        RoomCodeTiles(
            code = code,
            onCodeChange = {
                code = it.filter { ch -> ch.isLetterOrDigit() || ch == '-' }.uppercase().take(40)
                error = null
            },
            isError = error != null
        )
        error?.let {
            Text(it, color = Color(0xFFB3202B), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArcadeActionButton(
                label = localized(TextKey.FindRoom),
                style = ArcadeActionStyle.GOLD,
                large = true,
                enabled = code.isNotBlank() && LocalNewOnlineIntentAllowed.current,
                onClick = {
                    val room = findRoomByCode(rooms, code)
                    if (room == null) error = localization.text(TextKey.RoomCodeNotFound) else onSelect(room)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag("join_code_submit")
            )
            ArcadeActionButton(
                label = localized(TextKey.CancelShort),
                style = ArcadeActionStyle.OUTLINE,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 6 ô ký tự kiểu ô số trong trận (".tile"), gõ vào một ô nhập ẩn phủ lên trên. */
@Composable
private fun RoomCodeTiles(code: String, onCodeChange: (String) -> Unit, isError: Boolean) {
    androidx.compose.foundation.text.BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
            imeAction = ImeAction.Search
        ),
        modifier = Modifier.fillMaxWidth().testTag("join_code_input"),
        decorationBox = { inner ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val shown = roomCodeOf(code)
                    repeat(ROOM_CODE_LENGTH) { index ->
                        val ch = shown.getOrNull(index)?.toString().orEmpty()
                        Box(
                            modifier = Modifier
                                .size(width = 42.dp, height = 54.dp)
                                .a1Card(radius = 13.dp, depth = 4.dp, border = if (isError) Color(0xFFD0303A) else A1Palette.Line),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch, style = MaterialTheme.typography.headlineMedium, color = A1Palette.Ink)
                        }
                    }
                }
                // Ô nhập thật nằm phủ, chữ trong suốt để chỉ thấy các ô.
                Box(Modifier.matchParentSize().alpha(0f)) { inner() }
            }
        }
    )
}

/**
 * Bảng "Tạo phòng mới" (thiết kế PlayFlows): tên phòng, chế độ (chip), kiểu trận, quyền vào,
 * mật khẩu khi riêng tư và nút TẠO PHÒNG.
 */
@Composable
fun CreateRoomSheet(
    isLoading: Boolean,
    defaultRoomName: String,
    playerLevel: Int,
    onDismiss: () -> Unit,
    onCreate: (GameMode, MatchType, String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var roomName by remember(defaultRoomName) { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(GameMode.ORDER) }
    var matchType by remember { mutableStateOf(MatchType.CASUAL) }
    val onlineAllowed = LocalNewOnlineIntentAllowed.current
    A1BottomSheet(title = localized(TextKey.CreateNewRoom), onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = roomName,
            onValueChange = { roomName = it },
            label = localized(TextKey.RoomName),
            placeholder = defaultRoomName,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.MeetingRoom, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("create_room_name")
        )
        Column {
            A1FieldLabel(localized(TextKey.CreateRoomModeLabel))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectableGameModes.forEach { option ->
                    val unlocked = playerLevel >= option.unlockLevel
                    ArcadeChoiceChip(
                        label = option.displayName(),
                        selected = option == mode,
                        enabled = unlocked,
                        leadingIcon = if (unlocked) null else Icons.Rounded.Lock,
                        onClick = { mode = option },
                        modifier = Modifier.testTag("game_mode:${option.name}")
                    )
                }
            }
        }
        Column {
            A1FieldLabel(localized(TextKey.CreateRoomTypeLabel))
            ArcadeSegmentedControl(
                labels = listOf(localized(TextKey.CasualShort), localized(TextKey.Ranked)),
                selectedIndex = if (matchType == MatchType.RANKED) 1 else 0,
                onSelected = { matchType = if (it == 1) MatchType.RANKED else MatchType.CASUAL },
                itemTestTag = { index -> if (index == 1) "match_type:RANKED" else "match_type:CASUAL" }
            )
        }
        Column {
            A1FieldLabel(localized(TextKey.CreateRoomAccessLabel))
            ArcadeSegmentedControl(
                labels = listOf(localized(TextKey.Public), localized(TextKey.Private)),
                selectedIndex = if (isPrivate) 1 else 0,
                onSelected = { isPrivate = it == 1 },
                itemTestTag = { index ->
                    if (index == 0) "create_room_public" else "create_room_privacy_toggle"
                }
            )
        }
        if (isPrivate) {
            OutlinedTextField(
                value = roomPassword,
                onValueChange = { roomPassword = it },
                label = localized(TextKey.RoomPassword),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Outlined.Key, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().testTag("create_room_password")
            )
        }
        ArcadeActionButton(
            label = localized(TextKey.CreateRoomCaps),
            style = ArcadeActionStyle.GOLD,
            large = true,
            enabled = onlineAllowed && !isLoading && (!isPrivate || roomPassword.isNotEmpty()),
            onClick = {
                onCreate(mode, matchType, roomName.trim().ifBlank { defaultRoomName }, if (isPrivate) roomPassword else "")
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag("create_room_submit"),
            content = if (isLoading) {
                { CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) }
            } else {
                null
            }
        )
    }
}

/** Thẻ phòng (".card.soft .rm"): icon chế độ, tên (+ khóa), "chủ · chế độ · kiểu", nhãn 1/2, nút VÀO. */
@Composable
private fun RoomCard(room: AvailableRoom, onClick: () -> Unit) {
    val (iconBg, iconFg) = room.gameMode.roomIconColors()
    val enabled = LocalNewOnlineIntentAllowed.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_item:${room.id}")
            .a1Card(radius = 18.dp, depth = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBg, RoundedCornerShape(12.dp))
                .border(2.dp, A1Palette.Line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(room.gameMode.roomIcon(), contentDescription = null, tint = iconFg, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    color = A1Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (room.requiresPassword) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = localized(TextKey.PrivateRoom),
                        tint = A1Palette.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                "${room.hostName} · ${room.gameMode.displayName()} · ${localized(if (room.matchType == MatchType.RANKED) TextKey.Ranked else TextKey.CasualShort)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Danh sách chỉ gồm phòng đang chờ (chủ phòng + 1 chỗ trống).
        A1Tag("1/2", A1TagTone.GREEN)
        ArcadeActionButton(
            label = localized(TextKey.EnterRoom),
            style = ArcadeActionStyle.GOLD,
            compact = true,
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.width(IntrinsicSize.Max)
        )
    }
}

private fun GameMode.roomIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    GameMode.ORDER -> Icons.Rounded.GridView
    GameMode.COMBO -> Icons.Rounded.Bolt
    GameMode.RANDOM_TARGET -> Icons.Rounded.Shuffle
    GameMode.SPEED_UP -> Icons.Rounded.Speed
    GameMode.SURVIVAL -> Icons.Rounded.Favorite
    GameMode.TEAM_2V2 -> Icons.Rounded.Groups
    else -> Icons.Rounded.Timer
}

private fun GameMode.roomIconColors(): Pair<Color, Color> = when (this) {
    GameMode.ORDER -> A1Palette.Blue2 to Color.White
    GameMode.COMBO -> A1Palette.Yellow2 to Color(0xFF6A3300)
    GameMode.RANDOM_TARGET -> A1Palette.Purple2 to Color.White
    GameMode.SPEED_UP -> A1Palette.Red2 to Color.White
    GameMode.SURVIVAL -> A1Palette.Green2 to Color.White
    else -> A1Palette.Ink to Color.White
}

/** Hộp thoại vào phòng: ô icon khóa xanh, ô mật khẩu, VÀO PHÒNG + Huỷ (thiết kế "Phòng riêng"). */
@Composable
fun JoinRoomDialog(
    room: AvailableRoom,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var password by remember(room.id) { mutableStateOf("") }
    ArcadeDialog(
        title = localized(if (room.requiresPassword) TextKey.JoinPrivateRoom else TextKey.JoinRoomTitle),
        subtitle = if (room.requiresPassword) {
            localized(TextKey.PrivateJoinDescription, "room" to room.name)
        } else {
            localized(TextKey.PublicJoinDescription, "room" to room.name, "host" to room.hostName)
        },
        onDismissRequest = onDismiss,
        heroIcon = if (room.requiresPassword) Icons.Rounded.Lock else Icons.Rounded.MeetingRoom,
        heroTone = A1HeroTone.BLUE
    ) {
        if (room.requiresPassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = localized(TextKey.RoomPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("join_room_password")
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArcadeActionButton(
                label = localized(TextKey.JoinRoomCaps),
                style = ArcadeActionStyle.GOLD,
                large = true,
                enabled = LocalNewOnlineIntentAllowed.current && (!room.requiresPassword || password.isNotBlank()),
                onClick = { onJoin(password) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag("join_room_submit")
            )
            ArcadeActionButton(
                label = localized(TextKey.CancelShort),
                style = ArcadeActionStyle.OUTLINE,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Phòng chờ theo thiết kế A1: thẻ MÃ PHÒNG (sao chép/chia sẻ + nhãn chế độ/kiểu trận),
 * "Người chơi n/2" với hai thẻ người chơi và chữ VS, ghi chú, nút SẴN SÀNG và hàng Mời bạn/Rời phòng.
 */
@Composable
private fun RoomWaiting(
    state: GameState,
    onLeaveRoom: () -> Unit,
    onOpenFriends: () -> Unit,
    onSetReady: (Boolean) -> Unit,
    onKickOpponent: () -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    onShareRoom: (String, String) -> Result<Unit>,
    isGuest: Boolean
) {
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    var shareError by remember(state.currentRoomId) { mutableStateOf<String?>(null) }
    var copied by remember(state.currentRoomId) { mutableStateOf(false) }
    val defaultRoomTitle = localized(TextKey.Rooms)
    val shareSheetError = localized(TextKey.ShareSheetError)
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val roomCode = roomCodeOf(state.currentRoomId)
    val share = {
        state.currentRoomId?.let { roomId ->
            shareError = null
            onShareRoom(roomId, state.currentRoomName ?: defaultRoomTitle).onFailure { shareError = shareSheetError }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().a1Hills(bushes = false),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        // Thẻ mã phòng.
        Column(
            modifier = Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        localized(TextKey.RoomCodeCaps),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = A1Palette.Muted
                    )
                    Text(
                        roomCode,
                        style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 3.sp, lineHeight = 38.sp),
                        color = A1Palette.Ink,
                        modifier = Modifier.testTag("room_code")
                    )
                }
                ArcadeHeaderIconButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(roomCode))
                        copied = true
                    },
                    modifier = Modifier.testTag("copy_room_code")
                ) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = localized(if (copied) TextKey.Copied else TextKey.RoomCopyCode)
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                ArcadeHeaderIconButton(
                    onClick = { share() },
                    enabled = state.currentRoomId != null,
                    blue = true,
                    modifier = Modifier.testTag("share_room")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = localized(TextKey.RoomShareCode))
                }
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                A1Tag(localized(TextKey.ModeNumberCount, "mode" to state.gameMode.displayName()), A1TagTone.BLUE)
                A1Tag(
                    if (state.matchType == MatchType.RANKED) localized(TextKey.RankedEloImpact)
                    else "${localized(TextKey.CasualShort)} · ${localized(TextKey.NoEloImpact)}",
                    if (state.matchType == MatchType.RANKED) A1TagTone.PURPLE else A1TagTone.YELLOW
                )
            }
        }
        shareError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        val maxPlayers = if (state.gameMode == GameMode.TEAM_2V2) 4 else 2
        val playerCount = if (state.gameMode == GameMode.TEAM_2V2) {
            1 + state.teammates.count { it.id != null } + state.opponents.count { it.id != null }
        } else {
            if (state.hasOpponent) 2 else 1
        }
        A1OutlinedText(
            text = localized(TextKey.RoomPlayersCount, "count" to playerCount, "max" to maxPlayers),
            style = MaterialTheme.typography.titleLarge
        )
        if (state.gameMode == GameMode.TEAM_2V2) {
            // Chế độ cũ (không còn tạo mới): hiển thị dạng danh sách.
            RoomPlayerCard(state.player, isLocal = true, isHost = state.isRoomHost, latency = state.latencyMillis, modifier = Modifier.fillMaxWidth())
            (state.teammates + state.opponents).forEach { other ->
                RoomPlayerCard(other, isLocal = false, isHost = false, latency = null, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chủ phòng luôn ở bên trái như thiết kế.
                val local: @Composable (Modifier) -> Unit = { m ->
                    RoomPlayerCard(state.player, isLocal = true, isHost = state.isRoomHost, latency = state.latencyMillis, modifier = m)
                }
                val other: @Composable (Modifier) -> Unit = { m ->
                    if (state.hasOpponent) {
                        RoomPlayerCard(
                            state.opponent,
                            isLocal = false,
                            isHost = !state.isRoomHost,
                            latency = null,
                            onViewInfo = opponentFriend?.let { f -> { onOpenFriendProfile(f.userId) } },
                            onKick = if (state.isRoomHost) onKickOpponent else null,
                            modifier = m
                        )
                    } else {
                        RoomEmptySlot(m)
                    }
                }
                if (state.isRoomHost) local(Modifier.weight(1f).fillMaxHeight()) else other(Modifier.weight(1f).fillMaxHeight())
                A1OutlinedText(
                    text = "VS",
                    style = MaterialTheme.typography.headlineLarge,
                    outline = A1Palette.RedDeep,
                    outlineWidth = 3.dp
                )
                if (state.isRoomHost) other(Modifier.weight(1f).fillMaxHeight()) else local(Modifier.weight(1f).fillMaxHeight())
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                .border(3.dp, A1Palette.Line, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = A1Palette.Blue2, modifier = Modifier.size(18.dp))
            Text(
                localized(
                    if (state.player.isReady && state.hasOpponent && !state.opponent.isReady) TextKey.WaitingForReady else TextKey.RoomReadyInfo
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = A1Palette.Ink,
                textAlign = TextAlign.Center
            )
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ArcadeActionButton(
            label = localized(if (state.player.isReady) TextKey.CancelReady else TextKey.ReadyAction),
            style = if (state.player.isReady) ArcadeActionStyle.OUTLINE else ArcadeActionStyle.GOLD,
            hero = !state.player.isReady,
            onClick = { onSetReady(!state.player.isReady) },
            enabled = LocalNewOnlineIntentAllowed.current && state.connectionStatus == ConnectionStatus.CONNECTED && state.hasOpponent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).testTag("room_ready")
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isGuest) {
                ArcadeActionButton(
                    label = localized(TextKey.RoomInviteShort),
                    icon = Icons.Rounded.PersonAdd,
                    style = ArcadeActionStyle.PRIMARY,
                    onClick = onOpenFriends,
                    modifier = Modifier.weight(1f).testTag("room_invite")
                )
            }
            ArcadeActionButton(
                label = localized(if (state.isRoomHost) TextKey.RoomCloseShort else TextKey.RoomLeaveShort),
                style = ArcadeActionStyle.OUTLINE,
                onClick = onLeaveRoom,
                modifier = Modifier.weight(1f).testTag("room_leave")
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
    }
    }
}

/** Thẻ người chơi trong phòng chờ (".card.pl"): avatar khung 112dp, tên, vai trò, trạng thái, độ trễ. */
@Composable
private fun RoomPlayerCard(
    player: PlayerState,
    isLocal: Boolean,
    isHost: Boolean,
    latency: Long?,
    modifier: Modifier = Modifier,
    onViewInfo: (() -> Unit)? = null,
    onKick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .testTag(if (isLocal) "room_player:local" else "room_player:other")
            .a1Card(border = if (isLocal) A1Palette.Blue2 else A1Palette.Line)
            .clip(RoundedCornerShape(A1Dimens.CardRadius))
            .clickable(enabled = onViewInfo != null) { onViewInfo?.invoke() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PlayerAvatar(
            displayName = player.name,
            avatarId = player.avatarId,
            userId = player.id,
            frameId = player.frameId,
            size = 104.dp
        )
        Text(player.name, style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            isHost -> A1Tag(localized(TextKey.Host), A1TagTone.INK)
            isLocal -> A1Tag(localized(TextKey.MatchmakingYou), A1TagTone.BLUE)
        }
        // Chủ phòng chỉ mang nhãn "Chủ phòng" như thiết kế; viền xanh của thẻ cho biết đó là bạn.
        if (player.isReady) {
            A1Tag(localized(TextKey.ReadyStatus), A1TagTone.GREEN, icon = Icons.Rounded.Check)
        } else {
            A1Tag(localized(TextKey.RoomWaitingStatus), A1TagTone.YELLOW)
        }
        latency?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(
                    if (it < 80) Icons.Rounded.SignalCellularAlt else Icons.Rounded.SignalCellularAlt2Bar,
                    contentDescription = null,
                    tint = if (it < 80) A1Palette.Green2 else A1Palette.Yellow3,
                    modifier = Modifier.size(14.dp)
                )
                Text(connectionQualityLabel(it), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
            }
        }
        onKick?.let {
            Text(
                localized(TextKey.RoomKickShort),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = A1Palette.RedText,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = it)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("room_kick")
            )
        }
    }
}

/** Ô trống khi chưa có đối thủ: thẻ viền nét đứt, biểu tượng chờ. */
@Composable
private fun RoomEmptySlot(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 200.dp)
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(A1Dimens.CardRadius))
            .drawBehind {
                drawRoundRect(
                    A1Palette.Line,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(A1Dimens.CardRadius.toPx())
                )
            }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = A1Palette.Muted)
        Text(localized(TextKey.RoomWaitingSlot), style = MaterialTheme.typography.titleSmall, color = A1Palette.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun connectionQualityLabel(latencyMillis: Long): String = when {
    latencyMillis < 80 -> localized(TextKey.LatencyGood, "latency" to latencyMillis)
    latencyMillis < 180 -> localized(TextKey.LatencyStable, "latency" to latencyMillis)
    else -> localized(TextKey.LatencySlow, "latency" to latencyMillis)
}

/** Đếm ngược vào trận (thiết kế PlayFlows): hai avatar khung + VS, tên, số đếm lớn, nhãn chế độ. */
@Composable
private fun MatchedStatus(state: GameState, onOpenFriendProfile: (String) -> Unit) {
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .a1Hills(bushes = false)
            .drawBehind { drawLightRays(androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.55f), 320.dp.toPx()) }
            .verticalScroll(rememberScrollState())
            .padding(top = 90.dp)
            .testTag("match_countdown")
    ) {
        // Mỗi tên nằm ngay dưới avatar của người đó; VS căn giữa theo chiều cao avatar.
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CountdownPlayer(state.player)
            Box(Modifier.height(110.dp), contentAlignment = Alignment.Center) {
                A1OutlinedText(text = "VS", style = MaterialTheme.typography.displayMedium, outline = A1Palette.RedDeep, outlineWidth = 3.dp)
            }
            Box(Modifier.clickable(enabled = opponentFriend != null) { opponentFriend?.let { onOpenFriendProfile(it.userId) } }) {
                CountdownPlayer(state.opponent)
            }
        }
        A1OutlinedText(
            text = "${state.countdown ?: 3}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 150.sp, lineHeight = 160.sp),
            outline = A1Palette.YellowDeep,
            outlineWidth = 6.dp,
            modifier = Modifier.padding(top = 20.dp).semantics { liveRegion = LiveRegionMode.Assertive }
        )
        A1Tag(
            "${state.gameMode.displayName()} · ${localized(if (state.matchType == MatchType.RANKED) TextKey.Ranked else TextKey.CasualShort)}",
            A1TagTone.INK,
            large = true
        )
    }
}

@Composable
private fun CountdownPlayer(player: PlayerState) {
    Column(
        modifier = Modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlayerAvatar(player.name, player.avatarId, player.id, player.frameId, size = 110.dp)
        A1OutlinedText(
            text = player.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GameMode.displayName(): String = localized(titleKey)
