package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.SeasonHistoryEntrySnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.RewardAmounts
import com.hienthai.fastowin.ui.components.SeasonCosmeticRewardCard
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_leaderboard_trophy
import com.hienthai.fastowin.ui.theme.ArcadePalette

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
    ArcadeBackdrop(modifier = modifier.fillMaxSize().testTag("season_history_screen")) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                FastToWinHeader(
                    title = "Lịch sử mùa",
                    gold = state.profile?.progression?.gold ?: 0,
                    gems = state.profile?.progression?.gems ?: 0,
                    unreadNotifications = state.unreadNotificationCount,
                    onNotifications = onOpenNotifications,
                    onBack = onBack
                )
            }
        ) { paddingValues ->
            ResponsiveScreen(
                modifier = Modifier.padding(paddingValues),
                maxContentWidth = 800.dp,
                applySafeDrawingInsets = false
            ) { contentModifier ->
                FastToWinPullRefresh(
                    isRefreshing = state.isProfileLoading,
                    onRefresh = { if (!state.isProfileLoading) onRefresh() },
                    modifier = contentModifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("season_history_list"),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "season_history_hero") {
                            ArcadeFeatureHero(
                                illustration = Res.drawable.arcade_leaderboard_trophy,
                                title = if (history.isEmpty()) "Hành trình xếp hạng" else "${history.size} mùa đã thi đấu",
                                subtitle = "Xem lại bậc, Elo cao nhất và phần thưởng qua từng mùa.",
                                accent = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        when {
                            state.isProfileLoading && state.profile == null -> {
                                item(key = "season_history_loading") {
                                    ArcadePanel(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator()
                                            Text(
                                                "Đang tải lịch sử mùa...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            history.isEmpty() -> {
                                item(key = "season_history_empty") {
                                    SeasonHistoryEmptyState(Modifier.fillMaxWidth())
                                }
                            }

                            else -> {
                                item(key = "history_summary") {
                                    Text(
                                        "Thành tích qua các mùa",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                itemsIndexed(
                                    items = history,
                                    key = { _, season -> season.seasonNumber }
                                ) { index, season ->
                                    SeasonHistoryCard(season = season, highlighted = index == 0)
                                }
                            }
                        }

                        if (history.isNotEmpty()) {
                            item(key = "season_history_footer") {
                                Text(
                                    "Kéo xuống để cập nhật kết quả mùa mới nhất.",
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun SeasonHistoryEmptyState(modifier: Modifier = Modifier) {
    ArcadePanel(modifier = modifier, accent = MaterialTheme.colorScheme.tertiary) {
        ArcadeEmptyState(
            illustration = Res.drawable.arcade_leaderboard_trophy,
            title = "Chưa có lịch sử mùa",
            description = "Hoàn thành trận xếp hạng để lưu dấu mùa giải đầu tiên."
        )
    }
}

@Composable
private fun SeasonHistoryCard(
    season: SeasonHistoryEntrySnapshot,
    highlighted: Boolean
) {
    val ranked = season.placementMatchesPlayed >= season.placementMatchesRequired
    val reward = season.reward
    val tierName = if (ranked) {
        reward?.tier?.displayName ?: rankedTierFor(season.peakRating).displayName
    } else {
        "Chưa phân hạng"
    }
    val accent = if (highlighted) ArcadePalette.Gold500 else MaterialTheme.colorScheme.primary
    val metricValueColor = if (highlighted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    ArcadePanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("season_history_item:${season.seasonNumber}")
            .semantics {
                stateDescription = "${season.seasonName}, $tierName, Elo cao nhất ${season.peakRating}"
            },
        accent = accent
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
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (highlighted) ArcadePalette.Gold100 else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (highlighted) ArcadePalette.Gold800 else MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MilitaryTech,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            season.seasonName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        if (highlighted) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = ArcadePalette.Gold100,
                                contentColor = ArcadePalette.Gold800
                            ) {
                                Text(
                                    "MỚI NHẤT",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    Text(
                        "Mùa ${season.seasonNumber} • ${season.matchesPlayed} trận • $tierName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useTwoColumns = maxWidth >= 360.dp
                val metricWidth = if (useTwoColumns) (maxWidth - 8.dp) / 2 else maxWidth
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (useTwoColumns) 2 else 1
                ) {
                    SeasonMetric("Bậc cao nhất", tierName, metricValueColor, Modifier.width(metricWidth))
                    SeasonMetric("Elo cao nhất", season.peakRating.toString(), metricValueColor, Modifier.width(metricWidth))
                    SeasonMetric("Elo cuối mùa", season.finalRating.toString(), metricValueColor, Modifier.width(metricWidth))
                    SeasonMetric(
                        "Hạng cuối mùa",
                        season.finalRank?.let { "#$it" } ?: "Chưa xếp hạng",
                        metricValueColor,
                        Modifier.width(metricWidth)
                    )
                }
            }

            if (reward != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag("season_history_reward:${season.seasonNumber}"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Phần thưởng đã nhận",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black
                    )
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
private fun SeasonMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = accent)
        }
    }
}
