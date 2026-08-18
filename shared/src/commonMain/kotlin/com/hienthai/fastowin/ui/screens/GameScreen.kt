package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.state.GAME_NUMBER_COUNT
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.data.preferences.BoardStyle
import com.hienthai.fastowin.platform.GameFeedbackEffect
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onNumberClick: (Int) -> Unit,
    onFinish: () -> Unit,
    onOpenFriendProfile: (String) -> Unit = {},
    onExit: () -> Unit = {},
    preferences: AppPreferences = AppPreferences(),
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var showExitConfirmation by remember { mutableStateOf(false) }
    var tapFeedback by remember { mutableStateOf<GameFeedbackEffect?>(null) }
    var tapFeedbackToken by remember { mutableStateOf(0) }
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    LaunchedEffect(tapFeedbackToken) {
        if (tapFeedback != null) {
            delay(420)
            tapFeedback = null
        }
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Rời trận đấu?") },
            text = { Text("Trận hiện tại sẽ kết thúc và cả hai người chơi được đưa ra khỏi phòng.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onExit()
                }) { Text("Rời trận", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Tiếp tục chơi") }
            }
        )
    }
    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver) onFinish()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("game_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { showExitConfirmation = true }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Rời trận")
                    }
                },
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
                            text = buildString {
                                append(if (state.matchType == MatchType.RANKED) "Xếp hạng" else "Thường")
                                append(" • ").append(state.gameMode.description)
                            },
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
        ResponsiveScreen(
            modifier = Modifier.padding(paddingValues),
            maxContentWidth = 760.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            Column(modifier = contentModifier) {
                PlayerScoreBar(
                    player = state.player,
                    opponent = state.opponent,
                    onOpponentInfo = if (opponentFriend == null) null else ({
                        onOpenFriendProfile(opponentFriend.userId)
                    })
                )

                if (preferences.visualEffectsEnabled) {
                    CloseScoreWarning(state)
                }

                TargetPanel(
                    currentTarget = state.currentTarget,
                    completedCount = state.player.selectedNumbers.size
                )

                LiveMetricsBar(state)

                Box(modifier = Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                    state.message?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    NumberGrid(
                        numbers = state.numbers,
                        currentTarget = state.currentTarget,
                        selectedNumbers = state.player.selectedNumbers,
                        enabled = state.connectionStatus == ConnectionStatus.CONNECTED && !state.player.isFinished,
                        boardStyle = preferences.boardStyle,
                        onNumberClick = { number ->
                            val effect = if (number == state.currentTarget) {
                                GameFeedbackEffect.CORRECT
                            } else {
                                GameFeedbackEffect.WRONG
                            }
                            if (preferences.visualEffectsEnabled) {
                                tapFeedback = effect
                                tapFeedbackToken++
                            }
                            if (preferences.soundEnabled) playFeedbackSound(effect)
                            if (preferences.vibrationEnabled) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onNumberClick(number)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    tapFeedback?.let { FeedbackBurst(it) }
                }
            }
        }
    }
}

@Composable
private fun CloseScoreWarning(state: GameState) {
    val difference = abs(state.player.score - state.opponent.score)
    val enoughProgress = state.player.correctSelections + state.opponent.correctSelections >= 6
    if (!enoughProgress || difference > 30 || state.player.isFinished || state.opponent.isFinished) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            text = if (difference == 0) "Đang hòa điểm — lượt tiếp theo rất quan trọng!"
            else "Bám rất sát — chỉ cách nhau $difference điểm",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun LiveMetricsBar(state: GameState) {
    val speed = state.player.averageReactionMillis.takeIf { it > 0 }?.let {
        (10_000L / it).coerceAtMost(99L) / 10.0
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        speed?.let { LiveMetric("Tốc độ", "$it số/s", Modifier.weight(1f)) }
        when (state.gameMode) {
            com.hienthai.fastowin.navigation.GameMode.COMBO -> LiveMetric(
                "Combo",
                "${state.player.combo} • x${comboMultiplier(state.player.combo)}",
                Modifier.weight(1f)
            )
            com.hienthai.fastowin.navigation.GameMode.SURVIVAL -> LiveMetric(
                "Sinh tồn",
                "${state.player.lives} mạng",
                Modifier.weight(1f)
            )
            com.hienthai.fastowin.navigation.GameMode.SPEED_UP -> LiveMetric(
                "Nhịp",
                "${state.player.correctSelections + 1}/$GAME_NUMBER_COUNT",
                Modifier.weight(1f)
            )
            else -> Unit
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun FeedbackBurst(effect: GameFeedbackEffect) {
    val correct = effect == GameFeedbackEffect.CORRECT
    Surface(
        modifier = Modifier.size(76.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (correct) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (correct) "✓" else "×",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = if (correct) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private fun comboMultiplier(combo: Int): Int = when {
    combo >= 20 -> 4
    combo >= 10 -> 3
    combo >= 5 -> 2
    else -> 1
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
    onOpponentInfo: (() -> Unit)? = null,
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
            onViewInfo = onOpponentInfo,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerScoreCard(
    label: String,
    player: PlayerState,
    isLocal: Boolean,
    onViewInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isLocal) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isLocal) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier.then(
            if (onViewInfo == null) Modifier else Modifier.clickable(onClick = onViewInfo)
        ),
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
private fun TargetPanel(currentTarget: Int, completedCount: Int) {
    val completed = completedCount.coerceIn(0, GAME_NUMBER_COUNT)
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
    selectedNumbers: List<Int> = emptyList(),
    enabled: Boolean = true,
    boardStyle: BoardStyle = BoardStyle.CLASSIC,
    onNumberClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val columnCount = if (maxWidth >= 600.dp) 10 else 5
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().testTag("number_grid")
        ) {
            items(numbers, key = { it }) { number ->
                NumberCell(
                    number = number,
                    isCompleted = number in selectedNumbers,
                    enabled = enabled,
                    boardStyle = boardStyle,
                    onClick = { onNumberClick(number) }
                )
            }
        }
    }
}

@Composable
fun NumberCell(
    number: Int,
    isCompleted: Boolean,
    enabled: Boolean = true,
    boardStyle: BoardStyle = BoardStyle.CLASSIC,
    onClick: () -> Unit
) {
    val activeColor = when (boardStyle) {
        BoardStyle.CLASSIC -> MaterialTheme.colorScheme.surface
        BoardStyle.OCEAN -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        BoardStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.primaryContainer
    }
    val activeContentColor = when (boardStyle) {
        BoardStyle.CLASSIC -> MaterialTheme.colorScheme.onSurface
        BoardStyle.OCEAN, BoardStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val activeBorderColor = when (boardStyle) {
        BoardStyle.CLASSIC -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        BoardStyle.OCEAN -> MaterialTheme.colorScheme.primary
        BoardStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .testTag("game_number_$number")
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled && !isCompleted, onClick = onClick),
        color = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else activeColor,
        shape = RoundedCornerShape(16.dp),
        border = if (isCompleted) null else BorderStroke(
            1.dp,
            activeBorderColor
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
                else activeContentColor,
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
