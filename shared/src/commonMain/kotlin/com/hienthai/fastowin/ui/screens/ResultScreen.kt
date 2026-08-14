package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.platform.GameFeedbackEffect
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.state.PostMatchFriendStatus
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun ResultScreen(
    state: GameState,
    onRestart: () -> Unit,
    onRematch: () -> Unit,
    onCancelRematch: () -> Unit,
    onDeclineRematch: () -> Unit,
    onConnectOpponent: () -> Unit,
    onBlockOpponent: () -> Unit,
    preferences: AppPreferences = AppPreferences(),
    modifier: Modifier = Modifier
) {
    val isDraw = state.player.score == state.opponent.score
    val isWinner = state.player.score > state.opponent.score
    val hapticFeedback = LocalHapticFeedback.current
    val winScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "WinScale"
    )
    var showBlockConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(isWinner, isDraw) {
        val effect = when {
            isDraw -> GameFeedbackEffect.CORRECT
            isWinner -> GameFeedbackEffect.WIN
            else -> GameFeedbackEffect.LOSS
        }
        if (preferences.soundEnabled) playFeedbackSound(effect)
        if (preferences.vibrationEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    if (showBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmation = false },
            title = { Text("Chặn ${state.opponent.name}?") },
            text = { Text("Hai người sẽ không thể kết bạn, gửi lời mời hoặc đấu lại. Bạn cũng sẽ rời phòng hiện tại.") },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirmation = false
                    onBlockOpponent()
                }) { Text("Chặn", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmation = false }) { Text("Hủy") }
            }
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VictoryHeader(isWinner || isDraw, winScale)
            Text(
                text = when {
                    isDraw -> "HÒA!"
                    isWinner -> "CHIẾN THẮNG!"
                    else -> "THUA CUỘC"
                },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = when {
                    isDraw -> MaterialTheme.colorScheme.secondary
                    isWinner -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )

            ScoreBoard(state.player, state.opponent, isDraw)
            EloCard(state)
            MatchSummaryCard(state)
            RematchCard(
                state = state,
                onRematch = onRematch,
                onCancel = onCancelRematch,
                onDecline = onDeclineRematch
            )
            OpponentActions(
                state = state,
                onConnect = onConnectOpponent,
                onBlock = { showBlockConfirmation = true }
            )

            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Về sảnh") }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VictoryHeader(isWinner: Boolean, scale: Float) {
    Surface(
        shape = CircleShape,
        color = if (isWinner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.size(96.dp).scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isWinner) Icons.Rounded.EmojiEvents else Icons.Rounded.SentimentVeryDissatisfied,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
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
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                result,
                style = MaterialTheme.typography.bodySmall,
                color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
        Text(
            score.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EloCard(state: GameState) {
    val eloChange = state.lastMatchEloChange ?: return
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Thay đổi xếp hạng", fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(if (eloChange >= 0) "+$eloChange Elo" else "$eloChange Elo")
                    state.lastMatchEloRating?.let { append("  •  $it") }
                },
                color = if (eloChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun MatchSummaryCard(state: GameState) {
    val attempts = state.player.correctSelections + state.player.wrongSelections
    val accuracy = if (attempts == 0) 0 else state.player.correctSelections * 100 / attempts
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tóm tắt của bạn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric("Thời gian", formatDuration(state.lastMatchDurationMillis), Modifier.weight(1f))
                SummaryMetric("Chính xác", "$accuracy%", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetric("Đúng / Sai", "${state.player.correctSelections} / ${state.player.wrongSelections}", Modifier.weight(1f))
                SummaryMetric("Phản ứng", formatReaction(state.player.averageReactionMillis), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RematchCard(
    state: GameState,
    onRematch: () -> Unit,
    onCancel: () -> Unit,
    onDecline: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(epochMillis()) }
    val expiresAt = state.rematchExpiresAtEpochMillis
    LaunchedEffect(expiresAt) {
        while (expiresAt != null && nowMillis < expiresAt) {
            delay(250)
            nowMillis = epochMillis()
        }
    }
    val remainingSeconds = expiresAt?.let {
        ceil(((it - nowMillis).coerceAtLeast(0L)) / 1_000.0).toInt()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Đấu lại", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.rematchNotice?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if ((state.isRematchRequestedByMe || state.isRematchRequestedByOpponent) && remainingSeconds != null) {
                Text("Còn $remainingSeconds giây để phản hồi", style = MaterialTheme.typography.bodySmall)
            }
            when {
                state.isRematchRequestedByOpponent -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Từ chối") }
                    Button(onClick = onRematch, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Chấp nhận")
                    }
                }
                state.isRematchRequestedByMe -> OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Hủy yêu cầu") }
                else -> Button(
                    onClick = onRematch,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Mời đấu lại")
                }
            }
        }
    }
}

@Composable
private fun OpponentActions(state: GameState, onConnect: () -> Unit, onBlock: () -> Unit) {
    if (state.profile == null) {
        Text(
            "Đăng nhập để kết bạn hoặc chặn người chơi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val friendLabel = when (state.postMatchFriendStatus) {
        PostMatchFriendStatus.AVAILABLE -> "Kết bạn"
        PostMatchFriendStatus.REQUEST_RECEIVED -> "Chấp nhận kết bạn"
        PostMatchFriendStatus.REQUEST_SENT -> "Đã gửi lời mời"
        PostMatchFriendStatus.FRIEND -> "Đã là bạn bè"
        PostMatchFriendStatus.BLOCKED -> "Đã chặn"
        PostMatchFriendStatus.UNAVAILABLE -> "Đang tải thông tin"
    }
    val canConnect = state.postMatchFriendStatus in setOf(
        PostMatchFriendStatus.AVAILABLE,
        PostMatchFriendStatus.REQUEST_RECEIVED
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onConnect,
            enabled = canConnect,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.GroupAdd, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text(friendLabel)
        }
        TextButton(
            onClick = onBlock,
            enabled = state.postMatchFriendStatus != PostMatchFriendStatus.BLOCKED
        ) {
            Icon(Icons.Rounded.Block, contentDescription = null)
            Spacer(Modifier.size(4.dp))
            Text("Chặn")
        }
    }
}

private fun formatDuration(durationMillis: Long?): String {
    if (durationMillis == null) return "--"
    val totalSeconds = durationMillis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "$minutes:${seconds.toString().padStart(2, '0')}" else "${seconds}s"
}

private fun formatReaction(reactionMillis: Long): String =
    if (reactionMillis <= 0) {
        "--"
    } else if (reactionMillis < 1_000) {
        "${reactionMillis}ms"
    } else {
        "${reactionMillis / 100 / 10.0}s"
    }

@Composable
fun ResultScreenPreview() {
    FastToWinTheme {
        ResultScreen(
            state = GameState(
                isGameOver = true,
                player = PlayerState("Hiền", score = 320, correctSelections = 32, wrongSelections = 2, averageReactionMillis = 640),
                opponent = PlayerState("Hiếu", score = 180, correctSelections = 18, wrongSelections = 4),
                lastMatchDurationMillis = 43_000,
                lastMatchEloChange = 12,
                lastMatchEloRating = 1_124
            ),
            onRestart = {},
            onRematch = {},
            onCancelRematch = {},
            onDeclineRematch = {},
            onConnectOpponent = {},
            onBlockOpponent = {}
        )
    }
}
