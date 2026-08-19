package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    state: GameState,
    onBack: () -> Unit,
    onCreate: (String, GameMode) -> Unit,
    onInvite: (String) -> Unit,
    onRespondInvitation: (String, Boolean) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tournament = state.tournamentHub.activeTournament
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("tournament_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Đấu giải", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { paddingValues ->
        ResponsiveScreen(modifier = Modifier.padding(paddingValues), maxContentWidth = 760.dp) { contentModifier ->
            Column(
                modifier = contentModifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
    AlertDialog(
        onDismissRequest = onDefer,
        icon = { Icon(Icons.Rounded.EmojiEvents, contentDescription = null) },
        title = { Text("Lời mời đấu giải") },
        text = {
            Text(
                "${invitation.hostDisplayName} mời bạn tham gia “${invitation.tournamentName}” " +
                    "• ${invitation.gameMode.title()}."
            )
        },
        confirmButton = { Button(onClick = { onRespond(true) }) { Text("Tham gia") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDefer) { Text("Để sau") }
                TextButton(onClick = { onRespond(false) }) { Text("Từ chối") }
            }
        }
    )
}

@Composable
private fun CreateTournamentCard(
    playerLevel: Int,
    enabled: Boolean,
    onCreate: (String, GameMode) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(GameMode.ORDER) }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Tạo giải riêng 4 người", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Loại trực tiếp • 2 bán kết • 1 chung kết", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                label = { Text("Tên giải") },
                placeholder = { Text("Cúp cuối tuần") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("tournament_name")
            )
            OutlinedButton(onClick = { showModePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Chế độ: ${mode.title}")
            }
            Button(
                onClick = { onCreate(name.trim(), mode) },
                enabled = enabled && name.trim().length >= 3,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("create_tournament")
            ) { Text("Tạo giải") }
            Text(
                "Các trận đấu giải không ảnh hưởng Elo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tournament.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("${tournament.gameMode.title()} • ${tournament.phase.label()}")
            Text(
                "${tournament.players.size}/${tournament.maxPlayers} người",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }

    SectionLabel("Người tham gia")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(tournament.maxPlayers) { index ->
            val participant = tournament.players.getOrNull(index)
            Card(
                onClick = { participant?.playerId?.takeIf { it != myId }?.let(onOpenFriendProfile) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        participant?.displayName ?: "Đang chờ người chơi…",
                        fontWeight = if (participant != null) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        when {
                            participant == null -> "Trống"
                            participant.isHost -> "Chủ giải"
                            participant.isOnline -> "Online"
                            else -> "Offline"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (participant?.isOnline == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (tournament.phase == TournamentPhase.LOBBY && isHost) {
        val availableFriends = state.social.friends.filter { friend ->
            friend.presence != FriendPresence.OFFLINE && tournament.players.none { it.playerId == friend.userId }
        }
        if (availableFriends.isNotEmpty() && tournament.players.size < tournament.maxPlayers) {
            SectionLabel("Mời bạn đang online")
            availableFriends.forEach { friend ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(friend.displayName, fontWeight = FontWeight.Medium)
                    OutlinedButton(onClick = { onInvite(friend.userId) }) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                        Text(" Mời")
                    }
                }
            }
        }
        Button(
            onClick = onStart,
            enabled = tournament.players.size == tournament.maxPlayers &&
                tournament.players.all { it.isOnline } && !state.isTournamentLoading,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start_tournament")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Text(" Bắt đầu bán kết")
        }
    }

    if (tournament.phase != TournamentPhase.LOBBY || tournament.matches.any { it.playerOneId != null }) {
        SectionLabel("Nhánh đấu")
        TournamentBracket(tournament)
    }

    if (tournament.phase == TournamentPhase.LOBBY) {
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth(), enabled = !state.isTournamentLoading) {
            Text(if (isHost) "Hủy giải" else "Rời giải")
        }
    } else if (tournament.phase == TournamentPhase.RUNNING) {
        Text(
            "Các trận tiếp theo được tạo tự động. Người thắng bán kết sẽ vào chung kết.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TournamentBracket(tournament: TournamentSnapshot) {
    val names = tournament.players.associate { it.playerId to it.displayName }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BÁN KẾT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        tournament.matches.filter { it.round == 1 }.sortedBy { it.position }.forEach { match ->
            TournamentMatchCard(match, names)
        }
        HorizontalDivider()
        Text("CHUNG KẾT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        tournament.matches.firstOrNull { it.round == 2 }?.let { TournamentMatchCard(it, names) }
        tournament.championPlayerId?.let { championId ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null)
                    Text("  Vô địch: ${names[championId] ?: "Người chơi"}", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun TournamentMatchCard(match: TournamentMatchSnapshot, names: Map<String, String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TournamentPlayerLine(match.playerOneId, match.winnerPlayerId, names)
            HorizontalDivider()
            TournamentPlayerLine(match.playerTwoId, match.winnerPlayerId, names)
            Text(
                when (match.phase) {
                    TournamentMatchPhase.PENDING -> "Chưa bắt đầu"
                    TournamentMatchPhase.PLAYING -> "Đang thi đấu"
                    TournamentMatchPhase.FINISHED -> "Đã kết thúc"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TournamentPlayerLine(playerId: String?, winnerId: String?, names: Map<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(playerId?.let { names[it] } ?: "Đang chờ…")
        if (playerId != null && playerId == winnerId) {
            Text("THẮNG", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: TournamentInvitationSnapshot,
    onRespond: (String, Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(invitation.tournamentName, fontWeight = FontWeight.Bold)
            Text("${invitation.hostDisplayName} mời bạn • ${invitation.gameMode.title()}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRespond(invitation.invitationId, true) }, modifier = Modifier.weight(1f)) {
                    Text("Tham gia")
                }
                OutlinedButton(onClick = { onRespond(invitation.invitationId, false) }, modifier = Modifier.weight(1f)) {
                    Text("Từ chối")
                }
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
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tournament.name, fontWeight = FontWeight.Bold)
            Text("${tournament.gameMode.title()} • ${tournament.phase.label()}")
            champion?.let { Text("Vô địch: $it", color = MaterialTheme.colorScheme.primary) }
            Text(
                if (expanded) "Ẩn nhánh đấu" else "Xem nhánh đấu",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
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
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(message, modifier = Modifier.padding(14.dp))
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
