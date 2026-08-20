package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.GameFeedbackEffect
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
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    mode: GameMode,
    challenge: PracticeChallenge? = null,
    preferences: AppPreferences,
    onBack: () -> Unit,
    onShareChallenge: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentChallenge by remember(mode, challenge?.code) {
        mutableStateOf(challenge ?: createPracticeChallenge(mode, challengeSeed()))
    }
    var game by remember(mode, currentChallenge.code) {
        mutableStateOf(createPracticeGame(mode, epochMillis(), challenge = currentChallenge))
    }
    var feedback by remember(mode, currentChallenge.code) { mutableStateOf<String?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Luyện tập", fontWeight = FontWeight.Bold)
                        Text(
                            "${mode.title} • Ngoại tuyến",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Thoát luyện tập")
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
            if (game.isComplete) {
                PracticeResult(
                    game = game,
                    onRestart = {
                        game = createPracticeGame(mode, epochMillis(), challenge = currentChallenge)
                        feedback = null
                    },
                    onNewChallenge = {
                        currentChallenge = createPracticeChallenge(mode, challengeSeed())
                    },
                    onShareChallenge = onShareChallenge,
                    onBack = onBack,
                    modifier = contentModifier
                )
            } else {
                Column(modifier = contentModifier) {
                    PracticeStatus(game)
                    Text(
                        feedback ?: "Chạm các số theo thứ tự từ 1 đến $GAME_NUMBER_COUNT",
                        modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                        color = if (feedback == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                    NumberGrid(
                        numbers = game.numbers,
                        currentTarget = game.currentTarget,
                        selectedNumbers = game.selectedNumbers,
                        enabled = true,
                        boardStyle = preferences.boardStyle,
                        onNumberClick = { number ->
                            val correct = number == game.currentTarget
                            val effect = if (correct) GameFeedbackEffect.CORRECT else GameFeedbackEffect.WRONG
                            if (preferences.soundEnabled) playFeedbackSound(effect)
                            if (preferences.vibrationEnabled) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            game = game.select(number, epochMillis())
                            feedback = if (correct) null else "Chưa đúng số, hãy tìm ${game.currentTarget}"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeStatus(game: PracticeGameState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PracticeMetric("Cần tìm", game.currentTarget.coerceAtMost(GAME_NUMBER_COUNT).toString(), Modifier.weight(1f))
        PracticeMetric("Điểm", game.score.toString(), Modifier.weight(1f))
        PracticeMetric(
            if (game.mode in setOf(GameMode.TIME_ATTACK, GameMode.TIME_BONUS, GameMode.SPEED_UP)) "Còn lại" else "Thời gian",
            formatPracticeTime(
                if (game.mode in setOf(GameMode.TIME_ATTACK, GameMode.TIME_BONUS, GameMode.SPEED_UP)) {
                    game.timeLeftMillis
                } else game.elapsedMillis
            ),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun PracticeMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("HOÀN THÀNH", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(
            when {
                game.correctSelections >= GAME_NUMBER_COUNT -> "Bạn đã tìm đủ 50 số"
                game.mode == GameMode.SURVIVAL -> "Bạn đã hết 3 lượt bấm sai"
                game.mode == GameMode.SPEED_UP -> "Bạn không kịp tìm mục tiêu tiếp theo"
                game.mode == GameMode.TIME_BONUS -> "Bạn đã hết thời gian tích lũy"
                game.mode == GameMode.TIME_ATTACK -> "Hết 60 giây"
                else -> "Thử thách đã kết thúc"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${game.score} điểm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Đúng ${game.correctSelections} • Sai ${game.wrongSelections} • Chính xác ${game.accuracyPercent}%")
                Text("Thời gian ${formatPracticeTime(game.elapsedMillis)}")
            }
        }
        game.challengeCode?.let { code ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
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
        Text("Kết quả luyện tập không ảnh hưởng Elo.", style = MaterialTheme.typography.bodySmall)
        if (challengeText != null) {
            Button(
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
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("share_challenge")
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Text("  Chia sẻ thử thách")
            }
        }
        shareError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
            Text("  Chơi lại cùng bàn")
        }
        OutlinedButton(onClick = onNewChallenge, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text("  Tạo thử thách mới")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Về trang chủ") }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Luyện tập & thử thách") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Tạo một bàn mới hoặc nhập mã bạn bè đã gửi.")
                Button(
                    onClick = onStartNew,
                    modifier = Modifier.fillMaxWidth().testTag("practice_new")
                ) { Text("Bắt đầu luyện tập mới") }
                HorizontalDivider()
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
                    modifier = Modifier.fillMaxWidth().testTag("challenge_input")
                )
                OutlinedButton(
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
                    modifier = Modifier.fillMaxWidth().testTag("challenge_open")
                ) { Text("Chơi thử thách") }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

internal fun buildChallengeShareText(mode: GameMode, code: String, score: Int, elapsedMillis: Long): String =
    """
    Thử thách Fast To Win • ${mode.title}
    Mình đạt $score điểm trong ${formatPracticeTime(elapsedMillis)}.
    Mở trực tiếp: ${buildChallengeDeepLink(code)}
    Mã thử thách: $code
    Nếu liên kết không mở, vào Luyện tập offline và nhập mã để chơi cùng bàn số.
    """.trimIndent()

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
