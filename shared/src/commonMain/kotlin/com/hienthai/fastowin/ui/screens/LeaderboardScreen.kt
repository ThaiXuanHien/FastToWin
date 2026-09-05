package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_leaderboard_trophy
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeRankBadge
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.SeasonProgressCard
import com.hienthai.fastowin.ui.components.SeasonRewardReceiptCard
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette

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
    var selectedMainTab by remember { mutableIntStateOf(0) }

    ResponsiveScreen(
        modifier = modifier,
        maxContentWidth = 920.dp,
        applySafeDrawingInsets = showBackButton,
        includeBottomSafeDrawingInset = showBackButton
    ) { contentModifier ->
        FastToWinPullRefresh(
            isRefreshing = state.isLeaderboardLoading,
            onRefresh = { if (!state.isLeaderboardLoading) onRefresh() },
            modifier = contentModifier
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
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
                ArcadeLeaderboardTabs(
                    selectedIndex = selectedMainTab,
                    onSelect = { selectedMainTab = it },
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                )

                if (state.isLeaderboardLoading && leaderboard == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ArcadePalette.Gold500)
                    }
                } else if (selectedMainTab == 0) {
                    PlayerLeaderboard(
                        state = state,
                        selectedPeriod = selectedPeriod,
                        onSelectPeriod = { selectedPeriod = it },
                        onOpenFriendProfile = onOpenFriendProfile,
                        onOpenSeasonHistory = onOpenSeasonHistory
                    )
                } else {
                    ClanLeaderboard(state)
                }
            }
        }
    }
}

@Composable
private fun ArcadeLeaderboardTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ArcadePalette.Navy900,
        border = BorderStroke(1.dp, ArcadePalette.OutlineDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ArcadeLeaderboardTab(
                label = "Cá nhân",
                selected = selectedIndex == 0,
                onClick = { onSelect(0) },
                modifier = Modifier.weight(1f).testTag("leaderboard_tab_players")
            )
            ArcadeLeaderboardTab(
                label = "Bang hội",
                selected = selectedIndex == 1,
                onClick = { onSelect(1) },
                modifier = Modifier.weight(1f).testTag("leaderboard_tab_clans")
            )
        }
    }
}

@Composable
private fun ArcadeLeaderboardTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ArcadePalette.Blue600 else Color.Transparent,
        contentColor = if (selected) ArcadePalette.White else ArcadePalette.Blue100
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(label, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PlayerLeaderboard(
    state: GameState,
    selectedPeriod: LeaderboardPeriod,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onOpenFriendProfile: (String) -> Unit,
    onOpenSeasonHistory: () -> Unit
) {
    val leaderboard = state.leaderboard
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
    var visiblePlayerCount by remember(selectedPeriod) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleTopPlayers = displayedTop.take(visiblePlayerCount)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("leaderboard_players_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectedPeriod == LeaderboardPeriod.CURRENT_SEASON) {
            state.profile?.progression?.season?.let { season ->
                item(key = "season_progress") { SeasonProgressCard(season) }
            }
        }
        item(key = "leaderboard_hero") {
            ArcadeFeatureHero(
                illustration = Res.drawable.arcade_leaderboard_trophy,
                title = "Đường đua danh vọng",
                subtitle = "Leo bậc, giữ chuỗi thắng và chiếm vị trí cao nhất.",
                accent = ArcadePalette.Gold500
            )
        }
        item(key = "ranking_filters") {
            LeaderboardPeriodFilters(
                selectedPeriod = selectedPeriod,
                previousSeasonAvailable = leaderboard?.previousSeasonName != null,
                onSelect = onSelectPeriod
            )
        }
        item(key = "ranking_actions") {
            ArcadeSegmentedControl(
                labels = listOf(
                    when (selectedPeriod) {
                        LeaderboardPeriod.CURRENT_SEASON -> leaderboard?.seasonName ?: "Mùa hiện tại"
                        LeaderboardPeriod.PREVIOUS_SEASON -> leaderboard?.previousSeasonName ?: "Mùa trước"
                        LeaderboardPeriod.ALL_TIME -> "Toàn thời gian"
                    },
                    "Lịch sử"
                ),
                selectedIndex = 0,
                onSelected = { index -> if (index == 1) onOpenSeasonHistory() },
                enabled = { index -> index == 0 || state.profile != null },
                itemTestTag = { index -> if (index == 1) "open_season_history" else "season_context_current" },
                modifier = Modifier.testTag("season_context_tabs")
            )
        }
        displayedCurrent?.let { current ->
            item(key = "current_player") {
                CurrentPlayerPanel(current)
            }
        }
        if (selectedPeriod == LeaderboardPeriod.PREVIOUS_SEASON) {
            state.profile?.progression?.latestSeasonReward
                ?.takeIf { it.seasonName == leaderboard?.previousSeasonName }
                ?.let { receipt ->
                    item(key = "season_reward_receipt") { SeasonRewardReceiptCard(receipt) }
                }
        }
        item(key = "top_players_title") {
            LeaderboardSectionTitle("Top người chơi", "${displayedTop.size} chiến binh")
        }
        if (displayedTop.isEmpty()) {
            item(key = "empty_players") {
                LeaderboardEmptyPanel(
                    when (selectedPeriod) {
                        LeaderboardPeriod.CURRENT_SEASON -> "Chưa có người chơi hoàn thành phân hạng mùa này."
                        LeaderboardPeriod.PREVIOUS_SEASON -> "Mùa trước chưa có người chơi đủ điều kiện xếp hạng."
                        LeaderboardPeriod.ALL_TIME -> "Chưa có người chơi hoàn thành trận đấu."
                    }
                )
            }
        } else {
            items(visibleTopPlayers, key = { it.playerCode }) { entry ->
                val friend = state.social.friends.firstOrNull { it.playerCode == entry.playerCode }
                LeaderboardCard(
                    entry = entry,
                    highlighted = entry.playerCode == displayedCurrent?.playerCode,
                    onClick = friend?.let { { onOpenFriendProfile(it.userId) } }
                )
            }
            item(key = "leaderboard_players_load_more") {
                ArcadeLoadMoreButton(
                    visibleItemCount = visibleTopPlayers.size,
                    totalItemCount = displayedTop.size,
                    onLoadMore = {
                        visiblePlayerCount = nextArcadePageItemCount(visiblePlayerCount, displayedTop.size)
                    },
                    testTag = "leaderboard_players_load_more"
                )
            }
        }
    }
}

@Composable
private fun LeaderboardPeriodFilters(
    selectedPeriod: LeaderboardPeriod,
    previousSeasonAvailable: Boolean,
    onSelect: (LeaderboardPeriod) -> Unit
) {
    val periods = listOf(
        LeaderboardPeriod.CURRENT_SEASON,
        LeaderboardPeriod.PREVIOUS_SEASON,
        LeaderboardPeriod.ALL_TIME
    )
    ArcadeSegmentedControl(
        labels = listOf("Hiện tại", "Mùa trước", "Toàn thời gian"),
        selectedIndex = periods.indexOf(selectedPeriod),
        onSelected = { onSelect(periods[it]) },
        enabled = { index -> index != 1 || previousSeasonAvailable },
        itemTestTag = { index ->
            when (index) {
                0 -> "leaderboard_period_current"
                1 -> "leaderboard_period_previous"
                else -> "leaderboard_period_all_time"
            }
        },
        modifier = Modifier.testTag("leaderboard_period_filters")
    )
}

@Composable
private fun CurrentPlayerPanel(entry: LeaderboardEntrySnapshot) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArcadeRankBadge(entry.rank)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "VỊ TRÍ CỦA BẠN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = ArcadePalette.Gold500
                )
                Text(
                    "#${entry.rank} · ${entry.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${formatArcadeNumber(entry.eloRating)} Elo",
                fontWeight = FontWeight.Black,
                color = ArcadePalette.Gold500
            )
        }
    }
}

@Composable
private fun ClanLeaderboard(state: GameState) {
    val leaderboard = state.leaderboard
    val displayedTopClans = leaderboard?.topClans.orEmpty()
    var visibleClanCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleTopClans = displayedTopClans.take(visibleClanCount)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("leaderboard_clans_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "clan_leaderboard_hero") {
            ArcadeFeatureHero(
                illustration = Res.drawable.arcade_leaderboard_trophy,
                title = "Bang hội mạnh nhất",
                subtitle = "Tổng hợp sức mạnh Elo của toàn bộ thành viên.",
                accent = ArcadePalette.Violet600
            )
        }
        leaderboard?.currentClan?.let { currentClan ->
            item(key = "current_clan") {
                CurrentClanPanel(currentClan)
            }
        }
        item(key = "top_clans_title") {
            LeaderboardSectionTitle("Top bang hội", "${displayedTopClans.size} bang")
        }
        if (displayedTopClans.isEmpty()) {
            item(key = "empty_clans") { LeaderboardEmptyPanel("Chưa có bang hội nào trên bảng xếp hạng.") }
        } else {
            items(visibleTopClans, key = { it.clanId }) { entry ->
                ClanLeaderboardCard(
                    entry = entry,
                    highlighted = entry.clanId == leaderboard?.currentClan?.clanId
                )
            }
            item(key = "leaderboard_clans_load_more") {
                ArcadeLoadMoreButton(
                    visibleItemCount = visibleTopClans.size,
                    totalItemCount = displayedTopClans.size,
                    onLoadMore = {
                        visibleClanCount = nextArcadePageItemCount(visibleClanCount, displayedTopClans.size)
                    },
                    testTag = "leaderboard_clans_load_more"
                )
            }
        }
    }
}

@Composable
private fun CurrentClanPanel(entry: ClanLeaderboardEntrySnapshot) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Gold500) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArcadeRankBadge(entry.rank)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "BANG CỦA BẠN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = ArcadePalette.Gold500
                )
                Text(entry.clanName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
            Text(
                formatArcadeNumber(entry.totalElo),
                fontWeight = FontWeight.Black,
                color = ArcadePalette.Gold500
            )
        }
    }
}

@Composable
private fun LeaderboardSectionTitle(title: String, meta: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(meta, style = MaterialTheme.typography.labelLarge, color = ArcadePalette.Gold500)
    }
}

@Composable
private fun LeaderboardEmptyPanel(message: String) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Blue300) {
        ArcadeEmptyState(
            illustration = Res.drawable.arcade_leaderboard_trophy,
            title = "Đường đua đang chờ",
            description = message
        )
    }
}

@Composable
private fun ClanLeaderboardCard(
    entry: ClanLeaderboardEntrySnapshot,
    highlighted: Boolean
) {
    LeaderboardSurface(highlighted = highlighted) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArcadeRankBadge(entry.rank)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.clanName + if (highlighted) " · Bạn" else "",
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${entry.memberCount} thành viên",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatArcadeNumber(entry.totalElo), fontWeight = FontWeight.Black, color = ArcadePalette.Gold500)
                Text("Tổng Elo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    LeaderboardSurface(highlighted = highlighted, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeRankBadge(entry.rank)
            PlayerAvatar(
                displayName = entry.displayName,
                avatarId = entry.avatarId,
                userId = entry.userId,
                frameId = entry.frameId,
                size = 44.dp
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.displayName + if (highlighted) " · Bạn" else "",
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${rankedTierFor(entry.eloRating).displayName} · ${entry.wins} thắng · $winRate%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatArcadeNumber(entry.eloRating),
                    fontWeight = FontWeight.Black,
                    color = if (highlighted) ArcadePalette.Gold500 else MaterialTheme.colorScheme.primary
                )
                Text("Elo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LeaderboardSurface(
    highlighted: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val border = BorderStroke(
        1.dp,
        if (highlighted) ArcadePalette.Gold500 else ArcadePalette.Blue300.copy(alpha = 0.42f)
    )
    if (onClick == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = border,
            shadowElevation = if (highlighted) 4.dp else 2.dp,
            content = content
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = border,
            shadowElevation = if (highlighted) 4.dp else 2.dp,
            content = content
        )
    }
}

private enum class LeaderboardPeriod { CURRENT_SEASON, PREVIOUS_SEASON, ALL_TIME }

private fun formatArcadeNumber(value: Int): String = value
    .toString()
    .reversed()
    .chunked(3)
    .joinToString(".")
    .reversed()
