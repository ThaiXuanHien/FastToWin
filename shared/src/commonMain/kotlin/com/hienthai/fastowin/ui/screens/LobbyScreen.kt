package com.hienthai.fastowin.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.navigation.GameMode
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
    onOpenClan: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTournament: () -> Unit,
    onOpenShop: () -> Unit,
    onShareRoom: (String, String) -> Result<Unit>,
    onResolveRoomLink: (String?) -> Unit,
    onClaimDailyCheckIn: () -> Unit,
    serverUrl: String = "",
    modifier: Modifier = Modifier
) {
    val hasBottomNavigation = state.lobbyStage == LobbyStage.SELECT_MODE ||
        state.lobbyStage == LobbyStage.ROOM_BROWSER
    val displayName = (state.profile?.displayName ?: state.player.name).ifBlank { "người chơi" }
    val openRoomBrowserAction = {
        if (isGuest && state.player.name.isBlank()) {
            onModeSelected(state.gameMode)
        } else {
            onOpenRoomBrowser(displayName)
        }
    }
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
    Column(modifier = modifier.fillMaxSize().testTag("lobby_screen")) {
        FastToWinHeader(
            title = when (state.lobbyStage) {
                LobbyStage.SELECT_MODE -> "Xin chào, $displayName!"
                LobbyStage.ENTER_NAME -> "Tên hiển thị"
                LobbyStage.ROOM_BROWSER -> "Phòng chơi"
                LobbyStage.ROOM_WAITING -> "Sẵn sàng"
                LobbyStage.MATCHMAKING -> "Ghép đối thủ"
                LobbyStage.MATCHED -> "Đã ghép trận"
            },
            subtitle = if (state.lobbyStage == LobbyStage.SELECT_MODE) "Sẵn sàng phá kỷ lục mới?" else null,
            gold = state.profile?.progression?.gold ?: 0,
            gems = state.profile?.progression?.gems ?: 0,
            unreadNotifications = state.unreadNotificationCount,
            onNotifications = onOpenNotifications,
            onBack = when (state.lobbyStage) {
                LobbyStage.SELECT_MODE, LobbyStage.ROOM_BROWSER, LobbyStage.MATCHED -> null
                LobbyStage.ENTER_NAME -> onBackToMode
                LobbyStage.ROOM_WAITING -> onLeaveRoom
                LobbyStage.MATCHMAKING -> onCancelMatchmaking
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
            applySafeDrawingInsets = false,
            avoidKeyboard = true
        ) { contentModifier ->
            Box(
                modifier = contentModifier.then(
                    if (state.lobbyStage == LobbyStage.SELECT_MODE) Modifier else Modifier.padding(vertical = 16.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                when (state.lobbyStage) {
                LobbyStage.SELECT_MODE -> HomeDashboard(
                    state = state,
                    isGuest = isGuest,
                    serverUrl = serverUrl,
                    onQuickMatch = onStartMatchmaking,
                    onOpenRooms = openRoomBrowserAction,
                    onOpenFriends = onOpenFriends,
                    onOpenLeaderboard = if (isGuest) onUpgradeGuest else onOpenLeaderboard,
                    onOpenProfile = onOpenProfile,
                    onOpenNotifications = onOpenNotifications,
                    onOpenClan = onOpenClan,
                    onOpenPractice = onOpenPractice,
                    onOpenTournament = onOpenTournament,
                    onOpenShop = onOpenShop,
                    onClaimDailyCheckIn = onClaimDailyCheckIn,
                    onUpgradeGuest = onUpgradeGuest,
                    onLogout = onLogout
                )
                LobbyStage.ENTER_NAME -> NameEntry(onOpenRoomBrowser)
                LobbyStage.ROOM_BROWSER -> RoomBrowser(
                    state = state,
                    onCreateRoom = onCreateRoom,
                    onJoinRoom = onJoinRoom,
                    onRefreshRooms = onRefreshRooms,
                    onOpenProfile = onOpenProfile,
                    onOpenFriends = onOpenFriends,
                    isGuest = isGuest,
                    onUpgradeGuest = onUpgradeGuest,
                    onResolveRoomLink = onResolveRoomLink
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
                selected = if (state.lobbyStage == LobbyStage.ROOM_BROWSER) MainTab.ROOMS else MainTab.HOME,
                friendNotificationCount = 0,
                onHome = if (state.lobbyStage == LobbyStage.ROOM_BROWSER) onBackToMode else ({}),
                onRooms = {
                    if (state.lobbyStage != LobbyStage.ROOM_BROWSER) {
                        openRoomBrowserAction()
                    }
                },
                onLeaderboard = if (isGuest) onUpgradeGuest else onOpenLeaderboard,
                onClan = if (isGuest) onUpgradeGuest else onOpenClan,
                onAccount = if (isGuest) onUpgradeGuest else onOpenProfile
            )
        }
    }
}

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

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ArcadeIconHero(
            kicker = if (state.matchType == MatchType.RANKED) "XẾP HẠNG" else "ĐẤU THƯỜNG",
            title = "Đang tìm đối thủ...",
            subtitle = if (state.matchType == MatchType.RANKED) {
                "Ưu tiên người chơi có Elo gần bạn."
            } else {
                "Đang ghép người chơi cùng chế độ."
            },
            icon = Icons.Default.Search
        )
        Surface(
            modifier = Modifier.size(128.dp),
            shape = RoundedCornerShape(36.dp),
            color = ArcadePalette.Gold500,
            contentColor = ArcadePalette.Navy950,
            border = androidx.compose.foundation.BorderStroke(3.dp, Color.White.copy(alpha = 0.38f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(92.dp),
                    strokeWidth = 4.dp,
                    color = ArcadePalette.Blue600,
                    trackColor = ArcadePalette.Gold500
                )
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ArcadePalette.Navy800,
            border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (state.matchType == MatchType.RANKED) "Khoảng Elo" else state.gameMode.displayName(),
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        if (state.matchType == MatchType.RANKED) "±$expandedRange" else "Không ảnh hưởng Elo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA9BADC)
                    )
                }
                Text(
                    "${(elapsedSeconds / 60).toString().padStart(2, '0')}:${(elapsedSeconds % 60).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
        state.latencyMillis?.let {
            Text(connectionQualityLabel(it), style = MaterialTheme.typography.bodySmall)
        }
        ArcadeActionButton(
            label = "HỦY GHÉP TRẬN",
            style = ArcadeActionStyle.OUTLINE,
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
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
            kicker = "CHẾ ĐỘ KHÁCH",
            title = "Bạn muốn được gọi là gì?",
            subtitle = "Tên này chỉ dùng trong phiên chơi hiện tại.",
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
                    label = { Text("Nhập biệt danh") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                ArcadeActionButton(
                    label = "TIẾP TỤC",
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
                "Tạo tài khoản để giữ tên, Elo và lịch sử trên Android/iOS.",
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
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    isGuest: Boolean,
    onUpgradeGuest: () -> Unit,
    onResolveRoomLink: (String?) -> Unit
) {
    var selectedRoom by remember { mutableStateOf<AvailableRoom?>(null) }
    var showCreateMatchType by remember { mutableStateOf(false) }
    var createMatchType by remember { mutableStateOf<MatchType?>(null) }
    var createGameMode by remember { mutableStateOf<GameMode?>(null) }
    var showJoinCode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchTypeFilter by remember { mutableStateOf<MatchType?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullRefreshScope = rememberCoroutineScope()
    val filteredRooms = remember(state.availableRooms, searchQuery, matchTypeFilter) {
        state.availableRooms.filter { room ->
            (searchQuery.isBlank() || room.name.contains(searchQuery.trim(), ignoreCase = true) ||
                room.hostName.contains(searchQuery.trim(), ignoreCase = true)) &&
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

    LaunchedEffect(state.pendingRoomLinkId, state.roomListVersion) {
        val roomId = state.pendingRoomLinkId ?: return@LaunchedEffect
        if (state.roomListVersion <= state.pendingRoomLinkListVersion) return@LaunchedEffect
        val linkedRoom = state.availableRooms.firstOrNull { it.id == roomId }
        if (linkedRoom == null) {
            onResolveRoomLink("Phòng trong liên kết không còn tồn tại hoặc đã đủ người.")
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
    if (showCreateMatchType) {
        MatchTypePickerDialog(
            title = "Chọn loại phòng",
            onDismiss = { showCreateMatchType = false },
            onSelect = { matchType ->
                showCreateMatchType = false
                createMatchType = matchType
            }
        )
    }
    createMatchType?.takeIf { createGameMode == null }?.let { matchType ->
        GameModePickerDialog(
            title = if (matchType == MatchType.RANKED) {
                "Chọn chế độ xếp hạng"
            } else {
                "Chọn chế độ đấu thường"
            },
            playerLevel = state.profile?.progression?.level ?: 1,
            onDismiss = { createMatchType = null },
            onSelect = { mode -> createGameMode = mode }
        )
    }
    createGameMode?.let { mode ->
        val matchType = createMatchType ?: MatchType.CASUAL
        CreateRoomDialog(
            isLoading = state.isSearching,
            defaultRoomName = "Phòng của ${state.player.name}",
            mode = mode,
            matchType = matchType,
            onDismiss = {
                createGameMode = null
                createMatchType = null
            },
            onCreate = { roomName, password ->
                createGameMode = null
                createMatchType = null
                onCreateRoom(mode, matchType, roomName, password)
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
        isRefreshing = isPullRefreshing,
        onRefresh = {
            if (!state.isSearching) {
                isPullRefreshing = true
                onRefreshRooms()
                pullRefreshScope.launch {
                    delay(800)
                    isPullRefreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "room_hero") {
                ArcadeIconHero(
                    kicker = "PHÒNG CÔNG KHAI",
                    title = "Tìm đối thủ phù hợp",
                    subtitle = "Tạo phòng riêng hoặc tham gia phòng đang chờ.",
                    icon = Icons.Default.MeetingRoom
                )
            }

            state.error?.let { message ->
                item(key = "room_error") {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (isGuest) {
                item(key = "room_guest_upgrade") {
                    ArcadeActionButton(
                        label = "LƯU TIẾN TRÌNH BẰNG EMAIL",
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
                    canCreate = state.connectionStatus == ConnectionStatus.CONNECTED && !state.isSearching,
                    onCreate = {
                        createMatchType = null
                        createGameMode = null
                        showCreateMatchType = true
                    },
                    onJoinCode = { showJoinCode = true }
                )
            }

            item(key = "room_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Tìm tên phòng hoặc chủ phòng") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp)
                )
            }

            item(key = "room_filters") {
                ArcadeSegmentedControl(
                    labels = listOf("Tất cả", "Đấu thường", "Xếp hạng"),
                    selectedIndex = when (matchTypeFilter) {
                        null -> 0
                        MatchType.CASUAL -> 1
                        MatchType.RANKED -> 2
                    },
                    onSelected = { index ->
                        matchTypeFilter = when (index) {
                            1 -> MatchType.CASUAL
                            2 -> MatchType.RANKED
                            else -> null
                        }
                    }
                )
            }

            item(key = "room_section_title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Đang chờ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        "${filteredRooms.size} phòng",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                state.isSearching && visibleRooms.isEmpty() -> item(key = "room_loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            when (state.connectionStatus) {
                                ConnectionStatus.RECONNECTING -> "  Đang kết nối lại..."
                                ConnectionStatus.AUTHENTICATING -> "  Đang xác thực phiên chơi..."
                                else -> "  Đang kết nối..."
                            }
                        )
                    }
                }

                visibleRooms.isEmpty() -> item(key = "room_empty") {
                    Text(
                        if (searchQuery.isBlank() && matchTypeFilter == null) {
                            "Chưa có phòng đang chờ. Hãy tạo phòng mới hoặc kéo xuống để làm mới."
                        } else {
                            "Không tìm thấy phòng phù hợp với bộ lọc."
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun RoomBrowserActions(
    canCreate: Boolean,
    onCreate: () -> Unit,
    onJoinCode: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArcadeActionButton(
            label = "TẠO PHÒNG",
            icon = Icons.Default.Add,
            style = ArcadeActionStyle.GOLD,
            enabled = canCreate,
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().testTag("create_room_open")
        )
        ArcadeActionButton(
            label = "NHẬP MÃ",
            icon = Icons.Default.Lock,
            style = ArcadeActionStyle.OUTLINE,
            onClick = onJoinCode,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun JoinByCodeDialog(
    rooms: List<AvailableRoom>,
    onDismiss: () -> Unit,
    onSelect: (AvailableRoom) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ArcadeDialog(
        title = "Nhập mã phòng",
        subtitle = "Dán mã được chủ phòng chia sẻ.",
        onDismissRequest = onDismiss
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Mã phòng") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            shape = RoundedCornerShape(14.dp)
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeActionButton(
                label = "TÌM PHÒNG",
                style = ArcadeActionStyle.GOLD,
                enabled = code.isNotBlank(),
                onClick = {
                    val room = rooms.firstOrNull { it.id.equals(code.trim(), ignoreCase = true) }
                    if (room == null) error = "Không tìm thấy phòng với mã này." else onSelect(room)
                },
                modifier = Modifier.fillMaxWidth()
            )
            ArcadeActionButton(
                label = "HỦY",
                style = ArcadeActionStyle.OUTLINE,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CreateRoomDialog(
    isLoading: Boolean,
    defaultRoomName: String,
    mode: GameMode,
    matchType: MatchType,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var roomName by remember(defaultRoomName) { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    ArcadeDialog(
        title = "Tạo phòng mới",
        subtitle = "Bạn sẽ là chủ phòng.",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                label = { Text("Tên phòng") },
                placeholder = { Text(defaultRoomName) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("create_room_name")
            )
            ArcadeSegmentedControl(
                labels = listOf("Công khai", "Riêng tư"),
                selectedIndex = if (isPrivate) 1 else 0,
                onSelected = { isPrivate = it == 1 },
                itemTestTag = { index ->
                    if (index == 0) "create_room_public" else "create_room_privacy_toggle"
                }
            )
            Text(
                if (isPrivate) "Người chơi cần nhập mật khẩu." else "Mọi người có thể tham gia, không cần mật khẩu.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA9BADC)
            )
            if (isPrivate) {
                OutlinedTextField(
                    value = roomPassword,
                    onValueChange = { roomPassword = it },
                    label = { Text("Mật khẩu phòng") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("create_room_password")
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ArcadePalette.Navy800,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    ArcadePalette.OutlineDark.copy(alpha = 0.62f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = ArcadePalette.Blue700
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = mode.modeIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = ArcadePalette.Blue100
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mode.displayName(), color = Color.White, fontWeight = FontWeight.Black)
                        Text(
                            if (matchType == MatchType.RANKED) {
                                "Đấu xếp hạng · Có ảnh hưởng Elo"
                            } else {
                                "Đấu thường · Không ảnh hưởng Elo"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA9BADC)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = "TẠO PHÒNG",
                    style = ArcadeActionStyle.GOLD,
                    enabled = !isLoading && (!isPrivate || roomPassword.isNotEmpty()),
                    onClick = {
                        onCreate(
                            roomName.trim().ifBlank { defaultRoomName },
                            if (isPrivate) roomPassword else ""
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("create_room_submit"),
                    content = if (isLoading) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        null
                    }
                )
                ArcadeActionButton(
                    label = "HỦY",
                    style = ArcadeActionStyle.OUTLINE,
                    enabled = !isLoading,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RoomCard(room: AvailableRoom, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("room_item:${room.id}"),
        shape = RoundedCornerShape(16.dp),
        color = ArcadePalette.Navy800,
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                contentColor = ArcadePalette.Navy950
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFFFFE46D), ArcadePalette.Gold500)
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (room.requiresPassword) Icons.Default.Lock else Icons.Default.MeetingRoom,
                        contentDescription = if (room.requiresPassword) "Phòng riêng" else "Phòng công khai",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${room.hostName} • ${room.gameMode.displayName()} • ${if (room.matchType == MatchType.RANKED) "Xếp hạng" else "Đấu thường"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA9BADC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.72f))
            ) {
                Text(
                    "VÀO",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun JoinRoomDialog(
    room: AvailableRoom,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var password by remember(room.id) { mutableStateOf("") }
    ArcadeDialog(
        title = if (room.requiresPassword) "Phòng riêng" else "Tham gia phòng",
        subtitle = if (room.requiresPassword) {
            "Nhập mật khẩu để tham gia “${room.name}”."
        } else {
            "${room.name} • Chủ phòng ${room.hostName}"
        },
        onDismissRequest = onDismiss
    ) {
        if (room.requiresPassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mật khẩu phòng") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("join_room_password")
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeActionButton(
                label = "THAM GIA",
                style = ArcadeActionStyle.GOLD,
                enabled = !room.requiresPassword || password.isNotBlank(),
                onClick = { onJoin(password) },
                modifier = Modifier.fillMaxWidth().testTag("join_room_submit")
            )
            ArcadeActionButton(
                label = "HỦY",
                style = ArcadeActionStyle.OUTLINE,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        val roomCode = state.currentRoomId?.take(6)?.uppercase().orEmpty()
        ArcadeIconHero(
            kicker = if (state.isRoomHost && roomCode.isNotBlank()) "MÃ PHÒNG • $roomCode" else "ĐÃ VÀO PHÒNG",
            title = state.currentRoomName ?: "Phòng chơi",
            subtitle = if (state.isRoomHost) {
                "Chia sẻ mã để mời bạn bè."
            } else {
                "Chờ chủ phòng bắt đầu trận."
            },
            icon = Icons.Default.MeetingRoom
        )

        val playerCount = if (state.gameMode == GameMode.TEAM_2V2) {
            1 + state.teammates.count { it.id != null } + state.opponents.count { it.id != null }
        } else {
            if (state.hasOpponent) 2 else 1
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text("Người chơi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                "$playerCount / ${if (state.gameMode == GameMode.TEAM_2V2) 4 else 2}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.gameMode == GameMode.TEAM_2V2) {
            val myTeamName = if (state.player.teamId == "TEAM_A") "Đội Xanh" else "Đội Đỏ"
            val opponentTeamName = if (state.player.teamId == "TEAM_A") "Đội Đỏ" else "Đội Xanh"

            Text(myTeamName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            RoomWaitingPlayerCard("Bạn", state.player, isLocal = true)
            if (state.teammates.isNotEmpty()) {
                state.teammates.forEachIndexed { index, teammate ->
                    val friend = state.social.friends.firstOrNull { it.userId == teammate.id }
                    RoomWaitingPlayerCard("Đồng đội ${index + 1}", teammate, isLocal = false, onViewInfo = if (friend == null) null else ({ onOpenFriendProfile(friend.userId) }))
                }
            } else {
                RoomWaitingPlayerCard("Đồng đội", PlayerState("Đang chờ..."), isLocal = false)
            }

            Text(opponentTeamName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            val allOpponents = state.opponents.toMutableList()
            while (allOpponents.size < 2) {
                allOpponents.add(PlayerState("Đang chờ..."))
            }
            allOpponents.forEachIndexed { index, opp ->
                val friend = state.social.friends.firstOrNull { it.userId == opp.id }
                RoomWaitingPlayerCard("Đối thủ ${index + 1}", opp, isLocal = false, onViewInfo = if (friend == null) null else ({ onOpenFriendProfile(friend.userId) }))
            }
        } else {
            RoomWaitingPlayerCard(
                if (state.isRoomHost) "Chủ phòng • Bạn" else "Bạn",
                state.player,
                isLocal = true
            )
            RoomWaitingPlayerCard(
                if (state.isRoomHost) "Khách" else "Chủ phòng",
                if (state.hasOpponent) state.opponent else PlayerState("Đang chờ người chơi..."),
                isLocal = false,
                onViewInfo = if (opponentFriend == null) null else ({
                    onOpenFriendProfile(opponentFriend.userId)
                })
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ArcadePalette.Navy800,
            contentColor = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.58f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${state.gameMode.displayName()} • 50 số",
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (state.matchType == MatchType.RANKED) "Xếp hạng • Có ảnh hưởng Elo" else "Đấu thường • Không ảnh hưởng Elo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA9BADC)
                    )
                }
                state.latencyMillis?.let {
                    Text(
                        connectionQualityLabel(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcadePalette.Mint400
                    )
                }
            }
        }

        ArcadeActionButton(
            label = "CHIA SẺ MÃ PHÒNG",
            icon = Icons.Default.Share,
            style = ArcadeActionStyle.OUTLINE,
            onClick = {
                val roomId = state.currentRoomId ?: return@ArcadeActionButton
                shareError = null
                onShareRoom(roomId, state.currentRoomName ?: "Phòng").onFailure {
                    shareError = "Không thể mở bảng chia sẻ. Vui lòng thử lại."
                }
            },
            enabled = state.currentRoomId != null,
            modifier = Modifier.fillMaxWidth().testTag("share_room")
        )
        shareError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        ArcadeActionButton(
            label = if (state.player.isReady) "HỦY SẴN SÀNG" else "SẴN SÀNG",
            style = ArcadeActionStyle.GOLD,
            onClick = { onSetReady(!state.player.isReady) },
            enabled = state.connectionStatus == ConnectionStatus.CONNECTED && state.hasOpponent,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.player.isReady) {
            val waitingForOthers = if (state.gameMode == GameMode.TEAM_2V2) {
                (state.teammates + state.opponents).any { !it.isReady && it.id != null }
            } else {
                state.hasOpponent && !state.opponent.isReady
            }
            if (waitingForOthers) {
                Text("Đang chờ người khác sẵn sàng...", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (state.isRoomHost) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isGuest) {
                    ArcadeActionButton(
                        label = "MỜI BẠN BÈ",
                        icon = Icons.Default.Group,
                        style = ArcadeActionStyle.OUTLINE,
                        onClick = onOpenFriends,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.hasOpponent) {
                    ArcadeActionButton(
                        label = "MỜI RA",
                        style = ArcadeActionStyle.OUTLINE,
                        onClick = onKickOpponent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        ArcadeActionButton(
            label = if (state.isRoomHost) "ĐÓNG PHÒNG" else "RỜI PHÒNG",
            style = ArcadeActionStyle.DANGER,
            onClick = onLeaveRoom,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RoomWaitingPlayerCard(
    label: String,
    player: PlayerState,
    isLocal: Boolean,
    onViewInfo: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = onViewInfo != null) { onViewInfo?.invoke() },
        shape = RoundedCornerShape(16.dp),
        color = ArcadePalette.Navy800,
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLocal) ArcadePalette.Blue300.copy(alpha = 0.72f) else ArcadePalette.OutlineDark.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlayerAvatar(
                displayName = player.name,
                avatarId = player.avatarId,
                userId = player.id,
                frameId = player.frameId,
                size = 52.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(player.name, fontWeight = FontWeight.Bold)
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (player.isReady) ArcadePalette.Mint600.copy(alpha = 0.2f) else ArcadePalette.Navy950,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (player.isReady) ArcadePalette.Mint400.copy(alpha = 0.7f) else ArcadePalette.OutlineDark.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    if (player.isReady) "SẴN SÀNG" else "ĐANG CHỜ",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (player.isReady) ArcadePalette.Mint400 else Color(0xFFA9BADC)
                )
            }
        }
    }
}

private fun connectionQualityLabel(latencyMillis: Long): String = when {
    latencyMillis < 80 -> "$latencyMillis ms • Tốt"
    latencyMillis < 180 -> "$latencyMillis ms • Ổn định"
    else -> "$latencyMillis ms • Chậm"
}

@Composable
private fun MatchedStatus(state: GameState, onOpenFriendProfile: (String) -> Unit) {
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        ArcadeIconHero(
            kicker = "ĐỐI THỦ ĐÃ SẴN SÀNG",
            title = "Trận đấu sắp bắt đầu",
            subtitle = "${state.gameMode.displayName()} • ${if (state.matchType == MatchType.RANKED) "Xếp hạng" else "Đấu thường"} • 50 số",
            icon = Icons.Default.Group,
            accent = ArcadePalette.Coral600
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerMatchedCard(
                player = state.player,
                isLocal = true,
                modifier = Modifier.weight(1f)
            )
            Text("VS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            PlayerMatchedCard(
                player = state.opponent,
                isLocal = false,
                onViewInfo = if (opponentFriend == null) null else ({
                    onOpenFriendProfile(opponentFriend.userId)
                }),
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "${state.countdown ?: 3}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.Black),
            color = ArcadePalette.Gold500
        )
    }
}

@Composable
private fun PlayerMatchedCard(
    player: PlayerState,
    isLocal: Boolean,
    onViewInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(enabled = onViewInfo != null) { onViewInfo?.invoke() },
        shape = RoundedCornerShape(18.dp),
        color = ArcadePalette.Navy800,
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLocal) ArcadePalette.Blue300.copy(alpha = 0.75f) else ArcadePalette.Coral400.copy(alpha = 0.75f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (isLocal) "BẠN" else "ĐỐI THỦ",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLocal) ArcadePalette.Blue300 else ArcadePalette.Coral400,
                fontWeight = FontWeight.Bold
            )
            PlayerAvatar(
                displayName = player.name,
                avatarId = player.avatarId,
                userId = player.id,
                frameId = player.frameId,
                size = 76.dp
            )
            Text(
                player.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun GameMode.displayName(): String = title
