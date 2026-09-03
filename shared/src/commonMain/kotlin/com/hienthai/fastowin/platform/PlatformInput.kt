package com.hienthai.fastowin.platform

import androidx.compose.ui.platform.ClipEntry

/** Whether the current device has touch input suitable for a pull-to-refresh gesture. */
expect fun supportsTouchPullToRefresh(): Boolean

/** Creates a platform clipboard entry containing plain text. */
expect fun createPlainTextClipEntry(text: String): ClipEntry
