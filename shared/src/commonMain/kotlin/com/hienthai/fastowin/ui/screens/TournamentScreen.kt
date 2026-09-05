package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.protocol.FriendPresence
import com.hienthai.fastowin.protocol.ProtocolGameMode
import com.hienthai.fastowin.protocol.TournamentInvitationSnapshot
import com.hienthai.fastowin.protocol.TournamentMatchPhase
import com.hienthai.fastowin.protocol.TournamentMatchSnapshot
import com.hienthai.fastowin.protocol.TournamentPhase
import com.hienthai.fastowin.protocol.TournamentSnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.FriendPresenceIndicator
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.OnlineStatusIndicator
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_tournament_trophy
import com.hienthai.fastowin.ui.theme.ArcadePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    state: GameState,
    onBack: () -> Unit,
    onCreate: (String, GameMode, Int, Int) -> Unit,
    onInvite: (String) -> Unit,
    onRespondInvitation: (String, Boolean) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)

    val tournament = state.tournamentHub.activeTournament
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("tournament_screen"),
        containerColor = Color.Transparent,
        topBar = {
            FastToWinHeader(
                title = "Đấu giải",
                gold = state.profile?.progression?.gold ?: 0,
                gems = state.profile?.progression?.gems ?: 0,
                unreadNotifications = state.unreadNotificationCount,
                onNotifications = onOpenNotifications,
                onBack = onBack
            )
        }
    ) { paddingValues ->
        ResponsiveScreen(
            modifier = Modifier.padding(paddingValues),
            maxContentWidth = 760.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Column(
                modifier = contentModifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val heroTitle: String
                val heroSubtitle: String
                if (tournament == null) {
                    heroTitle = "Đấu trường loại trực tiếp"
                    heroSubtitle = "Tập hợp 4, 8 hoặc 16 chiến binh và chạm tay vào cúp vô địch."
                } else {
                    val championName = tournament.players
                        .firstOrNull { it.playerId == tournament.championPlayerId }
                        ?.displayName
                    heroTitle = when (tournament.phase) {
                        TournamentPhase.LOBBY -> "Sảnh giải đấu"
                        TournamentPhase.RUNNING -> tournament.name
                        TournamentPhase.FINISHED -> "Nhà vô địch: ${championName ?: "Đang cập nhật"}"
                        TournamentPhase.CANCELLED -> "Giải đấu đã hủy"
                    }
                    heroSubtitle = when (tournament.phase) {
                        TournamentPhase.LOBBY -> "${tournament.gameMode.title()} · ${tournament.players.size}/${tournament.maxPlayers} người · ${tournament.prizePool} vàng"
                        TournamentPhase.RUNNING -> "Nhánh đấu đang diễn ra. Người thắng sẽ tiến vào vòng tiếp theo."
                        TournamentPhase.FINISHED -> "Phần thưởng ${tournament.prizePool} vàng đã được trao."
                        TournamentPhase.CANCELLED -> "Bạn có thể tạo một giải đấu mới ngay bây giờ."
                    }
                }
                ArcadeFeatureHero(
                    illustration = Res.drawable.arcade_tournament_trophy,
                    title = heroTitle,
                    subtitle = heroSubtitle,
                    accent = ArcadePalette.Gold500
                )
                state.error?.let { MessageCard(it, isError = true) }
                state.tournamentNotice?.let { MessageCard(it, isError = false) }
                if (state.isTournamentLoading && tournament == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
                if (tournament == null) {
                    if (state.tournamentHub.invitations.isNotEmpty()) {
                        SectionLabel("Lời mời tham gia")
                        state.tournamentHub.invitations.forEach { invitation ->
                            InvitationCard(invitation, onRespondInvitation)
                        }
                    }
                    CreateTournamentCard(
                        playerLevel = state.profile?.progression?.level ?: 1,
                        enabled = !state.isTournamentLoading,
                        onCreate = onCreate
                    )
                    if (state.tournamentHub.recentTournaments.isNotEmpty()) {
                        SectionLabel("Giải gần đây")
                        state.tournamentHub.recentTournaments.forEach { history ->
                            TournamentHistoryCard(
                                tournament = history,
                                initiallyExpanded = history.tournamentId == state.currentTournamentId
                            )
                        }
                    }
                } else {
                    ActiveTournamentContent(
                        state = state,
                        tournament = tournament,
                        onInvite = onInvite,
                        onStart = onStart,
                        onLeave = onLeave,
                        onOpenFriendProfile = onOpenFriendProfile
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun TournamentInvitationDialog(
    invitation: TournamentInvitationSnapshot,
    onRespond: (Boolean) -> Unit,
    onDefer: () -> Unit
) {
    ArcadeDialog(
        title = "Lời mời đấu giải",
        subtitle = "${invitation.hostDisplayName} mời bạn tham gia “${invitation.tournamentName}” · ${invitation.gameMode.title()} · ${invitation.maxPlayers} người.",
        onDismissRequest = onDefer,
        modifier = Modifier.testTag("tournament_invitation_dialog")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeActionButton(
                label = "THAM GIA",
                onClick = { onRespond(true) },
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth()
            )
            ArcadeActionButton(
                label = "ĐỂ SAU",
                onClick = onDefer,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ArcadeActionButton(
            label = "TỪ CHỐI",
            onClick = { onRespond(false) },
            style = ArcadeActionStyle.DANGER,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CreateTournamentCard(
    playerLevel: Int,
    enabled: Boolean,
    onCreate: (String, GameMode, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(GameMode.ORDER) }
    var maxPlayers by remember { mutableStateOf(4) }
    var showModePicker by remember { mutableStateOf(false) }
    if (showModePicker) {
        GameModePickerDialog(
            title = "Chọn chế độ đấu giải",
            playerLevel = playerLevel,
            onDismiss = { showModePicker = false },
            onSelect = {
                mode = it
                showModePicker = false
            }
        )
    }
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        "Tạo giải riêng",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (maxPlayers) {
                            4 -> "4 người • 2 bán kết • 1 chung kết"
                            8 -> "8 người • 4 tứ kết • 2 bán kết • 1 chung kết"
                            else -> "16 người • 8 vòng 1/8 • 4 tứ kết • 2 bán kết • 1 chung kết"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Quy mô giải",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 400.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(4, 8).forEach { size ->
                                    TournamentSizeChip(
                                        playerCount = size,
                                        selected = maxPlayers == size,
                                        onClick = { maxPlayers = size },
                                        modifier = Modifier.weight(1f).testTag("tournament_size_$size")
                                    )
                                }
                            }
                            TournamentSizeChip(
                                playerCount = 16,
                                selected = maxPlayers == 16,
                                onClick = { maxPlayers = 16 },
                                modifier = Modifier.fillMaxWidth().testTag("tournament_size_16")
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(4, 8, 16).forEach { size ->
                                TournamentSizeChip(
                                    playerCount = size,
                                    selected = maxPlayers == size,
                                    onClick = { maxPlayers = size },
                                    modifier = Modifier.weight(1f).testTag("tournament_size_$size")
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                label = { Text("Tên giải đấu") },
                placeholder = { Text("VD: Cúp Chiến Thần") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("tournament_name")
            )

            Surface(
                onClick = { showModePicker = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Chế độ chơi",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            mode.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            var entryFee by remember { mutableStateOf(100) }
            val entryFeeOptions = listOf(0, 50, 100, 200, 500, 1000)
            var isCustomFee by remember { mutableStateOf(false) }
            var customFeeText by remember { mutableStateOf("") }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Lệ phí tham gia (Vàng)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    entryFeeOptions.forEach { fee ->
                        TournamentFeeChip(
                            label = if (fee == 0) "Miễn phí" else "$fee",
                            selected = !isCustomFee && entryFee == fee,
                            onClick = {
                                isCustomFee = false
                                entryFee = fee
                            }
                        )
                    }
                    TournamentFeeChip(
                        label = "Tùy chỉnh",
                        selected = isCustomFee,
                        onClick = { isCustomFee = true }
                    )
                }

                if (isCustomFee) {
                    OutlinedTextField(
                        value = customFeeText,
                        onValueChange = {
                            if (it.isEmpty() || (it.length <= 6 && it.all { char -> char.isDigit() })) {
                                customFeeText = it
                                entryFee = it.toIntOrNull() ?: 0
                            }
                        },
                        label = { Text("Nhập số vàng lệ phí") },
                        placeholder = { Text("VD: 750") },
                        singleLine = true,
                        prefix = {
                            Icon(
                                Icons.Filled.MonetizationOn,
                                contentDescription = null,
                                tint = ArcadePalette.Gold500,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        suffix = { Text("Vàng") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }

            ArcadeActionButton(
                label = "BẮT ĐẦU TẠO GIẢI",
                onClick = { onCreate(name.trim(), mode, entryFee, maxPlayers) },
                enabled = enabled && name.trim().length >= 3 && (!isCustomFee || customFeeText.isNotEmpty()),
                style = ArcadeActionStyle.GOLD,
                modifier = Modifier.fillMaxWidth().testTag("create_tournament")
            )
            
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Các trận đấu giải không ảnh hưởng Elo.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun TournamentSizeChip(
    playerCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) ArcadePalette.Blue700 else ArcadePalette.Navy800,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) ArcadePalette.Blue300 else ArcadePalette.Navy700
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.GroupAdd,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) ArcadePalette.Gold500 else ArcadePalette.Blue100
            )
            Spacer(Modifier.size(7.dp))
            Text(
                "$playerCount NGƯỜI",
                color = ArcadePalette.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun TournamentFeeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ArcadePalette.Blue700 else ArcadePalette.Navy800,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) ArcadePalette.Blue300 else ArcadePalette.Navy700
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.MonetizationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ArcadePalette.Gold500
                )
            }
            Text(
                text = label,
                color = ArcadePalette.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActiveTournamentContent(
    state: GameState,
    tournament: TournamentSnapshot,
    onInvite: (String) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onOpenFriendProfile: (String) -> Unit
) {
    val myId = state.player.id
    val isHost = tournament.hostPlayerId == myId
    
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tournament.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "${tournament.gameMode.title()} • ${tournament.phase.label()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "${tournament.players.size}/${tournament.maxPlayers}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (tournament.entryFee > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Lệ phí",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                        Text("${tournament.entryFee} Vàng", fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Giải thưởng",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                        Text(
                            "${tournament.prizePool} Vàng",
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    SectionLabel("Người tham gia")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(tournament.maxPlayers) { index ->
            val participant = tournament.players.getOrNull(index)
            val isMe = participant?.playerId == myId
            
            Surface(
                onClick = { participant?.playerId?.takeIf { it != myId }?.let(onOpenFriendProfile) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isMe) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) 
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (isMe) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (participant != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (participant != null) Icons.Rounded.EmojiEvents else Icons.Rounded.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.padding(6.dp),
                                tint = if (participant != null) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.outline
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                participant?.displayName ?: "Đang chờ người chơi…",
                                fontWeight = if (participant != null) FontWeight.Bold else FontWeight.Normal,
                                color = if (participant != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (isMe) {
                                Text(
                                    "Bạn",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ArcadePalette.Blue300
                                )
                            }
                        }
                    }
                    
                    if (participant != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (participant.isHost) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "Chủ giải",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            OnlineStatusIndicator(participant.isOnline)
                        }
                    }
                }
            }
        }
    }

    if (tournament.phase == TournamentPhase.LOBBY && isHost) {
        val availableFriends = state.social.friends.filter { friend ->
            friend.presence != FriendPresence.OFFLINE && tournament.players.none { it.playerId == friend.userId }
        }
        if (availableFriends.isNotEmpty() && tournament.players.size < tournament.maxPlayers) {
            SectionLabel("Mời bạn bè")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableFriends.forEach { friend ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(friend.displayName, fontWeight = FontWeight.Medium)
                                FriendPresenceIndicator(friend.presence)
                            }
                            TextButton(
                                onClick = { onInvite(friend.userId) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Mời")
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        ArcadeActionButton(
            label = "BẮT ĐẦU GIẢI ĐẤU",
            onClick = onStart,
            enabled = tournament.players.size == tournament.maxPlayers &&
                tournament.players.all { it.isOnline } && !state.isTournamentLoading,
            icon = Icons.Rounded.PlayArrow,
            style = ArcadeActionStyle.GOLD,
            modifier = Modifier.fillMaxWidth().testTag("start_tournament")
        )
    }

    if (tournament.phase != TournamentPhase.LOBBY || tournament.matches.any { it.playerOneId != null }) {
        SectionLabel("Nhánh đấu")
        TournamentBracket(tournament)
    }

    if (tournament.phase == TournamentPhase.LOBBY) {
        ArcadeActionButton(
            label = if (isHost) "HỦY GIẢI ĐẤU" else "RỜI KHỎI GIẢI",
            onClick = onLeave,
            enabled = !state.isTournamentLoading,
            style = ArcadeActionStyle.DANGER,
            modifier = Modifier.fillMaxWidth()
        )
    } else if (tournament.phase == TournamentPhase.RUNNING) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Các trận đấu được tạo tự động. Người thắng sẽ tiến vào vòng tiếp theo.",
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TournamentBracket(tournament: TournamentSnapshot) {
    val names = tournament.players.associate { it.playerId to it.displayName }
    val roundNumbers = tournament.matches.map(TournamentMatchSnapshot::round).distinct().sorted()
    val finalRound = roundNumbers.maxOrNull() ?: 1
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        roundNumbers.forEach { round ->
            val matches = tournament.matches.filter { it.round == round }
            val isFinal = round == finalRound
            Text(
                tournamentRoundLabel(round, finalRound),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFinal) ArcadePalette.Gold500 else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                modifier = if (isFinal) Modifier.align(Alignment.CenterHorizontally) else Modifier
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val sortedMatches = matches.sortedBy(TournamentMatchSnapshot::position)
                val columns = if (maxWidth >= 560.dp && sortedMatches.size > 1) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sortedMatches.chunked(columns).forEach { rowMatches ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowMatches.forEach { match ->
                                Box(modifier = Modifier.weight(1f)) {
                                    TournamentMatchCard(
                                        match = match,
                                        names = names,
                                        compact = columns > 1,
                                        isFinal = isFinal
                                    )
                                }
                            }
                            if (rowMatches.size < columns) {
                                repeat(columns - rowMatches.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (!isFinal) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = ArcadePalette.Blue300.copy(alpha = 0.45f),
                            start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }

        tournament.championPlayerId?.let { championId ->
            Spacer(Modifier.height(8.dp))
            ChampionCard(names[championId] ?: "Người chơi")
        }
    }
}

private fun tournamentRoundLabel(round: Int, finalRound: Int): String = when (round) {
    finalRound -> "TRẬN CHUNG KẾT"
    finalRound - 1 -> "VÒNG BÁN KẾT"
    finalRound - 2 -> "VÒNG TỨ KẾT"
    finalRound - 3 -> "VÒNG 1/8"
    else -> "VÒNG $round"
}

@Composable
private fun ChampionCard(name: String) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Rounded.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = ArcadePalette.Gold500
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "NHÀ VÔ ĐỊCH",
                    style = MaterialTheme.typography.labelLarge,
                    color = ArcadePalette.Gold500,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TournamentMatchCard(
    match: TournamentMatchSnapshot, 
    names: Map<String, String>,
    compact: Boolean = false,
    isFinal: Boolean = false
) {
    val isPlaying = match.phase == TournamentMatchPhase.PLAYING
    ArcadePanel(
        modifier = Modifier.fillMaxWidth(),
        accent = when {
            isFinal -> ArcadePalette.Gold500
            isPlaying -> ArcadePalette.Coral400
            else -> ArcadePalette.Blue500
        }
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TournamentPlayerLine(match.playerOneId, match.winnerPlayerId, names, compact)
            HorizontalDivider(
                modifier = Modifier.alpha(0.2f),
                color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            TournamentPlayerLine(match.playerTwoId, match.winnerPlayerId, names, compact)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when (match.phase) {
                        TournamentMatchPhase.PENDING -> ArcadePalette.Navy700
                        TournamentMatchPhase.PLAYING -> ArcadePalette.Coral600
                        TournamentMatchPhase.FINISHED -> ArcadePalette.Mint900
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        when (match.phase) {
                            TournamentMatchPhase.PENDING -> "CHỜ"
                            TournamentMatchPhase.PLAYING -> "ĐANG ĐẤU"
                            TournamentMatchPhase.FINISHED -> "XONG"
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (match.phase) {
                            TournamentMatchPhase.PLAYING -> ArcadePalette.White
                            TournamentMatchPhase.FINISHED -> ArcadePalette.Mint100
                            TournamentMatchPhase.PENDING -> ArcadePalette.Blue100
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TournamentPlayerLine(
    playerId: String?, 
    winnerId: String?, 
    names: Map<String, String>,
    compact: Boolean = false
) {
    val isWinner = playerId != null && playerId == winnerId
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = playerId?.let { names[it] } ?: "Đang chờ…",
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (isWinner) ArcadePalette.Gold500 else MaterialTheme.colorScheme.onSurface
        )
        if (isWinner) {
            Icon(
                Icons.Rounded.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 14.dp else 18.dp),
                tint = ArcadePalette.Gold500
            )
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: TournamentInvitationSnapshot,
    onRespond: (String, Boolean) -> Unit
) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(invitation.tournamentName, fontWeight = FontWeight.Bold)
            Text(
                "${invitation.hostDisplayName} mời bạn · ${invitation.gameMode.title()} · ${invitation.maxPlayers} người",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArcadeActionButton(
                    label = "THAM GIA",
                    onClick = { onRespond(invitation.invitationId, true) },
                    style = ArcadeActionStyle.GOLD,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "TỪ CHỐI",
                    onClick = { onRespond(invitation.invitationId, false) },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TournamentHistoryCard(
    tournament: TournamentSnapshot,
    initiallyExpanded: Boolean = false
) {
    val champion = tournament.players.firstOrNull { it.playerId == tournament.championPlayerId }?.displayName
    var expanded by remember(tournament.tournamentId) { mutableStateOf(initiallyExpanded) }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArcadePalette.Gold500.copy(alpha = 0.48f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tournament.name, fontWeight = FontWeight.Bold)
            Text("${tournament.gameMode.title()} • ${tournament.phase.label()}")
            champion?.let { Text("Vô địch: $it", color = ArcadePalette.Gold500, fontWeight = FontWeight.Bold) }
            Text(
                if (expanded) "Ẩn nhánh đấu" else "Xem nhánh đấu",
                style = MaterialTheme.typography.labelMedium,
                color = ArcadePalette.Blue300
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                TournamentBracket(tournament)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) ArcadePalette.Coral800 else ArcadePalette.Navy800,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isError) ArcadePalette.Coral400 else ArcadePalette.Blue300
        )
    ) {
        Text(message, modifier = Modifier.padding(14.dp), color = ArcadePalette.White)
    }
}

private fun ProtocolGameMode.title(): String = when (this) {
    ProtocolGameMode.ORDER -> "Cổ điển"
    ProtocolGameMode.RANDOM_TARGET -> "Ngẫu nhiên"
    ProtocolGameMode.TIME_BONUS -> "Thưởng thời gian"
    ProtocolGameMode.SPEED_UP -> "Tăng tốc"
    ProtocolGameMode.SURVIVAL -> "Sinh tồn"
    ProtocolGameMode.COMBO -> "Combo"
    ProtocolGameMode.TIME_ATTACK -> "Đua 60 giây"
    ProtocolGameMode.TEAM_2V2 -> "Đồng đội 2v2"
}

private fun TournamentPhase.label(): String = when (this) {
    TournamentPhase.LOBBY -> "Đang tập hợp"
    TournamentPhase.RUNNING -> "Đang diễn ra"
    TournamentPhase.FINISHED -> "Đã kết thúc"
    TournamentPhase.CANCELLED -> "Đã hủy"
}
