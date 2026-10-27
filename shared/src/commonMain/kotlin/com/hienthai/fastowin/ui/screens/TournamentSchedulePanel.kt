package com.hienthai.fastowin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.localization.timeZoneDisplayLabel
import com.hienthai.fastowin.protocol.TournamentScheduleSnapshot
import com.hienthai.fastowin.state.*
import com.hienthai.fastowin.ui.components.ArcadePanel
import com.hienthai.fastowin.ui.theme.ArcadePalette

@Composable
internal fun TournamentSchedulePanel(
    schedule: TournamentScheduleSnapshot,
    now: Long,
    maxPlayers: Int,
    checkedInCount: Int? = null,
    compact: Boolean = false,
    showCountdown: Boolean = true
) {
    val stage = schedule.stageAt(now)
    ArcadePanel(Modifier.fillMaxWidth().testTag("tournament_schedule_panel"), accent = ArcadePalette.Blue300) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = ArcadePalette.Blue300, modifier = Modifier.size(24.dp))
                Text(LocalLocalization.current.timeZoneDisplayLabel(schedule.timeZoneId), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(localized(TextKey.TournamentStartDeadline, "time" to tournamentLocalDateTime(schedule.startsAtEpochMillis, schedule.timeZoneId)),
                fontWeight = FontWeight.Bold)
            if (!compact) {
                Text(localized(TextKey.TournamentRegistrationDeadline, "time" to tournamentLocalDateTime(schedule.registrationClosesAtEpochMillis, schedule.timeZoneId)))
                Text(localized(TextKey.TournamentCheckInDeadline, "time" to tournamentLocalDateTime(schedule.checkInOpensAtEpochMillis, schedule.timeZoneId)))
            }
            if (showCountdown) Text(
                when (stage) {
                    TournamentScheduleStage.REGISTRATION -> localized(TextKey.TournamentCheckInCountdown,
                        "time" to tournamentCountdown(schedule.checkInOpensAtEpochMillis - now))
                    TournamentScheduleStage.CHECK_IN -> localized(TextKey.TournamentStartCountdown,
                        "time" to tournamentCountdown(schedule.startsAtEpochMillis - now))
                    TournamentScheduleStage.STARTING -> localized(TextKey.TournamentStarting)
                }, modifier = Modifier.testTag("tournament_countdown"), color = ArcadePalette.Gold500,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (now >= schedule.registrationClosesAtEpochMillis && showCountdown) {
                Text(localized(TextKey.TournamentRegistrationClosed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            checkedInCount?.let {
                Text(localized(TextKey.TournamentCheckInCount, "current" to it, "max" to maxPlayers),
                    modifier = Modifier.testTag("tournament_check_in_count"), fontWeight = FontWeight.Bold)
            }
            Text(localized(if (compact) TextKey.TournamentRefundRuleCompact else TextKey.TournamentRefundRule, "max" to maxPlayers),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
