package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)

    val leaderboard = state.leaderboard
    var showSeason by remember { mutableStateOf(true) }
    ResponsiveScreen(
        modifier = modifier,
        maxContentWidth = 920.dp,
        includeBottomSafeDrawingInset = showBackButton
    ) { contentModifier ->
        PullToRefreshBox(
            isRefreshing = state.isLeaderboardLoading,
            onRefresh = { if (!state.isLeaderboardLoading) onRefresh() },
            modifier = contentModifier
        ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
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
            Spacer(Modifier.size(48.dp))
        }

        var selectedMainTab by remember { mutableStateOf(0) } // 0: Cá nhân, 1: Bang hội

        androidx.compose.material3.TabRow(selectedTabIndex = selectedMainTab) {
            androidx.compose.material3.Tab(
                selected = selectedMainTab == 0,
                onClick = { selectedMainTab = 0 },
                text = { Text("Cá nhân") }
            )
            androidx.compose.material3.Tab(
                selected = selectedMainTab == 1,
                onClick = { selectedMainTab = 1 },
                text = { Text("Bang hội") }
            )
        }

        if (state.isLeaderboardLoading && leaderboard == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        if (selectedMainTab == 0) {
            Text(
                if (showSeason) "Xếp hạng trong mùa hiện tại, tự làm mới sau mỗi trận."
                else "Xếp theo Elo, sau đó số trận thắng, tỷ lệ thắng và điểm cao nhất.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                        val friend = state.social.friends.firstOrNull { it.playerCode == entry.playerCode }
                        LeaderboardCard(
                            entry = entry,
                            highlighted = entry.playerCode == displayedCurrent?.playerCode,
                            onClick = if (friend == null) null else ({ onOpenFriendProfile(friend.userId) })
                        )
                    }
                }
            }
        } else {
            Text("Xếp hạng Bang hội theo tổng Elo của các thành viên.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            val displayedTopClans = leaderboard?.topClans.orEmpty()
            
            leaderboard?.currentClan?.let { currentClan ->
                Text("Vị trí bang hội của bạn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ClanLeaderboardCard(currentClan, highlighted = true)
            }
            
            Text("Top Bang hội", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (displayedTopClans.isEmpty()) {
                Text("Chưa có bang hội nào.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayedTopClans, key = { it.clanId }) { entry ->
                        ClanLeaderboardCard(
                            entry = entry,
                            highlighted = entry.clanId == leaderboard?.currentClan?.clanId
                        )
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun ClanLeaderboardCard(
    entry: com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot,
    highlighted: Boolean
) {
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
                    entry.clanName + if (highlighted) " (Bạn)" else "",
                    fontWeight = FontWeight.Bold
                )
                Text("${entry.memberCount} thành viên", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Tổng Elo ${entry.totalElo}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LeaderboardCard(
    entry: LeaderboardEntrySnapshot,
    highlighted: Boolean,
    onClick: (() -> Unit)? = null
) {
    val winRate = if (entry.totalMatches == 0) 0 else entry.wins * 100 / entry.totalMatches
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
        ),
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
                Text(
                    "${rankedTierFor(entry.eloRating).displayName} • Elo ${entry.eloRating}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text("${entry.wins} thắng", fontWeight = FontWeight.Bold)
                Text("$winRate% • ${entry.highestScore} điểm", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
