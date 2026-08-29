@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberResultImageSharer(): ResultImageSharer = remember {
    ResultImageSharer { content ->
        runCatching { shareOrCopyText(content.caption, "Kết quả Fast To Win") }
    }
}

@Composable
actual fun rememberTextSharer(): TextSharer = remember {
    TextSharer { text, title -> runCatching { shareOrCopyText(text, title) } }
}

private fun shareOrCopyText(text: String, title: String): Unit = js(
    """{
        if (navigator.share) {
            navigator.share({ title: title, text: text });
        } else if (navigator.clipboard) {
            navigator.clipboard.writeText(text);
        }
    }"""
)
