package com.hienthai.fastowin.platform

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun supportsTouchPullToRefresh(): Boolean = true

actual fun createPlainTextClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText("Fast To Win", text))
