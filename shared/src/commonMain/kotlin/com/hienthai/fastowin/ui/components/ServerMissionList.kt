package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.Alignment
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import com.hienthai.fastowin.ui.theme.A1Palette
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.MissionState
import com.hienthai.fastowin.ui.theme.ArcadePalette
import com.hienthai.fastowin.ui.components.RewardReceiptAmounts
import com.hienthai.fastowin.ui.components.RewardReceiptDialog

/** Embedded in the existing screen's scrolling content; every returned period remains accessible. */
@Composable
fun ServerMissionList(
    state: MissionState,
    onRefresh: () -> Unit,
    onClaim: (String) -> Unit,
    onDismissReward: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dismissedRewardId by remember { mutableStateOf<String?>(null) }
    state.lastClaimedReward?.takeIf { it.instanceId != dismissedRewardId }?.let { receipt ->
        RewardReceiptDialog(
            RewardReceiptAmounts(
                gold = receipt.reward.gold.toLong(),
                xp = receipt.reward.xp.toLong(),
                gems = receipt.reward.gems.toLong()
            ),
            onDismiss = {
                dismissedRewardId = receipt.instanceId
                onDismissReward()
            }
        )
    }
    Column(modifier.fillMaxWidth().testTag("server_missions"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isLoading) Text(localized(TextKey.Loading), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (state.loadError != null) MissionError()
        // A1: nút tải lại chỉ hiện khi lỗi hoặc chưa có nhiệm vụ.
        if (state.loadError != null || (!state.isLoading && state.items.isEmpty())) {
            ArcadeActionButton(
                label = localized(if (state.loadError != null) TextKey.Retry else TextKey.MissionRefresh),
                onClick = onRefresh, enabled = !state.isLoading, style = ArcadeActionStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth().testTag("refresh_missions")
            )
        }
        if (!state.isLoading && state.loadError == null && state.items.isEmpty()) Text(localized(TextKey.NoMissions))
        MissionCadence.entries.forEach { cadence ->
            state.items.filter { it.period == cadence }.forEach { mission -> key(mission.instanceId) {
                ServerMissionCard(mission, mission.instanceId in state.pendingIds, state.itemErrors[mission.instanceId] != null) { onClaim(mission.instanceId) }
            } }
        }
    }
}

/**
 * Thẻ nhiệm vụ A1 ("Nhiệm vụ tuần" trong thiết kế): tiêu đề loại nhiệm vụ + nhãn "Còn N ngày", mô tả,
 * thanh tiến độ xanh lá kèm "x/y", phần thưởng và phần đóng góp của bạn (nhiệm vụ bang).
 */
@Composable
fun ServerMissionCard(mission: MissionInstanceView, pending: Boolean, hasError: Boolean, onClaim: () -> Unit) {
    val daysLeft = remember(mission.periodEndExclusive) { missionDaysLeft(mission.periodEndExclusive) }
    Column(
        Modifier.fillMaxWidth().a1Card().padding(horizontal = 18.dp, vertical = 16.dp).testTag("mission_instance:${mission.instanceId}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                localized(if (mission.period == MissionCadence.DAILY) TextKey.MissionDailyTitle else TextKey.MissionWeeklyTitle),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = A1Palette.Ink,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (daysLeft != null && daysLeft > 0) A1Tag(localized(TextKey.DaysRemaining, "days" to daysLeft), A1TagTone.YELLOW)
        }
        Text(
            LocalLocalization.current.missionTitle(mission),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = A1Palette.Ink
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                com.hienthai.fastowin.ui.screens.A1ProgressBar(
                    (mission.progress.toFloat() / mission.target.coerceAtLeast(1)).coerceIn(0f, 1f),
                    listOf(A1Palette.Green1, A1Palette.Green2)
                )
            }
            Text("${mission.progress}/${mission.target}", style = MaterialTheme.typography.titleMedium, color = A1Palette.Ink)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mission.reward.gold > 0) MissionRewardChip({ A1CoinIcon(size = 22.dp) }, "+${mission.reward.gold}")
            if (mission.reward.xp > 0) MissionRewardChip({
                Icon(androidx.compose.material.icons.Icons.Rounded.Star, contentDescription = null, tint = A1Palette.Blue2, modifier = Modifier.size(22.dp))
            }, "+${mission.reward.xp} XP")
            if (mission.reward.gems > 0) MissionRewardChip({ A1GemIcon(size = 22.dp) }, "+${mission.reward.gems}")
            if (mission.scope == MissionScope.CLAN) {
                Text(
                    localized(TextKey.MissionYouContributed, "count" to mission.personalContribution),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = A1Palette.Muted
                )
            }
        }
        if (mission.scope == MissionScope.CLAN && mission.reward.contributionPoints > 0) {
            Text(
                localized(TextKey.MissionContributionPoints, "points" to mission.reward.contributionPoints),
                style = MaterialTheme.typography.bodySmall,
                color = A1Palette.Muted
            )
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

@Composable
private fun MissionRewardChip(icon: @Composable () -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = A1Palette.Ink)
    }
}

/** Số ngày còn lại tới hết kỳ (giờ Việt Nam, như lịch nhiệm vụ của server); null nếu không đọc được ngày. */
private fun missionDaysLeft(periodEndExclusive: String): Int? = runCatching {
    val zone = kotlinx.datetime.TimeZone.of("Asia/Ho_Chi_Minh")
    val today = kotlinx.datetime.Instant.fromEpochMilliseconds(com.hienthai.fastowin.platform.epochMillis())
        .toLocalDateTime(zone).date
    today.daysUntil(kotlinx.datetime.LocalDate.parse(periodEndExclusive))
}.getOrNull()

@Composable
private fun MissionError() {
    Text(localized(TextKey.MissionRequestError), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
