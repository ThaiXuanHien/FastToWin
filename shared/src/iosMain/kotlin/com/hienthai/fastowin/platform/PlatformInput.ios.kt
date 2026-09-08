@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.hienthai.fastowin.platform

import androidx.compose.ui.platform.ClipEntry

actual fun platformRefreshInput(): PlatformRefreshInput = PlatformRefreshInput(
    touchPullEnabled = true,
    pointerRefreshEnabled = false
)

actual fun createPlainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)
