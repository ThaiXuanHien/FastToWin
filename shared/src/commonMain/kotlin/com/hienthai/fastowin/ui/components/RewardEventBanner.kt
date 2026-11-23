package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.theme.ArcadePalette

fun rewardEventStateKey(state: RewardEventState): TextKey = when (state) {
    RewardEventState.ACTIVE -> TextKey.RewardEventActive
    RewardEventState.SCHEDULED -> TextKey.RewardEventScheduled
    RewardEventState.ENDED -> TextKey.RewardEventEnded
    RewardEventState.CANCELLED -> TextKey.RewardEventCancelled
    RewardEventState.DRAFT -> TextKey.RewardEventsUnavailable
}
fun rewardEventLoadKey(load: RewardEventLoad): TextKey = when (load) {
    RewardEventLoad.UNKNOWN, RewardEventLoad.LOADING -> TextKey.RewardEventsLoading
    RewardEventLoad.UNAVAILABLE -> TextKey.RewardEventsUnavailable
    RewardEventLoad.ERROR -> TextKey.RewardEventsError
    RewardEventLoad.READY -> TextKey.RewardEventsEmpty
}

@Composable
fun RewardEventBanner(state: RewardEventUiState, onDetails: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val language = LocalLocalization.current.language
    val refreshingLabel = localized(TextKey.RewardEventsLoading)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = onDetails, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reward_event_banner"),
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ArcadePalette.Gold500.copy(alpha = 0.42f))) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val active = state.active.firstOrNull()
                    if (active != null) {
                        Text(if (language == AppLanguage.VIETNAMESE) active.nameVi else active.nameEn,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        RewardEventValues(active.goldMultiplier, active.xpMultiplier, factors = true)
                        Text(localized(TextKey.RewardEventActive), style = MaterialTheme.typography.labelMedium)
                    } else {
                        Text(localized(TextKey.RewardEventsTitle), fontWeight = FontWeight.Bold)
                        Text(localized(if (state.load == RewardEventLoad.READY && state.items.isNotEmpty())
                            rewardEventStateKey(state.items.first().state) else rewardEventLoadKey(state.load)),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = refreshingLabel },
                            strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = localized(TextKey.RewardEventsDetails))
                    }
                }
            }
        }
        if (state.load == RewardEventLoad.ERROR || state.load == RewardEventLoad.UNAVAILABLE) {
            RewardEventActionButton(label = localized(TextKey.RewardEventsRefresh), onClick = onRefresh)
        }
    }
}

@Composable
fun RewardEventActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text(label)
    }
}

/** Existing currency/progression glyphs, with one localized spoken label for both values. */
@Composable
fun RewardEventValues(gold: Int, xp: Int, factors: Boolean = false, emphasis: Boolean = false) {
    val description = localized(if (factors) TextKey.RewardEventFactors else TextKey.RewardResultAmounts,
        "gold" to gold, "xp" to xp)
    val typography = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium
    val weight = if (emphasis) FontWeight.Black else FontWeight.Bold
    val goldLabel = if (factors) localized(TextKey.RewardEventFactorValue, "count" to gold) else gold.toString()
    val xpLabel = if (factors) localized(TextKey.RewardEventFactorValue, "count" to xp) else xp.toString()
    FlowRow(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RewardAmount(goldLabel, RewardCurrency.GOLD, Modifier.align(Alignment.CenterVertically), typography, weight)
        RewardAmount("$xpLabel XP", RewardCurrency.XP, Modifier.align(Alignment.CenterVertically), typography, weight)
    }
}

/** Both the online result and committed history render server amounts, without reward arithmetic. */
@Composable
fun RewardBreakdownCard(breakdown: MatchRewardBreakdown?, persisted: Boolean = false,
    onOpenEvent: ((String) -> Unit)? = null, modifier: Modifier = Modifier) {
    val language = LocalLocalization.current.language
    ArcadePanel(modifier = modifier.fillMaxWidth().testTag("reward_breakdown"), accent = ArcadePalette.Gold500) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(localized(TextKey.RewardResultTitle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            if (breakdown == null) {
                Text(localized(TextKey.RewardResultLegacy))
            } else {
                RewardBreakdownLine(TextKey.RewardResultTotal, breakdown.totalGold, breakdown.totalXp, true)
                RewardBreakdownLine(TextKey.RewardResultBase, breakdown.baseGold, breakdown.baseXp)
                RewardBreakdownLine(TextKey.RewardResultBonus, breakdown.bonusGold, breakdown.bonusXp)
                if (breakdown.clanBonusGold > 0) RewardBreakdownLine(TextKey.RewardResultClanBonus, breakdown.clanBonusGold, 0)
                Text(localized(TextKey.RewardResultSources), fontWeight = FontWeight.Bold)
                if (breakdown.sources.isEmpty()) Text(localized(TextKey.RewardResultNoSources))
                breakdown.sources.forEach { source ->
                    val name = if (language == AppLanguage.VIETNAMESE) source.nameVi else source.nameEn
                    if (onOpenEvent == null) Text(name) else RewardEventActionButton(name, { onOpenEvent(source.id) })
                }
                Text(localized(if (persisted) TextKey.RewardResultPersisted else TextKey.RewardResultWalletAuthority),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable
private fun RewardBreakdownLine(key: TextKey, gold: Int, xp: Int, emphasis: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(localized(key), style = MaterialTheme.typography.labelLarge)
        RewardEventValues(gold, xp, emphasis = emphasis)
    }
}
