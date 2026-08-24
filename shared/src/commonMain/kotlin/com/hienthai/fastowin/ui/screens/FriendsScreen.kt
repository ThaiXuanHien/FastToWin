package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FriendPresenceIndicator

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenFriendProfile: (String) -> Unit,
    onOpenNotifications: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)
    var playerCode by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<PlayerActionTarget?>(null) }
    var blockTarget by remember { mutableStateOf<PlayerActionTarget?>(null) }
    val canInvite = state.isRoomHost && state.currentRoomId != null && state.lobbyStage == LobbyStage.ROOM_WAITING

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
    ResponsiveScreen(
        modifier = modifier,
        maxContentWidth = 920.dp,
        applySafeDrawingInsets = showBackButton,
        includeBottomSafeDrawingInset = showBackButton
    ) { contentModifier ->
        PullToRefreshBox(
            isRefreshing = state.isFriendsLoading,
            onRefresh = { if (!state.isFriendsLoading) onRefresh() },
            modifier = contentModifier
        ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        if (showBackButton) {
            FastToWinHeader(
                title = "Bạn bè",
                gold = state.profile?.progression?.gold ?: 0,
                gems = state.profile?.progression?.gems ?: 0,
                unreadNotifications = state.unreadNotificationCount,
                onNotifications = onOpenNotifications,
                onBack = onBack,
                applySafeDrawingInset = false
            )
        }
        FriendCodeForm(
            playerCode = playerCode,
            onPlayerCodeChange = { playerCode = it.uppercase().take(12) },
            onSubmit = { onSendRequest(playerCode); playerCode = "" },
            enabled = playerCode.isNotBlank() && !state.isFriendsLoading
        )
        state.socialNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (
            state.isFriendsLoading && state.social.friends.isEmpty() &&
            state.social.incomingRequests.isEmpty() && state.social.outgoingRequests.isEmpty() &&
            state.social.blockedPlayers.isEmpty() &&
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
                items(state.roomInvitations, key = { "room:${it.invitationId}" }) { invitation ->
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
                items(state.social.incomingRequests, key = { "incoming:${it.requestId}" }) { request ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${avatarEmoji(request.avatarId)}  ${request.displayName}", fontWeight = FontWeight.Bold)
                            Text("Mã: ${request.playerCode}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRespondRequest(request.requestId, true) }) { Text("Chấp nhận") }
                                OutlinedButton(onClick = { onRespondRequest(request.requestId, false) }) { Text("Từ chối") }
                                IconButton(onClick = {
                                    blockTarget = PlayerActionTarget(request.userId, request.displayName)
                                }) {
                                    Icon(Icons.Default.Block, contentDescription = "Chặn ${request.displayName}")
                                }
                            }
                        }
                    }
                }
            }
            if (state.social.outgoingRequests.isNotEmpty()) {
                item { Text("Đang chờ phản hồi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(state.social.outgoingRequests, key = { "outgoing:${it.requestId}" }) { request ->
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
                items(state.social.friends, key = { "friend:${it.userId}" }) { friend ->
                    FriendCard(
                        friend = friend,
                        canInvite = canInvite,
                        isSendingInvitation = friend.userId in state.sendingRoomInviteFriendIds,
                        isInvited = friend.userId in state.invitedRoomFriendIds,
                        onInviteFriend = onInviteFriend,
                        onViewInfo = { onOpenFriendProfile(friend.userId) },
                        onRemoveFriend = {
                            removeTarget = PlayerActionTarget(friend.userId, friend.displayName)
                        },
                        onBlockPlayer = {
                            blockTarget = PlayerActionTarget(friend.userId, friend.displayName)
                        }
                    )
                }
            }
            if (state.social.blockedPlayers.isNotEmpty()) {
                item {
                    Text("Đã chặn", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(state.social.blockedPlayers, key = { "blocked:${it.userId}" }) { player ->
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
    }
}

@Composable
private fun FriendCodeForm(
    playerCode: String,
    onPlayerCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = playerCode,
                    onValueChange = onPlayerCodeChange,
                    label = { Text("Mã người chơi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onSubmit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("Kết bạn")
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = playerCode,
                    onValueChange = onPlayerCodeChange,
                    label = { Text("Mã người chơi") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onSubmit,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text("Kết bạn") }
            }
        }
    }
}

@Composable
private fun FriendCard(
    friend: FriendSnapshot,
    canInvite: Boolean,
    isSendingInvitation: Boolean,
    isInvited: Boolean,
    onInviteFriend: (String) -> Unit,
    onViewInfo: () -> Unit,
    onRemoveFriend: () -> Unit,
    onBlockPlayer: () -> Unit
) {
    var showActions by remember(friend.userId) { mutableStateOf(false) }
    ElevatedCard(
        onClick = onViewInfo,
        modifier = Modifier.fillMaxWidth().testTag("friend_item:${friend.userId}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(avatarEmoji(friend.avatarId), style = MaterialTheme.typography.headlineMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(friend.displayName, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(friend.playerCode)
                        FriendPresenceIndicator(friend.presence)
                    }
                }
                Box {
                    IconButton(
                        onClick = { showActions = true },
                        modifier = Modifier.testTag("friend_more:${friend.userId}")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Thao tác với ${friend.displayName}")
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Hủy kết bạn") },
                            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                            onClick = {
                                showActions = false
                                onRemoveFriend()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Chặn người chơi") },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                            onClick = {
                                showActions = false
                                onBlockPlayer()
                            }
                        )
                    }
                }
            }
            if (canInvite) {
                Button(
                    onClick = { onInviteFriend(friend.userId) },
                    enabled = friend.presence == FriendPresence.ONLINE &&
                        !isSendingInvitation && !isInvited,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            isInvited -> "Đã mời"
                            isSendingInvitation -> "Đang gửi…"
                            else -> "Mời vào phòng"
                        }
                    )
                }
            }
        }
    }
}

private data class PlayerActionTarget(val userId: String, val displayName: String)

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

private fun avatarEmoji(avatarId: String?): String = when (avatarId) {
    "rocket" -> "🚀"
    "target" -> "🎯"
    "trophy" -> "🏆"
    "crown" -> "👑"
    "star" -> "⭐"
    else -> "⚡"
}
