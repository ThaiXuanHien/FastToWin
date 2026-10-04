package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.data.network.*
import com.hienthai.fastowin.localization.LocalLocalization
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.platform.epochMillis
import com.hienthai.fastowin.protocol.MaintenanceState
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay

/** UI affordance only. The server remains authoritative for every admitted action. */
val LocalNewOnlineIntentAllowed = staticCompositionLocalOf { true }

@Composable
fun MaintenanceBanner(
    status: ServiceStatusResponse,
    reachable: Boolean,
    modifier: Modifier = Modifier,
    nowMillis: Long? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
) {
    var clock by remember { mutableLongStateOf(nowMillis ?: epochMillis()) }
    LaunchedEffect(nowMillis) { if (nowMillis == null) while (true) { clock = epochMillis(); delay(1000) } }
    val draining = status.state == MaintenanceState.DRAINING
    val seconds = maintenanceCountdownSeconds(status.transitionAtEpochMillis, nowMillis ?: clock)
    val title = localized(if (draining) TextKey.MaintenanceDraining else TextKey.MaintenanceAnnouncing)
    var showDetails by remember { mutableStateOf(false) }
    if (showDetails) MaintenanceNoticeDialog(status, reachable, seconds, onDismiss = { showDetails = false })
    Surface(modifier = modifier.fillMaxWidth().testTag("maintenance_banner"), color = ArcadePalette.Navy800, contentColor = ArcadePalette.Blue100) {
        Row(
            Modifier.windowInsetsPadding(contentWindowInsets).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
            Text(title,
                color = ArcadePalette.Gold500, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (seconds != null) {
                val countdownDescription = localized(TextKey.MaintenanceCountdown, "seconds" to seconds)
                Text(localized(TextKey.MaintenanceCountdownCompact, "seconds" to seconds),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { contentDescription = countdownDescription })
            }
            }
            TextButton(onClick = { showDetails = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(localized(TextKey.MaintenanceDetails), color = ArcadePalette.Blue100)
            }
        }
    }
}

/** The complete notice stays readable without resizing an admitted match. */
@Composable
fun MaintenanceNoticeDialog(status: ServiceStatusResponse, reachable: Boolean, seconds: Long?, onDismiss: () -> Unit) {
    val draining = status.state == MaintenanceState.DRAINING
    val message = status.localizedMaintenanceMessage(LocalLocalization.current.language)
    ArcadeDialog(title = localized(if (draining) TextKey.MaintenanceDraining else TextKey.MaintenanceAnnouncing), onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message)
                Text(localized(if (draining) TextKey.MaintenanceDrainGuidance else TextKey.MaintenancePrepare))
                if (seconds != null) Text(localized(TextKey.MaintenanceCountdown, "seconds" to seconds))
                status.expectedEndEpochMillis?.let { Text(localized(TextKey.MaintenanceExpectedEnd, "time" to maintenanceBangkokTime(it))) }
                if (!reachable) Text(localized(TextKey.MaintenanceRecheckUnavailable))
            }
            ArcadeActionButton(label = localized(TextKey.Close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
