@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.platform

import androidx.compose.ui.platform.ClipEntry

actual fun supportsTouchPullToRefresh(): Boolean = browserHasTouchInput()

actual fun createPlainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)

private fun browserHasTouchInput(): Boolean = js(
    """(
        (navigator.maxTouchPoints || navigator.msMaxTouchPoints || 0) > 0 &&
        (!window.matchMedia || window.matchMedia('(pointer: coarse)').matches)
    )"""
)
