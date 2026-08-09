package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Composable
fun LobbyScreen(
    state: GameState,
    onFindMatch: (GameMode) -> Unit,
    onReadyUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(targetState = state.isSearching || state.opponent.isReady || state.player.isReady, label = "LobbyContent") { isInMatchmaking ->
            if (!isInMatchmaking) {
                ModeSelection(onFindMatch)
            } else {
                MatchmakingStatus(state, onReadyUp)
            }
        }
    }
}

@Composable
private fun ModeSelection(onFindMatch: (GameMode) -> Unit) {
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
                text = "Multiplayer Speed Battle",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        ModeCard(
            title = "Order Mode",
            subtitle = "Race from 1 to 100",
            icon = Icons.Rounded.Bolt,
            onClick = { onFindMatch(GameMode.ORDER) },
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )

        ModeCard(
            title = "Time Attack",
            subtitle = "60s Scoring Frenzy",
            icon = Icons.Rounded.Timer,
            onClick = { onFindMatch(GameMode.TIME_ATTACK) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxSize(),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MatchmakingStatus(state: GameState, onReadyUp: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.countdown != null) {
            Text(
                text = "${state.countdown}",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = "Match Starting!", style = MaterialTheme.typography.headlineMedium)
        } else {
            Text(
                text = if (state.isSearching) "Searching for Opponent..." else "Opponent Found!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlayerStatusCard(state.player, isLocal = true)
                PlayerStatusCard(state.opponent, isLocal = false)
            }

            if (!state.player.isReady && !state.isSearching) {
                Button(
                    onClick = onReadyUp,
                    modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Ready Up", style = MaterialTheme.typography.titleMedium)
                }
            } else if (state.player.isReady && !state.opponent.isReady) {
                CircularProgressIndicator()
                Text("Waiting for opponent...")
            }
        }
    }
}

@Composable
private fun PlayerStatusCard(player: PlayerState, isLocal: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = if (isLocal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).fillMaxSize()
                )
            }
            if (player.isReady) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Ready",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(2.dp)
                )
            }
        }
        Text(
            text = player.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (player.isReady) "READY" else "WAITING",
            style = MaterialTheme.typography.labelLarge,
            color = if (player.isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LobbyScreenPreview() {
    FastToWinTheme {
        LobbyScreen(
            state = GameState(isSearching = true),
            onFindMatch = {},
            onReadyUp = {}
        )
    }
}
