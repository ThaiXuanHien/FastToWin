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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedRankedTierName
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_leaderboard_trophy
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeFeatureHero
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeRankBadge
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.HeaderRefreshAction
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
    onOpenSeasonHistory: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
    onOpenClan: (String) -> Unit = {},
    onRefreshSocial: () -> Unit = {},
    onSendFriendRequest: (String) -> Unit = {},
    onRespondFriendRequest: (String, Boolean) -> Unit = { _, _ -> },
    onUpgradeGuest: (() -> Unit)? = null
) {
    SystemBackHandler(enabled = showBackButton, onBack = onBack)
    val leaderboard = state.leaderboard
    var selectedPeriod by rememberSaveable { mutableStateOf(LeaderboardPeriod.CURRENT_SEASON) }
    var selectedPlayerMetric by rememberSaveable { mutableStateOf(PlayerLeaderboardMetric.ELO) }
    var selectedClanMetric by rememberSaveable { mutableStateOf(ClanLeaderboardMetric.LEVEL) }
    var selectedMainTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedPlayer by remember { mutableStateOf<LeaderboardEntrySnapshot?>(null) }

    ResponsiveScreen(
        modifier = modifier.testTag("leaderboard_screen"),
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
                        title = localized(TextKey.Leaderboard),
                        gold = state.profile?.progression?.gold ?: 0,
                        gems = state.profile?.progression?.gems ?: 0,
                        unreadNotifications = state.unreadNotificationCount,
                        onNotifications = onOpenNotifications,
                        onBack = onBack,
                        applySafeDrawingInset = false,
                        actions = {
                            HeaderRefreshAction(
                                isRefreshing = state.isLeaderboardLoading,
                                onRefresh = onRefresh
                            )
                        }
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
                        selectedMetric = selectedPlayerMetric,
                        onSelectMetric = { selectedPlayerMetric = it },
                        selectedPeriod = selectedPeriod,
                        onSelectPeriod = { selectedPeriod = it },
                        onSelectPlayer = {
                            selectedPlayer = it
                            if (onUpgradeGuest == null && it.playerCode != state.profile?.playerCode && !state.isClanSocialReady) {
                                onRefreshSocial()
                            }
                        },
                        onOpenSeasonHistory = onOpenSeasonHistory
                    )
                } else {
                    ClanLeaderboard(
                        state = state,
                        selectedMetric = selectedClanMetric,
                        onSelectMetric = { selectedClanMetric = it },
                        onOpenClan = onOpenClan
                    )
                }
            }
        }
    }
    selectedPlayer?.let { entry ->
        LeaderboardPlayerDialog(
            entry = entry,
            state = state,
            onDismiss = { selectedPlayer = null },
            onRefreshSocial = onRefreshSocial,
            onSendFriendRequest = onSendFriendRequest,
            onRespondFriendRequest = onRespondFriendRequest,
            onUpgradeGuest = onUpgradeGuest
        )
    }
}

@Composable
fun LeaderboardPlayerDialog(
    entry: LeaderboardEntrySnapshot,
    state: GameState,
    onDismiss: () -> Unit,
    onRefreshSocial: () -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onRespondFriendRequest: (String, Boolean) -> Unit,
    onUpgradeGuest: (() -> Unit)?
) {
    val isSelf = entry.playerCode == state.profile?.playerCode ||
        entry.userId?.let { it == state.profile?.userId } == true
    val action = if (isSelf) null else clanMemberFriendAction(
        userId = entry.userId.orEmpty(),
        playerCode = entry.playerCode,
        currentUserId = state.profile?.userId,
        social = state.social,
        isLoading = state.isFriendsLoading,
        isSocialReady = state.isClanSocialReady
    )
    val winRate = if (entry.totalMatches == 0) 0 else entry.wins.toLong() * 100 / entry.totalMatches
    ArcadeDialog(
        title = entry.displayName,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("leaderboard_player_profile_dialog"),
        scrollableContent = true,
        fixedActions = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onUpgradeGuest != null && !isSelf) {
                    ArcadeActionButton(localized(TextKey.AddFriend), onUpgradeGuest, Modifier.fillMaxWidth())
                } else if (action != null) {
                    ArcadeActionButton(
                        label = localized(if (state.isFriendsLoading) TextKey.Processing else action.textKey),
                        onClick = {
                            when (action.type) {
                                ClanMemberFriendActionType.SEND -> onSendFriendRequest(entry.playerCode)
                                ClanMemberFriendActionType.ACCEPT -> action.requestId?.let { onRespondFriendRequest(it, true) }
                                else -> Unit
                            }
                        },
                        enabled = action.enabled,
                        modifier = Modifier.fillMaxWidth().testTag("leaderboard_player_friend_action")
                    )
                    if (!state.isClanSocialReady && !state.isFriendsLoading) {
                        ArcadeActionButton(
                            localized(TextKey.Retry), onRefreshSocial, Modifier.fillMaxWidth(),
                            style = ArcadeActionStyle.OUTLINE
                        )
                    }
                }
                ArcadeActionButton(
                    localized(TextKey.Close), onDismiss, Modifier.fillMaxWidth(),
                    style = ArcadeActionStyle.OUTLINE
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerAvatar(entry.displayName, size = 56.dp, avatarId = entry.avatarId,
                userId = entry.userId, frameId = entry.frameId)
            Text(localizedRankedTierName(rankedTierFor(entry.eloRating)), textAlign = TextAlign.Center)
            if (onUpgradeGuest == null && !isSelf) {
                (state.error ?: state.socialNotice)?.let { Text(it, textAlign = TextAlign.Center) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LeaderboardProfileMetric("Elo", formatArcadeNumber(entry.eloRating), Modifier.weight(1f))
                LeaderboardProfileMetric(localized(TextKey.Wins), formatArcadeNumber(entry.wins), Modifier.weight(1f))
                LeaderboardProfileMetric(localized(TextKey.WinRate), "$winRate%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LeaderboardProfileMetric(label: String, value: String, modifier: Modifier) {
    ArcadePanel(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
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
                label = localized(TextKey.Individual),
                selected = selectedIndex == 0,
                onClick = { onSelect(0) },
                modifier = Modifier.weight(1f).testTag("leaderboard_tab_players")
            )
            ArcadeLeaderboardTab(
                label = localized(TextKey.Clan),
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
    selectedMetric: PlayerLeaderboardMetric,
    onSelectMetric: (PlayerLeaderboardMetric) -> Unit,
    selectedPeriod: LeaderboardPeriod,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onSelectPlayer: (LeaderboardEntrySnapshot) -> Unit,
    onOpenSeasonHistory: () -> Unit
) {
    val leaderboard = state.leaderboard
    val displayedCurrent = when (selectedMetric) {
        PlayerLeaderboardMetric.ELO -> when (selectedPeriod) {
            LeaderboardPeriod.CURRENT_SEASON -> leaderboard?.seasonCurrentPlayer
            LeaderboardPeriod.PREVIOUS_SEASON -> leaderboard?.previousSeasonCurrentPlayer
            LeaderboardPeriod.ALL_TIME -> leaderboard?.currentPlayer
        }
        PlayerLeaderboardMetric.GOLD -> leaderboard?.currentGoldPlayer
        PlayerLeaderboardMetric.GEMS -> leaderboard?.currentGemPlayer
    }
    val displayedTop = when (selectedMetric) {
        PlayerLeaderboardMetric.ELO -> when (selectedPeriod) {
            LeaderboardPeriod.CURRENT_SEASON -> leaderboard?.seasonTopPlayers.orEmpty()
            LeaderboardPeriod.PREVIOUS_SEASON -> leaderboard?.previousSeasonTopPlayers.orEmpty()
            LeaderboardPeriod.ALL_TIME -> leaderboard?.topPlayers.orEmpty()
        }
        PlayerLeaderboardMetric.GOLD -> leaderboard?.topGoldPlayers.orEmpty()
        PlayerLeaderboardMetric.GEMS -> leaderboard?.topGemPlayers.orEmpty()
    }.take(LEADERBOARD_LIMIT)
    var visiblePlayerCount by remember(selectedPeriod, selectedMetric) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleTopPlayers = displayedTop.take(visiblePlayerCount)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("leaderboard_players_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "player_metric_filters") {
            PlayerLeaderboardMetricFilters(
                selectedMetric = selectedMetric,
                onSelect = onSelectMetric
            )
        }
        if (selectedMetric == PlayerLeaderboardMetric.ELO && selectedPeriod == LeaderboardPeriod.CURRENT_SEASON) {
            state.profile?.progression?.season?.let { season ->
                item(key = "season_progress") { SeasonProgressCard(season, serverClock = state.profileServerClock) }
            }
        }
        if (selectedMetric == PlayerLeaderboardMetric.ELO) {
            item(key = "leaderboard_hero") {
                ArcadeFeatureHero(
                    illustration = Res.drawable.arcade_leaderboard_trophy,
                    title = localized(TextKey.FameRace),
                    subtitle = localized(TextKey.FameRaceDescription),
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
                            LeaderboardPeriod.CURRENT_SEASON -> leaderboard?.seasonName ?: localized(TextKey.CurrentSeason)
                            LeaderboardPeriod.PREVIOUS_SEASON -> leaderboard?.previousSeasonName ?: localized(TextKey.PreviousSeason)
                            LeaderboardPeriod.ALL_TIME -> localized(TextKey.AllTime)
                        },
                        localized(TextKey.History)
                    ),
                    selectedIndex = 0,
                    onSelected = { index -> if (index == 1) onOpenSeasonHistory() },
                    enabled = { index -> index == 0 || state.profile != null },
                    itemTestTag = { index -> if (index == 1) "open_season_history" else "season_context_current" },
                    modifier = Modifier.testTag("season_context_tabs")
                )
            }
        }
        displayedCurrent?.let { current ->
            item(key = "current_player_title") {
                LeaderboardSectionTitle(localized(TextKey.YourPosition))
            }
            item(key = "current_player") {
                LeaderboardCard(
                    entry = current,
                    metric = selectedMetric,
                    highlighted = true,
                    onClick = { onSelectPlayer(current) },
                    testTag = "leaderboard_current_player"
                )
            }
        }
        if (selectedMetric == PlayerLeaderboardMetric.ELO && selectedPeriod == LeaderboardPeriod.PREVIOUS_SEASON) {
            state.profile?.progression?.latestSeasonReward
                ?.takeIf { it.seasonName == leaderboard?.previousSeasonName }
                ?.let { receipt ->
                    item(key = "season_reward_receipt") { SeasonRewardReceiptCard(receipt) }
                }
        }
        item(key = "top_players_title") {
            LeaderboardSectionTitle(localized(TextKey.TopPlayers))
        }
        if (displayedTop.isEmpty()) {
            item(key = "empty_players") {
                LeaderboardEmptyPanel(
                    when (selectedPeriod) {
                        LeaderboardPeriod.CURRENT_SEASON -> localized(TextKey.CurrentSeasonLeaderboardEmpty)
                        LeaderboardPeriod.PREVIOUS_SEASON -> localized(TextKey.PreviousSeasonLeaderboardEmpty)
                        LeaderboardPeriod.ALL_TIME -> localized(TextKey.AllTimeLeaderboardEmpty)
                    }
                )
            }
        } else {
            items(visibleTopPlayers, key = { it.playerCode }) { entry ->
                LeaderboardCard(
                    entry = entry,
                    metric = selectedMetric,
                    highlighted = entry.playerCode == displayedCurrent?.playerCode,
                    onClick = { onSelectPlayer(entry) }
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
private fun PlayerLeaderboardMetricFilters(
    selectedMetric: PlayerLeaderboardMetric,
    onSelect: (PlayerLeaderboardMetric) -> Unit
) {
    val metrics = PlayerLeaderboardMetric.entries
    ArcadeSegmentedControl(
        labels = listOf("Elo", localized(TextKey.Gold), localized(TextKey.Gems)),
        selectedIndex = metrics.indexOf(selectedMetric),
        onSelected = { onSelect(metrics[it]) },
        itemTestTag = { index ->
            when (metrics[index]) {
                PlayerLeaderboardMetric.ELO -> "leaderboard_player_metric_elo"
                PlayerLeaderboardMetric.GOLD -> "leaderboard_player_metric_gold"
                PlayerLeaderboardMetric.GEMS -> "leaderboard_player_metric_gems"
            }
        },
        modifier = Modifier.testTag("leaderboard_player_metric_filters")
    )
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
        labels = listOf(localized(TextKey.CurrentSeason), localized(TextKey.PreviousSeason), localized(TextKey.AllTime)),
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
private fun ClanLeaderboard(
    state: GameState,
    selectedMetric: ClanLeaderboardMetric,
    onSelectMetric: (ClanLeaderboardMetric) -> Unit,
    onOpenClan: (String) -> Unit
) {
    val leaderboard = state.leaderboard
    val displayedTopClans = when (selectedMetric) {
        ClanLeaderboardMetric.LEVEL -> leaderboard?.topLevelClans
            ?.takeIf { it.isNotEmpty() }
            ?: leaderboard?.topClans.orEmpty()
        ClanLeaderboardMetric.GOLD -> leaderboard?.topGoldClans.orEmpty()
        ClanLeaderboardMetric.GEMS -> leaderboard?.topGemClans.orEmpty()
    }.take(LEADERBOARD_LIMIT)
    val displayedCurrentClan = when (selectedMetric) {
        ClanLeaderboardMetric.LEVEL -> leaderboard?.currentLevelClan ?: leaderboard?.currentClan
        ClanLeaderboardMetric.GOLD -> leaderboard?.currentGoldClan
        ClanLeaderboardMetric.GEMS -> leaderboard?.currentGemClan
    }
    var visibleClanCount by remember(selectedMetric) { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleTopClans = displayedTopClans.take(visibleClanCount)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().testTag("leaderboard_clans_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "clan_metric_filters") {
            ClanLeaderboardMetricFilters(
                selectedMetric = selectedMetric,
                onSelect = onSelectMetric
            )
        }
        if (selectedMetric == ClanLeaderboardMetric.LEVEL) {
            item(key = "clan_leaderboard_hero") {
                ArcadeFeatureHero(
                    illustration = Res.drawable.arcade_leaderboard_trophy,
                    title = localized(TextKey.StrongestClans),
                    subtitle = localized(TextKey.StrongestClansDescription),
                    accent = ArcadePalette.Violet600
                )
            }
        }
        displayedCurrentClan?.let { currentClan ->
            item(key = "current_clan_title") {
                LeaderboardSectionTitle(localized(TextKey.YourClan))
            }
            item(key = "current_clan") {
                ClanLeaderboardCard(
                    entry = currentClan,
                    metric = selectedMetric,
                    highlighted = true,
                    onClick = { onOpenClan(currentClan.clanId) },
                    testTag = "leaderboard_current_clan",
                    logoTestTag = "leaderboard_current_clan_logo_${currentClan.clanId}"
                )
            }
        }
        item(key = "top_clans_title") {
            LeaderboardSectionTitle(localized(TextKey.TopClans))
        }
        if (displayedTopClans.isEmpty()) {
            item(key = "empty_clans") { LeaderboardEmptyPanel(localized(TextKey.ClanLeaderboardEmpty)) }
        } else {
            items(visibleTopClans, key = { it.clanId }) { entry ->
                ClanLeaderboardCard(
                    entry = entry,
                    metric = selectedMetric,
                    highlighted = entry.clanId == displayedCurrentClan?.clanId,
                    onClick = { onOpenClan(entry.clanId) }
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
private fun ClanLeaderboardMetricFilters(
    selectedMetric: ClanLeaderboardMetric,
    onSelect: (ClanLeaderboardMetric) -> Unit
) {
    val metrics = ClanLeaderboardMetric.entries
    ArcadeSegmentedControl(
        labels = listOf(
            localized(TextKey.Level, "level" to "").trim(),
            localized(TextKey.Gold),
            localized(TextKey.Gems)
        ),
        selectedIndex = metrics.indexOf(selectedMetric),
        onSelected = { onSelect(metrics[it]) },
        itemTestTag = { index ->
            when (metrics[index]) {
                ClanLeaderboardMetric.LEVEL -> "leaderboard_clan_metric_level"
                ClanLeaderboardMetric.GOLD -> "leaderboard_clan_metric_gold"
                ClanLeaderboardMetric.GEMS -> "leaderboard_clan_metric_gems"
            }
        },
        modifier = Modifier.testTag("leaderboard_clan_metric_filters")
    )
}

@Composable
private fun LeaderboardSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun LeaderboardEmptyPanel(message: String) {
    ArcadePanel(modifier = Modifier.fillMaxWidth(), accent = ArcadePalette.Blue300) {
        ArcadeEmptyState(
            illustration = Res.drawable.arcade_leaderboard_trophy,
            title = localized(TextKey.RankingAwaiting),
            description = message
        )
    }
}

@Composable
private fun ClanLeaderboardCard(
    entry: ClanLeaderboardEntrySnapshot,
    metric: ClanLeaderboardMetric,
    highlighted: Boolean,
    onClick: () -> Unit,
    testTag: String = "leaderboard_clan_${entry.clanId}",
    logoTestTag: String = "leaderboard_clan_logo_${entry.clanId}"
) {
    LeaderboardSurface(
        highlighted = highlighted,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcadeRankBadge(entry.rank)
            LeaderboardClanEmblem(entry.logoId, entry.clanName,
                Modifier.size(40.dp).testTag(logoTestTag))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.clanName, modifier = Modifier.testTag("${testTag}_name"),
                    fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (metric == ClanLeaderboardMetric.LEVEL) {
                        "${clanMetricPrimaryText(entry, metric)} - ${clanMetricSupportingText(entry, metric)}"
                    } else {
                        "${clanMetricPrimaryText(entry, metric)} ${clanMetricSupportingText(entry, metric)}"
                    },
                    modifier = Modifier.testTag("${testTag}_details"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) ArcadePalette.Gold500 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LeaderboardCard(
    entry: LeaderboardEntrySnapshot,
    metric: PlayerLeaderboardMetric,
    highlighted: Boolean,
    onClick: () -> Unit,
    testTag: String = "leaderboard_player_${entry.playerCode}"
) {
    val winRate = if (entry.totalMatches == 0) 0 else entry.wins.toLong() * 100 / entry.totalMatches
    LeaderboardSurface(
        highlighted = highlighted,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    ) {
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
                    entry.displayName,
                    modifier = Modifier.testTag("${testTag}_name"),
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    playerMetricText(entry, metric) + " · " + localized(
                        TextKey.LeaderboardPlayerSummary,
                        "tier" to localizedRankedTierName(rankedTierFor(entry.eloRating)),
                        "wins" to entry.wins,
                        "rate" to winRate
                    ),
                    modifier = Modifier.testTag("${testTag}_details"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LeaderboardClanEmblem(logoId: String?, clanName: String, modifier: Modifier = Modifier) {
    val description = localized(TextKey.ClanLogoForClan, "clan" to clanName)
    ClanLogoMark(logoId, modifier.semantics { contentDescription = description })
}

@Composable
private fun LeaderboardSurface(
    highlighted: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val border = BorderStroke(
        1.dp,
        if (highlighted) ArcadePalette.Gold500 else ArcadePalette.Blue300.copy(alpha = 0.42f)
    )
    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth().semantics { selected = highlighted },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = border,
            shadowElevation = if (highlighted) 4.dp else 2.dp,
            content = content
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().semantics { selected = highlighted },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = border,
            shadowElevation = if (highlighted) 4.dp else 2.dp,
            content = content
        )
    }
}

private enum class LeaderboardPeriod { CURRENT_SEASON, PREVIOUS_SEASON, ALL_TIME }

private const val LEADERBOARD_LIMIT = 30

private enum class PlayerLeaderboardMetric { ELO, GOLD, GEMS }

private enum class ClanLeaderboardMetric { LEVEL, GOLD, GEMS }

@Composable
private fun playerMetricLabel(metric: PlayerLeaderboardMetric): String = when (metric) {
    PlayerLeaderboardMetric.ELO -> "Elo"
    PlayerLeaderboardMetric.GOLD -> localized(TextKey.Gold)
    PlayerLeaderboardMetric.GEMS -> localized(TextKey.Gems)
}

private fun playerMetricValue(entry: LeaderboardEntrySnapshot, metric: PlayerLeaderboardMetric): Long = when (metric) {
    PlayerLeaderboardMetric.ELO -> entry.eloRating.toLong()
    PlayerLeaderboardMetric.GOLD -> entry.lifetimeEarnedGold
    PlayerLeaderboardMetric.GEMS -> entry.lifetimeEarnedGems
}

@Composable
private fun playerMetricText(entry: LeaderboardEntrySnapshot, metric: PlayerLeaderboardMetric): String =
    "${formatArcadeNumber(playerMetricValue(entry, metric))} ${playerMetricLabel(metric)}"

@Composable
private fun clanMetricPrimaryText(entry: ClanLeaderboardEntrySnapshot, metric: ClanLeaderboardMetric): String =
    when (metric) {
        ClanLeaderboardMetric.LEVEL -> localized(TextKey.Level, "level" to entry.level)
        ClanLeaderboardMetric.GOLD -> formatArcadeNumber(entry.donatedGold)
        ClanLeaderboardMetric.GEMS -> formatArcadeNumber(entry.donatedGems)
    }

@Composable
private fun clanMetricSupportingText(entry: ClanLeaderboardEntrySnapshot, metric: ClanLeaderboardMetric): String =
    when (metric) {
        ClanLeaderboardMetric.LEVEL -> "${formatArcadeNumber(entry.experiencePoints)} XP"
        ClanLeaderboardMetric.GOLD -> localized(TextKey.Gold)
        ClanLeaderboardMetric.GEMS -> localized(TextKey.Gems)
    }

private fun formatArcadeNumber(value: Int): String = value
    .toLong()
    .let(::formatArcadeNumber)

private fun formatArcadeNumber(value: Long): String = value
    .toString()
    .reversed()
    .chunked(3)
    .joinToString(".")
    .reversed()
