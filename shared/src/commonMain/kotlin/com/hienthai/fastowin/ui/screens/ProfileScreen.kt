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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.MatchHistoryOutcome
import com.hienthai.fastowin.protocol.MatchHistorySnapshot
import com.hienthai.fastowin.state.GameState

@Composable
fun ProfileScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = state.profile
    Column(
        modifier = modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text("Hồ sơ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !state.isProfileLoading) {
                Icon(Icons.Default.Refresh, "Làm mới hồ sơ")
            }
        }

        if (state.isProfileLoading && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (profile == null) {
            Text("Chưa tải được hồ sơ. Hãy kiểm tra kết nối và thử lại.")
            return@Column
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(14.dp))
                }
                Column {
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Mã người chơi: ${profile.playerCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val stats = profile.statistics
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Trận", stats.totalMatches, Modifier.weight(1f))
            StatCard("Thắng", stats.wins, Modifier.weight(1f))
            StatCard("Thua", stats.losses, Modifier.weight(1f))
            StatCard("Hòa", stats.draws, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Điểm cao", stats.highestScore, Modifier.weight(1f))
            StatCard("Chuỗi hiện tại", stats.currentWinStreak, Modifier.weight(1f))
            StatCard("Chuỗi tốt nhất", stats.bestWinStreak, Modifier.weight(1f))
        }

        Text("Trận gần đây", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (profile.recentMatches.isEmpty()) {
            Text("Bạn chưa có trận đấu hoàn thành.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profile.recentMatches, key = { it.matchId }) { match -> MatchHistoryCard(match) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MatchHistoryCard(match: MatchHistorySnapshot) {
    val (result, color) = when (match.outcome) {
        MatchHistoryOutcome.WIN -> "Thắng" to MaterialTheme.colorScheme.primary
        MatchHistoryOutcome.LOSS -> "Thua" to MaterialTheme.colorScheme.error
        MatchHistoryOutcome.DRAW -> "Hòa" to MaterialTheme.colorScheme.tertiary
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(match.opponentName, fontWeight = FontWeight.Bold)
                Text(match.roomName, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(result, color = color, fontWeight = FontWeight.Bold)
                Text("${match.playerScore} – ${match.opponentScore}")
            }
        }
    }
}
