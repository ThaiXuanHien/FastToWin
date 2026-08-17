package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

@Composable
fun LeaderboardScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val leaderboard = state.leaderboard
    var showSeason by remember { mutableStateOf(true) }
    ResponsiveScreen(
        modifier = modifier,
        maxContentWidth = 920.dp,
        includeBottomSafeDrawingInset = showBackButton
    ) { contentModifier ->
        Column(
            modifier = contentModifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showBackButton) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            else Spacer(Modifier.size(48.dp))
            Text("Bảng xếp hạng", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !state.isLeaderboardLoading) {
                Icon(Icons.Default.Refresh, "Làm mới")
            }
        }

        Text(
            if (showSeason) "Xếp hạng trong mùa hiện tại, tự làm mới sau mỗi trận."
            else "Xếp theo Elo, sau đó số trận thắng, tỷ lệ thắng và điểm cao nhất.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.isLeaderboardLoading && leaderboard == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showSeason, onClick = { showSeason = true }, label = {
                Text(leaderboard?.seasonName ?: "Mùa hiện tại")
            })
            FilterChip(selected = !showSeason, onClick = { showSeason = false }, label = { Text("Toàn thời gian") })
        }

        val displayedCurrent = if (showSeason) leaderboard?.seasonCurrentPlayer else leaderboard?.currentPlayer
        val displayedTop = if (showSeason) leaderboard?.seasonTopPlayers.orEmpty() else leaderboard?.topPlayers.orEmpty()

        displayedCurrent?.let { current ->
            Text("Vị trí của bạn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LeaderboardCard(current, highlighted = true)
        }

        Text("Top người chơi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (displayedTop.isEmpty()) {
            Text(if (showSeason) "Chưa có người chơi nào thi đấu trong mùa này." else "Chưa có người chơi nào hoàn thành trận đấu.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayedTop, key = { it.playerCode }) { entry ->
                    LeaderboardCard(
                        entry = entry,
                        highlighted = entry.playerCode == displayedCurrent?.playerCode
                    )
                }
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
                Text("Elo ${entry.eloRating}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${entry.wins} thắng", fontWeight = FontWeight.Bold)
                Text("$winRate% • ${entry.highestScore} điểm", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
