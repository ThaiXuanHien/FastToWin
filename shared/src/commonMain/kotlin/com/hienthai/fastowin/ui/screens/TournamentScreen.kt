package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material3.FilterChip
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
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    state: GameState,
    onBack: () -> Unit,
    onCreate: (String, GameMode, Int) -> Unit,
    onInvite: (String) -> Unit,
    onRespondInvitation: (String, Boolean) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)

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
    onCreate: (String, GameMode, Int) -> Unit
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
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
                        "Tạo giải riêng 4 người",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Loại trực tiếp • 2 bán kết • 1 chung kết",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entryFeeOptions.forEach { fee ->
                        FilterChip(
                            selected = !isCustomFee && entryFee == fee,
                            onClick = {
                                isCustomFee = false
                                entryFee = fee
                            },
                            label = { Text(if (fee == 0) "Miễn phí" else "$fee") },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    FilterChip(
                        selected = isCustomFee,
                        onClick = { isCustomFee = true },
                        label = { Text("Tùy chỉnh") },
                        shape = RoundedCornerShape(12.dp)
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
                        prefix = { Text("💰 ") },
                        suffix = { Text("Vàng") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }

            Button(
                onClick = { onCreate(name.trim(), mode, entryFee) },
                enabled = enabled && name.trim().length >= 3 && (!isCustomFee || customFeeText.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("create_tournament"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Bắt đầu tạo giải", fontWeight = FontWeight.Bold)
            }
            
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
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
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
                        Text(
                            participant?.displayName ?: "Đang chờ người chơi…",
                            fontWeight = if (participant != null) FontWeight.Bold else FontWeight.Normal,
                            color = if (participant != null) MaterialTheme.colorScheme.onSurface 
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        if (isMe) {
                            Text(
                                "(Bạn)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    if (participant != null) {
                        Surface(
                            color = when {
                                participant.isHost -> MaterialTheme.colorScheme.tertiaryContainer
                                participant.isOnline -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                when {
                                    participant.isHost -> "Chủ giải"
                                    participant.isOnline -> "Trực tuyến"
                                    else -> "Ngoại tuyến"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    participant.isHost -> MaterialTheme.colorScheme.onTertiaryContainer
                                    participant.isOnline -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
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
                            Text(friend.displayName, fontWeight = FontWeight.Medium)
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
        
        Button(
            onClick = onStart,
            enabled = tournament.players.size == tournament.maxPlayers &&
                tournament.players.all { it.isOnline } && !state.isTournamentLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_tournament"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("BẮT ĐẦU GIẢI ĐẤU", fontWeight = FontWeight.Black)
        }
    }

    if (tournament.phase != TournamentPhase.LOBBY || tournament.matches.any { it.playerOneId != null }) {
        SectionLabel("Nhánh đấu")
        TournamentBracket(tournament)
    }

    if (tournament.phase == TournamentPhase.LOBBY) {
        OutlinedButton(
            onClick = onLeave, 
            modifier = Modifier.fillMaxWidth().height(50.dp), 
            enabled = !state.isTournamentLoading,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isHost) "Hủy giải đấu" else "Rời khỏi giải", fontWeight = FontWeight.Bold)
        }
    } else if (tournament.phase == TournamentPhase.RUNNING) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Các trận đấu được tạo tự động. Người thắng bán kết sẽ vào chung kết.",
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Semi-finals
        Text(
            "VÒNG BÁN KẾT",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val semiFinals = tournament.matches.filter { it.round == 1 }.sortedBy { it.position }
            semiFinals.forEach { match ->
                Box(modifier = Modifier.weight(1f)) {
                    TournamentMatchCard(match, names, compact = true)
                }
            }
        }

        // Visual connector (simplified for now, can be improved with custom drawing)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f)
                val strokeWidth = 2.dp.toPx()
                
                // Horizontal line connecting the two semi-finals
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.25f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.75f, 0f),
                    strokeWidth = strokeWidth
                )
                // Vertical line down to final
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height),
                    strokeWidth = strokeWidth
                )
            }
        }

        // Final
        Text(
            "TRẬN CHUNG KẾT",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        tournament.matches.firstOrNull { it.round == 2 }?.let { finalMatch ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .align(Alignment.CenterHorizontally)
            ) {
                TournamentMatchCard(finalMatch, names, isFinal = true)
            }
        }

        // Champion
        tournament.championPlayerId?.let { championId ->
            Spacer(Modifier.height(8.dp))
            ChampionCard(names[championId] ?: "Người chơi")
        }
    }
}

@Composable
private fun ChampionCard(name: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Rounded.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "NHÀ VÔ ĐỊCH",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
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
                        TournamentMatchPhase.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                        TournamentMatchPhase.PLAYING -> MaterialTheme.colorScheme.primary
                        TournamentMatchPhase.FINISHED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                            TournamentMatchPhase.PLAYING -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
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
            color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (isWinner) {
            Icon(
                Icons.Rounded.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 14.dp else 18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
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
