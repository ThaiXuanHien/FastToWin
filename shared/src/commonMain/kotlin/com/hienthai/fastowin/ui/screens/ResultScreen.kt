package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.platform.GameFeedbackEffect
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.platform.ResultShareContent
import com.hienthai.fastowin.platform.rememberResultImageSharer
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.MATCH_DRAW_REWARD_GOLD
import com.hienthai.fastowin.protocol.MATCH_DRAW_REWARD_XP
import com.hienthai.fastowin.protocol.MATCH_LOSS_REWARD_GOLD
import com.hienthai.fastowin.protocol.MATCH_LOSS_REWARD_XP
import com.hienthai.fastowin.protocol.MATCH_WIN_REWARD_GOLD
import com.hienthai.fastowin.protocol.MATCH_WIN_REWARD_XP
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.state.PostMatchFriendStatus
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_leaderboard_trophy
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.RewardAmounts
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun ResultScreen(
    state: GameState,
    onRestart: () -> Unit,
    onBack: () -> Unit = onRestart,
    onRematch: () -> Unit,
    onCancelRematch: () -> Unit,
    onDeclineRematch: () -> Unit,
    onConnectOpponent: () -> Unit,
    onBlockOpponent: () -> Unit,
    onOpenFriendProfile: (String) -> Unit = {},
    onOpenTournament: () -> Unit = {},
    onShareResult: ((ResultShareContent) -> Unit)? = null,
    preferences: AppPreferences = AppPreferences(),
    modifier: Modifier = Modifier
) {
    val is2v2 = state.gameMode == com.hienthai.fastowin.navigation.GameMode.TEAM_2V2
    val myScore = if (is2v2) state.player.score + state.teammates.sumOf { it.score } else state.player.score
    val opponentScore = if (is2v2) state.opponents.sumOf { it.score } else state.opponent.score

    val isDraw = state.winnerPlayerId == null && myScore == opponentScore
    val isWinner = state.winnerPlayerId?.let { winnerId ->
        if (is2v2) {
            winnerId == state.player.id || state.teammates.any { it.id == winnerId }
        } else {
            winnerId == state.player.id
        }
    } ?: (myScore > opponentScore)
    val hapticFeedback = LocalHapticFeedback.current
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    val imageSharer = rememberResultImageSharer()
    val shareContent = resultShareContent(
        state = state,
        isDraw = isDraw,
        isWinner = isWinner,
        playerScore = myScore,
        opponentScore = opponentScore,
        is2v2 = is2v2
    )
    SystemBackHandler(onBack = onBack)

    LaunchedEffect(isWinner, isDraw) {
        val effect = when {
            isDraw -> GameFeedbackEffect.CORRECT
            isWinner -> GameFeedbackEffect.WIN
            else -> GameFeedbackEffect.LOSS
        }
        if (preferences.soundEnabled) playFeedbackSound(effect)
        if (preferences.vibrationEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    if (showBlockConfirmation && !state.isRematchRequestedByOpponent) {
        ArcadeDialog(
            title = "CHẶN ${state.opponent.name.uppercase()}?",
            subtitle = "Hai người sẽ không thể kết bạn, gửi lời mời hoặc đấu lại.",
            onDismissRequest = { showBlockConfirmation = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = "CHẶN",
                    onClick = {
                        showBlockConfirmation = false
                        onBlockOpponent()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = "HỦY",
                    onClick = { showBlockConfirmation = false },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (state.isRematchRequestedByOpponent) {
        ArcadeDialog(
            title = "MỜI ĐẤU LẠI",
            subtitle = "${state.opponent.name} muốn đấu lại với bạn.",
            onDismissRequest = {}
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = if (state.isRematchActionPending) "ĐANG XỬ LÝ..." else "CHẤP NHẬN",
                    onClick = onRematch,
                    icon = Icons.Rounded.Check,
                    modifier = Modifier.fillMaxWidth().testTag("accept_rematch"),
                    style = ArcadeActionStyle.GOLD,
                    enabled = !state.isRematchActionPending
                )
                ArcadeActionButton(
                    label = "TỪ CHỐI",
                    onClick = onDeclineRematch,
                    modifier = Modifier.fillMaxWidth().testTag("decline_rematch"),
                    style = ArcadeActionStyle.OUTLINE,
                    enabled = !state.isRematchActionPending
                )
            }
        }
    }

    val resultTitle = when {
        isDraw -> "HÒA!"
        isWinner -> "CHIẾN THẮNG!"
        else -> "THUA CUỘC"
    }
    val resultDescription = when {
        state.didForfeitLastMatch -> "Bạn đã chủ động rời trận và bị xử thua."
        isDraw -> "Hai bên ngang điểm sau trận đấu."
        isWinner -> "Bạn đã giành chiến thắng!"
        is2v2 -> "Đội đối thủ đã giành chiến thắng."
        else -> "${state.opponent.name} đã giành chiến thắng."
    }
    val eloSummary = state.lastMatchEloChange
        ?.takeIf { state.matchType == MatchType.RANKED }
        ?.let { if (it >= 0) "+$it Elo" else "$it Elo" }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("result_screen"),
        containerColor = Color.Transparent,
        topBar = {
            FastToWinHeader(
                title = "Kết quả trận",
                subtitle = "${state.gameMode.title} • ${if (state.matchType == MatchType.RANKED) "Xếp hạng" else "Đấu thường"}",
                gold = 0,
                gems = 0,
                unreadNotifications = 0,
                onNotifications = {},
                onBack = onBack,
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
            Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArcadeFeatureHero(
                    illustration = Res.drawable.arcade_leaderboard_trophy,
                    title = resultTitle,
                    subtitle = listOfNotNull(resultDescription, eloSummary).joinToString(" • "),
                    accent = when {
                        isDraw -> MaterialTheme.colorScheme.secondary
                        isWinner -> ArcadePalette.Gold500
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                ScoreBoard(
                    state = state,
                    isDraw = isDraw,
                    isPlayerWinner = isWinner,
                    myScore = myScore,
                    opponentScore = opponentScore,
                    onOpponentInfo = if (opponentFriend == null) null else ({
                        onOpenFriendProfile(opponentFriend.userId)
                    })
                )
                MatchSummaryCard(state)
                PaceAnalysisCard(state.player)
                EloCard(state)
                if (state.profile != null) {
                    MatchRewardCard(isDraw = isDraw, isWinner = isWinner)
                }
                if (state.isTournamentMatch) {
                    TournamentResultCard(state = state, onOpenTournament = onOpenTournament)
                } else {
                    RematchCard(
                        state = state,
                        onRematch = onRematch
                    )
                }
                ArcadeActionButton(
                    label = "CHIA SẺ KẾT QUẢ",
                    onClick = {
                        shareError = null
                        if (onShareResult != null) {
                            onShareResult(shareContent)
                        } else {
                            imageSharer.share(shareContent).onFailure {
                                shareError = "Không thể tạo ảnh chia sẻ. Vui lòng thử lại."
                            }
                        }
                    },
                    style = ArcadeActionStyle.OUTLINE,
                    icon = Icons.Rounded.Share,
                    modifier = Modifier.fillMaxWidth().testTag("share_result")
                )
                shareError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OpponentActions(
                    state = state,
                    onConnect = onConnectOpponent,
                    onBlock = { showBlockConfirmation = true }
                )

                if (!state.isTournamentMatch) {
                    ArcadeActionButton(
                        label = "Về sảnh",
                        onClick = onRestart,
                        style = ArcadeActionStyle.OUTLINE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ScoreBoard(
    state: GameState,
    isDraw: Boolean,
    isPlayerWinner: Boolean,
    myScore: Int,
    opponentScore: Int,
    onOpponentInfo: (() -> Unit)? = null
) {
    val is2v2 = state.gameMode == com.hienthai.fastowin.navigation.GameMode.TEAM_2V2
    val myName = if (is2v2) "Đội của bạn" else "${state.player.name} (Bạn)"
    val opponentName = if (is2v2) "Đội đối thủ" else state.opponent.name
    val myResult = if (isDraw) "Hòa" else if (isPlayerWinner) "Thắng" else "Thua"
    val opponentResult = if (isDraw) "Hòa" else if (!isPlayerWinner) "Thắng" else "Thua"

    ArcadePanel(
        modifier = Modifier.fillMaxWidth(),
        accent = if (isDraw) MaterialTheme.colorScheme.secondary else ArcadePalette.Gold500
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            val stackPlayers = maxWidth < 330.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.35f
            if (stackPlayers) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResultScoreCard(
                        name = myName,
                        score = myScore,
                        result = myResult,
                        player = state.player,
                        isLocal = true,
                        isWinner = !isDraw && isPlayerWinner,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "KẾT QUẢ",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    ResultScoreCard(
                        name = opponentName,
                        score = opponentScore,
                        result = opponentResult,
                        player = state.opponents.firstOrNull() ?: state.opponent,
                        isLocal = false,
                        isWinner = !isDraw && !isPlayerWinner,
                        onClick = onOpponentInfo,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResultScoreCard(
                        name = myName,
                        score = myScore,
                        result = myResult,
                        player = state.player,
                        isLocal = true,
                        isWinner = !isDraw && isPlayerWinner,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "KẾT\nQUẢ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    ResultScoreCard(
                        name = opponentName,
                        score = opponentScore,
                        result = opponentResult,
                        player = state.opponents.firstOrNull() ?: state.opponent,
                        isLocal = false,
                        isWinner = !isDraw && !isPlayerWinner,
                        onClick = onOpponentInfo,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultScoreCard(
    name: String,
    score: Int,
    result: String,
    player: PlayerState,
    isLocal: Boolean,
    isWinner: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = if (isLocal) ArcadePalette.Blue300 else ArcadePalette.Coral400
    val container = ArcadePalette.Navy800
    val content = Color.White
    Surface(
        modifier = modifier.then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
        ),
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(2.dp, accent.copy(alpha = if (isWinner) 0.82f else 0.46f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerAvatar(
                displayName = player.name,
                avatarId = player.avatarId,
                userId = player.id,
                frameId = player.frameId,
                size = 42.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    score.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = content
                )
                Text(
                    result,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWinner) accent else content.copy(alpha = 0.68f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EloCard(state: GameState) {
    if (state.matchType == MatchType.CASUAL) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trận thường", fontWeight = FontWeight.SemiBold)
                Text(
                    "Không ảnh hưởng Elo",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }
    val eloChange = state.lastMatchEloChange ?: return
    val season = state.profile?.progression?.season
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trận xếp hạng", fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(if (eloChange >= 0) "+$eloChange Elo" else "$eloChange Elo")
                        state.lastMatchEloRating?.let { append("  •  $it") }
                    },
                    color = if (eloChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Black
                )
            }
            if (season != null && season.placementMatchesPlayed < season.placementMatchesRequired) {
                Text(
                    "Phân hạng ${season.placementMatchesPlayed}/${season.placementMatchesRequired}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MatchRewardCard(isDraw: Boolean, isWinner: Boolean) {
    val gold = when {
        isDraw -> MATCH_DRAW_REWARD_GOLD
        isWinner -> MATCH_WIN_REWARD_GOLD
        else -> MATCH_LOSS_REWARD_GOLD
    }
    val xp = when {
        isDraw -> MATCH_DRAW_REWARD_XP
        isWinner -> MATCH_WIN_REWARD_XP
        else -> MATCH_LOSS_REWARD_XP
    }
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val stackRewards = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.35f
            if (stackRewards) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Thưởng trận", fontWeight = FontWeight.Black)
                    RewardAmounts(gold = gold, xp = xp, gems = 0)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Thưởng trận", fontWeight = FontWeight.Black)
                    RewardAmounts(gold = gold, xp = xp, gems = 0)
                }
            }
        }
    }
}

@Composable
private fun TournamentResultCard(state: GameState, onOpenTournament: () -> Unit) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.tertiary) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (state.currentTournamentRound == 2) "Trận chung kết" else "Trận bán kết",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                if (state.currentTournamentRound == 2) {
                    "Giải đấu đã hoàn tất. Mở nhánh đấu để xem nhà vô địch."
                } else {
                    "Nhánh đấu đã được cập nhật. Trận tiếp theo sẽ được tạo tự động."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ArcadeActionButton(
                label = "Xem nhánh đấu",
                onClick = onOpenTournament,
                modifier = Modifier.fillMaxWidth().testTag("open_tournament_bracket"),
                style = ArcadeActionStyle.GOLD
            )
        }
    }
}

@Composable
private fun MatchSummaryCard(state: GameState) {
    val attempts = state.player.correctSelections + state.player.wrongSelections
    val accuracy = if (attempts == 0) 0 else state.player.correctSelections * 100 / attempts
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tóm tắt của bạn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stackMetrics = maxWidth < 330.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.35f
                if (stackMetrics) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryMetric("Phản xạ TB", formatReaction(state.player.averageReactionMillis), Modifier.fillMaxWidth())
                        SummaryMetric("Chính xác", "$accuracy%", Modifier.fillMaxWidth())
                        SummaryMetric("Combo", "x${state.player.combo}", Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryMetric("Phản xạ TB", formatReaction(state.player.averageReactionMillis), Modifier.weight(1f))
                        SummaryMetric("Chính xác", "$accuracy%", Modifier.weight(1f))
                        SummaryMetric("Combo", "x${state.player.combo}", Modifier.weight(1f))
                    }
                }
            }
            Text(
                "Đúng ${state.player.correctSelections} • Sai ${state.player.wrongSelections} • ${formatDuration(state.lastMatchDurationMillis)}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PaceAnalysisCard(player: PlayerState) {
    val hasAnalysis = player.fastestSegmentAverageMillis > 0L || player.slowestSegmentAverageMillis > 0L
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.secondary) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Phân tích nhịp chơi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!hasAnalysis) {
                Text(
                    "Chưa đủ dữ liệu để phân tích chặng nhanh và chậm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Mỗi chặng gồm tối đa 10 số bạn đã tìm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentRow(
                    label = "Nhanh nhất",
                    start = player.fastestSegmentStart,
                    end = player.fastestSegmentEnd,
                    averageMillis = player.fastestSegmentAverageMillis,
                    highlight = true
                )
                if (
                    player.slowestSegmentStart != player.fastestSegmentStart ||
                    player.slowestSegmentEnd != player.fastestSegmentEnd
                ) {
                    SegmentRow(
                        label = "Chậm nhất",
                        start = player.slowestSegmentStart,
                        end = player.slowestSegmentEnd,
                        averageMillis = player.slowestSegmentAverageMillis,
                        highlight = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(
    label: String,
    start: Int,
    end: Int,
    averageMillis: Long,
    highlight: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontWeight = FontWeight.Bold)
                Text(
                    "Lượt $start–$end",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(formatReaction(averageMillis), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RematchCard(
    state: GameState,
    onRematch: () -> Unit
) {
    if (state.matchType == MatchType.RANKED) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Text(
                "Trận xếp hạng không hỗ trợ đấu lại. Hãy về sảnh để ghép một đối thủ mới.",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
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

    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Violet600) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Đấu lại", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            state.rematchNotice?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if ((state.isRematchRequestedByMe || state.isRematchRequestedByOpponent) && remainingSeconds != null) {
                Text("Còn $remainingSeconds giây để phản hồi", style = MaterialTheme.typography.bodySmall)
            }
            when {
                !state.hasOpponent -> ArcadeActionButton(
                    label = "ĐỐI THỦ ĐÃ RỜI",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().testTag("result_rematch_action"),
                    style = ArcadeActionStyle.OUTLINE,
                    enabled = false
                )
                state.isRematchRequestedByOpponent -> ArcadeActionButton(
                    label = if (state.isRematchActionPending) "ĐANG XỬ LÝ..." else "ĐANG CHỜ PHẢN HỒI",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().testTag("result_rematch_action"),
                    style = ArcadeActionStyle.OUTLINE,
                    enabled = false
                )
                state.isRematchRequestedByMe -> ArcadeActionButton(
                    label = "ĐÃ MỜI",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().testTag("result_rematch_action"),
                    icon = Icons.Rounded.RestartAlt,
                    style = ArcadeActionStyle.OUTLINE,
                    enabled = false
                )
                else -> ArcadeActionButton(
                    label = if (state.isRematchActionPending) "Đang gửi..." else "Mời đấu lại",
                    onClick = onRematch,
                    icon = Icons.Rounded.RestartAlt,
                    modifier = Modifier.fillMaxWidth().testTag("result_rematch_action"),
                    style = ArcadeActionStyle.GOLD,
                    enabled = !state.isRematchActionPending
                )
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OpponentBlockButton(
            enabled = state.postMatchFriendStatus != PostMatchFriendStatus.BLOCKED,
            onClick = onBlock,
            modifier = Modifier.fillMaxWidth()
        )
        OpponentConnectButton(friendLabel, canConnect, onConnect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun OpponentConnectButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArcadeActionButton(
        label = label,
        onClick = onClick,
        enabled = enabled,
        icon = Icons.Rounded.GroupAdd,
        modifier = modifier,
        style = ArcadeActionStyle.OUTLINE
    )
}

@Composable
private fun OpponentBlockButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArcadeActionButton(
        label = "Chặn",
        onClick = onClick,
        enabled = enabled,
        icon = Icons.Rounded.Block,
        modifier = modifier,
        style = ArcadeActionStyle.DANGER
    )
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

private fun resultShareContent(
    state: GameState,
    isDraw: Boolean,
    isWinner: Boolean,
    playerScore: Int,
    opponentScore: Int,
    is2v2: Boolean
): ResultShareContent {
    val teamPlayers = if (is2v2) listOf(state.player) + state.teammates else listOf(state.player)
    val correctSelections = teamPlayers.sumOf { it.correctSelections }
    val attempts = teamPlayers.sumOf { it.correctSelections + it.wrongSelections }
    val accuracy = if (attempts == 0) 0 else correctSelections * 100 / attempts
    return ResultShareContent(
        result = when {
            isDraw -> "HÒA"
            isWinner -> "CHIẾN THẮNG"
            else -> "THUA CUỘC"
        },
        playerName = if (is2v2) "Đội của bạn" else state.player.name,
        playerScore = playerScore,
        opponentName = if (is2v2) "Đội đối thủ" else state.opponent.name,
        opponentScore = opponentScore,
        gameMode = state.gameMode.title,
        matchType = if (state.matchType == MatchType.RANKED) "Trận xếp hạng" else "Trận thường",
        duration = formatDuration(state.lastMatchDurationMillis),
        accuracy = "$accuracy%",
        elo = state.lastMatchEloChange?.takeIf { state.matchType == MatchType.RANKED }?.let {
            if (it >= 0) "+$it Elo" else "$it Elo"
        }
    )
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
