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
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
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
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onNumberClick: (Int) -> Unit,
    onFinish: () -> Unit,
    onOpenFriendProfile: (String) -> Unit = {},
    onExit: () -> Unit = {},
    allowExit: Boolean = true,
    onSendEmoji: (String) -> Unit = {},
    preferences: AppPreferences = AppPreferences(),
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var showExitConfirmation by remember { mutableStateOf(false) }
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    SystemBackHandler(enabled = allowExit) {
        if (allowExit) showExitConfirmation = true
    }
    if (showExitConfirmation && allowExit) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Rời trận đấu?") },
            text = { Text("Bạn sẽ bị xử thua và trở về sảnh. Đối thủ vẫn xem được kết quả trận.") },
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
                    if (allowExit) {
                        IconButton(onClick = { showExitConfirmation = true }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Rời trận")
                        }
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
                    var showEmojiMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showEmojiMenu = true },
                            modifier = Modifier
                                .testTag("open_emoji_menu")
                                .semantics { contentDescription = "Gửi biểu cảm" }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = showEmojiMenu,
                            onDismissRequest = { showEmojiMenu = false }
                        ) {
                            val emojis = listOf("😀", "😂", "😡", "😭", "👍", "👎")
                            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                                emojis.forEach { emoji ->
                                    TextButton(
                                        onClick = {
                                            onSendEmoji(emoji)
                                            showEmojiMenu = false
                                        },
                                        contentPadding = PaddingValues(8.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("send_emoji:$emoji")
                                    ) {
                                        Text(emoji, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
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
                    state = state,
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
                            if (preferences.soundEnabled) playFeedbackSound(effect)
                            if (preferences.vibrationEnabled) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onNumberClick(number)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            EmojiOverlay(state.activeEmojis)
        }
    }
}

@Composable
private fun CloseScoreWarning(state: GameState) {
    val is2v2 = state.gameMode == com.hienthai.fastowin.navigation.GameMode.TEAM_2V2
    val myScore = if (is2v2) state.player.score + state.teammates.sumOf { it.score } else state.player.score
    val opponentScore = if (is2v2) state.opponents.sumOf { it.score } else state.opponent.score
    val myCorrect = if (is2v2) state.player.correctSelections + state.teammates.sumOf { it.correctSelections } else state.player.correctSelections
    val oppCorrect = if (is2v2) state.opponents.sumOf { it.correctSelections } else state.opponent.correctSelections

    val difference = abs(myScore - opponentScore)
    val enoughProgress = myCorrect + oppCorrect >= 6
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
    state: GameState,
    onOpponentInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val is2v2 = state.gameMode == com.hienthai.fastowin.navigation.GameMode.TEAM_2V2
    val myScore = if (is2v2) {
        state.player.score + state.teammates.sumOf { it.score }
    } else {
        state.player.score
    }
    val opponentScore = if (is2v2) {
        state.opponents.sumOf { it.score }
    } else {
        state.opponent.score
    }
    val labelMe = if (is2v2) "ĐỘI CỦA BẠN" else "BẠN"
    val labelOpp = if (is2v2) "ĐỘI ĐỐI THỦ" else "ĐỐI THỦ"
    val nameMe = if (is2v2) "(${state.player.name} & ${state.teammates.firstOrNull()?.name ?: "..."})" else state.player.name
    val nameOpp = if (is2v2) "(${state.opponents.joinToString(" & ") { it.name }})" else state.opponent.name

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerScoreCard(
            label = labelMe,
            name = nameMe,
            score = myScore,
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
            label = labelOpp,
            name = nameOpp,
            score = opponentScore,
            isLocal = false,
            onViewInfo = onOpponentInfo,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerScoreCard(
    label: String,
    name: String,
    score: Int,
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
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = score.toString(),
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
@Composable
private fun EmojiOverlay(emojis: List<com.hienthai.fastowin.state.EmojiEvent>) {
    Box(modifier = Modifier.fillMaxSize()) {
        emojis.forEach { emoji ->
            key(emoji.id) {
                var startAnimation by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    startAnimation = true
                }
                val offsetY by animateFloatAsState(
                    targetValue = if (startAnimation) -300f else 0f,
                    animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing)
                )
                val alpha by animateFloatAsState(
                    targetValue = if (startAnimation) 0f else 1f,
                    animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
                )
                Text(
                    text = emoji.emojiId,
                    fontSize = 48.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 16.dp, y = offsetY.dp - 32.dp)
                        .alpha(alpha)
                        .testTag("received_emoji:${emoji.id}")
                )
            }
        }
    }
}
