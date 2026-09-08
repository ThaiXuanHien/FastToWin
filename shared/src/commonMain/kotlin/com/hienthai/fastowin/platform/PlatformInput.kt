package com.hienthai.fastowin.platform

import androidx.compose.ui.platform.ClipEntry

/** Input affordances appropriate to the current platform. */
data class PlatformRefreshInput(
    val touchPullEnabled: Boolean,
    val pointerRefreshEnabled: Boolean
)

expect fun platformRefreshInput(): PlatformRefreshInput

/** Creates a platform clipboard entry containing plain text. */
expect fun createPlainTextClipEntry(text: String): ClipEntry
