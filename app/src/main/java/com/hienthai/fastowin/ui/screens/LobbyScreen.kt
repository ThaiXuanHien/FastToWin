package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.LobbyStage
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Composable
fun LobbyScreen(
    state: GameState,
    onModeSelected: (GameMode) -> Unit,
    onStartSearching: (String) -> Unit,
    onBackToMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state.lobbyStage,
            label = "LobbyContent",
            modifier = Modifier.widthIn(max = 600.dp)
        ) { stage ->
            when (stage) {
                LobbyStage.SELECT_MODE -> ModeSelection(onModeSelected)
                LobbyStage.ENTER_NAME -> NameEntry(onStartSearching, onBackToMode)
                LobbyStage.SEARCHING -> MatchmakingStatus(state, onRetry = { onStartSearching(state.player.name) })
                LobbyStage.MATCHED -> MatchedStatus(state)
            }
        }
    }
}

@Composable
private fun MatchedStatus(state: GameState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(40.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Match Found!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerMatchedCard(state.player, isLocal = true)
            Text(
                text = "VS",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            PlayerMatchedCard(state.opponent, isLocal = false)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Starting in",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "${state.countdown ?: 3}",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PlayerMatchedCard(player: PlayerState, isLocal: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = if (isLocal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 8.dp
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.padding(32.dp).fillMaxSize(),
                tint = if (isLocal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(
            text = player.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ModeSelection(onModeSelected: (GameMode) -> Unit) {
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
            onClick = { onModeSelected(GameMode.ORDER) },
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )

        ModeCard(
            title = "Time Attack",
            subtitle = "60s Scoring Frenzy",
            icon = Icons.Rounded.Timer,
            onClick = { onModeSelected(GameMode.TIME_ATTACK) },
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
private fun NameEntry(
    onStartSearching: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Ready to play?",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Enter your nickname to start finding opponents.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your Nickname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            )
        )
        
        Button(
            onClick = { if (name.isNotBlank()) onStartSearching(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Find Match", style = MaterialTheme.typography.titleLarge)
        }
        
        TextButton(onClick = onBack) {
            Text("Back to Mode Selection")
        }
    }
}

@Composable
private fun MatchmakingStatus(state: GameState, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.error != null) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRetry) {
                    Text("Retry Connection")
                }
            } else if (state.isSearching && state.opponent.name == "Opponent") {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = "Searching for Opponent...",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = "Opponent Found! Starting soon...",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (state.error == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlayerStatusCard(state.player, isLocal = true)
                PlayerStatusCard(state.opponent, isLocal = false)
            }
        }
    }
}

@Composable
private fun PlayerStatusCard(player: PlayerState, isLocal: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = if (isLocal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 4.dp
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                tint = if (isLocal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(
            text = player.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LobbyScreenPreview() {
    FastToWinTheme {
        LobbyScreen(
            state = GameState(lobbyStage = LobbyStage.SELECT_MODE),
            onModeSelected = {},
            onStartSearching = {},
            onBackToMode = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NameEntryPreview() {
    FastToWinTheme {
        LobbyScreen(
            state = GameState(lobbyStage = LobbyStage.ENTER_NAME),
            onModeSelected = {},
            onStartSearching = {},
            onBackToMode = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SearchingPreview() {
    FastToWinTheme {
        LobbyScreen(
            state = GameState(lobbyStage = LobbyStage.SEARCHING, isSearching = true),
            onModeSelected = {},
            onStartSearching = {},
            onBackToMode = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MatchedPreview() {
    FastToWinTheme {
        LobbyScreen(
            state = GameState(
                lobbyStage = LobbyStage.MATCHED,
                opponent = PlayerState("Hien Thái"),
                countdown = 3
            ),
            onModeSelected = {},
            onStartSearching = {},
            onBackToMode = {}
        )
    }
}
