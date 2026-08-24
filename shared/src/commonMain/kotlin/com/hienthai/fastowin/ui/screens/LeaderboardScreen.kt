package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.SeasonProgressCard
import com.hienthai.fastowin.ui.components.SeasonRewardReceiptCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    onOpenSeasonHistory: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)
    val leaderboard = state.leaderboard
    var selectedPeriod by remember { mutableStateOf(LeaderboardPeriod.CURRENT_SEASON) }
    ResponsiveScreen(
        modifier = modifier,
        maxContentWidth = 920.dp,
        applySafeDrawingInsets = showBackButton,
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
        if (showBackButton) {
            FastToWinHeader(
                title = "Xếp hạng",
                gold = state.profile?.progression?.gold ?: 0,
                gems = state.profile?.progression?.gems ?: 0,
                unreadNotifications = state.unreadNotificationCount,
                onNotifications = onOpenNotifications,
                onBack = onBack,
                applySafeDrawingInset = false
            )
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
            val displayedCurrent = when (selectedPeriod) {
                LeaderboardPeriod.CURRENT_SEASON -> leaderboard?.seasonCurrentPlayer
                LeaderboardPeriod.PREVIOUS_SEASON -> leaderboard?.previousSeasonCurrentPlayer
                LeaderboardPeriod.ALL_TIME -> leaderboard?.currentPlayer
            }
            val displayedTop = when (selectedPeriod) {
                LeaderboardPeriod.CURRENT_SEASON -> leaderboard?.seasonTopPlayers.orEmpty()
                LeaderboardPeriod.PREVIOUS_SEASON -> leaderboard?.previousSeasonTopPlayers.orEmpty()
                LeaderboardPeriod.ALL_TIME -> leaderboard?.topPlayers.orEmpty()
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("leaderboard_players_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedPeriod == LeaderboardPeriod.CURRENT_SEASON) {
                    state.profile?.progression?.season?.let { season ->
                        item(key = "season_progress") { SeasonProgressCard(season) }
                    }
                }
                if (selectedPeriod == LeaderboardPeriod.PREVIOUS_SEASON) {
                    state.profile?.progression?.latestSeasonReward
                        ?.takeIf { it.seasonName == leaderboard?.previousSeasonName }
                        ?.let { receipt ->
                        item(key = "season_reward_receipt") { SeasonRewardReceiptCard(receipt) }
                    }
                }
                item(key = "ranking_description") {
                    Text(
                        when (selectedPeriod) {
                            LeaderboardPeriod.CURRENT_SEASON ->
                                "${leaderboard?.seasonName ?: "Mùa hiện tại"}: Elo mùa được tính lại từ 1.000."
                            LeaderboardPeriod.PREVIOUS_SEASON ->
                                "Kết quả đã chốt của ${leaderboard?.previousSeasonName ?: "mùa trước"}."
                            LeaderboardPeriod.ALL_TIME ->
                                "Xếp theo Elo, sau đó số trận thắng, tỷ lệ thắng và điểm cao nhất."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "season_history_action") {
                    OutlinedButton(
                        onClick = onOpenSeasonHistory,
                        enabled = state.profile != null,
                        modifier = Modifier.fillMaxWidth().testTag("open_season_history")
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Lịch sử mùa giải")
                    }
                }
                item(key = "ranking_filters") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().testTag("leaderboard_period_filters"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedPeriod == LeaderboardPeriod.CURRENT_SEASON,
                            onClick = { selectedPeriod = LeaderboardPeriod.CURRENT_SEASON },
                            label = { Text("Hiện tại") },
                            modifier = Modifier.testTag("leaderboard_period_current")
                        )
                        FilterChip(
                            selected = selectedPeriod == LeaderboardPeriod.PREVIOUS_SEASON,
                            onClick = { selectedPeriod = LeaderboardPeriod.PREVIOUS_SEASON },
                            enabled = leaderboard?.previousSeasonName != null,
                            label = { Text("Mùa trước") },
                            modifier = Modifier.testTag("leaderboard_period_previous")
                        )
                        FilterChip(
                            selected = selectedPeriod == LeaderboardPeriod.ALL_TIME,
                            onClick = { selectedPeriod = LeaderboardPeriod.ALL_TIME },
                            label = { Text("Toàn thời gian") }
                        )
                    }
                }
                displayedCurrent?.let { current ->
                    item(key = "current_player_title") {
                        Text("Vị trí của bạn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item(key = "current_player") { LeaderboardCard(current, highlighted = true) }
                }
                item(key = "top_players_title") {
                    Text("Top người chơi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (displayedTop.isEmpty()) {
                    item(key = "empty_players") {
                        Text(
                            when (selectedPeriod) {
                                LeaderboardPeriod.CURRENT_SEASON -> "Chưa có người chơi nào hoàn thành phân hạng mùa này."
                                LeaderboardPeriod.PREVIOUS_SEASON -> "Mùa trước chưa có người chơi đủ điều kiện xếp hạng."
                                LeaderboardPeriod.ALL_TIME -> "Chưa có người chơi nào hoàn thành trận đấu."
                            }
                        )
                    }
                } else {
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
            val displayedTopClans = leaderboard?.topClans.orEmpty()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "clan_description") {
                    Text(
                        "Xếp hạng Bang hội theo tổng Elo của các thành viên.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                leaderboard?.currentClan?.let { currentClan ->
                    item(key = "current_clan_title") {
                        Text(
                            "Vị trí bang hội của bạn",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item(key = "current_clan") { ClanLeaderboardCard(currentClan, highlighted = true) }
                }
                item(key = "top_clans_title") {
                    Text("Top Bang hội", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (displayedTopClans.isEmpty()) {
                    item(key = "empty_clans") { Text("Chưa có bang hội nào.") }
                } else {
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

private enum class LeaderboardPeriod { CURRENT_SEASON, PREVIOUS_SEASON, ALL_TIME }

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
            PlayerAvatar(
                displayName = entry.displayName,
                avatarId = entry.avatarId,
                frameId = entry.frameId,
                size = 44.dp
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
