package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.ui.theme.FastToWinTheme

@Composable
fun ResultScreen(
    state: GameState,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDraw = state.player.score == state.opponent.score
    val isWinner = state.player.score > state.opponent.score
    val winScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "WinScale"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            VictoryHeader(isWinner || isDraw, winScale)

            Text(
                text = when {
                    isDraw -> "HÒA!"
                    isWinner -> "CHIẾN THẮNG!"
                    else -> "THUA CUỘC"
                },
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = when {
                    isDraw -> MaterialTheme.colorScheme.secondary
                    isWinner -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ScoreBoard(state.player, state.opponent, isDraw)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Về sảnh chờ", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VictoryHeader(isWinner: Boolean, scale: Float) {
    Surface(
        shape = CircleShape,
        color = if (isWinner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isWinner) Icons.Rounded.EmojiEvents else Icons.Rounded.SentimentVeryDissatisfied,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = if (isWinner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ScoreBoard(player: PlayerState, opponent: PlayerState, isDraw: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScoreRow(
            name = "${player.name} (Bạn)",
            score = player.score,
            result = if (isDraw) "Hòa" else if (player.score > opponent.score) "Thắng" else "Thua",
            isWinner = !isDraw && player.score > opponent.score
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ScoreRow(
            name = opponent.name,
            score = opponent.score,
            result = if (isDraw) "Hòa" else if (opponent.score > player.score) "Thắng" else "Thua",
            isWinner = !isDraw && opponent.score > player.score
        )
    }
}

@Composable
private fun ScoreRow(name: String, score: Int, result: String, isWinner: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ResultScreenPreview() {
    FastToWinTheme {
        ResultScreen(
            state = GameState(
                player = PlayerState("Hiền", score = 320),
                opponent = PlayerState("Hiếu", score = 180)
            ),
            onRestart = {}
        )
    }
}
