package com.hienthai.fastowin.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.hienthai.fastowin.state.AnnouncementState
import com.hienthai.fastowin.ui.screens.NotificationsScreen

@PreviewTest
@Preview(name = "first_load_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "first_load_vi_desktop", widthDp = 1024, heightDp = 768)
@Composable
fun notificationInitialLoad() = ArcadeScreenshotFrame {
    NotificationsScreen(emptyList(), {}, {}, {}, {}, {}, announcementState = AnnouncementState(loading = true))
}

@PreviewTest
@Preview(name = "failed_load_vi_phone", widthDp = 375, heightDp = 812)
@Preview(name = "failed_load_vi_large_font", widthDp = 320, heightDp = 812, fontScale = 1.6f)
@Composable
fun notificationInitialFailure() = ArcadeScreenshotFrame {
    NotificationsScreen(emptyList(), {}, {}, {}, {}, {}, announcementState = AnnouncementState(error = "offline"))
}
