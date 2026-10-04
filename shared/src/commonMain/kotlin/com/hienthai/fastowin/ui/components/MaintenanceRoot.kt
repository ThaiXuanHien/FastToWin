package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hienthai.fastowin.protocol.ServiceStatusResponse

/** Root notice and the admitted screen share ownership of the safe drawing area. */
@Composable
fun MaintenanceRoot(
    bannerStatus: ServiceStatusResponse?,
    reachable: Boolean,
    modifier: Modifier = Modifier,
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
    nowMillis: Long? = null,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize()) {
        if (bannerStatus != null) MaintenanceBanner(
            bannerStatus, reachable, nowMillis = nowMillis,
            contentWindowInsets = safeDrawingInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        )
        // Only top was occupied above the screen. Preserve horizontal/bottom
        // insets for ResponsiveScreen and each screen's bottom controls.
        val screenInsets = if (bannerStatus != null) {
            Modifier.consumeWindowInsets(safeDrawingInsets.only(WindowInsetsSides.Top))
        } else Modifier
        Box(Modifier.weight(1f).fillMaxWidth().then(screenInsets)) { content() }
    }
}
