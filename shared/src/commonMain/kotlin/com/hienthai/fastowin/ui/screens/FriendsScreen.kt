package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.FriendSnapshot
import com.hienthai.fastowin.protocol.ServerMessage
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeIconHero
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FriendPresenceIndicator
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

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
    val canInvite = state.isRoomHost &&
        state.currentRoomId != null &&
        state.lobbyStage == LobbyStage.ROOM_WAITING
    val isInitialLoading = state.isFriendsLoading &&
        state.social.friends.isEmpty() &&
        state.social.incomingRequests.isEmpty() &&
        state.social.outgoingRequests.isEmpty() &&
        state.social.blockedPlayers.isEmpty() &&
        state.roomInvitations.isEmpty()

    removeTarget?.let { target ->
        ArcadeDialog(
            title = "Hủy kết bạn?",
            subtitle = "Bạn và ${target.displayName} sẽ không còn trong danh sách bạn bè.",
            onDismissRequest = { removeTarget = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = "Hủy kết bạn",
                    onClick = {
                        onRemoveFriend(target.userId)
                        removeTarget = null
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "Quay lại",
                    onClick = { removeTarget = null },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    blockTarget?.let { target ->
        ArcadeDialog(
            title = "Chặn người chơi?",
            subtitle = "${target.displayName} sẽ không thể gửi lời mời kết bạn hoặc lời mời vào phòng cho bạn.",
            onDismissRequest = { blockTarget = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = "Chặn",
                    onClick = {
                        onBlockPlayer(target.userId)
                        blockTarget = null
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "Quay lại",
                    onClick = { blockTarget = null },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        ArcadeIconHero(
                            kicker = "SOCIAL HUB",
                            title = "Biệt đội của bạn",
                            subtitle = "Kết nối bằng mã người chơi và mời bạn vào trận.",
                            icon = Icons.Default.Groups,
                            accent = ArcadePalette.Mint600
                        )
                    }
                    item {
                        FriendCodeForm(
                            playerCode = playerCode,
                            onPlayerCodeChange = { playerCode = it.uppercase().take(12) },
                            onSubmit = { onSendRequest(playerCode); playerCode = "" },
                            enabled = playerCode.isNotBlank() && !state.isFriendsLoading
                        )
                    }
                    state.socialNotice?.let { notice ->
                        item { NoticePanel(notice, ArcadePalette.Mint400) }
                    }
                    state.error?.let { error ->
                        item { NoticePanel(error, ArcadePalette.Coral400) }
                    }

                    if (isInitialLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = ArcadePalette.Gold500)
                            }
                        }
                    } else {
                        if (state.roomInvitations.isNotEmpty()) {
                            item {
                                SocialSectionTitle("Lời mời vào phòng", "${state.roomInvitations.size} mới")
                            }
                            items(state.roomInvitations, key = { "room:${it.invitationId}" }) { invitation ->
                                SocialPanel {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(invitation.fromDisplayName, fontWeight = FontWeight.Black)
                                        Text(
                                            "Mời bạn vào “${invitation.roomName}”",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ArcadeActionButton(
                                                label = "THAM GIA",
                                                onClick = {
                                                    onRespondRoomInvitation(invitation.invitationId, true)
                                                },
                                                style = ArcadeActionStyle.GOLD,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            ArcadeActionButton(
                                                label = "TỪ CHỐI",
                                                onClick = {
                                                    onRespondRoomInvitation(invitation.invitationId, false)
                                                },
                                                style = ArcadeActionStyle.OUTLINE,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.social.incomingRequests.isNotEmpty()) {
                            item {
                                SocialSectionTitle(
                                    "Lời mời kết bạn",
                                    "${state.social.incomingRequests.size} mới"
                                )
                            }
                            items(
                                state.social.incomingRequests,
                                key = { "incoming:${it.requestId}" }
                            ) { request ->
                                SocialPanel {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            PlayerAvatar(
                                                displayName = request.displayName,
                                                avatarId = request.avatarId,
                                                frameId = request.frameId
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(request.displayName, fontWeight = FontWeight.Black)
                                                Text(
                                                    "Mã: ${request.playerCode}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    blockTarget = PlayerActionTarget(
                                                        request.userId,
                                                        request.displayName
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Block,
                                                    contentDescription = "Chặn ${request.displayName}",
                                                    tint = ArcadePalette.Coral400
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ArcadeActionButton(
                                                label = "CHẤP NHẬN",
                                                onClick = {
                                                    onRespondRequest(request.requestId, true)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            ArcadeActionButton(
                                                label = "TỪ CHỐI",
                                                onClick = {
                                                    onRespondRequest(request.requestId, false)
                                                },
                                                style = ArcadeActionStyle.OUTLINE,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val activeFriends = state.social.friends.count {
                            it.presence != FriendPresence.OFFLINE
                        }
                        item {
                            SocialSectionTitle("Danh sách bạn bè", "$activeFriends online")
                        }
                        if (state.social.friends.isEmpty()) {
                            item {
                                NoticePanel(
                                    "Chưa có đồng đội. Nhập mã người chơi để gửi lời mời.",
                                    ArcadePalette.Blue300
                                )
                            }
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

                        if (state.social.outgoingRequests.isNotEmpty()) {
                            item {
                                SocialSectionTitle(
                                    "Đang chờ phản hồi",
                                    state.social.outgoingRequests.size.toString()
                                )
                            }
                            items(
                                state.social.outgoingRequests,
                                key = { "outgoing:${it.requestId}" }
                            ) { request ->
                                SocialPanel {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        PlayerAvatar(
                                            displayName = request.displayName,
                                            avatarId = request.avatarId,
                                            frameId = request.frameId
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(request.displayName, fontWeight = FontWeight.Black)
                                            Text(
                                                "Đã gửi lời mời · ${request.playerCode}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        ArcadeActionButton(
                                            label = "HỦY",
                                            onClick = { onCancelRequest(request.requestId) },
                                            style = ArcadeActionStyle.OUTLINE,
                                            modifier = Modifier.width(104.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (state.social.blockedPlayers.isNotEmpty()) {
                            item {
                                SocialSectionTitle(
                                    "Đã chặn",
                                    state.social.blockedPlayers.size.toString()
                                )
                            }
                            items(
                                state.social.blockedPlayers,
                                key = { "blocked:${it.userId}" }
                            ) { player ->
                                SocialPanel(accent = ArcadePalette.Coral400) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        PlayerAvatar(
                                            displayName = player.displayName,
                                            avatarId = player.avatarId,
                                            frameId = player.frameId
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(player.displayName, fontWeight = FontWeight.Black)
                                            Text(
                                                "Mã: ${player.playerCode}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        ArcadeActionButton(
                                            label = "BỎ CHẶN",
                                            onClick = { onUnblockPlayer(player.userId) },
                                            style = ArcadeActionStyle.OUTLINE,
                                            modifier = Modifier.width(118.dp)
                                        )
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
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Blue300) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (maxWidth < 380.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FriendCodeField(playerCode, onPlayerCodeChange, Modifier.fillMaxWidth())
                    ArcadeActionButton(
                        label = "KẾT BẠN",
                        onClick = onSubmit,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FriendCodeField(playerCode, onPlayerCodeChange, Modifier.weight(1f))
                    ArcadeActionButton(
                        label = "KẾT BẠN",
                        onClick = onSubmit,
                        enabled = enabled,
                        modifier = Modifier.width(132.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Mã người chơi") },
        placeholder = { Text("VD: FTW8X2Q") },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun SocialSectionTitle(title: String, meta: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            meta,
            style = MaterialTheme.typography.labelLarge,
            color = ArcadePalette.Gold500,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NoticePanel(message: String, accent: Color) {
    SocialPanel(accent = accent) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SocialPanel(
    modifier: Modifier = Modifier,
    accent: Color = ArcadePalette.Blue300,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        shadowElevation = 2.dp,
        content = content
    )
}

@Composable
private fun ClickableSocialPanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.42f)),
        shadowElevation = 2.dp,
        content = content
    )
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
    ClickableSocialPanel(
        onClick = onViewInfo,
        modifier = Modifier.testTag("friend_item:${friend.userId}")
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
                PlayerAvatar(
                    displayName = friend.displayName,
                    avatarId = friend.avatarId,
                    frameId = friend.frameId
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(friend.displayName, fontWeight = FontWeight.Black)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(friend.playerCode, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FriendPresenceIndicator(friend.presence)
                    }
                }
                Box {
                    IconButton(
                        onClick = { showActions = true },
                        modifier = Modifier.testTag("friend_more:${friend.userId}")
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Thao tác với ${friend.displayName}"
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false },
                        containerColor = ArcadePalette.Navy800
                    ) {
                        DropdownMenuItem(
                            text = { Text("Hủy kết bạn", color = ArcadePalette.White) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PersonRemove,
                                    contentDescription = null,
                                    tint = ArcadePalette.White
                                )
                            },
                            onClick = {
                                showActions = false
                                onRemoveFriend()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Chặn người chơi", color = ArcadePalette.Coral400) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = ArcadePalette.Coral400
                                )
                            },
                            onClick = {
                                showActions = false
                                onBlockPlayer()
                            }
                        )
                    }
                }
            }
            if (canInvite) {
                ArcadeActionButton(
                    label = when {
                        isInvited -> "Đã mời"
                        isSendingInvitation -> "Đang gửi…"
                        else -> "Mời vào phòng"
                    },
                    onClick = { onInviteFriend(friend.userId) },
                    enabled = friend.presence == FriendPresence.ONLINE &&
                        !isSendingInvitation && !isInvited,
                    style = if (isInvited) ArcadeActionStyle.OUTLINE else ArcadeActionStyle.GOLD,
                    modifier = Modifier.fillMaxWidth()
                )
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
    ArcadeDialog(
        title = "LỜI MỜI VÀO PHÒNG",
        subtitle = "${invitation.fromDisplayName} đang chờ bạn",
        onDismissRequest = onDefer
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ArcadePalette.Navy900,
            border = BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text("PHÒNG", style = MaterialTheme.typography.labelSmall, color = ArcadePalette.Blue100)
                Text(
                    invitation.roomName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ArcadePalette.White
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ArcadeActionButton(
                label = "TỪ CHỐI",
                onClick = { onRespond(false) },
                style = ArcadeActionStyle.DANGER,
                modifier = Modifier.weight(1f)
            )
            ArcadeActionButton(
                label = "THAM GIA",
                onClick = { onRespond(true) },
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
