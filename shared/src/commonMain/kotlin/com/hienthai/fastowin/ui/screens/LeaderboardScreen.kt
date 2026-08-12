package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.state.GameState

@Composable
fun LeaderboardScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leaderboard = state.leaderboard
    Column(
        modifier = modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text("Bảng xếp hạng", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !state.isLeaderboardLoading) {
                Icon(Icons.Default.Refresh, "Làm mới")
            }
        }

        Text(
            "Xếp theo số trận thắng, tỷ lệ thắng và điểm cao nhất.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isLeaderboardLoading && leaderboard == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        leaderboard?.currentPlayer?.let { current ->
            Text("Vị trí của bạn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LeaderboardCard(current, highlighted = true)
        }

        Text("Top người chơi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (leaderboard == null || leaderboard.topPlayers.isEmpty()) {
            Text("Chưa có người chơi nào hoàn thành trận đấu.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(leaderboard.topPlayers, key = { it.playerCode }) { entry ->
                    LeaderboardCard(
                        entry = entry,
                        highlighted = entry.playerCode == leaderboard.currentPlayer?.playerCode
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(entry: LeaderboardEntrySnapshot, highlighted: Boolean) {
    val winRate = if (entry.totalMatches == 0) 0 else entry.wins * 100 / entry.totalMatches
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = when (entry.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${entry.rank}" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.displayName + if (highlighted) " (Bạn)" else "",
                    fontWeight = FontWeight.Bold
                )
                Text(entry.playerCode, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${entry.wins} thắng", fontWeight = FontWeight.Bold)
                Text("$winRate% • ${entry.highestScore} điểm", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
