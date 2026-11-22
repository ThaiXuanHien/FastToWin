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
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
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
                    enabled = state.load != RewardEventLoad.LOADING && !state.isRefreshing)
                if (state.isRefreshing) Text(localized(TextKey.RewardEventsLoading))
                if (state.load != RewardEventLoad.READY || (state.selected == null && state.items.isEmpty())) Text(localized(rewardEventLoadKey(state.load)))
                val events = if (state.load == RewardEventLoad.READY) state.selected?.let { listOf(it) } ?: state.items else emptyList()
                events.forEach { event ->
                    val displayZone = try {
                        TimeZone.of(event.timezone)
                    } catch (_: IllegalTimeZoneException) {
                        TimeZone.UTC
                    }
                    val start = Instant.fromEpochMilliseconds(event.startsAtEpochMillis)
                    val end = Instant.fromEpochMilliseconds(event.endsAtEpochMillis)
                    val startOffset = utcOffsetLabel(displayZone, start)
                    val endOffset = utcOffsetLabel(displayZone, end)
                    val zoneLabel = if (startOffset != null || endOffset != null) displayZone.id
                    else localization.timeZoneDisplayLabel(displayZone.id)
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
                            Text(
                                localized(
                                    TextKey.RewardEventBounds,
                                    "start" to eventDisplayTime(start, displayZone, language, startOffset),
                                    "end" to eventDisplayTime(end, displayZone, language, endOffset),
                                    "zone" to zoneLabel
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (state.selected == null) RewardEventActionButton(label = localized(TextKey.RewardEventsDetails), onClick = { onSelect(event.id) },
                                enabled = state.load != RewardEventLoad.LOADING && !state.isRefreshing)
                        }
                    }
                }
                Text(localized(TextKey.RewardEventAuthority), style = MaterialTheme.typography.bodyMedium)
                if (state.selected == null && state.hasMore) RewardEventActionButton(label = localized(TextKey.RewardEventsMore), onClick = onMore,
                    enabled = state.load == RewardEventLoad.READY && !state.isLoadingMore && !state.isRefreshing)
            }
        }
    }
}

private val eventVietnameseDate = LocalDate.Format {
    day()
    char('/')
    monthNumber()
    char('/')
    year()
}

private val eventClockTime = LocalTime.Format {
    hour()
    char(':')
    minute()
    char(':')
    second()
    char('.')
    secondFraction(fixedLength = 3)
}

private fun eventDisplayTime(instant: Instant, zone: TimeZone, language: AppLanguage, offset: String?): String {
    val local = instant.toLocalDateTime(zone)
    val dateFormat = if (language == AppLanguage.VIETNAMESE) eventVietnameseDate else LocalDate.Formats.ISO
    val dateTime = "${dateFormat.format(local.date)} ${eventClockTime.format(local.time)}"
    return if (offset == null) dateTime else "$dateTime ($offset)"
}
