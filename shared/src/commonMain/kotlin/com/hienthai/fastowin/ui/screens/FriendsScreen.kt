package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage

@Composable
fun FriendsScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSendRequest: (String) -> Unit,
    onRespondRequest: (String, Boolean) -> Unit,
    onInviteFriend: (String) -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    var playerCode by remember { mutableStateOf("") }
    val canInvite = state.isRoomHost && state.currentRoomId != null && state.lobbyStage == LobbyStage.ROOM_WAITING
    Column(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showBackButton) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            else Spacer(Modifier.size(48.dp))
            Text("Bạn bè", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !state.isFriendsLoading) {
                Icon(Icons.Default.Refresh, "Làm mới danh sách bạn bè")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = playerCode,
                onValueChange = { playerCode = it.uppercase().take(12) },
                label = { Text("Mã người chơi") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onSendRequest(playerCode); playerCode = "" },
                enabled = playerCode.isNotBlank() && !state.isFriendsLoading,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) { Text("Kết bạn") }
        }
        state.socialNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.isFriendsLoading && state.social.friends.isEmpty() && state.social.incomingRequests.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.social.incomingRequests.isNotEmpty()) {
                item { Text("Lời mời kết bạn", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(state.social.incomingRequests, key = { it.requestId }) { request ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${avatarEmoji(request.avatarId)}  ${request.displayName}", fontWeight = FontWeight.Bold)
                            Text("Mã: ${request.playerCode}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRespondRequest(request.requestId, true) }) { Text("Chấp nhận") }
                                OutlinedButton(onClick = { onRespondRequest(request.requestId, false) }) { Text("Từ chối") }
                            }
                        }
                    }
                }
            }
            if (state.social.outgoingRequests.isNotEmpty()) {
                item { Text("Đang chờ phản hồi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(state.social.outgoingRequests, key = { it.requestId }) { request ->
                    Text("${avatarEmoji(request.avatarId)}  ${request.displayName} • ${request.playerCode}")
                }
            }
            item { Text("Danh sách bạn bè", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.social.friends.isEmpty()) {
                item { Text("Chưa có bạn bè. Hãy nhập mã người chơi để gửi lời mời.") }
            } else {
                items(state.social.friends, key = { it.userId }) { friend ->
                    FriendCard(friend, canInvite, onInviteFriend)
                }
            }
        }
    }
}

@Composable
private fun FriendCard(friend: FriendSnapshot, canInvite: Boolean, onInviteFriend: (String) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(avatarEmoji(friend.avatarId), style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.displayName, fontWeight = FontWeight.Bold)
                Text("${friend.playerCode} • ${friend.presence.label()}")
            }
            if (canInvite && friend.presence == FriendPresence.ONLINE) {
                Button(onClick = { onInviteFriend(friend.userId) }) { Text("Mời") }
            }
        }
    }
}

@Composable
fun RoomInvitationDialog(
    invitation: ServerMessage.RoomInvitation,
    onRespond: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onRespond(false) },
        title = { Text("Lời mời vào phòng") },
        text = { Text("${invitation.fromDisplayName} mời bạn vào phòng “${invitation.roomName}”.") },
        confirmButton = { Button(onClick = { onRespond(true) }) { Text("Tham gia") } },
        dismissButton = { TextButton(onClick = { onRespond(false) }) { Text("Từ chối") } }
    )
}

private fun FriendPresence.label(): String = when (this) {
    FriendPresence.OFFLINE -> "Ngoại tuyến"
    FriendPresence.ONLINE -> "Đang online"
    FriendPresence.IN_ROOM -> "Đang trong phòng"
    FriendPresence.PLAYING -> "Đang thi đấu"
}

private fun avatarEmoji(avatarId: String?): String = when (avatarId) {
    "rocket" -> "🚀"
    "target" -> "🎯"
    "trophy" -> "🏆"
    "crown" -> "👑"
    "star" -> "⭐"
    else -> "⚡"
}
