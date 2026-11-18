package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.localization.TextKey
import com.hienthai.fastowin.localization.localized
import com.hienthai.fastowin.state.ConnectionStatus
import kotlinx.coroutines.delay

/** A quiet, non-modal recovery action; the match reconnect blocker owns active games. */
@Composable
fun ConnectionRecoveryNotice(
    connectionStatus: ConnectionStatus,
    serviceRecoveryAvailable: Boolean,
    onReconnect: () -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reconnecting = connectionStatus in setOf(ConnectionStatus.DISCONNECTED,
        ConnectionStatus.CONNECTING, ConnectionStatus.AUTHENTICATING, ConnectionStatus.RECONNECTING)
    var showConnection by remember { mutableStateOf(false) }
    LaunchedEffect(reconnecting) {
        showConnection = false
        if (reconnecting) { delay(2_000); showConnection = true }
    }
    if (!showConnection && !(connectionStatus == ConnectionStatus.CONNECTED && serviceRecoveryAvailable)) return
    BoxWithConstraints(modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Snackbar(
            modifier = Modifier.testTag("connection_recovery_notice")
                .semantics { liveRegion = LiveRegionMode.Polite },
            actionOnNewLine = maxWidth < 360.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            action = {
                TextButton(onClick = if (showConnection) onReconnect else onReload,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("connection_recovery_action")) {
                    Text(localized(if (showConnection) TextKey.Retry else TextKey.RecoveryReload))
                }
            },
            dismissAction = if (!showConnection) ({
                IconButton(onClick = onDismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("connection_recovery_dismiss")) {
                    Icon(Icons.Rounded.Close, contentDescription = localized(TextKey.Close),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }) else null
        ) {
            Text(localized(if (showConnection) TextKey.RecoveryConnecting else TextKey.RecoveryUnavailable))
        }
    }
}
