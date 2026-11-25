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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.AddReaction
import com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.A1Dimens
import com.hienthai.fastowin.ui.theme.A1Palette
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
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.state.GAME_NUMBER_COUNT
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.matchTypeLabelKey
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
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
    val labelKey: TextKey,
    val icon: ImageVector,
    val color: Color
)

private val gameReactions = listOf(
    GameReaction("😀", TextKey.ReactionHappy, Icons.Rounded.SentimentSatisfiedAlt, Color(0xFFFFC83D)),
    GameReaction("😂", TextKey.ReactionLaugh, Icons.Rounded.SentimentVerySatisfied, Color(0xFFFFA726)),
    GameReaction("🔥", TextKey.ReactionFire, Icons.Rounded.LocalFireDepartment, Color(0xFFFF5C5C)),
    GameReaction("🏆", TextKey.ReactionVictory, Icons.Rounded.EmojiEvents, Color(0xFFFFD54F)),
    GameReaction("❤️", TextKey.ReactionLove, Icons.Rounded.Favorite, Color(0xFFFF5C7A)),
    GameReaction("⚡", TextKey.ReactionSpeed, Icons.Rounded.Bolt, Color(0xFF68D8FF))
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
    // Bàn số dùng chung: server chỉ gửi các số đã bị lấy, nên app tự đối chiếu các lần bấm của mình với
    // kết quả server trả về để tô ô của đối thủ màu hồng (".tile.opp"). Vào lại giữa trận thì không tô hồng.
    val tileOwners = remember(state.currentRoomId, state.gameMode) { OpponentTileTracker() }
    val opponentNumbers = tileOwners.opponentNumbers(
        selectedNumbers = state.player.selectedNumbers,
        correctSelections = state.player.correctSelections
    )
    // Ô vừa bấm đúng lóe xanh trong chốc lát (".tile.hit" của thiết kế).
    var hitNumber by remember { mutableStateOf<Int?>(null) }
    var hitFeedbackToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(hitFeedbackToken) {
        if (hitFeedbackToken > 0) {
            delay(320)
            hitNumber = null
        }
    }
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    LaunchedEffect(wrongFeedbackToken) {
        if (wrongFeedbackToken > 0) {
            delay(180)
            wrongNumber = null
        }
    }
    LaunchedEffect(state.authoritativeSnapshotRevision) {
        wrongNumber = null
    }
    SystemBackHandler(enabled = allowExit) {
        if (allowExit) showExitConfirmation = true
    }
    if (showExitConfirmation && allowExit) {
        ArcadeDialog(
            title = localized(TextKey.ExitMatchTitle),
            subtitle = localized(when {
                state.isBotMatch -> TextKey.BotMatchRules
                state.matchType == MatchType.RANKED -> TextKey.ForfeitRankedDescription
                else -> TextKey.ForfeitCasualDescription
            }),
            onDismissRequest = { showExitConfirmation = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = localized(TextKey.ExitMatch),
                    onClick = {
                        showExitConfirmation = false
                        onExit()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = localized(TextKey.ContinuePlaying),
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
                title = localized(
                    TextKey.GameHeader,
                    "mode" to localized(state.gameMode.titleKey),
                    "matchType" to localized(state.matchTypeLabelKey)
                ),
                subtitle = if (state.isBotMatch) localized(TextKey.BotMatchRules)
                    else state.currentRoomName ?: localized(TextKey.GameDefaultRoom),
                gold = 0,
                gems = 0,
                unreadNotifications = 0,
                onNotifications = {},
                onBack = if (allowExit) ({ showExitConfirmation = true }) else null,
                backIcon = Icons.Rounded.Close,
                showBalances = false,
                showNotifications = false,
                actions = { if (state.timeLeftMillis > 0L) TimerBadge(state.timeLeftMillis) }

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
            val sidePanelWidth = maxWidth * 0.42f
            val scoreBar: @Composable () -> Unit = {
                PlayerScoreBar(
                    state = state,
                    onOpponentInfo = if (opponentFriend == null) null else ({
                        onOpenFriendProfile(opponentFriend.userId)
                    })
                )
            }
            val board: @Composable (Modifier) -> Unit = { boardModifier ->
                Box(
                    modifier = boardModifier.testTag("game_board"),
                    contentAlignment = Alignment.Center
                ) {
                    NumberGrid(
                        numbers = state.numbers,
                        selectedNumbers = state.player.selectedNumbers,
                        opponentNumbers = opponentNumbers,
                        wrongNumber = wrongNumber,
                        hitNumber = hitNumber,
                        enabled = state.connectionStatus == ConnectionStatus.CONNECTED && !state.player.isFinished,
                        boardStyle = preferences.boardStyle,
                        onNumberClick = { number ->
                            if (number !in state.player.selectedNumbers) tileOwners.onTap(number)
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
                            } else {
                                hitNumber = number
                                hitFeedbackToken += 1
                            }
                            onNumberClick(number)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (compactLandscape) {
                // Màn ngang thấp: điểm, số tiếp theo và thống kê bên trái; bàn số chiếm phần còn lại bên phải.
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.width(sidePanelWidth).fillMaxHeight()) {
                        scoreBar()
                        TargetPanel(state = state, compact = true)
                        Spacer(Modifier.weight(1f))
                        GameFooter(state, onSendEmoji, compact = true)
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        GameConnectionNotice(state)
                        board(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    scoreBar()
                    TargetPanel(state = state)
                    GameConnectionNotice(state)
                    board(Modifier.fillMaxWidth().weight(1f))
                    GameFooter(state, onSendEmoji)
                }
            }
            EmojiOverlay(state.activeEmojis)
            }
        }
    }
}

@Composable
private fun GameConnectionNotice(state: GameState) {
    val message = when (state.connectionStatus) {
        ConnectionStatus.DISCONNECTED -> localized(TextKey.GameDisconnected)
        ConnectionStatus.RECONNECTING -> localized(TextKey.GameReconnecting)
        else -> null
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(if (message == null) 4.dp else 32.dp),
        contentAlignment = Alignment.Center
    ) {
        message?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = A1Palette.Ink,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Thanh dưới trận đấu (thiết kế A1): "Đúng N · Sai N · Tốc độ x s/số" và nút gửi biểu cảm.
 */
@Composable
private fun GameFooter(state: GameState, onSendEmoji: (String) -> Unit, compact: Boolean = false) {
    val secondsPerNumber = state.player.averageReactionMillis.takeIf { it > 0 }?.let { ((it + 50) / 100) / 10.0 }
    var showEmojiMenu by remember { mutableStateOf(false) }
    val sendReactionLabel = localized(TextKey.SendReaction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .a1Card(radius = 18.dp, depth = 3.dp)
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = A1Palette.GreenText)) {
                    append(localized(TextKey.GameCorrect, "count" to state.player.correctSelections))
                }
                append(" · ")
                withStyle(androidx.compose.ui.text.SpanStyle(color = A1Palette.RedText)) {
                    append(localized(TextKey.GameWrong, "count" to state.player.wrongSelections))
                }
                append(" · ")
                withStyle(androidx.compose.ui.text.SpanStyle(color = A1Palette.Muted)) {
                    append(
                        secondsPerNumber?.let { localized(TextKey.GameSpeedPerNumber, "value" to it) } ?: localized(TextKey.Measuring)
                    )
                }
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 13.sp else 15.sp),
            fontWeight = FontWeight.Bold,
            color = A1Palette.Ink,
            maxLines = 2,
            modifier = Modifier.weight(1f).testTag("game_footer_stats")
        )
        Box {
            ArcadeHeaderIconButton(
                onClick = { showEmojiMenu = true },
                modifier = Modifier.testTag("open_emoji_menu").semantics { contentDescription = sendReactionLabel }
            ) {
                Icon(Icons.Rounded.AddReaction, contentDescription = null)
            }
            DropdownMenu(
                expanded = showEmojiMenu,
                onDismissRequest = { showEmojiMenu = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(3.dp, A1Palette.Line)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    gameReactions.chunked(3).forEach { row ->
                        Row {
                            row.forEach { reaction ->
                                val reactionLabel = localized(reaction.labelKey)
                                TextButton(
                                    onClick = {
                                        onSendEmoji(reaction.id)
                                        showEmojiMenu = false
                                    },
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("send_emoji:${reaction.id}")
                                        .semantics { contentDescription = reactionLabel }
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

private fun comboMultiplier(combo: Int): Int = when {
    combo >= 20 -> 4
    combo >= 10 -> 3
    combo >= 5 -> 2
    else -> 1
}

/** Đồng hồ trận: nhãn nền navy, chữ vàng Baloo 20sp (đỏ khi còn dưới 10 giây). */
@Composable
private fun TimerBadge(timeLeftMillis: Long, modifier: Modifier = Modifier) {
    val seconds = (timeLeftMillis / 1_000) % 60
    val minutes = (timeLeftMillis / 1_000) / 60
    val urgent = timeLeftMillis < 10_000
    Row(
        modifier = modifier
            .testTag("game_timer")
            .background(if (urgent) A1Palette.Red2 else A1Palette.Ink, RoundedCornerShape(12.dp))
            .border(2.dp, if (urgent) A1Palette.RedDeep else A1Palette.Ink, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Rounded.Timer, contentDescription = null, tint = if (urgent) Color.White else A1Palette.Yellow1, modifier = Modifier.size(20.dp))
        Text(
            text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp),
            color = if (urgent) Color.White else A1Palette.Yellow1
        )
    }
}

@Composable
private fun PlayerScoreBar(
    state: GameState,
    onOpponentInfo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val is2v2 = state.gameMode == GameMode.TEAM_2V2
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
    val labelMe = localized(if (is2v2) TextKey.YourTeam else TextKey.LocalPlayerLabel)
    val labelOpp = if (is2v2) localized(TextKey.OpponentTeam) else state.opponent.name.uppercase()
    val nameMe = if (is2v2) "(${state.player.name} & ${state.teammates.firstOrNull()?.name ?: "..."})" else state.player.name
    val nameOpp = if (is2v2) "(${state.opponents.joinToString(" & ") { it.name }})" else state.opponent.name
    val opponent = state.opponents.firstOrNull() ?: state.opponent
    val opponentAvatar = if (state.isBotMatch) opponent.copy(id = null) else opponent

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            PlayerScoreCard(
                label = labelMe,
                found = state.player.correctSelections,
                score = myScore,
                avatar = state.player,
                isLocal = true,
                compact = compact,
                modifier = Modifier.weight(1f).testTag("player_score_local")
            )
            PlayerScoreCard(
                label = labelOpp,
                found = (state.opponents.firstOrNull() ?: state.opponent).correctSelections,
                score = opponentScore,
                avatar = opponentAvatar,
                isLocal = false,
                onViewInfo = onOpponentInfo,
                compact = compact,
                modifier = Modifier.weight(1f).testTag("player_score_opponent")
            )
        }
    }
}

/** Thẻ điểm (".sc" của thiết kế): avatar khung, nhãn BẠN/tên, "N số", điểm Baloo; đối thủ viền đỏ. */
@Composable
private fun PlayerScoreCard(
    label: String,
    found: Int,
    score: Int,
    avatar: PlayerState,
    isLocal: Boolean,
    onViewInfo: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val edge = if (isLocal) A1Palette.Line else A1Palette.RedDeep
    Row(
        modifier = modifier
            .a1Card(radius = 16.dp, depth = 5.dp, border = edge)
            .clip(RoundedCornerShape(16.dp))
            .then(if (onViewInfo == null) Modifier else Modifier.clickable(onClick = onViewInfo))
            .padding(start = 6.dp, end = if (compact) 8.dp else 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        PlayerAvatar(
            displayName = avatar.name,
            avatarId = avatar.avatarId,
            userId = avatar.id,
            frameId = avatar.frameId,
            size = if (compact) 38.dp else 52.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isLocal) A1Palette.Ink else A1Palette.RedDeep,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = localized(TextKey.GameFoundCount, "count" to found),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = A1Palette.Muted,
                maxLines = 1
            )
        }
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (compact) 22.sp else 28.sp,
                lineHeight = if (compact) 24.sp else 30.sp
            ),
            color = if (isLocal) A1Palette.Ink else A1Palette.RedText
        )
    }
}

/**
 * Thẻ "SỐ TIẾP THEO" (thiết kế A1): nền trắng → vàng nhạt, chữ nhỏ bên trái, số lớn 66sp,
 * "+điểm" xanh bay lên mỗi khi điểm tăng.
 */
@Composable
private fun TargetPanel(state: GameState, compact: Boolean = false) {
    val displayedTarget = state.currentTarget.coerceAtMost(GAME_NUMBER_COUNT)
    val completed = state.player.selectedNumbers.size.coerceIn(0, GAME_NUMBER_COUNT)
    val modeMetric = when (state.gameMode) {
        GameMode.SURVIVAL -> localized(TextKey.LivesCount, "count" to state.player.lives)
        GameMode.SPEED_UP -> localized(TextKey.PaceProgress, "current" to state.player.correctSelections + 1, "total" to GAME_NUMBER_COUNT)
        GameMode.COMBO -> "${localized(TextKey.ComboLabel)} x${comboMultiplier(state.player.combo)}"
        else -> null
    }
    // "+N" khi điểm tăng (".d" xanh + keyframes "up").
    var lastScore by remember { mutableIntStateOf(state.player.score) }
    var gain by remember { mutableIntStateOf(0) }
    var gainToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.player.score) {
        val delta = state.player.score - lastScore
        lastScore = state.player.score
        if (delta > 0) {
            gain = delta
            gainToken += 1
        }
    }
    val rise = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(gainToken) {
        if (gainToken > 0) {
            rise.snapTo(0f)
            rise.animateTo(1f, androidx.compose.animation.core.tween(1000))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .a1Card(background = Color.Transparent)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF8D6))), RoundedCornerShape(A1Dimens.CardRadius))
            .border(A1Dimens.Border, A1Palette.Line, RoundedCornerShape(A1Dimens.CardRadius))
            .heightIn(min = if (compact) 56.dp else 74.dp)
            .testTag("game_target"),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    localized(TextKey.NextNumber),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = A1Palette.Muted
                )
                Text(
                    listOfNotNull(
                        localized(TextKey.GameProgress, "done" to completed, "total" to GAME_NUMBER_COUNT),
                        modeMetric ?: gain.takeIf { it > 0 }?.let { localized(TextKey.GamePointsGain, "points" to it) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = A1Palette.Muted
                )
            }
            Text(
                text = displayedTarget.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (compact) 40.sp else 62.sp,
                    lineHeight = if (compact) 42.sp else 62.sp,
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                    )
                ),
                color = A1Palette.Ink,
                // Giữ khung chữ gọn theo chiều cao số (Baloo có phần trên/dưới rất rộng) để thẻ thấp hơn.
                modifier = Modifier
                    .height(if (compact) 44.dp else 58.dp)
                    .wrapContentHeight(unbounded = true)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (rise.value < 1f && gain > 0) {
            Text(
                "+$gain",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = A1Palette.Green2,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 18.dp)
                    .graphicsLayer {
                        translationY = -30.dp.toPx() * rise.value
                        alpha = 1f - rise.value
                    }
                    .clearAndSetSemantics { }
            )
        }
    }
}

@Composable
fun NumberGrid(
    numbers: List<Int>,
    selectedNumbers: List<Int> = emptyList(),
    opponentNumbers: Set<Int> = emptySet(),
    wrongNumber: Int? = null,
    hitNumber: Int? = null,
    enabled: Boolean = true,
    boardStyle: BoardStyle = BoardStyle.CLASSIC,
    onNumberClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        fun paddingFor(columns: Int) = if (columns >= 8) 10.dp else 14.dp
        fun spacingFor(columns: Int) = 6.dp
        // Điện thoại dọc: 7 cột như thiết kế; màn rộng/ngang tự chọn số cột cho ô gần vuông.
        val columnCount = if (maxWidth < 600.dp && maxHeight > maxWidth) 7 else (5..10).minBy { columns ->
            val padding = paddingFor(columns)
            val spacing = spacingFor(columns)
            val rows = ((numbers.size + columns - 1) / columns).coerceAtLeast(1)
            val cellWidth = (
                maxWidth - (padding * 2) - (spacing * (columns - 1))
            ) / columns
            val cellHeight = (
                (maxHeight - (padding * 2) - (spacing * (rows - 1))) / rows
            ).coerceAtMost(56.dp)
            abs(cellWidth.value - cellHeight.value)
        }
        val gridPadding = paddingFor(columnCount)
        val gridSpacing = spacingFor(columnCount)
        val rowCount = ((numbers.size + columnCount - 1) / columnCount).coerceAtLeast(1)
        val availableCellWidth = (
            maxWidth - (gridPadding * 2) - (gridSpacing * (columnCount - 1))
        ) / columnCount
        val availableCellHeight = (
            maxHeight - (gridPadding * 2) - (gridSpacing * (rowCount - 1))
        ) / rowCount
        val cellHeight = availableCellHeight.coerceAtMost(if (columnCount == 7) 52.dp else 56.dp)
        val cellSize = minOf(availableCellWidth, cellHeight)
        val compactCells = cellHeight < 46.dp || availableCellWidth < 46.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(gridPadding),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize().testTag("number_grid")
        ) {
            items(numbers, key = { it }) { number ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(cellHeight),
                    contentAlignment = Alignment.Center
                ) {
                    NumberCell(
                        number = number,
                        isCompleted = number in selectedNumbers,
                        takenByOpponent = number in opponentNumbers,
                        isWrong = number == wrongNumber,
                        isHit = number == hitNumber,
                        enabled = enabled,
                        boardStyle = boardStyle,
                        compact = compactCells,
                        modifier = Modifier.fillMaxWidth().height(cellHeight),
                        onClick = { onNumberClick(number) }
                    )
                }
            }
        }
    }
}

/**
 * Ô số A1 (".tile"): trắng viền navy 3dp, đế 4dp; ô đã tìm lún xuống, xanh xám nhạt;
 * bấm đúng lóe xanh lá, bấm sai lóe đỏ.
 */
@Composable
fun NumberCell(
    number: Int,
    isCompleted: Boolean,
    takenByOpponent: Boolean = false,
    isWrong: Boolean = false,
    isHit: Boolean = false,
    enabled: Boolean = true,
    boardStyle: BoardStyle = BoardStyle.CLASSIC,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val radius = if (compact) 11.dp else 13.dp
    val depth = if (compact) 3.dp else 4.dp
    val shape = RoundedCornerShape(radius)
    data class TileColors(val face: Brush, val border: Color, val base: Color, val text: Color, val textShadow: Color?)
    val colors = when {
        isWrong -> TileColors(Brush.verticalGradient(listOf(A1Palette.Red1, A1Palette.Red2)), A1Palette.RedDeep, A1Palette.RedDeep, Color.White, A1Palette.RedDeep)
        isHit -> TileColors(Brush.verticalGradient(listOf(A1Palette.Green1, A1Palette.Green2)), A1Palette.GreenDeep, A1Palette.GreenDeep, Color.White, A1Palette.GreenDeep)
        isCompleted && takenByOpponent -> TileColors(androidx.compose.ui.graphics.SolidColor(Color(0xFFFFD9D9)), Color(0xFFC07A7A), Color.Transparent, Color(0xFF9A4A4A), null)
        isCompleted -> TileColors(androidx.compose.ui.graphics.SolidColor(Color(0xFFCFE0F5)), Color(0xFF8AA6C8), Color.Transparent, Color(0xFF5B7AA3), null)
        boardStyle == BoardStyle.OCEAN -> TileColors(Brush.verticalGradient(listOf(A1Palette.Blue1, A1Palette.Blue2)), A1Palette.BlueDeep, A1Palette.BlueDeep, Color.White, A1Palette.BlueDeep)
        boardStyle == BoardStyle.HIGH_CONTRAST -> TileColors(androidx.compose.ui.graphics.SolidColor(Color.White), Color.Black, Color.Black, Color.Black, null)
        else -> TileColors(androidx.compose.ui.graphics.SolidColor(Color.White), A1Palette.Line, A1Palette.Line, A1Palette.Ink, null)
    }
    val pressedDown = isCompleted
    Box(
        modifier = modifier
            .testTag("game_number_$number")
            .padding(bottom = depth)
            .drawBehind {
                if (!pressedDown) {
                    drawRoundRect(colors.base, topLeft = Offset(0f, depth.toPx()), size = size, cornerRadius = CornerRadius(radius.toPx()))
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (pressedDown) depth else 0.dp)
                .clip(shape)
                .background(colors.face)
                .border(A1Dimens.Border, colors.border, shape)
                .clickable(enabled = enabled && !isCompleted, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (compact) 18.sp else 21.sp,
                    lineHeight = if (compact) 20.sp else 24.sp,
                    shadow = colors.textShadow?.let { Shadow(it, Offset(0f, 4f), 0f) }
                ),
                color = colors.text
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
                player = PlayerState(name = "Hien", score = 70, currentTarget = 12),
                opponent = PlayerState(name = "Hieu", score = 40, currentTarget = 12)
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
                            contentDescription = localized(reaction.labelKey),
                            modifier = Modifier.size(38.dp),
                            tint = reaction.color
                        )
                    }
                }
            }
        }
    }
}

/**
 * Đoán ô nào do đối thủ lấy trên bàn số dùng chung. Mỗi lần server gửi trạng thái mới, các số vừa bị lấy
 * mà mình đang chờ kết quả được tính là của mình (tối đa bằng số lần đúng tăng thêm), số còn lại là của
 * đối thủ. Khi tổng không khớp với số lần đúng (vd. vào lại giữa trận) thì không tô.
 */
internal class OpponentTileTracker {
    private val pendingTaps = ArrayDeque<Int>()
    private val mine = mutableSetOf<Int>()
    private var seen = emptySet<Int>()
    private var lastCorrect = 0

    fun onTap(number: Int) {
        if (number !in pendingTaps) pendingTaps.addLast(number)
    }

    fun opponentNumbers(selectedNumbers: List<Int>, correctSelections: Int): Set<Int> {
        val selected = selectedNumbers.toSet()
        val newlyTaken = selected - seen
        var correctGain = (correctSelections - lastCorrect).coerceAtLeast(0)
        for (number in pendingTaps.toList()) {
            if (correctGain == 0) break
            if (number in newlyTaken) {
                mine += number
                pendingTaps.remove(number)
                correctGain -= 1
            }
        }
        // Số người khác vừa lấy mà mình bấm trễ thì không còn chờ nữa.
        pendingTaps.removeAll { it in selected }
        seen = selected
        lastCorrect = correctSelections
        return if (mine.size == correctSelections) selected - mine else emptySet()
    }
}
