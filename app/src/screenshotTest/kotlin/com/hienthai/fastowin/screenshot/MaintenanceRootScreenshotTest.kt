package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.protocol.MaintenanceState
import com.hienthai.fastowin.protocol.ServiceStatusResponse
import com.hienthai.fastowin.ui.components.MaintenanceRoot
import com.hienthai.fastowin.ui.screens.GameScreen

private val rootStatus = ServiceStatusResponse(
    state = MaintenanceState.DRAINING,
    transitionAtEpochMillis = ArcadeScreenshotFixtures.NOW_MILLIS + 240000
)

@Composable
private fun rootInsets(): WindowInsets = if (LocalConfiguration.current.screenWidthDp > 600) {
    WindowInsets(left = 40.dp, top = 24.dp, right = 20.dp, bottom = 16.dp)
} else {
    WindowInsets(left = 12.dp, top = 32.dp, right = 12.dp, bottom = 24.dp)
}

@PreviewTest
@Preview(name = "portrait_insets", widthDp = 390, heightDp = 844, showSystemUi = false)
@Preview(name = "landscape_cutout", widthDp = 844, heightDp = 390, showSystemUi = false)
@Composable
fun maintenanceRootGame() = ArcadeScreenshotFrame {
    val insets = rootInsets()
    MaintenanceRoot(rootStatus, true, safeDrawingInsets = insets, nowMillis = ArcadeScreenshotFixtures.NOW_MILLIS) {
        // The renderer has no OS bars; inject the same explicit screen insets that
        // ResponsiveScreen receives from Android in the actual app.
        Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            GameScreen(state = ArcadeScreenshotFixtures.activeGameState(), onNumberClick = {}, onFinish = {})
        }
    }
}

@PreviewTest
@Preview(name = "portrait_insets", widthDp = 390, heightDp = 844, showSystemUi = false)
@Preview(name = "landscape_cutout", widthDp = 844, heightDp = 390, showSystemUi = false)
@Composable
fun maintenanceRootInsetProbe() = ArcadeScreenshotFrame {
    val insets = rootInsets()
    MaintenanceRoot(rootStatus, true, safeDrawingInsets = insets, nowMillis = ArcadeScreenshotFixtures.NOW_MILLIS) {
        Column(Modifier.fillMaxSize().background(Color.Magenta).windowInsetsPadding(insets)) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Green))
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black))
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Cyan))
        }
    }
}
