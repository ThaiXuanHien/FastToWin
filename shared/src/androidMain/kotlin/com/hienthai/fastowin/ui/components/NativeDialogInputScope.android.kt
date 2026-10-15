package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

@Composable
internal actual fun NativeDialogInputScope(content: @Composable () -> Unit) {
    // Consume IME/system insets before measuring the dialog's scroll viewport.
    Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() }
}

// Compose owns keyboard avoidance; native panning would move the viewport off screen.
internal actual fun arcadeDialogProperties() = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)
