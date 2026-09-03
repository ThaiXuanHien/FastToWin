package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class GameReaction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private val gameReactions = listOf(
    GameReaction("😀", "Vui vẻ", Icons.Rounded.SentimentSatisfiedAlt, Color(0xFFFFC83D)),
    GameReaction("😂", "Cười lớn", Icons.Rounded.SentimentVerySatisfied, Color(0xFFFFA726)),
    GameReaction("🔥", "Bùng cháy", Icons.Rounded.LocalFireDepartment, Color(0xFFFF5C5C)),
    GameReaction("🏆", "Chiến thắng", Icons.Rounded.EmojiEvents, Color(0xFFFFD54F)),
    GameReaction("❤️", "Yêu thích", Icons.Rounded.Favorite, Color(0xFFFF5C7A)),
    GameReaction("⚡", "Tăng tốc", Icons.Rounded.Bolt, Color(0xFF68D8FF))
)

private fun reactionFor(id: String): GameReaction =
    gameReactions.firstOrNull { it.id == id } ?: gameReactions.first()

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
    var wrongNumber by remember { mutableStateOf<Int?>(null) }
    var wrongFeedbackToken by remember { mutableIntStateOf(0) }
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    LaunchedEffect(wrongFeedbackToken) {
        if (wrongFeedbackToken > 0) {
            delay(180)
            wrongNumber = null
        }
    }
    SystemBackHandler(enabled = allowExit) {
        if (allowExit) showExitConfirmation = true
    }
    if (showExitConfirmation && allowExit) {
        ArcadeDialog(
            title = "Rời trận?",
            subtitle = "Chủ động rời trận sẽ bị xử thua${if (state.matchType == MatchType.RANKED) " và mất Elo" else ""}.",
            onDismissRequest = { showExitConfirmation = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = "RỜI TRẬN",
                    onClick = {
                        showExitConfirmation = false
                        onExit()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "TIẾP TỤC CHƠI",
                    onClick = { showExitConfirmation = false },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver) onFinish()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("game_screen"),
        containerColor = Color.Transparent,
        topBar = {
            FastToWinHeader(
                title = "${state.gameMode.title} · ${if (state.matchType == MatchType.RANKED) "Xếp hạng" else "Đấu thường"}",
                subtitle = state.currentRoomName ?: "Ván đấu 50 số",
                gold = 0,
                gems = 0,
                unreadNotifications = 0,
                onNotifications = {},
                onBack = if (allowExit) ({ showExitConfirmation = true }) else null,
                backIcon = Icons.Rounded.Close,
                showBalances = false,
                showNotifications = false
            )
        }
    ) { paddingValues ->
        ResponsiveScreen(
            modifier = Modifier.padding(paddingValues),
            maxContentWidth = 760.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            BoxWithConstraints(modifier = contentModifier) {
            val compactLandscape = maxHeight < 430.dp && maxWidth > maxHeight
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerScoreBar(
                    state = state,
                    timeLeftMillis = state.timeLeftMillis,
                    onOpponentInfo = if (opponentFriend == null) null else ({
                        onOpenFriendProfile(opponentFriend.userId)
                    })
                )

                if (preferences.visualEffectsEnabled && !compactLandscape) {
                    CloseScoreWarning(state)
                }

                TargetPanel(
                    currentTarget = state.currentTarget,
                    completedCount = state.player.selectedNumbers.size,
                    compact = compactLandscape
                )

                if (!compactLandscape) LiveMetricsBar(state, onSendEmoji)

                GameConnectionNotice(state)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    NumberGrid(
                        numbers = state.numbers,
                        selectedNumbers = state.player.selectedNumbers,
                        wrongNumber = wrongNumber,
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
                            if (number != state.currentTarget) {
                                wrongNumber = number
                                wrongFeedbackToken += 1
                            }
                            onNumberClick(number)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!compactLandscape) GameBottomSummary(state)
            }
            EmojiOverlay(state.activeEmojis)
            }
        }
    }
}

@Composable
private fun GameConnectionNotice(state: GameState) {
    val message = when (state.connectionStatus) {
        ConnectionStatus.DISCONNECTED -> "Mất kết nối. Đang thử kết nối lại…"
        ConnectionStatus.RECONNECTING -> "Đang kết nối lại trận đấu…"
        else -> state.message?.takeIf { it.contains("máy chủ", ignoreCase = true) }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(if (message == null) 4.dp else 32.dp),
        contentAlignment = Alignment.Center
    ) {
        message?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
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
        color = ArcadePalette.Gold800.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, ArcadePalette.Gold400.copy(alpha = 0.72f))
    ) {
        Text(
            text = if (difference == 0) "Đang hòa điểm — lượt tiếp theo rất quan trọng!"
            else "Bám rất sát — chỉ cách nhau $difference điểm",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ArcadePalette.Gold100
        )
    }
}

@Composable
private fun LiveMetricsBar(state: GameState, onSendEmoji: (String) -> Unit) {
    val speed = state.player.averageReactionMillis.takeIf { it > 0 }?.let {
        (10_000L / it).coerceAtMost(99L) / 10.0
    }
    var showEmojiMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val leading = when (state.gameMode) {
            com.hienthai.fastowin.navigation.GameMode.SURVIVAL -> "${state.player.lives} mạng"
            com.hienthai.fastowin.navigation.GameMode.SPEED_UP -> "NHỊP ${state.player.correctSelections + 1}/$GAME_NUMBER_COUNT"
            else -> "COMBO x${comboMultiplier(state.player.combo)}"
        }
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = if (state.player.combo >= 5) ArcadePalette.Violet600 else Color(0xFF102B60),
            border = BorderStroke(1.dp, ArcadePalette.Violet400.copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.gameMode == com.hienthai.fastowin.navigation.GameMode.SURVIVAL) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFFF6B84)
                    )
                }
                Text(
                    leading,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
        Text(
            text = "TỐC ĐỘ  ${speed?.let { "$it số/s" } ?: "--"}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFFA9BADC)
        )
        Box {
            Surface(
                onClick = { showEmojiMenu = true },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("open_emoji_menu")
                    .semantics { contentDescription = "Gửi biểu cảm" },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SentimentSatisfiedAlt, contentDescription = null)
                }
            }
            DropdownMenu(expanded = showEmojiMenu, onDismissRequest = { showEmojiMenu = false }) {
                Column(modifier = Modifier.padding(8.dp)) {
                    gameReactions.chunked(3).forEach { row ->
                        Row {
                            row.forEach { reaction ->
                                TextButton(
                                    onClick = {
                                        onSendEmoji(reaction.id)
                                        showEmojiMenu = false
                                    },
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("send_emoji:${reaction.id}")
                                        .semantics { contentDescription = reaction.label }
                                ) {
                                    Icon(
                                        imageVector = reaction.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = reaction.color
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameBottomSummary(state: GameState) {
    val speed = state.player.averageReactionMillis.takeIf { it > 0 }?.let {
        (10_000L / it).coerceAtMost(99L) / 10.0
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0A1E45),
        border = BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.58f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Đúng ${state.player.correctSelections}  ·  Sai ${state.player.wrongSelections}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFA9BADC)
            )
            Text(
                "${speed?.let { "$it số/s" } ?: "Đang đo"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = ArcadePalette.Blue300
            )
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = ArcadePalette.Navy800,
        border = BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.58f))
    ) {
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
private fun TimerBadge(timeLeftMillis: Long, modifier: Modifier = Modifier) {
    val seconds = (timeLeftMillis / 1_000) % 60
    val minutes = (timeLeftMillis / 1_000) / 60
    val urgent = timeLeftMillis < 10_000

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (urgent) ArcadePalette.Coral800 else ArcadePalette.Navy800,
        border = BorderStroke(1.dp, if (urgent) ArcadePalette.Coral400 else ArcadePalette.Gold400)
    ) {
        Text(
            text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontWeight = FontWeight.Black,
            color = if (urgent) Color.White else ArcadePalette.Gold400
        )
    }
}

@Composable
private fun PlayerScoreBar(
    state: GameState,
    timeLeftMillis: Long,
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
            avatar = state.player,
            isLocal = true,
            modifier = Modifier.weight(1f)
        )
        if (timeLeftMillis > 0L) {
            TimerBadge(timeLeftMillis)
        } else {
            Text(
                text = "ĐẤU",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.outline
            )
        }
        PlayerScoreCard(
            label = labelOpp,
            name = nameOpp,
            score = opponentScore,
            avatar = state.opponents.firstOrNull() ?: state.opponent,
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
    avatar: PlayerState,
    isLocal: Boolean,
    onViewInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = ArcadePalette.Navy800
    val contentColor = Color.White
    val accentColor = if (isLocal) ArcadePalette.Blue300 else ArcadePalette.Coral400

    Surface(
        modifier = modifier.then(
            if (onViewInfo == null) Modifier else Modifier.clickable(onClick = onViewInfo)
        ),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(2.dp, accentColor.copy(alpha = 0.55f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 9.dp, end = 7.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            PlayerAvatar(
                displayName = avatar.name,
                avatarId = avatar.avatarId,
                userId = avatar.id,
                frameId = avatar.frameId,
                size = 40.dp
            )
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
                modifier = Modifier.padding(end = 5.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = FontWeight.Black,
                fontSize = 27.sp
            )
        }
    }
}

@Composable
private fun TargetPanel(currentTarget: Int, completedCount: Int, compact: Boolean = false) {
    val displayedTarget = currentTarget.coerceAtMost(GAME_NUMBER_COUNT)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFF9FB8FF).copy(alpha = 0.72f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF643BD1), Color(0xFF3D72EF))),
                    RoundedCornerShape(22.dp)
                )
                    .padding(horizontal = 18.dp, vertical = if (compact) 4.dp else 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SỐ TIẾP THEO",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.Black
            )
            Text(
                text = displayedTarget.toString(),
                fontSize = if (compact) 29.sp else 42.sp,
                lineHeight = if (compact) 30.sp else 44.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            if (!compact) {
                Text(
                    text = "${completedCount.coerceIn(0, GAME_NUMBER_COUNT)}/$GAME_NUMBER_COUNT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
fun NumberGrid(
    numbers: List<Int>,
    selectedNumbers: List<Int> = emptyList(),
    wrongNumber: Int? = null,
    enabled: Boolean = true,
    boardStyle: BoardStyle = BoardStyle.CLASSIC,
    onNumberClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val columnCount = when {
            maxWidth >= 600.dp -> 10
            maxWidth > maxHeight -> 10
            else -> 5
        }
        val gridPadding = if (columnCount == 10) 6.dp else 8.dp
        val gridSpacing = if (columnCount == 10) 4.dp else 6.dp
        val rowCount = ((numbers.size + columnCount - 1) / columnCount).coerceAtLeast(1)
        val availableCellHeight = (
            maxHeight - (gridPadding * 2) - (gridSpacing * (rowCount - 1))
        ) / rowCount
        val cellHeight = availableCellHeight.coerceAtMost(if (columnCount == 10) 54.dp else 52.dp)
        val compactCells = cellHeight < 46.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(gridPadding),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize().testTag("number_grid")
        ) {
            items(numbers, key = { it }) { number ->
                NumberCell(
                    number = number,
                    isCompleted = number in selectedNumbers,
                    isWrong = number == wrongNumber,
                    enabled = enabled,
                    boardStyle = boardStyle,
                    compact = compactCells,
                    modifier = Modifier.height(cellHeight),
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
    isWrong: Boolean = false,
    enabled: Boolean = true,
    boardStyle: BoardStyle = BoardStyle.CLASSIC,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val classicColor = Color(0xFFF6F8FF)
    val classicContentColor = Color(0xFF10234A)
    val classicBorderColor = Color(0xFFD6DEF1)
    val activeColor = when {
        isWrong -> ArcadePalette.Coral600
        boardStyle == BoardStyle.HIGH_CONTRAST -> Color.White
        else -> when (boardStyle) {
            BoardStyle.CLASSIC -> classicColor
            BoardStyle.OCEAN -> ArcadePalette.Blue900
            BoardStyle.HIGH_CONTRAST -> Color.White
        }
    }
    val activeContentColor = when {
        isWrong -> Color.White
        boardStyle == BoardStyle.HIGH_CONTRAST -> Color.Black
        else -> when (boardStyle) {
            BoardStyle.CLASSIC -> classicContentColor
            BoardStyle.OCEAN, BoardStyle.HIGH_CONTRAST -> Color.White
        }
    }
    val activeBorderColor = when {
        isWrong -> ArcadePalette.Coral400
        boardStyle == BoardStyle.HIGH_CONTRAST -> Color.Black
        else -> when (boardStyle) {
            BoardStyle.CLASSIC -> classicBorderColor.copy(alpha = 0.64f)
            BoardStyle.OCEAN -> ArcadePalette.Blue300
            BoardStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.outline
        }
    }
    Box(
        modifier = modifier.testTag("game_number_$number")
    ) {
        if (!isCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = if (compact) 2.dp else 3.dp)
                    .background(
                        if (isWrong) Color(0xFF9E2940) else Color(0xFFAAB7D2),
                        RoundedCornerShape(if (compact) 8.dp else 11.dp)
                    )
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isCompleted) 0.dp else if (compact) 2.dp else 3.dp)
                .clip(RoundedCornerShape(if (compact) 8.dp else 11.dp))
                .clickable(enabled = enabled && !isCompleted, onClick = onClick),
            color = if (isCompleted) Color(0xFF244F99) else activeColor,
            shape = RoundedCornerShape(if (compact) 8.dp else 11.dp),
            border = if (isCompleted) null else BorderStroke(1.dp, activeBorderColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = if (isCompleted) Color(0xFF7995C9) else activeContentColor,
                    fontSize = if (compact) 15.sp else 18.sp
                )
            }
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
                val reaction = reactionFor(emoji.emojiId)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 16.dp, y = offsetY.dp - 32.dp)
                        .alpha(alpha)
                        .size(58.dp)
                        .testTag("received_emoji:${emoji.id}"),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF102B60).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, reaction.color.copy(alpha = 0.72f)),
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = reaction.icon,
                            contentDescription = reaction.label,
                            modifier = Modifier.size(38.dp),
                            tint = reaction.color
                        )
                    }
                }
            }
        }
    }
}
