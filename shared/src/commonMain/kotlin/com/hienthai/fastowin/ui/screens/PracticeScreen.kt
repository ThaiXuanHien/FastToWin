package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.preferences.AppPreferences
import com.hienthai.fastowin.navigation.GameMode
import com.hienthai.fastowin.platform.GameFeedbackEffect
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.platform.playFeedbackSound
import com.hienthai.fastowin.state.GAME_NUMBER_COUNT
import com.hienthai.fastowin.state.PracticeGameState
import com.hienthai.fastowin.state.createPracticeGame
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    mode: GameMode,
    preferences: AppPreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var game by remember(mode) { mutableStateOf(createPracticeGame(mode, epochMillis())) }
    var feedback by remember(mode) { mutableStateOf<String?>(null) }
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
                            if (mode == GameMode.ORDER) "Đua thứ tự • Offline" else "Đua 60 giây • Offline",
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
                        game = createPracticeGame(mode, epochMillis())
                        feedback = null
                    },
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
            if (game.mode == GameMode.TIME_ATTACK) "Còn lại" else "Thời gian",
            formatPracticeTime(if (game.mode == GameMode.TIME_ATTACK) game.timeLeftMillis else game.elapsedMillis),
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Text("HOÀN THÀNH", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(
            if (game.mode == GameMode.ORDER && game.currentTarget > GAME_NUMBER_COUNT) {
                "Bạn đã tìm đủ 50 số"
            } else {
                "Hết 60 giây"
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
        Text("Kết quả luyện tập không ảnh hưởng Elo.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
            Text("  Luyện tập lại")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Về trang chủ") }
    }
}

private fun formatPracticeTime(millis: Long): String {
    val totalTenths = millis.coerceAtLeast(0L) / 100
    val minutes = totalTenths / 600
    val seconds = totalTenths % 600 / 10
    val tenths = totalTenths % 10
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.$tenths"
}
