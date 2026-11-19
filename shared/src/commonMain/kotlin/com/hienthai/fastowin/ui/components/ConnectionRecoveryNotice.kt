package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.state.ConnectionStatus
import com.hienthai.fastowin.ui.theme.ArcadePalette
import kotlinx.coroutines.delay

internal enum class RecoveryNoticeKind { CONNECTING, OFFLINE, RESTORED, UNAVAILABLE }

/** Pure timing rules, kept separate from Compose so they can be unit tested. */
internal object RecoveryNoticeTiming {
    /** A blip shorter than this never shows anything. */
    const val CONNECTING_AFTER_MILLIS = 2_000L
    /** Still disconnected after this long reads as offline, with the practice shortcut. */
    const val OFFLINE_AFTER_MILLIS = 10_000L
    const val RESTORED_VISIBLE_MILLIS = 2_000L
}

/**
 * The one place the app talks about connectivity outside a match (the match keeps its own
 * blocking reconnect overlay). Non-modal: the screen underneath stays usable with its last data.
 */
@Composable
fun ConnectionRecoveryNotice(
    connectionStatus: ConnectionStatus,
    serviceRecoveryAvailable: Boolean,
    serviceReachable: Boolean?,
    onReconnect: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    onPractice: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val disconnected = connectionStatus != ConnectionStatus.CONNECTED || serviceReachable == false
    var kind by remember { mutableStateOf<RecoveryNoticeKind?>(null) }
    var announcedOutage by remember { mutableStateOf(false) }
    LaunchedEffect(disconnected, serviceReachable) {
        if (disconnected) {
            if (serviceReachable == false) {
                kind = RecoveryNoticeKind.OFFLINE; announcedOutage = true; return@LaunchedEffect
            }
            delay(RecoveryNoticeTiming.CONNECTING_AFTER_MILLIS)
            kind = RecoveryNoticeKind.CONNECTING; announcedOutage = true
            delay(RecoveryNoticeTiming.OFFLINE_AFTER_MILLIS - RecoveryNoticeTiming.CONNECTING_AFTER_MILLIS)
            kind = RecoveryNoticeKind.OFFLINE
        } else if (announcedOutage) {
            announcedOutage = false
            kind = RecoveryNoticeKind.RESTORED
            delay(RecoveryNoticeTiming.RESTORED_VISIBLE_MILLIS)
            kind = null
        } else kind = null
    }
    val shown = kind ?: RecoveryNoticeKind.UNAVAILABLE.takeIf {
        connectionStatus == ConnectionStatus.CONNECTED && serviceRecoveryAvailable
    } ?: return

    val (container, accent, icon) = when (shown) {
        RecoveryNoticeKind.CONNECTING -> Triple(ArcadePalette.Navy800, ArcadePalette.Blue300, null)
        RecoveryNoticeKind.OFFLINE -> Triple(Color(0xFF3A2A08), ArcadePalette.Gold400, Icons.Rounded.WifiOff)
        RecoveryNoticeKind.RESTORED -> Triple(ArcadePalette.Mint900, ArcadePalette.Mint400, Icons.Rounded.CheckCircle)
        RecoveryNoticeKind.UNAVAILABLE -> Triple(ArcadePalette.Navy800, ArcadePalette.Blue300, Icons.Rounded.Info)
    }
    BoxWithConstraints(modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        val narrow = maxWidth < 480.dp // Offline copy plus two actions needs about 480 dp on one line.
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("connection_recovery_notice")
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(16.dp),
            color = container,
            contentColor = Color.White,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon == null) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                    } else {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(localized(when (shown) {
                            RecoveryNoticeKind.CONNECTING -> TextKey.RecoveryConnecting
                            RecoveryNoticeKind.OFFLINE -> TextKey.RecoveryOffline
                            RecoveryNoticeKind.RESTORED -> TextKey.RecoveryRestored
                            RecoveryNoticeKind.UNAVAILABLE -> TextKey.RecoveryUnavailable
                        }), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (shown == RecoveryNoticeKind.OFFLINE) {
                            Text(localized(TextKey.RecoveryOfflineHint), style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.82f))
                        }
                    }
                    // Wide screens keep every action on one line; a narrow phone moves offline actions
                    // to their own row so the message is never squeezed.
                    if (!narrow || shown != RecoveryNoticeKind.OFFLINE) {
                        NoticeActions(shown, accent, onReconnect, onReload, onDismiss, onPractice.takeIf { !narrow })
                    }
                }
                if (narrow && shown == RecoveryNoticeKind.OFFLINE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        NoticeActions(shown, accent, onReconnect, onReload, onDismiss, onPractice)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeActions(
    shown: RecoveryNoticeKind,
    accent: Color,
    onReconnect: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    onPractice: (() -> Unit)?
) {
    when (shown) {
        RecoveryNoticeKind.RESTORED -> Unit
        RecoveryNoticeKind.CONNECTING, RecoveryNoticeKind.OFFLINE -> Row(verticalAlignment = Alignment.CenterVertically) {
            onPractice?.takeIf { shown == RecoveryNoticeKind.OFFLINE }?.let { practice ->
                TextButton(onClick = practice, modifier = Modifier.heightIn(min = 48.dp).testTag("connection_recovery_practice")) {
                    Text(localized(TextKey.RecoveryPractice), color = accent)
                }
            }
            TextButton(onClick = onReconnect, modifier = Modifier.heightIn(min = 48.dp).testTag("connection_recovery_action")) {
                Text(localized(TextKey.Retry), color = accent, fontWeight = FontWeight.SemiBold)
            }
        }
        RecoveryNoticeKind.UNAVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onReload, modifier = Modifier.heightIn(min = 48.dp).testTag("connection_recovery_action")) {
                Text(localized(TextKey.RecoveryReload), color = accent, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("connection_recovery_dismiss")) {
                Icon(Icons.Rounded.Close, contentDescription = localized(TextKey.Close), tint = Color.White)
            }
        }
    }
}
