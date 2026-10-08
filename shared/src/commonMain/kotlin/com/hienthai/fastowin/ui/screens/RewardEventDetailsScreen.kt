package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.*
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.components.*
import com.hienthai.fastowin.ui.layout.ResponsiveScreen
import kotlin.time.Instant

@Composable
fun RewardEventDetailsScreen(state: RewardEventUiState, onBack: () -> Unit, onRefresh: () -> Unit,
    onSelect: (String) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val language = LocalLocalization.current.language
    val localization = LocalLocalization.current
    SystemBackHandler(onBack = onBack)
    Scaffold(modifier = modifier.fillMaxSize().testTag("reward_event_details"), topBar = {
        FastToWinHeader(title = localized(TextKey.RewardEventsTitle), gold = 0, gems = 0,
            unreadNotifications = 0, onNotifications = {}, onBack = onBack, showBalances = false, showNotifications = false)
    }) { padding ->
        ResponsiveScreen(modifier = Modifier.padding(padding), maxContentWidth = 760.dp, applySafeDrawingInsets = false) { content ->
            Column(content.verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RewardEventActionButton(label = localized(TextKey.RewardEventsRefresh), onClick = onRefresh,
                    enabled = state.load != RewardEventLoad.LOADING)
                if (state.load != RewardEventLoad.READY || (state.selected == null && state.items.isEmpty())) Text(localized(rewardEventLoadKey(state.load)))
                val events = if (state.load == RewardEventLoad.READY) state.selected?.let { listOf(it) } ?: state.items else emptyList()
                events.forEach { event ->
                    ArcadePanel(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (language == AppLanguage.VIETNAMESE) event.nameVi else event.nameEn,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(localized(rewardEventStateKey(event.state)), fontWeight = FontWeight.Bold)
                            RewardEventValues(event.goldMultiplier, event.xpMultiplier, factors = true)
                            Text(if (language == AppLanguage.VIETNAMESE) event.contentVi else event.contentEn)
                            Text(localized(TextKey.RewardEventModes, "modes" to event.matchTypes.sortedBy { it.name }.joinToString(" · ") {
                                localization.text(if (it == MatchType.RANKED) TextKey.Ranked else TextKey.Casual)
                            }))
                            Text(localized(TextKey.RewardEventBounds, "start" to utc(event.startsAtEpochMillis),
                                "end" to utc(event.endsAtEpochMillis), "zone" to event.timezone), style = MaterialTheme.typography.bodyMedium)
                            if (state.selected == null) RewardEventActionButton(label = localized(TextKey.RewardEventsDetails), onClick = { onSelect(event.id) },
                                enabled = state.load != RewardEventLoad.LOADING)
                        }
                    }
                }
                Text(localized(TextKey.RewardEventAuthority), style = MaterialTheme.typography.bodyMedium)
                if (state.selected == null && state.hasMore) RewardEventActionButton(label = localized(TextKey.RewardEventsMore), onClick = onMore,
                    enabled = state.load == RewardEventLoad.READY && !state.isLoadingMore)
            }
        }
    }
}
private fun utc(millis: Long) = Instant.fromEpochMilliseconds(millis).toString().replace('T',' ')
