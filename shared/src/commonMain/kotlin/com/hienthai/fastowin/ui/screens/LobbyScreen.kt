package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.state.AvailableRoom
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import kotlinx.coroutines.delay

@Composable
fun LobbyScreen(
    state: GameState,
    onModeSelected: (GameMode) -> Unit,
    onStartMatchmaking: (GameMode) -> Unit,
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
    onBackToMode: () -> Unit,
    onLogout: () -> Unit,
    isGuest: Boolean,
    onUpgradeGuest: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPractice: () -> Unit,
    sessionStartedAtMillis: Long,
    modifier: Modifier = Modifier
) {
    var openCreateAfterModeSelection by remember { mutableStateOf(false) }
    var showTabModePicker by remember { mutableStateOf(false) }
    if (showTabModePicker) {
        GameModePickerDialog(
            title = "Chọn chế độ chơi",
            onDismiss = { showTabModePicker = false },
            onSelect = { mode ->
                showTabModePicker = false
                onModeSelected(mode)
            }
        )
    }
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.widthIn(max = 600.dp)) {
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
                    onOpenPractice = onOpenPractice,
                    onUpgradeGuest = onUpgradeGuest,
                    onLogout = onLogout,
                    sessionStartedAtMillis = sessionStartedAtMillis
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
                    onInitialCreateHandled = { openCreateAfterModeSelection = false }
                )
                LobbyStage.ROOM_WAITING -> RoomWaiting(
                    state = state,
                    onLeaveRoom = onLeaveRoom,
                    onOpenFriends = onOpenFriends,
                    onSetReady = onSetReady,
                    onKickOpponent = onKickOpponent,
                    isGuest = isGuest
                )
                LobbyStage.MATCHMAKING -> MatchmakingScreen(state, onCancelMatchmaking)
                LobbyStage.MATCHED -> MatchedStatus(state)
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
    val expandedRange = (100 + (elapsedSeconds / 10L).toInt() * 50).coerceAtMost(600)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Text("Đang tìm đối thủ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "${state.gameMode.displayName()} • Elo ±$expandedRange",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Đã chờ ${elapsedSeconds}s\nPhạm vi Elo sẽ tự mở rộng sau mỗi 10 giây.",
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
        modifier = Modifier.fillMaxWidth()
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
    onInitialCreateHandled: () -> Unit
) {
    var selectedRoom by remember { mutableStateOf<AvailableRoom?>(null) }
    var showCreateRoom by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf<GameMode?>(null) }
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
            IconButton(onClick = onRefreshRooms, enabled = !state.isSearching) {
                Icon(Icons.Default.Refresh, contentDescription = "Làm mới danh sách phòng")
            }
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
            modifier = Modifier.fillMaxWidth().height(50.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = modeFilter == null, onClick = { modeFilter = null }, label = { Text("Tất cả") })
            FilterChip(
                selected = modeFilter == GameMode.ORDER,
                onClick = { modeFilter = GameMode.ORDER },
                label = { Text("Thứ tự") }
            )
            FilterChip(
                selected = modeFilter == GameMode.TIME_ATTACK,
                onClick = { modeFilter = GameMode.TIME_ATTACK },
                label = { Text("60 giây") }
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Tên phòng") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
                if (isPrivate) {
                    OutlinedTextField(
                        value = roomPassword,
                        onValueChange = { roomPassword = it },
                        label = { Text("Mật khẩu phòng") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(roomName.trim(), if (isPrivate) roomPassword else "") },
                enabled = !isLoading && roomName.isNotBlank() && (!isPrivate || roomPassword.isNotEmpty())
            ) { Text("Tạo phòng") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Hủy") } }
    )
}

@Composable
private fun RoomCard(room: AvailableRoom, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Chủ phòng: ${room.hostName}")
                if (room.requiresPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu phòng") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                } else {
                    Text("Phòng này không yêu cầu mật khẩu.")
                }
            }
        },
        confirmButton = { Button(onClick = { onJoin(password) }) { Text("Tham gia") } },
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
    isGuest: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
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

        RoomWaitingPlayerCard("Bạn", state.player, isLocal = true)
        RoomWaitingPlayerCard(
            "Đối thủ",
            if (state.hasOpponent) state.opponent else PlayerState("Đang chờ người chơi..."),
            isLocal = false
        )

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { onSetReady(!state.player.isReady) },
            enabled = state.connectionStatus == ConnectionStatus.CONNECTED && state.hasOpponent,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (state.player.isReady) "Hủy sẵn sàng" else "Sẵn sàng", fontWeight = FontWeight.Bold)
        }
        if (state.hasOpponent && state.player.isReady && !state.opponent.isReady) {
            Text("Đang chờ đối thủ sẵn sàng...", color = MaterialTheme.colorScheme.primary)
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
private fun RoomWaitingPlayerCard(label: String, player: PlayerState, isLocal: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
private fun MatchedStatus(state: GameState) {
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
            PlayerMatchedCard(state.opponent, false)
        }
        Text(
            "${state.countdown ?: 3}",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PlayerMatchedCard(player: PlayerState, isLocal: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

private fun GameMode.displayName(): String = when (this) {
    GameMode.ORDER -> "Đua thứ tự"
    GameMode.TIME_ATTACK -> "Đua 60 giây"
}
