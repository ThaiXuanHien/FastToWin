package com.hienthai.fastowin.screenshot

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.protocol.*
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import com.hienthai.fastowin.ui.components.MaintenanceBanner
import com.hienthai.fastowin.ui.screens.MaintenanceScreen

private val maintenancePreviewStatus = ServiceStatusResponse(
    state = MaintenanceState.DRAINING, messageVi = "Đấu trường đang cập nhật. Cảm ơn bạn đã chờ!",
    messageEn = "The arena is updating. Thank you for waiting!",
    transitionAtEpochMillis = ArcadeScreenshotFixtures.NOW_MILLIS + 240000
)

@PreviewTest
@Preview(name = "phone", widthDp = 390, heightDp = 844, showSystemUi = false)
@Preview(name = "desktop", widthDp = 1024, heightDp = 768, showSystemUi = false)
@Preview(name = "landscape", widthDp = 844, heightDp = 390, showSystemUi = false)
@Preview(name = "large_text", widthDp = 390, heightDp = 844, fontScale = 2f, showSystemUi = false)
@Composable
fun maintenanceClosed() = ArcadeScreenshotFrame {
    ArcadeBackdrop(Modifier.fillMaxSize()) {
        MaintenanceScreen(message = maintenancePreviewStatus.messageVi,
            expectedEndEpochMillis = ArcadeScreenshotFixtures.NOW_MILLIS + 3600000)
    }
}

@PreviewTest
@Preview(name = "phone", widthDp = 390, heightDp = 844, showSystemUi = false)
@Preview(name = "desktop", widthDp = 1024, heightDp = 768, showSystemUi = false)
@Composable
fun maintenanceDraining() = ArcadeScreenshotFrame {
    ArcadeBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MaintenanceBanner(maintenancePreviewStatus, true, nowMillis = ArcadeScreenshotFixtures.NOW_MILLIS)
            // The actual game surface remains composed below the banner.
            com.hienthai.fastowin.ui.screens.GameScreen(
                state = ArcadeScreenshotFixtures.activeGameState(), onNumberClick = {}, onFinish = {},
                onExit = {}, onSendEmoji = {}
            )
        }
    }
}

@PreviewTest
@Preview(name = "landscape_long_message", widthDp = 844, heightDp = 390, showSystemUi = false)
@Preview(name = "phone_long_message_large_text", widthDp = 390, heightDp = 844, fontScale = 2f, showSystemUi = false)
@Composable
fun maintenanceLongNotice() = ArcadeScreenshotFrame {
    Column(Modifier.fillMaxSize()) {
        MaintenanceBanner(maintenancePreviewStatus.copy(messageVi = "Thông báo bảo trì dài.\n".repeat(50).take(1000)), true,
            nowMillis = ArcadeScreenshotFixtures.NOW_MILLIS)
        Box(Modifier.weight(1f)) {
            com.hienthai.fastowin.ui.screens.GameScreen(state = ArcadeScreenshotFixtures.activeGameState(), onNumberClick = {}, onFinish = {})
        }
    }
}

// Matched controls distinguish pre-existing game scaling from maintenance's added height.
@PreviewTest
@Preview(name = "landscape_baseline", widthDp = 844, heightDp = 390, showSystemUi = false)
@Preview(name = "phone_large_text_baseline", widthDp = 390, heightDp = 844, fontScale = 2f, showSystemUi = false)
@Composable
fun maintenanceGameBaseline() = ArcadeScreenshotFrame {
    com.hienthai.fastowin.ui.screens.GameScreen(state = ArcadeScreenshotFixtures.activeGameState(), onNumberClick = {}, onFinish = {})
}

@PreviewTest
@Preview(name = "phone_details_large_text", widthDp = 390, heightDp = 844, fontScale = 2f, showSystemUi = false)
@Preview(name = "landscape_details", widthDp = 844, heightDp = 390, showSystemUi = false)
@Composable
fun maintenanceNoticeDetails() = ArcadeScreenshotFrame {
    com.hienthai.fastowin.ui.components.MaintenanceNoticeDialog(
        status = maintenancePreviewStatus.copy(messageVi = "Thông báo bảo trì dài.\n".repeat(50).take(1000)),
        reachable = false, seconds = 240, onDismiss = {})
}
