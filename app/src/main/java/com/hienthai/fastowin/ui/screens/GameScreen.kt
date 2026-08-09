package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.theme.FastToWinTheme

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onNumberClick: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver) {
            onFinish()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Fast To Win", style = MaterialTheme.typography.titleMedium)
                        state.message?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                actions = {
                    if (state.timeLeftMillis > 0) {
                        val seconds = (state.timeLeftMillis / 1000) % 60
                        val minutes = (state.timeLeftMillis / 1000) / 60
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                            modifier = Modifier.padding(end = 16.dp),
                            fontWeight = FontWeight.Bold,
                            color = if (state.timeLeftMillis < 10000)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Player vs Opponent scoreboard
            PlayerScoreBar(
                player = state.player,
                opponent = state.opponent
            )

            HorizontalDivider()

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isWide = maxWidth > 600.dp

                if (isWide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NumberGrid(
                            numbers = state.numbers,
                            currentTarget = state.currentTarget,
                            onNumberClick = onNumberClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    NumberGrid(
                        numbers = state.numbers,
                        currentTarget = state.currentTarget,
                        onNumberClick = onNumberClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerScoreBar(
    player: PlayerState,
    opponent: PlayerState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Local player — left side
        PlayerScoreCard(
            name = player.name,
            score = player.score,
            currentTarget = player.currentTarget,
            isLocal = true,
            modifier = Modifier.weight(1f)
        )

        // VS label
        Text(
            text = "VS",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Opponent — right side
        PlayerScoreCard(
            name = opponent.name,
            score = opponent.score,
            currentTarget = opponent.currentTarget,
            isLocal = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerScoreCard(
    name: String,
    score: Int,
    currentTarget: Int,
    isLocal: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isLocal)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    val onContainerColor = if (isLocal)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer

    val alignment = if (isLocal) Alignment.Start else Alignment.End
    val textAlign = if (isLocal) TextAlign.Start else TextAlign.End

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = alignment
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = onContainerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onContainerColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Bấm: $currentTarget",
                style = MaterialTheme.typography.bodySmall,
                color = onContainerColor.copy(alpha = 0.75f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun NumberGrid(
    numbers: List<Int>,
    currentTarget: Int,
    onNumberClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(numbers) { number ->
            NumberCell(
                number = number,
                isCompleted = number < currentTarget,
                onClick = { onNumberClick(number) }
            )
        }
    }
}

@Composable
fun NumberCell(
    number: Int,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isCompleted) { onClick() },
        color = when {
            isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = when {
                    isCompleted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontSize = 20.sp
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenMobilePreview() {
    FastToWinTheme {
        GameScreen(
            state = GameState(
                numbers = (1..100).toList(),
                currentTarget = 5,
                score = 40,
                timeLeftMillis = 295000,
                player = PlayerState(name = "hien", score = 40, currentTarget = 5),
                opponent = PlayerState(name = "hieu", score = 20, currentTarget = 3)
            ),
            onNumberClick = {},
            onFinish = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun GameScreenTabletPreview() {
    FastToWinTheme {
        GameScreen(
            state = GameState(
                numbers = (1..100).toList(),
                currentTarget = 1,
                score = 0,
                player = PlayerState(name = "hien", score = 0, currentTarget = 1),
                opponent = PlayerState(name = "hieu", score = 0, currentTarget = 1)
            ),
            onNumberClick = {},
            onFinish = {}
        )
    }
}
