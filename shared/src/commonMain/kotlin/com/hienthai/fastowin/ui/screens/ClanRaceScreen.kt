package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.ClanRaceUiState
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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
    Scaffold(containerColor = Color.Transparent, topBar = {
        FastToWinHeader(localized(TextKey.ClanRaceTitle), 0, 0, 0, {}, onBack = onBack,
            showBalances = false, showNotifications = false)
    }) { padding ->
        ResponsiveScreen(Modifier.padding(padding), maxContentWidth = 920.dp, applySafeDrawingInsets = false) { modifier ->
            LazyColumn(modifier.testTag("clan_race"), contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item("refresh") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onRefresh, enabled = !state.isLoading && !isGuest,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("race_refresh")) {
                            Text(localized(if (state.isLoading) TextKey.Loading else TextKey.ClanRaceRefresh))
                        }
                        if (state.isLoading) Text(localized(TextKey.Loading),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        state.lastRefreshedAtEpochMillis?.let {
                            Text(localized(TextKey.ClanRaceUpdated, "time" to raceTime(it)), style = MaterialTheme.typography.bodyMedium)
                        }
                        state.loadError?.let {
                            Text(localized(when (it) {
                                "UNAUTHORIZED" -> TextKey.GiftErrorUnauthorized
                                "FORBIDDEN" -> TextKey.ClanRaceForbidden
                                "CONFLICT" -> TextKey.ClanRaceConflict
                                else -> TextKey.ClanRaceError
                            }), color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("race_error"))
                        }
                    }
                }
                if (isGuest) item("guest") { Text(localized(TextKey.GiftMailboxGuest)) }
                val current = state.current
                if (current != null) {
                    item("week") { RacePanel {
                        RaceHeading(localized(TextKey.ClanRaceWeek, "week" to current.weekStart))
                        Text(localized(raceStatus(current.state)), color = ArcadePalette.Gold500,
                            fontWeight = FontWeight.Bold, modifier = Modifier.testTag("race_status"))
                        Text(localized(TextKey.ClanRaceCutoff, "time" to raceTime(current.endEpochMillis)))
                        if (current.state == ClanRaceState.ACTIVE) {
                            val minutes = ((current.endEpochMillis - current.serverNowEpochMillis).coerceAtLeast(0)) / 60_000
                            Text(localized(TextKey.ClanRaceTimeLeft, "hours" to minutes / 60, "minutes" to minutes % 60))
                        }
                    } }
                    // Only the server's current frozen configuration supplies prizes.
                    current.config?.takeIf { current.state != ClanRaceState.INACTIVE }?.let { config ->
                        item("prizes") { RacePanel {
                            RaceHeading(localized(TextKey.ClanRacePrizes))
                            Text(localized(TextKey.ClanRacePerMember), fontWeight = FontWeight.Bold)
                            config.rewards.forEachIndexed { index, reward ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text((index + 1).toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
                                        color = ArcadePalette.Gold500)
                                    RaceReward(reward)
                                }
                            }
                            Text(localized(TextKey.ClanRaceEligibility), style = MaterialTheme.typography.bodyMedium)
                        } }
                    }
                    if (current.state != ClanRaceState.INACTIVE) {
                        item("standings_heading") { RaceHeading(localized(TextKey.ClanRaceStandings)) }
                        val standings = state.standings
                        if (standings?.items.isNullOrEmpty()) item("standings_empty") { Text(localized(TextKey.ClanRaceEmpty)) }
                        items(standings?.items.orEmpty(), key = { "standing:${it.clanId}" }) { row ->
                            RaceStanding(row, row.clanId == standings?.ownClan?.clanId)
                        }
                        standings?.ownClan?.takeIf { own -> standings.items.none { it.clanId == own.clanId } }?.let { own ->
                            item("own_outside") { RaceStanding(own, own = true) }
                        }
                        standings?.let { page -> item("standings_pages") {
                            RacePagination(page.page, page.pageSize, page.total, state.isLoading, onStandingsPage)
                        } }
                        state.contributions?.takeIf { it.clanId != null }?.let { contributions ->
                            item("contributions_heading") { RaceHeading(localized(TextKey.ClanRaceContributions)) }
                            if (contributions.items.isEmpty()) item("contributions_empty") { Text(localized(TextKey.ClanRaceEmpty)) }
                            items(contributions.items, key = { "contribution:${it.userId}:${it.tenureId}" }) { member ->
                                RacePanel {
                                    Text(member.displayName ?: localized(TextKey.Player), fontWeight = FontWeight.Bold)
                                    Text(localized(TextKey.Score) + ": " + member.points)
                                    Text(localized(TextKey.ClanRaceMission, "points" to raceUnitsText(member.missionUnits)))
                                    Text(localized(TextKey.ClanRaceDonation, "points" to raceUnitsText(member.donationUnits)))
                                    Text(localized(when {
                                        !member.currentMember -> TextKey.ClanRaceFormer
                                        member.eligible -> TextKey.ClanRaceEligible
                                        else -> TextKey.ClanRaceIneligible
                                    }), color = if (member.eligible) ArcadePalette.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("race_eligibility_${member.tenureId}"))
                                }
                            }
                        }
                    }
                }
                state.history?.let { history ->
                    item("history_heading") { RaceHeading(localized(TextKey.ClanRaceHistory)) }
                    if (history.items.isEmpty()) item("history_empty") { Text(localized(TextKey.ClanRaceEmpty)) }
                    items(history.items, key = { "history:${it.weekStart}" }) { week -> RaceHistory(week) }
                    item("history_pages") { RacePagination(history.page, history.pageSize, history.total, state.isLoading, onHistoryPage) }
                }
            }
        }
    }
}

@Composable private fun RacePanel(content: @Composable ColumnScope.() -> Unit) {
    ArcadePanel(Modifier.fillMaxWidth(), accent = ArcadePalette.Blue300) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun RaceHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
        modifier = Modifier.semantics { heading() })
}

@Composable private fun RaceStanding(row: ClanRaceStanding, own: Boolean = false) {
    RacePanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClanLogoMark(row.logoId, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (own) Text(localized(TextKey.YourClan), color = ArcadePalette.Gold500, fontWeight = FontWeight.Bold)
                Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(localized(TextKey.Level, "level" to row.level))
            }
        }
        Text(localized(TextKey.ClanRaceRankPoints, "rank" to row.rank, "points" to row.points),
            fontWeight = FontWeight.Bold, modifier = Modifier.testTag("race_rank_${row.clanId}"))
    }
}

@Composable private fun RaceHistory(week: ClanRaceCurrent) {
    RacePanel {
        RaceHeading(localized(TextKey.ClanRaceWeek, "week" to week.weekStart))
        Text(localized(if (week.settlement?.state == ClanRaceSettlementState.FAILED) TextKey.ClanRaceSettlementFailed
            else raceStatus(week.state)), color = ArcadePalette.Gold500)
        Text(localized(TextKey.ClanRaceCutoff, "time" to raceTime(week.endEpochMillis)))
        week.settlement?.result?.let { snapshot ->
            // Snapshot identity and rewards survive later clan changes or dissolution.
            snapshot.standings.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClanLogoMark(row.logoId, Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.name, fontWeight = FontWeight.Bold)
                        Text(localized(TextKey.Level, "level" to row.level))
                        Text(localized(TextKey.ClanRaceRankPoints, "rank" to row.rank, "points" to row.points))
                    }
                }
                snapshot.winners.firstOrNull { it.standing.clanId == row.clanId }?.let {
                    Text(localized(TextKey.ClanRacePerMember))
                    RaceReward(it.reward)
                }
            }
        }
    }
}

@Composable internal fun RaceReward(reward: ClanRaceReward) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(localized(TextKey.GiftGoldReward, "count" to reward.gold), color = ArcadePalette.Gold500)
        Text(localized(TextKey.GiftXpReward, "count" to reward.xp))
        Text(localized(TextKey.RewardGemsDescription, "count" to reward.gems), color = ArcadePalette.Mint400)
    }
}

@Composable private fun RacePagination(page: Int, size: Int, total: Int, loading: Boolean, onPage: (Int) -> Unit) {
    if (total <= size && page == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(localized(TextKey.ClanRacePage, "page" to page + 1))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ onPage(page - 1) }, enabled = !loading && page > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(localized(TextKey.Previous)) }
            OutlinedButton({ onPage(page + 1) }, enabled = !loading && (page.toLong() + 1) * size < total, modifier = Modifier.heightIn(min = 48.dp)) { Text(localized(TextKey.Next)) }
        }
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
