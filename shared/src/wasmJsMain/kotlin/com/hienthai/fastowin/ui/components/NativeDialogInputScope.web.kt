@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.InteropViewGroup
import androidx.compose.ui.viewinterop.LocalInteropContainer
import androidx.compose.ui.viewinterop.TrackInteropPlacementContainer
import androidx.compose.ui.viewinterop.WebInteropContainer
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Compose dialog scenes do not track their native editors in the main scene's
 * placement tree (CMP-9366). Each editor is otherwise repeatedly inserted at
 * index zero during resize, which blurs a focused input and closes the keyboard.
 * Give this scene its own DOM root and placement tracker. Keep this workaround
 * isolated while Compose UI is pinned; remove it once upstream fixes dialog
 * interop and the clan-keyboard Chromium/WebKit regression passes without it.
 */
@Composable
internal actual fun NativeDialogInputScope(content: @Composable () -> Unit) {
    val root = remember {
        (document.createElement("div") as HTMLElement).apply {
            setAttribute("data-fasttowin-dialog-inputs", "")
            style.position = "fixed"
            style.left = "0"
            style.top = "0"
        }
    }
    val container = remember(root) { WebInteropContainer(InteropViewGroup(root)) }
    DisposableEffect(root) {
        // During keyboard resize, Compose can hide an editor for one frame
        // before BringIntoView scrolls it back. visibility:hidden blurs DOM
        // inputs, closing the keyboard. Keep focus while clipping that frame
        // completely; do not refocus after an intentional blur or dismissal.
        val focusStyle = document.createElement("style").apply {
            textContent = """
                /* Main-scene editors and their pointer-intercepting wrappers
                   must stay behind the modal, preserving the editor values. */
                [data-fasttowin-native-input]:not([data-fasttowin-dialog-inputs] [data-fasttowin-native-input]),
                div:has(> [data-fasttowin-native-input]):not([data-fasttowin-dialog-inputs] div) {
                    visibility: hidden !important;
                    pointer-events: none !important;
                }
                [data-fasttowin-dialog-inputs] > div:has(> [data-fasttowin-native-input]:focus) {
                    visibility: visible !important;
                }
                [data-fasttowin-dialog-inputs] > div[style*="visibility: hidden"]:has(> [data-fasttowin-native-input]:focus) {
                    clip-path: inset(100%) !important;
                }
            """.trimIndent()
        }
        document.head!!.appendChild(focusStyle)
        document.body!!.appendChild(root)
        onDispose {
            root.parentNode?.removeChild(root)
            focusStyle.parentNode?.removeChild(focusStyle)
        }
    }
    CompositionLocalProvider(LocalInteropContainer provides container) {
        container.TrackInteropPlacementContainer(content)
    }
}

internal actual fun arcadeDialogProperties() = DialogProperties(usePlatformDefaultWidth = false)
