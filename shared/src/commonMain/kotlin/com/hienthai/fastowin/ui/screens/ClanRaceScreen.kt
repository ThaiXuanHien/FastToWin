package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.ClanRaceUiState
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.A1Palette
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Màn Đua bang (A1): mỗi bang là một làn đua, khiên bang chạy tới vạch đích theo điểm tuần; thẻ phần thưởng
 * Top 1/2/3; đóng góp của thành viên và lịch sử tuần. Kéo xuống để tải lại.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanRaceScreen(
    state: ClanRaceUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onStandingsPage: (Int) -> Unit = {},
    onHistoryPage: (Int) -> Unit = {},
    isGuest: Boolean = false
) {
    SystemBackHandler(onBack = onBack)
    val current = state.current
    Scaffold(containerColor = Color.Transparent, topBar = {
        FastToWinHeader(localized(TextKey.ClanTabRace), 0, 0, 0, {}, onBack = onBack,
            showBalances = false, showNotifications = false,
            actions = {
                if (current?.state == ClanRaceState.ACTIVE) {
                    val minutes = ((current.endEpochMillis - current.serverNowEpochMillis).coerceAtLeast(0)) / 60_000
                    A1Tag(
                        localized(TextKey.ClanRaceTimeLeftShort, "days" to minutes / 1440, "hours" to (minutes % 1440) / 60),
                        A1TagTone.INK,
                        large = true,
                        modifier = Modifier.testTag("race_time_left")
                    )
                }
            })
    }) { padding ->
        ResponsiveScreen(Modifier.padding(padding), maxContentWidth = 640.dp, applySafeDrawingInsets = false) { modifier ->
            FastToWinPullRefresh(isRefreshing = state.isLoading, onRefresh = { if (!state.isLoading && !isGuest) onRefresh() }, modifier = modifier) {
            LazyColumn(Modifier.fillMaxSize().testTag("clan_race"), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item("refresh") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.isLoading) Text(localized(TextKey.Loading), color = A1Palette.Muted,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        state.loadError?.let {
                            Text(localized(when (it) {
                                "UNAUTHORIZED" -> TextKey.GiftErrorUnauthorized
                                "FORBIDDEN" -> TextKey.ClanRaceForbidden
                                "CONFLICT" -> TextKey.ClanRaceConflict
                                else -> TextKey.ClanRaceError
                            }), color = A1Palette.RedText, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("race_error"))
                            ArcadeActionButton(
                                label = localized(TextKey.ClanRaceRefresh),
                                onClick = onRefresh,
                                enabled = !state.isLoading && !isGuest,
                                style = ArcadeActionStyle.OUTLINE,
                                compact = true,
                                modifier = Modifier.fillMaxWidth().testTag("race_refresh")
                            )
                        }
                    }
                }
                if (isGuest) item("guest") { Text(localized(TextKey.GiftMailboxGuest), color = A1Palette.Ink) }
                if (current != null) {
                    val standings = state.standings
                    item("week") {
                        RaceCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    localized(TextKey.ClanRaceTrackTitle, "week" to current.weekStart),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    color = A1Palette.Ink,
                                    modifier = Modifier.weight(1f).semantics { heading() }
                                )
                                A1Tag(localized(raceStatus(current.state)), A1TagTone.YELLOW, modifier = Modifier.testTag("race_status"))
                            }
                            Text(localized(TextKey.ClanRaceTrackHint), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
                            Text(localized(TextKey.ClanRaceCutoff, "time" to raceTime(current.endEpochMillis)),
                                style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
                            if (current.state != ClanRaceState.INACTIVE) {
                                standings?.poolId?.let { A1Tag(racePoolLabel(it), A1TagTone.BLUE) }
                                val rows = standings?.items.orEmpty()
                                if (rows.isEmpty()) {
                                    Text(localized(TextKey.ClanRaceEmpty), color = A1Palette.Muted)
                                }
                                val maxPoints = (rows + listOfNotNull(standings?.ownClan)).maxOfOrNull { it.points.toDoubleOrNull() ?: 0.0 } ?: 0.0
                                rows.forEach { row -> RaceLane(row, row.clanId == standings?.ownClan?.clanId, maxPoints) }
                                standings?.ownClan?.takeIf { own -> rows.none { it.clanId == own.clanId } }?.let { own ->
                                    RaceLane(own, own = true, maxPoints = maxPoints)
                                }
                                if (current.config?.poolingVersion == 1) {
                                    Text(localized(TextKey.ClanRacePoolRules), style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
                                }
                                standings?.let { page -> RacePagination(page.page, page.pageSize, page.total, state.isLoading, onStandingsPage) }
                            }
                        }
                    }
                    // Chỉ cấu hình đã chốt của server mới có phần thưởng.
                    current.config?.takeIf { current.state != ClanRaceState.INACTIVE }?.let { config ->
                        item("prizes") {
                            val ownRank = standings?.ownClan?.rank
                            RaceCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(localized(TextKey.ClanRacePrizes), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                        color = A1Palette.Ink, modifier = Modifier.weight(1f).semantics { heading() })
                                }
                                Text(localized(TextKey.ClanRacePerMember), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold, color = A1Palette.Muted)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    config.rewards.forEachIndexed { index, reward ->
                                        RacePrizeTile(index + 1, reward, highlighted = ownRank == index + 1,
                                            modifier = Modifier.weight(1f).testTag("race_prize_${index + 1}"))
                                    }
                                }
                                Text(localized(TextKey.ClanRaceEligibility), style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
                            }
                        }
                    }
                    if (current.state != ClanRaceState.INACTIVE) {
                        state.contributions?.takeIf { it.clanId != null }?.let { contributions ->
                            item("contributions") {
                                RaceCard {
                                    Text(localized(TextKey.ClanRaceContributions), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                        color = A1Palette.Ink, modifier = Modifier.semantics { heading() })
                                    if (contributions.items.isEmpty()) Text(localized(TextKey.ClanRaceEmpty), color = A1Palette.Muted)
                                    contributions.items.forEach { member -> RaceContributionRow(member) }
                                }
                            }
                        }
                    }
                }
                state.history?.let { history ->
                    item("history_heading") {
                        Text(localized(TextKey.ClanRaceHistory), style = MaterialTheme.typography.titleLarge, color = A1Palette.Ink,
                            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
                    }
                    if (history.items.isEmpty()) item("history_empty") { Text(localized(TextKey.ClanRaceEmpty), color = A1Palette.Muted) }
                    items(history.items, key = { "history:${it.weekStart}" }) { week -> RaceHistory(week) }
                    item("history_pages") { RacePagination(history.page, history.pageSize, history.total, state.isLoading, onHistoryPage) }
                }
                state.lastRefreshedAtEpochMillis?.let {
                    item("updated") {
                        Text(localized(TextKey.ClanRaceUpdated, "time" to raceTime(it)), style = MaterialTheme.typography.bodySmall,
                            color = A1Palette.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable private fun RaceCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** Một làn đua: "hạng · tên" và điểm ở trên; làn sọc có vạch đích, khiên bang chạy theo tỉ lệ điểm. */
@Composable private fun RaceLane(row: ClanRaceStanding, own: Boolean, maxPoints: Double) {
    val progress = if (maxPoints <= 0.0) 0f else ((row.points.toDoubleOrNull() ?: 0.0) / maxPoints).toFloat().coerceIn(0f, 1f)
    val laneBase = if (own) Color(0xFFFFF3C4) else Color(0xFFDCE8F6)
    val laneStripe = if (own) Color(0xFFFFE9A3) else Color(0xFFCCDCEF)
    val edge = if (own) A1Palette.YellowBorder else A1Palette.Line
    Column(
        Modifier.fillMaxWidth().then(if (own) Modifier.semantics { selected = true } else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${row.rank} · ${row.name}" + if (own) " " + localized(TextKey.ClanRaceYourClanTag) else "",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = if (own) A1Palette.YellowBorder else A1Palette.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                row.points,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = A1Palette.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 140.dp).testTag("race_rank_${row.clanId}")
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(laneBase)
                .drawBehind {
                    // Sọc dọc và vạch đích ca-rô ở cuối làn.
                    val stripe = 18.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawRect(laneStripe, topLeft = Offset(x, 0f), size = Size(stripe / 2, size.height))
                        x += stripe
                    }
                    val flagW = 14.dp.toPx()
                    val left = size.width - flagW - 10.dp.toPx()
                    val cell = size.height / 6
                    for (i in 0 until 6) {
                        drawRect(if (i % 2 == 0) A1Palette.Line else Color.White, topLeft = Offset(left, i * cell), size = Size(flagW, cell))
                    }
                }
                .border(3.dp, edge, RoundedCornerShape(14.dp))
        ) {
            val emblem = 40.dp
            val travel = (maxWidth - emblem - 34.dp).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .offset(x = travel * progress)
                    .align(Alignment.CenterStart)
                    .size(width = emblem, height = 44.dp)
            ) {
                ClanLogoMark(row.logoId, Modifier.fillMaxSize())
            }
        }
    }
}

/** Ô phần thưởng hạng: nhãn Top N, phần thưởng (gem, vàng, XP); ô của hạng bang mình đang giữ được tô vàng. */
@Composable private fun RacePrizeTile(rank: Int, reward: ClanRaceReward, highlighted: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .a1Card(radius = 18.dp, depth = 3.dp, background = if (highlighted) A1Palette.Highlight else Color.White,
                border = if (highlighted) A1Palette.YellowBorder else A1Palette.Line)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        A1Tag(
            localized(TextKey.ClanRaceTopRank, "rank" to rank),
            when (rank) {
                1 -> A1TagTone.YELLOW
                2 -> A1TagTone.INK
                else -> A1TagTone.BLUE
            },
            modifier = Modifier.semantics { heading() }
        )
        RaceReward(reward, centered = true)
    }
}

@Composable private fun RaceContributionRow(member: ClanRaceContribution) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerAvatar(member.displayName ?: localized(TextKey.Player), avatarId = null, userId = member.userId, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.displayName ?: localized(TextKey.Player), style = MaterialTheme.typography.titleSmall, color = A1Palette.Ink)
            Text(
                localized(TextKey.ClanRaceMission, "points" to raceUnitsText(member.missionUnits)) + " · " +
                    localized(TextKey.ClanRaceDonation, "points" to raceUnitsText(member.donationUnits)),
                style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted
            )
            Text(localized(when {
                !member.currentMember -> TextKey.ClanRaceFormer
                member.eligible -> TextKey.ClanRaceEligible
                else -> TextKey.ClanRaceIneligible
            }), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                color = if (member.eligible) A1Palette.GreenText else A1Palette.Muted,
                modifier = Modifier.testTag("race_eligibility_${member.tenureId}"))
        }
        Text(member.points, style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink)
    }
}

@Composable internal fun racePoolLabel(id: String): String = localized(
    if (id.startsWith("starter-")) TextKey.ClanRacePoolStarter else TextKey.ClanRacePoolStrength,
    "number" to id.substringAfterLast('-'))

@Composable private fun RaceHistory(week: ClanRaceCurrent) {
    RaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(localized(TextKey.ClanRaceWeek, "week" to week.weekStart), style = MaterialTheme.typography.titleMedium,
                color = A1Palette.Ink, modifier = Modifier.weight(1f).semantics { heading() })
            A1Tag(localized(if (week.settlement?.state == ClanRaceSettlementState.FAILED) TextKey.ClanRaceSettlementFailed
                else raceStatus(week.state)), A1TagTone.YELLOW)
        }
        Text(localized(TextKey.ClanRaceCutoff, "time" to raceTime(week.endEpochMillis)), style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
        week.settlement?.result?.let { snapshot ->
            // Snapshot identity and rewards survive later clan changes or dissolution.
            snapshot.standings.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClanLogoMark(row.logoId, Modifier.size(width = 36.dp, height = 40.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall, color = A1Palette.Ink)
                        Text(localized(TextKey.ClanRaceRankPoints, "rank" to row.rank, "points" to row.points),
                            style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
                        row.poolId?.let { Text(racePoolLabel(it), style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted) }
                    }
                }
                snapshot.winners.firstOrNull { it.standing.clanId == row.clanId }?.let {
                    Text(localized(TextKey.ClanRacePerMember), style = MaterialTheme.typography.bodySmall, color = A1Palette.Muted)
                    RaceReward(it.reward)
                }
            }
        }
    }
}

/** Phần thưởng dạng icon + số (gem, vàng, XP), bỏ qua loại bằng 0. */
@Composable internal fun RaceReward(reward: ClanRaceReward, centered: Boolean = false) {
    FlowRow(modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = if (centered) Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (reward.gems > 0) RewardAmount({ A1GemIcon(size = 22.dp) }, "${reward.gems}",
            localized(TextKey.RewardGemsDescription, "count" to reward.gems))
        if (reward.gold > 0) RewardAmount({ A1CoinIcon(size = 22.dp) }, "${reward.gold}",
            localized(TextKey.GiftGoldReward, "count" to reward.gold))
        if (reward.xp > 0) RewardAmount({
            Icon(Icons.Rounded.Star, contentDescription = null, tint = A1Palette.Blue2, modifier = Modifier.size(22.dp))
        }, "${reward.xp} XP", localized(TextKey.GiftXpReward, "count" to reward.xp))
    }
}

@Composable private fun RewardAmount(icon: @Composable () -> Unit, value: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        icon()
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), color = A1Palette.Ink)
    }
}

@Composable private fun RacePagination(page: Int, size: Int, total: Int, loading: Boolean, onPage: (Int) -> Unit) {
    if (total <= size && page == 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ArcadeActionButton(localized(TextKey.Previous), { onPage(page - 1) }, enabled = !loading && page > 0,
            style = ArcadeActionStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f))
        Text(localized(TextKey.ClanRacePage, "page" to page + 1), style = MaterialTheme.typography.titleSmall, color = A1Palette.Muted)
        ArcadeActionButton(localized(TextKey.Next), { onPage(page + 1) }, enabled = !loading && (page.toLong() + 1) * size < total,
            style = ArcadeActionStyle.OUTLINE, compact = true, modifier = Modifier.weight(1f))
    }
}

private fun raceStatus(state: ClanRaceState): TextKey = when (state) {
    ClanRaceState.INACTIVE -> TextKey.ClanRaceInactive
    ClanRaceState.ACTIVE -> TextKey.ClanRaceActive
    ClanRaceState.CLOSED -> TextKey.ClanRacePending
    ClanRaceState.SETTLED -> TextKey.ClanRaceSettled
}

internal fun raceTime(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.of("Asia/Bangkok")).toString().replace('T', ' ').take(16)
