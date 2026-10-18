package com.hienthai.fastowin

import android.graphics.Bitmap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.printToString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun captureDialogFailure(label: String, vararg interactions: SemanticsNodeInteraction) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val cache = instrumentation.targetContext.cacheDir
    val screenshot = runCatching {
        File(cache, "dialog-$label.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    val native = runCatching { captureNativeInputFailure("dialog-$label") }
    val semantics = runCatching {
        val details = buildString {
            interactions.forEach { interaction ->
                val result = runCatching { interaction.fetchSemanticsNode() }
                val node = result.getOrNull()
                if (node == null) {
                    appendLine("Node unavailable: ${result.exceptionOrNull()}")
                    return@forEach
                }
                appendLine("size=${node.size} rootBounds=${node.boundsInRoot} windowBounds=${node.boundsInWindow} windowPosition=${node.positionInWindow}")
                instrumentation.runOnMainSync {
                    val view = (node.root as ViewRootForTest).view
                    val insets = ViewCompat.getRootWindowInsets(view)
                    val position = IntArray(2).also(view::getLocationOnScreen)
                    appendLine("hasWindowFocus=${view.hasWindowFocus()} hasFocus=${view.hasFocus()} attached=${view.isAttachedToWindow}")
                    appendLine("view=${view.width}x${view.height} screen=${position.toList()} ime=${insets?.getInsets(WindowInsetsCompat.Type.ime())} visible=${insets?.isVisible(WindowInsetsCompat.Type.ime())}")
                }
                var parent = node.parent
                while (parent != null) {
                    appendLine("parent size=${parent.size} bounds=${parent.boundsInRoot} config=${parent.config}")
                    parent = parent.parent
                }
                appendLine(interaction.printToString())
            }
        }
        File(cache, "dialog-$label.txt").writeText(details)
    }
    val failure = screenshot.exceptionOrNull() ?: native.exceptionOrNull() ?: semantics.exceptionOrNull()
    if (failure != null) {
        native.exceptionOrNull()?.takeIf { it !== failure }?.let(failure::addSuppressed)
        semantics.exceptionOrNull()?.takeIf { it !== failure }?.let(failure::addSuppressed)
        throw failure
    }
}
