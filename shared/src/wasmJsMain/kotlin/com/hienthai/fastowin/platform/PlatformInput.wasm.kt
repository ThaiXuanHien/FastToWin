@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.platform

import androidx.compose.ui.platform.ClipEntry

actual fun platformRefreshInput(): PlatformRefreshInput {
    val hasTouchInput = browserHasTouchInput()
    return PlatformRefreshInput(
        touchPullEnabled = hasTouchInput,
        pointerRefreshEnabled = !hasTouchInput
    )
}

actual fun createPlainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)

private fun browserHasTouchInput(): Boolean = js(
    """(
        (navigator.maxTouchPoints || navigator.msMaxTouchPoints || 0) > 0
    )"""
)
