package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.MissionState
import com.hienthai.fastowin.ui.theme.ArcadePalette

/** Embedded in the existing screen's scrolling content; every returned period remains accessible. */
@Composable
fun ServerMissionList(state: MissionState, onRefresh: () -> Unit, onClaim: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("server_missions"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isLoading) Text(localized(TextKey.Loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (state.loadError != null) MissionError()
        ArcadeActionButton(
            label = localized(if (state.loadError != null) TextKey.Retry else TextKey.MissionRefresh),
            onClick = onRefresh, enabled = !state.isLoading, style = ArcadeActionStyle.OUTLINE,
            modifier = Modifier.fillMaxWidth().testTag("refresh_missions")
        )
        if (!state.isLoading && state.loadError == null && state.items.isEmpty()) Text(localized(TextKey.NoMissions))
        MissionCadence.entries.forEach { cadence ->
            val rows = state.items.filter { it.period == cadence }
            if (rows.isNotEmpty()) {
                Text(localized(if (cadence == MissionCadence.DAILY) TextKey.DailyMissions else TextKey.WeeklyMissions),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.semantics { heading() })
                rows.forEach { mission -> key(mission.instanceId) {
                    ServerMissionCard(mission, mission.instanceId in state.pendingIds, state.itemErrors[mission.instanceId] != null) { onClaim(mission.instanceId) }
                } }
            }
        }
    }
}

@Composable
fun ServerMissionCard(mission: MissionInstanceView, pending: Boolean, hasError: Boolean, onClaim: () -> Unit) {
    ArcadePanel(Modifier.fillMaxWidth().testTag("mission_instance:${mission.instanceId}"), accent = ArcadePalette.Violet600) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(LocalLocalization.current.missionTitle(mission), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            mission.displayMatchType?.let { Text(localized(if (it == MatchType.RANKED) TextKey.Ranked else TextKey.Casual), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(LocalLocalization.current.missionPeriod(mission),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { (mission.progress.toFloat() / mission.target.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = ArcadePalette.Mint400, trackColor = ArcadePalette.Navy700)
            Text("${mission.progress}/${mission.target}", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mission.reward.gold > 0) RewardAmounts(gold = mission.reward.gold, xp = 0, gems = 0)
                if (mission.reward.xp > 0) RewardAmounts(gold = 0, xp = mission.reward.xp, gems = 0)
                if (mission.reward.gems > 0) RewardAmounts(gold = 0, xp = 0, gems = mission.reward.gems)
            }
            if (mission.scope == MissionScope.CLAN) {
                Text("${localized(TextKey.YourContribution)}: ${mission.personalContribution}", style = MaterialTheme.typography.bodyMedium)
                if (mission.reward.contributionPoints > 0) Text(localized(TextKey.MissionContributionPoints, "points" to mission.reward.contributionPoints),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasError) MissionError()
            when (mission.state) {
                MissionClaimState.CLAIMED -> ArcadeActionButton(localized(TextKey.Claimed), {}, enabled = false, style = ArcadeActionStyle.OUTLINE, modifier = Modifier.fillMaxWidth())
                MissionClaimState.CLAIMABLE -> ArcadeActionButton(
                    localized(when { pending -> TextKey.Claiming; hasError -> TextKey.Retry; else -> TextKey.ClaimReward }),
                    onClaim, enabled = !pending, style = ArcadeActionStyle.GOLD,
                    modifier = Modifier.fillMaxWidth().testTag("claim_mission_instance:${mission.instanceId}")
                )
                MissionClaimState.IN_PROGRESS -> Unit
            }
        }
    }
}

@Composable
private fun MissionError() {
    Text(localized(TextKey.MissionRequestError), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
