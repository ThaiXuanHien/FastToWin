package com.hienthai.fastowin.ui.screens

import com.hienthai.fastowin.ui.components.UgcReportMenuItem
import com.hienthai.fastowin.protocol.UgcTargetType

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
import com.hienthai.fastowin.ui.components.AppOutlinedTextField as OutlinedTextField
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
import com.hienthai.fastowin.protocol.FriendRequestSnapshot
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
import com.hienthai.fastowin.ui.components.HeaderRefreshAction
import com.hienthai.fastowin.ui.components.FriendPresenceIndicator
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.accessibilityLabel
import com.hienthai.fastowin.ui.theme.A1Palette
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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
            title = localized(TextKey.BlockPlayerTitle, "player" to target.displayName),
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
        modifier = modifier.testTag("friends_screen"),
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
                        gold = 0,
                        gems = 0,
                        unreadNotifications = 0,
                        onNotifications = onOpenNotifications,
                        onBack = onBack,
                        applySafeDrawingInset = false,
                        showBalances = false,
                        showNotifications = false,
                        actions = {
                            val online = state.social.friends.count { it.presence != FriendPresence.OFFLINE }
                            A1Tag(
                                "● " + localized(TextKey.FriendsOnlineCount, "count" to online),
                                A1TagTone.GREEN,
                                large = true,
                                modifier = Modifier.testTag("friends_online_count")
                            )
                            HeaderRefreshAction(isRefreshing = state.isFriendsLoading, onRefresh = onRefresh)
                        }
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item(key = "friend_code") {
                        FriendCodeCard(
                            ownCode = state.profile?.playerCode,
                            playerCode = playerCode,
                            onPlayerCodeChange = { playerCode = it.uppercase().take(12) },
                            onSubmit = { onSendRequest(playerCode); playerCode = "" },
                            enabled = playerCode.isNotBlank() && !state.isFriendsLoading
                        )
                    }
                    state.socialNotice?.let { notice ->
                        item { NoticePanel(notice, isError = false) }
                    }
                    state.error?.let { error ->
                        item { NoticePanel(error, isError = true) }
                    }

                    if (isInitialLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = A1Palette.Blue2)
                            }
                        }
                    } else {
                        // Lời mời vào phòng: thẻ tím, nút VÀO NGAY xanh lá + Từ chối.
                        items(
                            state.roomInvitations.take(visibleRoomInvitationCount),
                            key = { "room:${it.invitationId}" }
                        ) { invitation ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .a1Card(background = Color(0xFFF4EFFE), border = A1Palette.PurpleDeep)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("room_invitation:${invitation.invitationId}"),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PlayerAvatar(invitation.fromDisplayName, avatarId = null, size = 48.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(localized(TextKey.RoomInvitationCaps), style = MaterialTheme.typography.titleSmall,
                                            color = A1Palette.PurpleDeep)
                                        Text(
                                            invitation.fromDisplayName + " " + localized(TextKey.RoomInvitationSummary, "room" to invitation.roomName)
                                                .replaceFirstChar { it.lowercase() },
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = A1Palette.Ink
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ArcadeActionButton(
                                        label = localized(TextKey.JoinNowCaps),
                                        onClick = { onRespondRoomInvitation(invitation.invitationId, true) },
                                        style = ArcadeActionStyle.SUCCESS,
                                        compact = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ArcadeActionButton(
                                        label = localized(TextKey.Decline),
                                        onClick = { onRespondRoomInvitation(invitation.invitationId, false) },
                                        style = ArcadeActionStyle.OUTLINE,
                                        compact = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        if (state.roomInvitations.size > visibleRoomInvitationCount) {
                            item(key = "room_invitations_load_more") {
                                ArcadeLoadMoreButton(
                                    visibleItemCount = state.roomInvitations.take(visibleRoomInvitationCount).size,
                                    totalItemCount = state.roomInvitations.size,
                                    onLoadMore = {
                                        visibleRoomInvitationCount = nextArcadePageItemCount(visibleRoomInvitationCount, state.roomInvitations.size)
                                    },
                                    testTag = "room_invitations_load_more"
                                )
                            }
                        }

                        if (state.social.incomingRequests.isNotEmpty()) {
                            item(key = "incoming_requests") {
                                SocialCard(
                                    title = localized(TextKey.FriendRequests),
                                    meta = { A1Tag(localized(TextKey.NewCount, "count" to state.social.incomingRequests.size), A1TagTone.RED) }
                                ) {
                                    val visible = state.social.incomingRequests.take(visibleIncomingRequestCount)
                                    visible.forEachIndexed { index, request ->
                                        SocialRow(
                                            avatar = { PlayerAvatar(request.displayName, request.avatarId, request.userId, request.frameId, size = 52.dp) },
                                            title = request.displayName,
                                            subtitle = localized(TextKey.PlayerCodeShort, "code" to request.playerCode),
                                            showDivider = index < visible.lastIndex,
                                            modifier = Modifier.testTag("incoming_request:${request.requestId}")
                                        ) {
                                            SquareActionButton(
                                                icon = Icons.Default.Check,
                                                description = localized(TextKey.Accept),
                                                style = ArcadeActionStyle.SUCCESS,
                                                onClick = { onRespondRequest(request.requestId, true) }
                                            )
                                            SquareActionButton(
                                                icon = Icons.Default.Close,
                                                description = localized(TextKey.Decline),
                                                style = ArcadeActionStyle.OUTLINE,
                                                onClick = { onRespondRequest(request.requestId, false) }
                                            )
                                            IconButton(onClick = { blockTarget = PlayerActionTarget(request.userId, request.displayName) }) {
                                                Icon(Icons.Default.Block,
                                                    contentDescription = localized(TextKey.BlockPlayerNamed, "player" to request.displayName),
                                                    tint = A1Palette.RedText)
                                            }
                                        }
                                    }
                                    if (state.social.incomingRequests.size > visible.size) {
                                        ArcadeLoadMoreButton(
                                            visibleItemCount = visible.size,
                                            totalItemCount = state.social.incomingRequests.size,
                                            onLoadMore = {
                                                visibleIncomingRequestCount = nextArcadePageItemCount(visibleIncomingRequestCount, state.social.incomingRequests.size)
                                            },
                                            modifier = Modifier.padding(12.dp),
                                            testTag = "incoming_requests_load_more"
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "friend_list") {
                            SocialCard(
                                title = localized(TextKey.FriendList),
                                meta = {
                                    Text(state.social.friends.size.toString(), style = MaterialTheme.typography.titleSmall, color = A1Palette.Muted)
                                }
                            ) {
                                if (state.social.friends.isEmpty()) {
                                    Text(
                                        localized(TextKey.NoFriendsDescription),
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = A1Palette.Muted
                                    )
                                }
                                val visible = state.social.friends.take(visibleFriendCount)
                                visible.forEachIndexed { index, friend ->
                                    FriendCard(
                                        friend = friend,
                                        canInvite = canInvite,
                                        isSendingInvitation = friend.userId in state.sendingRoomInviteFriendIds,
                                        isInvited = friend.userId in state.invitedRoomFriendIds,
                                        onInviteFriend = onInviteFriend,
                                        onViewInfo = { onOpenFriendProfile(friend.userId) },
                                        onRemoveFriend = { removeTarget = PlayerActionTarget(friend.userId, friend.displayName) },
                                        onBlockPlayer = { blockTarget = PlayerActionTarget(friend.userId, friend.displayName) },
                                        showDivider = index < visible.lastIndex
                                    )
                                }
                                if (state.social.friends.size > visible.size) {
                                    ArcadeLoadMoreButton(
                                        visibleItemCount = visible.size,
                                        totalItemCount = state.social.friends.size,
                                        onLoadMore = { visibleFriendCount = nextArcadePageItemCount(visibleFriendCount, state.social.friends.size) },
                                        modifier = Modifier.padding(12.dp),
                                        testTag = "friends_load_more"
                                    )
                                }
                            }
                        }

                        if (state.social.outgoingRequests.isNotEmpty()) {
                            item(key = "outgoing_requests") {
                                SocialCard(
                                    title = localized(TextKey.PendingReplies),
                                    meta = { Text(state.social.outgoingRequests.size.toString(), style = MaterialTheme.typography.titleSmall, color = A1Palette.Muted) }
                                ) {
                                    val visible = state.social.outgoingRequests.take(visibleOutgoingRequestCount)
                                    visible.forEachIndexed { index, request ->
                                        SocialRow(
                                            avatar = { PlayerAvatar(request.displayName, request.avatarId, request.userId, request.frameId, size = 52.dp) },
                                            title = request.displayName,
                                            subtitle = localized(TextKey.SentFriendInvitation, "code" to request.playerCode),
                                            showDivider = index < visible.lastIndex
                                        ) {
                                            ArcadeActionButton(
                                                label = localized(TextKey.Cancel),
                                                onClick = { onCancelRequest(request.requestId) },
                                                style = ArcadeActionStyle.OUTLINE,
                                                compact = true
                                            )
                                        }
                                    }
                                    if (state.social.outgoingRequests.size > visible.size) {
                                        ArcadeLoadMoreButton(
                                            visibleItemCount = visible.size,
                                            totalItemCount = state.social.outgoingRequests.size,
                                            onLoadMore = {
                                                visibleOutgoingRequestCount = nextArcadePageItemCount(visibleOutgoingRequestCount, state.social.outgoingRequests.size)
                                            },
                                            modifier = Modifier.padding(12.dp),
                                            testTag = "outgoing_requests_load_more"
                                        )
                                    }
                                }
                            }
                        }

                        if (state.social.blockedPlayers.isNotEmpty()) {
                            item(key = "blocked_players") {
                                SocialCard(
                                    title = localized(TextKey.BlockedPlayers),
                                    meta = { Text(state.social.blockedPlayers.size.toString(), style = MaterialTheme.typography.titleSmall, color = A1Palette.Muted) }
                                ) {
                                    val visible = state.social.blockedPlayers.take(visibleBlockedPlayerCount)
                                    visible.forEachIndexed { index, player ->
                                        SocialRow(
                                            avatar = { PlayerAvatar(player.displayName, player.avatarId, player.userId, player.frameId, size = 52.dp) },
                                            title = player.displayName,
                                            subtitle = localized(TextKey.PlayerCodeShort, "code" to player.playerCode),
                                            showDivider = index < visible.lastIndex
                                        ) {
                                            ArcadeActionButton(
                                                label = localized(TextKey.Unblock),
                                                onClick = { onUnblockPlayer(player.userId) },
                                                style = ArcadeActionStyle.OUTLINE,
                                                compact = true
                                            )
                                        }
                                    }
                                    if (state.social.blockedPlayers.size > visible.size) {
                                        ArcadeLoadMoreButton(
                                            visibleItemCount = visible.size,
                                            totalItemCount = state.social.blockedPlayers.size,
                                            onLoadMore = {
                                                visibleBlockedPlayerCount = nextArcadePageItemCount(visibleBlockedPlayerCount, state.social.blockedPlayers.size)
                                            },
                                            modifier = Modifier.padding(12.dp),
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
    }
}

/** Thẻ "Mã của bạn": mã người chơi lớn + nút sao chép; ô nhập mã bạn bè và nút xanh Kết bạn. */
@Composable
private fun FriendCodeCard(
    ownCode: String?,
    playerCode: String,
    onPlayerCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(ownCode) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (ownCode != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(localized(TextKey.FriendsYourCode), style = MaterialTheme.typography.titleSmall, color = A1Palette.Muted)
                    Text(
                        ownCode,
                        style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 3.sp),
                        color = A1Palette.Ink,
                        modifier = Modifier.testTag("own_player_code")
                    )
                }
                com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton(onClick = {
                    clipboard.setText(AnnotatedString(ownCode))
                    copied = true
                }) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = localized(if (copied) TextKey.Copied else TextKey.CopyPlayerCode),
                        tint = A1Palette.Ink
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = playerCode,
                onValueChange = onPlayerCodeChange,
                placeholder = localized(TextKey.PlayerCodeExample),
                leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = A1Palette.Muted) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("friend_code_field")
            )
            ArcadeActionButton(
                label = localized(TextKey.AddFriend),
                onClick = onSubmit,
                enabled = enabled,
                style = ArcadeActionStyle.PRIMARY,
                modifier = Modifier.width(124.dp)
            )
        }
    }
}

/** Thẻ nhóm (".card"): tiêu đề + phần phụ bên phải, các dòng gạch đứt bên dưới. */
@Composable
private fun SocialCard(title: String, meta: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().a1Card().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = A1Palette.Ink,
                modifier = Modifier.weight(1f).semantics { heading() })
            meta()
        }
        content()
    }
}

@Composable
private fun SocialRow(
    avatar: @Composable () -> Unit,
    title: String,
    subtitle: String,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    subtitleColor: Color = A1Palette.Muted,
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .then(if (showDivider) Modifier.drawBehind {
                drawLine(
                    A1Palette.SurfaceAlt2,
                    Offset(0f, size.height - 1.dp.toPx()),
                    Offset(size.width, size.height - 1.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            } else Modifier)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        avatar()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = subtitleColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Nút vuông 3D nhỏ (✓ xanh lá / ✕ trắng) cho lời mời kết bạn. */
@Composable
private fun SquareActionButton(icon: ImageVector, description: String, style: ArcadeActionStyle, onClick: () -> Unit) {
    ArcadeActionButton(
        label = description,
        onClick = onClick,
        style = style,
        compact = true,
        modifier = Modifier.width(52.dp)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(26.dp),
            tint = if (style == ArcadeActionStyle.OUTLINE) A1Palette.Ink else Color.White)
    }
}

@Composable
private fun NoticePanel(message: String, isError: Boolean) {
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isError) Color(0xFFFFE0E0) else Color(0xFFDFF8D0), RoundedCornerShape(14.dp))
            .border(2.dp, if (isError) Color(0xFFF08A8A) else Color(0xFF6CC24A), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (isError) A1Palette.RedDeep else A1Palette.GreenDeep
    )
}

/** Dòng bạn bè: avatar có chấm trạng thái, tên, trạng thái màu, nút Mời / nhãn Trong trận, menu ba chấm. */
@Composable
private fun FriendCard(
    friend: FriendSnapshot,
    canInvite: Boolean,
    isSendingInvitation: Boolean,
    isInvited: Boolean,
    onInviteFriend: (String) -> Unit,
    onViewInfo: () -> Unit,
    onRemoveFriend: () -> Unit,
    onBlockPlayer: () -> Unit,
    showDivider: Boolean
) {
    var showActions by remember(friend.userId) { mutableStateOf(false) }
    val statusColor = when (friend.presence) {
        FriendPresence.ONLINE -> A1Palette.GreenText
        FriendPresence.PLAYING, FriendPresence.IN_ROOM -> A1Palette.Ink
        FriendPresence.OFFLINE -> A1Palette.Muted
    }
    SocialRow(
        avatar = {
            Box {
                PlayerAvatar(friend.displayName, friend.avatarId, friend.userId, friend.frameId, size = 52.dp)
                if (friend.presence != FriendPresence.OFFLINE) {
                    FriendPresenceIndicator(friend.presence, Modifier.align(Alignment.BottomEnd))
                }
            }
        },
        title = friend.displayName,
        subtitle = friend.presence.accessibilityLabel(),
        subtitleColor = statusColor,
        showDivider = showDivider,
        onClick = onViewInfo,
        modifier = Modifier.testTag("friend_item:${friend.userId}")
    ) {
        if (friend.presence == FriendPresence.PLAYING) {
            A1Tag(localized(TextKey.InMatchShort), A1TagTone.INK, large = true)
        } else if (canInvite) {
            ArcadeActionButton(
                label = when {
                    isInvited -> localized(TextKey.InviteSent)
                    isSendingInvitation -> localized(TextKey.SendingInvitation)
                    else -> localized(TextKey.InviteShort)
                },
                onClick = { onInviteFriend(friend.userId) },
                enabled = isInvitablePresence(friend.presence) && !isSendingInvitation && !isInvited,
                style = if (isInvited) ArcadeActionStyle.OUTLINE else ArcadeActionStyle.PRIMARY,
                compact = true,
                modifier = Modifier.widthIn(min = 84.dp)
            )
        }
        Box {
            IconButton(
                onClick = { showActions = true },
                modifier = Modifier.testTag("friend_more:${friend.userId}")
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = localized(TextKey.PlayerActions, "player" to friend.displayName),
                    tint = A1Palette.Muted
                )
            }
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                UgcReportMenuItem(UgcTargetType.PLAYER_PROFILE, friend.userId, friend.displayName) {
                    showActions = false
                }
                DropdownMenuItem(
                    text = { Text(localized(TextKey.RemoveFriendAction), color = A1Palette.Ink) },
                    leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = A1Palette.Ink) },
                    onClick = {
                        showActions = false
                        onRemoveFriend()
                    }
                )
                DropdownMenuItem(
                    text = { Text(localized(TextKey.Block), color = A1Palette.RedText) },
                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = A1Palette.RedText) },
                    onClick = {
                        showActions = false
                        onBlockPlayer()
                    }
                )
            }
        }
    }
}

/**
 * Offline friends can still be invited: the server stores the invitation and sends a push.
 * iOS suspends a backgrounded app, so its player shows offline while still reachable by push.
 */
internal fun isInvitablePresence(presence: FriendPresence): Boolean =
    presence == FriendPresence.ONLINE || presence == FriendPresence.OFFLINE

private data class PlayerActionTarget(val userId: String, val displayName: String)

internal fun nextFriendRequestPrompt(
    incomingRequests: List<FriendRequestSnapshot>,
    deferredRequestIds: Set<String>
): FriendRequestSnapshot? = incomingRequests.firstOrNull { it.requestId !in deferredRequestIds }

@Composable
fun FriendRequestDialog(
    request: FriendRequestSnapshot,
    isResponding: Boolean,
    onAccept: () -> Unit,
    onDefer: () -> Unit,
    onDecline: () -> Unit
) {
    ArcadeDialog(
        title = localized(TextKey.FriendRequests),
        subtitle = localized(TextKey.NotificationFriendRequestMessage, "player" to request.displayName),
        onDismissRequest = onDefer
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(request.displayName, fontWeight = FontWeight.Black)
                Text(
                    localized(TextKey.PlayerCodeShort, "code" to request.playerCode),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArcadeActionButton(
                label = localized(if (isResponding) TextKey.Processing else TextKey.Accept),
                onClick = onAccept,
                enabled = !isResponding,
                modifier = Modifier.fillMaxWidth()
            )
            ArcadeActionButton(
                label = localized(TextKey.Later),
                onClick = onDefer,
                enabled = !isResponding,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            )
            ArcadeActionButton(
                label = localized(TextKey.Decline),
                onClick = onDecline,
                enabled = !isResponding,
                style = ArcadeActionStyle.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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
