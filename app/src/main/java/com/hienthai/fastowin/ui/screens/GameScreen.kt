package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.theme.FastToWinTheme

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onNumberClick: (Int) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver) {
            onFinish()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Fast To Win", style = MaterialTheme.typography.titleMedium)
                        state.message?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                actions = {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 16.dp)) {
                        Text(text = "Score: ${state.score}", fontWeight = FontWeight.Bold)
                        if (state.timeLeftMillis > 0) {
                            val seconds = (state.timeLeftMillis / 1000) % 60
                            val minutes = (state.timeLeftMillis / 1000) / 60
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                                color = if (state.timeLeftMillis < 10000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isWide = maxWidth > 600.dp
            
            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // In a more complex app, this could be a side panel
                    NumberGrid(
                        numbers = state.numbers,
                        currentTarget = state.currentTarget,
                        onNumberClick = onNumberClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                NumberGrid(
                    numbers = state.numbers,
                    currentTarget = state.currentTarget,
                    onNumberClick = onNumberClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun NumberGrid(
    numbers: List<Int>,
    currentTarget: Int,
    onNumberClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(numbers) { number ->
            NumberCell(
                number = number,
                isCompleted = number < currentTarget,
                onClick = { onNumberClick(number) }
            )
        }
    }
}

@Composable
fun NumberCell(
    number: Int,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isCompleted) { onClick() },
        color = when {
            isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = when {
                    isCompleted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontSize = 20.sp
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GameScreenMobilePreview() {
    FastToWinTheme {
        GameScreen(
            state = GameState(
                numbers = (1..100).toList(),
                currentTarget = 5,
                score = 40,
                timeLeftMillis = 295000
            ),
            onNumberClick = {},
            onFinish = {}
        )
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun GameScreenTabletPreview() {
    FastToWinTheme {
        GameScreen(
            state = GameState(
                numbers = (1..100).toList(),
                currentTarget = 1,
                score = 0
            ),
            onNumberClick = {},
            onFinish = {}
        )
    }
}
