package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.AvailableRoom
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState

@Composable
fun LobbyScreen(
    state: GameState,
    onModeSelected: (GameMode) -> Unit,
    onOpenRoomBrowser: (String) -> Unit,
    onCreateRoom: (String, String) -> Unit,
    onJoinRoom: (String, String) -> Unit,
    onLeaveRoom: () -> Unit,
    onRefreshRooms: () -> Unit,
    onBackToMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state.lobbyStage,
            label = "LobbyContent",
            modifier = Modifier.widthIn(max = 600.dp)
        ) { stage ->
            when (stage) {
                LobbyStage.SELECT_MODE -> ModeSelection(onModeSelected)
                LobbyStage.ENTER_NAME -> NameEntry(onOpenRoomBrowser, onBackToMode)
                LobbyStage.ROOM_BROWSER -> RoomBrowser(
                    state = state,
                    onCreateRoom = onCreateRoom,
                    onJoinRoom = onJoinRoom,
                    onRefreshRooms = onRefreshRooms,
                    onBack = onBackToMode
                )
                LobbyStage.ROOM_WAITING -> RoomWaiting(state, onLeaveRoom)
                LobbyStage.MATCHED -> MatchedStatus(state)
            }
        }
    }
}

@Composable
private fun ModeSelection(onModeSelected: (GameMode) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Fast To Win",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Đấu tốc độ cùng bạn bè",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        ModeCard(
            title = "Đua thứ tự",
            subtitle = "Tìm thật nhanh các số từ 1 đến 50",
            icon = Icons.Rounded.Bolt,
            onClick = { onModeSelected(GameMode.ORDER) }
        )
        ModeCard(
            title = "Đua 60 giây",
            subtitle = "Giành nhiều điểm nhất trong 60 giây",
            icon = Icons.Rounded.Timer,
            onClick = { onModeSelected(GameMode.TIME_ATTACK) }
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(icon, null, modifier = Modifier.padding(12.dp).fillMaxSize())
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
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
    onBack: () -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }
    var selectedRoom by remember { mutableStateOf<AvailableRoom?>(null) }

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

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Danh sách phòng", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${state.player.name} • ${state.gameMode.displayName()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefreshRooms, enabled = !state.isSearching) {
                Icon(Icons.Default.Refresh, contentDescription = "Làm mới danh sách phòng")
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tạo phòng mới", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Tên phòng") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = roomPassword,
                    onValueChange = { roomPassword = it },
                    label = { Text("Mật khẩu phòng") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onCreateRoom(roomName, roomPassword) },
                    enabled = roomName.isNotBlank() && roomPassword.isNotEmpty() && !state.isSearching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Text(" Tạo phòng")
                }
            }
        }

        Text("Phòng đang chờ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        when {
            state.isSearching -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Đang kết nối...")
                }
            }
            state.availableRooms.isEmpty() -> {
                Text(
                    "Chưa có phòng nào đang chờ. Hãy tạo phòng mới hoặc làm mới danh sách.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.availableRooms, key = { it.id }) { room ->
                    RoomCard(room = room, onClick = { selectedRoom = room })
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Quay lại chọn chế độ")
        }
    }
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
private fun RoomWaiting(state: GameState, onLeaveRoom: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            state.currentRoomName ?: "Phòng",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Text(
            if (state.isRoomHost) "Đang chờ người chơi tham gia..." else "Đang gửi yêu cầu tham gia phòng...",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            if (state.isRoomHost) "Chỉ người nhập đúng mật khẩu mới có thể vào phòng."
            else "Chủ phòng đang kiểm tra mật khẩu.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onLeaveRoom) { Text("Rời phòng") }
    }
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
