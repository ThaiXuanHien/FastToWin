package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedRankedTierName
import com.hienthai.fastowin.protocol.ClanLeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.LeaderboardEntrySnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_leaderboard_trophy
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.ArcadeActionButton
import com.hienthai.fastowin.ui.components.ArcadeActionStyle
import com.hienthai.fastowin.ui.components.ArcadeChoiceChip
import com.hienthai.fastowin.ui.components.ArcadeDialog
import com.hienthai.fastowin.ui.components.ArcadeEmptyState
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.components.ArcadeSegmentedControl
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.PlayerAvatar
import com.hienthai.fastowin.ui.components.SeasonRewardReceiptCard
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.localizedSeasonTimeRemaining
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.components.rememberClockMillis
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.A1Palette

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
        maxContentWidth = 640.dp,
        applySafeDrawingInsets = showBackButton,
        includeBottomSafeDrawingInset = showBackButton
    ) { contentModifier ->
        FastToWinPullRefresh(
            isRefreshing = state.isLeaderboardLoading,
            onRefresh = { if (!state.isLeaderboardLoading) onRefresh() },
            modifier = contentModifier
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
                if (showBackButton) {
                    // Mở từ màn khác (không có thanh tab): tiêu đề + nhãn mùa giống thiết kế, kèm nút quay lại.
                    FastToWinHeader(
                        title = localized(TextKey.Leaderboard),
                        gold = 0,
                        gems = 0,
                        unreadNotifications = 0,
                        onNotifications = onOpenNotifications,
                        onBack = onBack,
                        applySafeDrawingInset = false,
                        showBalances = false,
                        showNotifications = false,
                        actions = { LeaderboardSeasonTag(state, onOpenSeasonHistory) }
                    )
                }
                ArcadeSegmentedControl(
                    labels = listOf(localized(TextKey.Individual), localized(TextKey.Clan), localized(TextKey.Friends)),
                    selectedIndex = selectedMainTab,
                    onSelected = { selectedMainTab = it },
                    itemTestTag = {
                        when (it) {
                            0 -> "leaderboard_tab_players"
                            1 -> "leaderboard_tab_clans"
                            else -> "leaderboard_tab_friends"
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                if (state.isLeaderboardLoading && leaderboard == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = A1Palette.Blue2)
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
                        }
                    )
                } else if (selectedMainTab == 2) {
                    FriendLeaderboard(state = state, onSelectPlayer = { selectedPlayer = it })
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

/**
 * Nhãn mùa ở góc phải tiêu đề (".tag.k" của thiết kế): "Mùa 7 · còn 12 ngày". Bấm để xem lịch sử mùa.
 */
@Composable
fun LeaderboardSeasonTag(state: GameState, onOpenSeasonHistory: () -> Unit) {
    val season = state.profile?.progression?.season
    val name = state.leaderboard?.seasonName ?: season?.name ?: localized(TextKey.CurrentSeason)
    val remaining = season?.let {
        val now = rememberClockMillis(state.profileServerClock, tickMillis = 60_000L, deadlineMillis = it.endsAtEpochMillis)
        localizedSeasonTimeRemaining(it.endsAtEpochMillis, now)
    }
    val enabled = state.profile != null
    A1Tag(
        text = listOfNotNull(name, remaining).joinToString(" · "),
        tone = A1TagTone.INK,
        icon = Icons.Rounded.Timer,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable(enabled = enabled, onClick = onOpenSeasonHistory)
            .testTag("open_season_history")
    )
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

/** Một dòng/ô trên bảng: dùng chung cho người chơi và bang hội. */
private class BoardEntry(
    val key: String,
    val rank: Int,
    val name: String,
    val details: String,
    val value: String,
    val testTag: String,
    val highlighted: Boolean,
    val onClick: () -> Unit,
    val emblem: @Composable (Dp) -> Unit
)

@Composable
private fun PlayerLeaderboard(
    state: GameState,
    selectedMetric: PlayerLeaderboardMetric,
    onSelectMetric: (PlayerLeaderboardMetric) -> Unit,
    selectedPeriod: LeaderboardPeriod,
    onSelectPeriod: (LeaderboardPeriod) -> Unit,
    onSelectPlayer: (LeaderboardEntrySnapshot) -> Unit
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
    val toBoard: @Composable (LeaderboardEntrySnapshot, String?) -> BoardEntry = { entry, tag ->
        BoardEntry(
            key = entry.playerCode,
            rank = entry.rank,
            name = entry.displayName,
            // Như thiết kế: dưới tên chỉ ghi bậc; số trận thắng và tỉ lệ thắng xem trong hồ sơ khi bấm vào.
            details = localizedRankedTierName(rankedTierFor(entry.eloRating)),
            value = playerMetricValueText(entry, selectedMetric),
            testTag = tag ?: "leaderboard_player_${entry.playerCode}",
            highlighted = entry.playerCode == displayedCurrent?.playerCode,
            onClick = { onSelectPlayer(entry) },
            emblem = { size ->
                PlayerAvatar(entry.displayName, entry.avatarId, entry.userId, entry.frameId, size = size)
            }
        )
    }
    val boardEntries = visibleTopPlayers.map { toBoard(it, null) }
    val currentEntry = displayedCurrent?.let { toBoard(it, "leaderboard_current_player") }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("leaderboard_players_list"),
            contentPadding = PaddingValues(bottom = if (currentEntry != null) 128.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "player_filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val metrics = PlayerLeaderboardMetric.entries
                    Row(Modifier.testTag("leaderboard_player_metric_filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        metrics.forEach { metric ->
                            ArcadeChoiceChip(
                                label = playerMetricLabel(metric),
                                selected = metric == selectedMetric,
                                onClick = { onSelectMetric(metric) },
                                modifier = Modifier.testTag(
                                    when (metric) {
                                        PlayerLeaderboardMetric.ELO -> "leaderboard_player_metric_elo"
                                        PlayerLeaderboardMetric.GOLD -> "leaderboard_player_metric_gold"
                                        PlayerLeaderboardMetric.GEMS -> "leaderboard_player_metric_gems"
                                    }
                                )
                            )
                        }
                    }
                    if (selectedMetric == PlayerLeaderboardMetric.ELO) {
                        Box(Modifier.width(2.dp).height(28.dp).background(A1Palette.Line.copy(alpha = 0.25f)))
                        Row(Modifier.testTag("leaderboard_period_filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple(LeaderboardPeriod.CURRENT_SEASON, TextKey.CurrentSeason, "leaderboard_period_current"),
                                Triple(LeaderboardPeriod.PREVIOUS_SEASON, TextKey.PreviousSeason, "leaderboard_period_previous"),
                                Triple(LeaderboardPeriod.ALL_TIME, TextKey.AllTime, "leaderboard_period_all_time")
                            ).forEach { (period, key, tag) ->
                                ArcadeChoiceChip(
                                    label = localized(key),
                                    selected = period == selectedPeriod,
                                    onClick = { onSelectPeriod(period) },
                                    enabled = period != LeaderboardPeriod.PREVIOUS_SEASON || leaderboard?.previousSeasonName != null,
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }
                    }
                }
            }
            item(key = "player_board") {
                if (boardEntries.isEmpty()) {
                    LeaderboardEmptyPanel(
                        when {
                            selectedMetric != PlayerLeaderboardMetric.ELO -> localized(TextKey.AllTimeLeaderboardEmpty)
                            selectedPeriod == LeaderboardPeriod.CURRENT_SEASON -> localized(TextKey.CurrentSeasonLeaderboardEmpty)
                            selectedPeriod == LeaderboardPeriod.PREVIOUS_SEASON -> localized(TextKey.PreviousSeasonLeaderboardEmpty)
                            else -> localized(TextKey.AllTimeLeaderboardEmpty)
                        }
                    )
                } else {
                    LeaderboardBoard(boardEntries)
                }
            }
            if (displayedTop.size > visibleTopPlayers.size) {
                item(key = "leaderboard_players_load_more") {
                    ArcadeLoadMoreButton(
                        visibleItemCount = visibleTopPlayers.size,
                        totalItemCount = displayedTop.size,
                        onLoadMore = { visiblePlayerCount = nextArcadePageItemCount(visiblePlayerCount, displayedTop.size) },
                        testTag = "leaderboard_players_load_more"
                    )
                }
            }
            if (selectedMetric == PlayerLeaderboardMetric.ELO && selectedPeriod == LeaderboardPeriod.PREVIOUS_SEASON) {
                state.profile?.progression?.latestSeasonReward
                    ?.takeIf { it.seasonName == leaderboard?.previousSeasonName }
                    ?.let { receipt -> item(key = "season_reward_receipt") { SeasonRewardReceiptCard(receipt) } }
            }
        }
        currentEntry?.let {
            CurrentPositionCard(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
        }
    }
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
        ClanLeaderboardMetric.LEVEL -> leaderboard?.topLevelClans?.takeIf { it.isNotEmpty() } ?: leaderboard?.topClans.orEmpty()
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
    val toBoard: @Composable (ClanLeaderboardEntrySnapshot, String?, String?) -> BoardEntry = { entry, tag, logoTag ->
        val rowTag = tag ?: "leaderboard_clan_${entry.clanId}"
        BoardEntry(
            key = entry.clanId,
            rank = entry.rank,
            name = entry.clanName,
            details = clanMetricDetails(entry, selectedMetric),
            value = clanMetricValue(entry, selectedMetric),
            testTag = rowTag,
            highlighted = entry.clanId == displayedCurrentClan?.clanId,
            onClick = { onOpenClan(entry.clanId) },
            emblem = { size ->
                LeaderboardClanEmblem(
                    entry.logoId, entry.clanName,
                    Modifier.size(size).testTag(logoTag ?: "leaderboard_clan_logo_${entry.clanId}"),
                    entry.clanId, entry.logoRevision
                )
            }
        )
    }
    val boardEntries = visibleTopClans.map { toBoard(it, null, null) }
    val currentEntry = displayedCurrentClan?.let {
        toBoard(it, "leaderboard_current_clan", "leaderboard_current_clan_logo_${it.clanId}")
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("leaderboard_clans_list"),
            contentPadding = PaddingValues(bottom = if (currentEntry != null) 128.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "clan_filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("leaderboard_clan_metric_filters"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClanLeaderboardMetric.entries.forEach { metric ->
                        ArcadeChoiceChip(
                            label = when (metric) {
                                ClanLeaderboardMetric.LEVEL -> localized(TextKey.Level, "level" to "").trim()
                                ClanLeaderboardMetric.GOLD -> localized(TextKey.Gold)
                                ClanLeaderboardMetric.GEMS -> localized(TextKey.Gems)
                            },
                            selected = metric == selectedMetric,
                            onClick = { onSelectMetric(metric) },
                            modifier = Modifier.testTag(
                                when (metric) {
                                    ClanLeaderboardMetric.LEVEL -> "leaderboard_clan_metric_level"
                                    ClanLeaderboardMetric.GOLD -> "leaderboard_clan_metric_gold"
                                    ClanLeaderboardMetric.GEMS -> "leaderboard_clan_metric_gems"
                                }
                            )
                        )
                    }
                }
            }
            item(key = "clan_board") {
                if (boardEntries.isEmpty()) LeaderboardEmptyPanel(localized(TextKey.ClanLeaderboardEmpty))
                else LeaderboardBoard(boardEntries)
            }
            if (displayedTopClans.size > visibleTopClans.size) {
                item(key = "leaderboard_clans_load_more") {
                    ArcadeLoadMoreButton(
                        visibleItemCount = visibleTopClans.size,
                        totalItemCount = displayedTopClans.size,
                        onLoadMore = { visibleClanCount = nextArcadePageItemCount(visibleClanCount, displayedTopClans.size) },
                        testTag = "leaderboard_clans_load_more"
                    )
                }
            }
        }
        currentEntry?.let {
            CurrentPositionCard(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
        }
    }
}

/** Tab "Bạn bè": chính người chơi và bạn bè, xếp theo Elo (server gửi sẵn hạng trong nhóm). */
@Composable
private fun FriendLeaderboard(state: GameState, onSelectPlayer: (LeaderboardEntrySnapshot) -> Unit) {
    val friends = state.leaderboard?.friendPlayers.orEmpty()
    val selfId = state.profile?.userId
    val toBoard: @Composable (LeaderboardEntrySnapshot, String?) -> BoardEntry = { entry, tag ->
        BoardEntry(
            key = entry.playerCode,
            rank = entry.rank,
            name = entry.displayName,
            details = localizedRankedTierName(rankedTierFor(entry.eloRating)),
            value = entry.eloRating.toString(),
            testTag = tag ?: "leaderboard_friend_${entry.playerCode}",
            highlighted = entry.userId != null && entry.userId == selfId,
            onClick = { onSelectPlayer(entry) },
            emblem = { size -> PlayerAvatar(entry.displayName, entry.avatarId, entry.userId, entry.frameId, size = size) }
        )
    }
    val boardEntries = friends.map { toBoard(it, null) }
    val currentEntry = friends.firstOrNull { it.userId != null && it.userId == selfId }?.let { toBoard(it, "leaderboard_current_friend") }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("leaderboard_friends_list"),
            contentPadding = PaddingValues(bottom = if (currentEntry != null) 128.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "friend_board") {
                // Chỉ có mình mình (chưa có bạn) thì gợi ý kết bạn thay vì một bảng một người.
                if (friends.size <= 1) LeaderboardEmptyPanel(localized(TextKey.FriendLeaderboardEmpty))
                else LeaderboardBoard(boardEntries)
            }
        }
        if (friends.size > 1) currentEntry?.let {
            CurrentPositionCard(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
        }
    }
}

/** Thẻ bảng xếp hạng (".card.board"): bục 3 hạng đầu ở trên, các hạng sau là dòng gạch đứt. */
@Composable
private fun LeaderboardBoard(entries: List<BoardEntry>) {
    val podium = entries.filter { it.rank in 1..3 }.sortedBy { it.rank }
    val rows = entries - podium.toSet()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .a1Card()
            .padding(A1BoardInset)
            // Cắt theo góc bo bên trong để khối bục không tràn ra mép thẻ.
            .clip(RoundedCornerShape(19.dp))
    ) {
        if (podium.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFEAF6FF), Color.White)),
                        RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp)
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Thứ tự bục: hạng 2 – hạng 1 – hạng 3.
                listOf(2, 1, 3).forEach { rank ->
                    val entry = podium.firstOrNull { it.rank == rank }
                    Box(Modifier.weight(1f)) { if (entry != null) PodiumColumn(entry) }
                }
            }
            if (rows.isNotEmpty()) Box(Modifier.fillMaxWidth().height(3.dp).background(A1Palette.Line))
        }
        rows.forEachIndexed { index, entry ->
            BoardRow(entry, showDivider = index < rows.lastIndex)
        }
    }
}

private val A1BoardInset = 3.dp

@Composable
private fun PodiumColumn(entry: BoardEntry) {
    val first = entry.rank == 1
    val (crown, crownEdge) = when (entry.rank) {
        1 -> Color(0xFFFFC928) to Color(0xFF7A4D00)
        2 -> Color(0xFFDFE7F2) to Color(0xFF6B7C93)
        else -> Color(0xFFE59655) to Color(0xFF7A3D10)
    }
    val (blockColors, blockText, blockHeight) = when (entry.rank) {
        1 -> Triple(listOf(Color(0xFFFFF3A6), Color(0xFFFFC928), Color(0xFFE09A00)), Color(0xFF7A4D00), 88.dp)
        2 -> Triple(listOf(Color(0xFFF4F8FF), Color(0xFFB9C6D8)), Color(0xFF4D5D72), 64.dp)
        else -> Triple(listOf(Color(0xFFFFD3A8), Color(0xFFE59655)), Color(0xFF6A3300), 48.dp)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
            .semantics(mergeDescendants = true) { selected = entry.highlighted }
            .testTag(entry.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Crown(fill = crown, edge = crownEdge, size = if (first) 42.dp else 34.dp)
        entry.emblem(if (first) 104.dp else 78.dp)
        Text(
            entry.name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
            color = A1Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("${entry.testTag}_name")
        )
        Text(
            entry.value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            fontWeight = FontWeight.ExtraBold,
            color = A1Palette.Ink,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("${entry.testTag}_value")
        )
        // Bậc của người chơi chỉ cho trình đọc màn hình; trên bục chỉ hiện điểm như thiết kế.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .semantics { text = androidx.compose.ui.text.AnnotatedString(entry.details) }
                .testTag("${entry.testTag}_details")
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .background(Brush.verticalGradient(blockColors), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .drawBehind {
                    // Viền 3dp ba cạnh (không có cạnh dưới) + vệt sáng phía trên.
                    val w = 3.dp.toPx()
                    val r = 14.dp.toPx()
                    val path = Path().apply {
                        moveTo(w / 2, size.height)
                        lineTo(w / 2, r)
                        quadraticTo(w / 2, w / 2, r, w / 2)
                        lineTo(size.width - r, w / 2)
                        quadraticTo(size.width - w / 2, w / 2, size.width - w / 2, r)
                        lineTo(size.width - w / 2, size.height)
                    }
                    drawLine(Color.White.copy(alpha = 0.38f), Offset(r, w + 2.dp.toPx()), Offset(size.width - r, w + 2.dp.toPx()), strokeWidth = 4.dp.toPx())
                    drawPath(path, A1Palette.Line, style = Stroke(width = w, join = StrokeJoin.Round))
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                entry.rank.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                color = blockText,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Vương miện vẽ tay: 3 đỉnh tròn + đai, màu theo hạng (vàng / bạc / đồng). */
@Composable
private fun Crown(fill: Color, edge: Color, size: Dp) {
    Canvas(Modifier.size(width = size, height = size * 0.8f)) {
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * 0.12f, h * 0.74f)
            lineTo(w * 0.08f, h * 0.26f)
            lineTo(w * 0.32f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.68f, h * 0.48f)
            lineTo(w * 0.92f, h * 0.26f)
            lineTo(w * 0.88f, h * 0.74f)
            close()
        }
        val stroke = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round)
        drawPath(body, fill)
        drawPath(body, edge, style = stroke)
        val bandTop = h * 0.76f
        drawRoundRect(fill, Offset(w * 0.12f, bandTop), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.18f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawRoundRect(edge, Offset(w * 0.12f, bandTop), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.18f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
        listOf(0.08f to 0.24f, 0.50f to 0.12f, 0.92f to 0.24f).forEach { (x, y) ->
            drawCircle(fill, radius = w * 0.07f, center = Offset(w * x, h * y))
            drawCircle(edge, radius = w * 0.07f, center = Offset(w * x, h * y), style = stroke)
        }
    }
}

/** Dòng hạng 4+ (".lr"): số hạng, avatar 56, tên + bậc, điểm Baloo bên phải, gạch đứt phân cách. */
@Composable
private fun BoardRow(entry: BoardEntry, showDivider: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (entry.highlighted) A1Palette.Highlight else Color.Transparent)
            .clickable(onClick = entry.onClick)
            .semantics(mergeDescendants = true) { selected = entry.highlighted }
            .then(
                if (showDivider) Modifier.drawBehind {
                    drawLine(
                        A1Palette.SurfaceAlt2,
                        Offset(0f, size.height - 1.dp.toPx()),
                        Offset(size.width, size.height - 1.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    )
                } else Modifier
            )
            .padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            .testTag(entry.testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            entry.rank.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = A1Palette.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp)
        )
        entry.emblem(56.dp)
        EntryNameBlock(entry, Modifier.weight(1f), detailColor = A1Palette.Muted)
        Text(
            entry.value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
            color = A1Palette.Ink,
            maxLines = 1,
            modifier = Modifier.testTag("${entry.testTag}_value")
        )
    }
}

@Composable
private fun EntryNameBlock(entry: BoardEntry, modifier: Modifier, detailColor: Color, namePrefix: String = "") {
    Column(modifier = modifier) {
        Text(
            namePrefix + entry.name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 19.sp),
            color = A1Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("${entry.testTag}_name")
        )
        Text(
            entry.details,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = detailColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("${entry.testTag}_details")
        )
    }
}

/** Thẻ vàng "vị trí của bạn" ghim ở đáy danh sách: "#128", avatar, "Bạn · tên", điểm. */
@Composable
private fun CurrentPositionCard(entry: BoardEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .a1Card(background = A1Palette.Highlight, border = A1Palette.YellowBorder)
            .clickable(onClick = entry.onClick)
            .semantics(mergeDescendants = true) { selected = true }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(entry.testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "#${entry.rank}",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
            color = A1Palette.YellowBorder,
            maxLines = 1
        )
        entry.emblem(56.dp)
        EntryNameBlock(
            entry,
            Modifier.weight(1f),
            detailColor = Color(0xFF7A4D00),
            namePrefix = localized(TextKey.You) + " · "
        )
        Text(
            entry.value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
            color = A1Palette.Ink,
            maxLines = 1,
            modifier = Modifier.testTag("${entry.testTag}_value")
        )
    }
}

@Composable
private fun LeaderboardEmptyPanel(message: String) {
    ArcadePanel(modifier = Modifier.fillMaxWidth()) {
        ArcadeEmptyState(
            illustration = Res.drawable.arcade_leaderboard_trophy,
            title = localized(TextKey.RankingAwaiting),
            description = message
        )
    }
}

@Composable
private fun LeaderboardClanEmblem(logoId: String?, clanName: String, modifier: Modifier = Modifier,
    clanId: String? = null, logoRevision: String? = null) {
    val description = localized(TextKey.ClanLogoForClan, "clan" to clanName)
    ClanLogoMark(logoId, modifier.semantics { contentDescription = description }, clanId, logoRevision)
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

/** Giá trị ở cột phải: Elo viết liền như thiết kế ("2104"), Vàng/Gem có dấu chấm nghìn. */
private fun playerMetricValueText(entry: LeaderboardEntrySnapshot, metric: PlayerLeaderboardMetric): String = when (metric) {
    PlayerLeaderboardMetric.ELO -> entry.eloRating.toString()
    PlayerLeaderboardMetric.GOLD -> formatArcadeNumber(entry.lifetimeEarnedGold)
    PlayerLeaderboardMetric.GEMS -> formatArcadeNumber(entry.lifetimeEarnedGems)
}

@Composable
private fun clanMetricDetails(entry: ClanLeaderboardEntrySnapshot, metric: ClanLeaderboardMetric): String = when (metric) {
    ClanLeaderboardMetric.LEVEL -> "${localized(TextKey.Level, "level" to entry.level)} · ${formatArcadeNumber(entry.experiencePoints)} XP"
    ClanLeaderboardMetric.GOLD, ClanLeaderboardMetric.GEMS -> localized(TextKey.Level, "level" to entry.level)
}

private fun clanMetricValue(entry: ClanLeaderboardEntrySnapshot, metric: ClanLeaderboardMetric): String = when (metric) {
    ClanLeaderboardMetric.LEVEL -> formatArcadeNumber(entry.experiencePoints)
    ClanLeaderboardMetric.GOLD -> formatArcadeNumber(entry.donatedGold)
    ClanLeaderboardMetric.GEMS -> formatArcadeNumber(entry.donatedGems)
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
