package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.state.GAME_NUMBER_COUNT
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onNumberClick: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver) onFinish()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.currentRoomName ?: "Fast To Win",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Chạm các số theo đúng thứ tự",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.timeLeftMillis > 0) {
                        TimerBadge(state.timeLeftMillis)
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
            PlayerScoreBar(player = state.player, opponent = state.opponent)

            TargetPanel(currentTarget = state.currentTarget)

            AnimatedVisibility(visible = state.message != null) {
                Text(
                    text = state.message.orEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            NumberGrid(
                numbers = state.numbers,
                currentTarget = state.currentTarget,
                onNumberClick = onNumberClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimerBadge(timeLeftMillis: Long) {
    val seconds = (timeLeftMillis / 1_000) % 60
    val minutes = (timeLeftMillis / 1_000) / 60
    val urgent = timeLeftMillis < 10_000

    Surface(
        modifier = Modifier.padding(end = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (urgent) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontWeight = FontWeight.Black,
            color = if (urgent) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PlayerScoreBar(
    player: PlayerState,
    opponent: PlayerState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerScoreCard(
            label = "BẠN",
            player = player,
            isLocal = true,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "ĐẤU",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.outline
        )
        PlayerScoreCard(
            label = "ĐỐI THỦ",
            player = opponent,
            isLocal = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerScoreCard(
    label: String,
    player: PlayerState,
    isLocal: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isLocal) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isLocal) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = player.score.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun TargetPanel(currentTarget: Int) {
    val completed = (currentTarget - 1).coerceIn(0, GAME_NUMBER_COUNT)
    val displayedTarget = currentTarget.coerceAtMost(GAME_NUMBER_COUNT)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SỐ CẦN TÌM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = displayedTarget.toString(),
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tiến độ", style = MaterialTheme.typography.labelMedium)
                    Text("$completed/$GAME_NUMBER_COUNT", fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { completed.toFloat() / GAME_NUMBER_COUNT },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                )
            }
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
        columns = GridCells.Adaptive(minSize = 58.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(numbers, key = { it }) { number ->
            NumberCell(
                number = number,
                isCompleted = number < currentTarget,
                onClick = { onNumberClick(number) }
            )
        }
    }
}

@Composable
fun NumberCell(number: Int, isCompleted: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isCompleted, onClick = onClick),
        color = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = if (isCompleted) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        tonalElevation = if (isCompleted) 0.dp else 2.dp,
        shadowElevation = if (isCompleted) 0.dp else 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun GameScreenMobilePreview() {
    FastToWinTheme {
        GameScreen(
            state = GameState(
                numbers = (1..GAME_NUMBER_COUNT).toList(),
                currentTarget = 12,
                score = 70,
                timeLeftMillis = 42_000,
                player = PlayerState(name = "Hiền", score = 70, currentTarget = 12),
                opponent = PlayerState(name = "Hiếu", score = 40, currentTarget = 12)
            ),
            onNumberClick = {},
            onFinish = {}
        )
    }
}
