package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.RankedTier
import com.hienthai.fastowin.protocol.CosmeticType
import com.hienthai.fastowin.protocol.SeasonCosmeticRewardSnapshot
import com.hienthai.fastowin.protocol.SeasonSnapshot
import com.hienthai.fastowin.protocol.SeasonRewardReceiptSnapshot
import com.hienthai.fastowin.protocol.SeasonTierRewardSnapshot
import com.hienthai.fastowin.protocol.rankedTierFor
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay

@Composable
fun SeasonProgressCard(
    season: SeasonSnapshot,
    modifier: Modifier = Modifier
) {
    var rewardsExpanded by remember(season.name) { mutableStateOf(false) }
    var nowMillis by remember(season.endsAtEpochMillis) { mutableLongStateOf(epochMillis()) }
    LaunchedEffect(season.endsAtEpochMillis) {
        while (true) {
            delay(SEASON_TIMER_REFRESH_MILLIS)
            nowMillis = epochMillis()
        }
    }

    val isPlacement = season.placementMatchesPlayed < season.placementMatchesRequired
    val currentTier = rankedTierFor(season.rating)
    val nextTier = nextRankedTier(season.rating)
    val progress = if (isPlacement) {
        season.placementMatchesPlayed.toFloat() / season.placementMatchesRequired.coerceAtLeast(1)
    } else {
        ratingProgressWithinTier(season.rating)
    }.coerceIn(0f, 1f)
    val progressDescription = if (isPlacement) {
        "Phân hạng ${season.placementMatchesPlayed} trên ${season.placementMatchesRequired} trận"
    } else if (nextTier == null) {
        "Đã đạt bậc cao nhất"
    } else {
        "Còn ${(nextTier.minimumRating - season.rating).coerceAtLeast(0)} Elo để lên ${nextTier.displayName}"
    }
    val peakTier = rankedTierFor(season.peakRating)
    val heldReward = if (isPlacement) null else season.tierRewards
        .lastOrNull { season.peakRating >= it.tier.minimumRating }

    ArcadePanel(
        modifier = modifier.fillMaxWidth().testTag("season_progress_card"),
        accent = ArcadePalette.Gold500
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                season.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = ArcadePalette.Gold500
            )
            if (season.tierRewards.isNotEmpty()) {
                TextButton(
                    onClick = { rewardsExpanded = !rewardsExpanded },
                    modifier = Modifier.align(Alignment.End).testTag("season_rewards_toggle")
                ) {
                    Text(if (rewardsExpanded) "Ẩn thưởng các bậc" else "Xem thưởng các bậc")
                    Icon(
                        if (rewardsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    seasonTimeRemaining(season.endsAtEpochMillis, nowMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                if (isPlacement) "Đang phân hạng" else "${currentTier.displayName} • ${season.rating} Elo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .testTag("season_progress")
                    .semantics { stateDescription = progressDescription }
            )
            Text(progressDescription, style = MaterialTheme.typography.bodySmall)

            if (isPlacement) {
                Text(
                    "Hoàn thành phân hạng để chốt bậc và mở mốc thưởng.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Elo cao nhất mùa: ${season.peakRating} • ${peakTier.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                heldReward?.let { reward ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mốc thưởng đang giữ", style = MaterialTheme.typography.labelMedium)
                                reward.cosmetic?.let { cosmetic ->
                                    Text(
                                        cosmetic.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            RewardAmounts(gold = reward.gold, xp = 0, gems = reward.gems)
                        }
                    }
                }
            }

            if (season.tierRewards.isNotEmpty()) {
                if (rewardsExpanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("season_rewards"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        season.tierRewards.sortedBy { it.tier.minimumRating }.forEach { reward ->
                            SeasonRewardRow(
                                reward = reward,
                                reached = !isPlacement && season.peakRating >= reward.tier.minimumRating,
                                current = !isPlacement && reward.tier == peakTier
                            )
                        }
                    }
                }
            } else {
                Text(season.rewardDescription, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SeasonRewardRow(
    reward: SeasonTierRewardSnapshot,
    reached: Boolean,
    current: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("season_reward:${reward.tier.name}"),
        color = if (current) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(reward.tier.displayName, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        current -> "Bậc cao nhất đã đạt"
                        reached -> "Đã vượt qua"
                        else -> "Từ ${reward.tier.minimumRating} Elo"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                reward.cosmetic?.let { cosmetic ->
                    Text(
                        "${cosmetic.type.rewardTypeLabel()}: ${cosmetic.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("season_reward_cosmetic:${reward.tier.name}")
                    )
                }
            }
            RewardAmounts(gold = reward.gold, xp = 0, gems = reward.gems)
        }
    }
}

@Composable
fun SeasonRewardReceiptCard(
    receipt: SeasonRewardReceiptSnapshot,
    modifier: Modifier = Modifier
) {
    ArcadePanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("season_reward_receipt")
            .semantics {
                stateDescription = "Đã nhận thưởng ${receipt.seasonName}, bậc ${receipt.tier.displayName}"
            },
        accent = ArcadePalette.Gold500
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Thưởng mùa đã nhận", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${receipt.seasonName} • ${receipt.tier.displayName} • ${receipt.peakRating} Elo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Đã cộng vào tài sản",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            RewardAmounts(gold = receipt.gold, xp = 0, gems = receipt.gems)
            receipt.cosmetic?.let { cosmetic -> SeasonCosmeticRewardCard(cosmetic) }
        }
    }
}

@Composable
fun SeasonRewardSummaryDialog(
    receipt: SeasonRewardReceiptSnapshot,
    onAcknowledge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAcknowledge,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .testTag("season_reward_summary_dialog"),
        icon = {
            Icon(Icons.Default.EmojiEvents, contentDescription = null)
        },
        title = {
            Text("Tổng kết mùa", fontWeight = FontWeight.Black)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Chúc mừng! Thành tích và phần thưởng mùa của bạn đã được ghi nhận.",
                    style = MaterialTheme.typography.bodyMedium
                )
                SeasonRewardReceiptCard(receipt)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.testTag("acknowledge_season_reward")
            ) {
                Text("Tuyệt vời")
            }
        }
    )
}

@Composable
internal fun SeasonCosmeticRewardCard(cosmetic: SeasonCosmeticRewardSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("season_reward_receipt_cosmetic"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (cosmetic.type == CosmeticType.FRAME) {
                PlayerAvatar(
                    displayName = "Phần thưởng mùa",
                    avatarId = "trophy",
                    frameId = cosmetic.id,
                    size = 48.dp
                )
            } else {
                Icon(
                    Icons.Default.MilitaryTech,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    cosmetic.type.rewardTypeLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    cosmetic.name,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Đã thêm vào Bộ sưu tập",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun CosmeticType.rewardTypeLabel(): String = when (this) {
    CosmeticType.FRAME -> "Khung mùa độc quyền"
    CosmeticType.TITLE -> "Danh hiệu mùa độc quyền"
    else -> "Ngoại trang mùa độc quyền"
}

internal fun nextRankedTier(rating: Int): RankedTier? = RankedTier.entries
    .firstOrNull { it.minimumRating > rating }

internal fun ratingProgressWithinTier(rating: Int): Float {
    val current = rankedTierFor(rating)
    val next = nextRankedTier(rating) ?: return 1f
    return (rating - current.minimumRating).toFloat() /
        (next.minimumRating - current.minimumRating).coerceAtLeast(1)
}

internal fun seasonTimeRemaining(endsAtEpochMillis: Long, nowMillis: Long): String {
    val remainingMillis = (endsAtEpochMillis - nowMillis).coerceAtLeast(0L)
    if (remainingMillis == 0L) return "Mùa đã kết thúc"
    val totalMinutes = (remainingMillis + 59_999L) / 60_000L
    val totalHours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    if (totalHours >= 24L) {
        val days = totalHours / 24L
        val hours = totalHours % 24L
        return if (hours == 0L) "Còn $days ngày" else "Còn $days ngày $hours giờ"
    }
    return if (totalHours > 0L) "Còn $totalHours giờ $minutes phút" else "Còn $minutes phút"
}

private const val SEASON_TIMER_REFRESH_MILLIS = 60_000L
