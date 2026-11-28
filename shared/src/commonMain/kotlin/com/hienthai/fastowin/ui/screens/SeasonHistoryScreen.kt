package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.localizedNetworkText
import com.hienthai.fastowin.localization.localizedRankedTierName
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.protocol.SeasonHistoryEntrySnapshot
import com.hienthai.fastowin.protocol.SeasonSnapshot
import com.hienthai.fastowin.protocol.SeasonTierRewardSnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.state.GameState
import com.hienthai.fastowin.ui.components.A1CoinIcon
import com.hienthai.fastowin.ui.components.A1GemIcon
import com.hienthai.fastowin.ui.components.A1Tag
import com.hienthai.fastowin.ui.components.A1TagTone
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.ArcadeLoadMoreButton
import com.hienthai.fastowin.ui.components.DEFAULT_ARCADE_PAGE_SIZE
import com.hienthai.fastowin.ui.components.FastToWinHeader
import com.hienthai.fastowin.ui.components.FastToWinPullRefresh
import com.hienthai.fastowin.ui.components.SEASON_TIMER_REFRESH_MILLIS
import com.hienthai.fastowin.ui.components.SystemBackHandler
import com.hienthai.fastowin.ui.components.a1Card
import com.hienthai.fastowin.ui.components.localizedSeasonTimeRemaining
import com.hienthai.fastowin.ui.components.nextArcadePageItemCount
import com.hienthai.fastowin.ui.components.nextRankedTier
import com.hienthai.fastowin.ui.components.rememberClockMillis
import com.hienthai.fastowin.ui.format.fullNumber
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.A1Palette

/*
 * Màn "Lịch sử mùa" theo thiết kế A1 (bảng "Tài khoản – màn con 2"):
 * thẻ mùa đang diễn ra (khiên bậc, Elo, phân hạng) và mỗi mùa trước là một thẻ nhỏ.
 */

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
    var visibleSeasonCount by remember { mutableStateOf(DEFAULT_ARCADE_PAGE_SIZE) }
    val visibleHistory = history.take(visibleSeasonCount)
    ArcadeBackdrop(modifier = modifier.fillMaxSize().testTag("season_history_screen")) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                FastToWinHeader(
                    title = localized(TextKey.SeasonHistoryTitle),
                    gold = 0,
                    gems = 0,
                    unreadNotifications = 0,
                    onNotifications = onOpenNotifications,
                    onBack = onBack,
                    showBalances = false,
                    showNotifications = false
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
                        // Cách mép 20dp như thiết kế (ResponsiveScreen đã chừa 16dp).
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        state.profile?.progression?.season?.let { season ->
                            item(key = "season_progress") {
                                A1CurrentSeasonCard(season, state)
                            }
                        }
                        when {
                            state.isProfileLoading && state.profile == null -> item(key = "season_history_loading") {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                            history.isEmpty() -> item(key = "season_history_empty") {
                                Column(
                                    Modifier.fillMaxWidth().a1SoftCard().padding(horizontal = 18.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        localized(TextKey.NoSeasonHistory),
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = A1Palette.Ink
                                    )
                                    Text(
                                        localized(TextKey.NoSeasonHistoryDescription),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = A1Palette.Muted
                                    )
                                }
                            }
                            else -> {
                                items(visibleHistory, key = { it.seasonNumber }) { season -> PastSeasonCard(season) }
                                item(key = "season_history_load_more") {
                                    ArcadeLoadMoreButton(
                                        visibleItemCount = visibleHistory.size,
                                        totalItemCount = history.size,
                                        onLoadMore = { visibleSeasonCount = nextArcadePageItemCount(visibleSeasonCount, history.size) },
                                        testTag = "season_history_load_more"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Màu khiên theo bậc (".shield.tier-*" của thiết kế). */
private fun RankedTier.shieldColors(): List<Color> = when (this) {
    RankedTier.BRONZE -> listOf(Color(0xFFFFD3A8), Color(0xFFE59655))
    RankedTier.SILVER -> listOf(Color(0xFFF4F8FF), Color(0xFFB9C6D8))
    RankedTier.GOLD -> listOf(Color(0xFFFFF3A6), Color(0xFFFFC928), Color(0xFFE09A00))
    RankedTier.PLATINUM -> listOf(Color(0xFFE6FFF8), Color(0xFF8FE3D0), Color(0xFF3FB8A0))
    RankedTier.DIAMOND -> listOf(Color(0xFFD8F7FF), Color(0xFF5FD0FF), Color(0xFF2A7DE0))
    RankedTier.MASTER -> listOf(Color(0xFFE9DEFF), Color(0xFFB48CFF), Color(0xFF7A4DFF))
    RankedTier.CHALLENGER -> listOf(Color(0xFFFFE0D6), Color(0xFFFF8A7A), Color(0xFFE03A3A))
}

private fun RankedTier.tagTone(): A1TagTone = when (this) {
    RankedTier.BRONZE -> A1TagTone.RED
    RankedTier.SILVER, RankedTier.PLATINUM, RankedTier.DIAMOND -> A1TagTone.BLUE
    RankedTier.GOLD -> A1TagTone.YELLOW
    RankedTier.MASTER, RankedTier.CHALLENGER -> A1TagTone.PURPLE
}

@Composable
private fun TierShield(tier: RankedTier, width: Dp, height: Dp) {
    Box(
        Modifier
            .size(width, height)
            .clip(ClanShieldShape)
            .background(Brush.verticalGradient(tier.shieldColors()), ClanShieldShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFF5A2B00).copy(alpha = 0.85f), modifier = Modifier.size(width * 0.42f))
    }
}

/** Thẻ mùa đang diễn ra: khiên bậc, nhãn "Mùa · còn …", "Bậc · Elo", phân hạng và nút xem thưởng các bậc. */
@Composable
private fun A1CurrentSeasonCard(season: SeasonSnapshot, state: GameState) {
    var rewardsExpanded by remember(season.name) { mutableStateOf(false) }
    val nowMillis = rememberClockMillis(state.profileServerClock, tickMillis = SEASON_TIMER_REFRESH_MILLIS, deadlineMillis = season.endsAtEpochMillis)
    val isPlacement = season.placementMatchesPlayed < season.placementMatchesRequired
    val tier = rankedTierFor(season.rating)
    val nextTier = nextRankedTier(season.rating)
    val progressDescription = when {
        isPlacement -> localized(TextKey.SeasonProgressPlacement, "played" to season.placementMatchesPlayed, "required" to season.placementMatchesRequired)
        nextTier == null -> localized(TextKey.HighestTierReached)
        else -> localized(TextKey.EloToNextTier, "count" to (nextTier.minimumRating - season.rating).coerceAtLeast(0), "tier" to localizedRankedTierName(nextTier))
    }
    val placementLine = localized(
        TextKey.SeasonPlacementLine,
        "played" to season.placementMatchesPlayed.coerceAtMost(season.placementMatchesRequired),
        "required" to season.placementMatchesRequired
    )
    // Thiết kế chỉ ghi "Phân hạng 10/10 trận"; mức Elo còn thiếu vẫn đọc được qua stateDescription.
    val detailLine = placementLine
    val peakTier = rankedTierFor(season.peakRating)
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("season_progress_card")
            .a1Card(background = Color.Transparent)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    0f to Color.White, 0.55f to Color.White, 1f to A1Palette.Highlight,
                    start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TierShield(tier, 64.dp, 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                A1Tag(
                    localized(
                        TextKey.SeasonOngoingTag,
                        "season" to localizedNetworkText(season.nameKey, season.nameArgs, season.name),
                        "time" to localizedSeasonTimeRemaining(season.endsAtEpochMillis, nowMillis).replaceFirstChar { it.lowercase() }
                    ),
                    A1TagTone.INK
                )
                Text(
                    if (isPlacement) localized(TextKey.PlacementPending) else "${localizedRankedTierName(tier)} · ${season.rating}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 26.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = A1Palette.Ink
                )
                Text(
                    detailLine,
                    modifier = Modifier.testTag("season_progress").semantics { stateDescription = progressDescription },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted
                )
            }
        }
        if (season.tierRewards.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("season_rewards_toggle")
                    .clickable(role = Role.Button) { rewardsExpanded = !rewardsExpanded }
                    .a1DashedDividerTop()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    localized(if (rewardsExpanded) TextKey.HideTierRewards else TextKey.ViewTierRewards),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = A1Palette.Blue2
                )
                Icon(
                    if (rewardsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = A1Palette.Blue2
                )
            }
            if (rewardsExpanded) {
                Column(Modifier.fillMaxWidth().testTag("season_rewards")) {
                    val rewards = season.tierRewards.sortedBy { it.tier.minimumRating }
                    rewards.forEachIndexed { index, reward ->
                        TierRewardRow(
                            reward = reward,
                            reached = !isPlacement && season.peakRating >= reward.tier.minimumRating,
                            current = !isPlacement && reward.tier == peakTier,
                            showDivider = index < rewards.lastIndex
                        )
                    }
                }
            }
        }
    }
}

/** Đường gạch đứt phía trên (ngăn phần thưởng các bậc với phần đầu thẻ). */
private fun Modifier.a1DashedDividerTop(): Modifier = this.drawBehind {
    drawLine(
        A1Palette.SurfaceAlt2,
        Offset(3.dp.toPx(), 1.dp.toPx()),
        Offset(size.width - 3.dp.toPx(), 1.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    )
}

@Composable
private fun TierRewardRow(reward: SeasonTierRewardSnapshot, reached: Boolean, current: Boolean, showDivider: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("season_reward:${reward.tier.name}")
            .a1DashedDivider(showDivider)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TierShield(reward.tier, 30.dp, 34.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    localizedRankedTierName(reward.tier),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = A1Palette.Ink
                )
                if (current) A1Tag(localized(TextKey.ReachedHighestTier), A1TagTone.GREEN)
            }
            if (!current) {
                Text(
                    if (reached) localized(TextKey.PassedTier) else localized(TextKey.TierStartsAtElo, "elo" to reward.tier.minimumRating),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = A1Palette.Muted
                )
            }
            reward.cosmetic?.let { cosmetic ->
                Text(
                    cosmetic.name,
                    modifier = Modifier.testTag("season_reward_cosmetic:${reward.tier.name}"),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = A1Palette.Purple2
                )
            }
            RewardInline(gold = reward.gold, gems = reward.gems, cosmeticLabel = null, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** "◆ 40 Gem + Khung mùa" / "● 3.000 Vàng". */
@Composable
private fun RewardInline(gold: Int, gems: Int, cosmeticLabel: String?, modifier: Modifier = Modifier) {
    val parts = buildList {
        if (gems > 0) add("${fullNumber(gems.toLong())} ${localized(TextKey.Gems)}")
        if (gold > 0) add("${fullNumber(gold.toLong())} ${localized(TextKey.Gold)}")
        cosmeticLabel?.let { add(it) }
    }
    if (parts.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            gems > 0 -> A1GemIcon(16.dp)
            gold > 0 -> A1CoinIcon(16.dp)
        }
        Text(
            parts.joinToString(" + "),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            fontWeight = FontWeight.Bold,
            color = A1Palette.Ink
        )
    }
}

/** Một mùa đã qua (".card.soft"): tên mùa + nhãn bậc, Elo cao nhất / hạng cuối, phần thưởng. */
@Composable
private fun PastSeasonCard(season: SeasonHistoryEntrySnapshot) {
    val ranked = season.placementMatchesPlayed >= season.placementMatchesRequired
    val reward = season.reward
    val seasonName = localizedNetworkText(season.seasonNameKey, season.seasonNameArgs, season.seasonName)
    val tier = reward?.tier ?: rankedTierFor(season.peakRating)
    val tierName = if (ranked) localizedRankedTierName(tier) else localized(TextKey.Unranked)
    val peakLabel = localized(TextKey.SeasonPeakEloShort, "elo" to season.peakRating)
    val rankLabel = season.finalRank?.let { localized(TextKey.SeasonFinalRankShort, "rank" to fullNumber(it.toLong())) }
        ?: localized(TextKey.NotRanked)
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("season_history_item:${season.seasonNumber}")
            .semantics { stateDescription = "$seasonName, $tierName, $peakLabel" }
            .a1SoftCard()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                seasonName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
                fontWeight = FontWeight.ExtraBold,
                color = A1Palette.Ink
            )
            A1Tag(tierName, if (ranked) tier.tagTone() else A1TagTone.BLUE)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(peakLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
            Text(rankLabel, textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
        }
        if (reward != null) {
            RewardInline(
                gold = reward.gold,
                gems = reward.gems,
                cosmeticLabel = reward.cosmetic?.let {
                    localized(if (it.type == CosmeticType.TITLE) TextKey.SeasonTitleShort else TextKey.SeasonFrameShort)
                },
                modifier = Modifier.testTag("season_history_reward:${season.seasonNumber}")
            )
        } else {
            Text(
                if (ranked) localized(TextKey.SeasonRewardProcessing)
                else localized(TextKey.NoPlacementReward, "count" to season.placementMatchesRequired),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = A1Palette.Muted
            )
        }
    }
}
