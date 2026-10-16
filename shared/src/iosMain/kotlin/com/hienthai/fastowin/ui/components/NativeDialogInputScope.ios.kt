package com.hienthai.fastowin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
internal actual fun NativeDialogInputScope(content: @Composable () -> Unit) = content()

internal actual fun arcadeDialogProperties() = DialogProperties(usePlatformDefaultWidth = false)
