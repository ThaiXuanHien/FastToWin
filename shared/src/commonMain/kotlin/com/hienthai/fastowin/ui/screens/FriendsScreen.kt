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
import com.hienthai.fastowin.protocol.RecentPlayerSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage

@Composable
fun FriendsScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSendRequest: (String) -> Unit,
    onRespondRequest: (String, Boolean) -> Unit,
    onCancelRequest: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onBlockPlayer: (String) -> Unit,
    onUnblockPlayer: (String) -> Unit,
    onInviteFriend: (String) -> Unit,
    onRespondRoomInvitation: (String, Boolean) -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    var playerCode by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<PlayerActionTarget?>(null) }
    var blockTarget by remember { mutableStateOf<PlayerActionTarget?>(null) }
    val canInvite = state.isRoomHost && state.currentRoomId != null && state.lobbyStage == LobbyStage.ROOM_WAITING
    val friendsById = state.social.friends.associateBy { it.userId }
    val blockedPlayerIds = state.social.blockedPlayers.mapTo(mutableSetOf()) { it.userId }
    val visibleRecentPlayers = state.social.recentPlayers.filterNot { it.userId in blockedPlayerIds }
    val recentPlayersNowMillis = remember(visibleRecentPlayers) { epochMillis() }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Hủy kết bạn?") },
            text = { Text("Bạn và ${target.displayName} sẽ không còn trong danh sách bạn bè.") },
            confirmButton = {
                Button(onClick = {
                    onRemoveFriend(target.userId)
                    removeTarget = null
                }) { Text("Hủy kết bạn") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Quay lại") } }
        )
    }
    blockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Chặn người chơi?") },
            text = {
                Text("${target.displayName} sẽ không thể gửi lời mời kết bạn hoặc lời mời vào phòng cho bạn.")
            },
            confirmButton = {
                Button(onClick = {
                    onBlockPlayer(target.userId)
                    blockTarget = null
                }) { Text("Chặn") }
            },
            dismissButton = { TextButton(onClick = { blockTarget = null }) { Text("Quay lại") } }
        )
    }

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
        if (
            state.isFriendsLoading && state.social.friends.isEmpty() &&
            state.social.incomingRequests.isEmpty() && state.social.outgoingRequests.isEmpty() &&
            state.social.blockedPlayers.isEmpty() && state.social.recentPlayers.isEmpty() &&
            state.roomInvitations.isEmpty()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.roomInvitations.isNotEmpty()) {
                item {
                    Text("Lời mời vào phòng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(state.roomInvitations, key = { it.invitationId }) { invitation ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(invitation.fromDisplayName, fontWeight = FontWeight.Bold)
                            Text("Mời bạn vào phòng “${invitation.roomName}”")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    onRespondRoomInvitation(invitation.invitationId, true)
                                }) { Text("Tham gia") }
                                OutlinedButton(onClick = {
                                    onRespondRoomInvitation(invitation.invitationId, false)
                                }) { Text("Từ chối") }
                            }
                        }
                    }
                }
            }
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
                                TextButton(onClick = {
                                    blockTarget = PlayerActionTarget(request.userId, request.displayName)
                                }) { Text("Chặn") }
                            }
                        }
                    }
                }
            }
            if (state.social.outgoingRequests.isNotEmpty()) {
                item { Text("Đang chờ phản hồi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(state.social.outgoingRequests, key = { it.requestId }) { request ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(avatarEmoji(request.avatarId), style = MaterialTheme.typography.headlineMedium)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(request.displayName, fontWeight = FontWeight.Bold)
                                Text("Mã: ${request.playerCode}")
                            }
                            OutlinedButton(onClick = { onCancelRequest(request.requestId) }) {
                                Text("Hủy")
                            }
                        }
                    }
                }
            }
            item { Text("Danh sách bạn bè", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.social.friends.isEmpty()) {
                item { Text("Chưa có bạn bè. Hãy nhập mã người chơi để gửi lời mời.") }
            } else {
                items(state.social.friends, key = { it.userId }) { friend ->
                    FriendCard(
                        friend = friend,
                        canInvite = canInvite,
                        onInviteFriend = onInviteFriend,
                        onRemoveFriend = {
                            removeTarget = PlayerActionTarget(friend.userId, friend.displayName)
                        },
                        onBlockPlayer = {
                            blockTarget = PlayerActionTarget(friend.userId, friend.displayName)
                        }
                    )
                }
            }
            if (visibleRecentPlayers.isNotEmpty()) {
                item {
                    Text("Vừa thi đấu cùng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(visibleRecentPlayers, key = { it.userId }) { player ->
                    val friend = friendsById[player.userId]
                    RecentPlayerCard(
                        player = player,
                        friend = friend,
                        canInvite = canInvite,
                        nowMillis = recentPlayersNowMillis,
                        actionsEnabled = !state.isFriendsLoading,
                        onAddFriend = { onSendRequest(player.playerCode) },
                        onInviteFriend = { onInviteFriend(player.userId) },
                        onBlockPlayer = {
                            blockTarget = PlayerActionTarget(player.userId, player.displayName)
                        }
                    )
                }
            }
            if (state.social.blockedPlayers.isNotEmpty()) {
                item {
                    Text("Đã chặn", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(state.social.blockedPlayers, key = { it.userId }) { player ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(avatarEmoji(player.avatarId), style = MaterialTheme.typography.headlineMedium)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(player.displayName, fontWeight = FontWeight.Bold)
                                Text("Mã: ${player.playerCode}")
                            }
                            OutlinedButton(onClick = { onUnblockPlayer(player.userId) }) {
                                Text("Bỏ chặn")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentPlayerCard(
    player: RecentPlayerSnapshot,
    friend: FriendSnapshot?,
    canInvite: Boolean,
    nowMillis: Long,
    actionsEnabled: Boolean,
    onAddFriend: () -> Unit,
    onInviteFriend: () -> Unit,
    onBlockPlayer: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(avatarEmoji(player.avatarId), style = MaterialTheme.typography.headlineMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(player.displayName, fontWeight = FontWeight.Bold)
                    Text("${player.playerCode} • ${recentPlayedLabel(player, nowMillis)}")
                    if (friend != null) Text("Đã là bạn • ${friend.presence.label()}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    friend == null -> {
                        Button(onClick = onAddFriend, enabled = actionsEnabled) { Text("Kết bạn") }
                        TextButton(onClick = onBlockPlayer, enabled = actionsEnabled) { Text("Chặn") }
                    }
                    else -> {
                        if (canInvite && friend.presence == FriendPresence.ONLINE) {
                            Button(onClick = onInviteFriend, enabled = actionsEnabled) { Text("Mời vào phòng") }
                        }
                        TextButton(onClick = onBlockPlayer, enabled = actionsEnabled) { Text("Chặn") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendCard(
    friend: FriendSnapshot,
    canInvite: Boolean,
    onInviteFriend: (String) -> Unit,
    onRemoveFriend: () -> Unit,
    onBlockPlayer: () -> Unit
) {
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
            Column(horizontalAlignment = Alignment.End) {
                if (canInvite && friend.presence == FriendPresence.ONLINE) {
                    Button(onClick = { onInviteFriend(friend.userId) }) { Text("Mời") }
                }
                Row {
                    TextButton(onClick = onRemoveFriend) { Text("Hủy bạn") }
                    TextButton(onClick = onBlockPlayer) { Text("Chặn") }
                }
            }
        }
    }
}

private data class PlayerActionTarget(val userId: String, val displayName: String)

private fun recentPlayedLabel(player: RecentPlayerSnapshot, nowMillis: Long): String {
    val elapsedMillis = (nowMillis - player.lastPlayedAtEpochMillis).coerceAtLeast(0L)
    val timeLabel = when {
        elapsedMillis < 60_000L -> "vừa xong"
        elapsedMillis < 3_600_000L -> "${elapsedMillis / 60_000L} phút trước"
        elapsedMillis < 86_400_000L -> "${elapsedMillis / 3_600_000L} giờ trước"
        else -> "${elapsedMillis / 86_400_000L} ngày trước"
    }
    return if (player.matchesPlayed > 1) "${player.matchesPlayed} trận • $timeLabel" else timeLabel
}

@Composable
fun RoomInvitationDialog(
    invitation: ServerMessage.RoomInvitation,
    onRespond: (Boolean) -> Unit,
    onDefer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDefer,
        title = { Text("Lời mời vào phòng") },
        text = { Text("${invitation.fromDisplayName} mời bạn vào phòng “${invitation.roomName}”.") },
        confirmButton = { Button(onClick = { onRespond(true) }) { Text("Tham gia") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDefer) { Text("Để sau") }
                TextButton(onClick = { onRespond(false) }) { Text("Từ chối") }
            }
        }
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
