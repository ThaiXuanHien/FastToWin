package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.SeasonHistoryEntrySnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.RewardAmounts
import com.hienthai.fastowin.ui.components.SeasonCosmeticRewardCard
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.layout.ResponsiveScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonHistoryScreen(
    state: GameState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    SystemBackHandler(onBack = onBack)
    val history = state.profile?.progression?.seasonHistory.orEmpty()
    ResponsiveScreen(
        modifier = modifier.testTag("season_history_screen"),
        maxContentWidth = 800.dp,
        applySafeDrawingInsets = true,
        includeBottomSafeDrawingInset = true
    ) { contentModifier ->
        PullToRefreshBox(
            isRefreshing = state.isProfileLoading,
            onRefresh = { if (!state.isProfileLoading) onRefresh() },
            modifier = contentModifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FastToWinHeader(
                    title = "Lịch sử mùa",
                    gold = state.profile?.progression?.gold ?: 0,
                    gems = state.profile?.progression?.gems ?: 0,
                    unreadNotifications = state.unreadNotificationCount,
                    onNotifications = onOpenNotifications,
                    onBack = onBack,
                    applySafeDrawingInset = false
                )
                when {
                    state.isProfileLoading && state.profile == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    history.isEmpty() -> SeasonHistoryEmptyState(Modifier.fillMaxSize())
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("season_history_list"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "history_summary") {
                                Text(
                                    "${history.size} mùa đã thi đấu",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(history, key = { it.seasonNumber }) { season ->
                                SeasonHistoryCard(season)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonHistoryEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Chưa có lịch sử mùa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Hoàn thành trận trong mùa xếp hạng để lưu thành tích tại đây.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeasonHistoryCard(season: SeasonHistoryEntrySnapshot) {
    val ranked = season.placementMatchesPlayed >= season.placementMatchesRequired
    val reward = season.reward
    val tierName = if (ranked) {
        reward?.tier?.displayName ?: rankedTierFor(season.peakRating).displayName
    } else {
        "Chưa phân hạng"
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("season_history_item:${season.seasonNumber}")
            .semantics {
                stateDescription = "${season.seasonName}, $tierName, Elo cao nhất ${season.peakRating}"
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.MilitaryTech,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(season.seasonName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Mùa ${season.seasonNumber} • ${season.matchesPlayed} trận",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val metricWidth = if (maxWidth >= 280.dp) (maxWidth - 8.dp) / 2 else maxWidth
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (maxWidth >= 280.dp) 2 else 1
                ) {
                    SeasonMetric("Bậc cao nhất", tierName, Modifier.width(metricWidth))
                    SeasonMetric("Elo cao nhất", season.peakRating.toString(), Modifier.width(metricWidth))
                    SeasonMetric("Elo cuối mùa", season.finalRating.toString(), Modifier.width(metricWidth))
                    SeasonMetric(
                        "Hạng cuối mùa",
                        season.finalRank?.let { "#$it" } ?: "Chưa xếp hạng",
                        Modifier.width(metricWidth)
                    )
                }
            }

            if (reward != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("season_history_reward:${season.seasonNumber}"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Phần thưởng đã nhận", fontWeight = FontWeight.Bold)
                    RewardAmounts(gold = reward.gold, xp = 0, gems = reward.gems)
                    reward.cosmetic?.let { SeasonCosmeticRewardCard(it) }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        if (ranked) "Phần thưởng mùa đang được xử lý."
                        else "Chưa đủ ${season.placementMatchesRequired} trận phân hạng nên mùa này không có thưởng.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}
