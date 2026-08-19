package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
    onCreateRoom: (String, String) -> Unit,
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
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenTournament: () -> Unit,
    onShareRoom: (String, String) -> Result<Unit>,
    onResolveRoomLink: (String?) -> Unit,
    onClaimDailyCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var openCreateAfterModeSelection by remember { mutableStateOf(false) }
    var showTabModePicker by remember { mutableStateOf(false) }
    if (showTabModePicker) {
        GameModePickerDialog(
            title = "Chọn chế độ chơi",
            playerLevel = state.profile?.progression?.level ?: 1,
            onDismiss = { showTabModePicker = false },
            onSelect = { mode ->
                showTabModePicker = false
                onModeSelected(mode)
            }
        )
    }
    Column(modifier = modifier.fillMaxSize().testTag("lobby_screen")) {
        ResponsiveScreen(
            modifier = Modifier.weight(1f),
            maxContentWidth = 760.dp,
            includeBottomSafeDrawingInset = state.lobbyStage != LobbyStage.SELECT_MODE,
            avoidKeyboard = true
        ) { contentModifier ->
            Box(
                modifier = contentModifier.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (state.lobbyStage) {
                LobbyStage.SELECT_MODE -> HomeDashboard(
                    state = state,
                    isGuest = isGuest,
                    onChooseMode = { mode, openCreateRoom ->
                        openCreateAfterModeSelection = openCreateRoom
                        onModeSelected(mode)
                    },
                    onQuickMatch = onStartMatchmaking,
                    onOpenRooms = {
                        openCreateAfterModeSelection = false
                        onModeSelected(state.gameMode)
                    },
                    onOpenFriends = onOpenFriends,
                    onOpenLeaderboard = if (isGuest) onUpgradeGuest else onOpenLeaderboard,
                    onOpenProfile = onOpenProfile,
                    onOpenSettings = onOpenSettings,
                    onOpenNotifications = onOpenNotifications,
                    onOpenPractice = onOpenPractice,
                    onOpenTournament = onOpenTournament,
                    onClaimDailyCheckIn = onClaimDailyCheckIn,
                    onUpgradeGuest = onUpgradeGuest,
                    onLogout = onLogout
                )
                LobbyStage.ENTER_NAME -> NameEntry(onOpenRoomBrowser, onBackToMode)
                LobbyStage.ROOM_BROWSER -> RoomBrowser(
                    state = state,
                    onCreateRoom = onCreateRoom,
                    onJoinRoom = onJoinRoom,
                    onRefreshRooms = onRefreshRooms,
                    onOpenProfile = onOpenProfile,
                    onOpenFriends = onOpenFriends,
                    onBack = {
                        openCreateAfterModeSelection = false
                        onBackToMode()
                    },
                    isGuest = isGuest,
                    onUpgradeGuest = onUpgradeGuest,
                    showCreateInitially = openCreateAfterModeSelection,
                    onInitialCreateHandled = { openCreateAfterModeSelection = false },
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
        if (state.lobbyStage == LobbyStage.SELECT_MODE) {
            FastToWinBottomBar(
                selected = MainTab.HOME,
                friendNotificationCount = state.pendingSocialInvitationCount,
                onHome = {},
                onLeaderboard = if (isGuest) onUpgradeGuest else onOpenLeaderboard,
                onPlay = { showTabModePicker = true },
                onFriends = if (isGuest) onUpgradeGuest else onOpenFriends,
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Text(
            if (state.matchType == MatchType.RANKED) "Đang tìm trận xếp hạng" else "Đang tìm trận thường",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            if (state.matchType == MatchType.RANKED) {
                "${state.gameMode.displayName()} • Elo ±$expandedRange"
            } else {
                "${state.gameMode.displayName()} • Không ảnh hưởng Elo"
            },
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (state.matchType == MatchType.RANKED) {
                "Đã chờ ${elapsedSeconds}s\nPhạm vi Elo mở rộng mỗi 10 giây, tối đa ±300."
            } else {
                "Đã chờ ${elapsedSeconds}s\nĐang ghép với người chơi cùng chế độ."
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.latencyMillis?.let {
            Text(connectionQualityLabel(it), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Hủy tìm trận")
        }
    }
}

@Composable
private fun NameEntry(onContinue: (String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        Text("Sẵn sàng chơi?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Nhập biệt danh, sau đó tạo phòng riêng hoặc tham gia một phòng đang chờ.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Biệt danh của bạn") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Person, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        Button(
            onClick = { onContinue(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Xem danh sách phòng", style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = onBack) { Text("Quay lại chọn chế độ") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomBrowser(
    state: GameState,
    onCreateRoom: (String, String) -> Unit,
    onJoinRoom: (String, String) -> Unit,
    onRefreshRooms: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    onBack: () -> Unit,
    isGuest: Boolean,
    onUpgradeGuest: () -> Unit,
    showCreateInitially: Boolean,
    onInitialCreateHandled: () -> Unit,
    onResolveRoomLink: (String?) -> Unit
) {
    var selectedRoom by remember { mutableStateOf<AvailableRoom?>(null) }
    var showCreateRoom by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf<GameMode?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    val pullRefreshScope = rememberCoroutineScope()
    val visibleRooms = remember(state.availableRooms, searchQuery, modeFilter) {
        state.availableRooms.filter { room ->
            (searchQuery.isBlank() || room.name.contains(searchQuery.trim(), ignoreCase = true) ||
                room.hostName.contains(searchQuery.trim(), ignoreCase = true)) &&
                (modeFilter == null || room.gameMode == modeFilter)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            onRefreshRooms()
        }
    }

    LaunchedEffect(showCreateInitially) {
        if (showCreateInitially) {
            showCreateRoom = true
            onInitialCreateHandled()
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
    if (showCreateRoom) {
        CreateRoomDialog(
            isLoading = state.isSearching,
            onDismiss = { showCreateRoom = false },
            onCreate = { roomName, password ->
                showCreateRoom = false
                onCreateRoom(roomName, password)
            }
        )
    }
    PullToRefreshBox(
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
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Về trang chủ")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Phòng chơi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(state.player.name).append(" • ").append(state.gameMode.displayName())
                        state.latencyMillis?.let { append(" • ").append(it).append(" ms") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.size(48.dp))
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (isGuest) {
            OutlinedButton(
                onClick = onUpgradeGuest,
                enabled = state.connectionStatus == ConnectionStatus.CONNECTED,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Person, null)
                Text("  Lưu tiến trình bằng tài khoản email")
            }
        }

        Button(
            onClick = { showCreateRoom = true },
            enabled = state.connectionStatus == ConnectionStatus.CONNECTED && !state.isSearching,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_room_open"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Text("  Tạo phòng mới", fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Tìm theo tên phòng hoặc chủ phòng") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = modeFilter == null, onClick = { modeFilter = null }, label = { Text("Tất cả") })
            GameMode.entries.filterNot(GameMode::isLegacy).forEach { mode ->
                FilterChip(
                    selected = modeFilter == mode,
                    onClick = { modeFilter = mode },
                    label = { Text(mode.title) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Phòng đang chờ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${visibleRooms.size} phòng", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isSearching -> Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        when (state.connectionStatus) {
                            ConnectionStatus.RECONNECTING -> "Đang kết nối lại..."
                            ConnectionStatus.AUTHENTICATING -> "Đang xác thực phiên chơi..."
                            else -> "Đang kết nối..."
                        }
                    )
                }
                visibleRooms.isEmpty() -> Text(
                    if (searchQuery.isBlank() && modeFilter == null) {
                        "Chưa có phòng nào đang chờ. Hãy tạo phòng mới hoặc làm mới danh sách."
                    } else {
                        "Không tìm thấy phòng phù hợp với bộ lọc."
                    },
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleRooms, key = { it.id }) { room ->
                        RoomCard(room = room, onClick = { selectedRoom = room })
                    }
                }
            }
        }

        }
    }
}

@Composable
private fun CreateRoomDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo phòng mới") },
        text = {
            Column(
                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Tên phòng") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_room_name")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phòng riêng tư", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isPrivate) "Người chơi cần nhập mật khẩu" else "Mọi người có thể tham gia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        modifier = Modifier.testTag("create_room_privacy_toggle")
                    )
                }
                if (isPrivate) {
                    OutlinedTextField(
                        value = roomPassword,
                        onValueChange = { roomPassword = it },
                        label = { Text("Mật khẩu phòng") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth().testTag("create_room_password")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(roomName.trim(), if (isPrivate) roomPassword else "") },
                enabled = !isLoading && roomName.isNotBlank() && (!isPrivate || roomPassword.isNotEmpty()),
                modifier = Modifier.testTag("create_room_submit")
            ) { Text("Tạo phòng") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Hủy") } }
    )
}

@Composable
private fun RoomCard(room: AvailableRoom, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("room_item:${room.id}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Chủ phòng: ${room.hostName} • ${room.gameMode.displayName()}")
            }
            if (room.requiresPassword) Icon(Icons.Default.Lock, contentDescription = "Phòng có mật khẩu")
            Text("  Tham gia", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tham gia ${room.name}") },
        text = {
            Column(
                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Chủ phòng: ${room.hostName}")
                if (room.requiresPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu phòng") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.testTag("join_room_password")
                    )
                } else {
                    Text("Phòng này không yêu cầu mật khẩu.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(password) },
                modifier = Modifier.testTag("join_room_submit")
            ) { Text("Tham gia") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        Text(
            state.currentRoomName ?: "Phòng",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            buildString {
                append(if (state.isRoomHost) "Bạn là chủ phòng" else "Phòng của ${state.opponent.name}")
                state.latencyMillis?.let { append(" • ").append(connectionQualityLabel(it)) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

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
            RoomWaitingPlayerCard("Bạn", state.player, isLocal = true)
            RoomWaitingPlayerCard(
                "Đối thủ",
                if (state.hasOpponent) state.opponent else PlayerState("Đang chờ người chơi..."),
                isLocal = false,
                onViewInfo = if (opponentFriend == null) null else ({
                    onOpenFriendProfile(opponentFriend.userId)
                })
            )
        }

        OutlinedButton(
            onClick = {
                val roomId = state.currentRoomId ?: return@OutlinedButton
                shareError = null
                onShareRoom(roomId, state.currentRoomName ?: "Phòng").onFailure {
                    shareError = "Không thể mở bảng chia sẻ. Vui lòng thử lại."
                }
            },
            enabled = state.currentRoomId != null,
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("share_room")
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Text("  Chia sẻ liên kết phòng")
        }
        shareError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { onSetReady(!state.player.isReady) },
            enabled = state.connectionStatus == ConnectionStatus.CONNECTED && state.hasOpponent,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (state.player.isReady) "Hủy sẵn sàng" else "Sẵn sàng", fontWeight = FontWeight.Bold)
        }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isGuest) {
                    OutlinedButton(onClick = onOpenFriends, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Group, null)
                        Text("  Mời bạn")
                    }
                }
                if (state.hasOpponent) {
                    OutlinedButton(onClick = onKickOpponent, modifier = Modifier.weight(1f)) {
                        Text("Mời ra")
                    }
                }
            }
        }
        TextButton(onClick = onLeaveRoom) {
            Text(if (state.isRoomHost) "Đóng phòng" else "Rời phòng", color = MaterialTheme.colorScheme.error)
        }
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
        shape = RoundedCornerShape(20.dp),
        color = if (isLocal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(10.dp).size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(player.name, fontWeight = FontWeight.Bold)
            }
            Text(
                if (player.isReady) "SẴN SÀNG" else "ĐANG CHỜ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (player.isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        verticalArrangement = Arrangement.spacedBy(40.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Đã tìm thấy đối thủ!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerMatchedCard(state.player, true)
            Text("ĐẤU", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            PlayerMatchedCard(
                state.opponent,
                false,
                onViewInfo = if (opponentFriend == null) null else ({
                    onOpenFriendProfile(opponentFriend.userId)
                })
            )
        }
        Text(
            "${state.countdown ?: 3}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PlayerMatchedCard(
    player: PlayerState,
    isLocal: Boolean,
    onViewInfo: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.clickable(enabled = onViewInfo != null) { onViewInfo?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = if (isLocal) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp).fillMaxSize())
        }
        Text(player.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun GameMode.displayName(): String = title
