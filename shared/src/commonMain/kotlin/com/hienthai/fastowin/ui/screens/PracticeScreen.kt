package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.GameFeedbackEffect
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.platform.buildChallengeDeepLink
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.platform.rememberTextSharer
import com.hienthai.fastowin.state.GAME_NUMBER_COUNT
import com.hienthai.fastowin.state.PracticeChallenge
import com.hienthai.fastowin.state.PracticeGameState
import com.hienthai.fastowin.state.createPracticeChallenge
import com.hienthai.fastowin.state.createPracticeGame
import com.hienthai.fastowin.state.parsePracticeChallenge
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_leaderboard_trophy
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay

@Composable
fun PracticeScreen(
    mode: GameMode,
    challenge: PracticeChallenge? = null,
    preferences: AppPreferences,
    onBack: () -> Unit,
    onShareChallenge: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)
    var currentChallengeCode by rememberSaveable(mode.name, challenge?.code) {
        mutableStateOf(
            (challenge?.takeIf { it.mode == mode } ?: createPracticeChallenge(mode, challengeSeed())).code
        )
    }
    val currentChallenge = remember(mode, currentChallengeCode, challenge?.code) {
        parsePracticeChallenge(currentChallengeCode)
            ?.takeIf { it.mode == mode }
            ?: challenge?.takeIf { it.mode == mode }
            ?: createPracticeChallenge(mode, challengeSeed())
    }
    val gameStateSaver = remember(currentChallenge.code) {
        practiceGameMutableStateSaver(currentChallenge)
    }
    var game by rememberSaveable(mode.name, currentChallenge.code, saver = gameStateSaver) {
        mutableStateOf(createPracticeGame(mode, epochMillis(), challenge = currentChallenge))
    }
    var wrongNumber by remember(mode, currentChallenge.code) { mutableStateOf<Int?>(null) }
    var wrongFeedbackToken by remember(mode, currentChallenge.code) { mutableIntStateOf(0) }
    var accessibilityFeedback by remember(mode, currentChallenge.code) { mutableStateOf<String?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(currentChallenge.code, currentChallengeCode) {
        if (currentChallengeCode != currentChallenge.code) {
            currentChallengeCode = currentChallenge.code
        }
    }

    LaunchedEffect(game.isComplete, game.startedAtMillis) {
        while (!game.isComplete) {
            delay(100)
            game = game.tick(epochMillis())
        }
    }
    LaunchedEffect(game.isComplete) {
        if (game.isComplete && preferences.soundEnabled) {
            playFeedbackSound(GameFeedbackEffect.WIN)
        }
    }
    LaunchedEffect(wrongFeedbackToken) {
        if (wrongFeedbackToken > 0) {
            delay(180)
            wrongNumber = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compactLandscape = maxHeight < 430.dp && maxWidth > maxHeight
        Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            FastToWinHeader(
                title = "Luyện tập · ${mode.title}",
                subtitle = "Ngoại tuyến • Không ảnh hưởng Elo",
                gold = 0,
                gems = 0,
                unreadNotifications = 0,
                onNotifications = {},
                onBack = onBack,
                showBalances = false,
                showNotifications = false
            )
        },
        bottomBar = {
            if (!game.isComplete && !compactLandscape) {
                PracticeExitBar(onBack = onBack)
            }
        }
    ) { paddingValues ->
        ResponsiveScreen(
            modifier = Modifier.padding(paddingValues),
            maxContentWidth = 760.dp,
            applySafeDrawingInsets = false
        ) { contentModifier ->
            if (game.isComplete) {
                PracticeResult(
                    game = game,
                    onRestart = {
                        game = createPracticeGame(mode, epochMillis(), challenge = currentChallenge)
                    },
                    onNewChallenge = {
                        currentChallengeCode = createPracticeChallenge(mode, challengeSeed()).code
                    },
                    onShareChallenge = onShareChallenge,
                    onBack = onBack,
                    modifier = contentModifier
                )
            } else {
                Column(modifier = contentModifier) {
                    PracticeStatus(game, compact = compactLandscape)
                    NumberGrid(
                        numbers = game.numbers,
                        selectedNumbers = game.selectedNumbers,
                        wrongNumber = wrongNumber,
                        enabled = true,
                        boardStyle = preferences.boardStyle,
                        onNumberClick = { number ->
                            val correct = number == game.currentTarget
                            val effect = if (correct) GameFeedbackEffect.CORRECT else GameFeedbackEffect.WRONG
                            accessibilityFeedback = if (correct) {
                                if (game.correctSelections + 1 >= GAME_NUMBER_COUNT) {
                                    "Đúng, đã hoàn thành bàn số"
                                } else {
                                    "Đúng, mục tiêu tiếp theo ${game.currentTarget + 1}"
                                }
                            } else {
                                "Sai, cần tìm ${game.currentTarget}"
                            }
                            wrongNumber = if (!correct && preferences.visualEffectsEnabled) number else null
                            if (!correct && preferences.visualEffectsEnabled) wrongFeedbackToken += 1
                            if (preferences.soundEnabled) playFeedbackSound(effect)
                            if (preferences.vibrationEnabled) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            game = game.select(number, epochMillis())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                accessibilityFeedback?.let { stateDescription = it }
                            }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun PracticeStatus(game: PracticeGameState, compact: Boolean) {
    val isCountdownMode = game.mode in setOf(GameMode.TIME_ATTACK, GameMode.TIME_BONUS, GameMode.SPEED_UP)
    if (compact) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = ArcadePalette.Navy800,
            border = BorderStroke(1.dp, ArcadePalette.Violet400.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PracticeCompactMetric(
                    "Mục tiêu",
                    game.currentTarget.coerceAtMost(GAME_NUMBER_COUNT).toString(),
                    Modifier.weight(1f)
                )
                PracticeCompactMetric("Điểm", game.score.toString(), Modifier.weight(1f))
                PracticeCompactMetric(
                    if (isCountdownMode) "Còn lại" else "Thời gian",
                    formatPracticeTime(if (isCountdownMode) game.timeLeftMillis else game.elapsedMillis),
                    Modifier.weight(1f)
                )
                PracticeCompactMetric(
                    if (game.mode == GameMode.SURVIVAL) "Mạng" else "Combo",
                    if (game.mode == GameMode.SURVIVAL) game.lives.toString() else "x${game.combo}",
                    Modifier.weight(1f)
                )
            }
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color(0xFF9FB8FF).copy(alpha = 0.7f)),
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF643BD1), Color(0xFF3D72EF))),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "MỤC TIÊU",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Black
                )
                Text(
                    game.currentTarget.coerceAtMost(GAME_NUMBER_COUNT).toString(),
                    modifier = Modifier.testTag("practice_target"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    lineHeight = 40.sp
                )
                when (game.mode) {
                    GameMode.SURVIVAL -> "${game.lives} mạng"
                    GameMode.COMBO -> "Combo x${game.combo}"
                    GameMode.SPEED_UP -> "Nhịp ${game.correctSelections + 1}/$GAME_NUMBER_COUNT"
                    else -> null
                }?.let { supporting ->
                    Text(
                        supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.74f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PracticeMetric(
                if (isCountdownMode) "Còn lại" else "Thời gian",
                formatPracticeTime(if (isCountdownMode) game.timeLeftMillis else game.elapsedMillis),
                Modifier.weight(1f)
            )
            PracticeMetric(
                "Điểm",
                game.score.toString(),
                Modifier.weight(1f),
                valueTestTag = "practice_score"
            )
            PracticeMetric("Chính xác", "${game.accuracyPercent}%", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PracticeCompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun PracticeMetric(
    label: String,
    value: String,
    modifier: Modifier,
    valueTestTag: String? = null
) {
    Surface(
        modifier = modifier.heightIn(min = 58.dp),
        shape = RoundedCornerShape(16.dp),
            color = ArcadePalette.Navy800,
            border = BorderStroke(1.dp, ArcadePalette.OutlineDark.copy(alpha = 0.6f))
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                modifier = valueTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PracticeExitBar(onBack: () -> Unit) {
    Surface(color = ArcadePalette.Navy900.copy(alpha = 0.98f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ArcadeActionButton(
                label = "KẾT THÚC",
                onClick = onBack,
                style = ArcadeActionStyle.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PracticeResult(
    game: PracticeGameState,
    onRestart: () -> Unit,
    onNewChallenge: () -> Unit,
    onShareChallenge: ((String) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textSharer = rememberTextSharer()
    var shareError by remember(game.challengeCode) { mutableStateOf<String?>(null) }
    val challengeText = game.challengeCode?.let {
        buildChallengeShareText(game.mode, it, game.score, game.elapsedMillis)
    }
    val completionMessage = when {
        game.correctSelections >= GAME_NUMBER_COUNT -> "Bạn đã tìm đủ 50 số"
        game.mode == GameMode.SURVIVAL -> "Bạn đã hết 3 lượt bấm sai"
        game.mode == GameMode.SPEED_UP -> "Bạn không kịp tìm mục tiêu tiếp theo"
        game.mode == GameMode.TIME_BONUS -> "Bạn đã hết thời gian tích lũy"
        game.mode == GameMode.TIME_ATTACK -> "Hết 60 giây"
        else -> "Thử thách đã kết thúc"
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ArcadeFeatureHero(
            illustration = Res.drawable.arcade_leaderboard_trophy,
            title = "HOÀN THÀNH",
            subtitle = "$completionMessage. ${game.score} điểm · ${game.mode.title}.",
            accent = ArcadePalette.Gold500
        )
        PracticeResultMetrics(game)
        PracticeAnalysisCard(game)
        game.challengeCode?.let { code ->
            ArcadePanel(accent = MaterialTheme.colorScheme.secondary) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Mã thử thách", style = MaterialTheme.typography.labelMedium)
                    Text(code, fontWeight = FontWeight.Black, modifier = Modifier.testTag("challenge_code"))
                    Text(
                        "Bạn bè nhập mã này để chơi đúng cùng một bàn số.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Text(
            "Kết quả luyện tập không ảnh hưởng Elo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (challengeText != null) {
            ArcadeActionButton(
                label = "CHIA SẺ THỬ THÁCH",
                onClick = {
                    shareError = null
                    if (onShareChallenge != null) {
                        onShareChallenge(challengeText)
                    } else {
                        textSharer.share(challengeText, "Chia sẻ thử thách").onFailure {
                            shareError = "Không thể mở bảng chia sẻ. Vui lòng thử lại."
                        }
                    }
                },
                style = ArcadeActionStyle.OUTLINE,
                icon = Icons.Rounded.Share,
                modifier = Modifier.fillMaxWidth().testTag("share_challenge")
            )
        }
        shareError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        ArcadeActionButton(
            label = "CHƠI LẠI CÙNG BÀN",
            onClick = onRestart,
            style = ArcadeActionStyle.GOLD,
            icon = Icons.Rounded.RestartAlt,
            modifier = Modifier.fillMaxWidth()
        )
        ArcadeActionButton(
            label = "TẠO THỬ THÁCH MỚI",
            onClick = onNewChallenge,
            style = ArcadeActionStyle.PRIMARY,
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth()
        )
        ArcadeActionButton(
            label = "VỀ TRANG CHỦ",
            onClick = onBack,
            style = ArcadeActionStyle.OUTLINE,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PracticeResultMetrics(game: PracticeGameState) {
    val averageReaction = if (game.correctSelections == 0) 0L else game.elapsedMillis / game.correctSelections
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackMetrics = maxWidth < 330.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.35f
        if (stackMetrics) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeResultMetric("Điểm", game.score.toString(), Modifier.fillMaxWidth())
                PracticeResultMetric("Chính xác", "${game.accuracyPercent}%", Modifier.fillMaxWidth())
                PracticeResultMetric("Phản xạ", formatPracticeReaction(averageReaction), Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeResultMetric("Điểm", game.score.toString(), Modifier.weight(1f))
                PracticeResultMetric("Chính xác", "${game.accuracyPercent}%", Modifier.weight(1f))
                PracticeResultMetric("Phản xạ", formatPracticeReaction(averageReaction), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PracticeResultMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(18.dp),
        color = ArcadePalette.Navy800,
        border = BorderStroke(1.dp, ArcadePalette.Blue300.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
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
private fun PracticeAnalysisCard(game: PracticeGameState) {
    val averageReaction = if (game.correctSelections == 0) 0L else game.elapsedMillis / game.correctSelections
    ArcadePanel(accent = MaterialTheme.colorScheme.primary) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Nhịp độ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            PracticeAnalysisRow("Đúng / Sai", "${game.correctSelections} / ${game.wrongSelections}")
            PracticeAnalysisRow("Thời gian", formatPracticeTime(game.elapsedMillis))
            PracticeAnalysisRow("Trung bình mỗi số", formatPracticeReaction(averageReaction))
        }
    }
}

@Composable
private fun PracticeAnalysisRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PracticeLauncherDialog(
    onDismiss: () -> Unit,
    onStartNew: () -> Unit,
    onOpenChallenge: (PracticeChallenge) -> Unit,
    playerLevel: Int = 1
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ArcadeDialog(
        title = "LUYỆN TẬP OFFLINE",
        subtitle = "Rèn phản xạ mỗi ngày mà không ảnh hưởng Elo.",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
                ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Mint600) {
                    Text(
                        "Không ảnh hưởng Elo và không cần kết nối máy chủ.",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                ArcadeActionButton(
                    label = "Bắt đầu luyện tập mới",
                    onClick = onStartNew,
                    icon = Icons.Rounded.FitnessCenter,
                    modifier = Modifier.fillMaxWidth().testTag("practice_new"),
                    style = ArcadeActionStyle.GOLD
                )
                HorizontalDivider()
                Text("Có mã thử thách?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase().take(20)
                        error = null
                    },
                    label = { Text("Mã thử thách") },
                    placeholder = { Text("FTW-CL-12345678-AB") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { message -> ({ Text(message) }) },
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().testTag("challenge_input")
                )
                ArcadeActionButton(
                    label = "Chơi thử thách",
                    onClick = {
                        val challenge = parsePracticeChallenge(code)
                        when {
                            challenge == null -> error = "Mã không hợp lệ hoặc đã nhập sai."
                            playerLevel < challenge.mode.unlockLevel ->
                                error = "Chế độ ${challenge.mode.title} mở khóa ở cấp ${challenge.mode.unlockLevel}."
                            else -> onOpenChallenge(challenge)
                        }
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("challenge_open"),
                    style = ArcadeActionStyle.PRIMARY
                )
        }
        ArcadeActionButton(
            label = "Đóng",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = ArcadeActionStyle.OUTLINE
        )
    }
}

internal fun buildChallengeShareText(mode: GameMode, code: String, score: Int, elapsedMillis: Long): String =
    """
    Thử thách Fast To Win • ${mode.title}
    Mình đạt $score điểm trong ${formatPracticeTime(elapsedMillis)}.
    Mở trực tiếp: ${buildChallengeDeepLink(code)}
    Mã thử thách: $code
    Nếu liên kết không mở, vào Luyện tập offline và nhập mã để chơi cùng bàn số.
    """.trimIndent()

private fun practiceGameMutableStateSaver(
    challenge: PracticeChallenge
): Saver<MutableState<PracticeGameState>, String> = Saver(
    save = { state -> encodePracticeGameState(state.value) },
    restore = { encoded ->
        decodePracticeGameState(encoded, challenge)
            ?.tick(epochMillis())
            ?.let(::mutableStateOf)
    }
)

/**
 * Save only mutable progress. The challenge code restores the immutable board and target order,
 * keeping the SaveableStateRegistry payload small and portable between Android and iOS.
 */
internal fun encodePracticeGameState(state: PracticeGameState): String = listOf(
    PRACTICE_STATE_VERSION,
    state.mode.name,
    state.challengeCode.orEmpty(),
    state.currentTarget.toString(),
    state.score.toString(),
    state.correctSelections.toString(),
    state.wrongSelections.toString(),
    state.startedAtMillis.toString(),
    state.nowMillis.toString(),
    if (state.isComplete) "1" else "0",
    state.targetIndex.toString(),
    state.selectedNumbers.joinToString(","),
    state.combo.toString(),
    state.lives.toString(),
    state.timeAdjustmentMillis.toString(),
    state.targetStartedAtMillis.toString()
).joinToString("|")

internal fun decodePracticeGameState(
    encoded: String,
    challenge: PracticeChallenge
): PracticeGameState? {
    val fields = encoded.split('|')
    if (fields.size != PRACTICE_STATE_FIELD_COUNT || fields[0] != PRACTICE_STATE_VERSION) return null
    val mode = GameMode.entries.firstOrNull { it.name == fields[1] } ?: return null
    if (mode != challenge.mode) return null
    if (fields[2] != challenge.code) return null
    val currentTarget = fields[3].toIntOrNull() ?: return null
    val score = fields[4].toIntOrNull() ?: return null
    val correctSelections = fields[5].toIntOrNull() ?: return null
    val wrongSelections = fields[6].toIntOrNull() ?: return null
    val startedAtMillis = fields[7].toLongOrNull() ?: return null
    val nowMillis = fields[8].toLongOrNull() ?: return null
    val isComplete = when (fields[9]) {
        "0" -> false
        "1" -> true
        else -> return null
    }
    val targetIndex = fields[10].toIntOrNull() ?: return null
    val selectedNumbers = if (fields[11].isEmpty()) {
        emptyList()
    } else {
        fields[11].split(',').map { it.toIntOrNull() ?: return null }
    }
    val combo = fields[12].toIntOrNull() ?: return null
    val lives = fields[13].toIntOrNull() ?: return null
    val timeAdjustmentMillis = fields[14].toLongOrNull() ?: return null
    val targetStartedAtMillis = fields[15].toLongOrNull() ?: return null

    if (targetIndex !in 0..GAME_NUMBER_COUNT) return null
    if (correctSelections != targetIndex || selectedNumbers != challenge.targetOrder.take(targetIndex)) return null
    if (currentTarget != (challenge.targetOrder.getOrNull(targetIndex) ?: GAME_NUMBER_COUNT + 1)) return null
    if (score < 0 || wrongSelections < 0 || combo !in 0..correctSelections || lives !in 0..3) return null
    if (nowMillis < startedAtMillis || targetStartedAtMillis !in startedAtMillis..nowMillis) return null
    if (!isComplete && targetIndex == GAME_NUMBER_COUNT) return null

    return PracticeGameState(
        mode = mode,
        numbers = challenge.numbers,
        currentTarget = currentTarget,
        score = score,
        correctSelections = correctSelections,
        wrongSelections = wrongSelections,
        startedAtMillis = startedAtMillis,
        nowMillis = nowMillis,
        isComplete = isComplete,
        targetOrder = challenge.targetOrder,
        targetIndex = targetIndex,
        selectedNumbers = selectedNumbers,
        combo = combo,
        lives = lives,
        timeAdjustmentMillis = timeAdjustmentMillis,
        targetStartedAtMillis = targetStartedAtMillis,
        challengeCode = challenge.code
    )
}

private fun challengeSeed(): Int {
    val now = epochMillis()
    return (now xor (now ushr 32)).toInt()
}

private fun formatPracticeTime(millis: Long): String {
    val totalTenths = millis.coerceAtLeast(0L) / 100
    val minutes = totalTenths / 600
    val seconds = totalTenths % 600 / 10
    val tenths = totalTenths % 10
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.$tenths"
}

private fun formatPracticeReaction(millis: Long): String = when {
    millis <= 0L -> "--"
    millis < 1_000L -> "${millis}ms"
    else -> "${millis / 100 / 10.0}s"
}

private const val PRACTICE_STATE_VERSION = "1"
private const val PRACTICE_STATE_FIELD_COUNT = 16
