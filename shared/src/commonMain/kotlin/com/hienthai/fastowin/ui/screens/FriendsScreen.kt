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
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeIconHero
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.FriendPresenceIndicator
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
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
    var visibleRoomInvitationCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    var visibleIncomingRequestCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    var visibleFriendCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    var visibleOutgoingRequestCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    var visibleBlockedPlayerCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
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
            title = localized(TextKey.RemoveFriendTitle),
            subtitle = localized(TextKey.RemoveFriendDescription, "player" to target.displayName),
            onDismissRequest = { removeTarget = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = localized(TextKey.RemoveFriendAction),
                    onClick = {
                        onRemoveFriend(target.userId)
                        removeTarget = null
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = localized(TextKey.GoBack),
                    onClick = { removeTarget = null },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    blockTarget?.let { target ->
        ArcadeDialog(
            title = localized(TextKey.BlockPlayerTitle),
            subtitle = localized(TextKey.BlockFriendDescription, "player" to target.displayName),
            onDismissRequest = { blockTarget = null }
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = localized(TextKey.Block),
                    onClick = {
                        onBlockPlayer(target.userId)
                        blockTarget = null
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = localized(TextKey.GoBack),
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
        FastToWinPullRefresh(
            isRefreshing = state.isFriendsLoading,
            onRefresh = { if (!state.isFriendsLoading) onRefresh() },
            modifier = contentModifier
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                if (showBackButton) {
                    FastToWinHeader(
                        title = localized(TextKey.Friends),
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
                            kicker = localized(TextKey.SocialHub),
                            title = localized(TextKey.YourSquad),
                            subtitle = localized(TextKey.YourSquadDescription),
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
                                SocialSectionTitle(localized(TextKey.RoomInvitations), localized(TextKey.NewCount, "count" to state.roomInvitations.size))
                            }
                            items(
                                state.roomInvitations.take(visibleRoomInvitationCount),
                                key = { "room:${it.invitationId}" }
                            ) { invitation ->
                                SocialPanel {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(invitation.fromDisplayName, fontWeight = FontWeight.Black)
                                        Text(
                                            localized(TextKey.RoomInvitationSummary, "room" to invitation.roomName),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ArcadeActionButton(
                                                label = localized(TextKey.JoinRoom),
                                                onClick = {
                                                    onRespondRoomInvitation(invitation.invitationId, true)
                                                },
                                                style = ArcadeActionStyle.GOLD,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            ArcadeActionButton(
                                                label = localized(TextKey.Decline),
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
                            item(key = "room_invitations_load_more") {
                                ArcadeLoadMoreButton(
                                    visibleItemCount = state.roomInvitations.take(visibleRoomInvitationCount).size,
                                    totalItemCount = state.roomInvitations.size,
                                    onLoadMore = {
                                        visibleRoomInvitationCount = nextArcadePageItemCount(
                                            visibleRoomInvitationCount,
                                            state.roomInvitations.size
                                        )
                                    },
                                    testTag = "room_invitations_load_more"
                                )
                            }
                        }

                        if (state.social.incomingRequests.isNotEmpty()) {
                            item {
                                SocialSectionTitle(
                                    localized(TextKey.FriendRequests),
                                    localized(TextKey.NewCount, "count" to state.social.incomingRequests.size)
                                )
                            }
                            items(
                                state.social.incomingRequests.take(visibleIncomingRequestCount),
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
                                                userId = request.userId,
                                                frameId = request.frameId
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(request.displayName, fontWeight = FontWeight.Black)
                                                Text(
                                                    localized(TextKey.PlayerCodeShort, "code" to request.playerCode),
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
                                                    contentDescription = localized(TextKey.BlockPlayerNamed, "player" to request.displayName),
                                                    tint = ArcadePalette.Coral400
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ArcadeActionButton(
                                                label = localized(TextKey.Accept),
                                                onClick = {
                                                    onRespondRequest(request.requestId, true)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            ArcadeActionButton(
                                                label = localized(TextKey.Decline),
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
                            item(key = "incoming_requests_load_more") {
                                ArcadeLoadMoreButton(
                                    visibleItemCount = state.social.incomingRequests.take(visibleIncomingRequestCount).size,
                                    totalItemCount = state.social.incomingRequests.size,
                                    onLoadMore = {
                                        visibleIncomingRequestCount = nextArcadePageItemCount(
                                            visibleIncomingRequestCount,
                                            state.social.incomingRequests.size
                                        )
                                    },
                                    testTag = "incoming_requests_load_more"
                                )
                            }
                        }

                        val activeFriends = state.social.friends.count {
                            it.presence != FriendPresence.OFFLINE
                        }
                        item {
                            SocialSectionTitle(localized(TextKey.FriendList), localized(TextKey.ActiveFriends, "count" to activeFriends))
                        }
                        if (state.social.friends.isEmpty()) {
                            item {
                                NoticePanel(
                                    localized(TextKey.NoFriendsDescription),
                                    ArcadePalette.Blue300
                                )
                            }
                        } else {
                            items(
                                state.social.friends.take(visibleFriendCount),
                                key = { "friend:${it.userId}" }
                            ) { friend ->
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
                            item(key = "friends_load_more") {
                                ArcadeLoadMoreButton(
                                    visibleItemCount = state.social.friends.take(visibleFriendCount).size,
                                    totalItemCount = state.social.friends.size,
                                    onLoadMore = {
                                        visibleFriendCount = nextArcadePageItemCount(
                                            visibleFriendCount,
                                            state.social.friends.size
                                        )
                                    },
                                    testTag = "friends_load_more"
                                )
                            }
                        }

                        if (state.social.outgoingRequests.isNotEmpty()) {
                            item {
                                SocialSectionTitle(
                                    localized(TextKey.PendingReplies),
                                    state.social.outgoingRequests.size.toString()
                                )
                            }
                            items(
                                state.social.outgoingRequests.take(visibleOutgoingRequestCount),
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
                                            userId = request.userId,
                                            frameId = request.frameId
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(request.displayName, fontWeight = FontWeight.Black)
                                            Text(
                                                localized(TextKey.SentFriendInvitation, "code" to request.playerCode),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        ArcadeActionButton(
                                            label = localized(TextKey.Cancel),
                                            onClick = { onCancelRequest(request.requestId) },
                                            style = ArcadeActionStyle.OUTLINE,
                                            modifier = Modifier.width(104.dp)
                                        )
                                    }
                                }
                            }
                            item(key = "outgoing_requests_load_more") {
                                ArcadeLoadMoreButton(
                                    visibleItemCount = state.social.outgoingRequests.take(visibleOutgoingRequestCount).size,
                                    totalItemCount = state.social.outgoingRequests.size,
                                    onLoadMore = {
                                        visibleOutgoingRequestCount = nextArcadePageItemCount(
                                            visibleOutgoingRequestCount,
                                            state.social.outgoingRequests.size
                                        )
                                    },
                                    testTag = "outgoing_requests_load_more"
                                )
                            }
                        }

                        if (state.social.blockedPlayers.isNotEmpty()) {
                            item {
                                SocialSectionTitle(
                                    localized(TextKey.BlockedPlayers),
                                    state.social.blockedPlayers.size.toString()
                                )
                            }
                            items(
                                state.social.blockedPlayers.take(visibleBlockedPlayerCount),
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
                                            userId = player.userId,
                                            frameId = player.frameId
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(player.displayName, fontWeight = FontWeight.Black)
                                            Text(
                                                localized(TextKey.PlayerCodeShort, "code" to player.playerCode),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        ArcadeActionButton(
                                            label = localized(TextKey.Unblock),
                                            onClick = { onUnblockPlayer(player.userId) },
                                            style = ArcadeActionStyle.OUTLINE,
                                            modifier = Modifier.width(118.dp)
                                        )
                                    }
                                }
                            }
                            item(key = "blocked_players_load_more") {
                                ArcadeLoadMoreButton(
                                    visibleItemCount = state.social.blockedPlayers.take(visibleBlockedPlayerCount).size,
                                    totalItemCount = state.social.blockedPlayers.size,
                                    onLoadMore = {
                                        visibleBlockedPlayerCount = nextArcadePageItemCount(
                                            visibleBlockedPlayerCount,
                                            state.social.blockedPlayers.size
                                        )
                                    },
                                    testTag = "blocked_players_load_more"
                                )
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
                        label = localized(TextKey.AddFriend),
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
                        label = localized(TextKey.AddFriend),
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
        label = { Text(localized(TextKey.PlayerCodeLabel)) },
        placeholder = { Text(localized(TextKey.PlayerCodeExample)) },
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
                    userId = friend.userId,
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
                            contentDescription = localized(TextKey.PlayerActions, "player" to friend.displayName)
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false },
                        containerColor = ArcadePalette.Navy800
                    ) {
                        DropdownMenuItem(
                            text = { Text(localized(TextKey.RemoveFriendAction), color = ArcadePalette.White) },
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
                            text = { Text(localized(TextKey.Block), color = ArcadePalette.Coral400) },
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
                        isInvited -> localized(TextKey.InviteSent)
                        isSendingInvitation -> localized(TextKey.SendingInvitation)
                        else -> localized(TextKey.InviteToRoom)
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
        title = localized(TextKey.RoomInvitationTitle),
        subtitle = localized(TextKey.WaitingForYou, "player" to invitation.fromDisplayName),
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
                Text(localized(TextKey.RoomLabel), style = MaterialTheme.typography.labelSmall, color = ArcadePalette.Blue100)
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
                label = localized(TextKey.Decline),
                onClick = { onRespond(false) },
                style = ArcadeActionStyle.DANGER,
                modifier = Modifier.weight(1f)
            )
            ArcadeActionButton(
                label = localized(TextKey.JoinRoom),
                onClick = { onRespond(true) },
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
