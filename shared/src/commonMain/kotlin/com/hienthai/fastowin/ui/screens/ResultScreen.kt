package com.hienthai.fastowin.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedRankedTierName
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.GameFeedbackEffect
import com.hienthai.fastowin.platform.ResultShareContent
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.platform.rememberResultImageSharer
import com.hienthai.fastowin.protocol.MATCH_DRAW_REWARD_GOLD
import com.hienthai.fastowin.protocol.MATCH_DRAW_REWARD_XP
import com.hienthai.fastowin.protocol.MATCH_LOSS_REWARD_GOLD
import com.hienthai.fastowin.protocol.MATCH_LOSS_REWARD_XP
import com.hienthai.fastowin.protocol.MATCH_WIN_REWARD_GOLD
import com.hienthai.fastowin.protocol.MATCH_WIN_REWARD_XP
import com.hienthai.fastowin.protocol.MatchType
import com.hienthai.fastowin.protocol.UgcTargetType
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.state.PlayerState
import com.hienthai.fastowin.state.PostMatchFriendStatus
import com.hienthai.fastowin.state.matchTypeLabelKey
import com.hienthai.fastowin.ui.components.A1BottomSheet
import com.hienthai.fastowin.ui.components.A1CoinIcon
import com.hienthai.fastowin.ui.components.A1Dimens
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeHeaderIconButton
import com.hienthai.fastowin.ui.components.LocalNewOnlineIntentAllowed
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.RewardBreakdownCard
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.TiaMascot
import com.hienthai.fastowin.ui.components.TiaMood
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.drawLightRays
import com.hienthai.fastowin.ui.components.nextRankedTier
import com.hienthai.fastowin.ui.components.ratingProgressWithinTier
import com.hienthai.fastowin.ui.components.rememberClockMillis
import com.hienthai.fastowin.ui.components.rememberUgcReport
import com.hienthai.fastowin.ui.theme.A1Palette
import com.hienthai.fastowin.ui.theme.FastToWinTheme
import kotlin.math.ceil

private enum class ResultOutcome { WIN, DRAW, LOSS }

/** Màu dải tiêu đề (".ribbon" đỏ khi thắng, ".ribbon.b" xanh xám khi thua, vàng khi hòa). */
private data class RibbonTone(val top: Color, val bottom: Color, val edge: Color)

private val WinRibbon = RibbonTone(A1Palette.Red1, A1Palette.Red2, A1Palette.RedDeep)
private val LossRibbon = RibbonTone(Color(0xFF8FB4D8), Color(0xFF5F86B0), Color(0xFF28456A))
private val DrawRibbon = RibbonTone(A1Palette.Yellow1, A1Palette.Yellow2, A1Palette.YellowBorder)

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
    onOpenRewardEvent: ((String) -> Unit)? = null,
    onShareResult: ((ResultShareContent) -> Unit)? = null,
    /** Tìm trận mới cùng chế độ (nút CHƠI TIẾP / THỬ LẠI); mặc định về sảnh. */
    onPlayAgain: (() -> Unit)? = null,
    /** Mở luyện tập (nút "Luyện tập" ở màn thua). */
    onPractice: (() -> Unit)? = null,
    preferences: AppPreferences = AppPreferences(),
    modifier: Modifier = Modifier
) {
    val localization = LocalLocalization.current
    val is2v2 = state.gameMode == GameMode.TEAM_2V2
    val myScore = if (is2v2) state.player.score + state.teammates.sumOf { it.score } else state.player.score
    val opponentScore = if (is2v2) state.opponents.sumOf { it.score } else state.opponent.score

    val winningTeam = state.winnerTeamId?.takeIf { is2v2 && it.isNotBlank() && state.player.teamId != null }
    val isDraw = winningTeam == null && state.winnerPlayerId == null && myScore == opponentScore
    val isWinner = winningTeam?.let { it == state.player.teamId } ?: state.winnerPlayerId?.let { winnerId ->
        if (is2v2) {
            winnerId == state.player.id || state.teammates.any { it.id == winnerId }
        } else {
            winnerId == state.player.id
        }
    } ?: (myScore > opponentScore)
    val outcome = when {
        isDraw -> ResultOutcome.DRAW
        isWinner -> ResultOutcome.WIN
        else -> ResultOutcome.LOSS
    }
    val attempts = state.player.correctSelections + state.player.wrongSelections
    val accuracy = if (attempts == 0) 0 else state.player.correctSelections * 100 / attempts

    val hapticFeedback = LocalHapticFeedback.current
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var showSafetySheet by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val opponentFriend = state.social.friends.firstOrNull { it.userId == state.opponent.id }
    val imageSharer = rememberResultImageSharer()
    val shareContent = resultShareContent(
        state = state,
        isDraw = isDraw,
        isWinner = isWinner,
        playerScore = myScore,
        opponentScore = opponentScore,
        is2v2 = is2v2,
        localization = localization
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
            title = localized(TextKey.BlockPlayerTitle, "player" to state.opponent.name.uppercase()),
            subtitle = localized(TextKey.BlockPlayerDescription),
            onDismissRequest = { showBlockConfirmation = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeActionButton(
                    label = localized(TextKey.Block),
                    onClick = {
                        showBlockConfirmation = false
                        onBlockOpponent()
                    },
                    style = ArcadeActionStyle.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
                ArcadeActionButton(
                    label = localized(TextKey.Cancel).uppercase(),
                    onClick = { showBlockConfirmation = false },
                    style = ArcadeActionStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (state.isRematchRequestedByOpponent) {
        ArcadeDialog(
            title = localized(TextKey.RematchInviteTitle),
            subtitle = localized(TextKey.RematchInviteDescription, "player" to state.opponent.name),
            onDismissRequest = {}
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcadeActionButton(
                    label = localized(if (state.isRematchActionPending) TextKey.Processing else TextKey.Accept),
                    onClick = onRematch,
                    icon = Icons.Rounded.Check,
                    modifier = Modifier.fillMaxWidth().testTag("accept_rematch"),
                    style = ArcadeActionStyle.GOLD,
                    enabled = LocalNewOnlineIntentAllowed.current && !state.isRematchActionPending
                )
                ArcadeActionButton(
                    label = localized(TextKey.Decline),
                    onClick = onDeclineRematch,
                    modifier = Modifier.fillMaxWidth().testTag("decline_rematch"),
                    style = ArcadeActionStyle.OUTLINE,
                    enabled = !state.isRematchActionPending
                )
            }
        }
    }

    if (showSafetySheet) {
        OpponentSafetySheet(
            state = state,
            onConnect = onConnectOpponent,
            onBlock = {
                showSafetySheet = false
                showBlockConfirmation = true
            },
            onDismiss = { showSafetySheet = false }
        )
    }

    val resultTitle = when (outcome) {
        ResultOutcome.DRAW -> localized(TextKey.DrawResult)
        ResultOutcome.WIN -> localized(TextKey.VictoryResult)
        ResultOutcome.LOSS -> localized(TextKey.DefeatResult)
    }
    val margin = opponentScore - myScore
    // Lời của Tia trong bong bóng: chỉ khi thua/hòa hoặc có thông báo riêng (rời trận, giải đấu).
    val bubbleText = when {
        state.message != null -> state.message
        state.isTournamentMatch -> localized(
            if (state.currentTournamentRound == 2) TextKey.TournamentFinishedDescription else TextKey.BracketUpdatedDescription
        )
        outcome == ResultOutcome.WIN -> null
        state.didForfeitLastMatch -> localized(TextKey.ForfeitResultDescription)
        outcome == ResultOutcome.DRAW -> localized(TextKey.DrawResultDescription)
        margin in 1..50 -> localized(TextKey.ResultNearMiss, "points" to margin)
        is2v2 -> localized(TextKey.OpposingTeamVictoryDescription)
        else -> localized(TextKey.OpponentVictoryDescription, "player" to state.opponent.name)
    }
    val stars = resultStars(outcome, accuracy, state.didForfeitLastMatch)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("result_screen")
    ) {
        ResultBackdrop(outcome = outcome, animate = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                ResultHero(outcome = outcome, stars = stars, bubbleText = bubbleText)
                ResultRibbon(
                    text = resultTitle,
                    tone = when (outcome) {
                        ResultOutcome.WIN -> WinRibbon
                        ResultOutcome.LOSS -> LossRibbon
                        ResultOutcome.DRAW -> DrawRibbon
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                ResultStatsCard(
                    state = state,
                    is2v2 = is2v2,
                    myScore = myScore,
                    opponentScore = opponentScore,
                    accuracy = accuracy,
                    onOpponentInfo = if (opponentFriend == null) null else ({
                        onOpenFriendProfile(opponentFriend.userId)
                    })
                )
                ResultRewardRow(state = state, outcome = outcome)
                ResultActions(
                    state = state,
                    outcome = outcome,
                    onPlayAgain = onPlayAgain ?: onRestart,
                    onRestart = onRestart,
                    onRematch = onRematch,
                    onPractice = onPractice,
                    onOpenTournament = onOpenTournament,
                    onShare = {
                        shareError = null
                        if (onShareResult != null) {
                            onShareResult(shareContent)
                        } else {
                            imageSharer.share(shareContent).onFailure {
                                shareError = localization.text(TextKey.ShareResultError)
                            }
                        }
                    },
                    onOpenSafety = { showSafetySheet = true },
                    shareError = shareError
                )
                // Chi tiết thêm (không có trong khung thiết kế chính): phân tích nhịp và nguồn thưởng sự kiện.
                PaceAnalysisCard(state.player)
                val breakdown = state.rewardBreakdowns[state.player.id]
                if (!state.isBotMatch && state.currentMatchId != null && breakdown != null &&
                    (breakdown.sources.isNotEmpty() || breakdown.bonusGold > 0 || breakdown.bonusXp > 0 || breakdown.clanBonusGold > 0)
                ) {
                    RewardBreakdownCard(breakdown, onOpenEvent = onOpenRewardEvent)
                }
            }
        }
        ArcadeHeaderIconButton(
            onClick = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 14.dp, top = 10.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = localized(TextKey.Back), tint = A1Palette.Ink)
        }
    }
}

/** 3 sao khi thắng chính xác ≥ 90%, 2 sao khi thắng; hòa 1–2 sao; thua 1 sao (rời trận 0 sao). */
private fun resultStars(outcome: ResultOutcome, accuracy: Int, forfeited: Boolean): Int = when (outcome) {
    ResultOutcome.WIN -> if (accuracy >= 90) 3 else 2
    ResultOutcome.DRAW -> if (accuracy >= 90) 2 else 1
    ResultOutcome.LOSS -> if (forfeited) 0 else 1
}

/** Nền màn kết quả: trời xanh có tia nắng xoay + pháo giấy khi thắng; trời xám xanh, mây và đồi nhạt khi thua. */
@Composable
private fun ResultBackdrop(outcome: ResultOutcome, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "resultBackdrop")
    val spin by if (animate && outcome != ResultOutcome.LOSS) {
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "rays")
    } else remember { mutableStateOf(0f) }
    val fall by if (animate && outcome == ResultOutcome.WIN) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4_500, easing = LinearEasing), RepeatMode.Restart), label = "confetti")
    } else remember { mutableStateOf(0.35f) }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (outcome == ResultOutcome.LOSS) {
            drawRect(Brush.verticalGradient(0f to Color(0xFF8FB7DC), 0.5f to Color(0xFFB6D3EC), 1f to Color(0xFFDCEBF7)))
            cloud(Offset(w * 0.62f, 200.dp.toPx()), 0.75f)
            val hillH = 260.dp.toPx()
            val hill = Rect(-50.dp.toPx(), h - hillH, w + 50.dp.toPx(), h + hillH)
            drawOval(Color(0xFFB3E6AB), hill.topLeft, hill.size)
            drawOval(Color(0xFF8FD08A), Offset(hill.left, hill.top + 10.dp.toPx()), hill.size)
            return@Canvas
        }
        drawRect(Brush.verticalGradient(0f to A1Palette.Sky1, 0.45f to A1Palette.Sky2, 1f to A1Palette.Sky3))
        rotate(spin, pivot = Offset(w / 2f, 170.dp.toPx())) {
            drawLightRays(Offset(w / 2f, 170.dp.toPx()), 350.dp.toPx())
        }
        // Đồi trước + đồi sau.
        val backH = 220.dp.toPx()
        drawOval(A1Palette.HillDark, Offset(150.dp.toPx(), h - backH), Size(w - 150.dp.toPx() + 90.dp.toPx(), backH * 2))
        val frontH = 280.dp.toPx()
        val front = Rect(-50.dp.toPx(), h - frontH, w + 50.dp.toPx(), h + frontH)
        drawOval(Color(0xFF9CF08F), front.topLeft, front.size)
        drawOval(A1Palette.Hill, Offset(front.left, front.top + 10.dp.toPx()), front.size)
        if (outcome == ResultOutcome.WIN) {
            val colors = listOf(Color(0xFFFFD23F), Color(0xFFFF4F5A), Color(0xFF3D8BFF), Color(0xFF3CC226), Color(0xFF7A4DFF), Color(0xFFFFD23F))
            val xs = listOf(30f, 90f, 160f, 230f, 300f, 350f)
            val delays = listOf(0f, 0.24f, 0.49f, 0.13f, 0.69f, 0.38f)
            colors.forEachIndexed { i, color ->
                val t = (fall + delays[i]) % 1f
                val x = xs[i] / 390f * w
                val y = -20.dp.toPx() + t * (h + 40.dp.toPx())
                rotate(t * 720f, pivot = Offset(x, y)) {
                    drawRoundRect(color, Offset(x - 4.5.dp.toPx(), y - 7.dp.toPx()), Size(9.dp.toPx(), 14.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.cloud(topLeft: Offset, scale: Float) {
    drawRoundRect(
        Color.White.copy(alpha = 0.95f),
        topLeft,
        Size(118.dp.toPx() * scale, 38.dp.toPx() * scale),
        CornerRadius(40.dp.toPx() * scale)
    )
}

/** Hàng 3 sao + linh vật Tia (vui khi thắng; buồn kèm bong bóng lời nhắn khi thua). */
@Composable
private fun ResultHero(outcome: ResultOutcome, stars: Int, bubbleText: String?) {
    val starsLabel = localized(TextKey.ResultStars, "count" to stars)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.semantics { contentDescription = starsLabel },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ResultStar(filled = stars >= 1, size = 46.dp)
            ResultStar(filled = stars >= 2, size = 64.dp)
            ResultStar(filled = stars >= 3, size = 46.dp)
        }
        val mood = when (outcome) {
            ResultOutcome.WIN -> TiaMood.CHEER
            ResultOutcome.DRAW -> TiaMood.HAPPY
            ResultOutcome.LOSS -> TiaMood.SAD
        }
        if (bubbleText == null) {
            TiaMascot(mood = mood, size = 120.dp)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TiaMascot(mood = mood, size = 110.dp)
                Text(
                    bubbleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = A1Palette.Ink,
                    modifier = Modifier
                        .weight(1f)
                        .a1Card(radius = 18.dp, depth = 4.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** Ngôi sao vẽ tay: vàng viền nâu khi đạt, xám xanh viền nhạt khi chưa đạt (".star"). */
@Composable
private fun ResultStar(filled: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f + r * 0.08f)
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r * 0.74f else r * 0.36f
            val angle = (-90.0 + i * 36.0) * kotlin.math.PI / 180.0
            val p = Offset(c.x + (radius * kotlin.math.cos(angle)).toFloat(), c.y + (radius * kotlin.math.sin(angle)).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        // Đầu cánh bo tròn như icon "star" bo góc của thiết kế: viền đậm vẽ trước, rồi phủ màu bằng nét dày bo tròn.
        val round = r * 0.16f
        drawPath(path, if (filled) Color(0xFF7A4D00) else Color(0xFF8AA6C8), style = Stroke(width = round * 2f + 6.dp.toPx(), join = StrokeJoin.Round))
        val fill = if (filled) Color(0xFFFFC928) else Color(0xFFE3ECF6)
        drawPath(path, fill, style = Stroke(width = round * 2f, join = StrokeJoin.Round))
        drawPath(path, fill)
    }
}

/** Dải tiêu đề kết quả: cao 58dp, dải màu, viền 3dp, đế 5dp, chữ Baloo 32sp trắng có bóng đậm. */
@Composable
private fun ResultRibbon(text: String, tone: RibbonTone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(bottom = 5.dp)
            .drawBehind {
                drawRoundRect(tone.edge, topLeft = Offset(0f, 5.dp.toPx()), size = size, cornerRadius = CornerRadius(12.dp.toPx()))
            }
            .background(Brush.verticalGradient(listOf(tone.top, tone.bottom)), RoundedCornerShape(12.dp))
            .border(3.dp, tone.edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 32.sp,
                lineHeight = 40.sp,
                shadow = Shadow(tone.edge, Offset(0f, 7f), 0f)
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** Thẻ tỉ số: avatar + tên + điểm hai bên, VS ở giữa, gạch đứt, 3 chỉ số và khối Elo (".card.pad"). */
@Composable
private fun ResultStatsCard(
    state: GameState,
    is2v2: Boolean,
    myScore: Int,
    opponentScore: Int,
    accuracy: Int,
    onOpponentInfo: (() -> Unit)?
) {
    val opponent = state.opponents.firstOrNull() ?: state.opponent
    val opponentAvatar = if (state.isBotMatch) opponent.copy(id = null) else opponent
    val myName = localized(if (is2v2) TextKey.YourTeam else TextKey.You)
    val opponentName = if (is2v2) localized(TextKey.OpponentTeam) else state.opponent.name
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .a1Card()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("result_stats"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tên nằm ngay dưới avatar; điểm to đặt giữa avatar và chữ VS.
            PlayerColumn(state.player, myName)
            ScoreText(myScore, A1Palette.Ink, TextAlign.Start, Modifier.weight(1f).padding(start = 10.dp))
            Text(
                text = "VS",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = A1Palette.Muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            ScoreText(opponentScore, A1Palette.RedText, TextAlign.End, Modifier.weight(1f).padding(end = 10.dp))
            PlayerColumn(
                opponentAvatar,
                opponentName,
                Modifier.then(if (onOpponentInfo == null) Modifier else Modifier.clickable(onClick = onOpponentInfo))
            )
        }
        // Gạch đứt phân cách + 3 chỉ số.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        A1Palette.SurfaceAlt2,
                        Offset(0f, 0f),
                        Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                }
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatPair(localized(TextKey.Time), formatDuration(state.lastMatchDurationMillis))
            StatPair(localized(TextKey.Accuracy), "$accuracy%")
            if (state.gameMode == GameMode.COMBO) {
                StatPair(localized(TextKey.ComboLabel), "x${state.player.combo}")
            } else {
                StatPair(localized(TextKey.AverageReaction), formatReaction(state.player.averageReactionMillis))
            }
        }
        ResultEloBlock(state)
    }
}

@Composable
private fun PlayerColumn(player: PlayerState, name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.widthIn(max = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PlayerAvatar(player.name, player.avatarId, player.id, player.frameId, size = 64.dp)
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
            color = A1Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScoreText(score: Int, color: Color, align: TextAlign, modifier: Modifier = Modifier) {
    Text(
        score.toString(),
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 40.sp,
            lineHeight = 40.sp,
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
            )
        ),
        color = color,
        maxLines = 1,
        textAlign = align,
        modifier = modifier
    )
}

/** "Nhãn giá trị" nhỏ, đậm, màu muted (hàng Thời gian · Chính xác · …). */
@Composable
private fun StatPair(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.Bold, color = A1Palette.Muted)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.Bold, color = A1Palette.Muted)
    }
}

/** Khối Elo: "Elo 1760 +18" + nhãn hạng + thanh tiến độ + "Còn X Elo để lên …". Trận thường/bot chỉ ghi chú ngắn. */
@Composable
private fun ResultEloBlock(state: GameState) {
    val note = when {
        state.isBotMatch -> localized(TextKey.BotMatchRules)
        state.matchType != MatchType.RANKED -> localized(TextKey.CasualNoEloImpact)
        else -> null
    }
    if (note != null) {
        Text(note, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
        return
    }
    val change = state.lastMatchEloChange ?: return
    val rating = state.lastMatchEloRating
    val season = state.profile?.progression?.season
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(if (rating != null) "Elo $rating " else "Elo ")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = if (change >= 0) A1Palette.GreenText else A1Palette.RedText)) {
                        append(if (change >= 0) "+$change" else "−${-change}")
                    }
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = A1Palette.Ink,
                modifier = Modifier.weight(1f)
            )
            if (rating != null) A1Tag(localizedRankedTierName(rankedTierFor(rating)), A1TagTone.YELLOW)
        }
        if (rating != null) {
            val placement = season != null && season.placementMatchesPlayed < season.placementMatchesRequired
            val progress = if (placement) {
                season!!.placementMatchesPlayed.toFloat() / season.placementMatchesRequired.coerceAtLeast(1)
            } else ratingProgressWithinTier(rating)
            ResultProgressBar(progress.coerceIn(0f, 1f))
            val next = nextRankedTier(rating)
            val caption = when {
                placement -> localized(TextKey.PlacementProgress, "played" to season!!.placementMatchesPlayed, "required" to season.placementMatchesRequired)
                next == null -> localized(TextKey.HighestTierReached)
                change >= 0 -> localized(TextKey.EloToNextTier, "count" to (next.minimumRating - rating).coerceAtLeast(0), "tier" to localizedRankedTierName(next))
                else -> null
            }
            caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
            }
        }
    }
}

/** Thanh tiến độ ".bar": cao 14dp, viền 2dp navy, nền xanh nhạt, phần đầy vàng có vệt sáng. */
@Composable
private fun ResultProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(A1Palette.SurfaceAlt2, RoundedCornerShape(9.dp))
            .border(2.dp, A1Palette.Line, RoundedCornerShape(9.dp))
            .padding(2.dp)
            .drawBehind {
                val w = size.width * progress
                if (w > 0f) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFFB800))),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(7.dp.toPx())
                    )
                    drawRect(Color.White.copy(alpha = 0.4f), size = Size(w, 3.dp.toPx().coerceAtMost(size.height)))
                }
            }
    )
}

/** Hàng ô thưởng: vàng, XP và nhiệm vụ ngày (thắng) hoặc mẹo chơi (thua). */
@Composable
private fun ResultRewardRow(state: GameState, outcome: ResultOutcome) {
    val showRewards = !state.isBotMatch && state.profile != null
    val breakdown = state.rewardBreakdowns[state.player.id]
    val gold = breakdown?.totalGold ?: when (outcome) {
        ResultOutcome.DRAW -> MATCH_DRAW_REWARD_GOLD
        ResultOutcome.WIN -> MATCH_WIN_REWARD_GOLD
        ResultOutcome.LOSS -> MATCH_LOSS_REWARD_GOLD
    }
    val xp = breakdown?.totalXp ?: when (outcome) {
        ResultOutcome.DRAW -> MATCH_DRAW_REWARD_XP
        ResultOutcome.WIN -> MATCH_WIN_REWARD_XP
        ResultOutcome.LOSS -> MATCH_LOSS_REWARD_XP
    }
    val missions = state.profile?.progression?.dailyMissions.orEmpty()
    val showTip = outcome == ResultOutcome.LOSS
    if (!showRewards && !showTip) return
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showRewards) {
            RewardTile(
                icon = { A1CoinIcon(size = if (showTip) 30.dp else 32.dp) },
                value = "+$gold",
                label = localized(TextKey.Gold),
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("result_reward_gold")
            )
            RewardTile(
                icon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = A1Palette.Blue2, modifier = Modifier.size(if (showTip) 32.dp else 34.dp)) },
                value = "+$xp",
                label = localized(TextKey.ResultXp),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        if (showTip) {
            val tipKey = listOf(TextKey.ResultTip1, TextKey.ResultTip2, TextKey.ResultTip3)[
                ((state.currentMatchId ?: state.currentRoomId)?.hashCode() ?: 0).mod(3)
            ]
            Column(
                modifier = Modifier
                    .weight(if (showRewards) 2f else 1f)
                    .fillMaxHeight()
                    .a1Card(radius = 18.dp, depth = A1Dimens.SoftDepth)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = A1Palette.Blue2, modifier = Modifier.size(26.dp))
                Text(localized(tipKey), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.SemiBold, color = A1Palette.Ink)
            }
        } else if (missions.isNotEmpty()) {
            RewardTile(
                icon = { Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = A1Palette.Green2, modifier = Modifier.size(34.dp)) },
                value = "${missions.count { it.completed }}/${missions.size}",
                label = localized(TextKey.MissionsTitle),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun RewardTile(icon: @Composable () -> Unit, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .a1Card(radius = 18.dp, depth = A1Dimens.SoftDepth)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        icon()
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = A1Palette.Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.Bold, color = A1Palette.Muted, maxLines = 1)
    }
}

/** Các nút: CTA vàng lớn, hàng Tái đấu / Về sảnh, hàng nhỏ Khoe kết quả / Báo cáo – chặn. */
@Composable
private fun ResultActions(
    state: GameState,
    outcome: ResultOutcome,
    onPlayAgain: () -> Unit,
    onRestart: () -> Unit,
    onRematch: () -> Unit,
    onPractice: (() -> Unit)?,
    onOpenTournament: () -> Unit,
    onShare: () -> Unit,
    onOpenSafety: () -> Unit,
    shareError: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.isTournamentMatch) {
            ArcadeActionButton(
                label = localized(TextKey.ViewBracket).uppercase(),
                onClick = onOpenTournament,
                style = ArcadeActionStyle.GOLD,
                large = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag("open_tournament_bracket")
            )
        } else {
            ArcadeActionButton(
                label = localized(if (outcome == ResultOutcome.LOSS) TextKey.ResultTryAgain else TextKey.ResultPlayAgain),
                onClick = onPlayAgain,
                style = ArcadeActionStyle.GOLD,
                hero = outcome == ResultOutcome.WIN,
                large = outcome != ResultOutcome.WIN,
                enabled = LocalNewOnlineIntentAllowed.current,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (outcome == ResultOutcome.WIN) 72.dp else 62.dp)
                    .testTag("result_play_again")
            )
        }
        val showRematch = !state.isTournamentMatch && !state.isBotMatch && state.matchType != MatchType.RANKED
        // Thua: hàng "Luyện tập + Về sảnh" như thiết kế; tái đấu (nếu được) nằm riêng phía trên.
        val showPractice = outcome == ResultOutcome.LOSS && onPractice != null && !state.isTournamentMatch
        if (showPractice && showRematch) RematchButton(state, onRematch, Modifier.fillMaxWidth())
        if (showRematch || !state.isTournamentMatch) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (showPractice) {
                    ArcadeActionButton(
                        label = localized(TextKey.ModeTabPractice),
                        onClick = { onPractice?.invoke() },
                        icon = Icons.Rounded.SportsEsports,
                        style = ArcadeActionStyle.PRIMARY,
                        modifier = Modifier.weight(1f).testTag("result_practice")
                    )
                } else if (showRematch) RematchButton(state, onRematch, Modifier.weight(1f))
                if (!state.isTournamentMatch) {
                    ArcadeActionButton(
                        label = localized(TextKey.ReturnToLobby),
                        onClick = onRestart,
                        icon = Icons.Rounded.Home,
                        style = ArcadeActionStyle.OUTLINE,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (showRematch) {
            val remainingSeconds = state.rematchExpiresAtEpochMillis?.let { expiresAt ->
                val now = rememberClockMillis(state.rematchServerClock, tickMillis = 250, deadlineMillis = expiresAt)
                ceil((expiresAt - now).coerceAtLeast(0L) / 1_000.0).toInt()
            }
            state.rematchNotice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = A1Palette.Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if ((state.isRematchRequestedByMe || state.isRematchRequestedByOpponent) && remainingSeconds != null) {
                Text(
                    localized(TextKey.ResponseSeconds, "seconds" to remainingSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = A1Palette.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ArcadeActionButton(
                label = localized(TextKey.ShareResultShort),
                onClick = onShare,
                icon = Icons.Rounded.Share,
                iconTint = A1Palette.Blue2,
                style = ArcadeActionStyle.OUTLINE,
                compact = true,
                modifier = Modifier.weight(1f).testTag("share_result")
            )
            if (!state.isBotMatch && state.profile != null) {
                ArcadeActionButton(
                    label = localized(TextKey.ReportOrBlock),
                    onClick = onOpenSafety,
                    icon = Icons.Rounded.Flag,
                    iconTint = Color(0xFFC97A00),
                    style = ArcadeActionStyle.OUTLINE,
                    compact = true,
                    modifier = Modifier.weight(1f).testTag("result_report_block")
                )
            }
        }
        shareError?.let {
            Text(it, color = A1Palette.RedText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RematchButton(state: GameState, onRematch: () -> Unit, modifier: Modifier) {
    val tagged = modifier.testTag("result_rematch_action")
    when {
        !state.hasOpponent -> ArcadeActionButton(
            label = localized(TextKey.OpponentLeft), onClick = {}, modifier = tagged, enabled = false
        )
        state.isRematchRequestedByOpponent -> ArcadeActionButton(
            label = localized(if (state.isRematchActionPending) TextKey.Processing else TextKey.WaitingForResponse),
            onClick = {}, modifier = tagged, enabled = false
        )
        state.isRematchRequestedByMe -> ArcadeActionButton(
            label = localized(TextKey.Invited), onClick = {}, icon = Icons.Rounded.Replay, modifier = tagged, enabled = false
        )
        else -> ArcadeActionButton(
            label = localized(if (state.isRematchActionPending) TextKey.SendingShort else TextKey.RematchShort),
            onClick = onRematch,
            icon = Icons.Rounded.Replay,
            style = ArcadeActionStyle.PRIMARY,
            modifier = tagged,
            enabled = LocalNewOnlineIntentAllowed.current && !state.isRematchActionPending
        )
    }
}

/** Bảng "Báo cáo / chặn": kết bạn, báo cáo người chơi và chặn (giữ đủ luồng an toàn bắt buộc). */
@Composable
private fun OpponentSafetySheet(state: GameState, onConnect: () -> Unit, onBlock: () -> Unit, onDismiss: () -> Unit) {
    val friendLabel = when (state.postMatchFriendStatus) {
        PostMatchFriendStatus.AVAILABLE -> localized(TextKey.AddFriend)
        PostMatchFriendStatus.REQUEST_RECEIVED -> localized(TextKey.AcceptFriend)
        PostMatchFriendStatus.REQUEST_SENT -> localized(TextKey.FriendRequestSent)
        PostMatchFriendStatus.FRIEND -> localized(TextKey.AlreadyFriends)
        PostMatchFriendStatus.BLOCKED -> localized(TextKey.Blocked)
        PostMatchFriendStatus.UNAVAILABLE -> localized(TextKey.LoadingInfo)
    }
    val canConnect = state.postMatchFriendStatus in setOf(PostMatchFriendStatus.AVAILABLE, PostMatchFriendStatus.REQUEST_RECEIVED)
    val report = rememberUgcReport(UgcTargetType.PLAYER_PROFILE, state.opponent.id, state.opponent.name)
    A1BottomSheet(title = state.opponent.name, onDismissRequest = onDismiss) {
        ArcadeActionButton(
            label = friendLabel,
            onClick = onConnect,
            enabled = canConnect,
            icon = Icons.Rounded.GroupAdd,
            style = ArcadeActionStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )
        if (report != null) {
            ArcadeActionButton(
                label = localized(TextKey.UgcReportPlayer),
                onClick = {
                    onDismiss()
                    report()
                },
                icon = Icons.Rounded.Report,
                style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth().testTag("report:PLAYER_PROFILE:${state.opponent.id}")
            )
        }
        ArcadeActionButton(
            label = localized(TextKey.Block),
            onClick = onBlock,
            enabled = state.postMatchFriendStatus != PostMatchFriendStatus.BLOCKED,
            icon = Icons.Rounded.Block,
            style = ArcadeActionStyle.DANGER,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Phân tích nhịp chơi (đoạn nhanh nhất / chậm nhất), dạng thẻ A1. */
@Composable
private fun PaceAnalysisCard(player: PlayerState) {
    val hasAnalysis = player.fastestSegmentAverageMillis > 0L || player.slowestSegmentAverageMillis > 0L
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .a1Card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(localized(TextKey.PaceAnalysis), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = A1Palette.Ink)
        if (!hasAnalysis) {
            Text(localized(TextKey.InsufficientPaceAnalysis), style = MaterialTheme.typography.bodyMedium, color = A1Palette.Muted)
        } else {
            Text(localized(TextKey.PaceSegmentHint), style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
            SegmentRow(
                label = localized(TextKey.Fastest),
                start = player.fastestSegmentStart,
                end = player.fastestSegmentEnd,
                averageMillis = player.fastestSegmentAverageMillis,
                highlight = true
            )
            if (player.slowestSegmentStart != player.fastestSegmentStart || player.slowestSegmentEnd != player.fastestSegmentEnd) {
                SegmentRow(
                    label = localized(TextKey.Slowest),
                    start = player.slowestSegmentStart,
                    end = player.slowestSegmentEnd,
                    averageMillis = player.slowestSegmentAverageMillis,
                    highlight = false
                )
            }
        }
    }
}

@Composable
private fun SegmentRow(label: String, start: Int, end: Int, averageMillis: Long, highlight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlight) Color(0xFFEEF5FF) else A1Palette.SurfaceAlt, RoundedCornerShape(14.dp))
            .border(2.dp, if (highlight) A1Palette.Blue2 else A1Palette.SurfaceAlt2, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = A1Palette.Ink)
            Text(
                localized(TextKey.TurnRange, "start" to start, "end" to end),
                style = MaterialTheme.typography.bodySmall,
                color = A1Palette.Muted
            )
        }
        Text(formatReaction(averageMillis), style = MaterialTheme.typography.titleMedium, color = if (highlight) A1Palette.Blue2 else A1Palette.Ink)
    }
}

private fun formatDuration(durationMillis: Long?): String {
    if (durationMillis == null) return "--"
    val totalSeconds = durationMillis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
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
    is2v2: Boolean,
    localization: com.hienthai.fastowin.localization.LocalizationService
): ResultShareContent {
    val teamPlayers = if (is2v2) listOf(state.player) + state.teammates else listOf(state.player)
    val correctSelections = teamPlayers.sumOf { it.correctSelections }
    val attempts = teamPlayers.sumOf { it.correctSelections + it.wrongSelections }
    val accuracy = if (attempts == 0) 0 else correctSelections * 100 / attempts
    return ResultShareContent(
        result = localization.text(if (isDraw) TextKey.DrawResult else if (isWinner) TextKey.VictoryResult else TextKey.DefeatResult),
        playerName = if (is2v2) localization.text(TextKey.YourTeam) else state.player.name,
        playerScore = playerScore,
        opponentName = if (is2v2) localization.text(TextKey.OpponentTeam) else state.opponent.name,
        opponentScore = opponentScore,
        gameMode = localization.text(state.gameMode.titleKey),
        matchType = localization.text(state.matchTypeLabelKey),
        duration = formatDuration(state.lastMatchDurationMillis),
        accuracy = "$accuracy%",
        elo = state.lastMatchEloChange?.takeIf { state.matchType == MatchType.RANKED }?.let {
            if (it >= 0) "+$it Elo" else "$it Elo"
        },
        caption = localization.text(TextKey.ShareResultCaption, mapOf("player" to (if (is2v2) localization.text(TextKey.YourTeam) else state.player.name), "playerScore" to playerScore, "opponentScore" to opponentScore, "opponent" to (if (is2v2) localization.text(TextKey.OpponentTeam) else state.opponent.name), "mode" to localization.text(state.gameMode.titleKey))),
        timeLabel = localization.text(TextKey.ShareTimeLabel),
        accuracyLabel = localization.text(TextKey.ShareAccuracyLabel),
        slogan = localization.text(TextKey.ShareSlogan),
        shareSheetTitle = localization.text(TextKey.ShareResultSheetTitle)
    )
}

@Composable
fun ResultScreenPreview() {
    FastToWinTheme {
        ResultScreen(
            state = GameState(
                isGameOver = true,
                player = PlayerState("Hien", score = 320, correctSelections = 32, wrongSelections = 2, averageReactionMillis = 640),
                opponent = PlayerState("Hieu", score = 180, correctSelections = 18, wrongSelections = 4),
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
